"""
integration_test_post_processing - 后处理分析管线集成测试

功能：
    1. 测试LogFileParser解析log.lammps文件
    2. 测试GPUBackend自动选择和基本计算
    3. 测试DensityCalculator从log.lammps计算密度
    4. 测试ConvergenceChecker收敛性判断
    5. 测试完整的后处理流程（stage5）

使用方法：
    pytest tests/integration_test_post_processing.py -v

作者: 电解液MD平台
版本: 1.0.0
"""

import json
import logging
import sys
from pathlib import Path

import numpy as np
import pytest

# 将scripts目录加入sys.path，确保modeling包的相对导入正常工作
# scripts/是modeling/的父目录，modeling作为包使用相对导入
_scripts_dir = str(Path(__file__).parent.parent.parent.parent)
if _scripts_dir not in sys.path:
    sys.path.insert(0, _scripts_dir)

from modeling.utils.mdanalysis_utils import GPUBackend, LogFileParser
from modeling.utils.property_calculator import ConvergenceChecker, DensityCalculator
from modeling.stages.stage5_post_processing import (
    validate_input_files,
    write_result_json,
    run_post_processing,
)
from modeling.config.config import PostProcessingConfig

logger = logging.getLogger(__name__)


# ==================== 测试夹具 ====================


@pytest.fixture
def tmp_job_dir(tmp_path):
    """创建临时任务目录结构

    模拟标准任务目录，包含raw_outputs、post_processing、
    visualization/charts、report等子目录，以及必要的测试文件。

    Args:
        tmp_path: pytest内置临时目录fixture

    Returns:
        Path: 临时任务根目录路径
    """
    # 创建目录结构
    raw_outputs = tmp_path / "raw_outputs"
    post_processing = tmp_path / "post_processing"
    charts = tmp_path / "visualization" / "charts"
    report = tmp_path / "report"

    raw_outputs.mkdir(parents=True, exist_ok=True)
    post_processing.mkdir(parents=True, exist_ok=True)
    charts.mkdir(parents=True, exist_ok=True)
    report.mkdir(parents=True, exist_ok=True)

    # 创建占位文件，确保目录非空
    (raw_outputs / "log.lammps").write_text("# placeholder")
    (raw_outputs / "dump.trajectory.lammpstrj").write_text("# placeholder")
    (raw_outputs / "final.data").write_text("# placeholder")

    logger.info("[集成测试] 创建临时任务目录: %s", tmp_path)
    return tmp_path


@pytest.fixture
def sample_log_lammps(tmp_path):
    """创建包含3个run段的示例log.lammps文件

    生成一个包含minimization（50步）、equilibrium（100步）、
    production（200步）三个阶段的LAMMPS日志文件。
    生产阶段包含Step, Temp, E_pair, TotEng, Press, Volume, Density列，
    Density值在1.2 g/cm³附近波动。

    Args:
        tmp_path: pytest内置临时目录fixture

    Returns:
        Path: 生成的log.lammps文件路径
    """
    log_path = tmp_path / "log.lammps"
    lines = []

    # LAMMPS日志头部
    lines.append("LAMMPS (29 Oct 2020)")
    lines.append("Reading data file ...")
    lines.append("")

    # ===== 第1段: Minimization (50步) =====
    lines.append("Minimization ...")
    lines.append("Step Temp E_pair TotEng Press")
    np.random.seed(42)
    for i in range(50):
        step = i * 1
        temp = max(300.0 - i * 5.0 + np.random.normal(0, 1), 0.1)
        e_pair = -12345.0 + i * 0.5 + np.random.normal(0, 0.1)
        tot_eng = -10000.0 + i * 0.3 + np.random.normal(0, 0.1)
        press = np.random.normal(1.0, 0.5)
        lines.append(f"{step} {temp:.4f} {e_pair:.4f} {tot_eng:.4f} {press:.4f}")
    lines.append("Loop time of 0.5 on 1 procs")
    lines.append("")

    # ===== 第2段: Equilibrium (100步) =====
    lines.append("Equilibrium run ...")
    lines.append("Step Temp E_pair TotEng Press Volume Density")
    for i in range(100):
        step = 50 + i * 10
        temp = 300.0 + np.random.normal(0, 5)
        e_pair = -12300.0 + np.random.normal(0, 1)
        tot_eng = -9900.0 + np.random.normal(0, 1)
        press = np.random.normal(1.0, 0.3)
        volume = 50000.0 + np.random.normal(0, 5)
        density = 1.15 + np.random.normal(0, 0.02)
        lines.append(
            f"{step} {temp:.4f} {e_pair:.4f} {tot_eng:.4f} "
            f"{press:.4f} {volume:.4f} {density:.6f}"
        )
    lines.append("Loop time of 1.0 on 1 procs")
    lines.append("")

    # ===== 第3段: Production (200步) =====
    lines.append("Production run ...")
    lines.append("Step Temp E_pair TotEng Press Volume Density")
    for i in range(200):
        step = 1050 + i * 10
        temp = 300.0 + np.random.normal(0, 3)
        e_pair = -12300.0 + np.random.normal(0, 0.5)
        tot_eng = -9900.0 + np.random.normal(0, 0.5)
        press = np.random.normal(1.0, 0.2)
        volume = 50000.0 + np.random.normal(0, 2)
        # 密度在1.2附近波动，模拟真实MD数据
        density = 1.2 + np.random.normal(0, 0.01)
        lines.append(
            f"{step} {temp:.4f} {e_pair:.4f} {tot_eng:.4f} "
            f"{press:.4f} {volume:.4f} {density:.6f}"
        )
    lines.append("Loop time of 2.0 on 1 procs")
    lines.append("")

    log_path.write_text("\n".join(lines), encoding="utf-8")
    logger.info("[集成测试] 创建示例log.lammps: %s", log_path)
    return log_path


