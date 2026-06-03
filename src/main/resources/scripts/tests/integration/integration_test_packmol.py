"""
Packmol集成测试脚本

本脚本用于真实执行Packmol集成测试（不使用mock），验证Docker容器中的Packmol功能。
测试内容包括：
    1. 环境检查：检查Docker和md-engine容器状态
    2. 创建测试分子模板文件（简单的EC分子PDB）
    3. 通过Docker容器执行Packmol（docker exec md-engine packmol）
    4. 验证PDB文件真实生成
    5. 验证文件系统变化（目录时间更新）
    6. 测试后清理临时文件

测试目录：temp/integration_test/

使用方法：
    python integration_test_packmol.py

作者: 电解液MD平台开发团队
版本: 1.0.0
"""

import subprocess
import os
import sys
import time
import shutil
import logging
from pathlib import Path
from typing import Dict, Any, List, Optional, Tuple
from datetime import datetime


# 配置日志输出
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(levelname)s - %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S"
)
logger = logging.getLogger(__name__)


# 常量定义
# 支持两种容器命名方式：md_engine（下划线）和md-engine（连字符）
DOCKER_CONTAINER_NAMES = ["md_engine", "md-engine"]
DOCKER_CONTAINER_NAME = None  # 将在运行时动态确定
PACKMOL_EXECUTABLE = "packmol"
TEST_DIR_NAME = "integration_test"

# 使用已挂载到容器的目录（data/md_platform_data → /workspace/data）
# 这样容器内可以访问测试文件
TEMP_BASE_DIR = Path("data") / "md_platform_data" / "test_temp" / TEST_DIR_NAME

# Docker容器内的路径映射
DOCKER_WORK_DIR = "/workspace/data/test_temp/integration_test/work"

# 测试超时时间（秒）
DOCKER_CHECK_TIMEOUT = 30
PACKMOL_EXECUTION_TIMEOUT = 300


class IntegrationTestRunner:
    """Packmol集成测试运行器
    
    本类封装了Packmol集成测试的完整流程，包括环境检查、测试执行、结果验证和清理工作。
    
    属性:
        test_dir (Path): 测试目录路径
        template_dir (Path): 分子模板目录路径
        work_dir (Path): Packmol工作目录路径
        output_dir (Path): 输出文件目录路径
        test_results (List[Dict]): 测试结果列表
        docker_available (bool): Docker是否可用
        container_running (bool): md-engine容器是否运行中
    
    使用示例:
        >>> runner = IntegrationTestRunner()
        >>> runner.run_all_tests()
    """
    
    def __init__(self):
        """初始化集成测试运行器
        
        创建测试所需的目录结构，并初始化测试状态。
        目录结构:
            temp/integration_test/
            ├── templates/          # 分子模板目录
            │   └── EC/
            │       └── EC.pdb
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
        
        logger.info(f"初始化集成测试运行器")
        logger.info(f"测试目录: {self.test_dir.absolute()}")
    
    def setup(self) -> bool:
        """设置测试环境
        
        创建测试目录结构，并创建测试用的分子模板文件。
        
        Returns:
            bool: 设置是否成功
        """
        logger.info("=" * 60)
        logger.info("步骤1: 设置测试环境")
        logger.info("=" * 60)
        
        try:
            # 创建测试目录结构
            logger.info("创建测试目录结构...")
            self.test_dir.mkdir(parents=True, exist_ok=True)
            self.template_dir.mkdir(parents=True, exist_ok=True)
            self.work_dir.mkdir(parents=True, exist_ok=True)
            self.output_dir.mkdir(parents=True, exist_ok=True)
            
            logger.info(f"  - 测试目录: {self.test_dir}")
            logger.info(f"  - 模板目录: {self.template_dir}")
            logger.info(f"  - 工作目录: {self.work_dir}")
            logger.info(f"  - 输出目录: {self.output_dir}")
            
            # 创建EC分子模板
            self._create_ec_template()
            
            logger.info("测试环境设置完成 ✓")
            return True
            
        except Exception as e:
            logger.error(f"测试环境设置失败: {e}")
            return False
    
    def _create_ec_template(self) -> None:
        """创建EC（碳酸乙烯酯）分子PDB模板文件
        
        创建一个简化的EC分子PDB文件，用于Packmol测试。
        EC分子结构：C3H4O3，包含7个原子。
        """
        ec_dir = self.template_dir / "EC"
        ec_dir.mkdir(parents=True, exist_ok=True)
        
        ec_pdb_path = ec_dir / "EC.pdb"
        
        # EC分子的简化PDB结构
        # 包含3个碳原子、4个氢原子、3个氧原子
        ec_pdb_content = """HEADER    ETHYLENE CARBONATE
