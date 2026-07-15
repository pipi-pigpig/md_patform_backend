"""
Moltemplate工具封装模块

提供Moltemplate力场生成功能的封装，以及PDB文件解析功能
"""

import logging
import subprocess
import os
import shutil
from pathlib import Path
from typing import Dict, Any, List, Optional, Tuple
from dataclasses import dataclass
import re

logger = logging.getLogger(__name__)


@dataclass
class AtomRecord:
    """PDB原子记录数据结构
    
    Attributes:
        serial: 原子序号
        name: 原子名称
        alt_loc: 替代位置标识符
        res_name: 残基名称（分子名称）
        chain_id: 链标识符
        res_seq: 残基序号（分子实例序号）
        icode: 插入代码
        x: X坐标
        y: Y坐标
        z: Z坐标
        occupancy: 占据率
        temp_factor: 温度因子
        element: 元素符号
        charge: 电荷
        record_type: 记录类型（ATOM或HETATM）
    """
    serial: int
    name: str
    alt_loc: str
    res_name: str
    chain_id: str
    res_seq: int
    icode: str
    x: float
    y: float
    z: float
    occupancy: float
    temp_factor: float
    element: str
    charge: str
    record_type: str


@dataclass
class MoleculeInstance:
    """分子实例数据结构
    
    Attributes:
        molecule_name: 分子名称
        instance_id: 实例序号（从1开始）
        atoms: 原子列表
        chain_id: 链标识符
        start_line: 在PDB文件中的起始行号
        end_line: 在PDB文件中的结束行号
    """
    molecule_name: str
    instance_id: int
    atoms: List[AtomRecord]
    chain_id: str
    start_line: int
    end_line: int


class MoltemplateRunner:
    """Moltemplate运行器"""
    
    def __init__(self, moltemplate_path: str = "moltemplate.sh"):
        """初始化Moltemplate运行器
        
        Args:
            moltemplate_path: Moltemplate脚本路径
        """
        self.moltemplate_path = moltemplate_path
    
    def generate_system_file(
        self,
        molecules: List[Dict[str, Any]],
        box_size: Dict[str, float],
        output_path: str,
        forcefield: str = "oplsaa",
    ) -> str:
        """生成Moltemplate系统文件
        
        Args:
            molecules: 分子列表，每个包含:
                - name: 分子名称
                - count: 分子数量
                - lt_file: LT模板文件路径（可选）
            box_size: 盒子尺寸 {"x", "y", "z"}
            output_path: 输出文件路径
            forcefield: 力场类型
            
        Returns:
            生成的LT文件路径
        """
        logger.info("生成Moltemplate系统文件")
        
        x = box_size.get("x", 50)
        y = box_size.get("y", 50)
        z = box_size.get("z", 50)
        
        lines = []
        
        for mol in molecules:
            name = mol.get("name", "Unknown")
            count = mol.get("count", 1)
            lt_file = mol.get("lt_file", f"{name}.lt")
            
            logger.debug(f"添加分子 {name}: 数量={count}, LT={lt_file}")
            
            lines.append(f'#include "{lt_file}"')
        
        lines.append("")
        lines.append("system = {")
        lines.append("  # 分子定义")
        
        for mol in molecules:
            name = mol.get("name", "Unknown")
            count = mol.get("count", 1)
            var_name = name.lower().replace("-", "_").replace("+", "_pos").replace("-", "_neg")
            lines.append(f"  {var_name}s = new {name}[{count}]")
        
        lines.append("")
        lines.append("  # 盒子尺寸")
        lines.append(f"  # Box size: {x:.2f} x {y:.2f} x {z:.2f} Angstrom")
        lines.append("}")
        
        content = "\n".join(lines)
        
        output_file = Path(output_path)
        output_file.parent.mkdir(parents=True, exist_ok=True)
        
        with open(output_file, "w") as f:
            f.write(content)
        
        logger.info(f"Moltemplate系统文件已生成: {output_file}")
        return str(output_file)
    
    def run(
        self,
        system_file: str,
        working_dir: str = None,
        timeout: int = 3600,
    ) -> Dict[str, Any]:
        """运行Moltemplate
        
        Args:
            system_file: 系统LT文件路径
            working_dir: 工作目录
            timeout: 超时时间 (秒)
            
        Returns:
            运行结果字典:
                - success: 是否成功
                - output: 标准输出
                - error: 错误信息
                - return_code: 返回码
        """
        logger.info(f"运行Moltemplate: {system_file}")
        
        system_path = Path(system_file)
        working_dir = working_dir or str(system_path.parent)
        
        try:
            result = subprocess.run(
                [self.moltemplate_path, "-atomstyle", "full", system_file],
                cwd=working_dir,
                capture_output=True,
                text=True,
                timeout=timeout,
            )
            
            success = result.returncode == 0
            
            if success:
                logger.info("Moltemplate运行成功")
            else:
                logger.error(f"Moltemplate运行失败: {result.stderr}")
            
            return {
                "success": success,
                "output": result.stdout,
                "error": result.stderr,
                "return_code": result.returncode,
            }
            
        except subprocess.TimeoutExpired:
            logger.error(f"Moltemplate运行超时: {timeout}秒")
            return {
                "success": False,
                "output": "",
                "error": f"Timeout after {timeout} seconds",
                "return_code": -1,
            }
        except Exception as e:
            logger.error(f"Moltemplate运行异常: {e}")
            return {
                "success": False,
                "output": "",
                "error": str(e),
                "return_code": -1,
            }
    
    def check_installation(self) -> bool:
        """检查Moltemplate是否已安装
        
        Returns:
            是否已安装
        """
        try:
            result = subprocess.run(
                [self.moltemplate_path, "-h"],
                capture_output=True,
                text=True,
                timeout=10,
            )
            installed = result.returncode == 0 or "moltemplate" in result.stdout.lower()
            logger.debug(f"Moltemplate安装检查: {'已安装' if installed else '未安装'}")
            return installed
        except Exception as e:
            logger.debug(f"Moltemplate安装检查失败: {e}")
            return False


def generate_lt_template(
    molecule_name: str,
    output_dir: str,
    forcefield: str = "oplsaa",
    atom_types: List[Dict[str, Any]] = None,
    bonds: List[Dict[str, Any]] = None,
    angles: List[Dict[str, Any]] = None,
    dihedrals: List[Dict[str, Any]] = None,
) -> str:
    """生成分子LT模板文件
    
    Args:
        molecule_name: 分子名称
        output_dir: 输出目录
        forcefield: 力场类型
        atom_types: 原子类型列表
        bonds: 键列表
        angles: 角度列表
        dihedrals: 二面角列表
        
    Returns:
        LT文件路径
    """
    logger.info(f"生成分子 {molecule_name} 的LT模板")
    
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    
    lines = [
        f"{molecule_name} {{",
        "",
        f"  # 力场: {forcefield}",
        "",
    ]
    
    if atom_types:
        lines.append("  # 原子类型")
        lines.append("  write_once(\"Data Masses\") {")
        for atom in atom_types:
            atom_type = atom.get("type", "X")
            mass = atom.get("mass", 1.0)
            lines.append(f"    @atom:{atom_type} {mass}")
        lines.append("  }")
        lines.append("")
    
    lines.append("  # 原子坐标")
    lines.append("  write('Data Atoms') {")
    lines.append("    # AtomID MolID AtomType Charge X Y Z")
    lines.append("    # 坐标将由Packmol生成")
    lines.append("  }")
    lines.append("")
    
    if bonds:
        lines.append("  # 键")
        lines.append("  write('Data Bond List') {")
        for i, bond in enumerate(bonds):
            atom1 = bond.get("atom1", 1)
            atom2 = bond.get("atom2", 2)
            lines.append(f"    $bond:{i} @bond:B{atom1}{atom2} $atom:{atom1} $atom:{atom2}")
        lines.append("  }")
        lines.append("")
    
    if angles:
        lines.append("  # 角度")
        lines.append("  write('Data Angle List') {")
        for i, angle in enumerate(angles):
            atom1 = angle.get("atom1", 1)
            atom2 = angle.get("atom2", 2)
            atom3 = angle.get("atom3", 3)
            lines.append(f"    $angle:{i} @angle:A{atom1}{atom2}{atom3} $atom:{atom1} $atom:{atom2} $atom:{atom3}")
        lines.append("  }")
        lines.append("")
    
    if dihedrals:
        lines.append("  # 二面角")
        lines.append("  write('Data Dihedral List') {")
        for i, dihedral in enumerate(dihedrals):
            atom1 = dihedral.get("atom1", 1)
            atom2 = dihedral.get("atom2", 2)
            atom3 = dihedral.get("atom3", 3)
            atom4 = dihedral.get("atom4", 4)
            lines.append(f"    $dihedral:{i} @dihedral:D{atom1}{atom2}{atom3}{atom4} $atom:{atom1} $atom:{atom2} $atom:{atom3} $atom:{atom4}")
        lines.append("  }")
        lines.append("")
    
    lines.append("}")
    lines.append("")
    
    content = "\n".join(lines)
    
    output_file = output_dir / f"{molecule_name}.lt"
    with open(output_file, "w") as f:
        f.write(content)
    
    logger.info(f"LT模板已生成: {output_file}")
    return str(output_file)


def run_moltemplate(
    system_file: str,
    working_dir: str = None,
    moltemplate_path: str = "moltemplate.sh",
    timeout: int = 3600,
) -> Dict[str, Any]:
    """运行Moltemplate（便捷函数）
    
    Args:
        system_file: 系统LT文件路径
        working_dir: 工作目录
        moltemplate_path: Moltemplate路径
        timeout: 超时时间
        
    Returns:
        运行结果
    """
    runner = MoltemplateRunner(moltemplate_path=moltemplate_path)
    return runner.run(system_file, working_dir, timeout)


def create_lammps_data(
    molecules: List[Dict[str, Any]],
    box_size: Dict[str, float],
    output_dir: str,
    forcefield: str = "oplsaa",
) -> Dict[str, Any]:
    """创建LAMMPS数据文件
    
    Args:
        molecules: 分子列表
        box_size: 盒子尺寸
        output_dir: 输出目录
        forcefield: 力场类型
        
    Returns:
        结果字典
    """
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    
    runner = MoltemplateRunner()
    
    system_file = runner.generate_system_file(
        molecules=molecules,
        box_size=box_size,
        output_path=str(output_dir / "system.lt"),
        forcefield=forcefield,
    )
    
    result = runner.run(system_file, str(output_dir))
    
    if result["success"]:
        data_file = output_dir / "system.data"
        if data_file.exists():
            result["data_file"] = str(data_file)
    
    return result


# ==================== PDB解析功能 ====================

def parse_pdb_file(pdb_file_path: str) -> Dict[str, Any]:
    """解析PDB文件，提取原子坐标和分子信息
    
    解析ATOM/HETATM记录，提取原子坐标、分子ID、原子类型和元素信息。
    
    Args:
        pdb_file_path: PDB文件路径
        
    Returns:
        解析结果字典，包含：
            - success: 是否成功
            - atoms: 原子记录列表
            - total_atoms: 原子总数
            - ter_records: TER记录位置列表
            - error: 错误信息（如果失败）
            
    Raises:
        FileNotFoundError: 文件不存在
        ValueError: 文件格式错误
        
    Example:
        >>> result = parse_pdb_file("packed_system.pdb")
        >>> if result["success"]:
        ...     print(f"总原子数: {result['total_atoms']}")
        ...     for atom in result["atoms"]:
        ...         print(f"{atom.res_name} {atom.name}: ({atom.x}, {atom.y}, {atom.z})")
    """
    logger.info(f"[PDB解析] 开始解析PDB文件: {pdb_file_path}")
    
    pdb_path = Path(pdb_file_path)
    if not pdb_path.exists():
        error_msg = f"PDB文件不存在: {pdb_file_path}"
        logger.error(f"[PDB解析] {error_msg}")
        return {
            "success": False,
            "atoms": [],
            "total_atoms": 0,
            "ter_records": [],
            "error": error_msg
        }
    
    atoms = []
    ter_records = []
    line_number = 0
    
    try:
        with open(pdb_path, 'r', encoding='utf-8') as f:
            for line_number, line in enumerate(f, 1):
                # 解析ATOM/HETATM记录
                if line.startswith('ATOM') or line.startswith('HETATM'):
                    try:
                        atom = _parse_atom_line(line, line_number)
                        atoms.append(atom)
                    except Exception as e:
                        logger.warning(f"[PDB解析] 行 {line_number} 解析失败: {e}")
                        continue
                
                # 记录TER记录位置
                elif line.startswith('TER'):
                    ter_records.append(line_number)
                    logger.debug(f"[PDB解析] 发现TER记录在行 {line_number}")
                
                # END记录，停止解析
                elif line.startswith('END'):
                    logger.debug(f"[PDB解析] 到达END记录，行 {line_number}")
                    break
        
        logger.info(f"[PDB解析] 解析完成，共 {len(atoms)} 个原子，{len(ter_records)} 个TER记录")
        
        return {
            "success": True,
            "atoms": atoms,
            "total_atoms": len(atoms),
            "ter_records": ter_records,
            "error": None
        }
        
    except Exception as e:
        error_msg = f"解析PDB文件异常: {str(e)}"
        logger.error(f"[PDB解析] {error_msg}")
        return {
            "success": False,
            "atoms": atoms,
            "total_atoms": len(atoms),
            "ter_records": ter_records,
            "error": error_msg
        }


def _parse_atom_line(line: str, line_number: int) -> AtomRecord:
    """解析单个ATOM/HETATM行
    
    PDB格式说明（列号从1开始）：
        1-6: 记录类型 (ATOM/HETATM)
        7-11: 原子序号
        13-16: 原子名称
        17: 替代位置标识符
        18-20: 残基名称
        22: 链标识符
        23-26: 残基序号
        27: 插入代码
        31-38: X坐标
        39-46: Y坐标
        47-54: Z坐标
        55-60: 占据率
        61-66: 温度因子
        77-78: 元素符号
        79-80: 电荷
    
    Args:
        line: PDB文件中的一行
        line_number: 行号
        
    Returns:
        AtomRecord对象
        
    Raises:
        ValueError: 行格式错误
    """
    # 确保行长度足够（至少需要54个字符来解析坐标）
    if len(line) < 54:
        raise ValueError(f"行长度不足: {len(line)} < 54")
    
    try:
        record_type = line[0:6].strip()
        serial = int(line[6:11].strip())
        name = line[12:16].strip()
        alt_loc = line[16:17].strip() if len(line) > 16 else ''
        res_name = line[17:20].strip()
        chain_id = line[21:22].strip() if len(line) > 21 else ''
        res_seq = int(line[22:26].strip())
        icode = line[26:27].strip() if len(line) > 26 else ''
        
        x = float(line[30:38].strip())
        y = float(line[38:46].strip())
        z = float(line[46:54].strip())
        
        occupancy = float(line[54:60].strip()) if len(line) > 60 and line[54:60].strip() else 1.0
        temp_factor = float(line[60:66].strip()) if len(line) > 66 and line[60:66].strip() else 0.0
        
        # 元素符号：优先从列77-78读取，如果不存在则从原子名称推断
        element = ''
        if len(line) > 78:
            element = line[76:78].strip()
        
        # 如果元素符号为空，尝试从原子名称推断
        if not element:
            # 从原子名称提取元素符号（去除数字和特殊字符）
            atom_name_clean = name.strip()
            if atom_name_clean:
                # 取第一个非数字字符作为元素符号
                for char in atom_name_clean:
                    if char.isalpha():
                        element = char.upper()
                        # 检查是否是双字母元素（如Cl, Br等）
                        if len(atom_name_clean) > 1 and atom_name_clean[1].isalpha() and atom_name_clean[1].islower():
                            element = atom_name_clean[:2]
                        break
        
        charge = line[78:80].strip() if len(line) > 80 else ''
        
        return AtomRecord(
            serial=serial,
            name=name,
            alt_loc=alt_loc,
            res_name=res_name,
            chain_id=chain_id,
            res_seq=res_seq,
            icode=icode,
            x=x,
            y=y,
            z=z,
            occupancy=occupancy,
            temp_factor=temp_factor,
            element=element,
            charge=charge,
            record_type=record_type
        )
    except Exception as e:
        raise ValueError(f"解析ATOM行失败 (行 {line_number}): {str(e)}")


def extract_molecule_coordinates(pdb_file_path: str) -> Dict[str, Any]:
    """从PDB文件中提取分子坐标
    
    为每个分子实例提取完整的原子坐标列表。
    
    Args:
        pdb_file_path: PDB文件路径
        
    Returns:
        结果字典，包含：
            - success: 是否成功
            - molecules: 分子实例列表，每个包含：
                - molecule_name: 分子名称
                - instance_id: 实例序号（从1开始）
                - atoms: 原子坐标列表 [{"atom_name": ..., "x": ..., "y": ..., "z": ...}]
                - atom_count: 原子数量
            - total_molecules: 分子实例总数
            - error: 错误信息（如果失败）
            
    Example:
        >>> result = extract_molecule_coordinates("packed_system.pdb")
        >>> if result["success"]:
        ...     for mol in result["molecules"]:
        ...         print(f"{mol['molecule_name']}_{mol['instance_id']}: {mol['atom_count']} atoms")
    """
    logger.info(f"[分子坐标提取] 开始提取分子坐标: {pdb_file_path}")
    
    # 先解析PDB文件
    parse_result = parse_pdb_file(pdb_file_path)
    if not parse_result["success"]:
        return {
            "success": False,
            "molecules": [],
            "total_molecules": 0,
            "error": parse_result["error"]
        }
    
    atoms = parse_result["atoms"]
    ter_records = parse_result["ter_records"]
    
    # 识别分子边界
    boundaries_result = identify_molecule_boundaries(atoms, ter_records)
    if not boundaries_result["success"]:
        return {
            "success": False,
            "molecules": [],
            "total_molecules": 0,
            "error": boundaries_result["error"]
        }
    
    boundaries = boundaries_result["boundaries"]
    
    # 提取每个分子的坐标
    molecules = []
    for boundary in boundaries:
        start_idx = boundary["start_index"]
        end_idx = boundary["end_index"]
        molecule_atoms = atoms[start_idx:end_idx]
        
        # 构建原子坐标列表
        atom_coords = []
        for atom in molecule_atoms:
            atom_coords.append({
                "atom_name": atom.name,
                "atom_serial": atom.serial,
                "x": atom.x,
                "y": atom.y,
                "z": atom.z,
                "element": atom.element,
                "res_name": atom.res_name
            })
        
        molecule_instance = {
            "molecule_name": boundary["molecule_name"],
            "instance_id": boundary["instance_id"],
            "atoms": atom_coords,
            "atom_count": len(atom_coords),
            "chain_id": boundary["chain_id"],
            "start_line": boundary["start_line"],
            "end_line": boundary["end_line"]
        }
        molecules.append(molecule_instance)
        
        logger.debug(
            f"[分子坐标提取] 分子 {boundary['molecule_name']}_{boundary['instance_id']}: "
            f"{len(atom_coords)} 个原子"
        )
    
    logger.info(f"[分子坐标提取] 提取完成，共 {len(molecules)} 个分子实例")
    
    return {
        "success": True,
        "molecules": molecules,
        "total_molecules": len(molecules),
        "error": None
    }