# ==================== 测试用例 ====================


class TestLogFileParser:
    """LogFileParser集成测试

    测试LAMMPS日志文件解析器能否正确识别多个run段，
    解析列标题和数据行，并提取生产阶段数据。
    """

    def test_log_file_parser(self, sample_log_lammps):
        """测试LogFileParser解析log.lammps文件

        验证点:
            1. 解析后应发现3个阶段（minimization, equilibrium, production）
            2. production阶段数据行数应为200
            3. production阶段应包含Density列
        """
        parser = LogFileParser()
        thermo_data = parser.parse_log_lammps(str(sample_log_lammps))

        # 验证3个阶段都被解析
        assert len(thermo_data) == 3, (
            f"期望3个阶段，实际解析到 {len(thermo_data)} 个: "
            f"{list(thermo_data.keys())}"
        )

        # 验证阶段名称
        expected_stages = ["minimization", "equilibrium", "production"]
        for stage in expected_stages:
            assert stage in thermo_data, f"缺少阶段: {stage}"

        # 验证production数据行数
        production_df = thermo_data["production"]
        assert len(production_df) == 200, (
            f"production阶段期望200行数据，实际 {len(production_df)} 行"
        )

        # 验证Density列存在
        assert "Density" in production_df.columns, (
            f"production阶段缺少Density列，实际列: {list(production_df.columns)}"
        )

        logger.info(
            "[集成测试] LogFileParser测试通过: %d 个阶段, production %d 行",
            len(thermo_data), len(production_df),
        )


class TestGPUBackend:
    """GPUBackend集成测试

    测试GPU加速计算后端的自动选择机制和基本数值计算功能，
    包括均值、标准差、自相关和梯形积分。
    """

    def test_gpu_backend_auto_select(self):
        """测试GPUBackend自动选择后端及基本计算

        验证点:
            1. use_gpu=True时后端名称不为空（至少为numpy）
            2. gpu_mean计算简单数组的均值
            3. gpu_std计算简单数组的标准差
            4. gpu_autocorrelation计算正弦波的自相关
            5. gpu_trapezoid_integral对已知函数积分
        """
        gpu = GPUBackend(use_gpu=True)

        # 验证后端已选择（至少为numpy）
        assert gpu.backend_name in ("cupy", "torch", "numpy"), (
            f"未识别的后端名称: {gpu.backend_name}"
        )

        # 测试gpu_mean
        data = np.array([1.0, 2.0, 3.0, 4.0, 5.0])
        mean_val = float(gpu.gpu_mean(data))
        assert abs(mean_val - 3.0) < 1e-10, f"gpu_mean期望3.0，实际 {mean_val}"

        # 测试gpu_std
        std_val = float(gpu.gpu_std(data))
        expected_std = float(np.std(data))
        assert abs(std_val - expected_std) < 1e-10, (
            f"gpu_std期望 {expected_std}，实际 {std_val}"
        )

        # 测试gpu_autocorrelation：正弦波的自相关
        t = np.linspace(0, 4 * np.pi, 1000)
        sine_wave = np.sin(t)
        autocorr = gpu.gpu_autocorrelation(sine_wave, max_lag=500)
        # 自相关在lag=0时应为1.0（归一化）
        assert abs(autocorr[0] - 1.0) < 1e-6, (
            f"自相关在lag=0时期望1.0，实际 {autocorr[0]}"
        )
        # 自相关值应在[-1, 1]范围内
        assert np.all(autocorr >= -1.1) and np.all(autocorr <= 1.1), (
            "自相关值超出合理范围[-1.1, 1.1]"
        )

        # 测试gpu_trapezoid_integral：对y=2x在[0,5]上积分，期望25.0
        x = np.linspace(0, 5, 1000)
        y = 2.0 * x
        integral = gpu.gpu_trapezoid_integral(y, x)
        assert abs(integral - 25.0) < 0.01, (
            f"梯形积分期望25.0，实际 {integral}"
        )

        logger.info(
            "[集成测试] GPUBackend测试通过，后端: %s", gpu.backend_name,
        )


