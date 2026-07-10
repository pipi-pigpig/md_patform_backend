"""
MDAnalysis轨迹解析与GPU加速计算工具模块

本模块提供基于MDAnalysis的LAMMPS轨迹文件解析、GPU加速数值计算以及LAMMPS日志文件解析功能，
用于后处理分析阶段的数据提取与预处理。

核心功能:
    1. TrajectoryLoader: 基于MDAnalysis的LAMMPS轨迹文件加载与解析
       - 加载标准轨迹文件和带电荷轨迹文件
       - 周期性边界条件去包裹（unwrap）
       - 盒子尺寸提取、原子选择与类型查询
    2. GPUBackend: GPU加速数值计算后端（自动降级）
       - 自动检测并选择最优计算后端: CuPy > PyTorch > NumPy
       - 提供自相关、数值积分、距离计算等GPU加速方法
       - 支持周期性边界条件的最小镜像约定
    3. LogFileParser: LAMMPS日志文件解析器
       - 解析log.lammps中所有run阶段的热力学数据
       - 自动区分minimization/equilibrium/production阶段
       - 提取生产阶段数据（排除平衡阶段）

关键特性:
    - GPU后端自动降级: CuPy不可用时自动切换到PyTorch，再降级到NumPy
    - FFT加速自相关计算: 使用IFFT(|FFT(data)|^2)/N方法，远快于直接计算
    - 最小镜像约定: 距离计算支持周期性边界条件
    - 多run段解析: 自动识别LAMMPS日志中的多个热力学输出段

使用示例:
    >>> from utils.mdanalysis_utils import TrajectoryLoader, GPUBackend, LogFileParser
    >>> # 加载轨迹
    >>> loader = TrajectoryLoader()
    >>> universe = loader.load_lammps_trajectory("dump.trajectory.lammpstrj")
    >>> # GPU加速计算
    >>> gpu = GPUBackend(use_gpu=True)
    >>> autocorr = gpu.gpu_autocorrelation(data, max_lag=1000)
    >>> # 解析日志
    >>> parser = LogFileParser()
    >>> thermo_data = parser.parse_log_lammps("log.lammps")
    >>> production_df = parser.extract_production_data(thermo_data)

作者: 电解液MD平台开发团队
版本: 1.0.0
"""

import logging
import re
import warnings
from pathlib import Path
from typing import Dict, Any, List, Optional, Tuple, Union

import numpy as np

try:
    from ..config.config import config
except ImportError:
    from config import config

logger = logging.getLogger(__name__)