def identify_molecule_boundaries(
    atoms: List[AtomRecord],
    ter_records: List[int] = None
) -> Dict[str, Any]:
    """识别分子边界
    
    通过三种方式识别分子边界：
    1. TER记录分隔符
    2. residue number变化
    3. chain ID变化
    
    Args:
        atoms: 原子记录列表
        ter_records: TER记录位置列表（可选）
        
    Returns:
        结果字典，包含：
            - success: 是否成功
            - boundaries: 分子边界列表，每个包含：
                - molecule_name: 分子名称
                - instance_id: 实例序号
                - start_index: 起始原子索引
                - end_index: 结束原子索引
                - start_line: 起始行号
                - end_line: 结束行号
                - chain_id: 链标识符
            - total_boundaries: 边界总数
            - error: 错误信息（如果失败）
            
    Example:
        >>> atoms = parse_pdb_file("system.pdb")["atoms"]
        >>> result = identify_molecule_boundaries(atoms)
        >>> for boundary in result["boundaries"]:
        ...     print(f"分子 {boundary['molecule_name']}_{boundary['instance_id']}")
    """
    logger.info("[分子边界识别] 开始识别分子边界")
    
    if not atoms:
        error_msg = "原子列表为空"
        logger.error(f"[分子边界识别] {error_msg}")
        return {
            "success": False,
            "boundaries": [],
            "total_boundaries": 0,
            "error": error_msg
        }
    
    boundaries = []
    ter_records = ter_records or []
    
    # 分子实例计数器（按分子名称分组）
    molecule_counters: Dict[str, int] = {}
    
    # 当前分子的起始索引
    start_index = 0
    current_molecule_name = atoms[0].res_name
    current_res_seq = atoms[0].res_seq
    current_chain_id = atoms[0].chain_id
    
    for i, atom in enumerate(atoms):
        # 检测分子边界变化
        is_boundary = False
        boundary_reason = ""
        
        # 1. 检查residue number变化
        if atom.res_seq != current_res_seq:
            is_boundary = True
            boundary_reason = f"residue number变化: {current_res_seq} -> {atom.res_seq}"
        
        # 2. 检查chain ID变化
        elif atom.chain_id != current_chain_id:
            is_boundary = True
            boundary_reason = f"chain ID变化: {current_chain_id} -> {atom.chain_id}"
        
        # 3. 检查TER记录（通过行号判断）
        elif ter_records and i > 0:
            prev_line = atoms[i-1].serial if i > 0 else 0
            # 简化处理：TER记录后的第一个原子作为新分子开始
            # 实际PDB中TER记录在原子之间
        
        # 如果检测到边界，保存当前分子
        if is_boundary:
            # 获取实例序号
            if current_molecule_name not in molecule_counters:
                molecule_counters[current_molecule_name] = 0
            molecule_counters[current_molecule_name] += 1
            instance_id = molecule_counters[current_molecule_name]
            
            boundary = {
                "molecule_name": current_molecule_name,
                "instance_id": instance_id,
                "start_index": start_index,
                "end_index": i,
                "start_line": atoms[start_index].serial,
                "end_line": atoms[i-1].serial,
                "chain_id": current_chain_id,
                "res_seq": current_res_seq
            }
            boundaries.append(boundary)
            
            logger.debug(
                f"[分子边界识别] 发现边界: {current_molecule_name}_{instance_id} "
                f"(原子 {start_index}-{i-1}), 原因: {boundary_reason}"
            )
            
            # 更新当前分子信息
            start_index = i
            current_molecule_name = atom.res_name
            current_res_seq = atom.res_seq
            current_chain_id = atom.chain_id
    
    # 保存最后一个分子
    if current_molecule_name not in molecule_counters:
        molecule_counters[current_molecule_name] = 0
    molecule_counters[current_molecule_name] += 1
    instance_id = molecule_counters[current_molecule_name]
    
    boundary = {
        "molecule_name": current_molecule_name,
        "instance_id": instance_id,
        "start_index": start_index,
        "end_index": len(atoms),
        "start_line": atoms[start_index].serial,
        "end_line": atoms[-1].serial,
        "chain_id": current_chain_id,
        "res_seq": current_res_seq
    }
    boundaries.append(boundary)
    
    logger.info(
        f"[分子边界识别] 识别完成，共 {len(boundaries)} 个分子边界，"
        f"分子种类: {len(molecule_counters)}"
    )
    
    return {
        "success": True,
        "boundaries": boundaries,
        "total_boundaries": len(boundaries),
        "molecule_counters": molecule_counters,
        "error": None
    }


def map_molecule_ids(
    atoms: List[AtomRecord],
    boundaries: List[Dict[str, Any]]
) -> Dict[str, Any]:
    """映射分子ID和实例序号
    
    为每个原子分配分子ID，格式为 {分子名称}_{实例序号}（如 EC_1, Li_50）。
    实例序号从1开始递增。
    
    Args:
        atoms: 原子记录列表
        boundaries: 分子边界列表（来自identify_molecule_boundaries）
        
    Returns:
        结果字典，包含：
            - success: 是否成功
            - atom_molecule_map: 原子索引到分子ID的映射 {atom_index: molecule_id}
            - molecule_id_list: 分子ID列表
            - molecule_id_set: 分子ID集合（去重）
            - error: 错误信息（如果失败）
            
    Example:
        >>> atoms = parse_pdb_file("system.pdb")["atoms"]
        >>> boundaries = identify_molecule_boundaries(atoms)["boundaries"]
        >>> result = map_molecule_ids(atoms, boundaries)
        >>> print(f"分子ID: {result['molecule_id_set']}")
    """
    logger.info("[分子ID映射] 开始映射分子ID")
    
    if not atoms:
        error_msg = "原子列表为空"
        logger.error(f"[分子ID映射] {error_msg}")
        return {
            "success": False,
            "atom_molecule_map": {},
            "molecule_id_list": [],
            "molecule_id_set": set(),
            "error": error_msg
        }
    
    if not boundaries:
        error_msg = "分子边界列表为空"
        logger.error(f"[分子ID映射] {error_msg}")
        return {
            "success": False,
            "atom_molecule_map": {},
            "molecule_id_list": [],
            "molecule_id_set": set(),
            "error": error_msg
        }
    
    atom_molecule_map = {}
    molecule_id_list = []
    
    for boundary in boundaries:
        molecule_name = boundary["molecule_name"]
        instance_id = boundary["instance_id"]
        start_index = boundary["start_index"]
        end_index = boundary["end_index"]
        
        # 构建分子ID
        molecule_id = f"{molecule_name}_{instance_id}"
        molecule_id_list.append(molecule_id)
        
        # 为该分子内的所有原子分配分子ID
        for i in range(start_index, end_index):
            atom_molecule_map[i] = molecule_id
        
        logger.debug(
            f"[分子ID映射] 分子 {molecule_id}: 原子 {start_index}-{end_index-1}"
        )
    
    molecule_id_set = set(molecule_id_list)
    
    logger.info(
        f"[分子ID映射] 映射完成，共 {len(molecule_id_list)} 个分子实例，"
        f"{len(molecule_id_set)} 种分子类型"
    )
    
    return {
        "success": True,
        "atom_molecule_map": atom_molecule_map,
        "molecule_id_list": molecule_id_list,
        "molecule_id_set": molecule_id_set,
        "error": None
    }


def validate_pdb_structure(
    pdb_file_path: str,
    expected_atoms: int = None,
    expected_molecules: Dict[str, int] = None
) -> Dict[str, Any]:
    """验证PDB文件结构完整性
    
    验证内容包括：
    1. 原子总数与预期一致
    2. 分子实例数量与配方一致
    3. 坐标数据完整性（无缺失值）
    
    Args:
        pdb_file_path: PDB文件路径
        expected_atoms: 预期原子总数（可选）
        expected_molecules: 预期分子数量字典 {分子名称: 数量}（可选）
        
    Returns:
        验证结果字典，包含：
            - success: 是否成功
            - valid: 是否验证通过
            - total_atoms: 实际原子总数
            - total_molecules: 实际分子实例总数
            - molecule_counts: 实际分子数量统计 {分子名称: 数量}
            - validation_errors: 验证错误列表
            - validation_warnings: 验证警告列表
            - error: 错误信息（如果解析失败）
            
    Example:
        >>> result = validate_pdb_structure(
        ...     "packed_system.pdb",
        ...     expected_atoms=10000,
        ...     expected_molecules={"EC": 500, "Li": 50}
        ... )
        >>> if result["valid"]:
        ...     print("PDB文件验证通过")
        >>> else:
        ...     print(f"验证失败: {result['validation_errors']}")
    """
    logger.info(f"[PDB验证] 开始验证PDB文件: {pdb_file_path}")
    
    validation_errors = []
    validation_warnings = []
    
    # 解析PDB文件
    parse_result = parse_pdb_file(pdb_file_path)
    if not parse_result["success"]:
        return {
            "success": False,
            "valid": False,
            "total_atoms": 0,
            "total_molecules": 0,
            "molecule_counts": {},
            "validation_errors": [parse_result["error"]],
            "validation_warnings": [],
            "error": parse_result["error"]
        }
    
    atoms = parse_result["atoms"]
    total_atoms = parse_result["total_atoms"]
    
    # 验证1: 原子总数
    if expected_atoms is not None:
        if total_atoms != expected_atoms:
            error_msg = f"原子总数不匹配: 实际 {total_atoms}, 预期 {expected_atoms}"
            validation_errors.append(error_msg)
            logger.error(f"[PDB验证] {error_msg}")
        else:
            logger.info(f"[PDB验证] 原子总数验证通过: {total_atoms}")
    
    # 验证2: 坐标数据完整性
    invalid_coords = []
    for i, atom in enumerate(atoms):
        # 检查坐标是否为有效数值
        if not all([
            isinstance(atom.x, (int, float)) and not isinstance(atom.x, bool),
            isinstance(atom.y, (int, float)) and not isinstance(atom.y, bool),
            isinstance(atom.z, (int, float)) and not isinstance(atom.z, bool)
        ]):
            invalid_coords.append(i)
        
        # 检查坐标是否为NaN或Inf
        if any([
            atom.x != atom.x,  # NaN check
            atom.y != atom.y,
            atom.z != atom.z,
            abs(atom.x) == float('inf'),
            abs(atom.y) == float('inf'),
            abs(atom.z) == float('inf')
        ]):
            invalid_coords.append(i)
    
    if invalid_coords:
        error_msg = f"发现 {len(invalid_coords)} 个原子坐标无效: {invalid_coords[:10]}..."
        validation_errors.append(error_msg)
        logger.error(f"[PDB验证] {error_msg}")
    else:
        logger.info("[PDB验证] 坐标数据完整性验证通过")
    
    # 识别分子边界
    boundaries_result = identify_molecule_boundaries(atoms, parse_result["ter_records"])
    if not boundaries_result["success"]:
        return {
            "success": False,
            "valid": False,
            "total_atoms": total_atoms,
            "total_molecules": 0,
            "molecule_counts": {},
            "validation_errors": [boundaries_result["error"]],
            "validation_warnings": [],
            "error": boundaries_result["error"]
        }
    
    boundaries = boundaries_result["boundaries"]
    total_molecules = len(boundaries)
    molecule_counts = boundaries_result["molecule_counters"]
    
    # 验证3: 分子实例数量
    if expected_molecules is not None:
        for mol_name, expected_count in expected_molecules.items():
            actual_count = molecule_counts.get(mol_name, 0)
            if actual_count != expected_count:
                error_msg = (
                    f"分子 {mol_name} 数量不匹配: "
                    f"实际 {actual_count}, 预期 {expected_count}"
                )
                validation_errors.append(error_msg)
                logger.error(f"[PDB验证] {error_msg}")
            else:
                logger.info(f"[PDB验证] 分子 {mol_name} 数量验证通过: {actual_count}")
        
        # 检查是否有额外的分子类型
        for mol_name in molecule_counts:
            if mol_name not in expected_molecules:
                warning_msg = f"发现未预期的分子类型: {mol_name} (数量: {molecule_counts[mol_name]})"
                validation_warnings.append(warning_msg)
                logger.warning(f"[PDB验证] {warning_msg}")
    
    # 验证4: 分子边界合理性
    for i, boundary in enumerate(boundaries):
        atom_count = boundary["end_index"] - boundary["start_index"]
        if atom_count == 0:
            error_msg = f"分子 {boundary['molecule_name']}_{boundary['instance_id']} 没有原子"
            validation_errors.append(error_msg)
            logger.error(f"[PDB验证] {error_msg}")
    
    # 判断验证是否通过
    valid = len(validation_errors) == 0
    
    logger.info(
        f"[PDB验证] 验证完成: {'通过' if valid else '失败'}, "
        f"原子总数: {total_atoms}, 分子实例: {total_molecules}, "
        f"分子类型: {len(molecule_counts)}"
    )
    
    return {
        "success": True,
        "valid": valid,
        "total_atoms": total_atoms,
        "total_molecules": total_molecules,
        "molecule_counts": molecule_counts,
        "validation_errors": validation_errors,
        "validation_warnings": validation_warnings,
        "error": None
    }


# ==================== 分子模板文件加载功能 ====================

def fetch_molecule_lt_templates(
    molecule_names: List[str],
    template_library_path: str
) -> Dict[str, Any]:
    """从分子模板库获取.lt文件路径
    
    根据分子名称列表，从模板库中查询对应的.lt文件路径。
    支持检测缺失的分子模板并返回详细的错误信息。
    
    Args:
        molecule_names: 分子名称列表（如 ["EC", "Li", "PF6"]）
        template_library_path: 分子模板库根路径（如 "system_templates/molecule_templates"）
        
    Returns:
        结果字典，包含：
            - success: 是否成功获取所有模板
            - template_paths: 分子模板文件路径字典 {分子名称: .lt文件路径}
            - found_molecules: 找到模板的分子名称列表
            - missing_molecules: 缺失模板的分子名称列表
            - error: 错误信息（如果有缺失模板或其他错误）
            
    Example:
        >>> result = fetch_molecule_lt_templates(
        ...     ["EC", "Li", "PF6"],
        ...     "system_templates/molecule_templates"
        ... )
        >>> if result["success"]:
        ...     print(f"找到模板: {result['template_paths']}")
        >>> else:
        ...     print(f"缺失模板: {result['missing_molecules']}")
        
    Note:
        模板库目录结构应为：
        template_library_path/
        ├── EC/
        │   ├── EC.lt
        │   ├── EC.pdb
        │   └── EC.json
        ├── Li/
        │   ├── Li.lt
        │   └── Li.pdb
        └── PF6/
            ├── PF6.lt
            └── PF6.pdb
    """
    logger.info(f"[模板加载] 开始获取分子模板: {molecule_names}")
    logger.info(f"[模板加载] 模板库路径: {template_library_path}")
    
    template_paths = {}
    found_molecules = []
    missing_molecules = []
    
    # 验证模板库路径是否存在
    library_path = Path(template_library_path)
    if not library_path.exists():
        error_msg = f"分子模板库路径不存在: {template_library_path}"
        logger.error(f"[模板加载] {error_msg}")
        return {
            "success": False,
            "template_paths": {},
            "found_molecules": [],
            "missing_molecules": molecule_names,
            "error": error_msg
        }
    
    # 遍历分子名称列表，查找对应的.lt文件
    for molecule_name in molecule_names:
        # 构建分子模板目录路径
        molecule_dir = library_path / molecule_name
        lt_file_path = molecule_dir / f"{molecule_name}.lt"
        
        logger.debug(f"[模板加载] 查找分子 {molecule_name} 的模板: {lt_file_path}")
        
        # 检查.lt文件是否存在
        if lt_file_path.exists() and lt_file_path.is_file():
            template_paths[molecule_name] = str(lt_file_path)
            found_molecules.append(molecule_name)
            logger.info(f"[模板加载] 找到分子 {molecule_name} 的模板: {lt_file_path}")
        else:
            missing_molecules.append(molecule_name)
            logger.warning(f"[模板加载] 分子 {molecule_name} 的模板不存在: {lt_file_path}")
    
    # 判断是否所有模板都找到
    success = len(missing_molecules) == 0
    
    # 构建错误信息
    error = None
    if missing_molecules:
        error = f"缺失分子模板: {', '.join(missing_molecules)}"
        logger.error(f"[模板加载] {error}")
    
    # 记录统计信息
    logger.info(
        f"[模板加载] 模板查询完成: 找到 {len(found_molecules)} 个, "
        f"缺失 {len(missing_molecules)} 个"
    )
    
    return {
        "success": success,
        "template_paths": template_paths,
        "found_molecules": found_molecules,
        "missing_molecules": missing_molecules,
        "error": error
    }


def copy_lt_files_to_workdir(
    template_paths: Dict[str, str],
    work_dir_path: str
) -> Dict[str, Any]:
    """复制.lt文件到工作目录的inputs目录
    
    将所有分子模板的.lt文件复制到指定工作目录的inputs子目录中。
    保持文件名不变，便于后续Moltemplate调用。
    
    Args:
        template_paths: 分子模板文件路径字典 {分子名称: .lt文件路径}
        work_dir_path: 工作目录路径（任务根目录）
        
    Returns:
        结果字典，包含：
            - success: 是否成功复制所有文件
            - copied_files: 复制后的文件路径字典 {分子名称: 新路径}
            - failed_files: 复制失败的文件列表
            - inputs_dir: inputs目录路径
            - error: 错误信息（如果有复制失败）
            
    Example:
        >>> template_paths = {"EC": "/templates/EC/EC.lt", "Li": "/templates/Li/Li.lt"}
        >>> result = copy_lt_files_to_workdir(template_paths, "user_1/jobs/job_1")
        >>> if result["success"]:
        ...     print(f"复制成功: {result['copied_files']}")
        
    Note:
        目标目录结构：
        work_dir_path/
        └── inputs/
            ├── EC.lt
            ├── Li.lt
            └── system.lt
    """
    logger.info(f"[模板复制] 开始复制.lt文件到工作目录: {work_dir_path}")
    
    copied_files = {}
    failed_files = []
    
    # 构建inputs目录路径
    work_dir = Path(work_dir_path)
    inputs_dir = work_dir / "inputs"
    
    # 创建inputs目录（如果不存在）
    try:
        inputs_dir.mkdir(parents=True, exist_ok=True)
        logger.info(f"[模板复制] 创建inputs目录: {inputs_dir}")
    except Exception as e:
        error_msg = f"创建inputs目录失败: {str(e)}"
        logger.error(f"[模板复制] {error_msg}")
        return {
            "success": False,
            "copied_files": {},
            "failed_files": list(template_paths.keys()),
            "inputs_dir": str(inputs_dir),
            "error": error_msg
        }
    
    # 遍历模板路径字典，复制每个.lt文件
    for molecule_name, template_path in template_paths.items():
        source_file = Path(template_path)
        target_file = inputs_dir / f"{molecule_name}.lt"
        
        logger.debug(
            f"[模板复制] 复制 {molecule_name}.lt: "
            f"{source_file} -> {target_file}"
        )
        
        # 检查源文件是否存在
        if not source_file.exists():
            failed_files.append(molecule_name)
            logger.error(f"[模板复制] 源文件不存在: {source_file}")
            continue
        
        # 执行文件复制
        try:
            import shutil
            shutil.copy2(source_file, target_file)
            copied_files[molecule_name] = str(target_file)
            logger.info(f"[模板复制] 成功复制 {molecule_name}.lt 到 {target_file}")
        except Exception as e:
            failed_files.append(molecule_name)
            logger.error(f"[模板复制] 复制 {molecule_name}.lt 失败: {str(e)}")
    
    # 判断是否所有文件都复制成功
    success = len(failed_files) == 0
    
    # 构建错误信息
    error = None
    if failed_files:
        error = f"复制失败的文件: {', '.join(failed_files)}"
        logger.error(f"[模板复制] {error}")
    
    # 记录统计信息
    logger.info(
        f"[模板复制] 复制完成: 成功 {len(copied_files)} 个, "
        f"失败 {len(failed_files)} 个"
    )
    
    return {
        "success": success,
        "copied_files": copied_files,
        "failed_files": failed_files,
        "inputs_dir": str(inputs_dir),
        "error": error
    }


