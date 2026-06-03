"""
Packmol工具封装模块

本模块提供Packmol分子打包功能的完整封装，用于实现分子初始构型的随机堆积与无重叠构型生成。
Packmol是一个广泛使用的分子动力学模拟前处理工具，能够将多个分子随机放置在指定盒子内，
同时保证分子之间没有空间重叠。

核心功能:
    1. 生成标准格式的packmol.inp输入脚本
    2. 执行Packmol程序并捕获输出
    3. 验证输出PDB文件的有效性
    4. 管理临时文件的创建与清理

关键特性:
    - tolerance参数固定为2.0埃，不可修改（这是Packmol推荐的默认值）
    - 所有临时文件存放在全局temp/packmol_temp/目录
    - 执行完成后自动清理临时文件
    - 输出文件(packed_system.pdb和packmol.inp)自动复制到任务的inputs目录

文件系统规范:
    根据项目文档"配方及计算引擎功能详细设计v1.0"中的文件系统结构：
    - 临时文件目录: temp/packmol_temp/（全局临时目录，定时清理）
    - 输入文件目录: user_{user_id}/jobs/job_{job_id}/inputs/
    - 输出文件:
        - packmol.inp: Packmol输入脚本
        - packed_system.pdb: Packmol生成的初始构型文件

使用示例:
    >>> from packmol_utils import run_packmol_packing
    >>> molecules = [
    ...     {"name": "EC", "count": 50, "pdb_file": "EC.pdb"},
    ...     {"name": "EMC", "count": 30, "pdb_file": "EMC.pdb"}
    ... ]
    >>> box_size = {"x": 50.0, "y": 50.0, "z": 50.0}
    >>> result = run_packmol_packing(molecules, box_size, job_dir="/path/to/job")

作者: 电解液MD平台开发团队
版本: 1.0.0
"""

import logging
import subprocess
import os
import shutil
import tempfile
import time
from pathlib import Path
from typing import Dict, Any, List, Optional, Tuple
from datetime import datetime

try:
    from ..config.config import config
except ImportError:
    from config import config

logger = logging.getLogger(__name__)

PACKMOL_TOLERANCE_FIXED = 2.0
PACKMOL_MAX_ITERATIONS_DEFAULT = 100
PACKMOL_DEFAULT_TIMEOUT = 3600
PACKMOL_MAX_RETRY_COUNT = 3
PACKMOL_BOX_SCALE_FACTOR = 1.1