class TrajectoryLoader:
    """LAMMPS轨迹文件加载器

    基于MDAnalysis库加载和解析LAMMPS轨迹文件，提供轨迹数据访问、
    周期性边界条件处理、原子选择等功能。

    本类封装了MDAnalysis.Universe的创建和常用操作，为后处理分析
    提供统一的数据访问接口。

    属性:
        _universe_cache: 已加载的Universe对象缓存，避免重复加载

    使用示例:
        >>> loader = TrajectoryLoader()
        >>> universe = loader.load_lammps_trajectory("dump.trajectory.lammpstrj")
        >>> atom_types = loader.get_atom_types(universe)
        >>> li_atoms = loader.get_atom_selection(universe, "Li")
    """

    def __init__(self):
        """初始化轨迹加载器

        检查MDAnalysis是否可用，并初始化缓存。
        """
        self._universe_cache: Dict[str, Any] = {}
        self._mda = None
        try:
            import MDAnalysis as mda
            self._mda = mda
            logger.info("[TrajectoryLoader] MDAnalysis库加载成功")
        except ImportError:
            logger.error("[TrajectoryLoader] MDAnalysis库未安装，轨迹加载功能不可用")
            raise ImportError(
                "MDAnalysis库未安装，请执行: pip install MDAnalysis"
            )

    def load_lammps_trajectory(self, dump_path: str) -> Any:
        """加载LAMMPS轨迹文件

        使用MDAnalysis加载LAMMPS dump格式的轨迹文件，返回Universe对象。
        支持标准的dump.trajectory.lammpstrj文件。

        Args:
            dump_path: 轨迹文件路径，通常为dump.trajectory.lammpstrj

        Returns:
            MDAnalysis.Universe对象，包含完整的轨迹信息

        Raises:
            FileNotFoundError: 轨迹文件不存在
            ValueError: 文件格式不正确或无法解析
        """
        dump_path = Path(dump_path)
        if not dump_path.exists():
            raise FileNotFoundError(f"[TrajectoryLoader] 轨迹文件不存在: {dump_path}")

        cache_key = str(dump_path.resolve())
        if cache_key in self._universe_cache:
            logger.info("[TrajectoryLoader] 使用缓存的Universe对象: %s", dump_path)
            return self._universe_cache[cache_key]

        logger.info("[TrajectoryLoader] 正在加载轨迹文件: %s", dump_path)
        try:
            universe = self._mda.Universe(str(dump_path), format='LAMMPSDUMP')
            self._universe_cache[cache_key] = universe
            logger.info(
                "[TrajectoryLoader] 轨迹加载成功，共 %d 帧，%d 个原子",
                len(universe.trajectory),
                len(universe.atoms),
            )
            return universe
        except Exception as e:
            logger.error("[TrajectoryLoader] 加载轨迹文件失败: %s", e)
            raise ValueError(f"加载轨迹文件失败: {e}")

    def load_lammps_trajectory_with_charges(self, dump_path: str) -> Any:
        """加载带电荷信息的LAMMPS轨迹文件

        使用MDAnalysis加载包含电荷信息的LAMMPS dump轨迹文件。
        通常用于dump.charge.lammpstrj文件，该文件包含原子的电荷数据。

        Args:
            dump_path: 带电荷的轨迹文件路径，通常为dump.charge.lammpstrj

        Returns:
            MDAnalysis.Universe对象，包含电荷信息的轨迹数据

        Raises:
            FileNotFoundError: 轨迹文件不存在
            ValueError: 文件格式不正确或无法解析
        """
        dump_path = Path(dump_path)
        if not dump_path.exists():
            raise FileNotFoundError(f"[TrajectoryLoader] 电荷轨迹文件不存在: {dump_path}")

        logger.info("[TrajectoryLoader] 正在加载带电荷轨迹文件: %s", dump_path)
        try:
            universe = self._mda.Universe(str(dump_path), format='LAMMPSDUMP')
            logger.info(
                "[TrajectoryLoader] 电荷轨迹加载成功，共 %d 帧，%d 个原子",
                len(universe.trajectory),
                len(universe.atoms),
            )
            return universe
        except Exception as e:
            logger.error("[TrajectoryLoader] 加载电荷轨迹文件失败: %s", e)
            raise ValueError(f"加载电荷轨迹文件失败: {e}")

    def unwrap_coordinates(self, universe: Any) -> np.ndarray:
        """去除周期性边界条件的包裹效应

        将被周期性边界条件包裹（wrap）的原子坐标恢复为连续轨迹，
        消除因周期性边界导致的坐标跳变。通过追踪相邻帧之间的
        原子位移，当位移超过盒子半长时进行修正。

        算法步骤:
            1. 遍历所有帧，计算相邻帧之间每个原子的位移
            2. 如果位移超过盒子半长，说明发生了周期性跳变
            3. 对跳变进行修正，累积位移偏移量
            4. 返回修正后的连续坐标数组

        Args:
            universe: MDAnalysis.Universe对象

        Returns:
            去包裹后的坐标数组，形状为(n_frames, n_atoms, 3)
        """
        n_frames = len(universe.trajectory)
        n_atoms = len(universe.atoms)

        if n_frames == 0:
            logger.warning("[TrajectoryLoader] 轨迹中没有帧数据")
            return np.empty((0, n_atoms, 3))

        logger.info(
            "[TrajectoryLoader] 开始去包裹坐标处理，%d 帧，%d 个原子",
            n_frames, n_atoms,
        )

        # 存储所有帧的坐标和盒子尺寸
        all_coords = np.zeros((n_frames, n_atoms, 3))
        all_boxes = np.zeros((n_frames, 3))

        for frame_idx, ts in enumerate(universe.trajectory):
            all_coords[frame_idx] = ts.positions.copy()
            # LAMMPSDUMP格式的dimensions为(A, B, C)
            all_boxes[frame_idx] = ts.dimensions[:3]

        # 去包裹处理
        unwrapped = np.zeros_like(all_coords)
        unwrapped[0] = all_coords[0].copy()
        cumulative_shift = np.zeros((n_atoms, 3))

        for frame_idx in range(1, n_frames):
            # 计算相邻帧之间的位移
            delta = all_coords[frame_idx] - all_coords[frame_idx - 1]
            box = all_boxes[frame_idx]

            # 最小镜像约定：如果位移超过盒子半长，说明发生了跳变
            for dim in range(3):
                if box[dim] > 0:
                    delta[:, dim] -= box[dim] * np.round(delta[:, dim] / box[dim])

            # 累积修正后的位移
            unwrapped[frame_idx] = unwrapped[frame_idx - 1] + delta

        logger.info("[TrajectoryLoader] 坐标去包裹处理完成")
        return unwrapped

    def extract_box_dimensions(self, universe: Any) -> np.ndarray:
        """提取轨迹中每帧的盒子尺寸

        从MDAnalysis Universe对象中提取所有帧的盒子尺寸信息。

        Args:
            universe: MDAnalysis.Universe对象

        Returns:
            盒子尺寸数组，形状为(n_frames, 6)，
            每行包含[lx, ly, lz, alpha, beta, gamma]
        """
        n_frames = len(universe.trajectory)
        dimensions = np.zeros((n_frames, 6))

        for frame_idx, ts in enumerate(universe.trajectory):
            dimensions[frame_idx] = ts.dimensions

        logger.info(
            "[TrajectoryLoader] 提取盒子尺寸完成，共 %d 帧",
            n_frames,
        )
        return dimensions

    def get_atom_selection(self, universe: Any, atom_type: str) -> Any:
        """选择指定类型的原子

        对于LAMMPSDUMP格式，优先通过type属性选择；
        如果失败，回退到name属性选择。

        LAMMPSDUMP格式中，原子类型通常是整数索引（1, 2, 3等），
        存储在types属性中。其他格式可能使用name属性存储原子名称。

        Args:
            universe: MDAnalysis.Universe对象
            atom_type: 原子类型，可以是整数索引（如"1"、"2"）或名称（如"Li"、"O"）

        Returns:
            MDAnalysis.AtomGroup对象，包含选定类型的所有原子。
            如果选择失败，返回空AtomGroup。
        """
        # 策略1: 首先尝试通过type属性选择（适用于LAMMPSDUMP格式）
        try:
            # 尝试使用MDAnalysis的type选择语法
            selection = universe.select_atoms(f"type {atom_type}")
            if len(selection) > 0:
                logger.info(
                    "[TrajectoryLoader] 通过type属性选择 '%s'，共 %d 个原子",
                    atom_type, len(selection),
                )
                return selection
        except Exception:
            pass

        # 策略2: 如果type选择失败或返回空，尝试通过types属性直接筛选
        try:
            mask = universe.atoms.types == atom_type
            selection = universe.atoms[mask]
            if len(selection) > 0:
                logger.info(
                    "[TrajectoryLoader] 通过types属性筛选 '%s'，共 %d 个原子",
                    atom_type, len(selection),
                )
                return selection
        except Exception:
            pass

        # 策略3: 最后回退到name选择（适用于其他格式）
        try:
            selection = universe.select_atoms(f"name {atom_type}")
            logger.info(
                "[TrajectoryLoader] 通过name属性选择 '%s'，共 %d 个原子",
                atom_type, len(selection),
            )
            return selection
        except Exception:
            logger.warning(
                "[TrajectoryLoader] 无法选择原子类型 '%s'，返回空AtomGroup",
                atom_type,
            )
            # 返回空AtomGroup
            return universe.atoms[[]]

    def get_atom_types(self, universe: Any) -> List[str]:
        """获取所有唯一的原子类型名称

        从Universe中提取所有不同的原子类型名称列表。

        Args:
            universe: MDAnalysis.Universe对象

        Returns:
            原子类型名称列表，如["Li", "O", "C", "H"]
        """
        # 优先使用types属性（LAMMPSDUMP格式）
        if hasattr(universe.atoms, 'types'):
            unique_types = sorted(set(universe.atoms.types))
        else:
            unique_types = sorted(set(universe.atoms.names))

        logger.info(
            "[TrajectoryLoader] 发现 %d 种原子类型: %s",
            len(unique_types), unique_types,
        )
        return unique_types

    def extract_timestep(self, universe: Any) -> np.ndarray:
        """提取轨迹中每帧的时间步

        从Universe对象中提取所有帧的LAMMPS时间步信息。

        Args:
            universe: MDAnalysis.Universe对象

        Returns:
            时间步数组，形状为(n_frames,)，单位为LAMMPS时间步
        """
        timesteps = np.zeros(len(universe.trajectory), dtype=np.int64)
        for frame_idx, ts in enumerate(universe.trajectory):
            timesteps[frame_idx] = ts.data.get("step", frame_idx)

        logger.info(
            "[TrajectoryLoader] 提取时间步完成，范围: %d ~ %d",
            timesteps[0], timesteps[-1],
        )
        return timesteps


