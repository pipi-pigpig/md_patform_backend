"""
stage5_post_processing - 后处理分析流水线

功能：
    1. 读取任务配置（目标性质、温度、时间步、盒子尺寸）
    2. 定位并验证原始输出文件
    3. 轨迹预处理（加载与去包裹）
    4. 逐性质调用计算器执行物性计算
    5. 收敛性验证
    6. 标准化输出（结果JSON + 图表JSON）

使用方法：
    python stage5_post_processing.py --job-dir <job_dir> --target-properties density,conductivity --temperature 300.0

作者: 电解液MD平台开发团队
版本: 1.0.0
"""

import json
import logging
import os
import tempfile
from pathlib import Path
from typing import Any, Dict, List, Optional

try:
    from config.config import config, PostProcessingConfig
except ImportError:
    try:
        from modeling.config.config import config, PostProcessingConfig
    except ImportError:
        from ..config.config import config, PostProcessingConfig

try:
    # Module mode: python3 -m modeling.run_modeling
    # 此时utils是modeling.utils（modeling是顶级包）
    from modeling.utils.mdanalysis_utils import TrajectoryLoader, GPUBackend, LogFileParser
    from modeling.utils.property_calculator import (
        DensityCalculator,
        ConductivityCalculator,
        ViscosityCalculator,
        DielectricCalculator,
        SolvationCalculator,
        ConvergenceChecker,
    )
except ImportError:
    try:
        # Direct script mode: python3 run_modeling.py
        # 此时sys.path包含scripts/modeling/，utils可直接解析
        from utils.mdanalysis_utils import TrajectoryLoader, GPUBackend, LogFileParser
        from utils.property_calculator import (
            DensityCalculator,
            ConductivityCalculator,
            ViscosityCalculator,
            DielectricCalculator,
            SolvationCalculator,
            ConvergenceChecker,
        )
    except ImportError:
        # 相对导入回退：作为stages包的一部分被导入
        from ..utils.mdanalysis_utils import TrajectoryLoader, GPUBackend, LogFileParser
        from ..utils.property_calculator import (
            DensityCalculator,
            ConductivityCalculator,
            ViscosityCalculator,
            DielectricCalculator,
            SolvationCalculator,
            ConvergenceChecker,
        )

logger = logging.getLogger(__name__)

# 性质名称 → 所需原始输出文件的映射
PROPERTY_FILE_MAP = {
    "density": ["log.lammps"],
    "conductivity": ["dump.charge.lammpstrj"],
    "viscosity": ["pressure.dat"],
    "dielectric": ["dipole.dat", "total_dipole.dat"],
    "solvation_structure": ["dump.solvation.lammpstrj"],
}

# 性质名称 → 可选回退文件的映射（缺失时仅记录警告，不阻塞计算）
PROPERTY_OPTIONAL_FILE_MAP = {
    "conductivity": ["msd.dat"],
}

# 性质名称 → 对应的图表类型名称
PROPERTY_CHART_TYPE_MAP = {
    "density": "thermo_curve",
    "conductivity": "conductivity_curve",
    "viscosity": "viscosity_curve",
    "dielectric": "dielectric_curve",
    "solvation_structure": "rdf_curve",
}