class PackmolRunner:
    """Packmol运行器类
    
    本类封装了Packmol分子堆积功能的核心实现，提供完整的生命周期管理。
    包括输入脚本生成、程序执行、输出验证和临时文件清理等功能。
    
    主要职责:
        1. 生成符合Packmol标准的输入脚本文件(packmol.inp)
        2. 调用Packmol可执行程序并捕获标准输出和错误输出
        3. 验证生成的PDB文件是否有效（原子数量、残基信息等）
        4. 管理临时文件的创建、使用和清理
    
    属性:
        packmol_path (str): Packmol可执行文件的路径
        tolerance (float): 分子间最小距离容差，固定为2.0埃
        max_iterations (int): 最大迭代次数
        temp_dir (Path): 临时文件目录路径
        _temp_files (List[Path]): 临时文件列表，用于清理
        _execution_log (List[str]): 执行日志列表，用于记录和调试
    
    注意事项:
        - tolerance参数固定为2.0埃，不接受外部设置
        - 临时文件默认存放在temp/packmol_temp/目录
        - 执行完成后应调用cleanup_temp_files()清理临时文件
        - 应调用move_outputs_to_inputs()将结果文件移动到inputs目录
    
    使用示例:
        >>> runner = PackmolRunner(packmol_path="/usr/local/bin/packmol")
        >>> input_file = runner.generate_packmol_input_script(molecules, box_size, output_path)
        >>> result = runner.execute_packmol(input_file)
        >>> if result["success"]:
        ...     validation = runner.validate_pdb_output(output_path)
        ...     runner.move_outputs_to_inputs(input_file, output_path, inputs_dir)
        >>> runner.cleanup_temp_files()
    """
    
    def __init__(
        self,
        packmol_path: str = "packmol",
        max_iterations: int = None,
        temp_dir: str = None,
    ):
        """初始化Packmol运行器
        
        创建PackmolRunner实例，设置执行参数和临时目录。
        
        Args:
            packmol_path (str): Packmol可执行文件的路径。
                可以是绝对路径，也可以是系统PATH中的可执行文件名。
                默认值为"packmol"，表示从系统PATH中查找。
                示例: "/usr/local/bin/packmol" 或 "packmol"
            
            max_iterations (int, optional): Packmol的最大迭代次数。
                如果未指定，使用默认值PACKMOL_MAX_ITERATIONS_DEFAULT(100)。
                更大的值可能导致更长的运行时间，但可能提高成功率。
            
            temp_dir (str, optional): 临时文件存储目录。
                如果未指定，使用全局默认目录"temp/packmol_temp/"。
                该目录用于存储packmol.inp输入脚本和packed_system.pdb输出文件。
        
        Note:
            - tolerance参数固定为2.0埃，不接受外部设置
            - 临时目录如果不存在会自动创建
            - 建议使用全局临时目录以符合项目文件系统规范
        
        Example:
            >>> # 使用默认配置
            >>> runner = PackmolRunner()
            
            >>> # 指定Packmol路径
            >>> runner = PackmolRunner(packmol_path="/usr/local/bin/packmol")
            
            >>> # 指定临时目录
            >>> runner = PackmolRunner(temp_dir="/tmp/packmol_work")
        """
        self.packmol_path = packmol_path
        self.tolerance = PACKMOL_TOLERANCE_FIXED
        self.max_iterations = max_iterations or PACKMOL_MAX_ITERATIONS_DEFAULT
        
        if temp_dir:
            self.temp_dir = Path(temp_dir)
        else:
            self.temp_dir = Path("temp") / "packmol_temp"
        
        self.temp_dir.mkdir(parents=True, exist_ok=True)
        
        self._temp_files: List[Path] = []
        self._execution_log: List[str] = []
    
    def generate_packmol_input_script(
        self,
        molecules: List[Dict[str, Any]],
        box_size: Dict[str, float],
        output_pdb_path: str,
        seed: int = None,
    ) -> str:
        """生成标准格式的packmol.inp输入脚本
        
        根据分子列表和盒子尺寸，生成符合Packmol标准的输入脚本文件。
        该脚本定义了每个分子的类型、数量和放置区域。
        
        Args:
            molecules (List[Dict[str, Any]]): 分子配置列表。
                每个分子字典应包含以下字段:
                    - name (str): 分子名称，用于日志记录
                    - count (int): 该分子的数量
                    - pdb_file (str): 分子PDB模板文件的路径
                
                示例:
                    >>> molecules = [
                    ...     {"name": "EC", "count": 50, "pdb_file": "/path/to/EC.pdb"},
                    ...     {"name": "LiPF6", "count": 10, "pdb_file": "/path/to/LiPF6.pdb"}
                    ... ]
            
            box_size (Dict[str, float]): 模拟盒子的尺寸（单位：埃）。
                应包含x、y、z三个维度的尺寸。
                
                示例:
                    >>> box_size = {"x": 50.0, "y": 50.0, "z": 50.0}
            
            output_pdb_path (str): 输出PDB文件的完整路径。
                Packmol将把堆积好的分子构型写入此文件。
                示例: "/path/to/packed_system.pdb"
            
            seed (int, optional): 随机数生成器的种子值。
                用于结果可重现性。如果未指定，使用当前时间戳。
                相同的种子值会产生相同的分子初始构型。
        
        Returns:
            str: 生成的输入脚本文件的完整路径。
                该文件位于临时目录中，文件名为"packmol.inp"。
        
        Note:
            - 生成的输入脚本使用固定的tolerance值(2.0埃)
            - 所有分子将被放置在盒子内部(inside box)
            - 分子坐标范围从(0, 0, 0)到(box_size.x, box_size.y, box_size.z)
        
        Example:
            >>> runner = PackmolRunner()
            >>> input_file = runner.generate_packmol_input_script(
            ...     molecules=[{"name": "EC", "count": 50, "pdb_file": "EC.pdb"}],
            ...     box_size={"x": 50.0, "y": 50.0, "z": 50.0},
            ...     output_pdb_path="packed_system.pdb",
            ...     seed=12345
            ... )
        """
        logger.info("生成Packmol输入脚本")
        
        if seed is None:
            seed = int(time.time())
        
        input_lines = [
            f"tolerance {self.tolerance}",
            f"seed {seed}",
            f"maxit {self.max_iterations}",
            "",
        ]
        
        x = box_size.get("x", 50.0)
        y = box_size.get("y", 50.0)
        z = box_size.get("z", 50.0)
        
        for mol in molecules:
            name = mol.get("name", "Unknown")
            count = mol.get("count", 1)
            pdb_file = mol.get("pdb_file", f"{name}.pdb")
            
            logger.debug(f"添加分子 {name}: 数量={count}, PDB={pdb_file}")
            
            input_lines.extend([
                f"structure {pdb_file}",
                f"  number {count}",
                f"  inside box 0. 0. 0. {x:.2f} {y:.2f} {z:.2f}",
                "end structure",
                "",
            ])
        
        input_lines.append(f"output {output_pdb_path}")
        input_lines.append("")
        
        input_content = "\n".join(input_lines)
        
        input_file = self.temp_dir / "packmol.inp"
        
        with open(input_file, "w", encoding="utf-8") as f:
            f.write(input_content)
        
        self._temp_files.append(input_file)
        
        logger.info(f"Packmol输入脚本已生成: {input_file}")
        logger.debug(f"输入脚本内容:\n{input_content}")
        
        return str(input_file)
    
    def execute_packmol(
        self,
        input_file: str,
        working_dir: str = None,
        timeout: int = PACKMOL_DEFAULT_TIMEOUT,
    ) -> Dict[str, Any]:
        """执行Packmol程序并捕获输出
        
        调用Packmol可执行程序，传入输入脚本，并捕获标准输出和错误输出。
        支持超时控制和异常处理。
        
        Args:
            input_file (str): Packmol输入脚本文件的路径。
                该文件通常由generate_packmol_input_script()方法生成。
            
            working_dir (str, optional): Packmol的工作目录。
                Packmol将在此目录下执行，PDB模板文件应位于此目录或其子目录中。
                如果未指定，使用临时目录作为工作目录。
            
            timeout (int): 执行超时时间（秒）。
                默认值为PACKMOL_DEFAULT_TIMEOUT(3600秒=1小时)。
                如果执行超过此时间，将被强制终止。
        
        Returns:
            Dict[str, Any]: 运行结果字典，包含以下字段:
                - success (bool): 执行是否成功（return_code为0表示成功）
                - stdout (str): Packmol的标准输出内容
                - stderr (str): Packmol的错误输出内容
                - return_code (int): 进程返回码（0表示成功，负数表示异常）
                - execution_time (float): 执行耗时（秒）
                - error_type (str|None): 错误类型，可能的值:
                    - None: 执行成功
                    - "execution_error": Packmol执行失败（返回非零码）
                    - "timeout": 执行超时
                    - "executable_not_found": Packmol可执行文件未找到
                    - "unknown_error": 未知异常
        
        Raises:
            不直接抛出异常，所有异常都被捕获并返回在结果字典中。
        
        Note:
            - 执行日志会被记录到实例的_execution_log列表中
            - 超时后进程会被强制终止
            - 建议在调用前使用check_installation()验证Packmol是否可用
        
        Example:
            >>> runner = PackmolRunner()
            >>> result = runner.execute_packmol("packmol.inp", timeout=1800)
            >>> if result["success"]:
            ...     print(f"执行成功，耗时{result['execution_time']:.2f}秒")
            ... else:
            ...     print(f"执行失败: {result['error_type']}")
        """
        logger.info(f"执行Packmol: {input_file}")
        
        working_dir = working_dir or str(self.temp_dir)
        
        start_time = time.time()
        
        self._execution_log.append(f"[{datetime.now().isoformat()}] 开始执行Packmol")
        self._execution_log.append(f"输入文件: {input_file}")
        self._execution_log.append(f"工作目录: {working_dir}")
        self._execution_log.append(f"超时设置: {timeout}秒")
        
        try:
            result = subprocess.run(
                [self.packmol_path],
                stdin=open(input_file, "r"),
                shell=False,
                cwd=working_dir,
                capture_output=True,
                text=True,
                timeout=timeout,
            )
            
            execution_time = time.time() - start_time
            success = result.returncode == 0
            
            self._execution_log.append(f"[{datetime.now().isoformat()}] 执行完成")
            self._execution_log.append(f"返回码: {result.returncode}")
            self._execution_log.append(f"执行时间: {execution_time:.2f}秒")
            
            if success:
                logger.info(f"Packmol执行成功，耗时{execution_time:.2f}秒")
                self._execution_log.append("状态: 成功")
            else:
                logger.error(f"Packmol执行失败: {result.stderr}")
                self._execution_log.append("状态: 失败")
                self._execution_log.append(f"错误输出: {result.stderr}")
            
            return {
                "success": success,
                "stdout": result.stdout,
                "stderr": result.stderr,
                "return_code": result.returncode,
                "execution_time": execution_time,
                "error_type": None if success else "execution_error",
            }
            
        except subprocess.TimeoutExpired as e:
            execution_time = time.time() - start_time
            logger.error(f"Packmol执行超时: {timeout}秒")
            
            self._execution_log.append(f"[{datetime.now().isoformat()}] 执行超时")
            self._execution_log.append(f"超时时间: {timeout}秒")
            
            stdout_content = e.stdout.decode() if e.stdout else ""
            stderr_content = e.stderr.decode() if e.stderr else ""
            
            return {
                "success": False,
                "stdout": stdout_content,
                "stderr": stderr_content,
                "return_code": -1,
                "execution_time": execution_time,
                "error_type": "timeout",
            }
            
        except FileNotFoundError as e:
            execution_time = time.time() - start_time
            logger.error(f"Packmol可执行文件未找到: {self.packmol_path}")
            
            self._execution_log.append(f"[{datetime.now().isoformat()}] 可执行文件未找到")
            self._execution_log.append(f"路径: {self.packmol_path}")
            
            return {
                "success": False,
                "stdout": "",
                "stderr": f"Packmol executable not found: {self.packmol_path}",
                "return_code": -2,
                "execution_time": execution_time,
                "error_type": "executable_not_found",
            }
            
        except Exception as e:
            execution_time = time.time() - start_time
            logger.error(f"Packmol执行异常: {e}")
            
            self._execution_log.append(f"[{datetime.now().isoformat()}] 执行异常")
            self._execution_log.append(f"异常信息: {str(e)}")
            
            return {
                "success": False,
                "stdout": "",
                "stderr": str(e),
                "return_code": -3,
                "execution_time": execution_time,
                "error_type": "unknown_error",
            }
    
    def validate_pdb_output(
        self,
        pdb_path: str,
        expected_atoms: int = None,
    ) -> Dict[str, Any]:
        """验证packed_system.pdb输出文件的有效性
        
        解析Packmol生成的PDB文件，检查其结构和内容是否符合预期。
        验证内容包括文件存在性、文件大小、原子记录数量等。
        
        Args:
            pdb_path (str): 待验证的PDB文件路径。
                通常是Packmol的输出文件packed_system.pdb。
            
            expected_atoms (int, optional): 预期的原子总数。
                如果指定，将验证实际原子数是否与预期一致。
                用于检测分子堆积过程中是否有原子丢失。
        
        Returns:
            Dict[str, Any]: 验证结果字典，包含以下字段:
                - valid (bool): PDB文件是否有效
                - atom_count (int): 文件中的原子总数
                - residue_count (int): 文件中的残基总数
                - chain_count (int): 文件中的链数量
                - errors (List[str]): 错误信息列表，空列表表示无错误
        
        Note:
            - 验证通过的条件：文件存在、非空、包含原子记录
            - 如果指定了expected_atoms，原子数量必须匹配
            - 残基和链的数量仅作统计，不作为有效性判断条件
        
        Example:
            >>> runner = PackmolRunner()
            >>> validation = runner.validate_pdb_output("packed_system.pdb", expected_atoms=5000)
            >>> if validation["valid"]:
            ...     print(f"验证通过: {validation['atom_count']}个原子")
            ... else:
            ...     print(f"验证失败: {validation['errors']}")
        """
        logger.info(f"验证PDB输出文件: {pdb_path}")
        
        pdb_file = Path(pdb_path)
        
        validation_result = {
            "valid": False,
            "atom_count": 0,
            "residue_count": 0,
            "chain_count": 0,
            "errors": [],
        }
        
        if not pdb_file.exists():
            validation_result["errors"].append(f"PDB文件不存在: {pdb_path}")
            logger.error(f"PDB文件不存在: {pdb_path}")
            return validation_result
        
        if pdb_file.stat().st_size == 0:
            validation_result["errors"].append("PDB文件为空")
            logger.error("PDB文件为空")
            return validation_result
        
        try:
            atom_count = 0
            residue_set = set()
            chain_set = set()
            
            with open(pdb_file, "r", encoding="utf-8") as f:
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
            
            validation_result["atom_count"] = atom_count
            validation_result["residue_count"] = len(residue_set)
            validation_result["chain_count"] = len(chain_set)
            
            if atom_count == 0:
                validation_result["errors"].append("PDB文件中未找到原子记录")
                logger.error("PDB文件中未找到原子记录")
                return validation_result
            
            if expected_atoms is not None and atom_count != expected_atoms:
                validation_result["errors"].append(
                    f"原子数量不匹配: 预期{expected_atoms}, 实际{atom_count}"
                )
                logger.warning(f"原子数量不匹配: 预期{expected_atoms}, 实际{atom_count}")
            
            validation_result["valid"] = len(validation_result["errors"]) == 0
            
            if validation_result["valid"]:
                logger.info(f"PDB文件验证成功: 原子数={atom_count}, 残基数={len(residue_set)}")
            
            return validation_result
            
        except Exception as e:
            validation_result["errors"].append(f"PDB文件解析错误: {str(e)}")
            logger.error(f"PDB文件解析错误: {str(e)}")
            return validation_result
    
    def cleanup_temp_files(
        self,
        keep_input_script: bool = False,
        keep_output_pdb: bool = False,
    ) -> Dict[str, Any]:
        """清理临时文件
        
        删除Packmol执行过程中产生的临时文件。
        通常在执行完成并保存结果后调用。
        
        Args:
            keep_input_script (bool): 是否保留packmol.inp输入脚本文件。
                默认为False，即删除。
                如果需要保留用于调试或记录，设为True。
            
            keep_output_pdb (bool): 是否保留packed_system.pdb输出文件。
                默认为False，即删除。
                注意：通常在调用move_outputs_to_inputs()后，临时目录中的
                packed_system.pdb已复制到inputs目录，可以安全删除。
        
        Returns:
            Dict[str, Any]: 清理结果字典，包含以下字段:
                - deleted_files (List[str]): 已删除的文件路径列表
                - kept_files (List[str]): 保留的文件路径列表
                - errors (List[str]): 删除过程中的错误信息列表
        
        Note:
            - 此方法只清理通过_temp_files列表跟踪的文件
            - 建议在finally块中调用，确保临时文件被清理
            - 如果文件已被移动或删除，不会报错
        
        Example:
            >>> runner = PackmolRunner()
            >>> # ... 执行Packmol ...
            >>> runner.move_outputs_to_inputs(...)  # 先复制结果
            >>> runner.cleanup_temp_files()  # 再清理临时文件
        """
        logger.info("清理Packmol临时文件")
        
        cleanup_result = {
            "deleted_files": [],
            "kept_files": [],
            "errors": [],
        }
        
        for temp_file in self._temp_files:
            try:
                if not temp_file.exists():
                    continue
                
                filename = temp_file.name
                
                if keep_input_script and filename == "packmol.inp":
                    cleanup_result["kept_files"].append(str(temp_file))
                    logger.debug(f"保留文件: {temp_file}")
                    continue
                
                if keep_output_pdb and filename == "packed_system.pdb":
                    cleanup_result["kept_files"].append(str(temp_file))
                    logger.debug(f"保留文件: {temp_file}")
                    continue
                
                temp_file.unlink()
                cleanup_result["deleted_files"].append(str(temp_file))
                logger.debug(f"删除临时文件: {temp_file}")
                
            except Exception as e:
                cleanup_result["errors"].append(f"删除文件失败: {temp_file}, 错误: {str(e)}")
                logger.warning(f"删除临时文件失败: {temp_file}, 错误: {str(e)}")
        
        self._temp_files = []
        
        logger.info(f"临时文件清理完成: 删除{len(cleanup_result['deleted_files'])}个文件")
        
        return cleanup_result
    
    def save_execution_log(self, log_path: str) -> None:
        """保存执行日志到文件
        
        将Packmol执行过程中的日志信息保存到指定文件。
        用于问题排查和执行记录归档。
        
        Args:
            log_path (str): 日志文件的完整路径。
                文件将以文本格式保存，每条日志占一行。
                如果目录不存在，会自动创建。
        
        Note:
            - 日志内容包括执行时间、输入参数、返回码等信息
            - 日志文件会覆盖已存在的同名文件
            - 建议将日志文件保存在任务目录下
        
        Example:
            >>> runner = PackmolRunner()
            >>> # ... 执行Packmol ...
            >>> runner.save_execution_log("/path/to/job/packmol_execution.log")
        """
        log_file = Path(log_path)
        log_file.parent.mkdir(parents=True, exist_ok=True)
        
        with open(log_file, "w", encoding="utf-8") as f:
            f.write("\n".join(self._execution_log))
        
        logger.info(f"执行日志已保存: {log_path}")
    
    def check_installation(self) -> bool:
        """检查Packmol是否已正确安装
        
        验证Packmol可执行文件是否存在于系统PATH中或指定路径。
        在执行Packmol之前调用此方法可以提前发现问题。
        
        Returns:
            bool: Packmol是否已安装且可用。
                True表示可以正常调用，False表示未找到或无法执行。
        
        Note:
            - 此方法通过调用"packmol -h"来验证安装
            - 如果packmol_path是相对路径，会在系统PATH中查找
            - 建议在run_packmol_packing()开始时调用
        
        Example:
            >>> runner = PackmolRunner(packmol_path="/usr/local/bin/packmol")
            >>> if runner.check_installation():
            ...     print("Packmol已安装")
            ... else:
            ...     print("请先安装Packmol")
        """
        try:
            result = subprocess.run(
                [self.packmol_path, "-h"],
                capture_output=True,
                text=True,
                timeout=10,
            )
            installed = result.returncode == 0 or "packmol" in result.stdout.lower()
            logger.debug(f"Packmol安装检查: {'已安装' if installed else '未安装'}")
            return installed
        except Exception as e:
            logger.debug(f"Packmol安装检查失败: {e}")
            return False
    
    def move_outputs_to_inputs(
        self,
        input_script_path: str,
        output_pdb_path: str,
        target_inputs_dir: str,
    ) -> Dict[str, Any]:
        """将packmol.inp和packed_system.pdb复制到inputs目录
        
        执行完成后，将Packmol的输入脚本和输出PDB文件复制到任务的标准inputs目录。
        这符合项目文件系统规范，确保所有输入文件集中管理。
        
        Args:
            input_script_path (str): packmol.inp输入脚本的源路径。
                通常是临时目录中的packmol.inp文件。
            
            output_pdb_path (str): packed_system.pdb输出文件的源路径。
                通常是临时目录中的packed_system.pdb文件。
            
            target_inputs_dir (str): 目标inputs目录的路径。
                格式应为: user_{user_id}/jobs/job_{job_id}/inputs/
                如果目录不存在，会自动创建。
        
        Returns:
            Dict[str, Any]: 移动结果字典，包含以下字段:
                - success (bool): 操作是否全部成功
                - moved_files (List[str]): 成功复制的文件路径列表
                - errors (List[str]): 错误信息列表
        
        Note:
            - 此方法使用复制而非移动，原文件仍保留在临时目录
            - 需要在cleanup_temp_files()之前调用
            - 目标文件名固定为"packmol.inp"和"packed_system.pdb"
        
        Example:
            >>> runner = PackmolRunner()
            >>> # ... 执行Packmol ...
            >>> result = runner.move_outputs_to_inputs(
            ...     input_script_path="/temp/packmol_temp/packmol.inp",
            ...     output_pdb_path="/temp/packmol_temp/packed_system.pdb",
            ...     target_inputs_dir="/data/user_1/jobs/job_123/inputs"
            ... )
            >>> if result["success"]:
            ...     print(f"文件已复制: {result['moved_files']}")
        """
        logger.info(f"移动Packmol输出文件到inputs目录: {target_inputs_dir}")
        
        target_dir = Path(target_inputs_dir)
        target_dir.mkdir(parents=True, exist_ok=True)
        
        result = {
            "success": True,
            "moved_files": [],
            "errors": [],
        }
        
        try:
            input_src = Path(input_script_path)
            if input_src.exists():
                input_dst = target_dir / "packmol.inp"
                shutil.copy2(input_src, input_dst)
                result["moved_files"].append(str(input_dst))
                logger.debug(f"复制输入脚本: {input_src} -> {input_dst}")
            
            pdb_src = Path(output_pdb_path)
            if pdb_src.exists():
                pdb_dst = target_dir / "packed_system.pdb"
                shutil.copy2(pdb_src, pdb_dst)
                result["moved_files"].append(str(pdb_dst))
                logger.debug(f"复制输出PDB: {pdb_src} -> {pdb_dst}")
            
            logger.info(f"文件移动完成: {len(result['moved_files'])}个文件")
            
        except Exception as e:
            result["success"] = False
            result["errors"].append(str(e))
            logger.error(f"文件移动失败: {e}")
        
        return result