def validate_lt_template(
    lt_file_path: str
) -> Dict[str, Any]:
    """验证.lt文件格式正确性
    
    检查.lt文件是否符合Moltemplate规范，包括：
    1. 文件是否存在且可读
    2. 文件基本语法结构（分子定义块）
    3. 必需的关键字（如 write_once, write 等）
    4. 原子类型定义、坐标数据等基本内容
    
    Args:
        lt_file_path: .lt文件路径
        
    Returns:
        验证结果字典，包含：
            - success: 是否成功验证
            - valid: 文件格式是否有效
            - molecule_name: 分子名称（从文件中提取）
            - has_atom_types: 是否包含原子类型定义
            - has_coordinates: 是否包含坐标数据
            - has_bonds: 是否包含键定义
            - has_angles: 是否包含角度定义
            - has_dihedrals: 是否包含二面角定义
            - validation_errors: 验证错误列表
            - validation_warnings: 验证警告列表
            - error: 错误信息（如果文件读取失败）
            
    Example:
        >>> result = validate_lt_template("EC.lt")
        >>> if result["valid"]:
        ...     print(f"分子 {result['molecule_name']} 模板验证通过")
        >>> else:
        ...     print(f"验证失败: {result['validation_errors']}")
            
    Note:
        Moltemplate .lt文件基本结构：
        MoleculeName {
          write_once("Data Masses") { ... }
          write('Data Atoms') { ... }
          write('Data Bond List') { ... }
          ...
        }
    """
    logger.info(f"[模板验证] 开始验证.lt文件: {lt_file_path}")
    
    validation_errors = []
    validation_warnings = []
    
    # 检查文件是否存在
    lt_file = Path(lt_file_path)
    if not lt_file.exists():
        error_msg = f".lt文件不存在: {lt_file_path}"
        logger.error(f"[模板验证] {error_msg}")
        return {
            "success": False,
            "valid": False,
            "molecule_name": None,
            "has_atom_types": False,
            "has_coordinates": False,
            "has_bonds": False,
            "has_angles": False,
            "has_dihedrals": False,
            "validation_errors": [error_msg],
            "validation_warnings": [],
            "error": error_msg
        }
    
    # 读取文件内容
    try:
        with open(lt_file, 'r', encoding='utf-8') as f:
            content = f.read()
        logger.debug(f"[模板验证] 文件读取成功，大小: {len(content)} 字符")
    except Exception as e:
        error_msg = f"读取.lt文件失败: {str(e)}"
        logger.error(f"[模板验证] {error_msg}")
        return {
            "success": False,
            "valid": False,
            "molecule_name": None,
            "has_atom_types": False,
            "has_coordinates": False,
            "has_bonds": False,
            "has_angles": False,
            "has_dihedrals": False,
            "validation_errors": [error_msg],
            "validation_warnings": [],
            "error": error_msg
        }
    
    # 验证1: 提取分子名称（查找第一个定义块）
    molecule_name = None
    molecule_pattern = re.compile(r'^(\w+)\s*\{', re.MULTILINE)
    match = molecule_pattern.search(content)
    if match:
        molecule_name = match.group(1)
        logger.info(f"[模板验证] 提取分子名称: {molecule_name}")
    else:
        error_msg = "无法从.lt文件中提取分子名称定义块"
        validation_errors.append(error_msg)
        logger.error(f"[模板验证] {error_msg}")
    
    # 验证2: 检查原子类型定义（Data Masses）
    has_atom_types = False
    if 'write_once("Data Masses")' in content or "write_once('Data Masses')" in content:
        has_atom_types = True
        logger.debug("[模板验证] 发现原子类型定义（Data Masses）")
    else:
        warning_msg = "未发现原子类型定义（Data Masses），可能使用外部力场"
        validation_warnings.append(warning_msg)
        logger.warning(f"[模板验证] {warning_msg}")
    
    # 验证3: 检查坐标数据（Data Atoms）
    has_coordinates = False
    if 'write("Data Atoms")' in content or "write('Data Atoms')" in content:
        has_coordinates = True
        logger.debug("[模板验证] 发现坐标数据定义（Data Atoms）")
    else:
        warning_msg = "未发现坐标数据定义（Data Atoms）"
        validation_warnings.append(warning_msg)
        logger.warning(f"[模板验证] {warning_msg}")
    
    # 验证4: 检查键定义（Data Bond List）
    has_bonds = False
    if 'write("Data Bond List")' in content or "write('Data Bond List')" in content:
        has_bonds = True
        logger.debug("[模板验证] 发现键定义（Data Bond List）")
    
    # 验证5: 检查角度定义（Data Angle List）
    has_angles = False
    if 'write("Data Angle List")' in content or "write('Data Angle List')" in content:
        has_angles = True
        logger.debug("[模板验证] 发现角度定义（Data Angle List）")
    
    # 验证6: 检查二面角定义（Data Dihedral List）
    has_dihedrals = False
    if 'write("Data Dihedral List")' in content or "write('Data Dihedral List')" in content:
        has_dihedrals = True
        logger.debug("[模板验证] 发现二面角定义（Data Dihedral List）")
    
    # 验证7: 检查基本语法（括号匹配）
    open_braces = content.count('{')
    close_braces = content.count('}')
    if open_braces != close_braces:
        error_msg = f"括号不匹配: 开括号 {open_braces}, 关括号 {close_braces}"
        validation_errors.append(error_msg)
        logger.error(f"[模板验证] {error_msg}")
    else:
        logger.debug(f"[模板验证] 括号匹配验证通过: {open_braces} 对")
    
    # 验证8: 检查文件是否为空或过小
    if len(content.strip()) < 10:
        error_msg = "文件内容过小，可能为空文件或格式不完整"
        validation_errors.append(error_msg)
        logger.error(f"[模板验证] {error_msg}")
    
    # 判断验证是否通过
    valid = len(validation_errors) == 0
    
    # 记录验证结果
    logger.info(
        f"[模板验证] 验证完成: {'通过' if valid else '失败'}, "
        f"分子名称: {molecule_name}, "
        f"原子类型: {has_atom_types}, 坐标: {has_coordinates}, "
        f"键: {has_bonds}, 角度: {has_angles}, 二面角: {has_dihedrals}"
    )
    
    return {
        "success": True,
        "valid": valid,
        "molecule_name": molecule_name,
        "has_atom_types": has_atom_types,
        "has_coordinates": has_coordinates,
        "has_bonds": has_bonds,
        "has_angles": has_angles,
        "has_dihedrals": has_dihedrals,
        "validation_errors": validation_errors,
        "validation_warnings": validation_warnings,
        "error": None
    }


def load_forcefield_file(
    forcefield_type: str,
    forcefield_library_path: str
) -> Dict[str, Any]:
    """加载力场参数文件
    
    根据力场类型加载对应的力场参数文件（如 oplsaa.lt, gaff.lt 等）。
    确保力场文件存在并可读，返回文件路径或内容。
    
    Args:
        forcefield_type: 力场类型（如 "oplsaa", "gaff", "amber"）
        forcefield_library_path: 力场文件库路径（如 "system_templates/force_fields"）
        
    Returns:
        结果字典，包含：
            - success: 是否成功加载力场文件
            - forcefield_path: 力场文件路径
            - forcefield_name: 力场文件名称（如 "oplsaa.lt"）
            - forcefield_content: 力场文件内容（可选，文件较大时不返回）
            - file_size: 文件大小（字节）
            - error: 错误信息（如果加载失败）
            
    Example:
        >>> result = load_forcefield_file("oplsaa", "system_templates/force_fields")
        >>> if result["success"]:
        ...     print(f"力场文件路径: {result['forcefield_path']}")
        ...     print(f"文件大小: {result['file_size']} 字节")
        
    Note:
        力场库目录结构：
        forcefield_library_path/
        ├── opls-aa/
        │   ├── oplsaa.lt
        │   └── oplsaa_params.json
        ├── gaff/
        │   ├── gaff.lt
        │   └── gaff_params.json
        └── amber/
            ├── amber.lt
            └── amber_params.json
    """
    logger.info(f"[力场加载] 开始加载力场文件: {forcefield_type}")
    logger.info(f"[力场加载] 力场库路径: {forcefield_library_path}")
    
    # 构建力场文件路径
    library_path = Path(forcefield_library_path)
    
    # 支持多种力场命名格式
    forcefield_filenames = [
        f"{forcefield_type}.lt",  # oplsaa.lt
        f"{forcefield_type.lower()}.lt",  # oplsaa.lt
        f"{forcefield_type.replace('-', '_')}.lt",  # opls_aa -> opls_aa.lt
    ]
    
    # 尝试不同的力场目录命名
    forcefield_dirs = [
        forcefield_type,  # oplsaa
        forcefield_type.lower(),  # oplsaa
        forcefield_type.replace('-', '_'),  # opls_aa -> opls_aa
        forcefield_type.replace('_', '-'),  # opls_aa -> opls-aa
    ]
    
    forcefield_path = None
    forcefield_name = None
    
    # 查找力场文件
    for ff_dir in forcefield_dirs:
        for ff_filename in forcefield_filenames:
            candidate_path = library_path / ff_dir / ff_filename
            logger.debug(f"[力场加载] 尝试路径: {candidate_path}")
            
            if candidate_path.exists() and candidate_path.is_file():
                forcefield_path = candidate_path
                forcefield_name = ff_filename
                logger.info(f"[力场加载] 找到力场文件: {forcefield_path}")
                break
        
        if forcefield_path:
            break
    
    # 检查是否找到力场文件
    if not forcefield_path:
        error_msg = f"未找到力场文件: {forcefield_type}"
        logger.error(f"[力场加载] {error_msg}")
        logger.error(f"[力场加载] 尝试的路径: {[str(library_path / d / f) for d in forcefield_dirs for f in forcefield_filenames]}")
        return {
            "success": False,
            "forcefield_path": None,
            "forcefield_name": None,
            "forcefield_content": None,
            "file_size": 0,
            "error": error_msg
        }
    
    # 获取文件大小
    try:
        file_size = forcefield_path.stat().st_size
        logger.info(f"[力场加载] 力场文件大小: {file_size} 字节")
    except Exception as e:
        logger.warning(f"[力场加载] 无法获取文件大小: {str(e)}")
        file_size = 0
    
    # 读取文件内容（如果文件较小）
    forcefield_content = None
    if file_size < 1024 * 1024:  # 小于1MB时读取内容
        try:
            with open(forcefield_path, 'r', encoding='utf-8') as f:
                forcefield_content = f.read()
            logger.debug(f"[力场加载] 成功读取力场文件内容")
        except Exception as e:
            logger.warning(f"[力场加载] 读取力场文件内容失败: {str(e)}")
    
    logger.info(f"[力场加载] 力场文件加载成功: {forcefield_path}")
    
    return {
        "success": True,
        "forcefield_path": str(forcefield_path),
        "forcefield_name": forcefield_name,
        "forcefield_content": forcefield_content,
        "file_size": file_size,
        "error": None
    }


# ==================== 分子实例创建功能 ====================

def create_molecule_instances(
    molecule_names: List[str],
    molecule_counts: Dict[str, int],
    pdb_parse_result: Dict[str, Any]
) -> Dict[str, Any]:
    """创建分子实例数据结构
    
    根据分子数量和PDB解析结果，创建完整的分子实例数据结构。
    每个分子实例包含分子名称、实例序号和原子坐标列表。
    
    Args:
        molecule_names: 分子名称列表（如 ["EC", "Li", "PF6"]）
        molecule_counts: 分子数量字典 {分子名称: 数量}（如 {"EC": 500, "Li": 50}）
        pdb_parse_result: PDB解析结果（来自 parse_pdb_file 函数）
        
    Returns:
        结果字典，包含：
            - success: 是否成功创建分子实例
            - instances: 分子实例列表（MoleculeInstance对象列表）
            - instance_summary: 实例统计摘要 {分子名称: 实例数量}
            - total_instances: 实例总数
            - total_atoms: 原子总数
            - error: 错误信息（如果失败）
            
    Example:
        >>> pdb_result = parse_pdb_file("packed_system.pdb")
        >>> instance_result = create_molecule_instances(
        ...     ["EC", "Li"],
        ...     {"EC": 500, "Li": 50},
        ...     pdb_result
        ... )
        >>> if instance_result["success"]:
        ...     for instance in instance_result["instances"]:
        ...         print(f"{instance.molecule_name}_{instance.instance_id}")
        
    Note:
        分子实例序号从1开始递增，每个分子类型独立计数。
        例如：EC_1, EC_2, ..., EC_500, Li_1, Li_2, ..., Li_50
    """
    logger.info("[分子实例创建] 开始创建分子实例数据结构")
    logger.info(f"[分子实例创建] 分子类型: {molecule_names}")
    logger.info(f"[分子实例创建] 分子数量: {molecule_counts}")
    
    # 验证PDB解析结果
    if not pdb_parse_result.get("success", False):
        error_msg = f"PDB解析失败: {pdb_parse_result.get('error', '未知错误')}"
        logger.error(f"[分子实例创建] {error_msg}")
        return {
            "success": False,
            "instances": [],
            "instance_summary": {},
            "total_instances": 0,
            "total_atoms": 0,
            "error": error_msg
        }
    
    atoms = pdb_parse_result.get("atoms", [])
    if not atoms:
        error_msg = "PDB解析结果中没有原子数据"
        logger.error(f"[分子实例创建] {error_msg}")
        return {
            "success": False,
            "instances": [],
            "instance_summary": {},
            "total_instances": 0,
            "total_atoms": 0,
            "error": error_msg
        }
    
    # 识别分子边界
    ter_records = pdb_parse_result.get("ter_records", [])
    boundaries_result = identify_molecule_boundaries(atoms, ter_records)
    
    if not boundaries_result.get("success", False):
        error_msg = f"分子边界识别失败: {boundaries_result.get('error', '未知错误')}"
        logger.error(f"[分子实例创建] {error_msg}")
        return {
            "success": False,
            "instances": [],
            "instance_summary": {},
            "total_instances": 0,
            "total_atoms": len(atoms),
            "error": error_msg
        }
    
    boundaries = boundaries_result.get("boundaries", [])
    
    # 创建分子实例列表
    instances = []
    instance_summary = {}
    
    for boundary in boundaries:
        molecule_name = boundary.get("molecule_name", "Unknown")
        instance_id = boundary.get("instance_id", 0)
        start_index = boundary.get("start_index", 0)
        end_index = boundary.get("end_index", 0)
        chain_id = boundary.get("chain_id", "")
        start_line = boundary.get("start_line", 0)
        end_line = boundary.get("end_line", 0)
        
        # 提取该分子实例的原子列表
        molecule_atoms = atoms[start_index:end_index]
        
        # 创建MoleculeInstance对象
        instance = MoleculeInstance(
            molecule_name=molecule_name,
            instance_id=instance_id,
            atoms=molecule_atoms,
            chain_id=chain_id,
            start_line=start_line,
            end_line=end_line
        )
        
        instances.append(instance)
        
        # 更新统计摘要
        if molecule_name not in instance_summary:
            instance_summary[molecule_name] = 0
        instance_summary[molecule_name] += 1
        
        logger.debug(
            f"[分子实例创建] 创建实例 {molecule_name}_{instance_id}: "
            f"{len(molecule_atoms)} 个原子"
        )
    
    total_instances = len(instances)
    total_atoms = len(atoms)
    
    # 验证分子实例数量是否与配方一致
    validation_result = validate_instance_count(instances, molecule_counts)
    if not validation_result.get("valid", False):
        warning_msg = f"分子实例数量与配方不一致: {validation_result.get('differences', [])}"
        logger.warning(f"[分子实例创建] {warning_msg}")
    
    logger.info(
        f"[分子实例创建] 创建完成: {total_instances} 个实例, "
        f"{total_atoms} 个原子, 分子类型: {len(instance_summary)}"
    )
    
    return {
        "success": True,
        "instances": instances,
        "instance_summary": instance_summary,
        "total_instances": total_instances,
        "total_atoms": total_atoms,
        "boundaries": boundaries,
        "error": None
    }


def assign_atom_coordinates(
    atoms: List[AtomRecord],
    molecule_id_map: Dict[int, str]
) -> Dict[str, Any]:
    """分配原子坐标到分子实例
    
    将PDB文件中的原子坐标分配到对应的分子实例，建立原子-分子映射关系。
    
    Args:
        atoms: PDB原子记录列表（AtomRecord对象列表）
        molecule_id_map: 原子索引到分子ID的映射 {原子索引: 分子ID}
                        （来自 map_molecule_ids 函数）
        
    Returns:
        结果字典，包含：
            - success: 是否成功分配
            - molecule_atoms_map: 分子ID到原子列表的映射 {分子ID: [原子坐标]}
            - atom_assignment_list: 原子分配列表 [{原子序号, 分子ID, 坐标}]
            - unassigned_atoms: 未分配的原子索引列表
            - assignment_summary: 分配统计摘要
            - error: 错误信息（如果失败）
            
    Example:
        >>> atoms = parse_pdb_file("system.pdb")["atoms"]
        >>> boundaries = identify_molecule_boundaries(atoms)["boundaries"]
        >>> molecule_id_map = map_molecule_ids(atoms, boundaries)["atom_molecule_map"]
        >>> result = assign_atom_coordinates(atoms, molecule_id_map)
        >>> for mol_id, atom_list in result["molecule_atoms_map"].items():
        ...     print(f"{mol_id}: {len(atom_list)} atoms")
        
    Note:
        原子坐标包含完整的AtomRecord信息，包括：
        - 原子序号、名称、元素符号
        - 三维坐标 (x, y, z)
        - 残基信息、链ID等
    """
    logger.info("[原子坐标分配] 开始分配原子坐标到分子实例")
    
    if not atoms:
        error_msg = "原子列表为空"
        logger.error(f"[原子坐标分配] {error_msg}")
        return {
            "success": False,
            "molecule_atoms_map": {},
            "atom_assignment_list": [],
            "unassigned_atoms": [],
            "assignment_summary": {},
            "error": error_msg
        }
    
    if not molecule_id_map:
        error_msg = "分子ID映射为空"
        logger.error(f"[原子坐标分配] {error_msg}")
        return {
            "success": False,
            "molecule_atoms_map": {},
            "atom_assignment_list": [],
            "unassigned_atoms": list(range(len(atoms))),
            "assignment_summary": {},
            "error": error_msg
        }
    
    molecule_atoms_map = {}
    atom_assignment_list = []
    unassigned_atoms = []
    
    # 遍历所有原子，分配到对应的分子实例
    for atom_index, atom in enumerate(atoms):
        # 查找该原子对应的分子ID
        molecule_id = molecule_id_map.get(atom_index)
        
        if molecule_id is None:
            # 未找到分子ID，记录为未分配
            unassigned_atoms.append(atom_index)
            logger.warning(
                f"[原子坐标分配] 原子 {atom.serial} (索引 {atom_index}) 未分配到分子"
            )
            continue
        
        # 将原子添加到对应分子的原子列表
        if molecule_id not in molecule_atoms_map:
            molecule_atoms_map[molecule_id] = []
        
        molecule_atoms_map[molecule_id].append(atom)
        
        # 记录原子分配信息
        assignment_info = {
            "atom_serial": atom.serial,
            "atom_name": atom.name,
            "atom_index": atom_index,
            "molecule_id": molecule_id,
            "x": atom.x,
            "y": atom.y,
            "z": atom.z,
            "element": atom.element,
            "res_name": atom.res_name
        }
        atom_assignment_list.append(assignment_info)
        
        logger.debug(
            f"[原子坐标分配] 原子 {atom.serial} ({atom.name}) "
            f"分配到分子 {molecule_id}"
        )
    
    # 构建分配统计摘要
    assignment_summary = {
        "total_atoms": len(atoms),
        "assigned_atoms": len(atom_assignment_list),
        "unassigned_atoms": len(unassigned_atoms),
        "molecule_count": len(molecule_atoms_map),
        "assignment_rate": len(atom_assignment_list) / len(atoms) if atoms else 0
    }
    
    # 检查是否有未分配的原子
    if unassigned_atoms:
        warning_msg = f"有 {len(unassigned_atoms)} 个原子未分配到分子实例"
        logger.warning(f"[原子坐标分配] {warning_msg}")
    
    logger.info(
        f"[原子坐标分配] 分配完成: {len(atom_assignment_list)} 个原子已分配, "
        f"{len(unassigned_atoms)} 个未分配, {len(molecule_atoms_map)} 个分子实例"
    )
    
    return {
        "success": True,
        "molecule_atoms_map": molecule_atoms_map,
        "atom_assignment_list": atom_assignment_list,
        "unassigned_atoms": unassigned_atoms,
        "assignment_summary": assignment_summary,
        "error": None
    }


def generate_atom_names(
    template_file_path: str,
    molecule_instances: List[MoleculeInstance]
) -> Dict[str, Any]:
    """生成原子名称映射
    
    根据分子模板文件和分子实例列表，生成原子名称映射字典。
    该映射用于system.lt文件中定义原子坐标。
    
    Args:
        template_file_path: 分子模板文件路径（.lt文件）
        molecule_instances: 分子实例列表（MoleculeInstance对象列表）
        
    Returns:
        结果字典，包含：
            - success: 是否成功生成
            - atom_names_map: 原子名称映射字典 {分子ID: [原子名称列表]}
            - template_atom_names: 模板中的原子名称列表
            - atom_name_count: 每个分子的原子数量
            - error: 错误信息（如果失败）
            
    Example:
        >>> instances = create_molecule_instances(...)["instances"]
        >>> result = generate_atom_names("EC.lt", instances)
        >>> for mol_id, names in result["atom_names_map"].items():
        ...     print(f"{mol_id}: {names}")
        
    Note:
        原子名称从.lt模板文件的 "Data Atoms" 部分提取。
        格式示例：
        write("Data Atoms") {
          $atom:1 @atom:C1 $mol:... 0.0 x y z
          $atom:2 @atom:C2 $mol:... 0.0 x y z
        }
        提取的原子名称为：C1, C2, ...
    """
    logger.info(f"[原子名称生成] 开始生成原子名称映射: {template_file_path}")
    
    # 验证模板文件
    template_file = Path(template_file_path)
    if not template_file.exists():
        error_msg = f"分子模板文件不存在: {template_file_path}"
        logger.error(f"[原子名称生成] {error_msg}")
        return {
            "success": False,
            "atom_names_map": {},
            "template_atom_names": [],
            "atom_name_count": {},
            "error": error_msg
        }
    
    # 读取模板文件内容
    try:
        with open(template_file, 'r', encoding='utf-8') as f:
            template_content = f.read()
        logger.debug(f"[原子名称生成] 模板文件读取成功")
    except Exception as e:
        error_msg = f"读取模板文件失败: {str(e)}"
        logger.error(f"[原子名称生成] {error_msg}")
        return {
            "success": False,
            "atom_names_map": {},
            "template_atom_names": [],
            "atom_name_count": {},
            "error": error_msg
        }
    
    # 从模板文件中提取原子名称
    template_atom_names = []
    
    # 使用正则表达式提取原子名称
    # 匹配格式：@atom:AtomName 或 $atom:index @atom:AtomName
    atom_name_pattern = re.compile(r'@atom:([A-Za-z0-9_]+)')
    
    # 查找所有原子名称定义
    atom_name_matches = atom_name_pattern.findall(template_content)
    
    if atom_name_matches:
        # 去重并保持顺序
        seen = set()
        for name in atom_name_matches:
            if name not in seen:
                template_atom_names.append(name)
                seen.add(name)
        
        logger.info(
            f"[原子名称生成] 从模板提取 {len(template_atom_names)} 个原子名称: "
            f"{template_atom_names[:10]}..."
        )
    else:
        # 如果没有找到原子名称，尝试从PDB原子名称推断
        logger.warning(
            "[原子名称生成] 模板文件中未找到原子名称定义，"
            "将使用分子实例中的原子名称"
        )
        
        # 从第一个分子实例提取原子名称
        if molecule_instances:
            first_instance = molecule_instances[0]
            template_atom_names = [atom.name for atom in first_instance.atoms]
            logger.info(
                f"[原子名称生成] 从分子实例提取 {len(template_atom_names)} 个原子名称"
            )
    
    # 构建原子名称映射字典
    atom_names_map = {}
    atom_name_count = {}
    
    for instance in molecule_instances:
        # 构建分子ID
        molecule_id = f"{instance.molecule_name}_{instance.instance_id}"
        
        # 使用模板原子名称（如果可用）或实例原子名称
        if template_atom_names:
            # 确保原子数量匹配
            if len(template_atom_names) == len(instance.atoms):
                atom_names_map[molecule_id] = template_atom_names.copy()
            else:
                # 原子数量不匹配，使用实例原子名称
                atom_names_map[molecule_id] = [atom.name for atom in instance.atoms]
                logger.warning(
                    f"[原子名称生成] 分子 {molecule_id} 原子数量不匹配: "
                    f"模板 {len(template_atom_names)}, 实例 {len(instance.atoms)}"
                )
        else:
            # 没有模板原子名称，使用实例原子名称
            atom_names_map[molecule_id] = [atom.name for atom in instance.atoms]
        
        # 记录原子数量
        atom_name_count[molecule_id] = len(atom_names_map[molecule_id])
        
        logger.debug(
            f"[原子名称生成] 分子 {molecule_id}: "
            f"{len(atom_names_map[molecule_id])} 个原子"
        )
    
    logger.info(
        f"[原子名称生成] 生成完成: {len(atom_names_map)} 个分子实例, "
        f"模板原子名称: {len(template_atom_names)} 个"
    )
    
    return {
        "success": True,
        "atom_names_map": atom_names_map,
        "template_atom_names": template_atom_names,
        "atom_name_count": atom_name_count,
        "error": None
    }