class GPUBackend:
    """GPU加速数值计算后端

    提供GPU加速的数值计算方法，自动检测并选择最优计算后端。
    后端优先级: CuPy (CUDA) > PyTorch (CUDA) > NumPy (CPU)。

    本类封装了常用的数值计算方法，包括自相关函数、数值积分、
    距离计算等，并透明地在GPU或CPU上执行计算。

    属性:
        use_gpu: 是否使用GPU加速
        backend_name: 当前使用的后端名称（'cupy'/'torch'/'numpy'）
        _xp: 当前后端的模块引用（cupy/torch/numpy）

    使用示例:
        >>> gpu = GPUBackend(use_gpu=True)
        >>> print(f"当前后端: {gpu.backend_name}")
        >>> autocorr = gpu.gpu_autocorrelation(data, max_lag=1000)
        >>> integral = gpu.gpu_trapezoid_integral(y_values, x_values)
    """

    def __init__(self, use_gpu: bool = True):
        """初始化GPU计算后端

        根据use_gpu参数和实际GPU可用性，自动选择最优计算后端。

        Args:
            use_gpu: 是否尝试使用GPU加速，默认为True。
                     如果GPU不可用，将自动降级到CPU后端。
        """
        self.use_gpu = use_gpu
        self._xp = np
        self._is_cupy = False
        self._is_torch = False
        self.backend_name = "numpy"
        self._torch_module = None

        if use_gpu:
            self.auto_select_backend()
        else:
            logger.info("[GPUBackend] GPU加速已禁用，使用NumPy后端")

    def auto_select_backend(self) -> str:
        """自动选择最优计算后端

        按照优先级 CuPy > PyTorch > NumPy 依次检测可用后端。
        CuPy提供最接近NumPy的API，优先级最高；
        PyTorch作为备选GPU后端；
        NumPy作为CPU兜底方案。

        Returns:
            选择的后端名称（'cupy'/'torch'/'numpy'）
        """
        # 尝试CuPy
        try:
            import cupy as cp
            # 检测CuPy是否能访问GPU
            cp.cuda.runtime.getDeviceCount()
            self._xp = cp
            self._is_cupy = True
            self.backend_name = "cupy"
            logger.info("[GPUBackend] 使用CuPy GPU后端")
            return "cupy"
        except ImportError:
            logger.debug("[GPUBackend] CuPy未安装，尝试PyTorch")
        except Exception as e:
            logger.warning("[GPUBackend] CuPy GPU不可用: %s，尝试PyTorch", e)

        # 尝试PyTorch
        try:
            import torch
            if torch.cuda.is_available():
                self._xp = torch
                self._is_torch = True
                self._torch_module = torch
                self.backend_name = "torch"
                logger.info("[GPUBackend] 使用PyTorch GPU后端")
                return "torch"
            else:
                logger.debug("[GPUBackend] PyTorch CUDA不可用，使用CPU")
        except ImportError:
            logger.debug("[GPUBackend] PyTorch未安装，使用NumPy")

        # 降级到NumPy
        self._xp = np
        self.backend_name = "numpy"
        logger.info("[GPUBackend] 使用NumPy CPU后端")
        return "numpy"

    def as_array(self, data: Union[np.ndarray, list]) -> Any:
        """将数据转换为当前后端的数组

        将输入数据转换为当前计算后端（CuPy/PyTorch/NumPy）的数组格式。
        对于GPU后端，数据会被传输到GPU显存。

        Args:
            data: 输入数据，可以是NumPy数组、列表或其他可转换对象

        Returns:
            当前后端的数组对象（cupy.ndarray/torch.Tensor/numpy.ndarray）
        """
        if self._is_cupy:
            import cupy as cp
            return cp.asarray(data)
        elif self._is_torch:
            return self._torch_module.tensor(
                np.asarray(data), dtype=self._torch_module.float64, device="cuda"
            )
        else:
            return np.asarray(data, dtype=np.float64)

    def as_numpy(self, data: Any) -> np.ndarray:
        """将后端数组转换回NumPy数组

        将CuPy或PyTorch的GPU数组转换回NumPy数组，用于后续的
        CPU端操作或结果保存。

        Args:
            data: 后端数组对象（cupy.ndarray/torch.Tensor/numpy.ndarray）

        Returns:
            NumPy数组
        """
        if self._is_cupy:
            import cupy as cp
            if isinstance(data, cp.ndarray):
                return cp.asnumpy(data)
            return np.asarray(data)
        elif self._is_torch:
            if self._torch_module.is_tensor(data):
                return data.detach().cpu().numpy()
            return np.asarray(data)
        else:
            return np.asarray(data)

    def gpu_autocorrelation(
        self, data: np.ndarray, max_lag: Optional[int] = None
    ) -> np.ndarray:
        """GPU加速的自相关函数计算

        使用FFT方法计算时间序列的自相关函数，相比直接计算
        复杂度从O(N^2)降低到O(N*logN)。

        算法原理:
            autocorr(tau) = IFFT(|FFT(data)|^2) / N

        其中FFT为快速傅里叶变换，IFFT为逆变换。该方法利用了
        循环卷积定理，将自相关计算转化为频域的乘法运算。

        Args:
            data: 输入时间序列数据，形状为(N,)或(N, M)，
                  支持多维数据的沿轴0计算
            max_lag: 最大滞后阶数，默认为None表示计算全部滞后

        Returns:
            自相关函数值数组，形状与输入相同（沿轴0截断到max_lag）
            归一化使得autocorr[0] = 1.0
        """
        xp = self._xp
        data_arr = self.as_array(data)

        # 确保是1D或2D
        original_1d = False
        if data_arr.ndim == 1:
            data_arr = data_arr.reshape(-1, 1)
            original_1d = True

        n = data_arr.shape[0]
        if max_lag is None:
            max_lag = n

        # FFT长度取2的幂次以加速计算
        fft_len = 1
        while fft_len < 2 * n:
            fft_len *= 2

        # 去均值
        mean = xp.mean(data_arr, axis=0)
        data_centered = data_arr - mean

        # 零填充到fft_len
        if self._is_torch:
            padded = xp.zeros((fft_len, data_arr.shape[1]),
                              dtype=xp.float64, device="cuda")
            padded[:n, :] = data_centered
        else:
            padded = xp.zeros((fft_len, data_arr.shape[1]), dtype=xp.float64)
            padded[:n, :] = data_centered

        # FFT计算自相关
        if self._is_torch:
            fft_result = xp.fft.rfft(padded, dim=0)
            power_spectrum = xp.abs(fft_result) ** 2
            autocorr_full = xp.fft.irfft(power_spectrum, n=fft_len, dim=0)
        else:
            fft_result = xp.fft.rfft(padded, axis=0)
            power_spectrum = xp.abs(fft_result) ** 2
            autocorr_full = xp.fft.irfft(power_spectrum, n=fft_len, axis=0)

        # 截取有效部分并归一化
        autocorr = autocorr_full[:max_lag, :]
        variance = autocorr[0:1, :].copy()

        # 避免除以零
        if self._is_torch:
            variance = xp.where(
                xp.abs(variance) < 1e-15,
                xp.ones_like(variance),
                variance,
            )
        else:
            variance[xp.abs(variance) < 1e-15] = 1.0

        autocorr = autocorr / variance

        if original_1d:
            autocorr = autocorr.reshape(-1)

        result = self.as_numpy(autocorr)
        logger.debug(
            "[GPUBackend] 自相关计算完成，max_lag=%d，后端=%s",
            max_lag, self.backend_name,
        )
        return result

    def gpu_trapezoid_integral(
        self, y: np.ndarray, x: Optional[np.ndarray] = None
    ) -> float:
        """GPU加速的梯形数值积分

        使用梯形法则对给定数据进行数值积分。

        公式:
            integral = sum((y[i] + y[i+1]) * (x[i+1] - x[i]) / 2)

        如果未提供x，则假设等间距采样，间距为1。

        Args:
            y: 被积函数值数组，形状为(N,)
            x: 自变量数组，形状为(N,)，默认为None表示等间距

        Returns:
            积分结果（标量值）
        """
        xp = self._xp
        y_arr = self.as_array(y)

        if len(y_arr) < 2:
            logger.warning("[GPUBackend] 积分数据点不足，返回0")
            return 0.0

        if x is not None:
            x_arr = self.as_array(x)
        else:
            x_arr = self.as_array(np.arange(len(y), dtype=np.float64))

        # 梯形法则: sum((y[i] + y[i+1]) * (x[i+1] - x[i]) / 2)
        dx = x_arr[1:] - x_arr[:-1]
        y_avg = (y_arr[1:] + y_arr[:-1]) / 2.0
        integral = xp.sum(y_avg * dx)

        result = float(self.as_numpy(integral))
        logger.debug(
            "[GPUBackend] 梯形积分完成，结果=%.6f，后端=%s",
            result, self.backend_name,
        )
        return result

    def gpu_pairwise_distances(
        self,
        coords1: np.ndarray,
        coords2: np.ndarray,
        box_dimensions: Optional[np.ndarray] = None,
    ) -> np.ndarray:
        """GPU加速的原子对距离计算

        计算两组坐标之间的所有原子对距离，支持周期性边界条件下的
        最小镜像约定（Minimum Image Convention）。

        最小镜像约定:
            对于周期性体系，每个原子只与最近的镜像原子计算距离，
            确保距离不超过盒子半长。

        Args:
            coords1: 第一组原子坐标，形状为(N, 3)
            coords2: 第二组原子坐标，形状为(M, 3)
            box_dimensions: 盒子尺寸，形状为(3,)或(6,)，
                           如果为(6,)则取前三个元素作为lx,ly,lz。
                           None表示非周期性体系。

        Returns:
            距离矩阵，形状为(N, M)，distances[i,j]为
            coords1[i]与coords2[j]之间的距离
        """
        xp = self._xp
        c1 = self.as_array(coords1)
        c2 = self.as_array(coords2)

        # 确保是2D
        if c1.ndim == 1:
            c1 = c1.reshape(1, -1)
        if c2.ndim == 1:
            c2 = c2.reshape(1, -1)

        n1 = c1.shape[0]
        n2 = c2.shape[0]

        # 扩展维度以进行广播计算
        # c1: (N, 1, 3), c2: (1, M, 3)
        if self._is_torch:
            c1_exp = c1.unsqueeze(1)
            c2_exp = c2.unsqueeze(0)
        else:
            c1_exp = c1[:, xp.newaxis, :]
            c2_exp = c2[xp.newaxis, :, :]

        # 计算位移向量
        delta = c1_exp - c2_exp

        # 应用最小镜像约定
        if box_dimensions is not None:
            box = self.as_array(box_dimensions)
            if box.shape[0] >= 3:
                box_lengths = box[:3]
            else:
                box_lengths = box

            if self._is_torch:
                box_lengths = box_lengths.unsqueeze(0).unsqueeze(0)
            else:
                box_lengths = box_lengths[xp.newaxis, xp.newaxis, :]

            # 最小镜像: delta -= box * round(delta / box)
            delta = delta - box_lengths * xp.round(delta / box_lengths)

        # 计算距离
        distances = xp.sqrt(xp.sum(delta ** 2, axis=-1))

        result = self.as_numpy(distances)
        logger.debug(
            "[GPUBackend] 距离计算完成，形状=(%d, %d)，后端=%s",
            n1, n2, self.backend_name,
        )
        return result

    def gpu_mean(
        self, data: np.ndarray, axis: Optional[int] = None
    ) -> Union[float, np.ndarray]:
        """GPU加速的均值计算

        Args:
            data: 输入数据数组
            axis: 计算均值的轴，None表示全局均值

        Returns:
            均值结果
        """
        xp = self._xp
        data_arr = self.as_array(data)
        result = xp.mean(data_arr, axis=axis)
        return self.as_numpy(result)

    def gpu_std(
        self, data: np.ndarray, axis: Optional[int] = None
    ) -> Union[float, np.ndarray]:
        """GPU加速的标准差计算

        Args:
            data: 输入数据数组
            axis: 计算标准差的轴，None表示全局标准差

        Returns:
            标准差结果
        """
        xp = self._xp
        data_arr = self.as_array(data)
        result = xp.std(data_arr, axis=axis)
        return self.as_numpy(result)