def fetch_molecule_templates(
    molecule_names: List[str],
    template_dir: str = None,
) -> Dict[str, Any]:
    """从模板库获取分子PDB文件信息
    
    检查指定分子的PDB模板文件是否存在，并返回模板路径信息。
    用于在执行Packmol之前验证所有必需的模板文件是否可用。
    
    Args:
        molecule_names (List[str]): 分子名称列表。
            每个名称应对应模板目录中的一个子目录。
            示例: ["EC", "EMC", "LiPF6"]
        
        template_dir (str, optional): 模板目录的根路径。
            如果未指定，使用默认路径"system_templates/molecule_templates/"。
            模板目录结构应为: template_dir/{molecule_name}/{molecule_name}.pdb
    
    Returns:
        Dict[str, Any]: 模板信息字典，包含以下字段:
            - found (List[str]): 找到模板的分子名称列表
            - missing (List[str]): 缺失模板的分子名称列表
            - templates (Dict[str, Dict]): 每个分子的详细信息:
                - pdb_path (str): PDB文件的完整路径
                - template_dir (str): 分子模板目录路径
                - exists (bool): PDB文件是否存在
    
    Note:
        - 此方法只检查文件存在性，不验证文件内容
        - 缺失模板会导致Packmol执行失败，应提前处理
    
    Example:
        >>> result = fetch_molecule_templates(["EC", "EMC"])
        >>> print(f"找到: {result['found']}, 缺失: {result['missing']}")
    """
    logger.info(f"获取分子模板: {molecule_names}")
    
    if template_dir is None:
        template_dir = Path("system_templates") / "molecule_templates"
    else:
        template_dir = Path(template_dir)
    
    result = {
        "found": [],
        "missing": [],
        "templates": {},
    }
    
    for mol_name in molecule_names:
        mol_template_dir = template_dir / mol_name
        pdb_file = mol_template_dir / f"{mol_name}.pdb"
        
        if pdb_file.exists():
            result["found"].append(mol_name)
            result["templates"][mol_name] = {
                "pdb_path": str(pdb_file),
                "template_dir": str(mol_template_dir),
                "exists": True,
            }
            logger.debug(f"找到模板: {mol_name} -> {pdb_file}")
        else:
            result["missing"].append(mol_name)
            result["templates"][mol_name] = {
                "pdb_path": str(pdb_file),
                "template_dir": str(mol_template_dir),
                "exists": False,
            }
            logger.warning(f"模板缺失: {mol_name}")
    
    if result["missing"]:
        logger.warning(f"缺失模板: {result['missing']}")
    
    logger.info(f"模板获取完成: 找到{len(result['found'])}个, 缺失{len(result['missing'])}个")
    
    return result