def validate_input_files(
    job_dir: Path,
    target_properties: List[str],
) -> Dict[str, Dict[str, Any]]:
    """验证每个目标性质所需的输入文件是否存在

    遍历 target_properties 中每个性质，检查 raw_outputs/ 目录下
    是否存在该性质所需的全部文件。缺失的必需文件会记录警告，
    缺失的可选文件仅记录调试信息。

    Args:
        job_dir: 任务根目录，结构为 job_{job_id}/
        target_properties: 目标性质名称列表，如 ["density", "conductivity"]

    Returns:
        验证结果字典，键为性质名称，值为包含以下字段的字典:
        - available (bool): 必需文件是否全部存在
        - missing_required (list[str]): 缺失的必需文件名列表
        - missing_optional (list[str]): 缺失的可选文件名列表
        - available_files (list[str]): 已存在的文件名列表
    """
    raw_outputs_dir = job_dir / "raw_outputs"
    validation_result = {}

    for prop in target_properties:
        prop = prop.strip().lower()
        required_files = PROPERTY_FILE_MAP.get(prop, [])
        optional_files = PROPERTY_OPTIONAL_FILE_MAP.get(prop, [])

        missing_required = []
        missing_optional = []
        available_files = []

        # 检查必需文件
        for filename in required_files:
            filepath = raw_outputs_dir / filename
            if filepath.exists():
                available_files.append(filename)
            else:
                missing_required.append(filename)
                logger.warning(
                    "[后处理验证] 性质 '%s' 缺少必需文件: %s",
                    prop, filename,
                )

        # 检查可选文件
        for filename in optional_files:
            filepath = raw_outputs_dir / filename
            if filepath.exists():
                available_files.append(filename)
            else:
                missing_optional.append(filename)
                logger.debug(
                    "[后处理验证] 性质 '%s' 缺少可选文件: %s",
                    prop, filename,
                )

        is_available = len(missing_required) == 0
        validation_result[prop] = {
            "available": is_available,
            "missing_required": missing_required,
            "missing_optional": missing_optional,
            "available_files": available_files,
        }

        if is_available:
            logger.info(
                "[后处理验证] 性质 '%s' 输入文件验证通过，可用文件: %s",
                prop, available_files,
            )
        else:
            logger.warning(
                "[后处理验证] 性质 '%s' 输入文件验证失败，缺失必需文件: %s",
                prop, missing_required,
            )

    return validation_result


def write_result_json(result: Dict[str, Any], output_path: Path) -> None:
    """将计算结果字典写入JSON文件（原子写入）

    采用"临时文件→重命名"的原子操作策略，避免写入过程中
    程序崩溃导致文件损坏。

    Args:
        result: 计算结果字典，包含 property_name、property_value 等字段
        output_path: 输出文件路径，通常为 post_processing/{property}_result.json

    Raises:
        IOError: 写入或重命名失败时抛出
    """
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    # 原子写入：先写入临时文件，再重命名
    temp_path = output_path.with_suffix(output_path.suffix + ".tmp")

    try:
        with open(temp_path, "w", encoding="utf-8") as f:
            json.dump(result, f, ensure_ascii=False, indent=2)

        # 如果目标文件已存在，先删除
        if output_path.exists():
            output_path.unlink()

        # 原子重命名
        temp_path.rename(output_path)
        logger.info(
            "[后处理输出] 结果已写入: %s", output_path,
        )

    except Exception as e:
        # 清理临时文件
        if temp_path.exists():
            temp_path.unlink()
        logger.error(
            "[后处理输出] 写入结果文件失败: %s, 错误: %s",
            output_path, e,
        )
        raise IOError(f"写入结果文件失败: {e}")


def write_chart_json(chart_data: Dict[str, Any], output_path: Path) -> None:
    """将图表数据写入JSON文件（原子写入）

    采用"临时文件→重命名"的原子操作策略，确保图表数据
    文件的完整性。

    Args:
        chart_data: 图表数据字典，包含 chart_type、x_label、y_label、series 等字段
        output_path: 输出文件路径，通常为 visualization/charts/{type}_curve.json

    Raises:
        IOError: 写入或重命名失败时抛出
    """
    output_path = Path(output_path)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    # 原子写入：先写入临时文件，再重命名
    temp_path = output_path.with_suffix(output_path.suffix + ".tmp")

    try:
        with open(temp_path, "w", encoding="utf-8") as f:
            json.dump(chart_data, f, ensure_ascii=False, indent=2)

        # 如果目标文件已存在，先删除
        if output_path.exists():
            output_path.unlink()

        # 原子重命名
        temp_path.rename(output_path)
        logger.info(
            "[后处理输出] 图表数据已写入: %s", output_path,
        )

    except Exception as e:
        # 清理临时文件
        if temp_path.exists():
            temp_path.unlink()
        logger.error(
            "[后处理输出] 写入图表数据文件失败: %s, 错误: %s",
            output_path, e,
        )
        raise IOError(f"写入图表数据文件失败: {e}")


