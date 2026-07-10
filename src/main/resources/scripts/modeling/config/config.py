"""
配置模块

定义建模过程中使用的常量和配置参数，包括：
- 建模配置（Config）
- 后处理配置（PostProcessingConfig）
"""

from dataclasses import dataclass, field
from typing import Dict, Any, List
import os


@dataclass
class PostProcessingConfig:
    """后处理分析配置类

    定义MDAnalysis后处理分析过程中使用的参数，包括：
    - 平衡阶段排除比例
    - Green-Kubo方法参数
    - RDF计算参数
    - 收敛阈值
    - GPU加速开关

    Attributes:
        EQUILIBRATION_FRACTION: 平衡阶段排除比例，默认排除前20%数据
        GK_CONDUCTIVITY_CORRELATION_TIME: 电导率Green-Kubo相关时间（帧数）
        GK_CONDUCTIVITY_SAMPLING_RATE: 电导率Green-Kubo采样率
        GK_VISCOSITY_CORRELATION_TIME: 粘度Green-Kubo相关时间（帧数）
        GK_VISCOSITY_SAMPLING_RATE: 粘度Green-Kubo采样率
        GK_DIELECTRIC_CORRELATION_TIME: 介电常数Green-Kubo相关时间（帧数）
        RDF_NUMBER_OF_BINS: RDF计算的bin数量
        RDF_RANGE: RDF计算的距离范围（Angstrom）
        RDF_COORDINATION_CUTOFF: 配位数积分的截断距离（Angstrom）
        DENSITY_CONVERGENCE_THRESHOLD: 密度收敛阈值（std/mean比值）
        GK_INTEGRAL_CONVERGENCE_WINDOW: GK积分收敛判断窗口大小
        USE_GPU: 是否使用GPU加速计算
        ION_TYPES: 离子类型列表，用于电导率和溶剂化计算
        SOLVENT_ATOM_TYPES: 溶剂原子类型列表，用于RDF计算
    """

    EQUILIBRATION_FRACTION: float = 0.2
    GK_CONDUCTIVITY_CORRELATION_TIME: int = 5000
    GK_CONDUCTIVITY_SAMPLING_RATE: int = 100
    GK_VISCOSITY_CORRELATION_TIME: int = 10000
    GK_VISCOSITY_SAMPLING_RATE: int = 10
    GK_DIELECTRIC_CORRELATION_TIME: int = 5000
    RDF_NUMBER_OF_BINS: int = 200
    RDF_RANGE: List[float] = field(default_factory=lambda: [0.0, 6.0])
    RDF_COORDINATION_CUTOFF: float = 2.5
    DENSITY_CONVERGENCE_THRESHOLD: float = 0.05
    GK_INTEGRAL_CONVERGENCE_WINDOW: int = 500
    USE_GPU: bool = True
    ION_TYPES: List[str] = field(default_factory=lambda: ["Li", "PF6", "BF4", "TFSI"])
    SOLVENT_ATOM_TYPES: List[str] = field(default_factory=lambda: ["O", "F"])

    # 默认体积（Å³），当无法从轨迹文件提取盒子尺寸时使用
    DEFAULT_VOLUME_ANGSTROM3: float = 50000.0

    # 物理常数
    BOLTZMANN_CONSTANT_SI: float = 1.380649e-23  # J/K
    ELEMENTARY_CHARGE: float = 1.602176634e-19   # C
    ANGSTROM_TO_METER: float = 1e-10
    PICOSECOND_TO_SECOND: float = 1e-12
    BAR_TO_PASCAL: float = 1e5
    DEBYE_TO_COULOMB_METER: float = 3.33564e-30

    @classmethod
    def from_env(cls) -> "PostProcessingConfig":
        """从环境变量创建后处理配置

        Returns:
            PostProcessingConfig实例
        """
        return cls(
            EQUILIBRATION_FRACTION=float(os.getenv("PP_EQUILIBRATION_FRACTION", "0.2")),
            GK_CONDUCTIVITY_CORRELATION_TIME=int(os.getenv("PP_GK_CONDUCTIVITY_CORR_TIME", "5000")),
            GK_CONDUCTIVITY_SAMPLING_RATE=int(os.getenv("PP_GK_CONDUCTIVITY_SAMPLE_RATE", "100")),
            GK_VISCOSITY_CORRELATION_TIME=int(os.getenv("PP_GK_VISCOSITY_CORR_TIME", "10000")),
            GK_VISCOSITY_SAMPLING_RATE=int(os.getenv("PP_GK_VISCOSITY_SAMPLE_RATE", "10")),
            GK_DIELECTRIC_CORRELATION_TIME=int(os.getenv("PP_GK_DIELECTRIC_CORR_TIME", "5000")),
            RDF_NUMBER_OF_BINS=int(os.getenv("PP_RDF_BINS", "200")),
            USE_GPU=os.getenv("PP_USE_GPU", "true").lower() == "true",
        )


