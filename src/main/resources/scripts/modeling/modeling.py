"""
核心建模计算模块

提供分子数量计算、盒子尺寸计算、电中性验证等功能
"""

import logging
import math
from typing import Dict, Any, List, Optional, Tuple
from dataclasses import dataclass

from .config.config import Config, config

logger = logging.getLogger(__name__)


@dataclass
class MoleculeInfo:
    """分子信息数据类"""
    name: str
    formula: str
    molecular_weight: float
    charge: int
    count: int = 0
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            "name": self.name,
            "formula": self.formula,
            "molecular_weight": self.molecular_weight,
            "charge": self.charge,
            "count": self.count,
        }


def calculate_molecule_counts(formula_data: dict) -> dict:
    """计算分子数量
    
    根据配方参数计算各组分分子数量
    
    Args:
        formula_data: 配方参数字典，包含:
            - solvent_info: 溶剂信息列表
                - name: 溶剂名称
                - mole_fraction: 摩尔分数
                - molecular_weight: 分子量
            - salt_info: 盐信息
                - name: 盐名称
                - concentration: 浓度 (mol/L)
                - cation: 阳离子信息
                - anion: 阴离子信息
            - box_size: 盒子尺寸 (Å)
                - x, y, z: 各边长
                
    Returns:
        分子数量字典，包含:
            - molecules: 分子列表（含数量）
            - total_mass: 总质量 (g)
            - volume: 体积 (Å³)
            - is_neutral: 是否电中性
            - total_charge: 总电荷
    """
    logger.info("开始计算分子数量")
    
    solvent_info = formula_data.get("solvent_info", [])
    salt_info = formula_data.get("salt_info", {})
    box_size = formula_data.get("box_size", {})
    
    if not box_size:
        raise ValueError("缺少盒子尺寸参数")
    
    x = box_size.get("x", 0)
    y = box_size.get("y", 0)
    z = box_size.get("z", 0)
    
    if x <= 0 or y <= 0 or z <= 0:
        raise ValueError(f"盒子尺寸必须为正数: x={x}, y={y}, z={z}")
    
    volume_angstrom3 = x * y * z
    volume_cm3 = volume_angstrom3 / config.CM3_TO_ANGSTROM3
    
    logger.debug(f"盒子体积: {volume_angstrom3:.2f} Å³ = {volume_cm3:.6e} cm³")
    
    density = formula_data.get("density", config.DEFAULT_DENSITY)
    
    total_mass = density * volume_cm3
    logger.debug(f"总质量: {total_mass:.6f} g (密度: {density} g/cm³)")
    
    molecules = []
    
    total_solvent_moles = total_mass / _calculate_average_solvent_mw(solvent_info)
    logger.debug(f"溶剂总摩尔数估算: {total_solvent_moles:.6e} mol")
    
    for solvent in solvent_info:
        name = solvent.get("name", "Unknown")
        mole_fraction = solvent.get("mole_fraction", 0)
        molecular_weight = solvent.get("molecular_weight", 0)
        
        if molecular_weight <= 0:
            mol_info = config.get_molecule_info(name)
            molecular_weight = mol_info.get("molecular_weight", 0)
        
        moles = total_solvent_moles * mole_fraction
        count = int(round(moles * config.AVOGADRO_NUMBER))
        
        molecules.append(MoleculeInfo(
            name=name,
            formula=solvent.get("formula", name),
            molecular_weight=molecular_weight,
            charge=0,
            count=count,
        ))
        
        logger.debug(f"溶剂 {name}: 摩尔分数={mole_fraction}, 分子数={count}")
    
    if salt_info:
        concentration = salt_info.get("concentration", 0)
        
        salt_moles = concentration * volume_cm3 / 1000
        salt_count = int(round(salt_moles * config.AVOGADRO_NUMBER))
        
        cation_info = salt_info.get("cation", {})
        anion_info = salt_info.get("anion", {})
        
        cation_name = cation_info.get("name", "Li")
        anion_name = anion_info.get("name", "PF6")
        
        cation_mw = cation_info.get("molecular_weight", 0)
        if cation_mw <= 0:
            mol_info = config.get_molecule_info(cation_name)
            cation_mw = mol_info.get("molecular_weight", 6.94)
        
        anion_mw = anion_info.get("molecular_weight", 0)
        if anion_mw <= 0:
            mol_info = config.get_molecule_info(anion_name)
            anion_mw = mol_info.get("molecular_weight", 144.96)
        
        cation_charge = cation_info.get("charge", 1)
        anion_charge = anion_info.get("charge", -1)
        
        molecules.append(MoleculeInfo(
            name=cation_name,
            formula=cation_info.get("formula", cation_name),
            molecular_weight=cation_mw,
            charge=cation_charge,
            count=salt_count,
        ))
        
        molecules.append(MoleculeInfo(
            name=anion_name,
            formula=anion_info.get("formula", anion_name),
            molecular_weight=anion_mw,
            charge=anion_charge,
            count=salt_count,
        ))
        
        logger.debug(f"盐浓度: {concentration} mol/L, 离子对数: {salt_count}")
    
    is_neutral, total_charge = validate_electrical_neutrality(molecules)
    
    if not is_neutral:
        logger.warning(f"体系非电中性，总电荷: {total_charge}")
        molecules = adjust_ion_counts(molecules)
        is_neutral, total_charge = validate_electrical_neutrality(molecules)
        logger.info(f"调整后电中性: {is_neutral}, 总电荷: {total_charge}")
    
    actual_mass = _calculate_total_mass(molecules)
    
    result = {
        "molecules": [m.to_dict() for m in molecules],
        "total_mass": actual_mass,
        "volume": volume_angstrom3,
        "box_size": {"x": x, "y": y, "z": z},
        "is_neutral": is_neutral,
        "total_charge": total_charge,
        "density": density,
    }
    
    logger.info(f"分子数量计算完成，共{len(molecules)}种分子")
    return result