def copy_pdb_files_to_workdir(
    molecule_names: List[str],
    template_dir: str,
    work_dir: str,
) -> Dict[str, Any]:
    """复制PDB文件到工作目录
    
    将分子模板PDB文件从模板目录复制到Packmol工作目录。
    Packmol需要所有PDB文件位于同一目录下才能正确读取。
    
    Args:
        molecule_names (List[str]): 分子名称列表。
            这些分子的PDB文件将被复制。
        
        template_dir (str): 模板目录的根路径。
            格式: template_dir/{molecule_name}/{molecule_name}.pdb
        
        work_dir (str): 目标工作目录路径。
            Packmol将在此目录下执行，PDB文件将复制到此目录。
    
    Returns:
        Dict[str, Any]: 复制结果字典，包含以下字段:
            - success (bool): 是否全部复制成功
            - copied_files (List[str]): 成功复制的文件路径列表
            - missing_templates (List[str]): 缺失模板的分子名称列表
            - errors (List[str]): 错误信息列表
    
    Note:
        - 如果任何模板缺失，success将为False
        - 复制后的文件名为{molecule_name}.pdb
    
    Example:
        >>> result = copy_pdb_files_to_workdir(
        ...     molecule_names=["EC", "EMC"],
        ...     template_dir="/templates/molecule_templates",
        ...     work_dir="/temp/packmol_work"
        ... )
    """
    logger.info(f"复制PDB文件到工作目录: {work_dir}")
    
    template_path = Path(template_dir)
    work_path = Path(work_dir)
    work_path.mkdir(parents=True, exist_ok=True)
    
    result = {
        "success": True,
        "copied_files": [],
        "missing_templates": [],
        "errors": [],
    }
    
    for mol_name in molecule_names:
        src_pdb = template_path / mol_name / f"{mol_name}.pdb"
        dst_pdb = work_path / f"{mol_name}.pdb"
        
        if not src_pdb.exists():
            result["missing_templates"].append(mol_name)
            result["success"] = False
            logger.warning(f"模板文件缺失: {src_pdb}")
            continue
        
        try:
            shutil.copy2(src_pdb, dst_pdb)
            result["copied_files"].append(str(dst_pdb))
            logger.debug(f"复制PDB: {src_pdb} -> {dst_pdb}")
        except Exception as e:
            result["errors"].append(f"复制失败: {mol_name}, 错误: {str(e)}")
            result["success"] = False
            logger.error(f"复制PDB失败: {mol_name}, 错误: {str(e)}")
    
    logger.info(f"PDB复制完成: 成功{len(result['copied_files'])}个, 失败{len(result['errors'])}个")
    
    return result


