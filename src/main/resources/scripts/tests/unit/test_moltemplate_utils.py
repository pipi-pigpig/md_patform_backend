"""
建模模块单元测试

测试modeling模块中的核心功能：
- 分子数量计算
- 盒子尺寸计算
- 电中性验证
- 离子数量调整
- 文件操作工具
- 配置模块
"""

import pytest
import json
import tempfile
import os
from pathlib import Path

# 导入建模模块（使用绝对导入）
import sys
sys.path.insert(0, str(Path(__file__).parent.parent.parent))

from modeling import (
    calculate_molecule_counts,
    calculate_box_size,
    validate_electrical_neutrality,
    adjust_ion_counts,
    MoleculeInfo,
)
from modeling.file_utils import (
    read_formula_json,
    write_result_json,
    read_json,
    write_json,
    file_exists,
    delete_file,
    get_file_size,
    list_files,
    ensure_directory,
)
from modeling.config import Config, config


class TestMoleculeInfo:
    """测试MoleculeInfo数据类"""

    def test_molecule_info_creation(self):
        """测试分子信息创建"""
        mol = MoleculeInfo(
            name="EC",
            formula="C3H4O3",
            molecular_weight=88.06,
            charge=0,
            count=100,
        )
        assert mol.name == "EC"
        assert mol.formula == "C3H4O3"
        assert mol.molecular_weight == 88.06
        assert mol.charge == 0
        assert mol.count == 100

    def test_molecule_info_to_dict(self):
        """测试分子信息转字典"""
        mol = MoleculeInfo(
            name="Li",
            formula="Li",
            molecular_weight=6.94,
            charge=1,
            count=50,
        )
        result = mol.to_dict()
        assert result["name"] == "Li"
        assert result["formula"] == "Li"
        assert result["molecular_weight"] == 6.94
        assert result["charge"] == 1
        assert result["count"] == 50