def calculate_box_size(molecule_data: dict, density: float = 1.2) -> dict:
    """根据分子数据计算盒子尺寸
    
    Args:
        molecule_data: 分子数据字典，包含:
            - molecules: 分子列表
                - name: 名称
                - molecular_weight: 分子量
                - count: 数量
        density: 密度 (g/cm³)，默认1.2
        
    Returns:
        盒子尺寸字典:
            - x, y, z: 各边长 (Å)
            - volume: 体积 (Å³)
            - scale_factor: 放大系数
    """
    logger.info(f"开始计算盒子尺寸，密度: {density} g/cm³")
    
    molecules = molecule_data.get("molecules", [])
    
    if not molecules:
        raise ValueError("分子列表为空")
    
    total_mass = _calculate_total_mass_from_dict(molecules)
    logger.debug(f"总质量: {total_mass:.6f} g")
    
    volume_cm3 = total_mass / density
    volume_angstrom3 = volume_cm3 * config.CM3_TO_ANGSTROM3
    
    logger.debug(f"体积: {volume_cm3:.6e} cm³ = {volume_angstrom3:.2f} Å³")
    
    edge_length = volume_angstrom3 ** (1/3)
    
    scaled_edge = edge_length * config.BOX_SCALE_FACTOR
    
    logger.debug(f"原始边长: {edge_length:.2f} Å, 放大后: {scaled_edge:.2f} Å")
    
    result = {
        "x": round(scaled_edge, 2),
        "y": round(scaled_edge, 2),
        "z": round(scaled_edge, 2),
        "volume": volume_angstrom3,
        "scaled_volume": scaled_edge ** 3,
        "scale_factor": config.BOX_SCALE_FACTOR,
    }
    
    logger.info(f"盒子尺寸计算完成: {result['x']} x {result['y']} x {result['z']} Å")
    return result


def validate_electrical_neutrality(molecules: list) -> tuple:
    """验证体系电中性
    
    Args:
        molecules: 分子列表，每个元素包含charge和count
        
    Returns:
        (是否电中性, 总电荷)
    """
    total_charge = 0
    
    for mol in molecules:
        if isinstance(mol, MoleculeInfo):
            charge = mol.charge
            count = mol.count
        else:
            charge = mol.get("charge", 0)
            count = mol.get("count", 0)
        
        total_charge += charge * count
        logger.debug(f"分子 {mol.name if isinstance(mol, MoleculeInfo) else mol.get('name')}: "
                    f"电荷={charge}, 数量={count}, 累计电荷={total_charge}")
    
    is_neutral = abs(total_charge) < 1e-6
    
    logger.debug(f"电中性验证: {'通过' if is_neutral else '未通过'}, 总电荷={total_charge}")
    
    return is_neutral, total_charge