class TestDensityCalculator:
    """DensityCalculator集成测试

    测试密度计算器从log.lammps文件中提取密度数据，
    计算统计量并进行收敛性判断的完整流程。
    """

    def test_density_calculator(self, sample_log_lammps):
        """测试DensityCalculator计算密度

        验证点:
            1. property_name为'density'
            2. property_unit为'g/cm³'
            3. property_value接近1.2
            4. convergence_status已设置
            5. property_detail包含mean, standard_deviation, standard_error
        """
        # 使用CPU后端以确保测试环境一致性
        pp_config = PostProcessingConfig(USE_GPU=False)
        calc = DensityCalculator(pp_config=pp_config)
        result = calc.calculate(log_path=str(sample_log_lammps))

        # 验证属性名称
        assert result["property_name"] == "density", (
            f"property_name期望'density'，实际 '{result['property_name']}'"
        )

        # 验证属性单位
        assert result["property_unit"] == "g/cm³", (
            f"property_unit期望'g/cm³'，实际 '{result['property_unit']}'"
        )

        # 验证密度值接近1.2
        prop_value = result["property_value"]
        assert prop_value is not None, "property_value不应为None"
        assert abs(prop_value - 1.2) < 0.1, (
            f"密度值期望接近1.2，实际 {prop_value}"
        )

        # 验证收敛状态已设置
        assert result["convergence_status"] in ("CONVERGED", "NOT_CONVERGED"), (
            f"convergence_status值异常: {result['convergence_status']}"
        )

        # 验证property_detail包含必要字段
        detail = result["property_detail"]
        assert "mean" in detail, "property_detail缺少mean字段"
        assert "standard_deviation" in detail, "property_detail缺少standard_deviation字段"
        assert "standard_error" in detail, "property_detail缺少standard_error字段"

        # 验证mean与property_value一致
        assert abs(detail["mean"] - prop_value) < 1e-6, (
            f"mean({detail['mean']})与property_value({prop_value})不一致"
        )

        logger.info(
            "[集成测试] DensityCalculator测试通过: 密度=%.6f g/cm³, 收敛=%s",
            prop_value, result["convergence_status"],
        )