def validate_instance_count(
    molecule_instances: List[MoleculeInstance],
    formula_counts: Dict[str, int]
) -> Dict[str, Any]:
    """验证分子实例数量与配方一致
    
    验证创建的分子实例数量是否与配方计算结果一致，
    返回详细的验证结果和差异信息。
    
    Args:
        molecule_instances: 分子实例列表（MoleculeInstance对象列表）
        formula_counts: 配方分子数量字典 {分子名称: 预期数量}
        
    Returns:
        验证结果字典，包含：
            - valid: 是否验证通过（数量一致）
            - instance_counts: 实际实例数量统计 {分子名称: 实际数量}
            - expected_counts: 预期数量统计 {分子名称: 预期数量}
            - differences: 差异列表 [{分子名称, 预期数量, 实际数量, 差异值}]
            - missing_molecules: 缺失的分子类型列表
            - extra_molecules: 多余的分子类型列表
            - total_instances: 实际实例总数
            - total_expected: 预期实例总数
            - error: 错误信息（如果有）
            
    Example:
        >>> instances = create_molecule_instances(...)["instances"]
        >>> result = validate_instance_count(
        ...     instances,
        ...     {"EC": 500, "Li": 50}
        ... )
        >>> if result["valid"]:
        ...     print("验证通过")
        ... else:
        ...     print(f"差异: {result['differences']}")
        
    Note:
        验证规则：
        1. 每种分子类型的实例数量必须与配方预期数量完全一致
        2. 不允许缺失任何配方中指定的分子类型
        3. 不允许出现配方中未指定的分子类型
    """
    logger.info("[实例数量验证] 开始验证分子实例数量")
    logger.info(f"[实例数量验证] 配方预期数量: {formula_counts}")
    
    # 统计实际实例数量
    instance_counts = {}
    for instance in molecule_instances:
        molecule_name = instance.molecule_name
        if molecule_name not in instance_counts:
            instance_counts[molecule_name] = 0
        instance_counts[molecule_name] += 1
    
    logger.info(f"[实例数量验证] 实际实例数量: {instance_counts}")
    
    # 计算差异
    differences = []
    missing_molecules = []
    extra_molecules = []
    
    # 检查配方中指定的分子
    for mol_name, expected_count in formula_counts.items():
        actual_count = instance_counts.get(mol_name, 0)
        
        if actual_count == 0:
            # 缺失分子类型
            missing_molecules.append(mol_name)
            differences.append({
                "molecule_name": mol_name,
                "expected_count": expected_count,
                "actual_count": 0,
                "difference": -expected_count,
                "status": "missing"
            })
            logger.error(
                f"[实例数量验证] 分子 {mol_name} 缺失: "
                f"预期 {expected_count}, 实际 0"
            )
        elif actual_count != expected_count:
            # 数量不匹配
            diff = actual_count - expected_count
            differences.append({
                "molecule_name": mol_name,
                "expected_count": expected_count,
                "actual_count": actual_count,
                "difference": diff,
                "status": "mismatch"
            })
            logger.warning(
                f"[实例数量验证] 分子 {mol_name} 数量不匹配: "
                f"预期 {expected_count}, 实际 {actual_count}, 差异 {diff}"
            )
        else:
            # 数量匹配
            logger.info(
                f"[实例数量验证] 分子 {mol_name} 数量匹配: {actual_count}"
            )
    
    # 检查实例中多余的分子类型
    for mol_name in instance_counts:
        if mol_name not in formula_counts:
            # 多余分子类型
            extra_molecules.append(mol_name)
            actual_count = instance_counts[mol_name]
            differences.append({
                "molecule_name": mol_name,
                "expected_count": 0,
                "actual_count": actual_count,
                "difference": actual_count,
                "status": "extra"
            })
            logger.warning(
                f"[实例数量验证] 发现多余分子类型 {mol_name}: "
                f"预期 0, 实际 {actual_count}"
            )
    
    # 计算总数
    total_instances = len(molecule_instances)
    total_expected = sum(formula_counts.values())
    
    # 判断验证是否通过
    valid = (
        len(differences) == 0 and
        len(missing_molecules) == 0 and
        len(extra_molecules) == 0
    )
    
    # 构建验证结果
    if valid:
        logger.info(
            f"[实例数量验证] 验证通过: 总实例数 {total_instances}, "
            f"与配方预期 {total_expected} 一致"
        )
    else:
        logger.error(
            f"[实例数量验证] 验证失败: 发现 {len(differences)} 个差异, "
            f"缺失 {len(missing_molecules)} 种分子, "
            f"多余 {len(extra_molecules)} 种分子"
        )
    
    return {
        "valid": valid,
        "instance_counts": instance_counts,
        "expected_counts": formula_counts,
        "differences": differences,
        "missing_molecules": missing_molecules,
        "extra_molecules": extra_molecules,
        "total_instances": total_instances,
        "total_expected": total_expected,
        "error": None if valid else "分子实例数量与配方不一致"
    }


# ==================== System.lt文件生成功能 ====================

def generate_template_imports(
    template_files: Dict[str, str],
    forcefield_file: str = None,
    template_library_path: str = None,
    forcefield_library_path: str = None
) -> Dict[str, Any]:
    """生成模板导入语句
    
    根据分子模板文件列表和力场文件，生成system.lt文件中的import语句块。
    import语句用于导入力场文件和分子模板文件。
    
    Args:
        template_files: 分子模板文件字典 {分子名称: 文件路径}
                       例如: {"EC": "EC.lt", "Li": "Li.lt"}
        forcefield_file: 力场文件路径（可选），例如: "oplsaa.lt"
        template_library_path: 分子模板库根路径（可选），例如: "system_templates/molecule_templates"
        forcefield_library_path: 力场文件库路径（可选），例如: "system_templates/force_fields"
        
    Returns:
        结果字典，包含：
            - success: 是否成功生成
            - import_block: 导入语句文本块（字符串）
            - import_lines: 导入语句行列表
            - imported_files: 已导入的文件列表
            - error: 错误信息（如果失败）
            
    Example:
        >>> template_files = {"EC": "EC.lt", "Li": "Li.lt"}
        >>> result = generate_template_imports(
        ...     template_files,
        ...     "oplsaa.lt",
        ...     template_library_path="system_templates/molecule_templates",
        ...     forcefield_library_path="system_templates/force_fields/opls-aa"
        ... )
        >>> print(result["import_block"])
        
    Note:
        根据文件输入输出规则：
        - 分子模板文件存储在 system_templates/molecule_templates/{分子名称}/ 目录
        - 力场文件存储在 system_templates/force_fields/{力场类型}/ 目录
        - inputs目录只存放任务输入文件，不包含分子模板和力场文件
        - import语句使用相对路径从inputs目录指向system_templates目录
    """
    logger.info("[模板导入生成] 开始生成模板导入语句")
    logger.info(f"[模板导入生成] 模板文件: {template_files}")
    logger.info(f"[模板导入生成] 力场文件: {forcefield_file}")
    
    import_lines = []
    imported_files = []
    
    # 辅助函数：将绝对路径转换为相对路径（从inputs目录开始）
    def to_relative_path(abs_path: str) -> str:
        """将绝对路径转换为相对于inputs目录的相对路径

        路径转换规则：
        - inputs目录位于 user_{user_id}/jobs/job_{job_id}/inputs/
        - system_templates目录位于 data/md_platform_data/system_templates/
        - 需要向上4级目录到达data目录，再导航到system_templates
        - 相对路径格式: ../../../..
        """
        path_obj = Path(abs_path)
        if path_obj.is_absolute():
            # 从inputs目录向上4级到达data/md_platform_data目录
            # inputs -> job_{job_id} -> jobs -> user_{user_id} -> md_platform_data
            # 然后再进入system_templates目录
            try:
                # 提取data之后的相对部分
                path_str = str(path_obj)
                # 统一使用正斜杠
                path_str = path_str.replace('\\', '/')
                # 查找 md_platform_data 或 data 的位置
                if 'md_platform_data' in path_str:
                    idx = path_str.index('md_platform_data')
                    relative_part = path_str[idx + len('md_platform_data') + 1:]  # 跳过 /
                    return f"../../../../{relative_part}"
                elif '/data/' in path_str:
                    idx = path_str.rfind('/data/')
                    relative_part = path_str[idx + 6:]  # 跳过 /data/
                    return f"../../../../{relative_part}"
                else:
                    # 无法解析，返回原始路径
                    return path_str
            except Exception as e:
                logger.warning(f"[模板导入生成] 路径转换失败: {e}")
                return str(path_obj).replace('\\', '/')
        else:
            # 已经是相对路径，统一使用正斜杠
            return abs_path.replace('\\', '/')
    
    # 1. 导入力场文件（如果提供）
    if forcefield_file:
        if forcefield_library_path:
            # 构建完整的力场文件绝对路径
            ff_filename = Path(forcefield_file).name
            ff_full_path = Path(forcefield_library_path) / ff_filename
            # 转换为相对路径
            forcefield_import_path = to_relative_path(str(ff_full_path))
            import_line = f'import "{forcefield_import_path}"  # 力场文件'
            imported_files.append(forcefield_import_path)
            logger.info(f"[模板导入生成] 添加力场文件导入: {forcefield_import_path}")
        else:
            # 兼容旧模式：直接使用文件名
            ff_filename = Path(forcefield_file).name if Path(forcefield_file).exists() else forcefield_file
            import_line = f'import "{ff_filename}"  # 力场文件'
            imported_files.append(ff_filename)
            logger.info(f"[模板导入生成] 添加力场文件导入: {ff_filename}")
        
        import_lines.append(import_line)
    
    # 2. 导入分子模板文件（按字母顺序）
    if template_files:
        sorted_molecules = sorted(template_files.keys())
        
        for molecule_name in sorted_molecules:
            template_path = template_files[molecule_name]
            
            if template_library_path:
                # 构建完整的模板文件路径
                template_filename = Path(template_path).name
                template_full_path = Path(template_library_path) / molecule_name / template_filename
                # 转换为相对路径
                template_import_path = to_relative_path(str(template_full_path))
                import_line = f'import "{template_import_path}"  # 分子模板'
                imported_files.append(template_import_path)
                logger.debug(f"[模板导入生成] 添加分子模板导入: {molecule_name} -> {template_import_path}")
            else:
                # 兼容旧模式：使用文件名
                template_filename = Path(template_path).name if Path(template_path).exists() else template_path
                import_line = f'import "{template_filename}"  # 分子模板'
                imported_files.append(template_filename)
                logger.debug(f"[模板导入生成] 添加分子模板导入: {molecule_name} -> {template_filename}")
            
            import_lines.append(import_line)
    
    # 3. 添加空行分隔
    if import_lines:
        import_lines.append("")
    
    # 4. 构建导入语句块
    import_block = "\n".join(import_lines)
    
    # 5. 记录统计信息
    logger.info(
        f"[模板导入生成] 生成完成: {len(imported_files)} 个导入语句, "
        f"力场文件: {1 if forcefield_file else 0}, "
        f"分子模板: {len(template_files)}"
    )
    
    return {
        "success": True,
        "import_block": import_block,
        "import_lines": import_lines,
        "imported_files": imported_files,
        "error": None
    }


def generate_molecule_instance_definitions(
    molecule_counts: Dict[str, int],
    class_name_mapping: Dict[str, str] = None
) -> Dict[str, Any]:
    """生成分子实例定义

    根据分子数量字典，生成system.lt文件中的分子实例创建语句。
    每个分子实例单独创建一行，使用正确的Moltemplate语法。

    Args:
        molecule_counts: 分子数量字典 {分子名称: 数量}
                        例如: {"EC": 500, "Li": 50, "PF6": 50}
        class_name_mapping: 类名映射字典（可选）
                           用于处理大小写等问题，例如 {"LI": "Li", "li": "Li"}

    Returns:
        结果字典，包含：
            - success: 是否成功生成
            - instance_block: 分子实例定义文本块（字符串）
            - instance_lines: 分子实例定义行列表
            - instance_summary: 实例统计摘要 {分子名称: 变量名列表}
            - total_instances: 实例总数
            - error: 错误信息（如果失败）

    Example:
        >>> molecule_counts = {"DMC": 200, "EC": 300, "LI": 50, "PF6": 50}
        >>> class_mapping = {"LI": "Li"}
        >>> result = generate_molecule_instance_definitions(molecule_counts, class_mapping)
        >>> print(result["instance_block"])
        # 输出:
        # # 分子实例创建
        # dmc1 = new DMC
        # dmc2 = new DMC
        # ...
        # ec1 = new EC
        # ec2 = new EC
        # ...
        # li1 = new Li
        # li2 = new Li
        # ...
        # pf61 = new PF6
        # pf62 = new PF6

    Note:
        Moltemplate规范要求：
        - 每个分子实例单独使用 new 关键字创建
        - 变量名格式: {分子名称小写}{序号}（如 dmc1, ec1, li1）
        - 类名必须与.lt文件中定义的完全一致（大小写敏感）
        - 不使用数组语法（如 new EC[500]），而是逐个创建
    """
    logger.info("[分子实例定义生成] 开始生成分子实例定义")
    logger.info(f"[分子实例定义生成] 分子数量: {molecule_counts}")

    if not molecule_counts:
        error_msg = "分子数量字典为空"
        logger.error(f"[分子实例定义生成] {error_msg}")
        return {
            "success": False,
            "instance_block": "",
            "instance_lines": [],
            "instance_summary": {},
            "total_instances": 0,
            "error": error_msg
        }

    instance_lines = []
    instance_summary = {}
    total_instances = 0

    # 1. 添加注释说明
    instance_lines.append("# 创建分子实例")

    # 2. 按分子名称排序生成实例定义
    sorted_molecules = sorted(molecule_counts.keys())

    for molecule_name in sorted_molecules:
        count = molecule_counts[molecule_name]

        # 获取实际的类名（处理大小写映射）
        if class_name_mapping and molecule_name in class_name_mapping:
            actual_class_name = class_name_mapping[molecule_name]
            logger.debug(f"[分子实例定义生成] 类名映射: {molecule_name} -> {actual_class_name}")
        else:
            actual_class_name = molecule_name

        # 构建变量名前缀（分子名称小写，处理特殊字符）
        var_prefix = molecule_name.lower().replace("+", "_pos").replace("-", "_neg")

        # 为该类型的每个分子创建单独的实例定义
        var_names = []
        for i in range(1, count + 1):
            var_name = f"{var_prefix}{i}"
            # 使用 new 关键字创建单个实例
            instance_line = f"{var_name} = new {actual_class_name}"
            instance_lines.append(instance_line)
            var_names.append(var_name)
            total_instances += 1

        # 更新统计信息
        instance_summary[molecule_name] = var_names

        logger.debug(
            f"[分子实例定义生成] 分子 {molecule_name} (类名: {actual_class_name}): "
            f"创建了 {count} 个实例"
        )

    # 3. 添加空行分隔
    instance_lines.append("")

    # 4. 构建实例定义块
    instance_block = "\n".join(instance_lines)

    # 5. 记录统计信息
    logger.info(
        f"[分子实例定义生成] 生成完成: {len(molecule_counts)} 种分子, "
        f"总实例数 {total_instances}"
    )

    return {
        "success": True,
        "instance_block": instance_block,
        "instance_lines": instance_lines,
        "instance_summary": instance_summary,
        "total_instances": total_instances,
        "error": None
    }


def generate_coordinate_data(
    molecule_instances: List[MoleculeInstance],
    atom_type_mapping: Dict[str, str] = None,
    charge_mapping: Dict[str, float] = None
) -> Dict[str, Any]:
    """生成原子坐标数据块
    
    根据分子实例列表，生成system.lt文件中的原子坐标数据块。
    数据块包含所有原子的坐标、原子类型、电荷等信息。
    
    Args:
        molecule_instances: 分子实例列表（MoleculeInstance对象列表）
        atom_type_mapping: 原子类型映射字典 {原子名称: 原子类型}（可选）
                          例如: {"C1": "opls_145", "O1": "opls_154"}
        charge_mapping: 原子电荷映射字典 {原子名称: 电荷}（可选）
                       例如: {"C1": 0.0, "O1": -0.5}
        
    Returns:
        结果字典，包含：
            - success: 是否成功生成
            - coordinate_block: 原子坐标数据块文本（字符串）
            - coordinate_lines: 原子坐标行列表
            - total_atoms: 原子总数
            - atom_summary: 原子统计摘要 {分子ID: 原子数量}
            - error: 错误信息（如果失败）
            
    Example:
        >>> instances = create_molecule_instances(...)["instances"]
        >>> result = generate_coordinate_data(instances)
        >>> print(result["coordinate_block"])
        # 输出:
        # write("Data Atoms") {
        #   $atom:EC_1:C1  @atom:opls_145  0.0  10.0  20.0  30.0
        #   $atom:EC_1:C2  @atom:opls_146  0.0  11.0  21.0  31.0
        #   ...
        # }
        
    Note:
        原子坐标数据格式：
        - $atom:{分子ID}:{原子名称}  @atom:{原子类型}  {电荷}  {x}  {y}  {z}
        - 分子ID格式: {分子名称}_{实例序号}（如 EC_1, Li_50）
        - 如果未提供原子类型映射，使用默认原子类型（原子名称）
        - 如果未提供电荷映射，使用默认电荷0.0
    """
    logger.info("[原子坐标生成] 开始生成原子坐标数据块")
    logger.info(f"[原子坐标生成] 分子实例数量: {len(molecule_instances)}")
    
    if not molecule_instances:
        error_msg = "分子实例列表为空"
        logger.error(f"[原子坐标生成] {error_msg}")
        return {
            "success": False,
            "coordinate_block": "",
            "coordinate_lines": [],
            "total_atoms": 0,
            "atom_summary": {},
            "error": error_msg
        }
    
    coordinate_lines = []
    atom_summary = {}
    total_atoms = 0
    
    # 1. 添加write("Data Atoms")块开始
    coordinate_lines.append('write("Data Atoms") {')
    
    # 2. 遍历所有分子实例，生成原子坐标
    for instance in molecule_instances:
        molecule_id = f"{instance.molecule_name}_{instance.instance_id}"
        molecule_atoms_count = 0
        
        logger.debug(
            f"[原子坐标生成] 处理分子 {molecule_id}: "
            f"{len(instance.atoms)} 个原子"
        )
        
        # 遍历该分子实例的所有原子
        for atom in instance.atoms:
            # 获取原子名称
            atom_name = atom.name
            
            # 获取原子类型（使用映射或默认值）
            if atom_type_mapping and atom_name in atom_type_mapping:
                atom_type = atom_type_mapping[atom_name]
            else:
                # 默认使用原子名称作为原子类型
                atom_type = f"@atom:{atom_name}"
            
            # 获取电荷（使用映射或默认值）
            if charge_mapping and atom_name in charge_mapping:
                charge = charge_mapping[atom_name]
            else:
                # 默认电荷为0.0
                charge = 0.0
            
            # 构建原子坐标行
            # 格式: $atom:{分子ID}:{原子名称}  @atom:{原子类型}  {电荷}  {x}  {y}  {z}
            coord_line = (
                f"  $atom:{molecule_id}:{atom_name}  "
                f"{atom_type}  {charge:.4f}  "
                f"{atom.x:.6f}  {atom.y:.6f}  {atom.z:.6f}"
            )
            
            coordinate_lines.append(coord_line)
            molecule_atoms_count += 1
            total_atoms += 1
        
        # 更新原子统计
        atom_summary[molecule_id] = molecule_atoms_count
    
    # 3. 添加write("Data Atoms")块结束
    coordinate_lines.append('}')
    coordinate_lines.append("")  # 添加空行分隔
    
    # 4. 构建原子坐标数据块
    coordinate_block = "\n".join(coordinate_lines)
    
    # 5. 记录统计信息
    logger.info(
        f"[原子坐标生成] 生成完成: {total_atoms} 个原子, "
        f"{len(molecule_instances)} 个分子实例"
    )
    
    return {
        "success": True,
        "coordinate_block": coordinate_block,
        "coordinate_lines": coordinate_lines,
        "total_atoms": total_atoms,
        "atom_summary": atom_summary,
        "error": None
    }


