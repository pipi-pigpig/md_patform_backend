"""
Moltemplate工具封装模块

提供Moltemplate力场生成功能的封装
"""

import logging
import subprocess
import os
from pathlib import Path
from typing import Dict, Any, List, Optional

logger = logging.getLogger(__name__)


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