#!/usr/bin/env python3
"""
建模入口脚本

提供命令行接口执行分子建模计算

Usage:
    python run_modeling.py --job-id <job_id> --job-dir <job_dir> --formula-file <formula_file>
    python run_modeling.py --job-id <job_id> --job-dir <job_dir> --formula-file <formula_file> --mode packmol
"""

import argparse
import json
import logging
import shutil
import sys
import os
from pathlib import Path
from typing import Dict, Any, List
from datetime import datetime

# 支持直接运行和作为模块运行
if __name__ == "__main__" and __package__ is None:
    # 直接运行时，添加父目录到sys.path
    script_dir = Path(__file__).parent
    sys.path.insert(0, str(script_dir.parent))
    import modeling
    from utils.file_utils import (
        read_formula_json,
        write_result_json,
        ensure_directory,
    )
    from config.config import Config, config
    from utils.packmol_utils import (
        run_packmol_packing,
        PackmolRunner,
        PACKMOL_TOLERANCE_FIXED,
        PACKMOL_DEFAULT_TIMEOUT,
        PACKMOL_MAX_RETRY_COUNT,
    )
    from utils.moltemplate_utils import (
        run_moltemplate_system_generation,
        run_moltemplate_system_generation_simple,
        run_moltemplate_execution,
    )
    from stages.stage4_lammps_execution import (
        execute_lammps_stages,
        collect_property_outputs,
    )
    from stages.stage5_post_processing import run_post_processing
else:
    # 作为模块运行时，使用相对导入
    from .modeling import (
        calculate_molecule_counts,
        calculate_box_size,
        validate_electrical_neutrality,
        adjust_ion_counts,
        generate_molecule_summary,
    )
    from .utils.file_utils import (
        read_formula_json,
        write_result_json,
        ensure_directory,
    )
    from .config.config import Config, config
    from .utils.packmol_utils import (
        run_packmol_packing,
        PackmolRunner,
        PACKMOL_TOLERANCE_FIXED,
        PACKMOL_DEFAULT_TIMEOUT,
        PACKMOL_MAX_RETRY_COUNT,
    )
    from .utils.moltemplate_utils import (
        run_moltemplate_system_generation,
        run_moltemplate_system_generation_simple,
        run_moltemplate_execution,
    )
    from .stages.stage4_lammps_execution import (
        execute_lammps_stages,
        collect_property_outputs,
    )
    from .stages.stage5_post_processing import run_post_processing


def setup_logging(log_level: str = "INFO", log_file: str = None) -> None:
    """配置日志
    
    Args:
        log_level: 日志级别
        log_file: 日志文件路径
    """
    log_format = "%(asctime)s - %(name)s - %(levelname)s - %(message)s"
    
    handlers = [logging.StreamHandler(sys.stdout)]
    
    if log_file:
        log_path = Path(log_file)
        log_path.parent.mkdir(parents=True, exist_ok=True)
        handlers.append(logging.FileHandler(log_file, encoding="utf-8"))
    
    logging.basicConfig(
        level=getattr(logging, log_level.upper(), logging.INFO),
        format=log_format,
        handlers=handlers,
    )


def normalize_formula_data(formula_data: Dict[str, Any]) -> Dict[str, Any]:
    """将Java端FormulaRequest的camelCase字段名转换为Python端期望的snake_case格式

    Java端使用Jackson序列化FormulaRequest，字段名为camelCase格式
    （如solventInfo、moleFraction、saltInfo），而Python端的
    calculate_molecule_counts()等函数期望snake_case格式
    （如solvent_info、mole_fraction、salt_info）。
    此函数执行必要的字段名转换，确保Java→Python数据格式兼容。

    Args:
        formula_data: Java端序列化的配方数据（camelCase字段名）

    Returns:
        转换后的配方数据（snake_case字段名），兼容Python端计算函数
    """
    import copy
    data = copy.deepcopy(formula_data)

    # 转换溶剂信息：solventInfo → solvent_info，moleFraction → mole_fraction
    if "solventInfo" in data and "solvent_info" not in data:
        data["solvent_info"] = data.pop("solventInfo")
    for solvent in data.get("solvent_info", []):
        if "moleFraction" in solvent and "mole_fraction" not in solvent:
            solvent["mole_fraction"] = solvent.pop("moleFraction")

    # 转换盐信息：saltInfo → salt_info
    if "saltInfo" in data and "salt_info" not in data:
        data["salt_info"] = data.pop("saltInfo")

    # 转换盐信息中的cation/anion：Java端为字符串，Python端期望字典
    # Java端SaltInfo.cation = "Li"，Python端期望 salt_info.cation = {"name": "Li", ...}
    salt_info = data.get("salt_info", {})
    if salt_info:
        cation = salt_info.get("cation")
        if isinstance(cation, str):
            salt_info["cation"] = {"name": cation}
        anion = salt_info.get("anion")
        if isinstance(anion, str):
            salt_info["anion"] = {"name": anion}

    # 转换添加剂信息：additiveInfo → additive_info
    if "additiveInfo" in data and "additive_info" not in data:
        additive_info = data.pop("additiveInfo")
        # Java端additiveInfo可能为null，此时不设置additive_info
        if additive_info is not None:
            data["additive_info"] = additive_info
    for additive in data.get("additive_info", []) or []:
        if "moleFraction" in additive and "mole_fraction" not in additive:
            additive["mole_fraction"] = additive.pop("moleFraction")

    # 转换盒子尺寸：boxSize → box_size（保留原始boxSize以兼容）
    if "boxSize" in data and "box_size" not in data:
        data["box_size"] = data.pop("boxSize")

    return data


def print_json_result(result_data: Any, mode: str) -> None:
    """输出JSON标记包裹的结果到stdout，供Java端JsonOutputParser解析

    Java端通过 ===JSON_RESULT=== / ===END_JSON=== 标记提取JSON数据，
    不同模式需要输出不同格式的JSON以匹配Java端的数据模型。

    Args:
        result_data: 完整的结果字典
        mode: 当前计算模式，用于决定输出格式
    """
    # 根据模式提取Java端所需的JSON格式
    if mode == "molecule-count":
        # Java端解析为 List<MoleculeCountResult>，需要name/count/charge字段
        # calculate_molecule_counts()返回的molecules是字典列表：
        # [{"name":"EC","formula":"C3H4O3","molecular_weight":88.06,"charge":0,"count":20}, ...]
        molecules_raw = result_data.get("molecules", [])
        molecules_list = []
        if isinstance(molecules_raw, list):
            # molecules是字典列表，直接提取name/count/charge字段
            for mol_info in molecules_raw:
                molecules_list.append({
                    "name": mol_info.get("name", ""),
                    "count": mol_info.get("count", 0),
                    "charge": float(mol_info.get("charge", 0)),
                })
        elif isinstance(molecules_raw, dict):
            # 兼容字典格式（key为分子名，value为分子信息）
            for mol_name, mol_info in molecules_raw.items():
                molecules_list.append({
                    "name": mol_name,
                    "count": mol_info.get("count", 0) if isinstance(mol_info, dict) else 0,
                    "charge": float(mol_info.get("charge", 0)) if isinstance(mol_info, dict) else 0.0,
                })
        json_output = molecules_list
    elif mode == "box-size":
        # Java端解析为 BoxSizeResult，需要x/y/z/volume字段
        json_output = {
            "x": result_data.get("x", 0.0),
            "y": result_data.get("y", 0.0),
            "z": result_data.get("z", 0.0),
            "volume": result_data.get("volume", 0.0),
        }
    else:
        # 其他模式输出完整结果字典
        json_output = result_data

    print("===JSON_RESULT===")
    print(json.dumps(json_output, ensure_ascii=False))
    print("===END_JSON===")