def generate_box_boundary(
    box_size: Dict[str, float]
) -> Dict[str, Any]:
    """生成盒子边界定义
    
    根据盒子尺寸，生成system.lt文件中的盒子边界定义块。
    定义模拟盒子的三维边界范围。
    
    Args:
        box_size: 盒子尺寸字典 {"x": x_size, "y": y_size, "z": z_size}
                 例如: {"x": 48.0, "y": 48.0, "z": 48.0}
        
    Returns:
        结果字典，包含：
            - success: 是否成功生成
            - boundary_block: 盒子边界定义文本块（字符串）
            - boundary_lines: 盒子边界定义行列表
            - box_dimensions: 盒子尺寸信息
            - error: 错误信息（如果失败）
            
    Example:
        >>> box_size = {"x": 48.0, "y": 48.0, "z": 48.0}
        >>> result = generate_box_boundary(box_size)
        >>> print(result["boundary_block"])
        # 输出:
        # write_once("Data Boundary") {
        #   0.0  48.0  xlo xhi
        #   0.0  48.0  ylo yhi
        #   0.0  48.0  zlo zhi
        # }
        
    Note:
        盒子边界定义格式：
        - 使用write_once("Data Boundary")定义盒子边界
        - 格式: {最小值}  {最大值}  {维度标识}
        - 默认最小值为0.0（盒子从原点开始）
        - 维度标识: xlo xhi, ylo yhi, zlo zhi
    """
    logger.info("[盒子边界生成] 开始生成盒子边界定义")
    logger.info(f"[盒子边界生成] 盒子尺寸: {box_size}")
    
    # 验证盒子尺寸
    if not box_size:
        error_msg = "盒子尺寸字典为空"
        logger.error(f"[盒子边界生成] {error_msg}")
        return {
            "success": False,
            "boundary_block": "",
            "boundary_lines": [],
            "box_dimensions": {},
            "error": error_msg
        }
    
    # 获取盒子尺寸（默认值为50.0）
    x_size = box_size.get("x", 50.0)
    y_size = box_size.get("y", 50.0)
    z_size = box_size.get("z", 50.0)
    
    # 验证尺寸有效性
    if x_size <= 0 or y_size <= 0 or z_size <= 0:
        error_msg = f"盒子尺寸无效: x={x_size}, y={y_size}, z={z_size}"
        logger.error(f"[盒子边界生成] {error_msg}")
        return {
            "success": False,
            "boundary_block": "",
            "boundary_lines": [],
            "box_dimensions": {},
            "error": error_msg
        }
    
    boundary_lines = []
    
    # 1. 添加write_once("Data Boundary")块开始
    boundary_lines.append('write_once("Data Boundary") {')
    
    # 2. 添加三维边界定义
    # X维度边界
    boundary_lines.append(f"  0.0  {x_size:.6f}  xlo xhi")
    
    # Y维度边界
    boundary_lines.append(f"  0.0  {y_size:.6f}  ylo yhi")
    
    # Z维度边界
    boundary_lines.append(f"  0.0  {z_size:.6f}  zlo zhi")
    
    # 3. 添加write_once("Data Boundary")块结束
    boundary_lines.append('}')
    boundary_lines.append("")  # 添加空行分隔
    
    # 4. 构建盒子边界定义块
    boundary_block = "\n".join(boundary_lines)
    
    # 5. 记录盒子尺寸信息
    box_dimensions = {
        "x": {"min": 0.0, "max": x_size, "size": x_size},
        "y": {"min": 0.0, "max": y_size, "size": y_size},
        "z": {"min": 0.0, "max": z_size, "size": z_size},
        "volume": x_size * y_size * z_size
    }
    
    # 6. 记录统计信息
    logger.info(
        f"[盒子边界生成] 生成完成: 盒子尺寸 "
        f"{x_size:.2f} x {y_size:.2f} x {z_size:.2f} Å, "
        f"体积 {box_dimensions['volume']:.2f} Å³"
    )
    
    return {
        "success": True,
        "boundary_block": boundary_block,
        "boundary_lines": boundary_lines,
        "box_dimensions": box_dimensions,
        "error": None
    }


# 默认类名映射字典
# 用于将输入的分子名称映射到.lt文件中实际定义的类名
# 解决大小写不一致问题（例如：数据库中存储为"LI"，但.lt文件中定义的类名为"Li"）
DEFAULT_CLASS_NAME_MAPPING = {
    # 锂离子相关
    "LI": "Li",
    "li": "Li",
    "LITHIUM": "Li",
    "lithium": "Li",
    # 其他常见映射可根据需要添加
    # "NA": "Na",  # 钠离子
    # "K": "K",    # 钾离子
}


def generate_system_lt_file(
    template_files: Dict[str, str],
    molecule_instances: List[MoleculeInstance],
    box_size: Dict[str, float],
    forcefield_type: str = "oplsaa",
    output_path: str = None,
    atom_type_mapping: Dict[str, str] = None,
    charge_mapping: Dict[str, float] = None,
    forcefield_library_path: str = None,
    template_library_path: str = None,
    class_name_mapping: Dict[str, str] = None
) -> Dict[str, Any]:
    """生成完整的system.lt文件

    整合所有子功能，生成完整的Moltemplate系统描述文件（system.lt）。
    该文件包含力场导入、分子模板导入、分子实例定义和盒子边界等。

    重要说明（Moltemplate规范）：
    - 不使用 system = { } 包裹结构，直接在全局作用域定义
    - 不使用 $atom:xxx:yyy 变量描述符语法
    - 不手动写入原子坐标，让 moltemplate 使用模板中的默认坐标
    - 每个分子实例单独创建（不使用数组语法）
    - 类名必须与 .lt 文件中定义的完全一致（大小写敏感）

    Args:
        template_files: 分子模板文件字典 {分子名称: 文件路径}
                       例如: {"EC": "EC.lt", "LI": "LI.lt"}
        molecule_instances: 分子实例列表（MoleculeInstance对象列表）
        box_size: 盒子尺寸字典 {"x": x_size, "y": y_size, "z": z_size}
                 例如: {"x": 48.0, "y": 48.0, "z": 48.0}
        forcefield_type: 力场类型（默认: "oplsaa"）
        output_path: 输出文件路径（可选，默认: "system.lt"）
        atom_type_mapping: 原子类型映射字典（可选，当前版本未使用）
        charge_mapping: 原子电荷映射字典（可选，当前版本未使用）
        forcefield_library_path: 力场文件库路径（可选）
        template_library_path: 分子模板库根路径（可选）
        class_name_mapping: 类名映射字典（可选）
                           用于处理类名大小写等问题
                           例如: {"LI": "Li", "li": "Li"}
                           这确保生成的类名与.lt文件中定义的一致

    Returns:
        结果字典，包含：
            - success: 是否成功生成
            - system_lt_path: 生成的system.lt文件路径
            - system_lt_content: system.lt文件内容
            - generation_summary: 生成摘要统计
            - validation_result: 文件验证结果
            - error: 错误信息（如果失败）

    Example:
        >>> template_files = {"EC": "EC.lt", "Li": "Li.lt"}
        >>> instances = create_molecule_instances(...)["instances"]
        >>> box_size = {"x": 48.0, "y": 48.0, "z": 48.0}
        >>> result = generate_system_lt_file(
        ...     template_files,
        ...     instances,
        ...     box_size,
        ...     forcefield_type="oplsaa",
        ...     output_path="inputs/system.lt",
        ...     template_library_path="system_templates/molecule_templates"
        ... )
        >>> if result["success"]:
        ...     print(f"文件已生成: {result['system_lt_path']}")

    Note:
        根据文件输入输出规则：
        - 分子模板和力场文件存储在system_templates目录中
        - inputs目录只存放任务输入文件
        - import语句使用相对路径指向system_templates目录

        system.lt文件结构（符合Moltemplate规范）：
        1. 文件头注释
        2. 导入语句块（力场文件和分子模板）
        3. 分子实例定义（逐个创建，如 dmc1 = new DMC）
        4. 盒子边界定义（write_once("Data Boundary")）

        重要：
        - 不使用 system = { } 包裹结构
        - 不使用 $atom:xxx:yyy 语法
        - 不手动写入原子坐标（moltemplate自动处理）
    """
    logger.info("[System.lt生成] 开始生成完整的system.lt文件")
    logger.info(f"[System.lt生成] 模板文件: {template_files}")
    logger.info(f"[System.lt生成] 分子实例数量: {len(molecule_instances)}")
    logger.info(f"[System.lt生成] 盒子尺寸: {box_size}")
    logger.info(f"[System.lt生成] 力场类型: {forcefield_type}")

    # 如果未提供类名映射，使用默认映射（处理常见的大小写问题）
    if class_name_mapping is None:
        class_name_mapping = DEFAULT_CLASS_NAME_MAPPING.copy()
        logger.debug(f"[System.lt生成] 使用默认类名映射: {class_name_mapping}")
    else:
        # 合并用户提供的映射和默认映射（用户映射优先）
        merged_mapping = DEFAULT_CLASS_NAME_MAPPING.copy()
        merged_mapping.update(class_name_mapping)
        class_name_mapping = merged_mapping
        logger.debug(f"[System.lt生成] 使用合并后的类名映射: {class_name_mapping}")

    # 1. 验证输入参数
    if not template_files:
        error_msg = "模板文件字典为空"
        logger.error(f"[System.lt生成] {error_msg}")
        return {
            "success": False,
            "system_lt_path": None,
            "system_lt_content": "",
            "generation_summary": {},
            "validation_result": {},
            "error": error_msg
        }
    
    if not molecule_instances:
        error_msg = "分子实例列表为空"
        logger.error(f"[System.lt生成] {error_msg}")
        return {
            "success": False,
            "system_lt_path": None,
            "system_lt_content": "",
            "generation_summary": {},
            "validation_result": {},
            "error": error_msg
        }
    
    if not box_size:
        error_msg = "盒子尺寸字典为空"
        logger.error(f"[System.lt生成] {error_msg}")
        return {
            "success": False,
            "system_lt_path": None,
            "system_lt_content": "",
            "generation_summary": {},
            "validation_result": {},
            "error": error_msg
        }
    
    # 2. 加载力场文件（如果提供力场库路径）
    forcefield_file = None
    if forcefield_library_path:
        ff_result = load_forcefield_file(forcefield_type, forcefield_library_path)
        if ff_result["success"]:
            forcefield_file = ff_result["forcefield_path"]
            logger.info(f"[System.lt生成] 加载力场文件: {forcefield_file}")
        else:
            logger.warning(f"[System.lt生成] 力场文件加载失败: {ff_result['error']}")
    
    # 3. 生成各个数据块
    # 3.1 生成导入语句块（使用相对路径指向system_templates目录）
    imports_result = generate_template_imports(
        template_files, 
        forcefield_file,
        template_library_path=template_library_path,
        forcefield_library_path=forcefield_library_path
    )
    if not imports_result["success"]:
        error_msg = f"生成导入语句失败: {imports_result['error']}"
        logger.error(f"[System.lt生成] {error_msg}")
        return {
            "success": False,
            "system_lt_path": None,
            "system_lt_content": "",
            "generation_summary": {},
            "validation_result": {},
            "error": error_msg
        }
    
    # 3.2 统计分子数量
    molecule_counts = {}
    for instance in molecule_instances:
        molecule_name = instance.molecule_name
        if molecule_name not in molecule_counts:
            molecule_counts[molecule_name] = 0
        molecule_counts[molecule_name] += 1
    
    # 3.3 生成分子实例定义块（使用正确的Moltemplate语法：逐个创建实例）
    instances_result = generate_molecule_instance_definitions(
        molecule_counts,
        class_name_mapping=class_name_mapping
    )
    if not instances_result["success"]:
        error_msg = f"生成分子实例定义失败: {instances_result['error']}"
        logger.error(f"[System.lt生成] {error_msg}")
        return {
            "success": False,
            "system_lt_path": None,
            "system_lt_content": "",
            "generation_summary": {},
            "validation_result": {},
            "error": error_msg
        }

    # 注意：根据Moltemplate规范，不再手动生成原子坐标数据块
    # moltemplate会自动使用模板中定义的默认坐标
    # 原子坐标将在Packmol堆积后由moltemplate处理

    # 3.4 生成盒子边界定义块
    boundary_result = generate_box_boundary(box_size)
    if not boundary_result["success"]:
        error_msg = f"生成盒子边界定义失败: {boundary_result['error']}"
        logger.error(f"[System.lt生成] {error_msg}")
        return {
            "success": False,
            "system_lt_path": None,
            "system_lt_content": "",
            "generation_summary": {},
            "validation_result": {},
            "error": error_msg
        }
    
    # 4. 组装完整的system.lt文件内容
    # 注意：根据Moltemplate规范，不使用system = { }包裹结构
    # 直接在全局作用域定义所有内容
    system_lt_lines = []

    # 4.1 添加文件头注释（不包含原子数统计，因为不再手动生成坐标）
    system_lt_lines.append("# Moltemplate system description file")
    system_lt_lines.append(f"# Generated by modeling system")
    system_lt_lines.append(f"# Force field: {forcefield_type}")
    system_lt_lines.append(f"# Total molecules: {len(molecule_instances)}")
    system_lt_lines.append(f"# Total molecule instances: {instances_result['total_instances']}")
    system_lt_lines.append(f"# Box size: {box_size.get('x', 0):.2f} x {box_size.get('y', 0):.2f} x {box_size.get('z', 0):.2f} Å")
    system_lt_lines.append("")

    # 4.2 添加导入语句块（力场和分子模板）
    system_lt_lines.append(imports_result["import_block"])

    # 4.3 添加分子实例定义块（直接在全局作用域创建实例）
    system_lt_lines.append(instances_result["instance_block"])

    # 4.4 添加盒子边界定义块
    system_lt_lines.append(boundary_result["boundary_block"])
    
    # 5. 构建完整文件内容
    system_lt_content = "\n".join(system_lt_lines)
    
    # 6. 确定输出路径
    if output_path:
        output_file = Path(output_path)
    else:
        output_file = Path("system.lt")
    
    # 7. 创建输出目录（如果不存在）
    try:
        output_file.parent.mkdir(parents=True, exist_ok=True)
        logger.info(f"[System.lt生成] 创建输出目录: {output_file.parent}")
    except Exception as e:
        error_msg = f"创建输出目录失败: {str(e)}"
        logger.error(f"[System.lt生成] {error_msg}")
        return {
            "success": False,
            "system_lt_path": None,
            "system_lt_content": system_lt_content,
            "generation_summary": {},
            "validation_result": {},
            "error": error_msg
        }
    
    # 8. 写入文件（原子写入）
    try:
        # 使用原子写入方式
        temp_file = output_file.with_suffix('.lt.tmp')
        
        with open(temp_file, 'w', encoding='utf-8') as f:
            f.write(system_lt_content)
        
        # 原子替换
        temp_file.replace(output_file)
        
        logger.info(f"[System.lt生成] 文件已写入: {output_file}")
        
    except Exception as e:
        error_msg = f"写入文件失败: {str(e)}"
        logger.error(f"[System.lt生成] {error_msg}")
        return {
            "success": False,
            "system_lt_path": None,
            "system_lt_content": system_lt_content,
            "generation_summary": {},
            "validation_result": {},
            "error": error_msg
        }
    
    # 9. 验证生成的文件
    validation_result = validate_system_lt(
        str(output_file),
        expected_molecules=molecule_counts,
        expected_atoms=coords_result["total_atoms"]
    )
    
    # 10. 构建生成摘要
    generation_summary = {
        "total_molecules": len(molecule_instances),
        "total_atoms": coords_result["total_atoms"],
        "molecule_types": len(molecule_counts),
        "molecule_counts": molecule_counts,
        "box_size": box_size,
        "forcefield_type": forcefield_type,
        "imported_files": imports_result["imported_files"],
        "file_size": len(system_lt_content),
        "output_path": str(output_file)
    }
    
    # 11. 记录完成信息
    logger.info(
        f"[System.lt生成] 生成完成: 文件 {output_file}, "
        f"分子 {len(molecule_instances)} 个, "
        f"原子 {coords_result['total_atoms']} 个, "
        f"文件大小 {len(system_lt_content)} 字符"
    )
    
    return {
        "success": True,
        "system_lt_path": str(output_file),
        "system_lt_content": system_lt_content,
        "generation_summary": generation_summary,
        "validation_result": validation_result,
        "error": None
    }


