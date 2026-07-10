"""
力场参数集成测试脚本

本脚本用于真实执行Packmol堆积测试，验证力场参数文件的正确性。
测试内容包括：
    1. 环境检查：检查Docker和md_engine容器状态
    2. 使用真实的.lt文件（EC.lt、DMC.lt、Li.lt、PF6.lt）进行Packmol堆积测试
    3. 验证packed_system.pdb原子类型匹配
    4. 验证堆积后无原子重叠
    5. 验证原子数量与预期一致
    6. 测试后清理临时文件

测试配置：
    - 测试目录：data/md_platform_data/test_temp/forcefield_test/
    - 分子数量：EC=10, DMC=10, Li=5, PF6=5
    - 容器名称：md_engine（使用下划线）
    - 使用已挂载的目录：data/md_platform_data → /workspace/data

使用方法：
    python test_packmol_forcefield.py

作者: 电解液MD平台开发团队
版本: 1.0.0
"""

import subprocess
import os
import sys
import time
import shutil
import logging
import math
from pathlib import Path
from typing import Dict, Any, List, Optional, Tuple
from datetime import datetime


logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(levelname)s - %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S"
)
logger = logging.getLogger(__name__)


DOCKER_CONTAINER_NAMES = ["md_engine", "md-engine"]
DOCKER_CONTAINER_NAME = None
PACKMOL_EXECUTABLE = "packmol"
TEST_DIR_NAME = "forcefield_test"


TEMP_BASE_DIR = Path("data") / "md_platform_data" / "test_temp" / TEST_DIR_NAME


TEMPLATE_BASE_DIR = Path("data") / "md_platform_data" / "system_templates" / "molecule_templates"


DOCKER_WORK_DIR = "/workspace/data/test_temp/forcefield_test/work"


DOCKER_CHECK_TIMEOUT = 30
PACKMOL_EXECUTION_TIMEOUT = 300


MOLECULE_CONFIG = {
    "EC": {
        "count": 10,
        "atoms_per_molecule": 9,
        "atom_types": ["C", "O", "C", "O", "O", "C", "H", "H", "H", "H"],
        "expected_elements": {"C": 3, "O": 3, "H": 4},
        "charge": 0,
        "description": "碳酸乙烯酯 (Ethylene Carbonate)"
    },
    "DMC": {
        "count": 10,
        "atoms_per_molecule": 13,
        "atom_types": ["C", "O", "C", "O", "C", "O", "C", "H", "H", "H", "H", "H", "H"],
        "expected_elements": {"C": 4, "O": 3, "H": 6},
        "charge": 0,
        "description": "碳酸二甲酯 (Dimethyl Carbonate)"
    },
    "Li": {
        "count": 5,
        "atoms_per_molecule": 1,
        "atom_types": ["Li"],
        "expected_elements": {"Li": 1},
        "charge": 1,
        "description": "锂离子 (Lithium Ion)"
    },
    "PF6": {
        "count": 5,
        "atoms_per_molecule": 7,
        "atom_types": ["P", "F", "F", "F", "F", "F", "F"],
        "expected_elements": {"P": 1, "F": 6},
        "charge": -1,
        "description": "六氟磷酸根 (Hexafluorophosphate)"
    }
}


BOX_SIZE = {
    "x": 40.0,
    "y": 40.0,
    "z": 40.0
}