def run_packmol_packing(
    molecules: List[Dict[str, Any]],
    box_size: Dict[str, float],
    job_dir: str,
    template_dir: str = None,
    packmol_path: str = "packmol",
    timeout: int = PACKMOL_DEFAULT_TIMEOUT,
    max_retries: int = PACKMOL_MAX_RETRY_COUNT,
) -> Dict[str, Any]:
    """执行Packmol分子堆积的主函数
    
    这是Packmol工具的主入口函数，整合了完整的分子堆积流程。
    包括模板获取、文件复制、输入生成、执行、验证、结果移动和清理等步骤。
    
    流程步骤:
        1. 获取并验证分子模板文件
        2. 复制PDB文件到全局临时工作目录
        3. 生成packmol.inp输入脚本
        4. 执行Packmol程序
        5. 验证输出PDB文件
        6. 将结果文件复制到任务的inputs目录
        7. 清理临时文件
        8. 如果失败，自动放大盒子尺寸并重试
    
    Args:
        molecules (List[Dict[str, Any]]): 分子配置列表。
            每个分子字典应包含:
                - name (str): 分子名称
                - count (int): 分子数量
            示例:
                >>> molecules = [
                ...     {"name": "EC", "count": 50},
                ...     {"name": "LiPF6", "count": 10}
                ... ]
        
        box_size (Dict[str, float]): 初始盒子尺寸（埃）。
            包含x、y、z三个维度。
            示例: {"x": 50.0, "y": 50.0, "z": 50.0}
        
        job_dir (str): 任务目录的完整路径。
            格式: user_{user_id}/jobs/job_{job_id}/
            结果文件将保存到job_dir/inputs/目录下。
        
        template_dir (str, optional): 分子模板目录路径。
            如果未指定，使用默认路径system_templates/molecule_templates/。
        
        packmol_path (str): Packmol可执行文件路径。
            默认为"packmol"，从系统PATH中查找。
        
        timeout (int): 单次执行超时时间（秒）。
            默认为PACKMOL_DEFAULT_TIMEOUT(3600秒)。
        
        max_retries (int): 最大重试次数。
            默认为PACKMOL_MAX_RETRY_COUNT(3)。
            每次重试会放大盒子尺寸(PACKMOL_BOX_SCALE_FACTOR=1.1倍)。
    
    Returns:
        Dict[str, Any]: 执行结果字典，包含以下字段:
            - success (bool): 是否成功完成分子堆积
            - molecules (List): 输入的分子列表
            - box_size (Dict): 初始盒子尺寸
            - attempts (List): 每次尝试的详细结果
            - final_box_size (Dict): 最终使用的盒子尺寸
            - output_files (List): 输出文件路径列表
            - errors (List): 错误信息列表
    
    Note:
        - 临时文件存放在全局temp/packmol_temp/目录
        - 输出文件保存到job_dir/inputs/目录:
            - packmol.inp: Packmol输入脚本
            - packed_system.pdb: 初始构型文件
        - 执行日志保存到job_dir/packmol_execution.log
    
    Example:
        >>> molecules = [
        ...     {"name": "EC", "count": 50},
        ...     {"name": "EMC", "count": 30}
        ... ]
        >>> box_size = {"x": 50.0, "y": 50.0, "z": 50.0}
        >>> result = run_packmol_packing(
        ...     molecules=molecules,
        ...     box_size=box_size,
        ...     job_dir="/data/user_1/jobs/job_123"
        ... )
        >>> if result["success"]:
        ...     print(f"成功，输出文件: {result['output_files']}")
    """
    logger.info("开始Packmol堆积流程")
    
    job_path = Path(job_dir)
    inputs_dir = job_path / "inputs"
    temp_dir = Path("temp") / "packmol_temp"
    
    temp_dir.mkdir(parents=True, exist_ok=True)
    
    result = {
        "success": False,
        "molecules": molecules,
        "box_size": box_size,
        "attempts": [],
        "final_box_size": box_size.copy(),
        "output_files": [],
        "errors": [],
    }
    
    molecule_names = [mol.get("name") for mol in molecules]
    
    template_result = fetch_molecule_templates(molecule_names, template_dir)
    
    if template_result["missing"]:
        result["errors"].append(f"缺失分子模板: {template_result['missing']}")
        logger.error(f"缺失分子模板: {template_result['missing']}")
        return result
    
    copy_result = copy_pdb_files_to_workdir(
        molecule_names,
        template_dir or str(Path("system_templates") / "molecule_templates"),
        str(temp_dir),
    )
    
    if not copy_result["success"]:
        result["errors"].extend(copy_result["errors"])
        result["errors"].extend([f"模板缺失: {m}" for m in copy_result["missing_templates"]])
        logger.error(f"PDB复制失败: {copy_result['errors']}")
        return result
    
    for mol in molecules:
        mol_name = mol.get("name")
        pdb_in_temp = temp_dir / f"{mol_name}.pdb"
        mol["pdb_file"] = str(pdb_in_temp)
    
    runner = PackmolRunner(
        packmol_path=packmol_path,
        temp_dir=str(temp_dir),
    )
    
    if not runner.check_installation():
        result["errors"].append(f"Packmol未安装或路径错误: {packmol_path}")
        logger.error(f"Packmol未安装: {packmol_path}")
        return result
    
    current_box_size = box_size.copy()
    output_pdb_path = str(temp_dir / "packed_system.pdb")
    
    for attempt in range(1, max_retries + 1):
        logger.info(f"Packmol执行尝试 {attempt}/{max_retries}")
        
        attempt_result = {
            "attempt_number": attempt,
            "box_size": current_box_size.copy(),
            "success": False,
        }
        
        input_script_path = runner.generate_packmol_input_script(
            molecules=molecules,
            box_size=current_box_size,
            output_pdb_path=output_pdb_path,
        )
        
        execution_result = runner.execute_packmol(
            input_file=input_script_path,
            working_dir=str(temp_dir),
            timeout=timeout,
        )
        
        attempt_result["execution"] = execution_result
        
        if execution_result["success"]:
            validation_result = runner.validate_pdb_output(output_pdb_path)
            attempt_result["validation"] = validation_result
            
            if validation_result["valid"]:
                attempt_result["success"] = True
                
                move_result = runner.move_outputs_to_inputs(
                    input_script_path=input_script_path,
                    output_pdb_path=output_pdb_path,
                    target_inputs_dir=str(inputs_dir),
                )
                
                if move_result["success"]:
                    result["output_files"] = move_result["moved_files"]
                    result["success"] = True
                    result["final_box_size"] = current_box_size.copy()
                    
                    log_path = str(job_path / "packmol_execution.log")
                    runner.save_execution_log(log_path)
                    
                    logger.info(f"Packmol堆积成功，尝试次数: {attempt}")
                else:
                    attempt_result["move_errors"] = move_result["errors"]
                    logger.error(f"文件移动失败: {move_result['errors']}")
                
                break
            else:
                attempt_result["validation_errors"] = validation_result["errors"]
                logger.warning(f"PDB验证失败: {validation_result['errors']}")
        
        result["attempts"].append(attempt_result)
        
        if attempt < max_retries:
            logger.info(f"增大盒子尺寸重试，放大系数: {PACKMOL_BOX_SCALE_FACTOR}")
            current_box_size["x"] *= PACKMOL_BOX_SCALE_FACTOR
            current_box_size["y"] *= PACKMOL_BOX_SCALE_FACTOR
            current_box_size["z"] *= PACKMOL_BOX_SCALE_FACTOR
    
    cleanup_result = runner.cleanup_temp_files(
        keep_input_script=False,
        keep_output_pdb=False,
    )
    
    result["cleanup"] = cleanup_result
    
    if not result["success"]:
        result["errors"].append(f"Packmol堆积失败，尝试{max_retries}次后仍未成功")
        logger.error(f"Packmol堆积失败，尝试{max_retries}次后仍未成功")
    
    logger.info(f"Packmol堆积流程完成，状态: {'成功' if result['success'] else '失败'}")
    
    return result