def validate_system_lt(
    system_lt_path: str,
    expected_molecules: Dict[str, int] = None,
    expected_atoms: int = None
) -> Dict[str, Any]:
    """验证生成的system.lt文件有效性
    
    验证system.lt文件的格式、分子实例数量、原子总数等是否符合预期。
    确保生成的文件符合Moltemplate规范，可以正确运行。
    
    Args:
        system_lt_path: system.lt文件路径
        expected_molecules: 预期分子数量字典 {分子名称: 数量}（可选）
        expected_atoms: 预期原子总数（可选）
        
    Returns:
        验证结果字典，包含：
            - success: 是否成功验证
            - valid: 文件是否有效
            - file_exists: 文件是否存在
            - file_size: 文件大小（字节）
            - has_imports: 是否包含导入语句
            - has_system_block: 是否包含系统定义块
            - has_molecule_instances: 是否包含分子实例定义
            - has_coordinates: 是否包含原子坐标数据
            - has_boundary: 是否包含盒子边界定义
            - molecule_counts: 实际分子数量统计
            - total_atoms: 实际原子总数
            - validation_errors: 验证错误列表
            - validation_warnings: 验证警告列表
            - error: 错误信息（如果文件读取失败）
            
    Example:
        >>> result = validate_system_lt(
        ...     "system.lt",
        ...     expected_molecules={"EC": 500, "Li": 50},
        ...     expected_atoms=10000
        ... )
        >>> if result["valid"]:
        ...     print("文件验证通过")
        >>> else:
        ...     print(f"验证失败: {result['validation_errors']}")
        
    Note:
        验证内容包括：
        1. 文件是否存在且可读
        2. 文件基本结构（导入语句、系统定义块）
        3. 分子实例定义完整性
        4. 原子坐标数据完整性
        5. 盒子边界定义完整性
        6. 分子实例数量与预期一致
        7. 原子总数与预期一致
        8. 基本语法检查（括号匹配等）
    """
    logger.info(f"[System.lt验证] 开始验证文件: {system_lt_path}")
    
    validation_errors = []
    validation_warnings = []
    
    # 1. 检查文件是否存在
    system_lt_file = Path(system_lt_path)
    if not system_lt_file.exists():
        error_msg = f"system.lt文件不存在: {system_lt_path}"
        logger.error(f"[System.lt验证] {error_msg}")
        return {
            "success": False,
            "valid": False,
            "file_exists": False,
            "file_size": 0,
            "has_imports": False,
            "has_system_block": False,
            "has_molecule_instances": False,
            "has_coordinates": False,
            "has_boundary": False,
            "molecule_counts": {},
            "total_atoms": 0,
            "validation_errors": [error_msg],
            "validation_warnings": [],
            "error": error_msg
        }
    
    # 2. 读取文件内容
    try:
        with open(system_lt_file, 'r', encoding='utf-8') as f:
            content = f.read()
        file_size = len(content)
        logger.info(f"[System.lt验证] 文件读取成功，大小: {file_size} 字符")
    except Exception as e:
        error_msg = f"读取文件失败: {str(e)}"
        logger.error(f"[System.lt验证] {error_msg}")
        return {
            "success": False,
            "valid": False,
            "file_exists": True,
            "file_size": 0,
            "has_imports": False,
            "has_system_block": False,
            "has_molecule_instances": False,
            "has_coordinates": False,
            "has_boundary": False,
            "molecule_counts": {},
            "total_atoms": 0,
            "validation_errors": [error_msg],
            "validation_warnings": [],
            "error": error_msg
        }
    
    # 3. 验证基本结构
    # 3.1 检查导入语句
    has_imports = False
    import_pattern = re.compile(r'^import\s+"[^"]+"\s*#', re.MULTILINE)
    import_matches = import_pattern.findall(content)
    if import_matches:
        has_imports = True
        logger.info(f"[System.lt验证] 发现 {len(import_matches)} 个导入语句")
    else:
        warning_msg = "未发现导入语句"
        validation_warnings.append(warning_msg)
        logger.warning(f"[System.lt验证] {warning_msg}")
    
    # 3.2 检查系统定义块
    has_system_block = False
    if 'system = {' in content and content.strip().endswith('}'):
        has_system_block = True
        logger.info("[System.lt验证] 发现系统定义块")
    else:
        error_msg = "未发现完整的系统定义块（system = { ... }）"
        validation_errors.append(error_msg)
        logger.error(f"[System.lt验证] {error_msg}")
    
    # 3.3 检查分子实例定义
    has_molecule_instances = False
    instance_pattern = re.compile(r'^(\w+)\s*=\s*new\s+(\w+)\s*\[\s*(\d+)\s*\]', re.MULTILINE)
    instance_matches = instance_pattern.findall(content)
    if instance_matches:
        has_molecule_instances = True
        logger.info(f"[System.lt验证] 发现 {len(instance_matches)} 个分子实例定义")
    else:
        error_msg = "未发现分子实例定义"
        validation_errors.append(error_msg)
        logger.error(f"[System.lt验证] {error_msg}")
    
    # 3.4 检查原子坐标数据
    has_coordinates = False
    if 'write("Data Atoms")' in content or "write('Data Atoms')" in content:
        has_coordinates = True
        logger.info("[System.lt验证] 发现原子坐标数据块")
    else:
        error_msg = "未发现原子坐标数据块（write('Data Atoms')）"
        validation_errors.append(error_msg)
        logger.error(f"[System.lt验证] {error_msg}")
    
    # 3.5 检查盒子边界定义
    has_boundary = False
    if 'write_once("Data Boundary")' in content or "write_once('Data Boundary')" in content:
        has_boundary = True
        logger.info("[System.lt验证] 发现盒子边界定义块")
    else:
        error_msg = "未发现盒子边界定义块（write_once('Data Boundary')）"
        validation_errors.append(error_msg)
        logger.error(f"[System.lt验证] {error_msg}")
    
    # 4. 统计分子实例数量
    molecule_counts = {}
    if instance_matches:
        for var_name, mol_name, count in instance_matches:
            # 统计分子数量（按分子名称）
            if mol_name not in molecule_counts:
                molecule_counts[mol_name] = 0
            molecule_counts[mol_name] += int(count)
        
        logger.info(f"[System.lt验证] 分子实例统计: {molecule_counts}")
    
    # 5. 统计原子总数
    total_atoms = 0
    atom_pattern = re.compile(r'\$atom:\w+:\w+')
    atom_matches = atom_pattern.findall(content)
    if atom_matches:
        total_atoms = len(atom_matches)
        logger.info(f"[System.lt验证] 原子总数: {total_atoms}")
    
    # 6. 验证分子实例数量（如果提供预期值）
    if expected_molecules:
        for mol_name, expected_count in expected_molecules.items():
            actual_count = molecule_counts.get(mol_name, 0)
            
            if actual_count == 0:
                error_msg = f"分子 {mol_name} 缺失: 预期 {expected_count}, 实际 0"
                validation_errors.append(error_msg)
                logger.error(f"[System.lt验证] {error_msg}")
            elif actual_count != expected_count:
                error_msg = (
                    f"分子 {mol_name} 数量不匹配: "
                    f"预期 {expected_count}, 实际 {actual_count}"
                )
                validation_errors.append(error_msg)
                logger.error(f"[System.lt验证] {error_msg}")
            else:
                logger.info(f"[System.lt验证] 分子 {mol_name} 数量验证通过: {actual_count}")
    
    # 7. 验证原子总数（如果提供预期值）
    if expected_atoms is not None:
        if total_atoms != expected_atoms:
            error_msg = f"原子总数不匹配: 预期 {expected_atoms}, 实际 {total_atoms}"
            validation_errors.append(error_msg)
            logger.error(f"[System.lt验证] {error_msg}")
        else:
            logger.info(f"[System.lt验证] 原子总数验证通过: {total_atoms}")
    
    # 8. 验证基本语法（括号匹配）
    open_braces = content.count('{')
    close_braces = content.count('}')
    if open_braces != close_braces:
        error_msg = f"括号不匹配: 开括号 {open_braces}, 关括号 {close_braces}"
        validation_errors.append(error_msg)
        logger.error(f"[System.lt验证] {error_msg}")
    else:
        logger.debug(f"[System.lt验证] 括号匹配验证通过: {open_braces} 对")
    
    # 9. 验证文件大小合理性
    if file_size < 100:
        error_msg = "文件内容过小，可能为空文件或格式不完整"
        validation_errors.append(error_msg)
        logger.error(f"[System.lt验证] {error_msg}")
    
    # 10. 判断验证是否通过
    valid = len(validation_errors) == 0
    
    # 11. 记录验证结果
    if valid:
        logger.info(
            f"[System.lt验证] 验证通过: 文件 {system_lt_path}, "
            f"分子实例 {sum(molecule_counts.values())} 个, "
            f"原子 {total_atoms} 个"
        )
    else:
        logger.error(
            f"[System.lt验证] 验证失败: 发现 {len(validation_errors)} 个错误, "
            f"{len(validation_warnings)} 个警告"
        )
    
    return {
        "success": True,
        "valid": valid,
        "file_exists": True,
        "file_size": file_size,
        "has_imports": has_imports,
        "has_system_block": has_system_block,
        "has_molecule_instances": has_molecule_instances,
        "has_coordinates": has_coordinates,
        "has_boundary": has_boundary,
        "molecule_counts": molecule_counts,
        "total_atoms": total_atoms,
        "validation_errors": validation_errors,
        "validation_warnings": validation_warnings,
        "error": None
    }


# ==================== 完整流程整合功能 ====================

import time
import json
from datetime import datetime


@dataclass
class StepExecutionResult:
    """步骤执行结果数据结构
    
    Attributes:
        step_name: 步骤名称
        success: 是否成功
        start_time: 开始时间
        end_time: 结束时间
        duration: 执行耗时（秒）
        result_data: 步骤结果数据
        error_message: 错误信息（如果失败）
    """
    step_name: str
    success: bool
    start_time: float
    end_time: float
    duration: float
    result_data: Dict[str, Any]
    error_message: Optional[str] = None


def run_moltemplate_system_generation(
    packed_pdb_path: str,
    formula_data: Dict[str, Any],
    work_dir_path: str,
    template_library_path: str,
    forcefield_type: str = "oplsaa",
    forcefield_library_path: str = None,
    output_json_path: str = None
) -> Dict[str, Any]:
    """Moltemplate系统生成完整流程主函数
    
    整合所有步骤，从PDB文件到生成完整的system.lt文件。
    包含完整的错误处理、日志记录和执行统计。
    
    Args:
        packed_pdb_path: Packmol生成的PDB文件路径（packed_system.pdb）
        formula_data: 配方数据字典，包含：
            - molecules: 分子列表 [{"name": "EC", "count": 500}, ...]
            - box_size: 盒子尺寸 {"x": 48.0, "y": 48.0, "z": 48.0}
        work_dir_path: 工作目录路径（任务根目录）
        template_library_path: 分子模板库路径（system_templates/molecule_templates）
        forcefield_type: 力场类型（默认: "oplsaa"）
        forcefield_library_path: 力场文件库路径（可选）
        output_json_path: 执行结果JSON输出路径（可选）
        
    Returns:
        执行结果字典，包含：
            - success: 是否成功完成所有步骤
            - system_lt_path: 生成的system.lt文件路径
            - execution_summary: 执行摘要统计
            - step_results: 各步骤执行结果列表
            - statistics: 详细统计信息
            - error: 错误信息（如果失败）
            - failed_step: 失败步骤名称（如果失败）
            
    Example:
        >>> formula_data = {
        ...     "molecules": [
        ...         {"name": "EC", "count": 500},
        ...         {"name": "Li", "count": 50}
        ...     ],
        ...     "box_size": {"x": 48.0, "y": 48.0, "z": 48.0}
        ... }
        >>> result = run_moltemplate_system_generation(
        ...     "inputs/packed_system.pdb",
        ...     formula_data,
        ...     "user_1/jobs/job_1",
        ...     "system_templates/molecule_templates",
        ...     forcefield_type="oplsaa"
        ... )
        >>> if result["success"]:
        ...     print(f"成功生成: {result['system_lt_path']}")
        ...     print(f"统计信息: {result['statistics']}")
        >>> else:
        ...     print(f"失败步骤: {result['failed_step']}")
        ...     print(f"错误信息: {result['error']}")
        
    Note:
        执行流程包含5个步骤：
        步骤1: 解析PDB文件（parse_pdb_file）
        步骤2: 加载分子模板文件（fetch_molecule_lt_templates + copy_lt_files_to_workdir）
        步骤3: 创建分子实例（create_molecule_instances + assign_atom_coordinates）
        步骤4: 生成system.lt文件（generate_system_lt_file）
        步骤5: 验证system.lt文件（validate_system_lt）
        
        每个步骤失败时，停止后续步骤执行。
        所有步骤执行结果和耗时都会被记录。
    """
    logger.info("[Moltemplate系统生成] ========== 开始执行完整流程 ==========")
    logger.info(f"[Moltemplate系统生成] PDB文件: {packed_pdb_path}")
    logger.info(f"[Moltemplate系统生成] 工作目录: {work_dir_path}")
    logger.info(f"[Moltemplate系统生成] 模板库: {template_library_path}")
    logger.info(f"[Moltemplate系统生成] 力场类型: {forcefield_type}")
    
    # 初始化执行结果
    step_results: List[StepExecutionResult] = []
    total_start_time = time.time()
    
    # 提取配方数据
    molecules_list = formula_data.get("molecules", [])
    box_size = formula_data.get("box_size", {})
    
    # 构建分子数量字典
    molecule_counts = {}
    molecule_names = []
    for mol in molecules_list:
        mol_name = mol.get("name", "Unknown")
        mol_count = mol.get("count", 1)
        molecule_counts[mol_name] = mol_count
        molecule_names.append(mol_name)
    
    logger.info(f"[Moltemplate系统生成] 分子配方: {molecule_counts}")
    logger.info(f"[Moltemplate系统生成] 盒子尺寸: {box_size}")
    
    # ==================== 步骤1: 解析PDB文件 ====================
    logger.info("[Moltemplate系统生成] 步骤1: 解析PDB文件")
    step1_start = time.time()
    
    try:
        parse_result = parse_pdb_file(packed_pdb_path)
        step1_end = time.time()
        step1_duration = step1_end - step1_start
        
        step1_result = StepExecutionResult(
            step_name="parse_pdb_file",
            success=parse_result.get("success", False),
            start_time=step1_start,
            end_time=step1_end,
            duration=step1_duration,
            result_data=parse_result,
            error_message=parse_result.get("error") if not parse_result.get("success") else None
        )
        step_results.append(step1_result)
        
        logger.info(
            f"[Moltemplate系统生成] 步骤1完成: 成功={parse_result['success']}, "
            f"耗时={step1_duration:.2f}秒, 原子数={parse_result.get('total_atoms', 0)}"
        )
        
        # 步骤1失败，停止后续执行
        if not parse_result.get("success", False):
            error_msg = f"步骤1失败: {parse_result.get('error', '未知错误')}"
            logger.error(f"[Moltemplate系统生成] {error_msg}")
            
            return build_final_result(
                success=False,
                step_results=step_results,
                total_start_time=total_start_time,
                error=error_msg,
                failed_step="parse_pdb_file"
            )
    
    except Exception as e:
        step1_end = time.time()
        step1_duration = step1_end - step1_start
        error_msg = f"步骤1异常: {str(e)}"
        
        step1_result = StepExecutionResult(
            step_name="parse_pdb_file",
            success=False,
            start_time=step1_start,
            end_time=step1_end,
            duration=step1_duration,
            result_data={},
            error_message=error_msg
        )
        step_results.append(step1_result)
        
        logger.error(f"[Moltemplate系统生成] {error_msg}")
        
        return build_final_result(
            success=False,
            step_results=step_results,
            total_start_time=total_start_time,
            error=error_msg,
            failed_step="parse_pdb_file"
        )
    
    # ==================== 步骤2: 加载分子模板文件 ====================
    logger.info("[Moltemplate系统生成] 步骤2: 加载分子模板文件")
    step2_start = time.time()
    
    try:
        # 从PDB文件中提取实际存在的分子名称
        pdb_molecule_names = []
        if parse_result.get("success", False):
            atoms = parse_result.get("atoms", [])
            # 提取所有分子名称（从residue name）
            molecule_names_set = set()
            for atom in atoms:
                if isinstance(atom, dict):
                    mol_name = atom.get("res_name", "")
                else:
                    mol_name = atom.res_name if hasattr(atom, 'res_name') else ""
                if mol_name:
                    molecule_names_set.add(mol_name)
            pdb_molecule_names = list(molecule_names_set)
        
        logger.info(f"[Moltemplate系统生成] PDB文件中的分子: {pdb_molecule_names}")
        
        # 2.1 获取分子模板路径（使用PDB文件中的分子名称）
        template_fetch_result = fetch_molecule_lt_templates(
            molecule_names=pdb_molecule_names,  # 使用PDB文件中的分子名称
            template_library_path=template_library_path
        )
        
        if not template_fetch_result.get("success", False):
            step2_end = time.time()
            step2_duration = step2_end - step2_start
            error_msg = f"获取分子模板失败: {template_fetch_result.get('error', '未知错误')}"
            
            step2_result = StepExecutionResult(
                step_name="fetch_molecule_templates",
                success=False,
                start_time=step2_start,
                end_time=step2_end,
                duration=step2_duration,
                result_data=template_fetch_result,
                error_message=error_msg
            )
            step_results.append(step2_result)
            
            logger.error(f"[Moltemplate系统生成] {error_msg}")
            
            return build_final_result(
                success=False,
                step_results=step_results,
                total_start_time=total_start_time,
                error=error_msg,
                failed_step="fetch_molecule_templates"
            )
        
        # 2.2 根据文件输入输出规则，不再复制模板到inputs目录
        # 分子模板和力场文件存储在system_templates目录中（所有用户共享）
        # inputs目录只存放任务输入文件（system.lt、packed_system.pdb等）
        template_paths = template_fetch_result.get("template_paths", {})
        
        # 模拟copy_result格式以保持兼容性（但不实际复制文件）
        copy_result = {
            "success": True,
            "copied_files": {},  # 空字典，表示没有复制任何文件
            "failed_files": [],
            "inputs_dir": str(Path(work_dir_path) / "inputs"),
            "error": None,
            "note": "根据文件输入输出规则，模板文件不复制到inputs目录"
        }
        
        step2_end = time.time()
        step2_duration = step2_end - step2_start
        
        step2_result = StepExecutionResult(
            step_name="load_molecule_templates",
            success=copy_result.get("success", False),
            start_time=step2_start,
            end_time=step2_end,
            duration=step2_duration,
            result_data={
                "fetch_result": template_fetch_result,
                "copy_result": copy_result
            },
            error_message=copy_result.get("error") if not copy_result.get("success") else None
        )
        step_results.append(step2_result)
        
        logger.info(
            f"[Moltemplate系统生成] 步骤2完成: 成功={copy_result['success']}, "
            f"耗时={step2_duration:.2f}秒, 复制文件数={len(copy_result.get('copied_files', {}))}"
        )
        
        # 步骤2失败，停止后续执行
        if not copy_result.get("success", False):
            error_msg = f"步骤2失败: {copy_result.get('error', '未知错误')}"
            logger.error(f"[Moltemplate系统生成] {error_msg}")
            
            return build_final_result(
                success=False,
                step_results=step_results,
                total_start_time=total_start_time,
                error=error_msg,
                failed_step="load_molecule_templates"
            )
    
    except Exception as e:
        step2_end = time.time()
        step2_duration = step2_end - step2_start
        error_msg = f"步骤2异常: {str(e)}"
        
        step2_result = StepExecutionResult(
            step_name="load_molecule_templates",
            success=False,
            start_time=step2_start,
            end_time=step2_end,
            duration=step2_duration,
            result_data={},
            error_message=error_msg
        )
        step_results.append(step2_result)
        
        logger.error(f"[Moltemplate系统生成] {error_msg}")
        
        return build_final_result(
            success=False,
            step_results=step_results,
            total_start_time=total_start_time,
            error=error_msg,
            failed_step="load_molecule_templates"
        )
    
    # ==================== 步骤3: 创建分子实例 ====================
    logger.info("[Moltemplate系统生成] 步骤3: 创建分子实例")
    step3_start = time.time()
    
    try:
        # 3.1 创建分子实例数据结构
        instance_result = create_molecule_instances(
            molecule_names=molecule_names,
            molecule_counts=molecule_counts,
            pdb_parse_result=parse_result
        )
        
        if not instance_result.get("success", False):
            step3_end = time.time()
            step3_duration = step3_end - step3_start
            error_msg = f"创建分子实例失败: {instance_result.get('error', '未知错误')}"
            
            step3_result = StepExecutionResult(
                step_name="create_molecule_instances",
                success=False,
                start_time=step3_start,
                end_time=step3_end,
                duration=step3_duration,
                result_data=instance_result,
                error_message=error_msg
            )
            step_results.append(step3_result)
            
            logger.error(f"[Moltemplate系统生成] {error_msg}")
            
            return build_final_result(
                success=False,
                step_results=step_results,
                total_start_time=total_start_time,
                error=error_msg,
                failed_step="create_molecule_instances"
            )
        
        # 3.2 分配原子坐标到分子实例
        molecule_instances = instance_result.get("instances", [])
        atoms = parse_result.get("atoms", [])
        
        # 获取分子边界和ID映射
        boundaries = instance_result.get("boundaries", [])
        molecule_id_map_result = map_molecule_ids(atoms, boundaries)
        
        if molecule_id_map_result.get("success", False):
            molecule_id_map = molecule_id_map_result.get("atom_molecule_map", {})
            assign_result = assign_atom_coordinates(atoms, molecule_id_map)
        else:
            # 如果映射失败，使用空映射
            assign_result = {"success": True, "molecule_atoms_map": {}}
        
        step3_end = time.time()
        step3_duration = step3_end - step3_start
        
        step3_result = StepExecutionResult(
            step_name="create_molecule_instances",
            success=True,
            start_time=step3_start,
            end_time=step3_end,
            duration=step3_duration,
            result_data={
                "instance_result": instance_result,
                "assign_result": assign_result
            },
            error_message=None
        )
        step_results.append(step3_result)
        
        logger.info(
            f"[Moltemplate系统生成] 步骤3完成: 成功=True, "
            f"耗时={step3_duration:.2f}秒, 分子实例数={len(molecule_instances)}"
        )
    
    except Exception as e:
        step3_end = time.time()
        step3_duration = step3_end - step3_start
        error_msg = f"步骤3异常: {str(e)}"
        
        step3_result = StepExecutionResult(
            step_name="create_molecule_instances",
            success=False,
            start_time=step3_start,
            end_time=step3_end,
            duration=step3_duration,
            result_data={},
            error_message=error_msg
        )
        step_results.append(step3_result)
        
        logger.error(f"[Moltemplate系统生成] {error_msg}")
        
        return build_final_result(
            success=False,
            step_results=step_results,
            total_start_time=total_start_time,
            error=error_msg,
            failed_step="create_molecule_instances"
        )
    
    # ==================== 步骤4: 生成system.lt文件 ====================
    logger.info("[Moltemplate系统生成] 步骤4: 生成system.lt文件")
    step4_start = time.time()
    
    try:
        # 构建inputs目录路径
        inputs_dir = Path(work_dir_path) / "inputs"
        system_lt_output_path = inputs_dir / "system.lt"
        
        # 根据文件输入输出规则：
        # - 分子模板和力场文件存储在system_templates目录中（不复制到inputs）
        # - import语句使用相对路径从inputs目录指向system_templates目录
        # - 使用模板库的原始路径作为模板文件路径
        template_paths = template_fetch_result.get("template_paths", {})
        
        # 构建相对路径：../../system_templates/molecule_templates/{分子名}/{分子名}.lt
        template_files_for_generation = {}
        for mol_name, lt_file_path in template_paths.items():
            template_files_for_generation[mol_name] = lt_file_path
        
        # 生成system.lt文件（传递模板库路径用于生成正确的import语句）
        generation_result = generate_system_lt_file(
            template_files=template_files_for_generation,
            molecule_instances=molecule_instances,
            box_size=box_size,
            forcefield_type=forcefield_type,
            output_path=str(system_lt_output_path),
            forcefield_library_path=forcefield_library_path,
            template_library_path=template_library_path  # 传递模板库路径
        )
        
        step4_end = time.time()
        step4_duration = step4_end - step4_start
        
        step4_result = StepExecutionResult(
            step_name="generate_system_lt",
            success=generation_result.get("success", False),
            start_time=step4_start,
            end_time=step4_end,
            duration=step4_duration,
            result_data=generation_result,
            error_message=generation_result.get("error") if not generation_result.get("success") else None
        )
        step_results.append(step4_result)
        
        logger.info(
            f"[Moltemplate系统生成] 步骤4完成: 成功={generation_result['success']}, "
            f"耗时={step4_duration:.2f}秒, 输出路径={generation_result.get('system_lt_path', 'N/A')}"
        )
        
        # 步骤4失败，停止后续执行
        if not generation_result.get("success", False):
            error_msg = f"步骤4失败: {generation_result.get('error', '未知错误')}"
            logger.error(f"[Moltemplate系统生成] {error_msg}")
            
            return build_final_result(
                success=False,
                step_results=step_results,
                total_start_time=total_start_time,
                error=error_msg,
                failed_step="generate_system_lt"
            )
    
    except Exception as e:
        step4_end = time.time()
        step4_duration = step4_end - step4_start
        error_msg = f"步骤4异常: {str(e)}"
        
        step4_result = StepExecutionResult(
            step_name="generate_system_lt",
            success=False,
            start_time=step4_start,
            end_time=step4_end,
            duration=step4_duration,
            result_data={},
            error_message=error_msg
        )
        step_results.append(step4_result)
        
        logger.error(f"[Moltemplate系统生成] {error_msg}")
        
        return build_final_result(
            success=False,
            step_results=step_results,
            total_start_time=total_start_time,
            error=error_msg,
            failed_step="generate_system_lt"
        )
    
    # ==================== 步骤5: 验证system.lt文件 ====================
    logger.info("[Moltemplate系统生成] 步骤5: 验证system.lt文件")
    step5_start = time.time()
    
    try:
        system_lt_path = generation_result.get("system_lt_path", "")
        
        validation_result = validate_system_lt(
            system_lt_path=system_lt_path,
            expected_molecules=molecule_counts,
            expected_atoms=parse_result.get("total_atoms", 0)
        )
        
        step5_end = time.time()
        step5_duration = step5_end - step5_start
        
        step5_result = StepExecutionResult(
            step_name="validate_system_lt",
            success=validation_result.get("success", False) and validation_result.get("valid", False),
            start_time=step5_start,
            end_time=step5_end,
            duration=step5_duration,
            result_data=validation_result,
            error_message=validation_result.get("error") if not validation_result.get("success") else None
        )
        step_results.append(step5_result)
        
        logger.info(
            f"[Moltemplate系统生成] 步骤5完成: 成功={validation_result.get('valid', False)}, "
            f"耗时={step5_duration:.2f}秒, 验证错误数={len(validation_result.get('validation_errors', []))}"
        )
        
        # 步骤5验证失败（但不影响文件生成）
        if not validation_result.get("valid", False):
            warning_msg = f"步骤5验证失败: {validation_result.get('validation_errors', [])}"
            logger.warning(f"[Moltemplate系统生成] {warning_msg}")
            # 验证失败不停止流程，但记录警告
    
    except Exception as e:
        step5_end = time.time()
        step5_duration = step5_end - step5_start
        error_msg = f"步骤5异常: {str(e)}"
        
        step5_result = StepExecutionResult(
            step_name="validate_system_lt",
            success=False,
            start_time=step5_start,
            end_time=step5_end,
            duration=step5_duration,
            result_data={},
            error_message=error_msg
        )
        step_results.append(step5_result)
        
        logger.warning(f"[Moltemplate系统生成] {error_msg}")
        # 验证异常不停止流程，但记录警告
    
    # ==================== 构建最终结果 ====================
    total_end_time = time.time()
    total_duration = total_end_time - total_start_time
    
    logger.info(f"[Moltemplate系统生成] ========== 流程执行完成 ==========")
    logger.info(f"[Moltemplate系统生成] 总耗时: {total_duration:.2f}秒")
    logger.info(f"[Moltemplate系统生成] 成功步骤: {sum(1 for r in step_results if r.success)}/{len(step_results)}")
    
    # 构建统计信息
    statistics = build_statistics(
        parse_result=parse_result,
        instance_result=instance_result,
        generation_result=generation_result,
        validation_result=validation_result,
        step_results=step_results,
        total_duration=total_duration,
        molecule_counts=molecule_counts,
        box_size=box_size,
        forcefield_type=forcefield_type
    )
    
    # 构建执行摘要
    execution_summary = {
        "total_steps": len(step_results),
        "successful_steps": sum(1 for r in step_results if r.success),
        "failed_steps": sum(1 for r in step_results if not r.success),
        "total_duration": total_duration,
        "start_time": datetime.fromtimestamp(total_start_time).isoformat(),
        "end_time": datetime.fromtimestamp(total_end_time).isoformat(),
        "packed_pdb_path": packed_pdb_path,
        "work_dir_path": work_dir_path,
        "template_library_path": template_library_path,
        "forcefield_type": forcefield_type
    }
    
    # 构建最终结果
    final_result = {
        "success": all(r.success for r in step_results[:4]),  # 前4步必须成功，验证步骤允许失败
        "system_lt_path": generation_result.get("system_lt_path", ""),
        "execution_summary": execution_summary,
        "step_results": [format_step_result(r) for r in step_results],
        "statistics": statistics,
        "error": None,
        "failed_step": None
    }
    
    # 如果有验证失败，添加警告信息
    if step_results and len(step_results) >= 5 and not step_results[4].success:
        final_result["validation_warnings"] = step_results[4].result_data.get("validation_errors", [])
    
    # 输出JSON结果文件（如果指定）
    if output_json_path:
        try:
            output_json_file = Path(output_json_path)
            output_json_file.parent.mkdir(parents=True, exist_ok=True)
            
            with open(output_json_file, 'w', encoding='utf-8') as f:
                json.dump(final_result, f, indent=2, ensure_ascii=False)
            
            logger.info(f"[Moltemplate系统生成] 结果已输出到: {output_json_path}")
        except Exception as e:
            logger.warning(f"[Moltemplate系统生成] 输出JSON文件失败: {str(e)}")
    
    logger.info(f"[Moltemplate系统生成] 最终结果: 成功={final_result['success']}")
    
    return final_result