class TestCalculateMoleculeCounts:
    """测试分子数量计算"""

    def test_basic_calculation(self):
        """基本计算测试"""
        formula_data = {
            "solvent_info": [
                {"name": "EC", "mole_fraction": 0.5, "molecular_weight": 88.06},
                {"name": "DMC", "mole_fraction": 0.5, "molecular_weight": 90.08},
            ],
            "salt_info": {
                "cation": {"name": "Li", "molecular_weight": 6.94, "charge": 1},
                "anion": {"name": "PF6", "molecular_weight": 144.96, "charge": -1},
                "concentration": 1.0,
            },
            "box_size": {"x": 40.0, "y": 40.0, "z": 40.0},
        }
        result = calculate_molecule_counts(formula_data)
        assert result["is_neutral"] == True
        assert len(result["molecules"]) >= 4
        assert result["volume"] == 64000.0

    def test_salt_split(self):
        """锂盐拆分测试"""
        formula_data = {
            "solvent_info": [
                {"name": "EC", "mole_fraction": 1.0, "molecular_weight": 88.06}
            ],
            "salt_info": {
                "cation": {"name": "Li", "molecular_weight": 6.94, "charge": 1},
                "anion": {"name": "PF6", "molecular_weight": 144.96, "charge": -1},
                "concentration": 1.0,
            },
            "box_size": {"x": 40.0, "y": 40.0, "z": 40.0},
        }
        result = calculate_molecule_counts(formula_data)
        li_count = None
        pf6_count = None
        for mol in result["molecules"]:
            if mol["name"] == "Li":
                li_count = mol["count"]
            if mol["name"] == "PF6":
                pf6_count = mol["count"]
        assert li_count is not None
        assert pf6_count is not None
        assert li_count == pf6_count

    def test_charge_balance(self):
        """电荷平衡测试"""
        formula_data = {
            "solvent_info": [
                {"name": "EC", "mole_fraction": 1.0, "molecular_weight": 88.06}
            ],
            "salt_info": {
                "cation": {"name": "Li", "molecular_weight": 6.94, "charge": 1},
                "anion": {"name": "PF6", "molecular_weight": 144.96, "charge": -1},
                "concentration": 1.0,
            },
            "box_size": {"x": 40.0, "y": 40.0, "z": 40.0},
        }
        result = calculate_molecule_counts(formula_data)
        assert result["total_charge"] == 0

    def test_missing_box_size(self):
        """测试缺少盒子尺寸"""
        formula_data = {
            "solvent_info": [{"name": "EC", "mole_fraction": 1.0}],
            "salt_info": {
                "cation": {"name": "Li"},
                "anion": {"name": "PF6"},
                "concentration": 1.0,
            },
        }
        with pytest.raises(ValueError, match="缺少盒子尺寸参数"):
            calculate_molecule_counts(formula_data)

    def test_invalid_box_size(self):
        """测试无效盒子尺寸"""
        formula_data = {
            "solvent_info": [{"name": "EC", "mole_fraction": 1.0}],
            "box_size": {"x": -10.0, "y": 40.0, "z": 40.0},
        }
        with pytest.raises(ValueError, match="盒子尺寸必须为正数"):
            calculate_molecule_counts(formula_data)

    def test_zero_box_size(self):
        """测试零盒子尺寸"""
        formula_data = {
            "solvent_info": [{"name": "EC", "mole_fraction": 1.0}],
            "box_size": {"x": 0, "y": 40.0, "z": 40.0},
        }
        with pytest.raises(ValueError, match="盒子尺寸必须为正数"):
            calculate_molecule_counts(formula_data)

    def test_custom_density(self):
        """测试自定义密度"""
        formula_data = {
            "solvent_info": [
                {"name": "EC", "mole_fraction": 1.0, "molecular_weight": 88.06}
            ],
            "box_size": {"x": 40.0, "y": 40.0, "z": 40.0},
            "density": 1.5,
        }
        result = calculate_molecule_counts(formula_data)
        assert result["density"] == 1.5
        assert result["total_mass"] > 0

    def test_no_salt(self):
        """测试无盐体系"""
        formula_data = {
            "solvent_info": [
                {"name": "EC", "mole_fraction": 1.0, "molecular_weight": 88.06}
            ],
            "box_size": {"x": 40.0, "y": 40.0, "z": 40.0},
        }
        result = calculate_molecule_counts(formula_data)
        assert result["is_neutral"] == True
        assert result["total_charge"] == 0

    def test_multiple_solvents(self):
        """测试多溶剂体系"""
        formula_data = {
            "solvent_info": [
                {"name": "EC", "mole_fraction": 0.3, "molecular_weight": 88.06},
                {"name": "EMC", "mole_fraction": 0.3, "molecular_weight": 104.10},
                {"name": "DMC", "mole_fraction": 0.4, "molecular_weight": 90.08},
            ],
            "salt_info": {
                "cation": {"name": "Li", "molecular_weight": 6.94, "charge": 1},
                "anion": {"name": "PF6", "molecular_weight": 144.96, "charge": -1},
                "concentration": 1.0,
            },
            "box_size": {"x": 50.0, "y": 50.0, "z": 50.0},
        }
        result = calculate_molecule_counts(formula_data)
        assert len(result["molecules"]) == 5
        assert result["is_neutral"] == True

    def test_different_ions(self):
        """测试不同离子类型"""
        formula_data = {
            "solvent_info": [
                {"name": "EC", "mole_fraction": 1.0, "molecular_weight": 88.06}
            ],
            "salt_info": {
                "cation": {"name": "Li", "molecular_weight": 6.94, "charge": 1},
                "anion": {"name": "BF4", "molecular_weight": 86.81, "charge": -1},
                "concentration": 0.5,
            },
            "box_size": {"x": 40.0, "y": 40.0, "z": 40.0},
        }
        result = calculate_molecule_counts(formula_data)
        ion_names = [mol["name"] for mol in result["molecules"]]
        assert "Li" in ion_names
        assert "BF4" in ion_names


class TestCalculateBoxSize:
    """测试盒子尺寸计算"""

    def test_auto_box_size(self):
        """自动计算盒子尺寸"""
        molecule_data = {
            "molecules": [
                {"name": "EC", "molecular_weight": 88.06, "count": 200},
                {"name": "Li", "molecular_weight": 6.94, "count": 50},
            ]
        }
        result = calculate_box_size(molecule_data)
        assert result["x"] > 0
        assert result["y"] > 0
        assert result["z"] > 0
        assert result["volume"] > 0
        assert result["scale_factor"] == config.BOX_SCALE_FACTOR

    def test_custom_density(self):
        """测试自定义密度"""
        molecule_data = {
            "molecules": [
                {"name": "EC", "molecular_weight": 88.06, "count": 200},
            ]
        }
        result_default = calculate_box_size(molecule_data, density=1.2)
        result_higher = calculate_box_size(molecule_data, density=1.5)
        assert result_default["x"] > result_higher["x"]

    def test_empty_molecules(self):
        """测试空分子列表"""
        molecule_data = {"molecules": []}
        with pytest.raises(ValueError, match="分子列表为空"):
            calculate_box_size(molecule_data)

    def test_single_molecule(self):
        """测试单分子类型"""
        molecule_data = {
            "molecules": [
                {"name": "EC", "molecular_weight": 88.06, "count": 100},
            ]
        }
        result = calculate_box_size(molecule_data)
        assert result["x"] == result["y"] == result["z"]
        assert result["scaled_volume"] > result["volume"]

    def test_large_system(self):
        """测试大体系"""
        molecule_data = {
            "molecules": [
                {"name": "EC", "molecular_weight": 88.06, "count": 10000},
                {"name": "DMC", "molecular_weight": 90.08, "count": 10000},
                {"name": "Li", "molecular_weight": 6.94, "count": 2000},
                {"name": "PF6", "molecular_weight": 144.96, "count": 2000},
            ]
        }
        result = calculate_box_size(molecule_data)
        assert result["x"] > 100