def adjust_ion_counts(molecules: list) -> list:
    """调整离子数量使体系电中性
    
    Args:
        molecules: 分子列表
        
    Returns:
        调整后的分子列表
    """
    logger.info("开始调整离子数量")
    
    total_charge = 0
    cations = []
    anions = []
    
    for i, mol in enumerate(molecules):
        if isinstance(mol, MoleculeInfo):
            charge = mol.charge
            count = mol.count
        else:
            charge = mol.get("charge", 0)
            count = mol.get("count", 0)
        
        total_charge += charge * count
        
        if charge > 0:
            cations.append((i, mol, charge))
        elif charge < 0:
            anions.append((i, mol, charge))
    
    if total_charge == 0:
        logger.info("体系已电中性，无需调整")
        return molecules
    
    logger.debug(f"初始总电荷: {total_charge}")
    
    adjusted_molecules = [m for m in molecules]
    
    if total_charge > 0:
        for idx, mol, charge in anions:
            if isinstance(mol, MoleculeInfo):
                adjustment = total_charge // abs(charge)
                if adjustment > 0:
                    new_count = mol.count + adjustment
                    adjusted_molecules[idx] = MoleculeInfo(
                        name=mol.name,
                        formula=mol.formula,
                        molecular_weight=mol.molecular_weight,
                        charge=mol.charge,
                        count=new_count,
                    )
                    total_charge += charge * adjustment
                    logger.debug(f"增加阴离子 {mol.name}: {mol.count} -> {new_count}")
                    break
    else:
        for idx, mol, charge in cations:
            if isinstance(mol, MoleculeInfo):
                adjustment = abs(total_charge) // charge
                if adjustment > 0:
                    new_count = mol.count + adjustment
                    adjusted_molecules[idx] = MoleculeInfo(
                        name=mol.name,
                        formula=mol.formula,
                        molecular_weight=mol.molecular_weight,
                        charge=mol.charge,
                        count=new_count,
                    )
                    total_charge += charge * adjustment
                    logger.debug(f"增加阳离子 {mol.name}: {mol.count} -> {new_count}")
                    break
    
    is_neutral, final_charge = validate_electrical_neutrality(adjusted_molecules)
    logger.info(f"调整完成，电中性: {is_neutral}, 最终电荷: {final_charge}")
    
    return adjusted_molecules


def _calculate_average_solvent_mw(solvent_info: list) -> float:
    """计算溶剂平均分子量
    
    Args:
        solvent_info: 溶剂信息列表
        
    Returns:
        平均分子量
    """
    total_mw = 0
    total_fraction = 0
    
    for solvent in solvent_info:
        mole_fraction = solvent.get("mole_fraction", 0)
        mw = solvent.get("molecular_weight", 0)
        
        if mw <= 0:
            name = solvent.get("name", "")
            mol_info = config.get_molecule_info(name)
            mw = mol_info.get("molecular_weight", 0)
        
        total_mw += mw * mole_fraction
        total_fraction += mole_fraction
    
    if total_fraction <= 0:
        return 100.0
    
    return total_mw / total_fraction


def _calculate_total_mass(molecules: List[MoleculeInfo]) -> float:
    """计算分子总质量
    
    Args:
        molecules: 分子信息列表
        
    Returns:
        总质量 (g)
    """
    total_mass = 0
    for mol in molecules:
        mass = mol.molecular_weight * mol.count / config.AVOGADRO_NUMBER
        total_mass += mass
        logger.debug(f"{mol.name}: MW={mol.molecular_weight}, count={mol.count}, mass={mass:.6e} g")
    
    return total_mass


def _calculate_total_mass_from_dict(molecules: list) -> float:
    """从字典列表计算分子总质量
    
    Args:
        molecules: 分子字典列表
        
    Returns:
        总质量 (g)
    """
    total_mass = 0
    for mol in molecules:
        mw = mol.get("molecular_weight", 0)
        count = mol.get("count", 0)
        
        if mw <= 0:
            name = mol.get("name", "")
            mol_info = config.get_molecule_info(name)
            mw = mol_info.get("molecular_weight", 0)
        
        mass = mw * count / config.AVOGADRO_NUMBER
        total_mass += mass
    
    return total_mass


def generate_molecule_summary(molecules: List[Dict[str, Any]]) -> Dict[str, Any]:
    """生成分子摘要信息
    
    Args:
        molecules: 分子列表
        
    Returns:
        摘要信息字典
    """
    total_count = 0
    total_mass = 0
    solvent_count = 0
    ion_count = 0
    
    for mol in molecules:
        count = mol.get("count", 0)
        mw = mol.get("molecular_weight", 0)
        charge = mol.get("charge", 0)
        
        total_count += count
        total_mass += mw * count / config.AVOGADRO_NUMBER
        
        if charge == 0:
            solvent_count += count
        else:
            ion_count += count
    
    return {
        "total_molecule_count": total_count,
        "total_mass_g": total_mass,
        "solvent_count": solvent_count,
        "ion_count": ion_count,
        "species_count": len(molecules),
    }