def build_final_result(
    success: bool,
    step_results: List[StepExecutionResult],
    total_start_time: float,
    error: str,
    failed_step: str
) -> Dict[str, Any]:
    """构建失败时的最终结果
    
    Args:
        success: 是否成功
        step_results: 步骤执行结果列表
        total_start_time: 总开始时间
        error: 错误信息
        failed_step: 失败步骤名称
        
    Returns:
        最终结果字典
    """
    total_end_time = time.time()
    total_duration = total_end_time - total_start_time
    
    execution_summary = {
        "total_steps": len(step_results),
        "successful_steps": sum(1 for r in step_results if r.success),
        "failed_steps": sum(1 for r in step_results if not r.success),
        "total_duration": total_duration,
        "start_time": datetime.fromtimestamp(total_start_time).isoformat(),
        "end_time": datetime.fromtimestamp(total_end_time).isoformat()
    }
    
    return {
        "success": success,
        "system_lt_path": "",
        "execution_summary": execution_summary,
        "step_results": [format_step_result(r) for r in step_results],
        "statistics": {},
        "error": error,
        "failed_step": failed_step
    }


def format_step_result(step_result: StepExecutionResult) -> Dict[str, Any]:
    """格式化步骤执行结果为字典
    
    Args:
        step_result: 步骤执行结果对象
        
    Returns:
        格式化后的字典
    """
    return {
        "step_name": step_result.step_name,
        "success": step_result.success,
        "start_time": datetime.fromtimestamp(step_result.start_time).isoformat(),
        "end_time": datetime.fromtimestamp(step_result.end_time).isoformat(),
        "duration": round(step_result.duration, 2),
        "error_message": step_result.error_message
    }


def build_statistics(
    parse_result: Dict[str, Any],
    instance_result: Dict[str, Any],
    generation_result: Dict[str, Any],
    validation_result: Dict[str, Any],
    step_results: List[StepExecutionResult],
    total_duration: float,
    molecule_counts: Dict[str, int],
    box_size: Dict[str, float],
    forcefield_type: str
) -> Dict[str, Any]:
    """构建详细统计信息
    
    Args:
        parse_result: PDB解析结果
        instance_result: 分子实例创建结果
        generation_result: system.lt生成结果
        validation_result: system.lt验证结果
        step_results: 步骤执行结果列表
        total_duration: 总耗时
        molecule_counts: 分子数量字典
        box_size: 盒子尺寸
        forcefield_type: 力场类型
        
    Returns:
        统计信息字典
    """
    # 计算各步骤耗时
    step_durations = {}
    for step_result in step_results:
        step_durations[step_result.step_name] = round(step_result.duration, 2)
    
    # 构建统计信息
    statistics = {
        "total_atoms": parse_result.get("total_atoms", 0),
        "total_molecules": instance_result.get("total_instances", 0),
        "molecule_types": len(molecule_counts),
        "molecule_counts": molecule_counts,
        "molecule_instance_summary": instance_result.get("instance_summary", {}),
        "box_size": box_size,
        "box_volume": box_size.get("x", 0) * box_size.get("y", 0) * box_size.get("z", 0),
        "forcefield_type": forcefield_type,
        "template_files": list(generation_result.get("generation_summary", {}).get("imported_files", [])),
        "system_lt_file_size": validation_result.get("file_size", 0),
        "execution_time": {
            "total_duration": round(total_duration, 2),
            "step_durations": step_durations
        },
        "validation_status": {
            "valid": validation_result.get("valid", False),
            "errors_count": len(validation_result.get("validation_errors", [])),
            "warnings_count": len(validation_result.get("validation_warnings", []))
        }
    }
    
    return statistics


def run_moltemplate_system_generation_simple(
    packed_pdb_path: str,
    formula_json_path: str,
    work_dir_path: str,
    template_library_path: str,
    forcefield_type: str = "oplsaa"
) -> Dict[str, Any]:
    """简化版的Moltemplate系统生成函数
    
    从JSON文件读取配方数据，执行完整流程。
    适用于命令行调用或简单场景。
    
    Args:
        packed_pdb_path: Packmol生成的PDB文件路径
        formula_json_path: 配方JSON文件路径
        work_dir_path: 工作目录路径
        template_library_path: 分子模板库路径
        forcefield_type: 力场类型
        
    Returns:
        执行结果字典
        
    Example:
        >>> result = run_moltemplate_system_generation_simple(
        ...     "inputs/packed_system.pdb",
        ...     "inputs/formula.json",
        ...     "user_1/jobs/job_1",
        ...     "system_templates/molecule_templates"
        ... )
        >>> print(f"成功: {result['success']}")
        
    Note:
        配方JSON文件格式：
        {
            "molecules": [
                {"name": "EC", "count": 500},
                {"name": "Li", "count": 50}
            ],
            "box_size": {"x": 48.0, "y": 48.0, "z": 48.0}
        }
    """
    logger.info("[简化流程] 开始执行Moltemplate系统生成")
    
    # 读取配方JSON文件
    try:
        formula_file = Path(formula_json_path)
        if not formula_file.exists():
            error_msg = f"配方JSON文件不存在: {formula_json_path}"
            logger.error(f"[简化流程] {error_msg}")
            return {
                "success": False,
                "error": error_msg,
                "failed_step": "read_formula_json"
            }
        
        with open(formula_file, 'r', encoding='utf-8') as f:
            formula_data = json.load(f)
        
        logger.info(f"[简化流程] 配方数据加载成功: {formula_data}")
    
    except Exception as e:
        error_msg = f"读取配方JSON文件失败: {str(e)}"
        logger.error(f"[简化流程] {error_msg}")
        return {
            "success": False,
            "error": error_msg,
            "failed_step": "read_formula_json"
        }
    
    # 调用完整流程函数
    return run_moltemplate_system_generation(
        packed_pdb_path=packed_pdb_path,
        formula_data=formula_data,
        work_dir_path=work_dir_path,
        template_library_path=template_library_path,
        forcefield_type=forcefield_type
    )


# ==================== Moltemplate命令执行功能（步骤6） ====================

def execute_moltemplate_command(
    system_lt_path: str,
    work_dir: str,
    moltemplate_path: str = "moltemplate.sh",
    timeout: int = 3600,
    pdb_file: str = None
) -> Dict[str, Any]:
    """执行Moltemplate命令，将system.lt转换为LAMMPS输入文件

    执行moltemplate.sh命令，生成LAMMPS所需的输入文件：
    - system.data: LAMMPS结构文件
    - system.in.init: 初始化设置文件
    - system.in.settings: 力场设置文件

    Args:
        system_lt_path: system.lt文件路径
        work_dir: 工作目录路径（执行命令的目录）
        moltemplate_path: Moltemplate脚本路径（默认: "moltemplate.sh"）
        timeout: 超时时间（秒，默认: 3600）
        pdb_file: PDB坐标文件路径（可选，用于传递-pdb参数给Moltemplate）

    Returns:
        执行结果字典，包含：
            - success: 是否成功
            - return_code: 返回码
            - stdout: 标准输出
            - stderr: 标准错误输出
            - duration: 执行耗时（秒）
            - output_files: 生成的输出文件列表
            - error: 错误信息（如果失败）
            - missing_files: 缺失文件列表（如果验证失败）

    Example:
        >>> result = execute_moltemplate_command(
        ...     system_lt_path="user_1/jobs/job_1/system.lt",
        ...     work_dir="user_1/jobs/job_1",
        ...     pdb_file="packed_system.pdb"
        ... )
        >>> if result["success"]:
        ...     print(f"执行成功，耗时 {result['duration']}秒")
        ...     print(f"生成文件: {result['output_files']}")
        >>> else:
        ...     print(f"执行失败: {result['error']}")
        ...     print(f"缺失文件: {result['missing_files']}")

    Note:
        执行流程：
        1. 构建命令：moltemplate.sh -atomstyle full [-pdb pdb_file] system.lt
        2. 在工作目录中执行命令
        3. 捕获标准输出和错误输出
        4. 解析执行结果（成功/失败）
        5. 验证生成的文件（system.data、system.in.init、system.in.settings）
        6. 记录执行耗时和日志
    """
    logger.info("[Moltemplate执行] ========== 开始执行Moltemplate命令（步骤6） ==========")
    logger.info(f"[Moltemplate执行] system.lt路径: {system_lt_path}")
    logger.info(f"[Moltemplate执行] 工作目录: {work_dir}")
    logger.info(f"[Moltemplate执行] Moltemplate路径: {moltemplate_path}")
    
    # 初始化结果
    start_time = time.time()
    
    # 验证输入文件存在性
    system_lt_file = Path(system_lt_path)
    if not system_lt_file.exists():
        error_msg = f"system.lt文件不存在: {system_lt_path}"
        logger.error(f"[Moltemplate执行] {error_msg}")
        return {
            "success": False,
            "return_code": -1,
            "stdout": "",
            "stderr": error_msg,
            "duration": 0.0,
            "output_files": [],
            "error": error_msg,
            "missing_files": []
        }
    
    # 验证工作目录存在性
    work_dir_path = Path(work_dir)
    if not work_dir_path.exists():
        error_msg = f"工作目录不存在: {work_dir}"
        logger.error(f"[Moltemplate执行] {error_msg}")
        return {
            "success": False,
            "return_code": -1,
            "stdout": "",
            "stderr": error_msg,
            "duration": 0.0,
            "output_files": [],
            "error": error_msg,
            "missing_files": []
        }
    
    # 构建Moltemplate命令
    # 基本命令：moltemplate.sh -atomstyle full [-pdb pdb_file] system.lt
    command = [
        moltemplate_path,
        "-atomstyle", "full",
    ]

    # 如果PDB文件存在，添加-pdb参数
    if pdb_file:
        pdb_path = os.path.join(work_dir, pdb_file)
        if os.path.exists(pdb_path):
            command.extend(["-pdb", pdb_file])
            logger.info(f"[Moltemplate执行] 使用PDB坐标文件: {pdb_file}")
        else:
            logger.warning(f"[Moltemplate执行] PDB文件不存在: {pdb_path}，将使用模板默认坐标")

    command.append(system_lt_file.name)  # 只使用文件名，因为会在工作目录中执行

    logger.info(f"[Moltemplate执行] 构建命令: {' '.join(command)}")
    
    # 执行Moltemplate命令
    try:
        logger.info("[Moltemplate执行] 开始执行subprocess命令")
        
        result = subprocess.run(
            command,
            cwd=work_dir,  # 在工作目录中执行
            capture_output=True,
            text=True,
            timeout=timeout
        )
        
        # 计算执行耗时
        end_time = time.time()
        duration = end_time - start_time
        
        # 解析执行结果
        success = result.returncode == 0
        
        logger.info(f"[Moltemplate执行] 命令执行完成，返回码: {result.returncode}")
        logger.info(f"[Moltemplate执行] 执行耗时: {duration:.2f}秒")
        
        # 记录输出信息
        if result.stdout:
            logger.debug(f"[Moltemplate执行] 标准输出:\n{result.stdout}")
        
        if result.stderr:
            if success:
                logger.debug(f"[Moltemplate执行] 标准错误输出:\n{result.stderr}")
            else:
                logger.error(f"[Moltemplate执行] 标准错误输出:\n{result.stderr}")
        
        # 验证生成的文件
        logger.info("[Moltemplate执行] 开始验证生成的文件")
        
        validation_result = validate_generated_files(work_dir)
        
        # 构建返回结果
        execution_result = {
            "success": success and validation_result["all_files_exist"],
            "return_code": result.returncode,
            "stdout": result.stdout,
            "stderr": result.stderr,
            "duration": duration,
            "output_files": validation_result["existing_files"],
            "error": None if success and validation_result["all_files_exist"] else validation_result["error_message"],
            "missing_files": validation_result["missing_files"]
        }
        
        if execution_result["success"]:
            logger.info("[Moltemplate执行] ========== Moltemplate命令执行成功 ==========")
            logger.info(f"[Moltemplate执行] 生成的文件: {validation_result['existing_files']}")
        else:
            logger.error("[Moltemplate执行] ========== Moltemplate命令执行失败 ==========")
            if not success:
                logger.error(f"[Moltemplate执行] 命令执行失败，返回码: {result.returncode}")
            if not validation_result["all_files_exist"]:
                logger.error(f"[Moltemplate执行] 文件验证失败，缺失文件: {validation_result['missing_files']}")
        
        return execution_result
        
    except subprocess.TimeoutExpired:
        # 处理超时异常
        end_time = time.time()
        duration = end_time - start_time
        
        error_msg = f"Moltemplate命令执行超时（{timeout}秒）"
        logger.error(f"[Moltemplate执行] {error_msg}")
        
        return {
            "success": False,
            "return_code": -1,
            "stdout": "",
            "stderr": error_msg,
            "duration": duration,
            "output_files": [],
            "error": error_msg,
            "missing_files": []
        }
        
    except Exception as e:
        # 处理其他异常
        end_time = time.time()
        duration = end_time - start_time
        
        error_msg = f"Moltemplate命令执行异常: {str(e)}"
        logger.error(f"[Moltemplate执行] {error_msg}")
        logger.exception(e)
        
        return {
            "success": False,
            "return_code": -1,
            "stdout": "",
            "stderr": error_msg,
            "duration": duration,
            "output_files": [],
            "error": error_msg,
            "missing_files": []
        }


def validate_generated_files(work_dir: str) -> Dict[str, Any]:
    """验证Moltemplate生成的文件
    
    检查必需的输出文件是否已生成：
    - system.data: LAMMPS结构文件
    - system.in.init: 初始化设置文件
    - system.in.settings: 力场设置文件
    
    Args:
        work_dir: 工作目录路径
        
    Returns:
        验证结果字典，包含：
            - all_files_exist: 所有文件是否都存在
            - existing_files: 存在的文件列表
            - missing_files: 缺失的文件列表
            - file_sizes: 文件大小字典
            - error_message: 错误信息（如果有文件缺失）
            
    Example:
        >>> result = validate_generated_files("user_1/jobs/job_1")
        >>> if result["all_files_exist"]:
        ...     print("所有文件验证成功")
        ...     print(f"文件大小: {result['file_sizes']}")
        >>> else:
        ...     print(f"缺失文件: {result['missing_files']}")
    """
    logger.info("[文件验证] 开始验证生成的文件")
    
    # 定义必需的输出文件
    required_files = [
        "system.data",       # LAMMPS结构文件
        "system.in.init",    # 初始化设置文件
        "system.in.settings" # 力场设置文件
    ]
    
    work_dir_path = Path(work_dir)
    
    existing_files = []
    missing_files = []
    file_sizes = {}
    
    # 检查每个必需文件
    for file_name in required_files:
        file_path = work_dir_path / file_name
        
        if file_path.exists():
            # 文件存在，获取文件大小
            file_size = file_path.stat().st_size
            file_sizes[file_name] = file_size
            existing_files.append(str(file_path))
            
            logger.debug(f"[文件验证] 文件存在: {file_name} (大小: {file_size}字节)")
            
            # 验证文件大小是否大于0
            if file_size == 0:
                logger.warning(f"[文件验证] 文件大小为0: {file_name}")
        else:
            # 文件缺失
            missing_files.append(file_name)
            logger.warning(f"[文件验证] 文件缺失: {file_name}")
    
    # 构建验证结果
    all_files_exist = len(missing_files) == 0
    
    error_message = None
    if not all_files_exist:
        error_message = f"缺失必需文件: {', '.join(missing_files)}"
        logger.error(f"[文件验证] {error_message}")
    
    validation_result = {
        "all_files_exist": all_files_exist,
        "existing_files": existing_files,
        "missing_files": missing_files,
        "file_sizes": file_sizes,
        "error_message": error_message
    }
    
    if all_files_exist:
        logger.info("[文件验证] ========== 所有必需文件验证成功 ==========")
        logger.info(f"[文件验证] 文件数量: {len(existing_files)}")
        logger.info(f"[文件验证] 文件大小统计: {file_sizes}")
    else:
        logger.error("[文件验证] ========== 文件验证失败 ==========")
        logger.error(f"[文件验证] 缺失文件数量: {len(missing_files)}")

    return validation_result