def collect_all_results(post_processing_dir: Path) -> Dict[str, Any]:
    """读取 post_processing/ 目录下所有结果JSON文件，合并为汇总字典

    遍历 post_processing/ 目录中所有 *_result.json 文件，
    读取每个文件的内容并以 property_name 为键合并到一个字典中。

    Args:
        post_processing_dir: 后处理结果目录路径

    Returns:
        汇总结果字典，键为性质名称（如 "density"），值为对应的结果字典。
        如果目录不存在或为空，返回空字典。
    """
    post_processing_dir = Path(post_processing_dir)

    if not post_processing_dir.exists():
        logger.warning(
            "[后处理汇总] 后处理结果目录不存在: %s",
            post_processing_dir,
        )
        return {}

    all_results = {}
    result_files = sorted(post_processing_dir.glob("*_result.json"))

    if not result_files:
        logger.warning(
            "[后处理汇总] 未找到任何结果文件: %s",
            post_processing_dir,
        )
        return {}

    for result_file in result_files:
        try:
            with open(result_file, "r", encoding="utf-8") as f:
                result_data = json.load(f)

            prop_name = result_data.get("property_name", result_file.stem)
            all_results[prop_name] = result_data
            logger.info(
                "[后处理汇总] 读取结果文件: %s, 性质: %s",
                result_file.name, prop_name,
            )

        except Exception as e:
            logger.error(
                "[后处理汇总] 读取结果文件失败: %s, 错误: %s",
                result_file, e,
            )

    logger.info(
        "[后处理汇总] 共收集 %d 个性质的计算结果: %s",
        len(all_results), list(all_results.keys()),
    )
    return all_results


def _compute_volume_from_box(box_size: Dict[str, float]) -> float:
    """根据盒子尺寸字典计算体积

    Args:
        box_size: 盒子尺寸字典，包含 lx, ly, lz 键，单位为 Å

    Returns:
        体系体积，单位为 Å³
    """
    lx = box_size.get("lx", 0.0)
    ly = box_size.get("ly", 0.0)
    lz = box_size.get("lz", 0.0)
    return lx * ly * lz


def _build_chart_data(
    property_name: str,
    calc_result: Dict[str, Any],
) -> Optional[Dict[str, Any]]:
    """从计算器结果中提取图表数据并构建标准图表JSON格式

    根据性质类型，从计算器返回的 chart_data 字段中提取数据，
    转换为标准化的图表JSON格式。

    Args:
        property_name: 性质名称
        calc_result: 计算器返回的完整结果字典

    Returns:
        标准图表数据字典，包含 chart_type、x_label、y_label、series；
        如果无图表数据则返回 None
    """
    chart_type = PROPERTY_CHART_TYPE_MAP.get(property_name)
    if chart_type is None:
        return None

    raw_chart = calc_result.get("chart_data", {})
    if not raw_chart:
        return None

    series = []

    if property_name == "density":
        # 密度曲线：热力学数据
        series.append({
            "name": "density",
            "x_data": [],
            "y_data": [],
        })

    elif property_name == "conductivity":
        # 电导率曲线：GK积分随时间变化
        cond_curve = raw_chart.get("conductivity_curve", {})
        time_data = cond_curve.get("time", [])
        integral_data = cond_curve.get("integral_value", [])
        series.append({
            "name": "conductivity_integral",
            "x_data": time_data,
            "y_data": integral_data,
        })

    elif property_name == "viscosity":
        # 粘度曲线：GK积分随时间变化
        visc_curve = raw_chart.get("viscosity_curve", {})
        time_data = visc_curve.get("time", [])
        integral_data = visc_curve.get("integral_value", [])
        series.append({
            "name": "viscosity_integral",
            "x_data": time_data,
            "y_data": integral_data,
        })

    elif property_name == "dielectric":
        # 介电常数曲线：运行平均
        diel_curve = raw_chart.get("dielectric_curve", {})
        time_data = diel_curve.get("time", [])
        running_avg = diel_curve.get("running_average", [])
        series.append({
            "name": "dielectric_running_average",
            "x_data": time_data,
            "y_data": running_avg,
        })

    elif property_name == "solvation_structure":
        # RDF曲线：各离子-溶剂对的RDF
        rdf_curve = raw_chart.get("rdf_curve", {})
        for pair_name, pair_data in rdf_curve.items():
            r_data = pair_data.get("r", [])
            g_r_data = pair_data.get("g_r", [])
            series.append({
                "name": pair_name,
                "x_data": r_data,
                "y_data": g_r_data,
            })

    if not series:
        return None

    # 构建标准图表JSON格式
    x_label_map = {
        "thermo_curve": "时间步",
        "conductivity_curve": "相关时间 (ps)",
        "viscosity_curve": "相关时间 (ps)",
        "dielectric_curve": "采样点",
        "rdf_curve": "r (Å)",
    }
    y_label_map = {
        "thermo_curve": "密度 (g/cm³)",
        "conductivity_curve": "电导率积分 (S/m)",
        "viscosity_curve": "粘度积分 (Pa·s)",
        "dielectric_curve": "介电常数",
        "rdf_curve": "g(r)",
    }

    chart_data = {
        "chart_type": chart_type,
        "x_label": x_label_map.get(chart_type, ""),
        "y_label": y_label_map.get(chart_type, ""),
        "series": series,
    }

    return chart_data