class TestConvergenceChecker:
    """ConvergenceChecker集成测试

    测试收敛性检查器对密度时间序列和Green-Kubo积分
    的收敛性判断是否正确。
    """

    def test_well_converged_density(self):
        """测试良好收敛的密度数据

        使用低变异系数（std/mean很小）的密度序列，
        期望判定为CONVERGED。
        """
        pp_config = PostProcessingConfig(USE_GPU=False)
        checker = ConvergenceChecker()
        # 生成低噪声密度数据：均值1.2，标准差0.01
        np.random.seed(123)
        well_converged = np.random.normal(1.2, 0.01, 1000)
        status = checker.check_density_convergence(well_converged, threshold=0.05)
        assert status == "CONVERGED", (
            f"良好收敛数据期望CONVERGED，实际 {status}"
        )

    def test_poorly_converged_density(self):
        """测试未收敛的密度数据

        使用高变异系数（std/mean很大）的密度序列，
        期望判定为NOT_CONVERGED。
        """
        checker = ConvergenceChecker()
        # 生成高噪声密度数据：均值1.2，标准差0.5
        np.random.seed(456)
        poorly_converged = np.random.normal(1.2, 0.5, 1000)
        status = checker.check_density_convergence(poorly_converged, threshold=0.05)
        assert status == "NOT_CONVERGED", (
            f"未收敛数据期望NOT_CONVERGED，实际 {status}"
        )

    def test_gk_convergence_plateau(self):
        """测试GK积分收敛（平台期数据）

        使用末尾已达到平台期的GK积分数据，
        期望判定为CONVERGED。
        """
        checker = ConvergenceChecker()
        # 生成平台期数据：前段上升，后段平稳
        # check_gk_convergence检查末尾window_size个点的std/|mean| < 0.05
        # 因此需要确保末尾段足够平稳
        n = 2000
        np.random.seed(789)
        # 直接构造积分曲线：前段上升后段平稳
        integral_data = np.zeros(n)
        # 前1000点逐渐上升
        integral_data[:1000] = np.linspace(0, 10.0, 1000)
        # 后1000点在10.0附近极小幅波动（std/mean < 0.05）
        integral_data[1000:] = 10.0 + np.random.normal(0, 0.01, 1000)

        status = checker.check_gk_convergence(integral_data, window_size=500)
        assert status == "CONVERGED", (
            f"平台期GK积分期望CONVERGED，实际 {status}"
        )

    def test_gk_convergence_non_plateau(self):
        """测试GK积分未收敛（非平台期数据）

        使用持续上升、未达到平台期的GK积分数据，
        期望判定为NOT_CONVERGED。
        """
        checker = ConvergenceChecker()
        # 生成持续上升的数据，无平台期
        n = 2000
        np.random.seed(101)
        non_plateau = np.cumsum(np.random.normal(0.1, 0.02, n))

        status = checker.check_gk_convergence(non_plateau, window_size=500)
        assert status == "NOT_CONVERGED", (
            f"非平台期GK积分期望NOT_CONVERGED，实际 {status}"
        )

        logger.info("[集成测试] ConvergenceChecker测试通过")


class TestValidateInputFiles:
    """validate_input_files集成测试

    测试stage5中的输入文件验证函数能否正确识别
    必需文件和可选文件的存在与缺失。
    """

    def test_validate_density_only(self, tmp_path):
        """测试仅计算密度时的文件验证

        密度计算仅需log.lammps文件，创建该文件后验证应通过。

        Args:
            tmp_path: pytest内置临时目录fixture
        """
        # 创建任务目录结构
        raw_outputs = tmp_path / "raw_outputs"
        raw_outputs.mkdir(parents=True, exist_ok=True)

        # 仅创建log.lammps
        (raw_outputs / "log.lammps").write_text("# sample log")

        # 验证density所需文件
        validation = validate_input_files(tmp_path, ["density"])
        assert validation["density"]["available"] is True, (
            "density验证应通过（log.lammps存在）"
        )
        assert len(validation["density"]["missing_required"]) == 0, (
            "density不应有缺失的必需文件"
        )

    def test_validate_conductivity_missing_files(self, tmp_path):
        """测试计算电导率时缺少文件的验证

        电导率需要dump.charge.lammpstrj文件，不创建该文件时
        验证应失败并报告缺失文件。

        Args:
            tmp_path: pytest内置临时目录fixture
        """
        # 创建任务目录结构（不创建电荷轨迹文件）
        raw_outputs = tmp_path / "raw_outputs"
        raw_outputs.mkdir(parents=True, exist_ok=True)
        (raw_outputs / "log.lammps").write_text("# sample log")

        # 验证conductivity所需文件
        validation = validate_input_files(tmp_path, ["conductivity"])
        assert validation["conductivity"]["available"] is False, (
            "conductivity验证应失败（dump.charge.lammpstrj不存在）"
        )
        assert "dump.charge.lammpstrj" in validation["conductivity"]["missing_required"], (
            "缺失文件列表应包含dump.charge.lammpstrj"
        )

        logger.info("[集成测试] validate_input_files测试通过")


