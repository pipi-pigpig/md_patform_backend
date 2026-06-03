"""
配置模块

定义建模过程中使用的常量和配置参数
"""

from dataclasses import dataclass
from typing import Dict, Any
import os


@dataclass
class Config:
    """建模配置类"""
    
    DEFAULT_DENSITY: float = 1.2
    DENSITY_UNIT: str = "g/cm³"
    BOX_SCALE_FACTOR: float = 1.3
    AVOGADRO_NUMBER: float = 6.02214076e23
    
    ANGSTROM_TO_CM: float = 1e-8
    CM3_TO_ANGSTROM3: float = 1e24
    
    DEFAULT_SOLVENT_DENSITY: float = 1.2
    
    PACKMOL_TOLERANCE: float = 2.0
    PACKMOL_MAX_ITERATIONS: int = 100
    
    LOG_FORMAT: str = "%(asctime)s - %(name)s - %(levelname)s - %(message)s"
    LOG_LEVEL: str = "INFO"
    
    MOLECULE_TEMPLATES: Dict[str, Dict[str, Any]] = None
    
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
        )


config = Config()