def parse_arguments() -> argparse.Namespace:
    """解析命令行参数
    
    Returns:
        解析后的参数对象
    """
    parser = argparse.ArgumentParser(
        description="电解液MD建模计算脚本",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
    python run_modeling.py --job-id 123 --job-dir /path/to/job --formula-file /path/to/formula.json
    python run_modeling.py --job-id 123 --job-dir /path/to/job --formula-file /path/to/formula.json --mode box-size
    python run_modeling.py --job-id 123 --job-dir /path/to/job --formula-file /path/to/formula.json --mode packmol
    python run_modeling.py --job-id 123 --job-dir /path/to/job --formula-file /path/to/formula.json --mode lammps-execution
    python run_modeling.py --job-id 123 --job-dir /path/to/job --formula-file /path/to/formula.json --mode post-processing
    python run_modeling.py --job-id 123 --job-dir /path/to/job --formula-file /path/to/formula.json --mode full
        """,
    )
    
    parser.add_argument(
        "--job-id",
        type=str,
        required=True,
        help="任务ID",
    )

    # 用户ID，与任务ID关联，用于构建用户隔离的文件路径
    parser.add_argument(
        "--user-id",
        type=str,
        default=None,
        help="用户ID",
    )
    
    parser.add_argument(
        "--job-dir",
        type=str,
        required=False,
        default=None,
        help="任务工作目录（file-organize模式不需要）",
    )
    
    parser.add_argument(
        "--formula-file",
        type=str,
        required=False,
        default=None,
        help="配方JSON文件路径（file-organize、lammps-execution、post-processing模式不需要）",
    )
    
    parser.add_argument(
        "--mode",
        type=str,
        choices=["molecule-count", "box-size", "packmol", "moltemplate-system", "moltemplate-execution", "lammps-execution", "post-processing", "file-organize", "full"],
        default="full",
        help="计算模式: molecule-count(仅计算分子数), box-size(仅计算盒子尺寸), packmol(仅执行Packmol堆积), moltemplate-system(仅生成system.lt), moltemplate-execution(仅执行Moltemplate命令和文件整理), lammps-execution(收集LAMMPS输出文件到raw_outputs目录，不执行LAMMPS，实际LAMMPS执行由Java端MDExecutorService通过docker-java完成), post-processing(执行后处理分析), file-organize(整理Moltemplate输出文件到目标目录), full(完整建模含Packmol、system.lt生成、Moltemplate执行和文件整理)",
    )
    
    parser.add_argument(
        "--density",
        type=float,
        default=1.2,
        help="密度 (g/cm³)，默认1.2",
    )
    
    parser.add_argument(
        "--log-level",
        type=str,
        choices=["DEBUG", "INFO", "WARNING", "ERROR"],
        default="INFO",
        help="日志级别，默认INFO",
    )
    
    parser.add_argument(
        "--output-file",
        type=str,
        default=None,
        help="输出文件路径，默认为job-dir/modeling_result.json",
    )

    parser.add_argument(
        "--result-json",
        type=str,
        default=None,
        help="JSON结果文件路径（与--output-file分离），用于moltemplate-system等模式避免与system.lt路径冲突",
    )
    
    parser.add_argument(
        "--template-dir",
        type=str,
        default=None,
        help="分子模板目录路径",
    )
    
    parser.add_argument(
        "--packmol-path",
        type=str,
        default="packmol",
        help="Packmol可执行文件路径，默认packmol",
    )
    
    parser.add_argument(
        "--packmol-timeout",
        type=int,
        default=PACKMOL_DEFAULT_TIMEOUT,
        help=f"Packmol执行超时时间(秒)，默认{PACKMOL_DEFAULT_TIMEOUT}",
    )
    
    parser.add_argument(
        "--packmol-retries",
        type=int,
        default=PACKMOL_MAX_RETRY_COUNT,
        help=f"Packmol失败重试次数，默认{PACKMOL_MAX_RETRY_COUNT}",
    )
    
    parser.add_argument(
        "--packed-pdb-file",
        type=str,
        default=None,
        help="Packmol生成的PDB文件路径（用于moltemplate-system模式）",
    )
    
    parser.add_argument(
        "--template-library-path",
        type=str,
        default=None,
        help="分子模板库路径（system_templates/molecule_templates）",
    )
    
    parser.add_argument(
        "--forcefield-type",
        type=str,
        default="oplsaa",
        help="力场类型，默认oplsaa",
    )
    
    parser.add_argument(
        "--forcefield-library-path",
        type=str,
        default=None,
        help="力场文件库路径（可选）",
    )
    
    parser.add_argument(
        "--system-lt-file",
        type=str,
        default=None,
        help="system.lt文件路径（用于moltemplate-execution模式）",
    )
    
    parser.add_argument(
        "--moltemplate-path",
        type=str,
        default="moltemplate.sh",
        help="Moltemplate脚本路径，默认moltemplate.sh",
    )
    
    parser.add_argument(
        "--moltemplate-timeout",
        type=int,
        default=3600,
        help="Moltemplate执行超时时间(秒)，默认3600",
    )

    parser.add_argument(
        "--pdb-file",
        type=str,
        default=None,
        help="PDB坐标文件路径（用于moltemplate-execution模式，传递-pdb参数给Moltemplate）",
    )

    # LAMMPS文件收集模式参数
    parser.add_argument(
        "--work-dir",
        type=str,
        default=None,
        help="LAMMPS工作目录（inputs目录，用于lammps-execution文件收集模式）",
    )

    parser.add_argument(
        "--output-dir",
        type=str,
        default=None,
        help="LAMMPS输出目录（raw_outputs目录，用于lammps-execution文件收集模式）",
    )

    parser.add_argument(
        "--target-properties",
        type=str,
        default="density",
        help="目标性质列表，逗号分隔（用于lammps-execution文件收集模式），默认density",
    )

    parser.add_argument(
        "--use-gpu",
        action="store_true",
        default=False,
        help="是否使用GPU加速（保留参数，实际LAMMPS执行由Java端MDExecutorService控制）",
    )

    # 后处理模式参数
    parser.add_argument(
        "--temperature",
        type=float,
        default=300.0,
        help="模拟温度(K)",
    )

    parser.add_argument(
        "--time-step-fs",
        type=float,
        default=1.0,
        help="时间步长(fs)",
    )

    parser.add_argument(
        "--box-size",
        type=str,
        default=None,
        help='盒子尺寸JSON，如 {"lx":50,"ly":50,"lz":50}',
    )

    # 文件整理模式参数
    parser.add_argument(
        "--source-dir",
        type=str,
        default=None,
        help="源目录路径（Moltemplate输出目录，用于file-organize模式）",
    )

    parser.add_argument(
        "--target-dir",
        type=str,
        default=None,
        help="目标目录路径（LAMMPS输入文件目录，用于file-organize模式）",
    )

    return parser.parse_args()


def run_molecule_count_mode(formula_data: Dict[str, Any]) -> Dict[str, Any]:
    """运行分子数量计算模式
    
    Args:
        formula_data: 配方数据
        
    Returns:
        计算结果
    """
    logging.info("执行分子数量计算")
    
    result = calculate_molecule_counts(formula_data)
    
    summary = generate_molecule_summary(result["molecules"])
    result["summary"] = summary
    
    return result


def run_box_size_mode(formula_data: Dict[str, Any], density: float) -> Dict[str, Any]:
    """运行盒子尺寸计算模式
    
    Args:
        formula_data: 配方数据
        density: 密度
        
    Returns:
        计算结果
    """
    logging.info("执行盒子尺寸计算")
    
    if "molecules" not in formula_data:
        molecule_result = calculate_molecule_counts(formula_data)
        formula_data["molecules"] = molecule_result["molecules"]
    
    result = calculate_box_size(formula_data, density)
    
    return result


def run_packmol_mode(
    formula_data: Dict[str, Any],
    job_dir: str,
    template_dir: str = None,
    packmol_path: str = "packmol",
    timeout: int = PACKMOL_DEFAULT_TIMEOUT,
    max_retries: int = PACKMOL_MAX_RETRY_COUNT,
) -> Dict[str, Any]:
    """运行Packmol堆积模式
    
    Args:
        formula_data: 配方数据
        job_dir: 任务目录
        template_dir: 模板目录
        packmol_path: Packmol路径
        timeout: 超时时间
        max_retries: 最大重试次数
        
    Returns:
        计算结果
    """
    logging.info("执行Packmol分子堆积")
    
    if "molecules" not in formula_data:
        molecule_result = calculate_molecule_counts(formula_data)
        formula_data["molecules"] = molecule_result["molecules"]
    
    if "box_size" not in formula_data or not formula_data["box_size"]:
        box_result = calculate_box_size(formula_data, formula_data.get("density", 1.2))
        formula_data["box_size"] = box_result
    
    # 将分子数据转换为Packmol所需的列表格式
    # calculate_molecule_counts()返回的molecules是列表格式：[{"name":"EC","count":303,...}, ...]
    # 需要统一处理列表和字典两种格式
    molecules = []
    molecules_raw = formula_data.get("molecules", [])
    if isinstance(molecules_raw, list):
        # 列表格式：[{"name":"EC","count":303,"charge":0,...}, ...]
        for mol_info in molecules_raw:
            mol_name = mol_info.get("name", "")
            molecules.append({
                "name": mol_name,
                "count": mol_info.get("count", 1),
                "pdb_file": mol_info.get("pdb_file", f"{mol_name}.pdb"),
            })
    elif isinstance(molecules_raw, dict):
        # 字典格式：{"EC": {"count": 303, ...}, ...}
        for mol_name, mol_info in molecules_raw.items():
            molecules.append({
                "name": mol_name,
                "count": mol_info.get("count", 1) if isinstance(mol_info, dict) else 1,
                "pdb_file": mol_info.get("pdb_file", f"{mol_name}.pdb") if isinstance(mol_info, dict) else f"{mol_name}.pdb",
            })

    # 关键修复：按分子名称字母序排序，确保Packmol输出的PDB原子顺序与
    # system.lt中分子实例定义顺序（generate_molecule_instance_definitions
    # 使用sorted(molecule_counts.keys())）保持一致。
    # 否则Moltemplate的-pdb坐标映射会错位，导致键长异常（5-11Å），
    # 进而引发LAMMPS模拟能量爆炸。
    molecules.sort(key=lambda m: m["name"])

    box_size = formula_data.get("box_size", {})
    if isinstance(box_size, dict):
        box_dimensions = {
            "x": box_size.get("x", 50.0),
            "y": box_size.get("y", 50.0),
            "z": box_size.get("z", 50.0),
        }
    else:
        box_dimensions = {"x": 50.0, "y": 50.0, "z": 50.0}
    
    packmol_result = run_packmol_packing(
        molecules=molecules,
        box_size=box_dimensions,
        job_dir=job_dir,
        template_dir=template_dir,
        packmol_path=packmol_path,
        timeout=timeout,
        max_retries=max_retries,
    )
    
    result = {
        "success": packmol_result.get("success", False),
        "molecules": molecules,
        "box_size": box_dimensions,
        "final_box_size": packmol_result.get("final_box_size", box_dimensions),
        "output_files": packmol_result.get("output_files", []),
        "attempts": len(packmol_result.get("attempts", [])),
        "tolerance": PACKMOL_TOLERANCE_FIXED,
        "errors": packmol_result.get("errors", []),
    }
    
    return result


def run_moltemplate_system_mode(
    formula_data: Dict[str, Any],
    packed_pdb_file: str,
    job_dir: str,
    template_library_path: str,
    forcefield_type: str = "oplsaa",
    forcefield_library_path: str = None,
) -> Dict[str, Any]:
    """运行Moltemplate系统文件生成模式
    
    Args:
        formula_data: 配方数据
        packed_pdb_file: Packmol生成的PDB文件路径
        job_dir: 任务目录
        template_library_path: 分子模板库路径
        forcefield_type: 力场类型
        forcefield_library_path: 力场文件库路径
        
    Returns:
        计算结果，包含：
            - success: 是否成功
            - system_lt_path: system.lt文件路径
            - execution_summary: 执行摘要
            - statistics: 统计信息
            - error: 错误信息（如果失败）
    """
    logging.info("执行Moltemplate系统文件生成")
    
    # 准备配方数据
    if "molecules" not in formula_data:
        molecule_result = calculate_molecule_counts(formula_data)
        formula_data["molecules"] = molecule_result["molecules"]
    
    if "box_size" not in formula_data or not formula_data["box_size"]:
        box_result = calculate_box_size(formula_data, formula_data.get("density", 1.2))
        formula_data["box_size"] = box_result
    
    # 转换分子数据格式（统一处理列表和字典两种格式）
    molecules_list = []
    molecules_raw = formula_data.get("molecules", [])
    if isinstance(molecules_raw, list):
        for mol_info in molecules_raw:
            molecules_list.append({
                "name": mol_info.get("name", ""),
                "count": mol_info.get("count", 1),
            })
    elif isinstance(molecules_raw, dict):
        for mol_name, mol_info in molecules_raw.items():
            molecules_list.append({
                "name": mol_name,
                "count": mol_info.get("count", 1) if isinstance(mol_info, dict) else 1,
            })
    
    formula_data_for_moltemplate = {
        "molecules": molecules_list,
        "box_size": formula_data.get("box_size", {}),
    }
    
    # 调用moltemplate系统生成函数
    moltemplate_result = run_moltemplate_system_generation(
        packed_pdb_path=packed_pdb_file,
        formula_data=formula_data_for_moltemplate,
        work_dir_path=job_dir,
        template_library_path=template_library_path,
        forcefield_type=forcefield_type,
        forcefield_library_path=forcefield_library_path,
    )
    
    result = {
        "success": moltemplate_result.get("success", False),
        "system_lt_path": moltemplate_result.get("system_lt_path"),
        "execution_summary": moltemplate_result.get("execution_summary", {}),
        "statistics": moltemplate_result.get("statistics", {}),
        "step_results": moltemplate_result.get("step_results", []),
        "error": moltemplate_result.get("error"),
        "failed_step": moltemplate_result.get("failed_step"),
    }
    
    return result


def run_moltemplate_execution_mode(
    system_lt_file: str,
    job_dir: str,
    moltemplate_path: str = "moltemplate.sh",
    timeout: int = 3600,
    input_dir: str = "inputs",
    pdb_file: str = None,
) -> Dict[str, Any]:
    """运行Moltemplate执行模式（步骤6和步骤7）

    执行Moltemplate命令将system.lt转换为LAMMPS输入文件，
    并将生成的文件整理到正确的目录结构。

    Args:
        system_lt_file: system.lt文件路径
        job_dir: 任务目录
        moltemplate_path: Moltemplate脚本路径
        timeout: Moltemplate执行超时时间
        input_dir: 输入文件目录名称
        pdb_file: PDB坐标文件路径（可选，用于传递-pdb参数给Moltemplate）

    Returns:
        计算结果，包含：
            - success: 是否成功
            - step6_result: 步骤6执行结果（Moltemplate命令执行）
            - step7_result: 步骤7执行结果（文件整理输出）
            - statistics: 统计信息（总耗时、文件数量等）
            - error: 错误信息（如果失败）
    """
    logging.info("执行Moltemplate命令和文件整理（步骤6-7）")

    # 调用run_moltemplate_execution函数
    execution_result = run_moltemplate_execution(
        system_lt_path=system_lt_file,
        work_dir=job_dir,
        moltemplate_path=moltemplate_path,
        timeout=timeout,
        input_dir=input_dir,
        pdb_file=pdb_file,
    )
    
    result = {
        "success": execution_result.get("success", False),
        "step6_result": execution_result.get("step6_result", {}),
        "step7_result": execution_result.get("step7_result", {}),
        "statistics": execution_result.get("statistics", {}),
        "error": execution_result.get("error"),
    }
    
    return result


def run_file_organize_mode(
    source_dir: str,
    target_dir: str,
) -> Dict[str, Any]:
    """运行文件整理模式

    将Moltemplate输出的LAMMPS输入文件从源目录复制到目标目录。
    复制的文件包括：system.data、system.in.init、system.in.settings，
    以及其他 .data 和 .in.* 文件。

    Args:
        source_dir: 源目录路径（Moltemplate输出目录）
        target_dir: 目标目录路径（LAMMPS输入文件目录）

    Returns:
        计算结果，包含：
            - success: 是否成功
            - output_files: 已复制的文件列表
            - error: 错误信息（如果失败）
    """
    logging.info("执行文件整理模式")

    source_path = Path(source_dir)
    target_path = Path(target_dir)

    # 校验源目录存在性
    if not source_path.exists():
        error_msg = f"源目录不存在: {source_dir}"
        logging.error(error_msg)
        return {"success": False, "output_files": [], "error": error_msg}

    if not source_path.is_dir():
        error_msg = f"源路径不是目录: {source_dir}"
        logging.error(error_msg)
        return {"success": False, "output_files": [], "error": error_msg}

    # 确保目标目录存在
    target_path.mkdir(parents=True, exist_ok=True)

    # 需要复制的固定文件名
    fixed_files = ["system.data", "system.in.init", "system.in.settings"]

    # 收集需要复制的文件
    files_to_copy: List[str] = []

    # 添加固定文件（如果存在）
    for filename in fixed_files:
        src_file = source_path / filename
        if src_file.exists() and src_file.is_file():
            files_to_copy.append(filename)

    # 扫描源目录中的其他 .data 文件和 .in.* 文件
    for item in source_path.iterdir():
        if item.is_file() and item.name not in files_to_copy:
            # 匹配 .data 文件（排除已有的固定文件）
            if item.suffix == ".data":
                files_to_copy.append(item.name)
            # 匹配 .in.* 模式的文件（如 in.minimization, in.equilibrium, in.production）
            elif item.name.startswith("in.") or item.name.startswith("system.in."):
                files_to_copy.append(item.name)

    # 执行文件复制
    copied_files: List[str] = []
    copy_errors: List[str] = []

    for filename in files_to_copy:
        src_file = source_path / filename
        dst_file = target_path / filename
        try:
            shutil.copy2(str(src_file), str(dst_file))
            copied_files.append(filename)
            logging.info(f"已复制文件: {filename} -> {dst_file}")
        except OSError as e:
            error_msg = f"复制文件失败 {filename}: {e}"
            logging.error(error_msg)
            copy_errors.append(error_msg)

    # 判断整体是否成功
    if copy_errors:
        return {
            "success": False,
            "output_files": copied_files,
            "error": "; ".join(copy_errors),
        }

    return {
        "success": True,
        "output_files": copied_files,
    }


def run_full_mode(
    formula_data: Dict[str, Any],
    density: float,
    job_dir: str,
    template_dir: str = None,
    template_library_path: str = None,
    packmol_path: str = "packmol",
    timeout: int = PACKMOL_DEFAULT_TIMEOUT,
    max_retries: int = PACKMOL_MAX_RETRY_COUNT,
    forcefield_type: str = "oplsaa",
    forcefield_library_path: str = None,
    moltemplate_path: str = "moltemplate.sh",
    moltemplate_timeout: int = 3600,
    pdb_file: str = None,
) -> Dict[str, Any]:
    """运行完整计算模式（含Packmol、system.lt生成、Moltemplate执行和文件整理）

    执行完整的Moltemplate自动化建模流程（步骤1-7）：
    - 步骤1：接收配方参数
    - 步骤2：计算分子数量
    - 步骤3：调取分子模板
    - 步骤4：Packmol堆积
    - 步骤5：生成system.lt
    - 步骤6：执行Moltemplate
    - 步骤7：文件整理输出

    Args:
        formula_data: 配方数据
        density: 密度
        job_dir: 任务目录
        template_dir: 模板目录（用于Packmol）
        template_library_path: 分子模板库路径（用于Moltemplate）
        packmol_path: Packmol路径
        timeout: Packmol超时时间
        max_retries: Packmol最大重试次数
        forcefield_type: 力场类型
        forcefield_library_path: 力场文件库路径
        moltemplate_path: Moltemplate脚本路径
        moltemplate_timeout: Moltemplate执行超时时间
        pdb_file: PDB坐标文件路径（可选，用于传递-pdb参数给Moltemplate）

    Returns:
        计算结果，包含：
            - molecules: 分子数据
            - box_size: 盒子尺寸
            - summary: 分子摘要
            - packmol: Packmol执行结果
            - moltemplate_system: Moltemplate system.lt生成结果
            - moltemplate_execution: Moltemplate执行和文件整理结果
            - density: 密度
    """
    logging.info("执行完整建模计算（步骤1-7：含Packmol堆积、system.lt生成、Moltemplate执行和文件整理）")
    
    # 步骤1: 分子数量计算
    molecule_result = calculate_molecule_counts(formula_data)
    formula_data["molecules"] = molecule_result.get("molecules", {})
    
    # 步骤2: 盒子尺寸计算
    box_result = calculate_box_size(molecule_result, density)
    formula_data["box_size"] = box_result
    
    # 步骤3: Packmol堆积
    # 将分子数据转换为Packmol所需的列表格式
    # calculate_molecule_counts()返回的molecules是列表格式：[{"name":"EC","count":303,...}, ...]
    # 需要统一处理列表和字典两种格式
    molecules = []
    molecules_raw = formula_data.get("molecules", [])
    if isinstance(molecules_raw, list):
        # 列表格式：[{"name":"EC","count":303,"charge":0,...}, ...]
        for mol_info in molecules_raw:
            mol_name = mol_info.get("name", "")
            molecules.append({
                "name": mol_name,
                "count": mol_info.get("count", 1),
                "pdb_file": mol_info.get("pdb_file", f"{mol_name}.pdb"),
            })
    elif isinstance(molecules_raw, dict):
        # 字典格式：{"EC": {"count": 303, ...}, ...}
        for mol_name, mol_info in molecules_raw.items():
            molecules.append({
                "name": mol_name,
                "count": mol_info.get("count", 1) if isinstance(mol_info, dict) else 1,
                "pdb_file": mol_info.get("pdb_file", f"{mol_name}.pdb") if isinstance(mol_info, dict) else f"{mol_name}.pdb",
            })

    # 关键修复：按分子名称字母序排序，确保Packmol输出的PDB原子顺序与
    # system.lt中分子实例定义顺序（generate_molecule_instance_definitions
    # 使用sorted(molecule_counts.keys())）保持一致。
    # 否则Moltemplate的-pdb坐标映射会错位，导致键长异常（5-11Å），
    # 进而引发LAMMPS模拟能量爆炸。
    molecules.sort(key=lambda m: m["name"])

    box_dimensions = {
        "x": box_result.get("x", 50.0),
        "y": box_result.get("y", 50.0),
        "z": box_result.get("z", 50.0),
    }
    
    packmol_result = run_packmol_packing(
        molecules=molecules,
        box_size=box_dimensions,
        job_dir=job_dir,
        template_dir=template_dir,
        packmol_path=packmol_path,
        timeout=timeout,
        max_retries=max_retries,
    )
    
    # 步骤4: system.lt生成（如果Packmol成功）
    moltemplate_system_result = None
    moltemplate_execution_result = None
    
    if packmol_result.get("success", False):
        # 获取Packmol输出的PDB文件路径
        output_files = packmol_result.get("output_files", [])
        packed_pdb_file = None
        for f in output_files:
            if f.endswith(".pdb") and "packed" in f.lower():
                packed_pdb_file = f
                break
        
        if packed_pdb_file and template_library_path:
            # 转换分子数据格式（统一处理列表和字典两种格式）
            molecules_list = []
            molecules_raw_mt = formula_data.get("molecules", [])
            if isinstance(molecules_raw_mt, list):
                for mol_info in molecules_raw_mt:
                    molecules_list.append({
                        "name": mol_info.get("name", ""),
                        "count": mol_info.get("count", 1),
                    })
            elif isinstance(molecules_raw_mt, dict):
                for mol_name, mol_info in molecules_raw_mt.items():
                    molecules_list.append({
                        "name": mol_name,
                        "count": mol_info.get("count", 1) if isinstance(mol_info, dict) else 1,
                    })
            
            formula_data_for_moltemplate = {
                "molecules": molecules_list,
                "box_size": packmol_result.get("final_box_size", box_dimensions),
            }
            
            # 步骤4：调用moltemplate系统生成函数
            moltemplate_system_result = run_moltemplate_system_generation(
                packed_pdb_path=packed_pdb_file,
                formula_data=formula_data_for_moltemplate,
                work_dir_path=job_dir,
                template_library_path=template_library_path,
                forcefield_type=forcefield_type,
                forcefield_library_path=forcefield_library_path,
            )
            
            # 步骤5-6：执行Moltemplate命令和文件整理（如果system.lt生成成功）
            if moltemplate_system_result.get("success", False):
                system_lt_path = moltemplate_system_result.get("system_lt_path")
                
                if system_lt_path:
                    logging.info(f"开始执行步骤6-7：Moltemplate命令执行和文件整理")
                    logging.info(f"system.lt路径: {system_lt_path}")
                    
                    # 调用run_moltemplate_execution函数执行步骤6和步骤7
                    moltemplate_execution_result = run_moltemplate_execution(
                        system_lt_path=system_lt_path,
                        work_dir=job_dir,
                        moltemplate_path=moltemplate_path,
                        timeout=moltemplate_timeout,
                        input_dir="inputs",
                        pdb_file=pdb_file,
                    )
                    
                    if moltemplate_execution_result.get("success", False):
                        logging.info("步骤6-7执行成功")
                    else:
                        logging.error(f"步骤6-7执行失败: {moltemplate_execution_result.get('error')}")
                else:
                    logging.warning("未找到system.lt文件路径，跳过步骤6-7")
                    moltemplate_execution_result = {
                        "success": False,
                        "error": "缺少system.lt文件路径",
                    }
            else:
                logging.warning("system.lt生成失败，跳过步骤6-7")
                moltemplate_execution_result = {
                    "success": False,
                    "error": "system.lt生成失败",
                }
        else:
            logging.warning("未找到packed PDB文件或模板库路径，跳过system.lt生成和后续步骤")
            moltemplate_system_result = {
                "success": False,
                "error": "缺少packed PDB文件或模板库路径",
            }
            moltemplate_execution_result = {
                "success": False,
                "error": "缺少前置条件",
            }
    else:
        logging.warning("Packmol失败，跳过后续步骤")
        moltemplate_system_result = {
            "success": False,
            "error": "Packmol堆积失败",
        }
        moltemplate_execution_result = {
            "success": False,
            "error": "Packmol堆积失败",
        }
    
    summary = generate_molecule_summary(formula_data.get("molecules", {}))
    
    result = {
        "molecules": molecule_result.get("molecules", {}),
        "box_size": box_result,
        "summary": summary,
        "packmol": {
            "success": packmol_result.get("success", False),
            "final_box_size": packmol_result.get("final_box_size", box_dimensions),
            "output_files": packmol_result.get("output_files", []),
            "attempts": len(packmol_result.get("attempts", [])),
            "tolerance": PACKMOL_TOLERANCE_FIXED,
            "errors": packmol_result.get("errors", []),
        },
        "moltemplate_system": {
            "success": moltemplate_system_result.get("success", False) if moltemplate_system_result else False,
            "system_lt_path": moltemplate_system_result.get("system_lt_path") if moltemplate_system_result else None,
            "execution_summary": moltemplate_system_result.get("execution_summary", {}) if moltemplate_system_result else {},
            "statistics": moltemplate_system_result.get("statistics", {}) if moltemplate_system_result else {},
            "error": moltemplate_system_result.get("error") if moltemplate_system_result else None,
            "failed_step": moltemplate_system_result.get("failed_step") if moltemplate_system_result else None,
        },
        "moltemplate_execution": {
            "success": moltemplate_execution_result.get("success", False) if moltemplate_execution_result else False,
            "step6_result": moltemplate_execution_result.get("step6_result", {}) if moltemplate_execution_result else {},
            "step7_result": moltemplate_execution_result.get("step7_result", {}) if moltemplate_execution_result else {},
            "statistics": moltemplate_execution_result.get("statistics", {}) if moltemplate_execution_result else {},
            "error": moltemplate_execution_result.get("error") if moltemplate_execution_result else None,
        },
        "density": density,
    }
    
    return result


def print_packmol_summary(result: Dict[str, Any]) -> None:
    """打印Packmol执行摘要
    
    Args:
        result: Packmol执行结果
    """
    print("\nPackmol堆积结果摘要:")
    print(f"  状态: {'成功' if result.get('success', False) else '失败'}")
    print(f"  Tolerance: {result.get('tolerance', PACKMOL_TOLERANCE_FIXED)} Å (固定值)")
    
    box_size = result.get("box_size", {})
    final_box = result.get("final_box_size", box_size)
    print(f"  初始盒子尺寸: {box_size.get('x', 0):.2f} x {box_size.get('y', 0):.2f} x {box_size.get('z', 0):.2f} Å")
    print(f"  最终盒子尺寸: {final_box.get('x', 0):.2f} x {final_box.get('y', 0):.2f} x {final_box.get('z', 0):.2f} Å")
    
    print(f"  尝试次数: {result.get('attempts', 0)}")
    
    output_files = result.get("output_files", [])
    if output_files:
        print(f"  输出文件:")
        for f in output_files:
            print(f"    - {f}")
    
    molecules = result.get("molecules", [])
    if molecules:
        print(f"  分子种类: {len(molecules)}")
        total_count = sum(m.get("count", 0) for m in molecules)
        print(f"  总分子数: {total_count}")
    
    errors = result.get("errors", [])
    if errors:
        print(f"  错误信息:")
        for e in errors:
            print(f"    - {e}")


def print_moltemplate_system_summary(result: Dict[str, Any]) -> None:
    """打印Moltemplate system.lt生成摘要
    
    Args:
        result: Moltemplate执行结果，包含：
            - success: 是否成功
            - system_lt_path: system.lt文件路径
            - execution_summary: 执行摘要
            - statistics: 统计信息
            - error: 错误信息（如果失败）
            - failed_step: 失败步骤（如果失败）
    """
    print("\nMoltemplate system.lt生成结果摘要:")
    print(f"  状态: {'成功' if result.get('success', False) else '失败'}")
    
    # 显示system.lt文件路径
    system_lt_path = result.get("system_lt_path")
    if system_lt_path:
        print(f"  system.lt文件路径: {system_lt_path}")
    
    # 显示执行摘要
    execution_summary = result.get("execution_summary", {})
    if execution_summary:
        total_duration = execution_summary.get("total_duration", 0)
        print(f"  执行耗时: {total_duration:.2f} 秒")
        
        step_count = execution_summary.get("total_steps", 0)
        successful_steps = execution_summary.get("successful_steps", 0)
        print(f"  步骤统计: {successful_steps}/{step_count} 成功")
    
    # 显示统计信息
    statistics = result.get("statistics", {})
    if statistics:
        # 分子模板文件列表（可能是dict或list格式）
        template_files = statistics.get("template_files", {})
        if template_files:
            print(f"  分子模板文件列表:")
            if isinstance(template_files, dict):
                for mol_name, template_path in template_files.items():
                    print(f"    - {mol_name}: {template_path}")
            elif isinstance(template_files, list):
                for template_path in template_files:
                    print(f"    - {template_path}")
        
        # 分子实例数量
        molecule_counts = statistics.get("molecule_counts", {})
        if molecule_counts:
            print(f"  分子实例数量:")
            total_instances = 0
            for mol_name, count in molecule_counts.items():
                print(f"    - {mol_name}: {count} 个")
                total_instances += count
            print(f"    总计: {total_instances} 个")
        
        # 总原子数
        total_atoms = statistics.get("total_atoms", 0)
        if total_atoms:
            print(f"  总原子数: {total_atoms}")
        
        # 盒子尺寸
        box_size = statistics.get("box_size", {})
        if box_size:
            x = box_size.get("x", 0)
            y = box_size.get("y", 0)
            z = box_size.get("z", 0)
            print(f"  盒子尺寸: {x:.2f} x {y:.2f} x {z:.2f} Å")
        
        # 力场类型
        forcefield_type = statistics.get("forcefield_type", "oplsaa")
        print(f"  力场类型: {forcefield_type}")
    
    # 显示错误信息
    if not result.get("success", False):
        failed_step = result.get("failed_step")
        if failed_step:
            print(f"  失败步骤: {failed_step}")
        
        error = result.get("error")
        if error:
            print(f"  错误信息: {error}")


def print_moltemplate_execution_summary(result: Dict[str, Any]) -> None:
    """打印Moltemplate执行和文件整理结果摘要
    
    Args:
        result: Moltemplate执行结果，包含：
            - success: 是否成功
            - step6_result: 步骤6执行结果（Moltemplate命令执行）
            - step7_result: 步骤7执行结果（文件整理输出）
            - statistics: 统计信息（总耗时、文件数量等）
            - error: 错误信息（如果失败）
    """
    print("\nMoltemplate执行和文件整理结果摘要:")
    print(f"  状态: {'成功' if result.get('success', False) else '失败'}")
    
    # 显示统计信息
    statistics = result.get("statistics", {})
    if statistics:
        total_duration = statistics.get("total_duration", 0)
        print(f"  总耗时: {total_duration:.2f} 秒")
        
        step6_duration = statistics.get("step6_duration", 0)
        step7_duration = statistics.get("step7_duration", 0)
        print(f"  步骤6耗时: {step6_duration:.2f} 秒")
        print(f"  步骤7耗时: {step7_duration:.2f} 秒")
        
        total_files = statistics.get("total_files", 0)
        print(f"  生成文件数量: {total_files}")
        
        total_size = statistics.get("total_size", 0)
        print(f"  总文件大小: {total_size} 字节")
        
        input_dir = statistics.get("input_dir", "inputs")
        print(f"  输入目录: {input_dir}")
    
    # 显示步骤6执行结果
    step6_result = result.get("step6_result", {})
    if step6_result:
        print("\n  步骤6（Moltemplate命令执行）:")
        print(f"    状态: {'成功' if step6_result.get('success', False) else '失败'}")
        
        output_files = step6_result.get("output_files", [])
        if output_files:
            print(f"    生成文件:")
            for f in output_files:
                print(f"      - {f}")
        
        missing_files = step6_result.get("missing_files", [])
        if missing_files:
            print(f"    缺失文件:")
            for f in missing_files:
                print(f"      - {f}")
        
        if not step6_result.get("success", False):
            error = step6_result.get("error")
            if error:
                print(f"    错误信息: {error}")
    
    # 显示步骤7执行结果
    step7_result = result.get("step7_result", {})
    if step7_result:
        print("\n  步骤7（文件整理输出）:")
        print(f"    状态: {'成功' if step7_result.get('success', False) else '失败'}")
        
        moved_files = step7_result.get("moved_files", [])
        if moved_files:
            print(f"    移动文件:")
            for file_info in moved_files:
                file_name = file_info.get("file_name", "unknown")
                size = file_info.get("size", 0)
                print(f"      - {file_name}: {size} 字节")
        
        missing_files = step7_result.get("missing_files", [])
        if missing_files:
            print(f"    缺失文件:")
            for f in missing_files:
                print(f"      - {f}")
        
        if not step7_result.get("success", False):
            error = step7_result.get("error")
            if error:
                print(f"    错误信息: {error}")
    
    # 显示错误信息
    if not result.get("success", False):
        error = result.get("error")
        if error:
            print(f"\n  总体错误信息: {error}")


def main() -> int:
    """主函数
    
    Returns:
        退出码
    """
    args = parse_arguments()
    
    # 需要job-dir的模式列表（file-organize模式使用source-dir/target-dir代替）
    job_dir_required_modes = [
        "molecule-count", "box-size", "packmol",
        "moltemplate-system", "moltemplate-execution",
        "lammps-execution", "post-processing", "full",
    ]
    if args.mode in job_dir_required_modes and not args.job_dir:
        print(f"错误: {args.mode}模式需要--job-dir参数")
        return 1
    
    # 设置日志（file-organize模式不需要job-dir，使用临时目录）
    if args.job_dir:
        job_dir = Path(args.job_dir)
        job_dir.mkdir(parents=True, exist_ok=True)
        log_file = str(job_dir / "modeling.log")
    else:
        # file-organize模式不需要创建job目录
        job_dir = None
        log_file = None
    
    setup_logging(args.log_level, log_file)
    
    logger = logging.getLogger(__name__)
    logger.info(f"开始建模任务: user_id={args.user_id}, job_id={args.job_id}")
    logger.info(f"工作目录: {job_dir}")
    logger.info(f"配方文件: {args.formula_file}")
    logger.info(f"计算模式: {args.mode}")
    
    # 需要配方文件的模式列表（moltemplate-execution只需要system-lt-file，不需要配方文件）
    formula_required_modes = [
        "molecule-count", "box-size", "packmol",
        "moltemplate-system", "full",
    ]
    if args.mode in formula_required_modes and not args.formula_file:
        logger.error(f"{args.mode}模式需要--formula-file参数")
        print(f"错误: {args.mode}模式需要--formula-file参数")
        return 1
    
    try:
        # file-organize模式不需要读取配方文件
        if args.mode == "file-organize":
            # 检查必要参数
            if not args.source_dir:
                logger.error("file-organize模式需要--source-dir参数")
                print("错误: file-organize模式需要--source-dir参数")
                return 1
            if not args.target_dir:
                logger.error("file-organize模式需要--target-dir参数")
                print("错误: file-organize模式需要--target-dir参数")
                return 1

            result = run_file_organize_mode(
                source_dir=args.source_dir,
                target_dir=args.target_dir,
            )
        # lammps-execution模式：仅收集LAMMPS输出文件，不执行LAMMPS
        # 实际LAMMPS模拟由Java端MDExecutorService通过docker-java在容器中执行
        elif args.mode == "lammps-execution":
            import json as json_module

            # 确定工作目录和输出目录
            lammps_work_dir = args.work_dir if args.work_dir else str(job_dir / "inputs")
            lammps_output_dir = args.output_dir if args.output_dir else str(job_dir / "raw_outputs")

            target_properties = [p.strip() for p in args.target_properties.split(',')]

            logger.info("执行LAMMPS输出文件收集（不执行LAMMPS，LAMMPS由Java端MDExecutorService执行）")
            logger.info(f"工作目录: {lammps_work_dir}")
            logger.info(f"输出目录: {lammps_output_dir}")
            logger.info(f"目标性质: {target_properties}")

            lammps_result = execute_lammps_stages(
                work_dir=Path(lammps_work_dir),
                output_dir=Path(lammps_output_dir),
                target_properties=target_properties,
                use_gpu=args.use_gpu,
            )

            result = {
                "success": lammps_result.get("success", False),
                "collected_files": lammps_result.get("collected_files", []),
                "work_dir": lammps_result.get("work_dir", lammps_work_dir),
                "output_dir": lammps_result.get("output_dir", lammps_output_dir),
            }

            # 打印LAMMPS文件收集摘要
            print("\nLAMMPS输出文件收集结果摘要:")
            print(f"  状态: {'成功' if result.get('success', False) else '失败'}")
            collected_files = result.get("collected_files", [])
            if collected_files:
                print(f"  收集文件: {len(collected_files)} 个")
                for f in collected_files:
                    print(f"    - {f}")
            else:
                print("  未收集到任何文件，请确认LAMMPS模拟已由Java端MDExecutorService执行完成")
        elif args.mode == "post-processing":
            # 后处理模式不需要读取配方文件
            logger.info("[后处理] 开始执行后处理分析")

            # 解析盒子尺寸JSON
            box_size = None
            if args.box_size:
                box_size = json.loads(args.box_size)

            # 解析目标性质列表
            target_properties = [p.strip() for p in args.target_properties.split(',')]

            # 调用后处理函数
            result = run_post_processing(
                job_dir=Path(args.job_dir),
                target_properties=target_properties,
                temperature=args.temperature,
                time_step_fs=args.time_step_fs,
                box_size=box_size,
            )

            # 输出后处理结果
            if result.get('success'):
                logger.info("[后处理] 后处理分析完成")
                for prop, prop_result in result.get('results', {}).items():
                    status = prop_result.get('convergence_status', 'UNKNOWN')
                    value = prop_result.get('property_value', 'N/A')
                    unit = prop_result.get('property_unit', '')
                    logger.info(f"[后处理] {prop}: {value} {unit} ({status})")
            else:
                logger.error("[后处理] 后处理分析失败")
        elif args.mode == "moltemplate-execution":
            # moltemplate-execution模式不需要读取配方文件，只需要system-lt-file
            if not args.system_lt_file:
                logger.error("moltemplate-execution模式需要--system-lt-file参数")
                print("错误: moltemplate-execution模式需要--system-lt-file参数")
                return 1

            result = run_moltemplate_execution_mode(
                system_lt_file=args.system_lt_file,
                job_dir=str(job_dir),
                moltemplate_path=args.moltemplate_path,
                timeout=args.moltemplate_timeout,
                pdb_file=args.pdb_file,
            )
        else:
            formula_data = read_formula_json(args.formula_file)
            logger.debug(f"配方数据: {formula_data}")

            # 将Java端camelCase字段名转换为Python端snake_case格式
            # Java端FormulaRequest使用camelCase（如solventInfo、moleFraction），
            # Python端计算函数期望snake_case（如solvent_info、mole_fraction）
            formula_data = normalize_formula_data(formula_data)
            logger.debug(f"规范化后配方数据: {formula_data}")

            if args.density:
                formula_data["density"] = args.density

            if args.mode == "molecule-count":
                result = run_molecule_count_mode(formula_data)
            elif args.mode == "box-size":
                result = run_box_size_mode(formula_data, args.density)
            elif args.mode == "packmol":
                result = run_packmol_mode(
                    formula_data=formula_data,
                    job_dir=str(job_dir),
                    template_dir=args.template_dir,
                    packmol_path=args.packmol_path,
                    timeout=args.packmol_timeout,
                    max_retries=args.packmol_retries,
                )
            elif args.mode == "moltemplate-system":
                # 检查必要参数
                if not args.packed_pdb_file:
                    logger.error("moltemplate-system模式需要--packed-pdb-file参数")
                    print("错误: moltemplate-system模式需要--packed-pdb-file参数")
                    return 1

                if not args.template_library_path:
                    logger.error("moltemplate-system模式需要--template-library-path参数")
                    print("错误: moltemplate-system模式需要--template-library-path参数")
                    return 1

                result = run_moltemplate_system_mode(
                    formula_data=formula_data,
                    packed_pdb_file=args.packed_pdb_file,
                    job_dir=str(job_dir),
                    template_library_path=args.template_library_path,
                    forcefield_type=args.forcefield_type,
                    forcefield_library_path=args.forcefield_library_path,
                )
            else:
                result = run_full_mode(
                    formula_data=formula_data,
                    density=args.density,
                    job_dir=str(job_dir),
                    template_dir=args.template_dir,
                    template_library_path=args.template_library_path,
                    packmol_path=args.packmol_path,
                    timeout=args.packmol_timeout,
                    max_retries=args.packmol_retries,
                    forcefield_type=args.forcefield_type,
                    forcefield_library_path=args.forcefield_library_path,
                    moltemplate_path=args.moltemplate_path,
                    moltemplate_timeout=args.moltemplate_timeout,
                    pdb_file=args.pdb_file,
                )
        
        result["job_id"] = args.job_id
        result["user_id"] = args.user_id
        result["mode"] = args.mode
        result["timestamp"] = datetime.now().isoformat()
        result["status"] = "success" if result.get("success", True) else "failed"
        
        # 输出JSON标记包裹的结果到stdout，供Java端JsonOutputParser解析
        # 必须在人类可读摘要之前输出，确保Java端能正确提取
        print_json_result(result, args.mode)
        
        # result_json和output_file分离：--result-json优先（用于moltemplate-system等模式），
        # 不存在时回退到--output-file，再回退到默认路径modeling_result.json
        result_json_path = args.result_json or args.output_file or (str(job_dir / "modeling_result.json") if job_dir else None)
        if result_json_path:
            write_result_json(result_json_path, result)
            logger.info(f"建模计算完成，结果已保存: {result_json_path}")
        else:
            logger.info("建模计算完成（未指定输出文件路径）")
        
        print(f"\n建模结果摘要:")
        print(f"  任务ID: {args.job_id}")
        print(f"  计算模式: {args.mode}")
        print(f"  状态: {'成功' if result.get('success', True) else '失败'}")
        
        if args.mode == "moltemplate-system":
            print_moltemplate_system_summary(result)
        elif args.mode == "moltemplate-execution":
            print_moltemplate_execution_summary(result)
        elif args.mode == "lammps-execution":
            # LAMMPS文件收集摘要已在模式处理中打印
            pass
        elif args.mode == "file-organize":
            # 文件整理模式摘要
            output_files = result.get("output_files", [])
            print(f"  已复制文件: {len(output_files)} 个")
            for f in output_files:
                print(f"    - {f}")
            if not result.get("success", False):
                error = result.get("error", "")
                if error:
                    print(f"  错误信息: {error}")
        elif args.mode in ["packmol", "full"]:
            if args.mode == "packmol":
                print_packmol_summary(result)
            else:
                if "summary" in result:
                    summary = result["summary"]
                    print(f"  分子种类: {summary.get('species_count', 'N/A')}")
                    print(f"  总分子数: {summary.get('total_molecule_count', 'N/A')}")
                    print(f"  溶剂分子数: {summary.get('solvent_count', 'N/A')}")
                    print(f"  离子数: {summary.get('ion_count', 'N/A')}")
                    print(f"  总质量: {summary.get('total_mass_g', 0):.6f} g")
                
                if "box_size" in result:
                    box = result["box_size"]
                    print(f"  盒子尺寸: {box.get('x', 0):.2f} x {box.get('y', 0):.2f} x {box.get('z', 0):.2f} Å")
                
                if "packmol" in result:
                    print_packmol_summary(result["packmol"])
                
                if "moltemplate_system" in result:
                    print_moltemplate_system_summary(result["moltemplate_system"])
                
                if "moltemplate_execution" in result:
                    print_moltemplate_execution_summary(result["moltemplate_execution"])
        else:
            if "summary" in result:
                summary = result["summary"]
                print(f"  分子种类: {summary.get('species_count', 'N/A')}")
                print(f"  总分子数: {summary.get('total_molecule_count', 'N/A')}")
                print(f"  溶剂分子数: {summary.get('solvent_count', 'N/A')}")
                print(f"  离子数: {summary.get('ion_count', 'N/A')}")
                print(f"  总质量: {summary.get('total_mass_g', 0):.6f} g")
            
            if "box_size" in result:
                box = result["box_size"]
                print(f"  盒子尺寸: {box.get('x', 0):.2f} x {box.get('y', 0):.2f} x {box.get('z', 0):.2f} Å")
        
        return 0
        
    except FileNotFoundError as e:
        logger.error(f"文件未找到: {e}")
        # 输出JSON标记包裹的错误信息，供Java端JsonOutputParser解析具体错误原因
        # Java端通过 ===JSON_RESULT=== / ===END_JSON=== 标记提取错误详情，
        # 否则只能看到"Command failed with exit code: 1"而无法获知具体错误
        error_result = {
            "success": False,
            "error": f"文件未找到: {e}",
            "error_type": "FileNotFoundError",
            "mode": args.mode if args else "unknown",
            "job_id": args.job_id if args else None,
        }
        print("===JSON_RESULT===")
        print(json.dumps(error_result, ensure_ascii=False))
        print("===END_JSON===")
        return 1
    except ValueError as e:
        logger.error(f"参数错误: {e}")
        # 输出JSON标记包裹的错误信息，供Java端获取具体参数错误详情
        error_result = {
            "success": False,
            "error": f"参数错误: {e}",
            "error_type": "ValueError",
            "mode": args.mode if args else "unknown",
            "job_id": args.job_id if args else None,
        }
        print("===JSON_RESULT===")
        print(json.dumps(error_result, ensure_ascii=False))
        print("===END_JSON===")
        return 2
    except Exception as e:
        logger.exception(f"建模计算失败: {e}")
        # 输出JSON标记包裹的错误信息，供Java端获取具体异常详情
        # 包含异常类型和消息，便于Java端分类处理和展示
        error_result = {
            "success": False,
            "error": f"建模计算失败: {e}",
            "error_type": type(e).__name__,
            "mode": args.mode if args else "unknown",
            "job_id": args.job_id if args else None,
        }
        print("===JSON_RESULT===")
        print(json.dumps(error_result, ensure_ascii=False))
        print("===END_JSON===")
        return 3


if __name__ == "__main__":
    sys.exit(main())