TITLE     EC MOLECULE - SIMPLIFIED FOR TESTING
COMPND    MOL_ID: 1;
COMPND   2 MOLECULE: ETHYLENE CARBONATE;
COMPND   3 CHAIN: A;
REMARK    THIS IS A SIMPLIFIED EC MOLECULE FOR INTEGRATION TESTING
ATOM      1  C1  EC     1       0.000   0.000   0.000  1.00  0.00           C
ATOM      2  C2  EC     1       1.520   0.000   0.000  1.00  0.00           C
ATOM      3  C3  EC     1       2.020   1.426   0.000  1.00  0.00           C
ATOM      4  O1  EC     1       0.760  -1.200   0.000  1.00  0.00           O
ATOM      5  O2  EC     1       1.260   2.626   0.000  1.00  0.00           O
ATOM      6  O3  EC     1      -0.500   1.000   0.000  1.00  0.00           O
ATOM      7  H1  EC     1      -0.540  -0.970   0.000  1.00  0.00           H
ATOM      8  H2  EC     1      -0.540   0.485   0.866  1.00  0.00           H
ATOM      9  H3  EC     1      -0.540   0.485  -0.866  1.00  0.00           H
ATOM     10  H4  EC     1       1.980  -0.485   0.866  1.00  0.00           H
ATOM     11  H5  EC     1       1.980  -0.485  -0.866  1.00  0.00           H
ATOM     12  H6  EC     1       3.110   1.526   0.000  1.00  0.00           H
END
"""
        
        with open(ec_pdb_path, "w", encoding="utf-8") as f:
            f.write(ec_pdb_content)
        
        logger.info(f"  - 创建EC分子模板: {ec_pdb_path}")
        logger.info(f"    原子数量: 12 (C:3, H:6, O:3)")
    
    def check_environment(self) -> bool:
        """检查测试环境
        
        检查Docker是否可用，以及md-engine容器是否正在运行。
        
        Returns:
            bool: 环境检查是否通过
        """
        logger.info("")
        logger.info("=" * 60)
        logger.info("步骤2: 检查测试环境")
        logger.info("=" * 60)
        
        # 检查Docker是否可用
        logger.info("检查Docker是否可用...")
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
        
        # 检查md-engine容器是否运行
        logger.info("检查md-engine容器状态...")
        container_check = self._check_container_running()
        
        if not container_check["success"]:
            logger.error(f"  ✗ md-engine容器未运行: {container_check['error']}")
            self._record_test_result(
                "环境检查-容器状态",
                False,
                container_check["error"]
            )
            return False
        
        logger.info(f"  ✓ md-engine容器正在运行")
        self.container_running = True
        self._record_test_result(
            "环境检查-容器状态",
            True,
            "容器运行中"
        )
        
        # 检查容器中Packmol是否可用
        logger.info("检查容器中Packmol是否可用...")
        packmol_check = self._check_packmol_in_container()
        
        if not packmol_check["success"]:
            logger.error(f"  ✗ Packmol不可用: {packmol_check['error']}")
            self._record_test_result(
                "环境检查-Packmol可用性",
                False,
                packmol_check["error"]
            )
            return False
        
        logger.info(f"  ✓ Packmol可用")
        self._record_test_result(
            "环境检查-Packmol可用性",
            True,
            "Packmol已安装"
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
        """检查md-engine容器是否正在运行
        
        支持两种容器命名方式：md_engine（下划线）和md-engine（连字符）
        动态查找正在运行的容器名称
        
        Returns:
            Dict: 检查结果，包含success、status、error、container_name字段
        """
        global DOCKER_CONTAINER_NAME
        
        try:
            # 首先获取所有运行中的容器
            result = subprocess.run(
                ["docker", "ps", "--filter", "status=running", "--format", "{{.Names}}"],
                capture_output=True,
                text=True,
                timeout=DOCKER_CHECK_TIMEOUT,
                shell=False
            )
            
            if result.returncode == 0:
                running_containers = result.stdout.strip().split('\n')
                
                # 查找匹配的容器名称
                for container_name in DOCKER_CONTAINER_NAMES:
                    if container_name in running_containers:
                        # 动态设置全局容器名称
                        DOCKER_CONTAINER_NAME = container_name
                        logger.info(f"  找到运行中的容器: {container_name}")
                        return {
                            "success": True,
                            "status": "running",
                            "container_name": container_name,
                            "error": None
                        }
                
                # 检查容器是否存在但未运行
                for container_name in DOCKER_CONTAINER_NAMES:
                    if container_name in running_containers:
                        continue  # 已经在上面检查过了
                    
                    # 检查所有容器（包括停止的）
                    result_all = subprocess.run(
                        ["docker", "ps", "-a", "--filter", f"name={container_name}", "--format", "{{.Names}}\t{{.Status}}"],
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
                # 尝试直接运行packmol检查
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
        
        将分子模板复制到工作目录，并生成Packmol输入脚本。
        
        Returns:
            bool: 准备是否成功
        """
        logger.info("")
        logger.info("=" * 60)
        logger.info("步骤3: 准备测试文件")
        logger.info("=" * 60)
        
        try:
            # 复制EC模板到工作目录
            src_pdb = self.template_dir / "EC" / "EC.pdb"
            dst_pdb = self.work_dir / "EC.pdb"
            
            shutil.copy2(src_pdb, dst_pdb)
            logger.info(f"  - 复制EC.pdb到工作目录: {dst_pdb}")
            
            # 生成Packmol输入脚本
            input_script = self._generate_packmol_input()
            logger.info(f"  - 生成Packmol输入脚本: {input_script}")
            
            # 记录工作目录修改时间
            self.work_dir_mtime_before = datetime.fromtimestamp(self.work_dir.stat().st_mtime)
            logger.info(f"  - 工作目录修改时间（测试前）: {self.work_dir_mtime_before}")
            
            logger.info("测试文件准备完成 ✓")
            self._record_test_result(
                "测试文件准备",
                True,
                f"EC.pdb已复制，packmol.inp已生成"
            )
            return True
            
        except Exception as e:
            logger.error(f"测试文件准备失败: {e}")
            self._record_test_result(
                "测试文件准备",
                False,
                str(e)
            )
            return False
    
    def _generate_packmol_input(self) -> Path:
        """生成Packmol输入脚本
        
        生成用于测试的Packmol输入脚本，包含简单的EC分子堆积配置。
        
        Returns:
            Path: 输入脚本文件路径
        """
        input_path = self.work_dir / "packmol.inp"
        
        # Packmol输入脚本内容
        # 堆积10个EC分子到一个30x30x30埃的盒子中
        input_content = f"""tolerance 2.0
seed 12345
maxit 100

structure EC.pdb
  number 10
  inside box 0. 0. 0. 30. 30. 30.
end structure

output packed_system.pdb
"""
        
        with open(input_path, "w", encoding="utf-8") as f:
            f.write(input_content)
        
        return input_path
    
    def execute_packmol(self) -> bool:
        """执行Packmol测试
        
        通过Docker容器执行Packmol，进行分子堆积测试。
        
        Returns:
            bool: 执行是否成功
        """
        logger.info("")
        logger.info("=" * 60)
        logger.info("步骤4: 执行Packmol测试")
        logger.info("=" * 60)
        
        # 获取工作目录的绝对路径
        work_dir_abs = self.work_dir.absolute()
        
        logger.info(f"本地工作目录: {work_dir_abs}")
        logger.info(f"容器工作目录: {DOCKER_WORK_DIR}")
        logger.info(f"执行命令: docker exec -w {DOCKER_WORK_DIR} {DOCKER_CONTAINER_NAME} {PACKMOL_EXECUTABLE} < packmol.inp")
        
        start_time = time.time()
        
        try:
            # 在Docker容器中执行Packmol
            # 使用已挂载的目录（data/md_platform_data → /workspace/data）
            # Packmol需要从文件读取输入，不能使用stdin管道（Fortran不支持）
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
            
            # 输出执行结果
            logger.info(f"执行时间: {execution_time:.2f}秒")
            logger.info(f"返回码: {result.returncode}")
            
            if result.stdout:
                logger.info("标准输出:")
                for line in result.stdout.strip().split("\n"):
                    logger.info(f"  {line}")
            
            if result.stderr:
                logger.info("错误输出:")
                for line in result.stderr.strip().split("\n"):
                    logger.info(f"  {line}")
            
            # 检查执行结果
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
            
            # 保存执行输出
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
            f.write(f"Packmol执行日志\n")
            f.write(f"{'=' * 60}\n")
            f.write(f"执行时间: {datetime.now().isoformat()}\n")
            f.write(f"耗时: {execution_time:.2f}秒\n")
            f.write(f"{'=' * 60}\n\n")
            f.write(f"标准输出:\n{stdout}\n\n")
            f.write(f"错误输出:\n{stderr}\n")
        
        logger.info(f"  - 执行日志已保存: {output_file}")
    
    def verify_results(self) -> bool:
        """验证测试结果
        
        验证Packmol输出文件是否正确生成，并检查文件内容。
        
        Returns:
            bool: 验证是否通过
        """
        logger.info("")
        logger.info("=" * 60)
        logger.info("步骤5: 验证测试结果")
        logger.info("=" * 60)
        
        all_passed = True
        
        # 验证1: 检查PDB文件是否存在
        pdb_file = self.work_dir / "packed_system.pdb"
        logger.info(f"验证1: 检查PDB文件是否存在")
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
            all_passed = False
            return all_passed
        
        # 验证2: 检查PDB文件内容
        logger.info(f"验证2: 检查PDB文件内容")
        validation_result = self._validate_pdb_content(pdb_file)
        
        if validation_result["valid"]:
            logger.info(f"  ✓ PDB文件内容有效")
            logger.info(f"    - 原子数量: {validation_result['atom_count']}")
            logger.info(f"    - 残基数量: {validation_result['residue_count']}")
            self._record_test_result(
                "验证-PDB文件内容",
                True,
                f"原子数: {validation_result['atom_count']}, 残基数: {validation_result['residue_count']}"
            )
        else:
            logger.error(f"  ✗ PDB文件内容无效: {validation_result['errors']}")
            self._record_test_result(
                "验证-PDB文件内容",
                False,
                str(validation_result["errors"])
            )
            all_passed = False
        
        # 验证3: 检查原子数量是否符合预期
        logger.info(f"验证3: 检查原子数量是否符合预期")
        expected_atoms = 10 * 12  # 10个EC分子，每个12个原子
        
        if validation_result["atom_count"] == expected_atoms:
            logger.info(f"  ✓ 原子数量正确: {validation_result['atom_count']} (预期: {expected_atoms})")
            self._record_test_result(
                "验证-原子数量",
                True,
                f"原子数: {validation_result['atom_count']} (预期: {expected_atoms})"
            )
        else:
            logger.warning(f"  ! 原子数量不匹配: {validation_result['atom_count']} (预期: {expected_atoms})")
            self._record_test_result(
                "验证-原子数量",
                False,
                f"实际: {validation_result['atom_count']}, 预期: {expected_atoms}"
            )
            # 原子数量不匹配不视为失败，因为Packmol可能成功堆积但原子数有差异
        
        # 验证4: 检查文件系统变化
        logger.info(f"验证4: 检查文件系统变化")
        self.work_dir_mtime_after = datetime.fromtimestamp(self.work_dir.stat().st_mtime)
        logger.info(f"  工作目录修改时间（测试后）: {self.work_dir_mtime_after}")
        
        if self.work_dir_mtime_after > self.work_dir_mtime_before:
            logger.info(f"  ✓ 目录修改时间已更新")
            self._record_test_result(
                "验证-目录时间更新",
                True,
                f"修改时间: {self.work_dir_mtime_after}"
            )
        else:
            logger.warning(f"  ! 目录修改时间未更新")
            self._record_test_result(
                "验证-目录时间更新",
                False,
                "目录修改时间未变化"
            )
        
        if all_passed:
            logger.info("结果验证通过 ✓")
        else:
            logger.error("结果验证存在失败项 ✗")
        
        return all_passed
    
    def _validate_pdb_content(self, pdb_path: Path) -> Dict[str, Any]:
        """验证PDB文件内容
        
        解析PDB文件，检查原子记录、残基信息等。
        
        Args:
            pdb_path: PDB文件路径
            
        Returns:
            Dict: 验证结果，包含valid、atom_count、residue_count、errors字段
        """
        result = {
            "valid": False,
            "atom_count": 0,
            "residue_count": 0,
            "chain_count": 0,
            "errors": []
        }
        
        try:
            atom_count = 0
            residue_set = set()
            chain_set = set()
            
            with open(pdb_path, "r", encoding="utf-8") as f:
                for line in f:
                    if line.startswith("ATOM") or line.startswith("HETATM"):
                        atom_count += 1
                        
                        try:
                            residue_name = line[17:20].strip()
                            residue_num = int(line[22:26].strip())
                            chain_id = line[21].strip()
                            
                            residue_set.add((residue_name, residue_num))
                            chain_set.add(chain_id)
                        except (ValueError, IndexError):
                            pass
            
            result["atom_count"] = atom_count
            result["residue_count"] = len(residue_set)
            result["chain_count"] = len(chain_set)
            
            if atom_count == 0:
                result["errors"].append("PDB文件中未找到原子记录")
            else:
                result["valid"] = True
            
        except Exception as e:
            result["errors"].append(f"PDB文件解析错误: {str(e)}")
        
        return result
    
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
                # 列出要删除的文件
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
        
        # 打印详细结果
        logger.info("详细结果:")
        for i, result in enumerate(self.test_results, 1):
            status = "✓ 通过" if result["success"] else "✗ 失败"
            logger.info(f"  {i}. {result['test_name']}: {status}")
            if result["message"]:
                logger.info(f"     消息: {result['message']}")
        
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
        
        执行完整的集成测试流程。
        
        Returns:
            bool: 所有测试是否通过
        """
        logger.info("")
        logger.info("*" * 60)
        logger.info("*" + " " * 58 + "*")
        logger.info("*" + " Packmol集成测试".center(56) + "*")
        logger.info("*" + " " * 58 + "*")
        logger.info("*" * 60)
        logger.info("")
        logger.info(f"测试开始时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
        logger.info("")
        
        try:
            # 步骤1: 设置测试环境
            if not self.setup():
                logger.error("测试环境设置失败，终止测试")
                self.print_summary()
                return False
            
            # 步骤2: 检查环境
            if not self.check_environment():
                logger.error("环境检查失败，终止测试")
                self.print_summary()
                self.cleanup()
                return False
            
            # 步骤3: 准备测试文件
            if not self.prepare_test_files():
                logger.error("测试文件准备失败，终止测试")
                self.print_summary()
                self.cleanup()
                return False
            
            # 步骤4: 执行Packmol
            if not self.execute_packmol():
                logger.error("Packmol执行失败")
                # 继续验证和清理
            
            # 步骤5: 验证结果
            self.verify_results()
            
            # 步骤6: 清理
            self.cleanup()
            
            # 打印摘要
            self.print_summary()
            
            # 返回是否所有测试通过
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
    runner = IntegrationTestRunner()
    success = runner.run_all_tests()
    
    # 返回退出码
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()