def run_post_processing(
    job_dir: Path,
    target_properties: list,
    temperature: float = 300.0,
    time_step_fs: float = 1.0,
    box_size: dict = None,
    pp_config=None,
) -> dict:
    """执行完整的后处理分析流水线

    按照六步工作流依次执行：
        1. 读取任务配置
        2. 加载输出文件
        3. 轨迹预处理
        4. 逐性质计算
        5. 收敛性验证
        6. 标准化输出

    对于缺失必需文件的性质，记录警告并跳过；
    对于计算失败的性质，记录错误并继续下一个。
    最终返回包含每个性质成功/失败状态的汇总字典。

    Args:
        job_dir: 任务根目录，遵循项目文件结构:
                 job_{job_id}/
                 ├── inputs/
                 ├── raw_outputs/
                 ├── post_processing/
                 ├── visualization/charts/
                 └── report/
        target_properties: 目标性质名称列表，如 ["density", "conductivity"]
        temperature: 系统温度，单位K，默认300.0
        time_step_fs: 模拟时间步长，单位fs，默认1.0
        box_size: 盒子尺寸字典，包含 lx/ly/lz 键，单位Å；
                  为None时从轨迹文件中自动提取
        pp_config: 后处理配置对象，为None时使用全局config中的配置

    Returns:
        汇总结果字典，包含以下字段:
        - success (bool): 整体是否成功（至少一个性质计算成功）
        - total_properties (int): 目标性质总数
        - successful_properties (int): 成功计算的性质数
        - failed_properties (int): 失败的性质数
        - skipped_properties (int): 因文件缺失而跳过的性质数
        - results (dict): 各性质的计算结果，键为性质名称
        - status (dict): 各性质的状态，键为性质名称，值为
          "success"/"failed"/"skipped"
        - output_files (dict): 各性质的输出文件路径
    """
    job_dir = Path(job_dir)
    pp_config = pp_config or config.post_processing

    logger.info("[后处理] ========== 开始后处理分析流水线 ==========")
    logger.info("[后处理] 任务目录: %s", job_dir)
    logger.info("[后处理] 目标性质: %s", target_properties)
    logger.info("[后处理] 温度: %.1f K", temperature)
    logger.info("[后处理] 时间步: %.1f fs", time_step_fs)

    # ===== 步骤1: 读取任务配置 =====
    logger.info("[后处理] 步骤1: 读取任务配置")

    # 标准化性质名称（小写、去空格）
    target_properties = [p.strip().lower() for p in target_properties]
    logger.info("[后处理] 标准化后的目标性质: %s", target_properties)

    # 计算盒子体积
    volume_angstrom3 = None
    if box_size is not None:
        volume_angstrom3 = _compute_volume_from_box(box_size)
        logger.info(
            "[后处理] 盒子尺寸: lx=%.2f, ly=%.2f, lz=%.2f Å, 体积=%.2f Å³",
            box_size.get("lx", 0), box_size.get("ly", 0),
            box_size.get("lz", 0), volume_angstrom3,
        )
    else:
        logger.info("[后处理] 未提供盒子尺寸，将从轨迹文件中提取")

    # ===== 步骤2: 加载输出文件 =====
    logger.info("[后处理] 步骤2: 加载并验证输出文件")

    validation = validate_input_files(job_dir, target_properties)

    raw_outputs_dir = job_dir / "raw_outputs"
    post_processing_dir = job_dir / "post_processing"
    charts_dir = job_dir / "visualization" / "charts"

    # 确保输出目录存在
    post_processing_dir.mkdir(parents=True, exist_ok=True)
    charts_dir.mkdir(parents=True, exist_ok=True)

    # ===== 步骤3: 轨迹预处理 =====
    logger.info("[后处理] 步骤3: 轨迹预处理")

    # 如果未提供盒子尺寸，尝试从轨迹文件中提取
    if volume_angstrom3 is None:
        trajectory_path = raw_outputs_dir / "dump.trajectory.lammpstrj"
        if trajectory_path.exists():
            try:
                loader = TrajectoryLoader()
                universe = loader.load_lammps_trajectory(str(trajectory_path))
                dimensions = loader.extract_box_dimensions(universe)
                # 使用最后一帧的盒子尺寸
                last_dims = dimensions[-1]
                lx, ly, lz = last_dims[0], last_dims[1], last_dims[2]
                volume_angstrom3 = lx * ly * lz
                logger.info(
                    "[后处理] 从轨迹提取盒子尺寸: lx=%.2f, ly=%.2f, lz=%.2f Å, 体积=%.2f Å³",
                    lx, ly, lz, volume_angstrom3,
                )
                # 同时更新 box_size 以备后续使用
                box_size = {"lx": float(lx), "ly": float(ly), "lz": float(lz)}
            except Exception as e:
                logger.warning(
                    "[后处理] 从轨迹提取盒子尺寸失败: %s，将使用默认值",
                    e,
                )
                # 使用配置中的默认体积，避免硬编码
                volume_angstrom3 = config.post_processing.DEFAULT_VOLUME_ANGSTROM3
                box_size = {"lx": 36.84, "ly": 36.84, "lz": 36.84}
        else:
            logger.warning(
                "[后处理] 轨迹文件不存在，使用默认体积: %.1f Å³",
                volume_angstrom3 or config.post_processing.DEFAULT_VOLUME_ANGSTROM3,
            )
            volume_angstrom3 = volume_angstrom3 or config.post_processing.DEFAULT_VOLUME_ANGSTROM3

    # ===== 步骤4: 逐性质计算 =====
    logger.info("[后处理] 步骤4: 逐性质计算")

    results = {}
    status = {}
    output_files = {}
    successful_count = 0
    failed_count = 0
    skipped_count = 0

    for prop in target_properties:
        logger.info("[后处理] ---------- 计算性质: %s ----------", prop)

        # 检查文件是否可用
        prop_validation = validation.get(prop, {})
        if not prop_validation.get("available", False):
            logger.warning(
                "[后处理] 性质 '%s' 所需文件不完整，跳过计算。缺失: %s",
                prop, prop_validation.get("missing_required", []),
            )
            status[prop] = "skipped"
            results[prop] = {
                "property_name": prop,
                "property_value": None,
                "error": f"缺少必需文件: {prop_validation.get('missing_required', [])}",
            }
            skipped_count += 1
            continue

        # 根据性质类型调用对应的计算器
        calc_result = None
        try:
            calc_result = _calculate_property(
                prop=prop,
                raw_outputs_dir=raw_outputs_dir,
                temperature=temperature,
                volume_angstrom3=volume_angstrom3,
                pp_config=pp_config,
            )
        except Exception as e:
            logger.error(
                "[后处理] 性质 '%s' 计算异常: %s", prop, e,
            )
            calc_result = {
                "property_name": prop,
                "property_value": None,
                "error": f"计算异常: {e}",
            }

        if calc_result is None:
            logger.error(
                "[后处理] 性质 '%s' 计算返回空结果", prop,
            )
            calc_result = {
                "property_name": prop,
                "property_value": None,
                "error": "计算返回空结果",
            }

        # ===== 步骤5: 收敛性验证 =====
        # 收敛性已在各计算器内部完成，此处仅记录结果
        convergence = calc_result.get("convergence_status", "UNKNOWN")
        logger.info(
            "[后处理] 性质 '%s' 收敛状态: %s", prop, convergence,
        )

        # 判断计算是否成功
        prop_value = calc_result.get("property_value")
        if prop_value is not None:
            status[prop] = "success"
            successful_count += 1
            logger.info(
                "[后处理] 性质 '%s' 计算成功: %s %s",
                prop,
                prop_value,
                calc_result.get("property_unit", ""),
            )
        else:
            status[prop] = "failed"
            failed_count += 1
            error_msg = calc_result.get("error", "未知错误")
            logger.error(
                "[后处理] 性质 '%s' 计算失败: %s", prop, error_msg,
            )

        results[prop] = calc_result

        # ===== 步骤6: 标准化输出 =====
        prop_output_files = {}

        # 6.1 写入结果JSON
        result_filename = f"{prop}_result.json"
        result_path = post_processing_dir / result_filename
        try:
            write_result_json(calc_result, result_path)
            prop_output_files["result"] = str(result_path)
        except IOError as e:
            logger.error(
                "[后处理] 性质 '%s' 结果写入失败: %s", prop, e,
            )

        # 6.2 写入图表JSON
        chart_data = _build_chart_data(prop, calc_result)
        if chart_data is not None:
            chart_type = chart_data.get("chart_type", f"{prop}_curve")
            chart_filename = f"{chart_type}.json"
            chart_path = charts_dir / chart_filename
            try:
                write_chart_json(chart_data, chart_path)
                prop_output_files["chart"] = str(chart_path)
            except IOError as e:
                logger.error(
                    "[后处理] 性质 '%s' 图表数据写入失败: %s", prop, e,
                )

        output_files[prop] = prop_output_files

    # ===== 汇总结果 =====
    logger.info("[后处理] ========== 后处理分析流水线完成 ==========")
    logger.info(
        "[后处理] 汇总: 总计 %d 个性质, 成功 %d, 失败 %d, 跳过 %d",
        len(target_properties), successful_count, failed_count, skipped_count,
    )

    summary = {
        "success": successful_count > 0,
        "total_properties": len(target_properties),
        "successful_properties": successful_count,
        "failed_properties": failed_count,
        "skipped_properties": skipped_count,
        "results": results,
        "status": status,
        "output_files": output_files,
    }

    return summary