def generate_packmol_input(
    molecules: List[Dict[str, Any]],
    box_size: Dict[str, float],
    output_path: str,
    seed: int = None,
) -> str:
    """生成Packmol输入文件的便捷函数
    
    快速生成Packmol输入脚本，无需创建PackmolRunner实例。
    适用于只需要生成输入脚本而不执行的场景。
    
    Args:
        molecules (List[Dict[str, Any]]): 分子配置列表。
            每个分子字典应包含name、count、pdb_file字段。
        
        box_size (Dict[str, float]): 盒子尺寸。
            包含x、y、z三个维度。
        
        output_path (str): 输出PDB文件的路径。
        
        seed (int, optional): 随机种子。
            用于结果可重现性。
    
    Returns:
        str: 生成的输入脚本文件路径。
    
    Note:
        - tolerance固定为2.0埃
        - 输入脚本保存在temp/packmol_temp/目录下
    
    Example:
        >>> input_file = generate_packmol_input(
        ...     molecules=[{"name": "EC", "count": 50, "pdb_file": "EC.pdb"}],
        ...     box_size={"x": 50.0, "y": 50.0, "z": 50.0},
        ...     output_path="packed_system.pdb"
        ... )
    """
    runner = PackmolRunner()
    return runner.generate_packmol_input_script(molecules, box_size, output_path, seed)


