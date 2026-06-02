#!/usr/bin/env python3
"""
建模入口脚本

提供命令行接口执行分子建模计算

Usage:
    python run_modeling.py --job-id <job_id> --job-dir <job_dir> --formula-file <formula_file>
    python run_modeling.py --job-id <job_id> --job-dir <job_dir> --formula-file <formula_file> --mode packmol
"""

import argparse
import logging
import sys
import os
from pathlib import Path
from typing import Dict, Any
from datetime import datetime

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
    python run_modeling.py --job-id 123 --job-dir /path/to/job --formula-file /path/to/formula.json --mode full
        """,
    )
    
    parser.add_argument(
        "--job-id",
        type=str,
        required=True,
        help="任务ID",
    )
    
    parser.add_argument(
        "--job-dir",
        type=str,
        required=True,
        help="任务工作目录",
    )
    
    parser.add_argument(
        "--formula-file",
        type=str,
        required=True,
        help="配方JSON文件路径",
    )
    
    parser.add_argument(
        "--mode",
        type=str,
        choices=["molecule-count", "box-size", "packmol", "full"],
        default="full",
        help="计算模式: molecule-count(仅计算分子数), box-size(仅计算盒子尺寸), packmol(仅执行Packmol堆积), full(完整建模含Packmol)",
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
    
    molecules = []
    for mol_name, mol_info in formula_data.get("molecules", {}).items():
        molecules.append({
            "name": mol_name,
            "count": mol_info.get("count", 1),
            "pdb_file": mol_info.get("pdb_file", f"{mol_name}.pdb"),
        })
    
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


def run_full_mode(
    formula_data: Dict[str, Any],
    density: float,
    job_dir: str,
    template_dir: str = None,
    packmol_path: str = "packmol",
    timeout: int = PACKMOL_DEFAULT_TIMEOUT,
    max_retries: int = PACKMOL_MAX_RETRY_COUNT,
) -> Dict[str, Any]:
    """运行完整计算模式（含Packmol步骤）
    
    Args:
        formula_data: 配方数据
        density: 密度
        job_dir: 任务目录
        template_dir: 模板目录
        packmol_path: Packmol路径
        timeout: 超时时间
        max_retries: 最大重试次数
        
    Returns:
        计算结果
    """
    logging.info("执行完整建模计算（含Packmol堆积）")
    
    molecule_result = calculate_molecule_counts(formula_data)
    formula_data["molecules"] = molecule_result.get("molecules", {})
    
    box_result = calculate_box_size(molecule_result, density)
    formula_data["box_size"] = box_result
    
    molecules = []
    for mol_name, mol_info in formula_data.get("molecules", {}).items():
        molecules.append({
            "name": mol_name,
            "count": mol_info.get("count", 1),
            "pdb_file": mol_info.get("pdb_file", f"{mol_name}.pdb"),
        })
    
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


def main() -> int:
    """主函数
    
    Returns:
        退出码
    """
    args = parse_arguments()
    
    job_dir = Path(args.job_dir)
    job_dir.mkdir(parents=True, exist_ok=True)
    
    log_file = job_dir / "modeling.log"
    setup_logging(args.log_level, str(log_file))
    
    logger = logging.getLogger(__name__)
    logger.info(f"开始建模任务: job_id={args.job_id}")
    logger.info(f"工作目录: {job_dir}")
    logger.info(f"配方文件: {args.formula_file}")
    logger.info(f"计算模式: {args.mode}")
    
    try:
        formula_data = read_formula_json(args.formula_file)
        logger.debug(f"配方数据: {formula_data}")
        
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
        else:
            result = run_full_mode(
                formula_data=formula_data,
                density=args.density,
                job_dir=str(job_dir),
                template_dir=args.template_dir,
                packmol_path=args.packmol_path,
                timeout=args.packmol_timeout,
                max_retries=args.packmol_retries,
            )
        
        result["job_id"] = args.job_id
        result["mode"] = args.mode
        result["timestamp"] = datetime.now().isoformat()
        result["status"] = "success" if result.get("success", True) else "failed"
        
        output_file = args.output_file or str(job_dir / "modeling_result.json")
        write_result_json(output_file, result)
        
        logger.info(f"建模计算完成，结果已保存: {output_file}")
        
        print(f"\n建模结果摘要:")
        print(f"  任务ID: {args.job_id}")
        print(f"  计算模式: {args.mode}")
        print(f"  状态: {'成功' if result.get('success', True) else '失败'}")
        
        if args.mode in ["packmol", "full"]:
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
        return 1
    except ValueError as e:
        logger.error(f"参数错误: {e}")
        return 2
    except Exception as e:
        logger.exception(f"建模计算失败: {e}")
        return 3


if __name__ == "__main__":
    sys.exit(main())