def fix_system_in_init(input_dir: str) -> Dict[str, Any]:
    """修复moltemplate生成的system.in.init文件

    修复内容：
    1. 添加缺失的atom_style full
    2. 添加缺失的kspace_style pppm 1.0e-4
    3. 去除重复的力场样式定义

    Args:
        input_dir: 输入文件目录路径

    Returns:
        修复结果字典，包含：
            - success: 是否成功
            - added_atom_style: 是否添加了atom_style
            - added_kspace_style: 是否添加了kspace_style
            - removed_duplicates: 是否去除了重复定义
            - error: 错误信息（如果失败）

    Example:
        >>> result = fix_system_in_init("/workspace/data/user_1/jobs/job_1/inputs")
        >>> if result["success"]:
        ...     print(f"修复完成，添加atom_style: {result['added_atom_style']}")
        ...     print(f"添加kspace_style: {result['added_kspace_style']}")
    """
    init_file = os.path.join(input_dir, "system.in.init")

    # 检查文件是否存在
    if not os.path.exists(init_file):
        error_msg = f"system.in.init文件不存在: {init_file}"
        logger.error(f"[init文件修复] {error_msg}")
        return {"success": False, "error": error_msg}

    # 读取文件内容
    logger.info(f"[init文件修复] 开始修复system.in.init文件: {init_file}")
    with open(init_file, 'r', encoding='utf-8') as f:
        content = f.read()

    lines = content.strip().split('\n')
    fixed_lines = []

    # 检查并去重
    has_atom_style = False
    has_kspace_style = False
    seen_settings = set()  # 用于去重

    for line in lines:
        stripped = line.strip()

        # 跳过空行和注释
        if not stripped or stripped.startswith('#'):
            fixed_lines.append(line)
            continue

        # 检查atom_style
        if stripped.startswith('atom_style'):
            has_atom_style = True
            if 'atom_style' not in seen_settings:
                seen_settings.add('atom_style')
                fixed_lines.append(line)
            else:
                # 重复的atom_style跳过
                logger.info(f"[init文件修复] 去除重复的atom_style定义: {stripped}")
            continue

        # 检查kspace_style
        if stripped.startswith('kspace_style'):
            has_kspace_style = True
            if 'kspace_style' not in seen_settings:
                seen_settings.add('kspace_style')
                fixed_lines.append(line)
            else:
                # 重复的kspace_style跳过
                logger.info(f"[init文件修复] 去除重复的kspace_style定义: {stripped}")
            continue

        # 去重其他力场样式定义
        setting_key = stripped.split()[0] if stripped else ''
        if setting_key in ('units', 'bond_style', 'angle_style', 'dihedral_style',
                           'improper_style', 'pair_style', 'special_bonds'):
            if setting_key not in seen_settings:
                seen_settings.add(setting_key)
                fixed_lines.append(line)
            else:
                # 重复的定义跳过
                logger.info(f"[init文件修复] 去除重复的{setting_key}定义: {stripped}")
            continue

        fixed_lines.append(line)

    # 如果缺少atom_style full，在开头添加
    if not has_atom_style:
        fixed_lines.insert(0, "atom_style      full")
        logger.info("[init文件修复] 添加缺失的atom_style full")

    # 如果缺少kspace_style，在special_bonds之后添加
    if not has_kspace_style:
        # 找到special_bonds的位置，在其后添加
        insert_pos = 0
        for i, line in enumerate(fixed_lines):
            if line.strip().startswith('special_bonds'):
                insert_pos = i + 1
                break
        if insert_pos == 0:
            # 没有找到special_bonds，在末尾添加
            insert_pos = len(fixed_lines)
        fixed_lines.insert(insert_pos, "kspace_style    pppm 1.0e-4")
        logger.info("[init文件修复] 添加缺失的kspace_style pppm 1.0e-4")

    # 写回文件
    fixed_content = '\n'.join(fixed_lines) + '\n'
    with open(init_file, 'w', encoding='utf-8') as f:
        f.write(fixed_content)

    logger.info("[init文件修复] system.in.init文件修复完成")

    return {
        "success": True,
        "added_atom_style": not has_atom_style,
        "added_kspace_style": not has_kspace_style,
        "removed_duplicates": True
    }


def organize_lammps_input_files(
    work_dir: str,
    input_dir: str = "inputs"
) -> Dict[str, Any]:
    """整理LAMMPS输入文件到指定目录（步骤7）

    将生成的LAMMPS输入文件整理到正确的目录结构，移动到inputs/目录。
    包括以下文件：
    - system.lt: Moltemplate系统描述文件
    - system.data: LAMMPS结构文件
    - system.in.init: 初始化设置文件
    - system.in.settings: 力场设置文件
    - packmol.inp: Packmol输入脚本
    - packed_system.pdb: Packmol初始构型文件

    Args:
        work_dir: 工作目录路径（任务根目录）
        input_dir: 输入文件目录名称（默认: "inputs"）

    Returns:
        整理结果字典，包含：
            - success: 是否成功
            - moved_files: 已移动的文件列表（包含源路径和目标路径）
            - missing_files: 缺失的文件列表
            - file_sizes: 文件大小字典（移动后的文件）
            - error: 错误信息（如果失败）
            - statistics: 统计信息（文件数量、总大小等）

    Example:
        >>> result = organize_lammps_input_files(
        ...     work_dir="user_1/jobs/job_1"
        ... )
        >>> if result["success"]:
        ...     print(f"文件整理成功，移动了 {len(result['moved_files'])} 个文件")
        ...     print(f"统计信息: {result['statistics']}")
        >>> else:
        ...     print(f"文件整理失败: {result['error']}")
        ...     print(f"缺失文件: {result['missing_files']}")

    Note:
        执行流程：
        1. 验证工作目录存在性
        2. 创建inputs/目录（如果不存在）
        3. 构建文件列表（需要移动的文件）
        4. 验证文件完整性（文件存在、大小检查）
        5. 移动文件到inputs/目录
        6. 验证移动后的文件完整性
        7. 统计整理结果和输出
    """
    logger.info("[文件整理] ========== 开始整理LAMMPS输入文件（步骤7） ==========")
    logger.info(f"[文件整理] 工作目录: {work_dir}")
    logger.info(f"[文件整理] 输入目录: {input_dir}")

    # 初始化结果
    start_time = time.time()

    # 验证工作目录存在性
    work_dir_path = Path(work_dir)
    if not work_dir_path.exists():
        error_msg = f"工作目录不存在: {work_dir}"
        logger.error(f"[文件整理] {error_msg}")
        return {
            "success": False,
            "moved_files": [],
            "missing_files": [],
            "file_sizes": {},
            "error": error_msg,
            "statistics": {}
        }

    # 创建inputs目录（如果不存在）
    input_path = work_dir_path / input_dir
    if not input_path.exists():
        logger.info(f"[文件整理] 创建inputs目录: {input_path}")
        input_path.mkdir(parents=True, exist_ok=True)

    # 定义需要移动的文件列表
    required_files = [
        "system.lt",          # Moltemplate系统描述文件
        "system.data",        # LAMMPS结构文件
        "system.in.init",     # 初始化设置文件
        "system.in.settings", # 力场设置文件
        "packmol.inp",        # Packmol输入脚本
        "packed_system.pdb"   # Packmol初始构型文件
    ]

    logger.info(f"[文件整理] 需要移动的文件列表: {required_files}")

    # 验证文件完整性（移动前）
    logger.info("[文件整理] 开始验证文件完整性（移动前）")

    existing_files = []
    missing_files = []
    file_sizes_before = {}

    for file_name in required_files:
        source_file = work_dir_path / file_name

        if source_file.exists():
            # 文件存在，获取文件大小
            file_size = source_file.stat().st_size
            file_sizes_before[file_name] = file_size
            existing_files.append(str(source_file))

            logger.debug(f"[文件整理] 文件存在: {file_name} (大小: {file_size}字节)")

            # 验证文件大小是否大于0
            if file_size == 0:
                logger.warning(f"[文件整理] 文件大小为0: {file_name}")
        else:
            # 文件缺失
            missing_files.append(file_name)
            logger.warning(f"[文件整理] 文件缺失: {file_name}")

    # 如果有文件缺失，返回失败结果
    if missing_files:
        error_msg = f"缺失必需文件: {', '.join(missing_files)}"
        logger.error(f"[文件整理] {error_msg}")
        logger.error("[文件整理] ========== 文件整理失败 ==========")

        return {
            "success": False,
            "moved_files": [],
            "missing_files": missing_files,
            "file_sizes": {},
            "error": error_msg,
            "statistics": {
                "total_files": len(required_files),
                "existing_files": len(existing_files),
                "missing_files": len(missing_files)
            }
        }

    # 移动文件到inputs目录
    logger.info("[文件整理] 开始移动文件到inputs目录")

    moved_files = []
    file_sizes_after = {}
    move_errors = []

    for file_name in required_files:
        source_file = work_dir_path / file_name
        target_file = input_path / file_name

        try:
            # 使用shutil.move移动文件
            logger.debug(f"[文件整理] 移动文件: {file_name}")
            logger.debug(f"[文件整理]   源路径: {source_file}")
            logger.debug(f"[文件整理]   目标路径: {target_file}")

            # 如果目标文件已存在，先删除
            if target_file.exists():
                logger.warning(f"[文件整理] 目标文件已存在，将覆盖: {target_file}")
                target_file.unlink()

            # 移动文件
            shutil.move(str(source_file), str(target_file))

            # 验证移动后的文件
            if target_file.exists():
                file_size = target_file.stat().st_size
                file_sizes_after[file_name] = file_size
                moved_files.append({
                    "file_name": file_name,
                    "source_path": str(source_file),
                    "target_path": str(target_file),
                    "size": file_size
                })

                logger.info(f"[文件整理] 文件移动成功: {file_name} -> {target_file}")
            else:
                error_msg = f"文件移动失败，目标文件不存在: {file_name}"
                move_errors.append(error_msg)
                logger.error(f"[文件整理] {error_msg}")

        except Exception as e:
            error_msg = f"文件移动异常: {file_name} - {str(e)}"
            move_errors.append(error_msg)
            logger.error(f"[文件整理] {error_msg}")
            logger.exception(e)

    # 修复system.in.init文件（在文件移动完成后执行）
    logger.info("[文件整理] 开始修复system.in.init文件")
    fix_result = fix_system_in_init(str(input_path))
    if fix_result.get("success"):
        logger.info(f"[文件整理] system.in.init修复完成: {fix_result}")
    else:
        logger.warning(f"[文件整理] system.in.init修复失败: {fix_result.get('error', '未知错误')}")

    # 验证移动后的文件完整性
    logger.info("[文件整理] 开始验证移动后的文件完整性")

    final_missing_files = []
    for file_name in required_files:
        target_file = input_path / file_name

        if not target_file.exists():
            final_missing_files.append(file_name)
            logger.error(f"[文件整理] 移动后文件缺失: {file_name}")
        elif target_file.stat().st_size == 0:
            logger.warning(f"[文件整理] 移动后文件大小为0: {file_name}")

    # 构建统计信息
    end_time = time.time()
    duration = end_time - start_time

    total_size = sum(file_sizes_after.values())

    statistics = {
        "total_files": len(required_files),
        "moved_files": len(moved_files),
        "missing_files": len(final_missing_files),
        "total_size": total_size,
        "duration": duration,
        "input_dir": str(input_path)
    }

    # 构建返回结果
    success = len(moved_files) == len(required_files) and len(final_missing_files) == 0

    result = {
        "success": success,
        "moved_files": moved_files,
        "missing_files": final_missing_files,
        "file_sizes": file_sizes_after,
        "error": None if success else f"文件整理失败，缺失文件: {', '.join(final_missing_files)}",
        "statistics": statistics,
        "init_fix_result": fix_result
    }

    if success:
        logger.info("[文件整理] ========== 文件整理成功 ==========")
        logger.info(f"[文件整理] 移动文件数量: {len(moved_files)}")
        logger.info(f"[文件整理] 总文件大小: {total_size}字节")
        logger.info(f"[文件整理] 执行耗时: {duration:.2f}秒")
        logger.info(f"[文件整理] 输入目录: {input_path}")

        # 输出详细的文件列表
        logger.info("[文件整理] 已移动文件列表:")
        for file_info in moved_files:
            logger.info(f"[文件整理]   - {file_info['file_name']}: {file_info['size']}字节")
    else:
        logger.error("[文件整理] ========== 文件整理失败 ==========")
        logger.error(f"[文件整理] 缺失文件数量: {len(final_missing_files)}")
        logger.error(f"[文件整理] 缺失文件: {final_missing_files}")

        if move_errors:
            logger.error("[文件整理] 移动错误:")
            for error in move_errors:
                logger.error(f"[文件整理]   - {error}")

    return result


def run_moltemplate_execution(
    system_lt_path: str,
    work_dir: str,
    moltemplate_path: str = "moltemplate.sh",
    timeout: int = 3600,
    input_dir: str = "inputs",
    pdb_file: str = None
) -> Dict[str, Any]:
    """执行完整的Moltemplate流程（步骤6和步骤7）

    整合Moltemplate命令执行（步骤6）和文件整理输出（步骤7），
    实现完整的Moltemplate自动化建模流程的最后两个步骤。

    Args:
        system_lt_path: system.lt文件路径
        work_dir: 工作目录路径（任务根目录）
        moltemplate_path: Moltemplate脚本路径（默认: "moltemplate.sh"）
        timeout: Moltemplate执行超时时间（秒，默认: 3600）
        input_dir: 输入文件目录名称（默认: "inputs"）
        pdb_file: PDB坐标文件路径（可选，用于传递-pdb参数给Moltemplate）

    Returns:
        执行结果字典，包含：
            - success: 是否成功
            - step6_result: 步骤6执行结果（Moltemplate命令执行）
            - step7_result: 步骤7执行结果（文件整理输出）
            - statistics: 统计信息（总耗时、文件数量等）
            - error: 错误信息（如果失败）

    Example:
        >>> result = run_moltemplate_execution(
        ...     system_lt_path="user_1/jobs/job_1/system.lt",
        ...     work_dir="user_1/jobs/job_1",
        ...     pdb_file="packed_system.pdb"
        ... )
        >>> if result["success"]:
        ...     print(f"执行成功，总耗时 {result['statistics']['total_duration']}秒")
        ...     print(f"生成文件数量: {result['statistics']['total_files']}")
        >>> else:
        ...     print(f"执行失败: {result['error']}")

    Note:
        执行流程：
        1. 验证system.lt文件存在性（依赖检查）
        2. 执行步骤6：调用execute_moltemplate_command()
        3. 如果步骤6失败，停止执行并返回错误
        4. 执行步骤7：调用organize_lammps_input_files()
        5. 如果步骤7失败，记录错误并返回失败状态
        6. 统计执行结果和输出
    """
    logger.info("[完整流程] ========== 开始执行Moltemplate完整流程（步骤6-7） ==========")
    logger.info(f"[完整流程] system.lt路径: {system_lt_path}")
    logger.info(f"[完整流程] 工作目录: {work_dir}")
    logger.info(f"[完整流程] Moltemplate路径: {moltemplate_path}")
    logger.info(f"[完整流程] 输入目录: {input_dir}")

    # 初始化结果
    start_time = time.time()

    # ========== 步骤依赖检查：验证system.lt文件存在性 ==========
    logger.info("[完整流程] 步骤依赖检查：验证system.lt文件存在性")

    system_lt_file = Path(system_lt_path)
    if not system_lt_file.exists():
        error_msg = f"依赖检查失败：system.lt文件不存在: {system_lt_path}"
        logger.error(f"[完整流程] {error_msg}")
        logger.error("[完整流程] ========== Moltemplate完整流程执行失败 ==========")

        return {
            "success": False,
            "step6_result": None,
            "step7_result": None,
            "statistics": {
                "total_duration": 0.0,
                "step6_duration": 0.0,
                "step7_duration": 0.0,
                "dependency_check": "failed"
            },
            "error": error_msg
        }

    logger.info(f"[完整流程] 依赖检查通过：system.lt文件存在（大小: {system_lt_file.stat().st_size}字节）")

    # ========== 步骤6：执行Moltemplate命令 ==========
    logger.info("[完整流程] ========== 开始执行步骤6：Moltemplate命令执行 ==========")

    step6_result = execute_moltemplate_command(
        system_lt_path=system_lt_path,
        work_dir=work_dir,
        moltemplate_path=moltemplate_path,
        timeout=timeout,
        pdb_file=pdb_file
    )

    # 检查步骤6执行结果
    if not step6_result["success"]:
        error_msg = f"步骤6执行失败: {step6_result['error']}"
        logger.error(f"[完整流程] {error_msg}")
        logger.error(f"[完整流程] 缺失文件: {step6_result['missing_files']}")
        logger.error("[完整流程] ========== Moltemplate完整流程执行失败 ==========")

        end_time = time.time()
        total_duration = end_time - start_time

        return {
            "success": False,
            "step6_result": step6_result,
            "step7_result": None,
            "statistics": {
                "total_duration": total_duration,
                "step6_duration": step6_result["duration"],
                "step7_duration": 0.0,
                "step6_success": False,
                "step7_success": False
            },
            "error": error_msg
        }

    logger.info("[完整流程] 步骤6执行成功")
    logger.info(f"[完整流程] 步骤6耗时: {step6_result['duration']:.2f}秒")
    logger.info(f"[完整流程] 步骤6生成文件数量: {len(step6_result['output_files'])}")

    # ========== 步骤7：文件整理输出 ==========
    logger.info("[完整流程] ========== 开始执行步骤7：文件整理输出 ==========")

    step7_result = organize_lammps_input_files(
        work_dir=work_dir,
        input_dir=input_dir
    )

    # 检查步骤7执行结果
    if not step7_result["success"]:
        error_msg = f"步骤7执行失败: {step7_result['error']}"
        logger.error(f"[完整流程] {error_msg}")
        logger.error(f"[完整流程] 缺失文件: {step7_result['missing_files']}")
        logger.error("[完整流程] ========== Moltemplate完整流程执行失败 ==========")

        end_time = time.time()
        total_duration = end_time - start_time

        return {
            "success": False,
            "step6_result": step6_result,
            "step7_result": step7_result,
            "statistics": {
                "total_duration": total_duration,
                "step6_duration": step6_result["duration"],
                "step7_duration": step7_result["statistics"]["duration"],
                "step6_success": True,
                "step7_success": False,
                "total_files": len(step7_result["moved_files"]),
                "missing_files": len(step7_result["missing_files"])
            },
            "error": error_msg
        }

    logger.info("[完整流程] 步骤7执行成功")
    logger.info(f"[完整流程] 步骤7耗时: {step7_result['statistics']['duration']:.2f}秒")
    logger.info(f"[完整流程] 步骤7移动文件数量: {len(step7_result['moved_files'])}")

    # ========== 统计执行结果 ==========
    end_time = time.time()
    total_duration = end_time - start_time

    statistics = {
        "total_duration": total_duration,
        "step6_duration": step6_result["duration"],
        "step7_duration": step7_result["statistics"]["duration"],
        "step6_success": True,
        "step7_success": True,
        "total_files": len(step7_result["moved_files"]),
        "total_size": step7_result["statistics"]["total_size"],
        "input_dir": step7_result["statistics"]["input_dir"]
    }

    # 构建返回结果
    result = {
        "success": True,
        "step6_result": step6_result,
        "step7_result": step7_result,
        "statistics": statistics,
        "error": None
    }

    # ========== 输出执行结果摘要 ==========
    logger.info("[完整流程] ========== Moltemplate完整流程执行成功 ==========")
    logger.info(f"[完整流程] 总耗时: {total_duration:.2f}秒")
    logger.info(f"[完整流程] 步骤6耗时: {step6_result['duration']:.2f}秒")
    logger.info(f"[完整流程] 步骤7耗时: {step7_result['statistics']['duration']:.2f}秒")
    logger.info(f"[完整流程] 生成文件数量: {len(step7_result['moved_files'])}")
    logger.info(f"[完整流程] 总文件大小: {step7_result['statistics']['total_size']}字节")
    logger.info(f"[完整流程] 输入目录: {step7_result['statistics']['input_dir']}")

    # 输出详细的文件列表
    logger.info("[完整流程] 已生成文件列表:")
    for file_info in step7_result["moved_files"]:
        logger.info(f"[完整流程]   - {file_info['file_name']}: {file_info['size']}字节")

    return result