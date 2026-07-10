"""
物性计算器模块 - MD后处理分析物性计算

功能：
    1. ConvergenceChecker: 收敛性检查器，判断密度/GK积分/RDF是否收敛
    2. DensityCalculator: 密度计算器，从log.lammps提取密度数据并统计
    3. ConductivityCalculator: 电导率计算器，基于Green-Kubo方法计算离子电导率
    4. ViscosityCalculator: 粘度计算器，基于Green-Kubo方法计算剪切粘度
    5. DielectricCalculator: 介电常数计算器，基于偶极矩涨落方法计算介电常数
    6. SolvationCalculator: 溶剂化结构计算器，基于RDF积分计算配位数

所有计算均通过GPUBackend实现GPU加速，自动降级到CPU后端。
物理常数统一使用PostProcessingConfig中定义的SI单位值。

使用方法：
    from utils.property_calculator import DensityCalculator, ConductivityCalculator
    calc = DensityCalculator()
    result = calc.calculate(log_path="log.lammps")

作者: 电解液MD平台开发团队
版本: 1.0.0
"""

import logging
import json
import numpy as np
from scipy.integrate import cumulative_trapezoid
from pathlib import Path
from typing import Dict, Any, List, Optional, Tuple

try:
    from ..config.config import config, PostProcessingConfig
except ImportError:
    from config import config, PostProcessingConfig

from .mdanalysis_utils import TrajectoryLoader, GPUBackend, LogFileParser

logger = logging.getLogger(__name__)


class ConvergenceChecker:
    """收敛性检查器

    提供多种收敛性判断方法，用于评估MD模拟后处理分析结果的可靠性。
    支持密度收敛、Green-Kubo积分收敛和RDF收敛三种检查方式。

    使用示例:
        >>> checker = ConvergenceChecker()
        >>> status = checker.check_density_convergence(density_values, threshold=0.05)
        >>> print(status)  # 'CONVERGED' 或 'NOT_CONVERGED'
    """

    def check_density_convergence(
        self, density_values: np.ndarray, threshold: float = 0.05
    ) -> str:
        """检查密度时间序列的收敛性

        通过计算密度序列的标准差与均值之比（变异系数）来判断收敛性。
        当std/mean < threshold时认为密度已收敛。

        Args:
            density_values: 密度时间序列数据，形状为(N,)
            threshold: 收敛阈值，std/mean比值小于此值判定为收敛，默认0.05

        Returns:
            收敛状态字符串，'CONVERGED'表示已收敛，'NOT_CONVERGED'表示未收敛
        """
        if len(density_values) < 10:
            logger.warning(
                "[ConvergenceChecker] 密度数据点不足(%d)，无法可靠判断收敛性",
                len(density_values),
            )
            return "NOT_CONVERGED"

        gpu = GPUBackend(use_gpu=config.post_processing.USE_GPU)
        mean_val = float(gpu.gpu_mean(density_values))
        std_val = float(gpu.gpu_std(density_values))

        if abs(mean_val) < 1e-15:
            logger.warning("[ConvergenceChecker] 密度均值接近零，无法计算变异系数")
            return "NOT_CONVERGED"

        cv = std_val / abs(mean_val)
        status = "CONVERGED" if cv < threshold else "NOT_CONVERGED"

        logger.info(
            "[ConvergenceChecker] 密度收敛检查: std/mean=%.6f, 阈值=%.4f, 结果=%s",
            cv, threshold, status,
        )
        return status

    def check_gk_convergence(
        self, integral_values: np.ndarray, window_size: int = 500
    ) -> str:
        """检查Green-Kubo积分的收敛性

        通过分析GK积分末尾窗口内的波动来判断积分是否已达到平台期。
        如果末尾窗口内积分值的相对波动较小，则认为积分已收敛。

        Args:
            integral_values: GK积分的时间序列值，形状为(N,)
            window_size: 末尾分析窗口大小，默认500个数据点

        Returns:
            收敛状态字符串，'CONVERGED'表示已收敛，'NOT_CONVERGED'表示未收敛
        """
        if len(integral_values) < window_size:
            logger.warning(
                "[ConvergenceChecker] GK积分数据点(%d)少于窗口大小(%d)，无法判断收敛",
                len(integral_values), window_size,
            )
            return "NOT_CONVERGED"

        gpu = GPUBackend(use_gpu=config.post_processing.USE_GPU)

        # 取末尾窗口数据
        tail = integral_values[-window_size:]
        tail_mean = float(gpu.gpu_mean(tail))
        tail_std = float(gpu.gpu_std(tail))

        if abs(tail_mean) < 1e-15:
            logger.warning("[ConvergenceChecker] GK积分末尾均值接近零")
            return "NOT_CONVERGED"

        # 相对波动：std/|mean|
        relative_fluctuation = tail_std / abs(tail_mean)

        # 如果末尾窗口的相对波动小于5%，认为已收敛
        convergence_threshold = 0.05
        status = "CONVERGED" if relative_fluctuation < convergence_threshold else "NOT_CONVERGED"

        logger.info(
            "[ConvergenceChecker] GK积分收敛检查: 末尾波动=%.6f, 阈值=%.4f, 结果=%s",
            relative_fluctuation, convergence_threshold, status,
        )
        return status

    def check_rdf_convergence(
        self, rdf_values: np.ndarray, threshold: float = 0.05
    ) -> str:
        """检查RDF的收敛性（平滑性）

        通过分析RDF高r区域（远距离区域）的方差来判断RDF是否足够平滑。
        高r区域的RDF值应趋近于1.0，方差越小表示收敛越好。

        Args:
            rdf_values: RDF值数组，形状为(N,)，对应从近到远的距离
            threshold: 高r区域方差阈值，小于此值判定为收敛，默认0.05

        Returns:
            收敛状态字符串，'CONVERGED'表示已收敛，'NOT_CONVERGED'表示未收敛
        """
        if len(rdf_values) < 20:
            logger.warning(
                "[ConvergenceChecker] RDF数据点不足(%d)，无法判断收敛",
                len(rdf_values),
            )
            return "NOT_CONVERGED"

        gpu = GPUBackend(use_gpu=config.post_processing.USE_GPU)

        # 取高r区域（后1/3的数据点）
        n_high_r = len(rdf_values) // 3
        high_r_region = rdf_values[-n_high_r:]

        # 计算高r区域的方差
        variance = float(gpu.gpu_std(high_r_region)) ** 2

        status = "CONVERGED" if variance < threshold else "NOT_CONVERGED"

        logger.info(
            "[ConvergenceChecker] RDF收敛检查: 高r区域方差=%.6f, 阈值=%.4f, 结果=%s",
            variance, threshold, status,
        )
        return status