class TestElectricalNeutrality:
    """测试电中性验证"""

    def test_neutral_system(self):
        """电中性体系"""
        molecules = [
            MoleculeInfo(name="EC", formula="C3H4O3", molecular_weight=88.06, charge=0, count=100),
            MoleculeInfo(name="Li", formula="Li", molecular_weight=6.94, charge=1, count=50),
            MoleculeInfo(name="PF6", formula="PF6", molecular_weight=144.96, charge=-1, count=50),
        ]
        is_neutral, total_charge = validate_electrical_neutrality(molecules)
        assert is_neutral == True
        assert total_charge == 0

    def test_non_neutral_system(self):
        """非电中性体系"""
        molecules = [
            MoleculeInfo(name="EC", formula="C3H4O3", molecular_weight=88.06, charge=0, count=100),
            MoleculeInfo(name="Li", formula="Li", molecular_weight=6.94, charge=1, count=50),
            MoleculeInfo(name="PF6", formula="PF6", molecular_weight=144.96, charge=-1, count=40),
        ]
        is_neutral, total_charge = validate_electrical_neutrality(molecules)
        assert is_neutral == False
        assert total_charge == 10

    def test_neutral_only_solvents(self):
        """仅溶剂体系"""
        molecules = [
            MoleculeInfo(name="EC", formula="C3H4O3", molecular_weight=88.06, charge=0, count=100),
            MoleculeInfo(name="DMC", formula="C3H6O3", molecular_weight=90.08, charge=0, count=100),
        ]
        is_neutral, total_charge = validate_electrical_neutrality(molecules)
        assert is_neutral == True
        assert total_charge == 0

    def test_dict_format_molecules(self):
        """测试字典格式分子列表"""
        molecules = [
            {"name": "EC", "charge": 0, "count": 100},
            {"name": "Li", "charge": 1, "count": 50},
            {"name": "PF6", "charge": -1, "count": 50},
        ]
        is_neutral, total_charge = validate_electrical_neutrality(molecules)
        assert is_neutral == True

    def test_multivalent_ions(self):
        """测试多价离子"""
        molecules = [
            MoleculeInfo(name="EC", formula="C3H4O3", molecular_weight=88.06, charge=0, count=100),
            MoleculeInfo(name="Mg", formula="Mg", molecular_weight=24.31, charge=2, count=25),
            MoleculeInfo(name="Cl", formula="Cl", molecular_weight=35.45, charge=-1, count=50),
        ]
        is_neutral, total_charge = validate_electrical_neutrality(molecules)
        assert is_neutral == True
        assert total_charge == 0