class TestWriteResultJson:
    """write_result_json集成测试

    测试stage5中的结果JSON写入函数，验证原子写入机制
    和内容正确性。
    """

    def test_write_result_json(self, tmp_path):
        """测试结果JSON写入和读取

        验证点:
            1. 写入后文件存在
            2. 读取内容与原始数据一致
            3. 原子写入后临时文件已清理

        Args:
            tmp_path: pytest内置临时目录fixture
        """
        output_path = tmp_path / "post_processing" / "density_result.json"

        # 构造测试结果数据
        sample_result = {
            "property_name": "density",
            "property_value": 1.1985,
            "property_unit": "g/cm³",
            "calculation_method": "时间序列平均",
            "convergence_status": "CONVERGED",
            "property_detail": {
                "mean": 1.1985,
                "standard_deviation": 0.0089,
                "standard_error": 0.0006,
                "sample_size": 160,
            },
        }

        # 写入结果
        write_result_json(sample_result, output_path)

        # 验证文件存在
        assert output_path.exists(), f"结果文件未创建: {output_path}"

        # 读取并验证内容一致
        with open(output_path, "r", encoding="utf-8") as f:
            loaded = json.load(f)

        assert loaded["property_name"] == "density", (
            "读取的property_name不一致"
        )
        assert abs(loaded["property_value"] - 1.1985) < 1e-10, (
            "读取的property_value不一致"
        )
        assert loaded["convergence_status"] == "CONVERGED", (
            "读取的convergence_status不一致"
        )

        # 验证临时文件已清理
        temp_path = output_path.with_suffix(output_path.suffix + ".tmp")
        assert not temp_path.exists(), (
            f"临时文件未被清理: {temp_path}"
        )

        logger.info("[集成测试] write_result_json测试通过")


class TestRunPostProcessingDensityOnly:
    """run_post_processing密度计算集成测试

    测试完整的后处理流水线在仅计算密度时的端到端行为，
    验证输出文件结构和结果内容。
    """

    def test_run_post_processing_density_only(self, tmp_path, sample_log_lammps):
        """测试完整后处理流水线（仅密度）

        创建包含sample log.lammps的任务目录，运行后处理流水线，
        验证post_processing/density_result.json文件存在且结构正确。

        验证点:
            1. 流水线返回success=True
            2. density_result.json文件存在
            3. 结果包含正确的property_name和property_value
            4. status中density为'success'

        Args:
            tmp_path: pytest内置临时目录fixture
            sample_log_lammps: 示例log.lammps文件fixture
        """
        # 构建任务目录结构
        raw_outputs = tmp_path / "raw_outputs"
        post_processing = tmp_path / "post_processing"
        charts = tmp_path / "visualization" / "charts"
        report = tmp_path / "report"

        raw_outputs.mkdir(parents=True, exist_ok=True)
        post_processing.mkdir(parents=True, exist_ok=True)
        charts.mkdir(parents=True, exist_ok=True)
        report.mkdir(parents=True, exist_ok=True)

        # 将示例log.lammps复制到raw_outputs目录
        log_dest = raw_outputs / "log.lammps"
        log_dest.write_text(sample_log_lammps.read_text(encoding="utf-8"), encoding="utf-8")

        # 使用CPU后端运行后处理
        pp_config = PostProcessingConfig(USE_GPU=False)
        summary = run_post_processing(
            job_dir=tmp_path,
            target_properties=["density"],
            temperature=300.0,
            pp_config=pp_config,
        )

        # 验证流水线整体成功
        assert summary["success"] is True, (
            "后处理流水线应成功（至少density计算成功）"
        )

        # 验证density_result.json文件存在
        result_path = post_processing / "density_result.json"
        assert result_path.exists(), (
            f"density_result.json未生成: {result_path}"
        )

        # 读取并验证结果结构
        with open(result_path, "r", encoding="utf-8") as f:
            result_data = json.load(f)

        assert result_data["property_name"] == "density", (
            "结果property_name应为'density'"
        )
        assert result_data["property_value"] is not None, (
            "结果property_value不应为None"
        )
        assert abs(result_data["property_value"] - 1.2) < 0.1, (
            f"密度值期望接近1.2，实际 {result_data['property_value']}"
        )

        # 验证status
        assert summary["status"]["density"] == "success", (
            f"density状态期望'success'，实际 '{summary['status']['density']}'"
        )

        # 验证输出文件记录
        assert "density" in summary["output_files"], (
            "output_files中缺少density条目"
        )

        logger.info(
            "[集成测试] run_post_processing密度测试通过: 密度=%.6f g/cm³",
            result_data["property_value"],
        )