def run_packmol(
    input_file: str,
    working_dir: str = None,
    packmol_path: str = "packmol",
    timeout: int = PACKMOL_DEFAULT_TIMEOUT,
) -> Dict[str, Any]:
    """运行Packmol的便捷函数
    
    快速执行Packmol，无需创建PackmolRunner实例。
    适用于已有输入脚本只需要执行的场景。
    
    Args:
        input_file (str): Packmol输入脚本文件路径。
        
        working_dir (str, optional): 工作目录。
            如果未指定，使用全局临时目录。
        
        packmol_path (str): Packmol可执行文件路径。
            默认为"packmol"。
        
        timeout (int): 超时时间（秒）。
            默认为PACKMOL_DEFAULT_TIMEOUT。
    
    Returns:
        Dict[str, Any]: 执行结果字典。
            包含success、stdout、stderr、return_code等字段。
    
    Example:
        >>> result = run_packmol("packmol.inp", timeout=1800)
        >>> if result["success"]:
        ...     print("执行成功")
    """
    runner = PackmolRunner(packmol_path=packmol_path)
    return runner.execute_packmol(input_file, working_dir, timeout)


def create_pdb_from_molecule(
    molecule_name: str,
    output_dir: str,
    template_dir: str = None,
) -> Optional[str]:
    """从模板创建分子PDB文件
    
    从分子模板库复制指定分子的PDB文件到目标目录。
    用于准备Packmol所需的分子结构文件。
    
    Args:
        molecule_name (str): 分子名称。
            应与模板目录中的子目录名称一致。
        
        output_dir (str): 输出目录路径。
            PDB文件将复制到此目录。
        
        template_dir (str, optional): 模板目录根路径。
            如果未指定，使用默认路径system_templates/molecule_templates/。
    
    Returns:
        Optional[str]: 成功时返回输出PDB文件路径，失败返回None。
    
    Note:
        - 输出文件名为{molecule_name}.pdb
        - 如果模板不存在，返回None并记录警告日志
    
    Example:
        >>> pdb_path = create_pdb_from_molecule(
        ...     molecule_name="EC",
        ...     output_dir="/temp/work",
        ...     template_dir="/templates/molecule_templates"
        ... )
        >>> if pdb_path:
        ...     print(f"PDB文件已创建: {pdb_path}")
    """
    output_dir = Path(output_dir)
    output_dir.mkdir(parents=True, exist_ok=True)
    
    if template_dir:
        template_path = Path(template_dir) / molecule_name / f"{molecule_name}.pdb"
        if template_path.exists():
            output_path = output_dir / f"{molecule_name}.pdb"
            shutil.copy2(template_path, output_path)
            logger.info(f"复制PDB模板: {template_path} -> {output_path}")
            return str(output_path)
    
    logger.warning(f"未找到分子 {molecule_name} 的PDB模板")
    return None