class LogFileParser:
    """LAMMPS日志文件解析器

    解析LAMMPS的log.lammps输出文件，提取所有run阶段的热力学数据。
    LAMMPS日志文件通常包含多个run段（如minimization、equilibrium、
    production），每个段都有独立的热力学输出表。

    本解析器能够:
        - 自动识别日志中的所有热力学输出段
        - 解析列标题和数据行
        - 区分不同阶段（minimization/equilibrium/production）
        - 提取生产阶段数据并排除平衡阶段

    属性:
        _run_stage_names: 阶段名称映射，按出现顺序依次命名

    使用示例:
        >>> parser = LogFileParser()
        >>> thermo_data = parser.parse_log_lammps("log.lammps")
        >>> # thermo_data = {'minimization': DataFrame, 'equilibrium': DataFrame, ...}
        >>> production = parser.extract_production_data(thermo_data)
    """

    # 阶段名称，按LAMMPS日志中run段的典型出现顺序
    _run_stage_names = ["minimization", "equilibrium", "production"]

    def __init__(self):
        """初始化日志解析器"""
        pass

    def parse_log_lammps(self, log_path: str) -> Dict[str, Any]:
        """解析LAMMPS日志文件

        解析log.lammps文件，提取所有run阶段的热力学数据。
        每个阶段的数据以pandas DataFrame形式返回。

        LAMMPS日志格式说明:
            日志文件中每个run段的热力学输出由列标题行和数据行组成，
            例如:
                Step Temp E_pair TotEng Press Volume Density
                0 300.0 -1234.5 -1000.0 1.0 1000.0 1.2
                100 298.5 -1235.0 -1001.0 0.9 1000.0 1.21
                ...
            不同run段之间可能被其他输出信息分隔。

        Args:
            log_path: log.lammps文件路径

        Returns:
            字典，键为阶段名称（如'minimization'、'equilibrium'、'production'），
            值为对应阶段的pandas DataFrame。如果阶段数量超过预定义名称，
            额外阶段使用'run_N'命名。

        Raises:
            FileNotFoundError: 日志文件不存在
            ValueError: 文件内容无法解析
        """
        try:
            import pandas as pd
        except ImportError:
            raise ImportError(
                "pandas库未安装，请执行: pip install pandas"
            )

        log_path = Path(log_path)
        if not log_path.exists():
            raise FileNotFoundError(f"[LogFileParser] 日志文件不存在: {log_path}")

        logger.info("[LogFileParser] 正在解析日志文件: %s", log_path)

        try:
            with open(log_path, "r", encoding="utf-8", errors="replace") as f:
                lines = f.readlines()
        except Exception as e:
            logger.error("[LogFileParser] 读取日志文件失败: %s", e)
            raise ValueError(f"读取日志文件失败: {e}")

        # 查找所有热力学输出段
        thermo_sections = self._find_thermo_sections(lines)

        if not thermo_sections:
            logger.warning("[LogFileParser] 未找到热力学数据段")
            return {}

        # 解析每个段的数据
        result = {}
        for idx, (header_start, data_start) in enumerate(thermo_sections):
            # 解析列标题
            columns = self.parse_thermo_header(lines, header_start)
            if not columns:
                logger.warning("[LogFileParser] 第 %d 段列标题解析失败，跳过", idx)
                continue

            # 解析数据
            df = self.parse_thermo_data(lines, data_start, columns)
            if df is None or df.empty:
                logger.warning("[LogFileParser] 第 %d 段数据解析为空，跳过", idx)
                continue

            # 分配阶段名称
            if idx < len(self._run_stage_names):
                stage_name = self._run_stage_names[idx]
            else:
                stage_name = f"run_{idx}"

            result[stage_name] = df
            logger.info(
                "[LogFileParser] 解析阶段 '%s'：共 %d 行数据，列: %s",
                stage_name, len(df), list(df.columns),
            )

        logger.info(
            "[LogFileParser] 日志解析完成，共 %d 个阶段: %s",
            len(result), list(result.keys()),
        )
        return result

    def extract_production_data(
        self,
        thermo_data: Dict[str, Any],
        equilibration_fraction: float = 0.2,
    ) -> Any:
        """提取生产阶段数据

        从解析的热力学数据中提取生产阶段（production）的数据，
        并排除指定比例的平衡阶段数据。

        Args:
            thermo_data: parse_log_lammps返回的热力学数据字典
            equilibration_fraction: 需要排除的平衡阶段比例，
                                   默认0.2表示排除前20%的数据

        Returns:
            生产阶段的pandas DataFrame，已排除平衡阶段数据。
            如果没有production阶段，返回最后一个阶段的数据。
        """
        try:
            import pandas as pd
        except ImportError:
            raise ImportError(
                "pandas库未安装，请执行: pip install pandas"
            )

        # 优先使用production阶段数据
        if "production" in thermo_data:
            df = thermo_data["production"]
        elif thermo_data:
            # 如果没有production阶段，使用最后一个阶段
            last_key = list(thermo_data.keys())[-1]
            logger.warning(
                "[LogFileParser] 未找到production阶段，使用最后阶段 '%s'",
                last_key,
            )
            df = thermo_data[last_key]
        else:
            logger.error("[LogFileParser] 热力学数据为空")
            return pd.DataFrame()

        # 排除平衡阶段数据
        n_total = len(df)
        n_equil = int(n_total * equilibration_fraction)
        n_production = n_total - n_equil

        if n_production <= 0:
            logger.warning(
                "[LogFileParser] 平衡阶段排除比例过大(%.1f%%)，返回全部数据",
                equilibration_fraction * 100,
            )
            return df

        production_df = df.iloc[n_equil:].reset_index(drop=True)
        logger.info(
            "[LogFileParser] 提取生产数据：排除前 %d 行(%.0f%%)，保留 %d 行",
            n_equil, equilibration_fraction * 100, n_production,
        )
        return production_df

    def parse_thermo_header(self, lines: List[str], start_idx: int) -> List[str]:
        """解析热力学输出的列标题

        从LAMMPS日志中解析热力学输出表的列标题行。
        列标题通常为一行由空格分隔的字符串，如:
        "Step Temp E_pair TotEng Press Volume Density"

        Args:
            lines: 日志文件的所有行
            start_idx: 列标题行的起始索引

        Returns:
            列标题名称列表，如['Step', 'Temp', 'E_pair', ...]
        """
        if start_idx >= len(lines):
            return []

        header_line = lines[start_idx].strip()
        # 跳过空行
        while not header_line and start_idx < len(lines) - 1:
            start_idx += 1
            header_line = lines[start_idx].strip()

        columns = header_line.split()

        # 验证列标题是否有效（至少包含Step列）
        if not columns or not any(
            col.lower() in ("step", "step/") for col in columns
        ):
            # 放宽验证：只要第一列看起来像数字索引即可
            if not columns:
                return []

        logger.debug("[LogFileParser] 解析列标题: %s", columns)
        return columns

    def parse_thermo_data(
        self, lines: List[str], start_idx: int, columns: List[str]
    ) -> Any:
        """解析热力学输出的数值数据

        从LAMMPS日志中解析热力学输出表的数值数据行。
        数据行由空格分隔的数值组成，直到遇到非数据行（如LAMMPS提示信息）
        或文件结束。

        Args:
            lines: 日志文件的所有行
            start_idx: 数据行的起始索引
            columns: 列标题名称列表

        Returns:
            pandas DataFrame，包含解析的数值数据
        """
        try:
            import pandas as pd
        except ImportError:
            raise ImportError(
                "pandas库未安装，请执行: pip install pandas"
            )

        data_rows = []
        idx = start_idx

        while idx < len(lines):
            line = lines[idx].strip()

            # 空行跳过
            if not line:
                idx += 1
                continue

            # 检查是否为数据行（所有token都可以转为数值）
            tokens = line.split()
            try:
                row = [float(t) for t in tokens]
            except ValueError:
                # 遇到非数值行，数据段结束
                break

            # 列数必须匹配
            if len(row) != len(columns):
                # 列数不匹配可能是数据段结束标志
                break

            data_rows.append(row)
            idx += 1

        if not data_rows:
            logger.debug("[LogFileParser] 未找到有效数据行")
            return pd.DataFrame(columns=columns)

        df = pd.DataFrame(data_rows, columns=columns)
        logger.debug("[LogFileParser] 解析数据行: %d 行", len(df))
        return df

    def _find_thermo_sections(self, lines: List[str]) -> List[Tuple[int, int]]:
        """查找日志中所有热力学输出段

        在LAMMPS日志文件中定位所有热力学输出表的起始位置。
        热力学输出段由列标题行标识，列标题行通常紧跟在
        "run"命令输出之后。

        识别规则:
            - 列标题行以"Step"开头（LAMMPS默认第一列为Step）
            - 列标题行由纯字母/下划线组成的token构成
            - 列标题行之后紧跟数值数据行

        Args:
            lines: 日志文件的所有行

        Returns:
            列表，每个元素为(header_line_idx, data_start_idx)元组
        """
        sections = []
        idx = 0
        n_lines = len(lines)

        while idx < n_lines:
            line = lines[idx].strip()

            # 查找可能的列标题行
            if line and not line.startswith("#"):
                tokens = line.split()
                # 列标题行的特征：所有token都是字母/下划线，且以Step开头
                if (
                    tokens
                    and tokens[0] == "Step"
                    and all(re.match(r'^[A-Za-z_][A-Za-z0-9_]*$', t) for t in tokens)
                ):
                    header_idx = idx
                    data_start_idx = idx + 1

                    # 跳过可能的空行
                    while data_start_idx < n_lines and not lines[data_start_idx].strip():
                        data_start_idx += 1

                    # 验证下一行是否为数据行
                    if data_start_idx < n_lines:
                        data_line = lines[data_start_idx].strip()
                        data_tokens = data_line.split()
                        try:
                            # 尝试将第一个token转为数值
                            float(data_tokens[0])
                            sections.append((header_idx, data_start_idx))
                        except (ValueError, IndexError):
                            pass

            idx += 1

        logger.debug(
            "[LogFileParser] 找到 %d 个热力学数据段",
            len(sections),
        )
        return sections