@dataclass
class Config:
    """建模配置类

    Attributes:
        post_processing: 后处理分析配置，默认使用PostProcessingConfig
    """

    DEFAULT_DENSITY: float = 1.2
    DENSITY_UNIT: str = "g/cm³"
    BOX_SCALE_FACTOR: float = 1.3
    AVOGADRO_NUMBER: float = 6.02214076e23

    ANGSTROM_TO_CM: float = 1e-8
    CM3_TO_ANGSTROM3: float = 1e24

    DEFAULT_SOLVENT_DENSITY: float = 1.2

    # 估算盒子尺寸时的默认目标溶剂分子数，用于在未提供box_size时打破循环依赖
    DEFAULT_TARGET_MOLECULE_COUNT: int = 500

    PACKMOL_TOLERANCE: float = 2.0
    PACKMOL_MAX_ITERATIONS: int = 100

    LOG_FORMAT: str = "%(asctime)s - %(name)s - %(levelname)s - %(message)s"
    LOG_LEVEL: str = "INFO"

    MOLECULE_TEMPLATES: Dict[str, Dict[str, Any]] = None
    post_processing: PostProcessingConfig = None
    
    def __post_init__(self):
        if self.MOLECULE_TEMPLATES is None:
            self.MOLECULE_TEMPLATES = {
                "EC": {
                    "name": "Ethylene Carbonate",
                    "formula": "C3H4O3",
                    "molecular_weight": 88.06,
                    "charge": 0,
                    "smiles": "C1COC(=O)O1",
                },
                "EMC": {
                    "name": "Ethyl Methyl Carbonate",
                    "formula": "C4H8O3",
                    "molecular_weight": 104.10,
                    "charge": 0,
                    "smiles": "CCOC(=O)OC",
                },
                "DMC": {
                    "name": "Dimethyl Carbonate",
                    "formula": "C3H6O3",
                    "molecular_weight": 90.08,
                    "charge": 0,
                    "smiles": "COC(=O)OC",
                },
                "DEC": {
                    "name": "Diethyl Carbonate",
                    "formula": "C5H10O3",
                    "molecular_weight": 118.13,
                    "charge": 0,
                    "smiles": "CCOC(=O)OCC",
                },
                "PC": {
                    "name": "Propylene Carbonate",
                    "formula": "C4H6O3",
                    "molecular_weight": 102.09,
                    "charge": 0,
                    "smiles": "CC1COC(=O)O1",
                },
                "Li": {
                    "name": "Lithium Ion",
                    "formula": "Li",
                    "molecular_weight": 6.94,
                    "charge": 1,
                    "smiles": "[Li+]",
                },
                "PF6": {
                    "name": "Hexafluorophosphate",
                    "formula": "PF6",
                    "molecular_weight": 144.96,
                    "charge": -1,
                    "smiles": "F[P-](F)(F)(F)(F)F",
                },
                "BF4": {
                    "name": "Tetrafluoroborate",
                    "formula": "BF4",
                    "molecular_weight": 86.81,
                    "charge": -1,
                    "smiles": "B(F)(F)(F)F",
                },
                "TFSI": {
                    "name": "Bis(trifluoromethanesulfonyl)imide",
                    "formula": "C2F6NO4S2",
                    "molecular_weight": 280.14,
                    "charge": -1,
                    "smiles": "C(F)(F)(F)S(=O)(=O)[N-]S(=O)(=O)C(F)(F)F",
                },
            }
        if self.post_processing is None:
            self.post_processing = PostProcessingConfig()
    
    def get_molecule_info(self, molecule_name: str) -> Dict[str, Any]:
        """获取分子信息
        
        Args:
            molecule_name: 分子名称
            
        Returns:
            分子信息字典
        """
        return self.MOLECULE_TEMPLATES.get(molecule_name, {})
    
    @classmethod
    def from_env(cls) -> "Config":
        """从环境变量创建配置

        Returns:
            Config实例
        """
        return cls(
            DEFAULT_DENSITY=float(os.getenv("MODELING_DEFAULT_DENSITY", "1.2")),
            BOX_SCALE_FACTOR=float(os.getenv("MODELING_BOX_SCALE_FACTOR", "1.3")),
            LOG_LEVEL=os.getenv("MODELING_LOG_LEVEL", "INFO"),
            post_processing=PostProcessingConfig.from_env(),
        )


config = Config()