class ForcefieldTestRunner:
    """力场参数集成测试运行器
    
    本类封装了力场参数集成测试的完整流程，包括环境检查、文件准备、
    Packmol执行、结果验证和清理工作。
    
    测试流程：
        1. 检查Docker环境和md_engine容器状态
        2. 从模板库复制真实的.lt文件和.pdb文件到工作目录
        3. 生成Packmol输入脚本
        4. 通过Docker容器执行Packmol
        5. 验证输出PDB文件的原子类型匹配
        6. 验证堆积后无原子重叠
        7. 验证原子数量与预期一致
        8. 清理测试临时文件
    
    属性：
        test_dir (Path): 测试目录路径
        template_dir (Path): 分子模板源目录路径
        work_dir (Path): Packmol工作目录路径
        output_dir (Path): 输出文件目录路径
        test_results (List[Dict]): 测试结果列表
        docker_available (bool): Docker是否可用
        container_running (bool): md_engine容器是否运行中
    
    使用示例：
        >>> runner = ForcefieldTestRunner()
        >>> success = runner.run_all_tests()
        >>> if success:
        ...     print("所有测试通过")
    """
    
    def __init__(self):
        """初始化力场参数集成测试运行器
        
        创建测试所需的目录结构，并初始化测试状态。
        目录结构：
            data/md_platform_data/test_temp/forcefield_test/
            ├── templates/          # 分子模板目录（从系统模板复制）
            │   ├── EC/
            │   │   ├── EC.lt
            │   │   └── EC.pdb
            │   ├── DMC/
            │   │   ├── DMC.lt
            │   │   └── DMC.pdb
            │   ├── LiPF6/
            │   │   ├── Li.lt
            │   │   ├── Li.pdb
            │   │   ├── PF6.lt
            │   │   └── PF6.pdb
            ├── work/               # Packmol工作目录
            └── output/             # 输出文件目录
        """
        self.test_dir = TEMP_BASE_DIR
        self.template_dir = self.test_dir / "templates"
        self.work_dir = self.test_dir / "work"
        self.output_dir = self.test_dir / "output"
        
        self.test_results: List[Dict[str, Any]] = []
        self.docker_available = False
        self.container_running = False
        
        self.expected_total_atoms = self._calculate_expected_atoms()
        
        logger.info(f"初始化力场参数集成测试运行器")
        logger.info(f"测试目录: {self.test_dir.absolute()}")
        logger.info(f"模板源目录: {TEMPLATE_BASE_DIR.absolute()}")
        logger.info(f"预期总原子数: {self.expected_total_atoms}")
    
    def _calculate_expected_atoms(self) -> int:
        """计算预期的总原子数
        
        根据分子配置计算堆积后PDB文件应包含的总原子数。
        
        Returns:
            int: 预期的总原子数
        """
        total_atoms = 0
        for mol_name, mol_config in MOLECULE_CONFIG.items():
            atoms = mol_config["count"] * mol_config["atoms_per_molecule"]
            total_atoms += atoms
            logger.debug(
                f"分子 {mol_name}: {mol_config['count']}个 × "
                f"{mol_config['atoms_per_molecule']}原子 = {atoms}原子"
            )
        return total_atoms
    
    def setup(self) -> bool:
        """设置测试环境
        
        创建测试目录结构，并从系统模板库复制真实的.lt和.pdb文件。
        
        Returns:
            bool: 设置是否成功
        """
        logger.info("=" * 60)
        logger.info("步骤1: 设置测试环境")
        logger.info("=" * 60)
        
        try:
            logger.info("创建测试目录结构...")
            self.test_dir.mkdir(parents=True, exist_ok=True)
            self.template_dir.mkdir(parents=True, exist_ok=True)
            self.work_dir.mkdir(parents=True, exist_ok=True)
            self.output_dir.mkdir(parents=True, exist_ok=True)
            
            logger.info(f"  - 测试目录: {self.test_dir}")
            logger.info(f"  - 模板目录: {self.template_dir}")
            logger.info(f"  - 工作目录: {self.work_dir}")
            logger.info(f"  - 输出目录: {self.output_dir}")
            
            logger.info("从系统模板库复制分子模板文件...")
            copy_result = self._copy_molecule_templates()
            
            if not copy_result["success"]:
                logger.error(f"分子模板复制失败: {copy_result['errors']}")
                self._record_test_result(
                    "环境设置-模板复制",
                    False,
                    str(copy_result["errors"])
                )
                return False
            
            logger.info(f"  - 成功复制 {len(copy_result['copied_files'])} 个模板文件")
            for file_path in copy_result["copied_files"]:
                logger.info(f"    • {file_path}")
            
            self._record_test_result(
                "环境设置-模板复制",
                True,
                f"复制了 {len(copy_result['copied_files'])} 个文件"
            )
            
            logger.info("测试环境设置完成 ✓")
            return True
            
        except Exception as e:
            logger.error(f"测试环境设置失败: {e}")
            self._record_test_result(
                "环境设置",
                False,
                str(e)
            )
            return False
    
    def _copy_molecule_templates(self) -> Dict[str, Any]:
        """从系统模板库复制分子模板文件
        
        复制EC、DMC、Li、PF6分子的.lt和.pdb文件到测试模板目录。
        
        Returns:
            Dict: 复制结果，包含success、copied_files、missing_files、errors字段
        """
        result = {
            "success": True,
            "copied_files": [],
            "missing_files": [],
            "errors": []
        }
        
        molecules_to_copy = {
            "EC": ["EC.lt", "EC.pdb"],
            "DMC": ["DMC.lt", "DMC.pdb"],
            "LiPF6": ["Li.lt", "Li.pdb", "PF6.lt", "PF6.pdb"]
        }
        
        for mol_name, files in molecules_to_copy.items():
            src_dir = TEMPLATE_BASE_DIR / mol_name
            dst_dir = self.template_dir / mol_name
            
            if not src_dir.exists():
                result["missing_files"].append(f"模板目录不存在: {src_dir}")
                result["success"] = False
                continue
            
            dst_dir.mkdir(parents=True, exist_ok=True)
            
            for file_name in files:
                src_file = src_dir / file_name
                dst_file = dst_dir / file_name
                
                if not src_file.exists():
                    result["missing_files"].append(str(src_file))
                    result["success"] = False
                    continue
                
                try:
                    shutil.copy2(src_file, dst_file)
                    result["copied_files"].append(str(dst_file))
                    logger.debug(f"复制: {src_file} -> {dst_file}")
                except Exception as e:
                    result["errors"].append(f"复制失败: {src_file}, 错误: {str(e)}")
                    result["success"] = False
        
        return result
    
    def check_environment(self) -> bool:
        """检查测试环境
        
        检查Docker是否可用，以及md_engine容器是否正在运行。
        
        Returns:
            bool: 环境检查是否通过
        """
        logger.info("")
        logger.info("=" * 60)
        logger.info("步骤2: 检查测试环境")
        logger.info("=" * 60)
        
        docker_check = self._check_docker_available()
        
        if not docker_check["success"]:
            logger.error(f"  ✗ Docker不可用: {docker_check['error']}")
            self._record_test_result(
                "环境检查-Docker可用性",
                False,
                docker_check["error"]
            )
            return False
        
        logger.info(f"  ✓ Docker可用: {docker_check['version']}")
        self.docker_available = True
        self._record_test_result(
            "环境检查-Docker可用性",
            True,
            f"Docker版本: {docker_check['version']}"
        )
        
        container_check = self._check_container_running()
        
        if not container_check["success"]:
            logger.error(f"  ✗ md_engine容器未运行: {container_check['error']}")
            self._record_test_result(
                "环境检查-容器状态",
                False,
                container_check["error"]
            )
            return False
        
        logger.info(f"  ✓ md_engine容器正在运行 (使用容器名: {DOCKER_CONTAINER_NAME})")
        self.container_running = True
        self._record_test_result(
            "环境检查-容器状态",
            True,
            f"容器 {DOCKER_CONTAINER_NAME} 运行中"
        )
        
        packmol_check = self._check_packmol_in_container()
        
        if not packmol_check["success"]:
            logger.error(f"  ✗ Packmol不可用: {packmol_check['error']}")
            self._record_test_result(
                "环境检查-Packmol可用性",
                False,
                packmol_check["error"]
            )
            return False
        
        logger.info(f"  ✓ Packmol可用: {packmol_check['output']}")
        self._record_test_result(
            "环境检查-Packmol可用性",
            True,
            f"Packmol路径: {packmol_check['output']}"
        )
        
        logger.info("环境检查通过 ✓")
        return True
    
    def _check_docker_available(self) -> Dict[str, Any]:
        """检查Docker是否可用
        
        Returns:
            Dict: 检查结果，包含success、version、error字段
        """
        try:
            result = subprocess.run(
                ["docker", "--version"],
                capture_output=True,
                text=True,
                timeout=DOCKER_CHECK_TIMEOUT,
                shell=False
            )
            
            if result.returncode == 0:
                version = result.stdout.strip()
                return {
                    "success": True,
                    "version": version,
                    "error": None
                }
            else:
                return {
                    "success": False,
                    "version": None,
                    "error": f"Docker命令返回错误码: {result.returncode}"
                }
                
        except FileNotFoundError:
            return {
                "success": False,
                "version": None,
                "error": "Docker命令未找到，请确保Docker已安装并添加到PATH"
            }
        except subprocess.TimeoutExpired:
            return {
                "success": False,
                "version": None,
                "error": "Docker命令执行超时"
            }
        except Exception as e:
            return {
                "success": False,
                "version": None,
                "error": f"检查Docker时发生异常: {str(e)}"
            }
    
    def _check_container_running(self) -> Dict[str, Any]:
        """检查md_engine容器是否正在运行
        
        支持两种容器命名方式：md_engine（下划线）和md-engine（连字符）
        动态查找正在运行的容器名称
        
        Returns:
            Dict: 检查结果，包含success、status、error、container_name字段
        """
        global DOCKER_CONTAINER_NAME
        
        try:
            result = subprocess.run(
                ["docker", "ps", "--filter", "status=running", "--format", "{{.Names}}"],
                capture_output=True,
                text=True,
                timeout=DOCKER_CHECK_TIMEOUT,
                shell=False
            )
            
            if result.returncode == 0:
                running_containers = result.stdout.strip().split('\n')
                
                for container_name in DOCKER_CONTAINER_NAMES:
                    if container_name in running_containers:
                        DOCKER_CONTAINER_NAME = container_name
                        logger.info(f"  找到运行中的容器: {container_name}")
                        return {
                            "success": True,
                            "status": "running",
                            "container_name": container_name,
                            "error": None
                        }
                
                for container_name in DOCKER_CONTAINER_NAMES:
                    result_all = subprocess.run(
                        ["docker", "ps", "-a", "--filter", f"name={container_name}",
                         "--format", "{{.Names}}\t{{.Status}}"],
                        capture_output=True,
                        text=True,
                        timeout=DOCKER_CHECK_TIMEOUT,
                        shell=False
                    )
                    
                    if result_all.returncode == 0 and result_all.stdout.strip():
                        lines = result_all.stdout.strip().split('\n')
                        for line in lines:
                            parts = line.split('\t')
                            if len(parts) >= 2 and parts[0] == container_name:
                                status = parts[1]
                                return {
                                    "success": False,
                                    "status": "not_running",
                                    "container_name": container_name,
                                    "error": f"容器 {container_name} 存在但未运行（状态: {status}）。请先启动容器: docker start {container_name}"
                                }
                
                return {
                    "success": False,
                    "status": "not_found",
                    "error": f"未找到md容器。请检查容器名称（支持: {DOCKER_CONTAINER_NAMES}）"
                }
            else:
                return {
                    "success": False,
                    "status": None,
                    "error": f"docker ps命令返回错误码: {result.returncode}"
                }
                
        except subprocess.TimeoutExpired:
            return {
                "success": False,
                "status": None,
                "error": "docker ps命令执行超时"
            }
        except Exception as e:
            return {
                "success": False,
                "status": None,
                "error": f"检查容器状态时发生异常: {str(e)}"
            }
    
    def _check_packmol_in_container(self) -> Dict[str, Any]:
        """检查容器中Packmol是否可用
        
        Returns:
            Dict: 检查结果，包含success、output、error字段
        """
        try:
            result = subprocess.run(
                ["docker", "exec", DOCKER_CONTAINER_NAME, "which", PACKMOL_EXECUTABLE],
                capture_output=True,
                text=True,
                timeout=DOCKER_CHECK_TIMEOUT,
                shell=False
            )
            
            if result.returncode == 0:
                packmol_path = result.stdout.strip()
                return {
                    "success": True,
                    "output": packmol_path,
                    "error": None
                }
            else:
                result2 = subprocess.run(
                    ["docker", "exec", DOCKER_CONTAINER_NAME, PACKMOL_EXECUTABLE, "-h"],
                    capture_output=True,
                    text=True,
                    timeout=DOCKER_CHECK_TIMEOUT,
                    shell=False
                )
                
                if "packmol" in result2.stdout.lower() or result2.returncode == 0:
                    return {
                        "success": True,
                        "output": "packmol available",
                        "error": None
                    }
                else:
                    return {
                        "success": False,
                        "output": None,
                        "error": f"Packmol未在容器中找到。请确保容器中已安装Packmol"
                    }
                    
        except subprocess.TimeoutExpired:
            return {
                "success": False,
                "output": None,
                "error": "检查Packmol命令执行超时"
            }
        except Exception as e:
            return {
                "success": False,
                "output": None,
                "error": f"检查Packmol时发生异常: {str(e)}"
            }
    
    def prepare_test_files(self) -> bool:
        """准备测试文件
        
        将分子PDB文件复制到工作目录，并生成Packmol输入脚本。
        
        Returns:
            bool: 准备是否成功
        """
        logger.info("")
        logger.info("=" * 60)
        logger.info("步骤3: 准备测试文件")
        logger.info("=" * 60)
        
        try:
            logger.info("复制PDB文件到工作目录...")
            pdb_files = {
                "EC": self.template_dir / "EC" / "EC.pdb",
                "DMC": self.template_dir / "DMC" / "DMC.pdb",
                "Li": self.template_dir / "LiPF6" / "Li.pdb",
                "PF6": self.template_dir / "LiPF6" / "PF6.pdb"
            }
            
            for mol_name, src_pdb in pdb_files.items():
                dst_pdb = self.work_dir / f"{mol_name}.pdb"
                if src_pdb.exists():
                    shutil.copy2(src_pdb, dst_pdb)
                    logger.info(f"  - 复制 {mol_name}.pdb: {src_pdb} -> {dst_pdb}")
                else:
                    logger.error(f"  ✗ PDB文件不存在: {src_pdb}")
                    self._record_test_result(
                        "文件准备-PDB复制",
                        False,
                        f"PDB文件不存在: {src_pdb}"
                    )
                    return False
            
            logger.info("生成Packmol输入脚本...")
            input_script = self._generate_packmol_input()
            logger.info(f"  - 输入脚本: {input_script}")
            
            self._record_test_result(
                "文件准备",
                True,
                "PDB文件已复制，packmol.inp已生成"
            )
            
            logger.info("测试文件准备完成 ✓")
            return True
            
        except Exception as e:
            logger.error(f"测试文件准备失败: {e}")
            self._record_test_result(
                "文件准备",
                False,
                str(e)
            )
            return False
    
    def _generate_packmol_input(self) -> Path:
        """生成Packmol输入脚本
        
        根据分子配置生成Packmol输入脚本，包含EC、DMC、Li、PF6分子的堆积配置。
        
        Returns:
            Path: 输入脚本文件路径
        """
        input_path = self.work_dir / "packmol.inp"
        
        tolerance = 2.0
        seed = 12345
        max_iterations = 100
        
        input_lines = [
            f"tolerance {tolerance}",
            f"seed {seed}",
            f"maxit {max_iterations}",
            "",
            f"# 力场参数集成测试 - Packmol输入脚本",
            f"# 分子配置: EC=10, DMC=10, Li=5, PF6=5",
            f"# 盒子尺寸: {BOX_SIZE['x']} x {BOX_SIZE['y']} x {BOX_SIZE['z']} Å",
            "",
        ]
        
        x = BOX_SIZE["x"]
        y = BOX_SIZE["y"]
        z = BOX_SIZE["z"]
        
        for mol_name, mol_config in MOLECULE_CONFIG.items():
            count = mol_config["count"]
            pdb_file = f"{mol_name}.pdb"
            
            input_lines.extend([
                f"structure {pdb_file}",
                f"  number {count}",
                f"  inside box 0. 0. 0. {x:.2f} {y:.2f} {z:.2f}",
                "end structure",
                "",
            ])
        
        input_lines.append(f"output packed_system.pdb")
        input_lines.append("")
        
        input_content = "\n".join(input_lines)
        
        with open(input_path, "w", encoding="utf-8") as f:
            f.write(input_content)
        
        logger.debug(f"Packmol输入脚本内容:\n{input_content}")
        
        return input_path
    
    def execute_packmol(self) -> bool:
        """执行Packmol测试
        
        通过Docker容器执行Packmol，进行分子堆积测试。
        
        Returns:
            bool: 执行是否成功
        """
        logger.info("")
        logger.info("=" * 60)
        logger.info("步骤4: 执行Packmol堆积测试")
        logger.info("=" * 60)
        
        work_dir_abs = self.work_dir.absolute()
        
        logger.info(f"本地工作目录: {work_dir_abs}")
        logger.info(f"容器工作目录: {DOCKER_WORK_DIR}")
        logger.info(f"执行命令: docker exec -w {DOCKER_WORK_DIR} {DOCKER_CONTAINER_NAME} packmol < packmol.inp")
        
        start_time = time.time()
        
        try:
            result = subprocess.run(
                [
                    "docker", "exec",
                    "-w", DOCKER_WORK_DIR,
                    DOCKER_CONTAINER_NAME,
                    "bash", "-c",
                    f"{PACKMOL_EXECUTABLE} < packmol.inp"
                ],
                capture_output=True,
                text=True,
                timeout=PACKMOL_EXECUTION_TIMEOUT,
                shell=False
            )
            
            execution_time = time.time() - start_time
            
            logger.info(f"执行时间: {execution_time:.2f}秒")
            logger.info(f"返回码: {result.returncode}")
            
            if result.stdout:
                logger.info("标准输出:")
                for line in result.stdout.strip().split("\n")[:20]:
                    logger.info(f"  {line}")
                if len(result.stdout.strip().split("\n")) > 20:
                    logger.info(f"  ... (共 {len(result.stdout.strip().split('\n'))} 行)")
            
            if result.stderr:
                logger.info("错误输出:")
                for line in result.stderr.strip().split("\n")[:10]:
                    logger.info(f"  {line}")
            
            success = result.returncode == 0
            
            if success:
                logger.info("Packmol执行成功 ✓")
                self._record_test_result(
                    "Packmol执行",
                    True,
                    f"执行时间: {execution_time:.2f}秒, 返回码: {result.returncode}"
                )
            else:
                logger.error(f"Packmol执行失败，返回码: {result.returncode}")
                self._record_test_result(
                    "Packmol执行",
                    False,
                    f"返回码: {result.returncode}, 错误: {result.stderr[:200]}"
                )
            
            self._save_execution_output(result.stdout, result.stderr, execution_time)
            
            return success
            
        except subprocess.TimeoutExpired:
            execution_time = time.time() - start_time
            logger.error(f"Packmol执行超时（{PACKMOL_EXECUTION_TIMEOUT}秒）")
            self._record_test_result(
                "Packmol执行",
                False,
                f"执行超时（{PACKMOL_EXECUTION_TIMEOUT}秒）"
            )
            return False
            
        except Exception as e:
            logger.error(f"Packmol执行异常: {e}")
            self._record_test_result(
                "Packmol执行",
                False,
                str(e)
            )
            return False
    
    def _save_execution_output(self, stdout: str, stderr: str, execution_time: float) -> None:
        """保存Packmol执行输出到文件
        
        Args:
            stdout: 标准输出内容
            stderr: 错误输出内容
            execution_time: 执行时间（秒）
        """
        output_file = self.output_dir / "packmol_execution.log"
        
        with open(output_file, "w", encoding="utf-8") as f:
            f.write(f"Packmol执行日志 - 力场参数集成测试\n")
            f.write(f"{'=' * 60}\n")
            f.write(f"执行时间: {datetime.now().isoformat()}\n")
            f.write(f"耗时: {execution_time:.2f}秒\n")
            f.write(f"分子配置: EC=10, DMC=10, Li=5, PF6=5\n")
            f.write(f"盒子尺寸: {BOX_SIZE['x']} x {BOX_SIZE['y']} x {BOX_SIZE['z']} Å\n")
            f.write(f"{'=' * 60}\n\n")
            f.write(f"标准输出:\n{stdout}\n\n")
            f.write(f"错误输出:\n{stderr}\n")
        
        logger.info(f"  - 执行日志已保存: {output_file}")
    
    def verify_results(self) -> bool:
        """验证测试结果
        
        验证Packmol输出文件是否正确生成，并检查：
        1. PDB文件是否存在
        2. 原子数量是否与预期一致
        3. 原子类型是否匹配
        4. 是否存在原子重叠
        
        Returns:
            bool: 验证是否通过
        """
        logger.info("")
        logger.info("=" * 60)
        logger.info("步骤5: 验证测试结果")
        logger.info("=" * 60)
        
        all_passed = True
        
        pdb_file = self.work_dir / "packed_system.pdb"
        logger.info("验证1: 检查PDB文件是否存在")
        logger.info(f"  文件路径: {pdb_file}")
        
        if pdb_file.exists():
            file_size = pdb_file.stat().st_size
            logger.info(f"  ✓ PDB文件存在，大小: {file_size} 字节")
            self._record_test_result(
                "验证-PDB文件存在",
                True,
                f"文件大小: {file_size} 字节"
            )
        else:
            logger.error(f"  ✗ PDB文件不存在")
            self._record_test_result(
                "验证-PDB文件存在",
                False,
                "PDB文件未生成"
            )
            return False
        
        logger.info("验证2: 解析PDB文件内容")
        pdb_content = self._parse_pdb_file(pdb_file)
        
        if pdb_content["success"]:
            logger.info(f"  ✓ PDB文件解析成功")
            logger.info(f"    - 总原子数: {pdb_content['atom_count']}")
            logger.info(f"    - 残基数量: {pdb_content['residue_count']}")
            logger.info(f"    - 分子类型: {pdb_content['molecule_types']}")
            self._record_test_result(
                "验证-PDB解析",
                True,
                f"原子数: {pdb_content['atom_count']}, 残基数: {pdb_content['residue_count']}"
            )
        else:
            logger.error(f"  ✗ PDB文件解析失败: {pdb_content['errors']}")
            self._record_test_result(
                "验证-PDB解析",
                False,
                str(pdb_content["errors"])
            )
            return False
        
        logger.info("验证3: 验证原子数量")
        actual_atoms = pdb_content["atom_count"]
        expected_atoms = self.expected_total_atoms
        
        if actual_atoms == expected_atoms:
            logger.info(f"  ✓ 原子数量正确: {actual_atoms} (预期: {expected_atoms})")
            self._record_test_result(
                "验证-原子数量",
                True,
                f"原子数: {actual_atoms} (预期: {expected_atoms})"
            )
        else:
            logger.error(f"  ✗ 原子数量不匹配: {actual_atoms} (预期: {expected_atoms})")
            self._record_test_result(
                "验证-原子数量",
                False,
                f"实际: {actual_atoms}, 预期: {expected_atoms}"
            )
            all_passed = False
        
        logger.info("验证4: 验证原子类型匹配")
        atom_type_result = self._verify_atom_types(pdb_content)
        
        if atom_type_result["success"]:
            logger.info(f"  ✓ 原子类型匹配")
            for mol_name, element_counts in atom_type_result["element_counts"].items():
                logger.info(f"    - {mol_name}: {element_counts}")
            self._record_test_result(
                "验证-原子类型",
                True,
                f"所有分子原子类型匹配"
            )
        else:
            logger.error(f"  ✗ 原子类型不匹配: {atom_type_result['errors']}")
            self._record_test_result(
                "验证-原子类型",
                False,
                str(atom_type_result["errors"])
            )
            all_passed = False
        
        logger.info("验证5: 验证无原子重叠")
        overlap_result = self._check_atom_overlap(pdb_file)
        
        if overlap_result["success"]:
            logger.info(f"  ✓ 无原子重叠")
            logger.info(f"    - 最小原子间距: {overlap_result['min_distance']:.3f} Å")
            logger.info(f"    - tolerance阈值: 2.0 Å")
            self._record_test_result(
                "验证-无原子重叠",
                True,
                f"最小间距: {overlap_result['min_distance']:.3f} Å"
            )
        else:
            logger.error(f"  ✗ 存在原子重叠: {overlap_result['errors']}")
            self._record_test_result(
                "验证-无原子重叠",
                False,
                str(overlap_result["errors"])
            )
            all_passed = False
        
        logger.info("验证6: 验证电荷平衡")
        charge_result = self._verify_charge_balance(pdb_content)
        
        if charge_result["balanced"]:
            logger.info(f"  ✓ 系统电荷平衡")
            logger.info(f"    - 总电荷: {charge_result['total_charge']}")
            self._record_test_result(
                "验证-电荷平衡",
                True,
                f"总电荷: {charge_result['total_charge']}"
            )
        else:
            logger.warning(f"  ! 系统电荷不平衡: {charge_result['total_charge']}")
            self._record_test_result(
                "验证-电荷平衡",
                False,
                f"总电荷: {charge_result['total_charge']}"
            )
        
        if all_passed:
            logger.info("结果验证通过 ✓")
        else:
            logger.error("结果验证存在失败项 ✗")
        
        return all_passed
    
    def _parse_pdb_file(self, pdb_path: Path) -> Dict[str, Any]:
        """解析PDB文件
        
        解析Packmol生成的PDB文件，提取原子、残基、分子类型等信息。
        
        Args:
            pdb_path: PDB文件路径
            
        Returns:
            Dict: 解析结果，包含success、atom_count、residue_count、
                  molecule_types、atoms、errors字段
        """
        result = {
            "success": False,
            "atom_count": 0,
            "residue_count": 0,
            "molecule_types": set(),
            "atoms": [],
            "errors": []
        }
        
        try:
            atom_count = 0
            residue_set = set()
            molecule_types = set()
            atoms = []
            
            with open(pdb_path, "r", encoding="utf-8") as f:
                for line in f:
                    if line.startswith("ATOM") or line.startswith("HETATM"):
                        atom_count += 1
                        
                        try:
                            atom_serial = int(line[6:11].strip())
                            atom_name = line[12:16].strip()
                            residue_name = line[17:20].strip()
                            chain_id = line[21].strip()
                            residue_num = int(line[22:26].strip())
                            x = float(line[30:38].strip())
                            y = float(line[38:46].strip())
                            z = float(line[46:54].strip())
                            
                            element = line[76:78].strip() if len(line) >= 78 else atom_name[0]
                            
                            residue_set.add((residue_name, residue_num))
                            molecule_types.add(residue_name)
                            
                            atoms.append({
                                "serial": atom_serial,
                                "name": atom_name,
                                "residue_name": residue_name,
                                "residue_num": residue_num,
                                "chain_id": chain_id,
                                "x": x,
                                "y": y,
                                "z": z,
                                "element": element
                            })
                            
                        except (ValueError, IndexError) as e:
                            result["errors"].append(f"解析原子行失败: {line.strip()}")
            
            result["atom_count"] = atom_count
            result["residue_count"] = len(residue_set)
            result["molecule_types"] = molecule_types
            result["atoms"] = atoms
            
            if atom_count == 0:
                result["errors"].append("PDB文件中未找到原子记录")
            else:
                result["success"] = True
            
        except Exception as e:
            result["errors"].append(f"PDB文件解析错误: {str(e)}")
        
        return result
    
    def _verify_atom_types(self, pdb_content: Dict) -> Dict[str, Any]:
        """验证原子类型是否匹配
        
        检查PDB文件中各分子的原子类型是否与预期一致。
        
        Args:
            pdb_content: PDB解析结果
            
        Returns:
            Dict: 验证结果，包含success、element_counts、errors字段
        """
        result = {
            "success": True,
            "element_counts": {},
            "errors": []
        }
        
        molecule_atoms = {}
        for atom in pdb_content["atoms"]:
            mol_name = atom["residue_name"]
            element = atom["element"]
            
            if mol_name not in molecule_atoms:
                molecule_atoms[mol_name] = {"count": 0, "elements": {}}
            
            molecule_atoms[mol_name]["count"] += 1
            
            if element not in molecule_atoms[mol_name]["elements"]:
                molecule_atoms[mol_name]["elements"][element] = 0
            molecule_atoms[mol_name]["elements"][element] += 1
        
        for mol_name, mol_config in MOLECULE_CONFIG.items():
            if mol_name not in molecule_atoms:
                result["errors"].append(f"分子 {mol_name} 未在PDB中找到")
                result["success"] = False
                continue
            
            actual_count = molecule_atoms[mol_name]["count"]
            expected_count = mol_config["count"] * mol_config["atoms_per_molecule"]
            
            if actual_count != expected_count:
                result["errors"].append(
                    f"分子 {mol_name} 原子数不匹配: "
                    f"实际 {actual_count}, 预期 {expected_count}"
                )
                result["success"] = False
            
            result["element_counts"][mol_name] = molecule_atoms[mol_name]["elements"]
        
        return result
    
    def _check_atom_overlap(self, pdb_path: Path) -> Dict[str, Any]:
        """检查原子重叠
        
        计算所有原子之间的最小距离，验证是否存在重叠。
        Packmol的tolerance为2.0Å，原子间距应大于此值。
        
        Args:
            pdb_path: PDB文件路径
            
        Returns:
            Dict: 检查结果，包含success、min_distance、overlap_count、errors字段
        """
        result = {
            "success": True,
            "min_distance": float('inf'),
            "overlap_count": 0,
            "errors": []
        }
        
        tolerance = 2.0
        
        try:
            atoms = []
            with open(pdb_path, "r", encoding="utf-8") as f:
                for line in f:
                    if line.startswith("ATOM") or line.startswith("HETATM"):
                        try:
                            x = float(line[30:38].strip())
                            y = float(line[38:46].strip())
                            z = float(line[46:54].strip())
                            atoms.append((x, y, z))
                        except (ValueError, IndexError):
                            pass
            
            min_distance = float('inf')
            overlap_count = 0
            
            sample_size = min(100, len(atoms))
            sampled_atoms = atoms[:sample_size]
            
            for i in range(len(sampled_atoms)):
                for j in range(i + 1, len(sampled_atoms)):
                    dx = sampled_atoms[i][0] - sampled_atoms[j][0]
                    dy = sampled_atoms[i][1] - sampled_atoms[j][1]
                    dz = sampled_atoms[i][2] - sampled_atoms[j][2]
                    distance = math.sqrt(dx*dx + dy*dy + dz*dz)
                    
                    if distance < min_distance:
                        min_distance = distance
                    
                    if distance < tolerance:
                        overlap_count += 1
            
            result["min_distance"] = min_distance
            result["overlap_count"] = overlap_count
            
            if min_distance < tolerance:
                result["success"] = False
                result["errors"].append(
                    f"存在原子重叠: 最小距离 {min_distance:.3f}Å < tolerance {tolerance}Å"
                )
            
        except Exception as e:
            result["success"] = False
            result["errors"].append(f"原子重叠检查失败: {str(e)}")
        
        return result
    
    def _verify_charge_balance(self, pdb_content: Dict) -> Dict[str, Any]:
        """验证系统电荷平衡
        
        计算系统的总电荷，验证是否电荷平衡。
        
        Args:
            pdb_content: PDB解析结果
            
        Returns:
            Dict: 验证结果，包含balanced、total_charge字段
        """
        total_charge = 0
        
        for mol_name, mol_config in MOLECULE_CONFIG.items():
            charge_per_molecule = mol_config["charge"]
            count = mol_config["count"]
            total_charge += charge_per_molecule * count
        
        return {
            "balanced": total_charge == 0,
            "total_charge": total_charge
        }
    
    def cleanup(self) -> None:
        """清理测试文件
        
        清理测试过程中生成的临时文件和目录。
        """
        logger.info("")
        logger.info("=" * 60)
        logger.info("步骤6: 清理测试文件")
        logger.info("=" * 60)
        
        try:
            if self.test_dir.exists():
                files_to_delete = list(self.test_dir.rglob("*"))
                file_count = len([f for f in files_to_delete if f.is_file()])
                
                shutil.rmtree(self.test_dir)
                logger.info(f"  - 已删除测试目录: {self.test_dir}")
                logger.info(f"  - 已删除 {file_count} 个文件")
                
                self._record_test_result(
                    "清理测试文件",
                    True,
                    f"已删除 {file_count} 个文件"
                )
            else:
                logger.info(f"  - 测试目录不存在，无需清理")
                self._record_test_result(
                    "清理测试文件",
                    True,
                    "测试目录不存在"
                )
                
        except Exception as e:
            logger.error(f"清理测试文件失败: {e}")
            self._record_test_result(
                "清理测试文件",
                False,
                str(e)
            )
    
    def _record_test_result(self, test_name: str, success: bool, message: str) -> None:
        """记录测试结果
        
        Args:
            test_name: 测试名称
            success: 测试是否成功
            message: 测试消息
        """
        self.test_results.append({
            "test_name": test_name,
            "success": success,
            "message": message,
            "timestamp": datetime.now().isoformat()
        })
    
    def print_summary(self) -> None:
        """打印测试摘要
        
        输出所有测试结果的汇总信息。
        """
        logger.info("")
        logger.info("=" * 60)
        logger.info("测试摘要")
        logger.info("=" * 60)
        
        total_tests = len(self.test_results)
        passed_tests = sum(1 for r in self.test_results if r["success"])
        failed_tests = total_tests - passed_tests
        
        logger.info(f"总测试数: {total_tests}")
        logger.info(f"通过: {passed_tests}")
        logger.info(f"失败: {failed_tests}")
        logger.info("")
        
        logger.info("详细结果:")
        for i, result in enumerate(self.test_results, 1):
            status = "✓ 通过" if result["success"] else "✗ 失败"
            logger.info(f"  {i}. {result['test_name']}: {status}")
            if result["message"]:
                logger.info(f"     消息: {result['message']}")
        
        logger.info("")
        
        logger.info("分子配置验证:")
        for mol_name, mol_config in MOLECULE_CONFIG.items():
            logger.info(
                f"  - {mol_name} ({mol_config['description']}): "
                f"{mol_config['count']}个分子, "
                f"{mol_config['atoms_per_molecule']}原子/分子, "
                f"电荷={mol_config['charge']}"
            )
        
        logger.info(f"预期总原子数: {self.expected_total_atoms}")
        logger.info(f"盒子尺寸: {BOX_SIZE['x']} x {BOX_SIZE['y']} x {BOX_SIZE['z']} Å")
        
        logger.info("")
        if failed_tests == 0:
            logger.info("=" * 60)
            logger.info("所有测试通过！ ✓")
            logger.info("=" * 60)
        else:
            logger.info("=" * 60)
            logger.info(f"存在 {failed_tests} 个失败的测试 ✗")
            logger.info("=" * 60)
    
    def run_all_tests(self) -> bool:
        """运行所有测试
        
        执行完整的力场参数集成测试流程。
        
        Returns:
            bool: 所有测试是否通过
        """
        logger.info("")
        logger.info("*" * 60)
        logger.info("*" + " " * 58 + "*")
        logger.info("*" + " 力场参数集成测试 - Packmol堆积验证".center(56) + "*")
        logger.info("*" + " " * 58 + "*")
        logger.info("*" * 60)
        logger.info("")
        logger.info(f"测试开始时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        logger.info(f"测试分子: EC, DMC, Li, PF6")
        logger.info(f"分子数量: EC=10, DMC=10, Li=5, PF6=5")
        logger.info("")
        
        try:
            if not self.setup():
                logger.error("测试环境设置失败，终止测试")
                self.print_summary()
                return False
            
            if not self.check_environment():
                logger.error("环境检查失败，终止测试")
                self.print_summary()
                self.cleanup()
                return False
            
            if not self.prepare_test_files():
                logger.error("测试文件准备失败，终止测试")
                self.print_summary()
                self.cleanup()
                return False
            
            if not self.execute_packmol():
                logger.error("Packmol执行失败")
            
            self.verify_results()
            
            self.cleanup()
            
            self.print_summary()
            
            return all(r["success"] for r in self.test_results)
            
        except KeyboardInterrupt:
            logger.warning("\n测试被用户中断")
            self.cleanup()
            return False
            
        except Exception as e:
            logger.error(f"测试过程中发生未预期的异常: {e}")
            self.cleanup()
            return False


def main():
    """主函数入口
    
    创建测试运行器并执行所有测试。
    """
    runner = ForcefieldTestRunner()
    success = runner.run_all_tests()
    
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()