class TestAdjustIonCounts:
    """测试离子数量调整"""

    def test_adjust_positive_charge_imbalance(self):
        """调整正电荷过剩"""
        molecules = [
            MoleculeInfo(name="EC", formula="C3H4O3", molecular_weight=88.06, charge=0, count=100),
            MoleculeInfo(name="Li", formula="Li", molecular_weight=6.94, charge=1, count=50),
            MoleculeInfo(name="PF6", formula="PF6", molecular_weight=144.96, charge=-1, count=40),
        ]
        adjusted = adjust_ion_counts(molecules)
        is_neutral, _ = validate_electrical_neutrality(adjusted)
        assert is_neutral == True

    def test_adjust_negative_charge_imbalance(self):
        """调整负电荷过剩"""
        molecules = [
            MoleculeInfo(name="EC", formula="C3H4O3", molecular_weight=88.06, charge=0, count=100),
            MoleculeInfo(name="Li", formula="Li", molecular_weight=6.94, charge=1, count=40),
            MoleculeInfo(name="PF6", formula="PF6", molecular_weight=144.96, charge=-1, count=50),
        ]
        adjusted = adjust_ion_counts(molecules)
        is_neutral, _ = validate_electrical_neutrality(adjusted)
        assert is_neutral == True

    def test_already_neutral(self):
        """测试已电中性体系"""
        molecules = [
            MoleculeInfo(name="EC", formula="C3H4O3", molecular_weight=88.06, charge=0, count=100),
            MoleculeInfo(name="Li", formula="Li", molecular_weight=6.94, charge=1, count=50),
            MoleculeInfo(name="PF6", formula="PF6", molecular_weight=144.96, charge=-1, count=50),
        ]
        adjusted = adjust_ion_counts(molecules)
        assert len(adjusted) == len(molecules)

    def test_dict_format_adjustment(self):
        """测试字典格式输入 - 字典格式不调整，返回原始列表"""
        molecules = [
            {"name": "EC", "formula": "C3H4O3", "molecular_weight": 88.06, "charge": 0, "count": 100},
            {"name": "Li", "formula": "Li", "molecular_weight": 6.94, "charge": 1, "count": 50},
            {"name": "PF6", "formula": "PF6", "molecular_weight": 144.96, "charge": -1, "count": 40},
        ]
        adjusted = adjust_ion_counts(molecules)
        assert adjusted == molecules


class TestFileUtils:
    """测试文件操作"""

    def test_read_write_json(self):
        """读写JSON测试"""
        with tempfile.TemporaryDirectory() as tmpdir:
            filepath = os.path.join(tmpdir, "test.json")
            data = {"test": "data", "number": 123}
            write_json(filepath, data)
            result = read_json(filepath)
            assert result == data

    def test_read_formula_json(self):
        """读取配方JSON测试"""
        with tempfile.TemporaryDirectory() as tmpdir:
            filepath = os.path.join(tmpdir, "formula.json")
            data = {
                "solvent_info": [{"name": "EC", "mole_fraction": 1.0}],
                "box_size": {"x": 40.0, "y": 40.0, "z": 40.0},
            }
            write_json(filepath, data)
            result = read_formula_json(filepath)
            assert result == data

    def test_write_result_json(self):
        """写入结果JSON测试"""
        with tempfile.TemporaryDirectory() as tmpdir:
            filepath = os.path.join(tmpdir, "result.json")
            result_data = {
                "molecules": [{"name": "EC", "count": 100}],
                "total_mass": 1.46e-20,
            }
            write_result_json(filepath, result_data)
            assert os.path.exists(filepath)
            loaded = read_json(filepath)
            assert loaded == result_data

    def test_read_nonexistent_file(self):
        """测试读取不存在的文件"""
        with pytest.raises(FileNotFoundError):
            read_formula_json("/nonexistent/path/file.json")

    def test_file_exists(self):
        """测试文件存在检查"""
        with tempfile.TemporaryDirectory() as tmpdir:
            filepath = os.path.join(tmpdir, "exists.json")
            assert file_exists(filepath) == False
            write_json(filepath, {"data": "test"})
            assert file_exists(filepath) == True

    def test_delete_file(self):
        """测试删除文件"""
        with tempfile.TemporaryDirectory() as tmpdir:
            filepath = os.path.join(tmpdir, "to_delete.json")
            write_json(filepath, {"data": "test"})
            assert file_exists(filepath) == True
            result = delete_file(filepath)
            assert result == True
            assert file_exists(filepath) == False

    def test_delete_nonexistent_file(self):
        """测试删除不存在的文件"""
        result = delete_file("/nonexistent/path/file.json")
        assert result == False

    def test_get_file_size(self):
        """测试获取文件大小"""
        with tempfile.TemporaryDirectory() as tmpdir:
            filepath = os.path.join(tmpdir, "size_test.json")
            data = {"data": "x" * 100}
            write_json(filepath, data)
            size = get_file_size(filepath)
            assert size is not None
            assert size > 0

    def test_get_file_size_nonexistent(self):
        """测试获取不存在文件大小"""
        size = get_file_size("/nonexistent/path/file.json")
        assert size is None

    def test_list_files(self):
        """测试列出文件"""
        with tempfile.TemporaryDirectory() as tmpdir:
            write_json(os.path.join(tmpdir, "file1.json"), {})
            write_json(os.path.join(tmpdir, "file2.json"), {})
            write_json(os.path.join(tmpdir, "file3.txt"), {})
            files = list_files(tmpdir, "*.json")
            assert len(files) == 2
            files_all = list_files(tmpdir)
            assert len(files_all) == 3

    def test_list_files_nonexistent_dir(self):
        """测试列出不存在的目录"""
        files = list_files("/nonexistent/directory")
        assert files == []

    def test_ensure_directory(self):
        """测试确保目录存在"""
        with tempfile.TemporaryDirectory() as tmpdir:
            new_dir = os.path.join(tmpdir, "subdir", "nested")
            result = ensure_directory(new_dir)
            assert result.exists()
            assert result.is_dir()

    def test_write_json_overwrite(self):
        """测试JSON覆盖写入"""
        with tempfile.TemporaryDirectory() as tmpdir:
            filepath = os.path.join(tmpdir, "overwrite.json")
            write_json(filepath, {"version": 1})
            write_json(filepath, {"version": 2})
            result = read_json(filepath)
            assert result["version"] == 2


