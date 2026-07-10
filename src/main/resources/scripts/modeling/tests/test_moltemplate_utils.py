"""
Moltemplate工具模块测试

测试moltemplate_utils模块中的PDB解析功能
"""

import tempfile
import os
import pytest
from pathlib import Path

from utils.moltemplate_utils import (
    parse_pdb_file,
    extract_molecule_coordinates,
    identify_molecule_boundaries,
    map_molecule_ids,
    validate_pdb_structure,
    AtomRecord,
    MoleculeInstance,
)


class TestPDBParsing:
    """PDB解析功能测试类"""

    @pytest.fixture
    def sample_pdb_file(self):
        """创建测试PDB文件"""
        test_pdb_content = """
ATOM      1  C1  EC     1       0.000   0.000   0.000  1.00  0.00           C
ATOM      2  C2  EC     1       1.000   0.000   0.000  1.00  0.00           C
ATOM      3  O1  EC     1       2.000   0.000   0.000  1.00  0.00           O
TER
ATOM      4  C1  EC     2       5.000   0.000   0.000  1.00  0.00           C
ATOM      5  C2  EC     2       6.000   0.000   0.000  1.00  0.00           C
ATOM      6  O1  EC     2       7.000   0.000   0.000  1.00  0.00           O
TER
ATOM      7  LI  Li     3      10.000   0.000   0.000  1.00  0.00           Li
TER
END
"""
        temp_file = tempfile.NamedTemporaryFile(mode='w', suffix='.pdb', delete=False)
        temp_file.write(test_pdb_content)
        temp_file.close()
        yield temp_file.name
        # 清理
        os.unlink(temp_file.name)

    def test_parse_pdb_file(self, sample_pdb_file):
        """测试parse_pdb_file函数"""
        result = parse_pdb_file(sample_pdb_file)
        
        # 验证解析成功
        assert result["success"] is True
        assert result["error"] is None
        
        # 验证原子总数
        assert result["total_atoms"] == 7
        
        # 验证TER记录数量
        assert len(result["ter_records"]) == 3
        
        # 验证原子数据
        atoms = result["atoms"]
        assert len(atoms) == 7
        
        # 验证第一个原子
        first_atom = atoms[0]
        assert first_atom.serial == 1
        assert first_atom.name == "C1"
        assert first_atom.res_name == "EC"
        assert first_atom.res_seq == 1
        assert first_atom.x == 0.0
        assert first_atom.y == 0.0
        assert first_atom.z == 0.0
        assert first_atom.element == "C"

    def test_extract_molecule_coordinates(self, sample_pdb_file):
        """测试extract_molecule_coordinates函数"""
        result = extract_molecule_coordinates(sample_pdb_file)
        
        # 验证提取成功
        assert result["success"] is True
        assert result["error"] is None
        
        # 验证分子实例总数
        assert result["total_molecules"] == 3
        
        # 验证分子数据
        molecules = result["molecules"]
        assert len(molecules) == 3
        
        # 验证第一个分子（EC_1）
        mol1 = molecules[0]
        assert mol1["molecule_name"] == "EC"
        assert mol1["instance_id"] == 1
        assert mol1["atom_count"] == 3
        
        # 验证第二个分子（EC_2）
        mol2 = molecules[1]
        assert mol2["molecule_name"] == "EC"
        assert mol2["instance_id"] == 2
        assert mol2["atom_count"] == 3
        
        # 验证第三个分子（Li_1）
        mol3 = molecules[2]
        assert mol3["molecule_name"] == "Li"
        assert mol3["instance_id"] == 1
        assert mol3["atom_count"] == 1

    def test_identify_molecule_boundaries(self, sample_pdb_file):
        """测试identify_molecule_boundaries函数"""
        parse_result = parse_pdb_file(sample_pdb_file)
        atoms = parse_result["atoms"]
        ter_records = parse_result["ter_records"]
        
        result = identify_molecule_boundaries(atoms, ter_records)
        
        # 验证识别成功
        assert result["success"] is True
        assert result["error"] is None
        
        # 验证边界总数
        assert result["total_boundaries"] == 3
        
        # 验证边界数据
        boundaries = result["boundaries"]
        assert len(boundaries) == 3
        
        # 验证分子计数器
        molecule_counters = result["molecule_counters"]
        assert molecule_counters["EC"] == 2
        assert molecule_counters["Li"] == 1

    def test_map_molecule_ids(self, sample_pdb_file):
        """测试map_molecule_ids函数"""
        parse_result = parse_pdb_file(sample_pdb_file)
        atoms = parse_result["atoms"]
        boundaries_result = identify_molecule_boundaries(
            atoms, parse_result["ter_records"]
        )
        boundaries = boundaries_result["boundaries"]
        
        result = map_molecule_ids(atoms, boundaries)
        
        # 验证映射成功
        assert result["success"] is True
        assert result["error"] is None
        
        # 验证分子ID列表
        molecule_id_list = result["molecule_id_list"]
        assert len(molecule_id_list) == 3
        assert "EC_1" in molecule_id_list
        assert "EC_2" in molecule_id_list
        assert "Li_1" in molecule_id_list
        
        # 验证原子映射
        atom_molecule_map = result["atom_molecule_map"]
        assert len(atom_molecule_map) == 7
        assert atom_molecule_map[0] == "EC_1"
        assert atom_molecule_map[3] == "EC_2"
        assert atom_molecule_map[6] == "Li_1"

    def test_validate_pdb_structure(self, sample_pdb_file):
        """测试validate_pdb_structure函数"""
        # 测试正确的验证
        result = validate_pdb_structure(
            sample_pdb_file,
            expected_atoms=7,
            expected_molecules={"EC": 2, "Li": 1}
        )
        
        # 验证验证成功
        assert result["success"] is True
        assert result["valid"] is True
        assert len(result["validation_errors"]) == 0
        
        # 验证原子总数
        assert result["total_atoms"] == 7
        
        # 验证分子实例总数
        assert result["total_molecules"] == 3
        
        # 验证分子计数
        molecule_counts = result["molecule_counts"]
        assert molecule_counts["EC"] == 2
        assert molecule_counts["Li"] == 1

    def test_validate_pdb_structure_with_errors(self, sample_pdb_file):
        """测试validate_pdb_structure函数的错误检测"""
        # 测试错误的原子总数
        result = validate_pdb_structure(
            sample_pdb_file,
            expected_atoms=10,
            expected_molecules={"EC": 2, "Li": 1}
        )
        
        # 验证验证失败
        assert result["success"] is True
        assert result["valid"] is False
        assert len(result["validation_errors"]) > 0
        assert "原子总数不匹配" in result["validation_errors"][0]
        
        # 测试错误的分子数量
        result = validate_pdb_structure(
            sample_pdb_file,
            expected_atoms=7,
            expected_molecules={"EC": 5, "Li": 1}
        )
        
        # 验证验证失败
        assert result["success"] is True
        assert result["valid"] is False
        assert len(result["validation_errors"]) > 0
        assert "分子 EC 数量不匹配" in result["validation_errors"][0]

    def test_parse_nonexistent_file(self):
        """测试解析不存在的文件"""
        result = parse_pdb_file("nonexistent.pdb")
        
        # 验证解析失败
        assert result["success"] is False
        assert result["error"] is not None
        assert "PDB文件不存在" in result["error"]

    def test_atom_record_dataclass(self):
        """测试AtomRecord数据类"""
        atom = AtomRecord(
            serial=1,
            name="C1",
            alt_loc="",
            res_name="EC",
            chain_id="",
            res_seq=1,
            icode="",
            x=0.0,
            y=0.0,
            z=0.0,
            occupancy=1.0,
            temp_factor=0.0,
            element="C",
            charge="",
            record_type="ATOM"
        )
        
        # 验证数据类属性
        assert atom.serial == 1
        assert atom.name == "C1"
        assert atom.res_name == "EC"
        assert atom.x == 0.0
        assert atom.element == "C"

    def test_molecule_instance_dataclass(self):
        """测试MoleculeInstance数据类"""
        atoms = [
            AtomRecord(
                serial=1,
                name="C1",
                alt_loc="",
                res_name="EC",
                chain_id="",
                res_seq=1,
                icode="",
                x=0.0,
                y=0.0,
                z=0.0,
                occupancy=1.0,
                temp_factor=0.0,
                element="C",
                charge="",
                record_type="ATOM"
            )
        ]
        
        molecule = MoleculeInstance(
            molecule_name="EC",
            instance_id=1,
            atoms=atoms,
            chain_id="",
            start_line=1,
            end_line=1
        )
        
        # 验证数据类属性
        assert molecule.molecule_name == "EC"
        assert molecule.instance_id == 1
        assert len(molecule.atoms) == 1


if __name__ == "__main__":
    pytest.main([__file__, "-v"])