class DensityCalculator:
    """密度计算器

    从LAMMPS日志文件中提取密度数据，计算平均密度及其统计误差。
    支持排除平衡阶段数据，并进行收敛性判断。

    属性:
        pp_config: 后处理配置参数
        gpu: GPU加速计算后端
        parser: LAMMPS日志文件解析器
        convergence_checker: 收敛性检查器

    使用示例:
        >>> calc = DensityCalculator()
        >>> result = calc.calculate(log_path="log.lammps")
        >>> print(result['property_value'], result['property_unit'])
    """

    def __init__(self, pp_config: Optional[PostProcessingConfig] = None):
        """初始化密度计算器

        Args:
            pp_config: 后处理配置参数，为None时使用全局config中的配置
        """
        self.pp_config = pp_config or config.post_processing
        self.gpu = GPUBackend(use_gpu=self.pp_config.USE_GPU)
        self.parser = LogFileParser()
        self.convergence_checker = ConvergenceChecker()
        logger.info("[DensityCalculator] 初始化完成，GPU后端: %s", self.gpu.backend_name)

    def calculate(
        self, log_path: str, equilibration_fraction: float = 0.2
    ) -> Dict[str, Any]:
        """计算密度

        从log.lammps文件中解析密度数据，排除平衡阶段后计算统计量。

        计算流程:
            1. 使用LogFileParser解析log.lammps
            2. 提取生产阶段密度数据
            3. 使用GPUBackend计算均值、标准差、标准误差
            4. 检查密度收敛性
            5. 组装结果字典

        Args:
            log_path: log.lammps文件路径
            equilibration_fraction: 平衡阶段排除比例，默认0.2（排除前20%）

        Returns:
            计算结果字典，包含:
            - property_name: 属性名称
            - property_value: 属性值（均值）
            - property_unit: 属性单位
            - calculation_method: 计算方法
            - convergence_status: 收敛状态
            - property_detail: 详细统计信息
            - sub_table_data: 子表数据（密度张量、分量密度）
        """
        logger.info("[DensityCalculator] 开始计算密度，日志文件: %s", log_path)

        # 解析日志文件
        thermo_data = self.parser.parse_log_lammps(log_path)
        production_df = self.parser.extract_production_data(
            thermo_data, equilibration_fraction
        )

        if production_df.empty:
            logger.error("[DensityCalculator] 生产阶段数据为空")
            return self._empty_result("密度数据为空")

        # 查找密度列（LAMMPS中可能叫Density或density）
        density_col = None
        for col in production_df.columns:
            if col.lower() == "density":
                density_col = col
                break

        if density_col is None:
            logger.error("[DensityCalculator] 日志中未找到Density列")
            return self._empty_result("日志中未找到Density列")

        # 提取密度数据
        density_values = production_df[density_col].values.astype(np.float64)

        # 使用GPUBackend计算统计量
        mean_density = float(self.gpu.gpu_mean(density_values))
        std_density = float(self.gpu.gpu_std(density_values))
        n_samples = len(density_values)
        standard_error = std_density / np.sqrt(n_samples) if n_samples > 1 else 0.0

        # 收敛性检查
        convergence_status = self.convergence_checker.check_density_convergence(
            density_values, threshold=self.pp_config.DENSITY_CONVERGENCE_THRESHOLD
        )

        # 查找各分量密度列（如DensityX, DensityY, DensityZ）
        component_density = {}
        for axis in ["X", "Y", "Z"]:
            comp_col = f"Density{axis}"
            if comp_col in production_df.columns:
                comp_values = production_df[comp_col].values.astype(np.float64)
                component_density[axis.lower()] = float(self.gpu.gpu_mean(comp_values))

        # 构建密度张量（对角矩阵）
        density_tensor = {
            "xx": component_density.get("x", mean_density),
            "yy": component_density.get("y", mean_density),
            "zz": component_density.get("z", mean_density),
        }

        result = {
            "property_name": "density",
            "property_value": round(mean_density, 6),
            "property_unit": "g/cm³",
            "calculation_method": "时间序列平均",
            "convergence_status": convergence_status,
            "property_detail": {
                "mean": round(mean_density, 6),
                "standard_deviation": round(std_density, 6),
                "standard_error": round(standard_error, 6),
                "sample_size": n_samples,
                "equilibration_fraction": equilibration_fraction,
            },
            "sub_table_data": {
                "density_tensor": density_tensor,
                "component_density": component_density,
            },
        }

        logger.info(
            "[DensityCalculator] 密度计算完成: %.6f g/cm³, 收敛状态=%s",
            mean_density, convergence_status,
        )
        return result

    def _empty_result(self, reason: str) -> Dict[str, Any]:
        """生成空结果字典

        Args:
            reason: 空结果原因说明

        Returns:
            包含默认空值的计算结果字典
        """
        return {
            "property_name": "density",
            "property_value": None,
            "property_unit": "g/cm³",
            "calculation_method": "时间序列平均",
            "convergence_status": "NOT_CONVERGED",
            "property_detail": {
                "mean": None,
                "standard_deviation": None,
                "standard_error": None,
                "sample_size": 0,
                "equilibration_fraction": 0.0,
            },
            "sub_table_data": {
                "density_tensor": {"xx": None, "yy": None, "zz": None},
                "component_density": {},
            },
            "error": reason,
        }