class TestConfig:
    """测试配置模块"""

    def test_default_config(self):
        """测试默认配置"""
        assert config.DEFAULT_DENSITY == 1.2
        assert config.BOX_SCALE_FACTOR == 1.3
        assert config.AVOGADRO_NUMBER == 6.02214076e23
        assert config.CM3_TO_ANGSTROM3 == 1e24

    def test_molecule_templates(self):
        """测试分子模板"""
        ec_info = config.get_molecule_info("EC")
        assert ec_info["name"] == "Ethylene Carbonate"
        assert ec_info["molecular_weight"] == 88.06
        assert ec_info["charge"] == 0

        li_info = config.get_molecule_info("Li")
        assert li_info["name"] == "Lithium Ion"
        assert li_info["charge"] == 1

        pf6_info = config.get_molecule_info("PF6")
        assert pf6_info["charge"] == -1

    def test_unknown_molecule(self):
        """测试未知分子"""
        unknown = config.get_molecule_info("UnknownMolecule")
        assert unknown == {}

    def test_config_from_env(self):
        """测试从环境变量创建配置"""
        test_config = Config.from_env()
        assert isinstance(test_config, Config)
        assert test_config.AVOGADRO_NUMBER == config.AVOGADRO_NUMBER

    def test_custom_config(self):
        """测试自定义配置"""
        custom_config = Config(
            DEFAULT_DENSITY=1.5,
            BOX_SCALE_FACTOR=1.5,
        )
        assert custom_config.DEFAULT_DENSITY == 1.5
        assert custom_config.BOX_SCALE_FACTOR == 1.5


class TestIntegration:
    """集成测试"""

    def test_full_workflow(self):
        """测试完整工作流程"""
        with tempfile.TemporaryDirectory() as tmpdir:
            formula_file = os.path.join(tmpdir, "formula.json")
            result_file = os.path.join(tmpdir, "result.json")
            
            formula_data = {
                "solvent_info": [
                    {"name": "EC", "mole_fraction": 0.5, "molecular_weight": 88.06},
                    {"name": "DMC", "mole_fraction": 0.5, "molecular_weight": 90.08},
                ],
                "salt_info": {
                    "cation": {"name": "Li", "molecular_weight": 6.94, "charge": 1},
                    "anion": {"name": "PF6", "molecular_weight": 144.96, "charge": -1},
                    "concentration": 1.0,
                },
                "box_size": {"x": 40.0, "y": 40.0, "z": 40.0},
                "density": 1.2,
            }
            
            write_json(formula_file, formula_data)
            loaded = read_formula_json(formula_file)
            
            result = calculate_molecule_counts(loaded)
            write_result_json(result_file, result)
            
            assert file_exists(result_file)
            saved_result = read_json(result_file)
            assert saved_result["is_neutral"] == True
            assert len(saved_result["molecules"]) >= 4

    def test_box_size_calculation_workflow(self):
        """测试盒子尺寸计算工作流"""
        formula_data = {
            "solvent_info": [
                {"name": "EC", "mole_fraction": 1.0, "molecular_weight": 88.06}
            ],
            "salt_info": {
                "cation": {"name": "Li", "molecular_weight": 6.94, "charge": 1},
                "anion": {"name": "PF6", "molecular_weight": 144.96, "charge": -1},
                "concentration": 1.0,
            },
            "box_size": {"x": 50.0, "y": 50.0, "z": 50.0},
        }
        
        result = calculate_molecule_counts(formula_data)
        box_result = calculate_box_size({"molecules": result["molecules"]})
        
        assert box_result["x"] > 0
        assert box_result["y"] > 0
        assert box_result["z"] > 0


if __name__ == "__main__":
    pytest.main([__file__, "-v"])