def _calculate_property(
    prop: str,
    raw_outputs_dir: Path,
    temperature: float,
    volume_angstrom3: float,
    pp_config: PostProcessingConfig,
) -> Optional[Dict[str, Any]]:
    """根据性质类型调用对应的计算器

    根据性质名称选择合适的计算器，准备输入参数并执行计算。
    每个计算器返回标准化的结果字典。

    Args:
        prop: 性质名称，如 "density"、"conductivity" 等
        raw_outputs_dir: 原始输出文件目录路径
        temperature: 系统温度，单位K
        volume_angstrom3: 系统体积，单位Å³
        pp_config: 后处理配置对象

    Returns:
        计算结果字典；如果性质类型未知返回 None

    Raises:
        Exception: 计算器内部错误时向上传播
    """
    if prop == "density":
        # 密度计算：从 log.lammps 提取密度列
        log_path = raw_outputs_dir / "log.lammps"
        calculator = DensityCalculator(pp_config=pp_config)
        return calculator.calculate(
            log_path=str(log_path),
            equilibration_fraction=pp_config.EQUILIBRATION_FRACTION,
        )

    elif prop == "conductivity":
        # 电导率计算：Green-Kubo方法
        charge_traj_path = raw_outputs_dir / "dump.charge.lammpstrj"
        msd_path = raw_outputs_dir / "msd.dat"
        calculator = ConductivityCalculator(pp_config=pp_config)
        return calculator.calculate(
            charge_trajectory_path=str(charge_traj_path),
            temperature=temperature,
            volume_angstrom3=volume_angstrom3,
            msd_path=str(msd_path) if msd_path.exists() else None,
        )

    elif prop == "viscosity":
        # 粘度计算：Green-Kubo方法
        pressure_path = raw_outputs_dir / "pressure.dat"
        calculator = ViscosityCalculator(pp_config=pp_config)
        return calculator.calculate(
            pressure_path=str(pressure_path),
            temperature=temperature,
            volume_angstrom3=volume_angstrom3,
        )

    elif prop == "dielectric":
        # 介电常数计算：偶极矩涨落方法
        dipole_path = raw_outputs_dir / "dipole.dat"
        total_dipole_path = raw_outputs_dir / "total_dipole.dat"
        calculator = DielectricCalculator(pp_config=pp_config)
        return calculator.calculate(
            dipole_path=str(dipole_path),
            total_dipole_path=str(total_dipole_path),
            temperature=temperature,
            volume_angstrom3=volume_angstrom3,
        )

    elif prop == "solvation_structure":
        # 溶剂化结构计算：RDF积分
        solvation_traj_path = raw_outputs_dir / "dump.solvation.lammpstrj"
        calculator = SolvationCalculator(pp_config=pp_config)
        return calculator.calculate(
            solvation_trajectory_path=str(solvation_traj_path),
        )

    else:
        logger.warning(
            "[后处理] 未知性质类型: '%s'，跳过计算", prop,
        )
        return None