class ConductivityCalculator:
    """电导率计算器

    基于Green-Kubo方法计算离子电导率，通过电流密度自相关函数的时间积分
    得到电导率。当GK方法不收敛时，自动回退到Einstein方法。

    Green-Kubo公式:
        σ = 1/(3*V*kB*T) * ∫⟨J(0)·J(t)⟩dt

    其中J为电流密度矢量，V为体系体积，kB为玻尔兹曼常数，T为温度。

    属性:
        pp_config: 后处理配置参数
        gpu: GPU加速计算后端
        loader: 轨迹文件加载器
        convergence_checker: 收敛性检查器

    使用示例:
        >>> calc = ConductivityCalculator()
        >>> result = calc.calculate(
        ...     charge_trajectory_path="dump.charge.lammpstrj",
        ...     temperature=300,
        ...     volume_angstrom3=50000.0,
        ... )
    """

    def __init__(self, pp_config: Optional[PostProcessingConfig] = None):
        """初始化电导率计算器

        Args:
            pp_config: 后处理配置参数，为None时使用全局config中的配置
        """
        self.pp_config = pp_config or config.post_processing
        self.gpu = GPUBackend(use_gpu=self.pp_config.USE_GPU)
        self.loader = TrajectoryLoader()
        self.convergence_checker = ConvergenceChecker()
        logger.info("[ConductivityCalculator] 初始化完成，GPU后端: %s", self.gpu.backend_name)

    def calculate(
        self,
        charge_trajectory_path: str,
        temperature: float,
        volume_angstrom3: float,
        msd_path: str = None,
    ) -> Dict[str, Any]:
        """计算离子电导率

        使用Green-Kubo方法计算离子电导率。首先从带电荷轨迹中计算电流密度，
        然后计算电流自相关函数并积分得到电导率。如果GK方法不收敛，
        尝试使用Einstein方法（基于MSD数据）。

        计算流程:
            1. 加载带电荷轨迹文件
            2. 计算每帧的电流密度 J = Σ(qi*vi)
            3. 计算电流密度自相关函数 ⟨J(0)·J(t)⟩
            4. 对自相关函数进行时间积分
            5. 转换为SI单位得到电导率
            6. 检查收敛性，不收敛时回退到Einstein方法

        Args:
            charge_trajectory_path: 带电荷轨迹文件路径（dump.charge.lammpstrj）
            temperature: 系统温度，单位K
            volume_angstrom3: 系统体积，单位Å³
            msd_path: MSD数据文件路径，用于Einstein方法回退，可选

        Returns:
            计算结果字典，包含:
            - property_name: 属性名称
            - property_value: 属性值（S/m）
            - property_unit: 属性单位
            - calculation_method: 计算方法
            - convergence_status: 收敛状态
            - property_detail: 详细统计信息
            - sub_table_data: 子表数据（电导率张量、离子贡献等）
            - chart_data: 图表数据（电导率曲线）
        """
        logger.info(
            "[ConductivityCalculator] 开始计算电导率, T=%.1f K, V=%.1f Å³",
            temperature, volume_angstrom3,
        )

        # 物理常数
        kB = self.pp_config.BOLTZMANN_CONSTANT_SI  # J/K
        e = self.pp_config.ELEMENTARY_CHARGE  # C
        angstrom_to_m = self.pp_config.ANGSTROM_TO_METER  # m/Å
        ps_to_s = self.pp_config.PICOSECOND_TO_SECOND  # s/ps

        try:
            # 加载带电荷轨迹
            universe = self.loader.load_lammps_trajectory_with_charges(
                charge_trajectory_path
            )
        except Exception as exc:
            logger.error("[ConductivityCalculator] 加载电荷轨迹失败: %s", exc)
            return self._empty_result(f"加载电荷轨迹失败: {exc}")

        # ========== 全面异常处理：捕获后续计算中的所有异常 ==========
        try:
            # 提取原子电荷和速度信息
            n_frames = len(universe.trajectory)
            logger.info("[ConductivityCalculator] 轨迹共 %d 帧", n_frames)

            # 计算每帧的电流密度 J = Σ(qi*vi)
            # J的形状为(n_frames, 3)，单位为 e*Å/ps
            current_density = self._compute_current_density(universe)

            if current_density is None or len(current_density) < 10:
                logger.error("[ConductivityCalculator] 电流密度数据不足")
                return self._empty_result("电流密度数据不足")

            # 计算电流密度自相关函数
            # 对J的三个分量分别计算自相关，然后求和
            max_lag = min(
                self.pp_config.GK_CONDUCTIVITY_CORRELATION_TIME,
                len(current_density),
            )
            logger.info(
                "[ConductivityCalculator] 计算电流自相关，max_lag=%d", max_lag
            )

            # 分别计算Jx, Jy, Jz的自相关
            jx_acf = self.gpu.gpu_autocorrelation(current_density[:, 0], max_lag=max_lag)
            jy_acf = self.gpu.gpu_autocorrelation(current_density[:, 1], max_lag=max_lag)
            jz_acf = self.gpu.gpu_autocorrelation(current_density[:, 2], max_lag=max_lag)

            # 总自相关 = ⟨Jx(0)Jx(t)⟩ + ⟨Jy(0)Jy(t)⟩ + ⟨Jz(0)Jz(t)⟩
            # 需要乘以各自的方差（因为gpu_autocorrelation返回归一化结果）
            jx_var = float(self.gpu.gpu_mean(current_density[:, 0] ** 2))
            jy_var = float(self.gpu.gpu_mean(current_density[:, 1] ** 2))
            jz_var = float(self.gpu.gpu_mean(current_density[:, 2] ** 2))

            total_acf = jx_acf * jx_var + jy_acf * jy_var + jz_acf * jz_var

            # 计算累积积分（running integral）
            # 使用cumulative_trapezoid实现O(n)增量积分，替代原先O(n²)的逐次从头积分循环
            time_axis = np.arange(len(total_acf), dtype=np.float64) * self.pp_config.GK_CONDUCTIVITY_SAMPLING_RATE
            running_integral = np.zeros(len(total_acf))
            running_integral[1:] = cumulative_trapezoid(total_acf, time_axis)

            # 转换为SI单位
            # σ = e²/(3*V*kB*T) * ∫⟨J(0)·J(t)⟩dt
            # J单位: e*Å/ps, V单位: Å³, dt单位: ps
            # 转换因子: e²/(Å*ps) → S/m = e² * 1e10 * 1e12 = e² * 1e22
            prefactor = e ** 2 / (3.0 * volume_angstrom3 * kB * temperature)
            # 积分结果的单位是 (e*Å/ps)² * ps = e²*Å²/ps
            # 需要乘以 Å²/ps → m²/s 的转换: (1e-10)² / 1e-12 = 1e-8
            # 综合转换: prefactor * integral * (Å²→m²) / (ps→s) / Å³→m³
            # 完整: e²/(3*V*kB*T) * integral * (1e-10)² / (1e-12) / (1e-10)³
            #      = e²/(3*V*kB*T) * integral * 1e-20 / 1e-12 / 1e-30
            #      = e²/(3*V*kB*T) * integral * 1e22
            conversion_factor = angstrom_to_m ** 2 / ps_to_s / (angstrom_to_m ** 3)
            conductivity_integral = running_integral * prefactor * conversion_factor

            # 取最终积分值作为电导率
            sigma = float(conductivity_integral[-1])

            # 收敛性检查
            convergence_status = self.convergence_checker.check_gk_convergence(
                conductivity_integral,
                window_size=self.pp_config.GK_INTEGRAL_CONVERGENCE_WINDOW,
            )

            # 如果GK不收敛，尝试Einstein方法
            calculation_method = "Green-Kubo"
            if convergence_status == "NOT_CONVERGED" and msd_path is not None:
                logger.info("[ConductivityCalculator] GK方法未收敛，尝试Einstein方法")
                einstein_result = self._einstein_fallback(msd_path, temperature, volume_angstrom3)
                if einstein_result is not None:
                    sigma = einstein_result
                    calculation_method = "Einstein"
                    convergence_status = "CONVERGED"
                    logger.info(
                        "[ConductivityCalculator] Einstein方法电导率: %.6f S/m",
                        sigma,
                    )

            # 计算标准误差（使用block averaging近似）
            standard_error = self._estimate_standard_error(conductivity_integral)

            # 电导率张量分量
            sigma_xx = float(jx_acf[-1] * jx_var * prefactor * conversion_factor) if len(jx_acf) > 0 else 0.0
            sigma_yy = float(jy_acf[-1] * jy_var * prefactor * conversion_factor) if len(jy_acf) > 0 else 0.0
            sigma_zz = float(jz_acf[-1] * jz_var * prefactor * conversion_factor) if len(jz_acf) > 0 else 0.0

            # 电阻率：电导率为0时电阻率无法计算，使用None（JSON的null）避免Infinity序列化问题
            # 注意：Python的json.dumps会将float('inf')序列化为"Infinity"，这不是有效JSON，
            # 会导致Java的Jackson解析器报错
            resistivity = 1.0 / sigma if sigma > 0 else None

            result = {
                "property_name": "conductivity",
                "property_value": round(sigma, 6),
                "property_unit": "S/m",
                "calculation_method": calculation_method,
                "convergence_status": convergence_status,
                "property_detail": {
                    "mean": round(sigma, 6),
                    "standard_error": round(standard_error, 6),
                    "correlation_time": max_lag,
                    "integral_convergence": convergence_status,
                },
                "sub_table_data": {
                    "conductivity_tensor": {
                        "xx": round(sigma_xx, 6),
                        "yy": round(sigma_yy, 6),
                        "zz": round(sigma_zz, 6),
                    },
                    "ion_contribution": self._compute_ion_contribution(universe),
                    "electric_field_strength": 0.0,
                    # 修复：当resistivity为None时，直接返回None而不是尝试round(None)
                    # 这避免了"type NoneType doesn't define __round__ method"错误
                    "resistivity": round(resistivity, 6) if resistivity is not None else None,
                },
                "chart_data": {
                    "conductivity_curve": {
                        "time": time_axis.tolist(),
                        "integral_value": conductivity_integral.tolist(),
                    },
                },
            }

            logger.info(
                "[ConductivityCalculator] 电导率计算完成: %.6f S/m, 方法=%s, 收敛=%s",
                sigma, calculation_method, convergence_status,
            )
            return result

        except Exception as exc:
            # 捕获计算过程中的所有异常（如None值、NaN值、除零错误等）
            logger.error("[ConductivityCalculator] 电导率计算异常: %s", exc, exc_info=True)
            return self._empty_result(f"计算异常: {exc}")

    def _compute_current_density(self, universe: Any) -> Optional[np.ndarray]:
        """计算每帧的电流密度

        电流密度 J = Σ(qi*vi)，其中qi为原子电荷，vi为原子速度。
        从带电荷轨迹中提取电荷和速度数据。

        Args:
            universe: MDAnalysis.Universe对象，包含电荷和速度信息

        Returns:
            电流密度数组，形状为(n_frames, 3)，单位为e*Å/ps；
            如果数据不足返回None
        """
        n_frames = len(universe.trajectory)
        n_atoms = len(universe.atoms)
        current_density = np.zeros((n_frames, 3))

        for frame_idx, ts in enumerate(universe.trajectory):
            # 获取原子电荷
            if hasattr(ts, 'charges') and ts.charges is not None:
                charges = ts.charges
            elif hasattr(universe.atoms, 'charges'):
                charges = universe.atoms.charges
            else:
                # 如果没有电荷信息，尝试从type推断
                logger.warning(
                    "[ConductivityCalculator] 第%d帧无电荷信息，使用零电荷", frame_idx
                )
                charges = np.zeros(n_atoms)

            # 获取原子速度
            if hasattr(ts, 'velocities') and ts.velocities is not None:
                velocities = ts.velocities
            else:
                logger.warning(
                    "[ConductivityCalculator] 第%d帧无速度信息", frame_idx
                )
                continue

            # J = Σ(qi*vi)
            charges_arr = self.gpu.as_array(charges.reshape(-1, 1))
            vel_arr = self.gpu.as_array(velocities)
            j_frame = self.gpu.as_numpy(self.gpu._xp.sum(charges_arr * vel_arr, axis=0))
            current_density[frame_idx] = j_frame

        logger.info(
            "[ConductivityCalculator] 电流密度计算完成，%d 帧", n_frames
        )
        return current_density

    def _einstein_fallback(
        self, msd_path: str, temperature: float, volume_angstrom3: float
    ) -> Optional[float]:
        """Einstein方法回退计算电导率

        当Green-Kubo方法不收敛时，使用Einstein关系从MSD数据计算电导率。
        σ = e²/(6*V*kB*T) * lim(t→∞) dMSD_ion/dt

        Args:
            msd_path: MSD数据文件路径
            temperature: 系统温度（K）
            volume_angstrom3: 系统体积（Å³）

        Returns:
            电导率值（S/m），如果计算失败返回None
        """
        msd_path = Path(msd_path)
        if not msd_path.exists():
            logger.warning("[ConductivityCalculator] MSD文件不存在: %s", msd_path)
            return None

        try:
            msd_data = np.loadtxt(str(msd_path))
            if msd_data.ndim != 2 or msd_data.shape[1] < 2:
                logger.warning("[ConductivityCalculator] MSD数据格式不正确")
                return None

            time_col = msd_data[:, 0]
            msd_col = msd_data[:, 1]

            # 取后半段数据做线性拟合
            n_half = len(time_col) // 2
            time_half = time_col[n_half:]
            msd_half = msd_col[n_half:]

            # 线性拟合 dMSD/dt
            coeffs = np.polyfit(time_half, msd_half, 1)
            slope = coeffs[0]  # dMSD/dt，单位 Å²/ps

            # 转换为SI单位
            kB = self.pp_config.BOLTZMANN_CONSTANT_SI
            e = self.pp_config.ELEMENTARY_CHARGE
            angstrom_to_m = self.pp_config.ANGSTROM_TO_METER
            ps_to_s = self.pp_config.PICOSECOND_TO_SECOND

            # σ = e²/(6*V*kB*T) * dMSD/dt
            # dMSD/dt 单位: Å²/ps → m²/s: (1e-10)² / 1e-12 = 1e-8
            slope_si = slope * (angstrom_to_m ** 2) / ps_to_s
            volume_si = volume_angstrom3 * (angstrom_to_m ** 3)

            sigma = (e ** 2 * slope_si) / (6.0 * volume_si * kB * temperature)

            logger.info(
                "[ConductivityCalculator] Einstein方法: dMSD/dt=%.6f Å²/ps, σ=%.6f S/m",
                slope, sigma,
            )
            return sigma

        except Exception as exc:
            logger.error("[ConductivityCalculator] Einstein方法计算失败: %s", exc)
            return None

    def _compute_ion_contribution(self, universe: Any) -> Dict[str, float]:
        """计算各离子类型对电导率的贡献比例

        Args:
            universe: MDAnalysis.Universe对象

        Returns:
            离子贡献字典，键为离子类型，值为贡献比例
        """
        ion_contribution = {}
        try:
            atom_types = self.loader.get_atom_types(universe)
            total_atoms = len(universe.atoms)
            for ion_type in self.pp_config.ION_TYPES:
                if ion_type in atom_types:
                    ion_atoms = self.loader.get_atom_selection(universe, ion_type)
                    ion_contribution[ion_type] = len(ion_atoms) / total_atoms
        except Exception as exc:
            logger.warning("[ConductivityCalculator] 计算离子贡献失败: %s", exc)
        return ion_contribution

    def _estimate_standard_error(self, integral_values: np.ndarray) -> float:
        """估计电导率的标准误差

        使用block averaging方法估计Green-Kubo积分的标准误差。

        Args:
            integral_values: GK积分的时间序列

        Returns:
            标准误差估计值
        """
        n = len(integral_values)
        if n < 20:
            return 0.0

        # 将积分值分为若干块，计算块间方差
        n_blocks = max(5, n // 50)
        block_size = n // n_blocks
        block_means = []

        for i in range(n_blocks):
            start = i * block_size
            end = start + block_size
            block_mean = float(self.gpu.gpu_mean(integral_values[start:end]))
            block_means.append(block_mean)

        block_means = np.array(block_means)
        std_error = float(self.gpu.gpu_std(block_means)) / np.sqrt(len(block_means))
        return std_error

    def _empty_result(self, reason: str) -> Dict[str, Any]:
        """生成空结果字典

        Args:
            reason: 空结果原因说明

        Returns:
            包含默认空值的计算结果字典
        """
        return {
            "property_name": "conductivity",
            "property_value": None,
            "property_unit": "S/m",
            "calculation_method": "Green-Kubo",
            "convergence_status": "NOT_CONVERGED",
            "property_detail": {
                "mean": None,
                "standard_error": None,
                "correlation_time": 0,
                "integral_convergence": "NOT_CONVERGED",
            },
            "sub_table_data": {
                "conductivity_tensor": {"xx": None, "yy": None, "zz": None},
                "ion_contribution": {},
                "electric_field_strength": None,
                "resistivity": None,
            },
            "chart_data": {
                "conductivity_curve": {"time": [], "integral_value": []},
            },
            "error": reason,
        }


class ViscosityCalculator:
    """粘度计算器

    基于Green-Kubo方法计算剪切粘度，通过应力张量非对角分量的
    自相关函数时间积分得到粘度。

    Green-Kubo公式:
        η = V/(3*kB*T) * ∫⟨Pαβ(0)·Pαβ(t)⟩dt

    其中Pαβ为应力张量的非对角分量（pxy, pxz, pyz），V为体积，
    kB为玻尔兹曼常数，T为温度。

    属性:
        pp_config: 后处理配置参数
        gpu: GPU加速计算后端
        convergence_checker: 收敛性检查器

    使用示例:
        >>> calc = ViscosityCalculator()
        >>> result = calc.calculate(
        ...     pressure_path="pressure.dat",
        ...     temperature=300,
        ...     volume_angstrom3=50000.0,
        ... )
    """

    def __init__(self, pp_config: Optional[PostProcessingConfig] = None):
        """初始化粘度计算器

        Args:
            pp_config: 后处理配置参数，为None时使用全局config中的配置
        """
        self.pp_config = pp_config or config.post_processing
        self.gpu = GPUBackend(use_gpu=self.pp_config.USE_GPU)
        self.convergence_checker = ConvergenceChecker()
        logger.info("[ViscosityCalculator] 初始化完成，GPU后端: %s", self.gpu.backend_name)

    def calculate(
        self,
        pressure_path: str,
        temperature: float,
        volume_angstrom3: float,
    ) -> Dict[str, Any]:
        """计算剪切粘度

        从pressure.dat文件中读取应力张量数据，计算非对角分量的自相关函数，
        并通过Green-Kubo积分得到剪切粘度。

        计算流程:
            1. 解析pressure.dat文件（6列：pxy, pxz, pyz及3个额外分量）
            2. 计算各非对角分量的自相关函数
            3. 对自相关函数进行时间积分
            4. 转换为SI单位得到粘度
            5. 检查收敛性

        Args:
            pressure_path: pressure.dat文件路径
            temperature: 系统温度，单位K
            volume_angstrom3: 系统体积，单位Å³

        Returns:
            计算结果字典，包含:
            - property_name: 属性名称
            - property_value: 属性值（mPa·s）
            - property_unit: 属性单位
            - calculation_method: 计算方法
            - convergence_status: 收敛状态
            - property_detail: 详细统计信息
            - sub_table_data: 子表数据（粘度值Pa·s、剪切率等）
            - chart_data: 图表数据（粘度曲线）
        """
        logger.info(
            "[ViscosityCalculator] 开始计算粘度, T=%.1f K, V=%.1f Å³",
            temperature, volume_angstrom3,
        )

        # 物理常数
        kB = self.pp_config.BOLTZMANN_CONSTANT_SI  # J/K
        bar_to_pa = self.pp_config.BAR_TO_PASCAL  # Pa/bar
        angstrom_to_m = self.pp_config.ANGSTROM_TO_METER  # m/Å

        # 解析pressure.dat文件
        pressure_data = self._parse_pressure_file(pressure_path)
        if pressure_data is None:
            return self._empty_result("解析pressure.dat文件失败")

        # pressure.dat通常包含6列: pxy, pxz, pyz, 及3个额外应力分量
        # 主要使用前3列（非对角分量）
        pxy = pressure_data[:, 0]
        pxz = pressure_data[:, 1]
        pyz = pressure_data[:, 2]

        # 排除平衡阶段
        n_total = len(pxy)
        n_equil = int(n_total * self.pp_config.EQUILIBRATION_FRACTION)
        pxy = pxy[n_equil:]
        pxz = pxz[n_equil:]
        pyz = pyz[n_equil:]

        if len(pxy) < 10:
            logger.error("[ViscosityCalculator] 排除平衡阶段后数据不足")
            return self._empty_result("排除平衡阶段后数据不足")

        # 计算各分量的自相关函数
        max_lag = min(
            self.pp_config.GK_VISCOSITY_CORRELATION_TIME,
            len(pxy),
        )
        logger.info(
            "[ViscosityCalculator] 计算应力自相关，max_lag=%d", max_lag
        )

        acf_pxy = self.gpu.gpu_autocorrelation(pxy, max_lag=max_lag)
        acf_pxz = self.gpu.gpu_autocorrelation(pxz, max_lag=max_lag)
        acf_pyz = self.gpu.gpu_autocorrelation(pyz, max_lag=max_lag)

        # 计算各分量的方差
        var_pxy = float(self.gpu.gpu_mean(pxy ** 2))
        var_pxz = float(self.gpu.gpu_mean(pxz ** 2))
        var_pyz = float(self.gpu.gpu_mean(pyz ** 2))

        # 总自相关 = ⟨Pxy(0)Pxy(t)⟩ + ⟨Pxz(0)Pxz(t)⟩ + ⟨Pyz(0)Pyz(t)⟩
        total_acf = acf_pxy * var_pxy + acf_pxz * var_pxz + acf_pyz * var_pyz

        # 计算累积积分
        # 使用cumulative_trapezoid实现O(n)增量积分，替代原先O(n²)的逐次从头积分循环
        time_axis = np.arange(len(total_acf), dtype=np.float64) * self.pp_config.GK_VISCOSITY_SAMPLING_RATE
        running_integral = np.zeros(len(total_acf))
        running_integral[1:] = cumulative_trapezoid(total_acf, time_axis)

        # 转换为SI单位
        # η = V/(3*kB*T) * ∫⟨P(0)·P(t)⟩dt
        # P单位: bar, V单位: Å³, dt单位: 采样间隔
        # 转换: V(Å³→m³) * P²(bar²→Pa²) * dt(→s)
        volume_si = volume_angstrom3 * (angstrom_to_m ** 3)
        prefactor = volume_si / (3.0 * kB * temperature)

        # 积分结果单位: bar² * 采样间隔
        # 需要转换: bar² → Pa², 采样间隔 → s
        # 假设采样间隔为1 ps
        ps_to_s = self.pp_config.PICOSECOND_TO_SECOND
        viscosity_integral = running_integral * prefactor * (bar_to_pa ** 2) * ps_to_s

        # 取最终积分值作为粘度（Pa·s）
        eta_pa_s = float(viscosity_integral[-1])
        # 转换为mPa·s
        eta_mpa_s = eta_pa_s * 1000.0

        # 收敛性检查
        convergence_status = self.convergence_checker.check_gk_convergence(
            viscosity_integral,
            window_size=self.pp_config.GK_INTEGRAL_CONVERGENCE_WINDOW,
        )

        # 标准误差估计
        standard_error = self._estimate_standard_error(viscosity_integral)
        standard_error_mpa_s = standard_error * 1000.0

        # 运动粘度（假设密度为1.2 g/cm³）
        density_approx = 1.2  # g/cm³
        kinematic_viscosity = eta_pa_s / (density_approx * 1000) if eta_pa_s > 0 else 0.0  # m²/s

        result = {
            "property_name": "viscosity",
            "property_value": round(eta_mpa_s, 6),
            "property_unit": "mPa·s",
            "calculation_method": "Green-Kubo",
            "convergence_status": convergence_status,
            "property_detail": {
                "mean": round(eta_mpa_s, 6),
                "standard_error": round(standard_error_mpa_s, 6),
                "correlation_time": max_lag,
                "integral_convergence": convergence_status,
            },
            "sub_table_data": {
                "viscosity_value": round(eta_pa_s, 10),
                "shear_rate": 0.0,
                "stress_response": round(float(total_acf[0]) * (bar_to_pa ** 2), 6),
                "kinematic_viscosity": round(kinematic_viscosity, 10),
            },
            "chart_data": {
                "viscosity_curve": {
                    "time": time_axis.tolist(),
                    "integral_value": viscosity_integral.tolist(),
                },
            },
        }

        logger.info(
            "[ViscosityCalculator] 粘度计算完成: %.6f mPa·s, 收敛状态=%s",
            eta_mpa_s, convergence_status,
        )
        return result

    def _parse_pressure_file(self, pressure_path: str) -> Optional[np.ndarray]:
        """解析pressure.dat文件

        读取LAMMPS输出的应力张量数据文件，通常包含6列数据。

        Args:
            pressure_path: pressure.dat文件路径

        Returns:
            应力张量数据数组，形状为(N, 6)；
            如果文件不存在或格式错误返回None
        """
        pressure_path = Path(pressure_path)
        if not pressure_path.exists():
            logger.error("[ViscosityCalculator] pressure.dat文件不存在: %s", pressure_path)
            return None

        try:
            data = np.loadtxt(str(pressure_path))
            if data.ndim != 2 or data.shape[1] < 3:
                logger.error(
                    "[ViscosityCalculator] pressure.dat格式不正确，期望至少3列，实际%d列",
                    data.shape[1] if data.ndim == 2 else 0,
                )
                return None

            logger.info(
                "[ViscosityCalculator] 解析pressure.dat完成，%d 行，%d 列",
                data.shape[0], data.shape[1],
            )
            return data

        except Exception as exc:
            logger.error("[ViscosityCalculator] 解析pressure.dat失败: %s", exc)
            return None

    def _estimate_standard_error(self, integral_values: np.ndarray) -> float:
        """估计粘度的标准误差

        使用block averaging方法估计Green-Kubo积分的标准误差。

        Args:
            integral_values: GK积分的时间序列

        Returns:
            标准误差估计值（Pa·s）
        """
        n = len(integral_values)
        if n < 20:
            return 0.0

        n_blocks = max(5, n // 50)
        block_size = n // n_blocks
        block_means = []

        for i in range(n_blocks):
            start = i * block_size
            end = start + block_size
            block_mean = float(self.gpu.gpu_mean(integral_values[start:end]))
            block_means.append(block_mean)

        block_means = np.array(block_means)
        std_error = float(self.gpu.gpu_std(block_means)) / np.sqrt(len(block_means))
        return std_error

    def _empty_result(self, reason: str) -> Dict[str, Any]:
        """生成空结果字典

        Args:
            reason: 空结果原因说明

        Returns:
            包含默认空值的计算结果字典
        """
        return {
            "property_name": "viscosity",
            "property_value": None,
            "property_unit": "mPa·s",
            "calculation_method": "Green-Kubo",
            "convergence_status": "NOT_CONVERGED",
            "property_detail": {
                "mean": None,
                "standard_error": None,
                "correlation_time": 0,
                "integral_convergence": "NOT_CONVERGED",
            },
            "sub_table_data": {
                "viscosity_value": None,
                "shear_rate": None,
                "stress_response": None,
                "kinematic_viscosity": None,
            },
            "chart_data": {
                "viscosity_curve": {"time": [], "integral_value": []},
            },
            "error": reason,
        }


class DielectricCalculator:
    """介电常数计算器

    基于偶极矩涨落方法计算介电常数，通过分析体系总偶极矩的
    统计涨落来获得静态介电常数。

    偶极矩涨落公式:
        ε = 1 + (⟨M²⟩ - ⟨M⟩²) / (3*ε0*V*kB*T)

    其中M为体系总偶极矩，ε0为真空介电常数，V为体积，
    kB为玻尔兹曼常数，T为温度。

    属性:
        pp_config: 后处理配置参数
        gpu: GPU加速计算后端
        convergence_checker: 收敛性检查器

    使用示例:
        >>> calc = DielectricCalculator()
        >>> result = calc.calculate(
        ...     dipole_path="dipole.dat",
        ...     total_dipole_path="total_dipole.dat",
        ...     temperature=300,
        ...     volume_angstrom3=50000.0,
        ... )
    """

    def __init__(self, pp_config: Optional[PostProcessingConfig] = None):
        """初始化介电常数计算器

        Args:
            pp_config: 后处理配置参数，为None时使用全局config中的配置
        """
        self.pp_config = pp_config or config.post_processing
        self.gpu = GPUBackend(use_gpu=self.pp_config.USE_GPU)
        self.convergence_checker = ConvergenceChecker()
        logger.info("[DielectricCalculator] 初始化完成，GPU后端: %s", self.gpu.backend_name)

    def calculate(
        self,
        dipole_path: str,
        total_dipole_path: str,
        temperature: float,
        volume_angstrom3: float,
    ) -> Dict[str, Any]:
        """计算介电常数

        从dipole.dat和total_dipole.dat文件中读取偶极矩数据，
        通过偶极矩涨落方法计算静态介电常数。

        计算流程:
            1. 解析dipole.dat和total_dipole.dat文件
            2. 计算总偶极矩的均值和方差
            3. 应用偶极矩涨落公式计算介电常数
            4. 计算运行平均以观察收敛行为
            5. 检查收敛性

        Args:
            dipole_path: dipole.dat文件路径（分子偶极矩）
            total_dipole_path: total_dipole.dat文件路径（体系总偶极矩）
            temperature: 系统温度，单位K
            volume_angstrom3: 系统体积，单位Å³

        Returns:
            计算结果字典，包含:
            - property_name: 属性名称
            - property_value: 属性值（无量纲）
            - property_unit: 属性单位
            - calculation_method: 计算方法
            - convergence_status: 收敛状态
            - property_detail: 详细统计信息
            - sub_table_data: 子表数据（介电张量、静态介电常数等）
            - chart_data: 图表数据（介电常数曲线）
        """
        logger.info(
            "[DielectricCalculator] 开始计算介电常数, T=%.1f K, V=%.1f Å³",
            temperature, volume_angstrom3,
        )

        # 物理常数
        kB = self.pp_config.BOLTZMANN_CONSTANT_SI  # J/K
        epsilon_0 = 8.854187817e-12  # F/m，真空介电常数
        debye_to_cm = self.pp_config.DEBYE_TO_COULOMB_METER  # C·m/Debye
        angstrom_to_m = self.pp_config.ANGSTROM_TO_METER  # m/Å

        # 解析偶极矩文件
        total_dipole_data = self._parse_dipole_file(total_dipole_path)
        if total_dipole_data is None:
            return self._empty_result("解析total_dipole.dat文件失败")

        # 解析分子偶极矩文件（可选，用于分量分析）
        molecular_dipole_data = self._parse_dipole_file(dipole_path)

        # 排除平衡阶段
        n_total = len(total_dipole_data)
        n_equil = int(n_total * self.pp_config.EQUILIBRATION_FRACTION)
        total_dipole = total_dipole_data[n_equil:]

        if len(total_dipole) < 10:
            logger.error("[DielectricCalculator] 排除平衡阶段后数据不足")
            return self._empty_result("排除平衡阶段后数据不足")

        # 计算总偶极矩的模
        # 假设total_dipole_data形状为(N, 3)或(N, 4)（第一列为时间）
        if total_dipole.shape[1] >= 4:
            # 第一列为时间，后三列为Mx, My, Mz
            mx = total_dipole[:, 1]
            my = total_dipole[:, 2]
            mz = total_dipole[:, 3]
        else:
            # 三列分别为Mx, My, Mz
            mx = total_dipole[:, 0]
            my = total_dipole[:, 1]
            mz = total_dipole[:, 2]

        # 使用GPUBackend计算统计量
        # ⟨M⟩ 各分量
        mean_mx = float(self.gpu.gpu_mean(mx))
        mean_my = float(self.gpu.gpu_mean(my))
        mean_mz = float(self.gpu.gpu_mean(mz))

        # ⟨M²⟩ = ⟨Mx² + My² + Mz²⟩
        m_squared = mx ** 2 + my ** 2 + mz ** 2
        mean_m_squared = float(self.gpu.gpu_mean(m_squared))

        # ⟨M⟩² = ⟨Mx⟩² + ⟨My⟩² + ⟨Mz⟩²
        mean_m_squared_mean = mean_mx ** 2 + mean_my ** 2 + mean_mz ** 2

        # 偶极矩涨落 ⟨M²⟩ - ⟨M⟩²
        fluctuation = mean_m_squared - mean_m_squared_mean

        # 转换为SI单位
        # 假设偶极矩单位为Debye
        fluctuation_si = fluctuation * (debye_to_cm ** 2)
        volume_si = volume_angstrom3 * (angstrom_to_m ** 3)

        # ε = 1 + (⟨M²⟩ - ⟨M⟩²) / (3*ε0*V*kB*T)
        epsilon = 1.0 + fluctuation_si / (3.0 * epsilon_0 * volume_si * kB * temperature)

        # 计算运行平均（用于观察收敛行为）
        n_samples = len(mx)
        running_avg_fluctuation = np.zeros(n_samples)
        for i in range(1, n_samples):
            # 累积计算偶极矩涨落的运行平均
            mx_partial = mx[:i + 1]
            my_partial = my[:i + 1]
            mz_partial = mz[:i + 1]
            mean_mx_p = float(self.gpu.gpu_mean(mx_partial))
            mean_my_p = float(self.gpu.gpu_mean(my_partial))
            mean_mz_p = float(self.gpu.gpu_mean(mz_partial))
            mean_m2_p = float(self.gpu.gpu_mean(mx_partial ** 2 + my_partial ** 2 + mz_partial ** 2))
            fluct_p = mean_m2_p - (mean_mx_p ** 2 + mean_my_p ** 2 + mean_mz_p ** 2)
            fluct_p_si = fluct_p * (debye_to_cm ** 2)
            running_avg_fluctuation[i] = 1.0 + fluct_p_si / (
                3.0 * epsilon_0 * volume_si * kB * temperature
            )

        # 收敛性检查
        convergence_status = self.convergence_checker.check_gk_convergence(
            running_avg_fluctuation,
            window_size=self.pp_config.GK_INTEGRAL_CONVERGENCE_WINDOW,
        )

        # 标准误差估计
        standard_error = self._estimate_standard_error(running_avg_fluctuation)

        # 介电常数张量分量
        # εαα = 1 + (⟨Mα²⟩ - ⟨Mα⟩²) / (ε0*V*kB*T)
        fluct_xx = float(self.gpu.gpu_mean(mx ** 2)) - mean_mx ** 2
        fluct_yy = float(self.gpu.gpu_mean(my ** 2)) - mean_my ** 2
        fluct_zz = float(self.gpu.gpu_mean(mz ** 2)) - mean_mz ** 2

        epsilon_xx = 1.0 + fluct_xx * (debye_to_cm ** 2) / (
            epsilon_0 * volume_si * kB * temperature
        )
        epsilon_yy = 1.0 + fluct_yy * (debye_to_cm ** 2) / (
            epsilon_0 * volume_si * kB * temperature
        )
        epsilon_zz = 1.0 + fluct_zz * (debye_to_cm ** 2) / (
            epsilon_0 * volume_si * kB * temperature
        )

        # 偶极矩数据
        mean_dipole_magnitude = np.sqrt(mean_mx ** 2 + mean_my ** 2 + mean_mz ** 2)

        # 分量贡献
        component_contribution = {
            "x": round(fluct_xx / fluctuation * 100, 2) if fluctuation > 0 else 0.0,
            "y": round(fluct_yy / fluctuation * 100, 2) if fluctuation > 0 else 0.0,
            "z": round(fluct_zz / fluctuation * 100, 2) if fluctuation > 0 else 0.0,
        }

        result = {
            "property_name": "dielectric_constant",
            "property_value": round(epsilon, 6),
            "property_unit": "无量纲",
            "calculation_method": "偶极矩涨落",
            "convergence_status": convergence_status,
            "property_detail": {
                "mean": round(epsilon, 6),
                "standard_error": round(standard_error, 6),
                "correlation_time": self.pp_config.GK_DIELECTRIC_CORRELATION_TIME,
                "integral_convergence": convergence_status,
            },
            "sub_table_data": {
                "dielectric_constant_tensor": {
                    "xx": round(epsilon_xx, 6),
                    "yy": round(epsilon_yy, 6),
                    "zz": round(epsilon_zz, 6),
                },
                "static_dielectric_constant": round(epsilon, 6),
                "dipole_moment_data": {
                    "mean_dipole_magnitude": round(mean_dipole_magnitude, 6),
                    "fluctuation": round(fluctuation, 6),
                    "unit": "Debye",
                },
                "component_contribution": component_contribution,
                "system_size": {
                    "volume_angstrom3": volume_angstrom3,
                    "temperature_K": temperature,
                    "n_samples": n_samples,
                },
            },
            "chart_data": {
                "dielectric_curve": {
                    "time": list(range(n_samples)),
                    "running_average": running_avg_fluctuation.tolist(),
                },
            },
        }

        logger.info(
            "[DielectricCalculator] 介电常数计算完成: %.6f, 收敛状态=%s",
            epsilon, convergence_status,
        )
        return result

    def _parse_dipole_file(self, dipole_path: str) -> Optional[np.ndarray]:
        """解析偶极矩数据文件

        读取LAMMPS输出的偶极矩数据文件，格式为多列数值数据。

        Args:
            dipole_path: 偶极矩数据文件路径

        Returns:
            偶极矩数据数组，形状为(N, M)；
            如果文件不存在或格式错误返回None
        """
        dipole_path = Path(dipole_path)
        if not dipole_path.exists():
            logger.error("[DielectricCalculator] 偶极矩文件不存在: %s", dipole_path)
            return None

        try:
            data = np.loadtxt(str(dipole_path))
            if data.ndim != 2 or data.shape[1] < 3:
                logger.error(
                    "[DielectricCalculator] 偶极矩文件格式不正确，期望至少3列",
                )
                return None

            logger.info(
                "[DielectricCalculator] 解析偶极矩文件完成，%d 行，%d 列",
                data.shape[0], data.shape[1],
            )
            return data

        except Exception as exc:
            logger.error("[DielectricCalculator] 解析偶极矩文件失败: %s", exc)
            return None

    def _estimate_standard_error(self, running_values: np.ndarray) -> float:
        """估计介电常数的标准误差

        使用block averaging方法估计运行平均的标准误差。

        Args:
            running_values: 运行平均时间序列

        Returns:
            标准误差估计值
        """
        n = len(running_values)
        if n < 20:
            return 0.0

        n_blocks = max(5, n // 50)
        block_size = n // n_blocks
        block_means = []

        for i in range(n_blocks):
            start = i * block_size
            end = start + block_size
            block_mean = float(self.gpu.gpu_mean(running_values[start:end]))
            block_means.append(block_mean)

        block_means = np.array(block_means)
        std_error = float(self.gpu.gpu_std(block_means)) / np.sqrt(len(block_means))
        return std_error

    def _empty_result(self, reason: str) -> Dict[str, Any]:
        """生成空结果字典

        Args:
            reason: 空结果原因说明

        Returns:
            包含默认空值的计算结果字典
        """
        return {
            "property_name": "dielectric_constant",
            "property_value": None,
            "property_unit": "无量纲",
            "calculation_method": "偶极矩涨落",
            "convergence_status": "NOT_CONVERGED",
            "property_detail": {
                "mean": None,
                "standard_error": None,
                "correlation_time": 0,
                "integral_convergence": "NOT_CONVERGED",
            },
            "sub_table_data": {
                "dielectric_constant_tensor": {"xx": None, "yy": None, "zz": None},
                "static_dielectric_constant": None,
                "dipole_moment_data": None,
                "component_contribution": None,
                "system_size": None,
            },
            "chart_data": {
                "dielectric_curve": {"time": [], "running_average": []},
            },
            "error": reason,
        }


class SolvationCalculator:
    """溶剂化结构计算器

    基于径向分布函数（RDF）计算溶剂化结构，包括配位数、
    特征峰位置和溶剂化壳层结构。

    计算方法:
        1. 对每种离子类型，计算其与溶剂原子之间的RDF
        2. 对RDF进行积分得到配位数: n = 4π*ρ*∫r²*g(r)dr
        3. 识别RDF中的特征峰

    属性:
        pp_config: 后处理配置参数
        gpu: GPU加速计算后端
        loader: 轨迹文件加载器
        convergence_checker: 收敛性检查器

    使用示例:
        >>> calc = SolvationCalculator()
        >>> result = calc.calculate(
        ...     solvation_trajectory_path="dump.trajectory.lammpstrj",
        ... )
    """

    def __init__(self, pp_config: Optional[PostProcessingConfig] = None):
        """初始化溶剂化结构计算器

        Args:
            pp_config: 后处理配置参数，为None时使用全局config中的配置
        """
        self.pp_config = pp_config or config.post_processing
        self.gpu = GPUBackend(use_gpu=self.pp_config.USE_GPU)
        self.loader = TrajectoryLoader()
        self.convergence_checker = ConvergenceChecker()
        logger.info("[SolvationCalculator] 初始化完成，GPU后端: %s", self.gpu.backend_name)

    def calculate(
        self,
        solvation_trajectory_path: str,
        atom_types_info: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        """计算溶剂化结构

        从轨迹文件中计算各离子类型与溶剂原子之间的RDF，
        并通过积分RDF得到配位数。

        计算流程:
            1. 加载轨迹文件
            2. 对每种离子类型，计算与溶剂原子的RDF
            3. 积分RDF得到配位数
            4. 识别RDF特征峰
            5. 检查RDF收敛性

        Args:
            solvation_trajectory_path: 轨迹文件路径
            atom_types_info: 原子类型信息字典，可选。
                           包含离子和溶剂原子的类型映射。

        Returns:
            计算结果字典，包含:
            - property_name: 属性名称
            - property_value: 属性值（平均配位数）
            - property_unit: 属性单位
            - calculation_method: 计算方法
            - convergence_status: 收敛状态
            - property_detail: 详细统计信息
            - sub_table_data: 子表数据（中心离子类型、溶剂化壳层等）
            - chart_data: 图表数据（RDF曲线）
        """
        logger.info(
            "[SolvationCalculator] 开始计算溶剂化结构, 轨迹: %s",
            solvation_trajectory_path,
        )

        try:
            # 加载轨迹
            universe = self.loader.load_lammps_trajectory(solvation_trajectory_path)
        except Exception as exc:
            logger.error("[SolvationCalculator] 加载轨迹失败: %s", exc)
            return self._empty_result(f"加载轨迹失败: {exc}")

        # 获取原子类型
        atom_types = self.loader.get_atom_types(universe)
        logger.info("[SolvationCalculator] 发现原子类型: %s", atom_types)

        # 确定离子类型和溶剂原子类型
        ion_types = self.pp_config.ION_TYPES
        solvent_atom_types = self.pp_config.SOLVENT_ATOM_TYPES

        # 只保留轨迹中实际存在的离子类型
        available_ion_types = [ion for ion in ion_types if ion in atom_types]
        if not available_ion_types:
            logger.warning("[SolvationCalculator] 轨迹中未找到配置的离子类型")
            available_ion_types = [atom_types[0]] if atom_types else []

        # 对每种离子-溶剂对计算RDF
        all_rdf_results = {}
        all_coordination_numbers = []
        chart_data_rdf = {}

        for ion_type in available_ion_types:
            for solvent_type in solvent_atom_types:
                if solvent_type not in atom_types:
                    continue

                pair_key = f"{ion_type}-{solvent_type}"
                logger.info(
                    "[SolvationCalculator] 计算RDF: %s", pair_key
                )

                try:
                    rdf_result = self._compute_rdf(
                        universe, ion_type, solvent_type
                    )
                    if rdf_result is not None:
                        all_rdf_results[pair_key] = rdf_result
                        all_coordination_numbers.append(
                            rdf_result["coordination_number"]
                        )
                        chart_data_rdf[pair_key] = {
                            "r": rdf_result["r"].tolist(),
                            "g_r": rdf_result["g_r"].tolist(),
                        }
                except Exception as exc:
                    logger.warning(
                        "[SolvationCalculator] 计算RDF %s 失败: %s",
                        pair_key, exc,
                    )

        if not all_rdf_results:
            logger.error("[SolvationCalculator] 未能计算任何RDF")
            return self._empty_result("未能计算任何RDF")

        # 计算平均配位数
        avg_coordination = float(self.gpu.gpu_mean(
            np.array(all_coordination_numbers)
        )) if all_coordination_numbers else 0.0

        # 收敛性检查（使用第一个RDF结果）
        first_rdf = list(all_rdf_results.values())[0]
        convergence_status = self.convergence_checker.check_rdf_convergence(
            first_rdf["g_r"], threshold=0.05
        )

        # 标准误差
        if len(all_coordination_numbers) > 1:
            std_error = float(self.gpu.gpu_std(
                np.array(all_coordination_numbers)
            )) / np.sqrt(len(all_coordination_numbers))
        else:
            std_error = 0.0

        # 构建溶剂化壳层结构
        solvation_shells = {}
        for pair_key, rdf_data in all_rdf_results.items():
            solvation_shells[pair_key] = {
                "coordination_number": round(rdf_data["coordination_number"], 4),
                "first_shell_distance": round(rdf_data["first_peak_position"], 4),
                "first_peak_height": round(rdf_data["first_peak_height"], 4),
            }

        # 取第一个离子类型作为中心离子
        central_ion = available_ion_types[0]

        # 特征峰信息
        rdf_peaks = {}
        for pair_key, rdf_data in all_rdf_results.items():
            rdf_peaks[pair_key] = {
                "first_peak_r": round(rdf_data["first_peak_position"], 4),
                "first_peak_g": round(rdf_data["first_peak_height"], 4),
            }

        result = {
            "property_name": "solvation_structure",
            "property_value": round(avg_coordination, 4),
            "property_unit": "无量纲",
            "calculation_method": "RDF积分",
            "convergence_status": convergence_status,
            "property_detail": {
                "mean_coordination_number": round(avg_coordination, 4),
                "standard_error": round(std_error, 4),
                "sample_size": len(all_rdf_results),
            },
            "sub_table_data": {
                "central_ion_type": central_ion,
                "solvation_shell_structure": solvation_shells,
                "average_coordination_number": round(avg_coordination, 4),
                "coordination_distance": round(
                    first_rdf["first_peak_position"], 4
                ) if first_rdf else None,
                "rdf_characteristic_peak": rdf_peaks,
                "hydrogen_bond_feature": {},
                "solvation_stability": None,  # 溶剂化壳层寿命(ps)，暂未计算
                "convergence_status": convergence_status,  # 收敛状态
                "ion_solvent_interaction_energy": None,
            },
            "chart_data": {
                "rdf_curve": chart_data_rdf,
            },
        }

        logger.info(
            "[SolvationCalculator] 溶剂化结构计算完成: 平均配位数=%.4f, 收敛=%s",
            avg_coordination, convergence_status,
        )
        return result

    def _compute_rdf(
        self, universe: Any, ion_type: str, solvent_type: str
    ) -> Optional[Dict[str, Any]]:
        """计算离子-溶剂原子之间的径向分布函数

        使用MDAnalysis的InterRDF计算RDF，并积分得到配位数。

        Args:
            universe: MDAnalysis.Universe对象
            ion_type: 离子类型名称
            solvent_type: 溶剂原子类型名称

        Returns:
            RDF计算结果字典，包含r, g_r, coordination_number等；
            如果计算失败返回None
        """
        try:
            import MDAnalysis.analysis.rdf as mda_rdf
        except ImportError:
            logger.error(
                "[SolvationCalculator] MDAnalysis.analysis.rdf不可用，尝试手动计算RDF"
            )
            return self._compute_rdf_manual(universe, ion_type, solvent_type)

        try:
            # 选择离子和溶剂原子
            ion_group = self.loader.get_atom_selection(universe, ion_type)
            solvent_group = self.loader.get_atom_selection(universe, solvent_type)

            if len(ion_group) == 0 or len(solvent_group) == 0:
                logger.warning(
                    "[SolvationCalculator] 原子选择为空: %s(%d), %s(%d)",
                    ion_type, len(ion_group), solvent_type, len(solvent_group),
                )
                return None

            # 使用MDAnalysis InterRDF计算RDF
            rdf_obj = mda_rdf.InterRDF(
                ion_group,
                solvent_group,
                nbins=self.pp_config.RDF_NUMBER_OF_BINS,
                range=self.pp_config.RDF_RANGE,
            )
            rdf_obj.run()

            r = rdf_obj.results.bins
            g_r = rdf_obj.results.rdf

            # 计算配位数: n = 4π*ρ*∫r²*g(r)dr
            coordination_number = self._integrate_rdf_for_coordination(
                r, g_r, universe, solvent_type
            )

            # 识别特征峰
            first_peak_position, first_peak_height = self._find_first_peak(r, g_r)

            return {
                "r": r,
                "g_r": g_r,
                "coordination_number": coordination_number,
                "first_peak_position": first_peak_position,
                "first_peak_height": first_peak_height,
            }

        except Exception as exc:
            logger.warning(
                "[SolvationCalculator] MDAnalysis InterRDF计算失败: %s，尝试手动计算",
                exc,
            )
            return self._compute_rdf_manual(universe, ion_type, solvent_type)

    def _compute_rdf_manual(
        self, universe: Any, ion_type: str, solvent_type: str
    ) -> Optional[Dict[str, Any]]:
        """手动计算RDF（当MDAnalysis InterRDF不可用时的回退方案）

        通过遍历轨迹帧，统计离子-溶剂原子距离的直方图来计算RDF。

        Args:
            universe: MDAnalysis.Universe对象
            ion_type: 离子类型名称
            solvent_type: 溶剂原子类型名称

        Returns:
            RDF计算结果字典；如果计算失败返回None
        """
        try:
            ion_group = self.loader.get_atom_selection(universe, ion_type)
            solvent_group = self.loader.get_atom_selection(universe, solvent_type)

            if len(ion_group) == 0 or len(solvent_group) == 0:
                return None

            n_bins = self.pp_config.RDF_NUMBER_OF_BINS
            r_range = self.pp_config.RDF_RANGE
            r_min, r_max = r_range[0], r_range[1]
            dr = (r_max - r_min) / n_bins

            # 初始化直方图
            hist = np.zeros(n_bins)
            r_centers = np.linspace(r_min + dr / 2, r_max - dr / 2, n_bins)

            n_frames = len(universe.trajectory)
            n_ions = len(ion_group)
            n_solvents = len(solvent_group)

            # 遍历轨迹帧
            for ts in universe.trajectory:
                # 获取盒子尺寸
                box = ts.dimensions[:3]

                # 获取离子和溶剂原子坐标
                ion_coords = ion_group.positions
                solvent_coords = solvent_group.positions

                # 使用GPUBackend计算距离
                distances = self.gpu.gpu_pairwise_distances(
                    ion_coords, solvent_coords, box_dimensions=box
                )

                # 统计距离直方图
                flat_dist = distances.flatten()
                bin_indices = ((flat_dist - r_min) / dr).astype(int)
                valid_mask = (bin_indices >= 0) & (bin_indices < n_bins)
                for idx in bin_indices[valid_mask]:
                    hist[idx] += 1

            # 归一化RDF
            # g(r) = hist / (N_ions * N_solvents * n_frames * 4π*r²*dr / V)
            # 计算平均体积
            total_volume = 0.0
            for ts in universe.trajectory:
                box = ts.dimensions[:3]
                total_volume += box[0] * box[1] * box[2]
            avg_volume = total_volume / n_frames

            # 归一化
            rho = n_solvents / avg_volume  # 溶剂数密度
            for i in range(n_bins):
                shell_volume = 4.0 * np.pi * r_centers[i] ** 2 * dr
                normalization = n_ions * n_frames * rho * shell_volume
                if normalization > 0:
                    hist[i] /= normalization

            g_r = hist
            r = r_centers

            # 计算配位数
            coordination_number = self._integrate_rdf_for_coordination(
                r, g_r, universe, solvent_type
            )

            # 识别特征峰
            first_peak_position, first_peak_height = self._find_first_peak(r, g_r)

            return {
                "r": r,
                "g_r": g_r,
                "coordination_number": coordination_number,
                "first_peak_position": first_peak_position,
                "first_peak_height": first_peak_height,
            }

        except Exception as exc:
            logger.error(
                "[SolvationCalculator] 手动RDF计算失败: %s", exc
            )
            return None

    def _integrate_rdf_for_coordination(
        self,
        r: np.ndarray,
        g_r: np.ndarray,
        universe: Any,
        solvent_type: str,
    ) -> float:
        """积分RDF计算配位数

        配位数公式: n = 4π*ρ*∫₀^r_cut r²*g(r)dr
        其中ρ为溶剂数密度，r_cut为截断距离。

        Args:
            r: 距离数组（Å）
            g_r: RDF值数组
            universe: MDAnalysis.Universe对象，用于计算体积
            solvent_type: 溶剂原子类型名称

        Returns:
            配位数
        """
        # 截断距离
        r_cut = self.pp_config.RDF_COORDINATION_CUTOFF

        # 筛选截断距离内的数据
        mask = r <= r_cut
        r_cut_region = r[mask]
        g_r_cut_region = g_r[mask]

        if len(r_cut_region) < 2:
            return 0.0

        # 计算溶剂数密度
        solvent_group = self.loader.get_atom_selection(universe, solvent_type)
        n_solvents = len(solvent_group)

        # 计算平均体积
        total_volume = 0.0
        n_frames = len(universe.trajectory)
        for ts in universe.trajectory:
            box = ts.dimensions[:3]
            total_volume += box[0] * box[1] * box[2]
        avg_volume = total_volume / n_frames if n_frames > 0 else 1.0

        rho = n_solvents / avg_volume  # 数密度，单位 1/Å³

        # 积分: n = 4π*ρ*∫r²*g(r)dr
        integrand = r_cut_region ** 2 * g_r_cut_region
        integral = self.gpu.gpu_trapezoid_integral(integrand, r_cut_region)
        coordination_number = 4.0 * np.pi * rho * integral

        logger.info(
            "[SolvationCalculator] 配位数计算: n=%.4f, ρ=%.6f 1/Å³, r_cut=%.2f Å",
            coordination_number, rho, r_cut,
        )
        return coordination_number

    def _find_first_peak(
        self, r: np.ndarray, g_r: np.ndarray
    ) -> Tuple[float, float]:
        """识别RDF中的第一个特征峰

        在RDF曲线中查找第一个局部最大值作为第一溶剂化壳层的特征峰。

        Args:
            r: 距离数组（Å）
            g_r: RDF值数组

        Returns:
            元组(峰位置, 峰高度)，如果未找到峰返回(0.0, 0.0)
        """
        if len(g_r) < 3:
            return 0.0, 0.0

        # 查找局部最大值
        for i in range(1, len(g_r) - 1):
            if g_r[i] > g_r[i - 1] and g_r[i] > g_r[i + 1] and g_r[i] > 1.0:
                return float(r[i]), float(g_r[i])

        # 如果没有找到明显的峰，返回最大值
        max_idx = int(np.argmax(g_r))
        return float(r[max_idx]), float(g_r[max_idx])

    def _empty_result(self, reason: str) -> Dict[str, Any]:
        """生成空结果字典

        Args:
            reason: 空结果原因说明

        Returns:
            包含默认空值的计算结果字典
        """
        return {
            "property_name": "solvation_structure",
            "property_value": None,
            "property_unit": "无量纲",
            "calculation_method": "RDF积分",
            "convergence_status": "NOT_CONVERGED",
            "property_detail": {
                "mean_coordination_number": None,
                "standard_error": None,
                "sample_size": 0,
            },
            "sub_table_data": {
                "central_ion_type": None,
                "solvation_shell_structure": {},
                "average_coordination_number": None,
                "coordination_distance": None,
                "rdf_characteristic_peak": {},
                "hydrogen_bond_feature": {},
                "solvation_stability": "NOT_CONVERGED",
                "ion_solvent_interaction_energy": None,
            },
            "chart_data": {
                "rdf_curve": {},
            },
            "error": reason,
        }
