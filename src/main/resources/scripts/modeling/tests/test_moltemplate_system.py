"""
Moltemplate系统生成功能测试

测试分子模板加载、分子实例创建、system.lt文件生成等核心功能

作者: AI Assistant
版本: 1.0.0
"""

import tempfile
import os
import json
import pytest
from pathlib import Path
from typing import Dict, Any, List
import shutil

from utils.moltemplate_utils import (
    # 分子模板加载功能
    fetch_molecule_lt_templates,
    copy_lt_files_to_workdir,
    validate_lt_template,
    load_forcefield_file,
    
    # 分子实例创建功能
    create_molecule_instances,
    assign_atom_coordinates,
    generate_atom_names,
    validate_instance_count,
    
    # system.lt文件生成功能
    generate_system_lt_file,
    generate_template_imports,
    generate_molecule_instance_definitions,
    generate_coordinate_data,
    generate_box_boundary,
    validate_system_lt,
    
    # PDB解析功能（用于测试准备）
    parse_pdb_file,
    identify_molecule_boundaries,
    map_molecule_ids,
    
    # 数据类
    AtomRecord,
    MoleculeInstance,
)


class TestMoleculeTemplateLoading:
    """分子模板加载功能测试类"""

    @pytest.fixture
    def template_library(self):
        """创建测试模板库"""
        temp_dir = tempfile.mkdtemp(prefix="template_lib_")
        
        # 创建EC模板目录和文件
        ec_dir = Path(temp_dir) / "EC"
        ec_dir.mkdir(parents=True, exist_ok=True)
        
        ec_lt_content = """
EC {
  write_once("Data Masses") {
    @atom:C1 12.0
    @atom:C2 12.0
    @atom:O1 16.0
  }
  
  write('Data Atoms') {
    $atom:1 @atom:C1 $mol:... 0.0 0.0 0.0 0.0
    $atom:2 @atom:C2 $mol:... 0.0 1.0 0.0 0.0
    $atom:3 @atom:O1 $mol:... 0.0 2.0 0.0 0.0
  }
}
"""
        with open(ec_dir / "EC.lt", 'w') as f:
            f.write(ec_lt_content)
        
        # 创建Li模板目录和文件
        li_dir = Path(temp_dir) / "Li"
        li_dir.mkdir(parents=True, exist_ok=True)
        
        li_lt_content = """
Li {
  write_once("Data Masses") {
    @atom:LI 6.9
  }
  
  write('Data Atoms') {
    $atom:1 @atom:LI $mol:... 1.0 0.0 0.0 0.0
  }
}
"""
        with open(li_dir / "Li.lt", 'w') as f:
            f.write(li_lt_content)
        
        yield temp_dir
        # 清理
        shutil.rmtree(temp_dir, ignore_errors=True)

    @pytest.fixture
    def work_dir(self):
        """创建测试工作目录"""
        temp_dir = tempfile.mkdtemp(prefix="work_dir_")
        yield temp_dir
        # 清理
        shutil.rmtree(temp_dir, ignore_errors=True)

    def test_fetch_molecule_lt_templates_success(self, template_library):
        """测试fetch_molecule_lt_templates函数成功场景"""
        molecule_names = ["EC", "Li"]
        
        result = fetch_molecule_lt_templates(molecule_names, template_library)
        
        # 验证获取成功
        assert result["success"] is True
        assert result["error"] is None
        
        # 验证找到的分子
        assert len(result["found_molecules"]) == 2
        assert "EC" in result["found_molecules"]
        assert "Li" in result["found_molecules"]
        
        # 验证缺失的分子
        assert len(result["missing_molecules"]) == 0
        
        # 验证模板路径
        template_paths = result["template_paths"]
        assert "EC" in template_paths
        assert "Li" in template_paths
        assert Path(template_paths["EC"]).exists()
        assert Path(template_paths["Li"]).exists()

    def test_fetch_molecule_lt_templates_missing_molecules(self, template_library):
        """测试fetch_molecule_lt_templates函数缺失分子模板"""
        molecule_names = ["EC", "DMC", "PF6"]
        
        result = fetch_molecule_lt_templates(molecule_names, template_library)
        
        # 验证获取失败（部分缺失）
        assert result["success"] is False
        assert result["error"] is not None
        
        # 验证找到的分子
        assert len(result["found_molecules"]) == 1
        assert "EC" in result["found_molecules"]
        
        # 验证缺失的分子
        assert len(result["missing_molecules"]) == 2
        assert "DMC" in result["missing_molecules"]
        assert "PF6" in result["missing_molecules"]

    def test_fetch_molecule_lt_templates_invalid_library(self):
        """测试fetch_molecule_lt_templates函数无效模板库路径"""
        molecule_names = ["EC", "Li"]
        
        result = fetch_molecule_lt_templates(molecule_names, "nonexistent_library")
        
        # 验证获取失败
        assert result["success"] is False
        assert result["error"] is not None
        assert "模板库路径不存在" in result["error"]
        
        # 验证所有分子都缺失
        assert len(result["missing_molecules"]) == 2

    def test_copy_lt_files_to_workdir_success(self, template_library, work_dir):
        """测试copy_lt_files_to_workdir函数成功场景"""
        # 先获取模板路径
        fetch_result = fetch_molecule_lt_templates(["EC", "Li"], template_library)
        template_paths = fetch_result["template_paths"]
        
        result = copy_lt_files_to_workdir(template_paths, work_dir)
        
        # 验证复制成功
        assert result["success"] is True
        assert result["error"] is None
        
        # 验证复制的文件
        assert len(result["copied_files"]) == 2
        assert "EC" in result["copied_files"]
        assert "Li" in result["copied_files"]
        
        # 验证inputs目录已创建
        inputs_dir = Path(result["inputs_dir"])
        assert inputs_dir.exists()
        assert inputs_dir.name == "inputs"
        
        # 验证文件已复制到正确位置
        assert Path(result["copied_files"]["EC"]).exists()
        assert Path(result["copied_files"]["EC"]).parent == inputs_dir
        assert Path(result["copied_files"]["Li"]).exists()
        assert Path(result["copied_files"]["Li"]).parent == inputs_dir

    def test_copy_lt_files_to_workdir_missing_source(self, work_dir):
        """测试copy_lt_files_to_workdir函数源文件缺失"""
        # 使用不存在的模板路径
        template_paths = {
            "EC": "nonexistent/EC/EC.lt",
            "Li": "nonexistent/Li/Li.lt"
        }
        
        result = copy_lt_files_to_workdir(template_paths, work_dir)
        
        # 验证复制失败
        assert result["success"] is False
        assert result["error"] is not None
        
        # 验证失败的文件
        assert len(result["failed_files"]) == 2
        assert "EC" in result["failed_files"]
        assert "Li" in result["failed_files"]

    def test_validate_lt_template_valid(self, template_library):
        """测试validate_lt_template函数有效模板"""
        ec_lt_path = str(Path(template_library) / "EC" / "EC.lt")
        
        result = validate_lt_template(ec_lt_path)
        
        # 验证验证成功
        assert result["success"] is True
        assert result["valid"] is True
        assert len(result["validation_errors"]) == 0
        
        # 验证模板信息
        assert result["molecule_name"] == "EC"
        assert result["has_atom_types"] is True  # 使用 has_atom_types 字段
        assert result["has_coordinates"] is True  # 验证坐标数据

    def test_validate_lt_template_invalid_syntax(self, work_dir):
        """测试validate_lt_template函数无效语法"""
        # 创建一个语法错误的.lt文件（使用UTF-8编码）
        invalid_lt_path = Path(work_dir) / "invalid.lt"
        invalid_lt_content = """InvalidMolecule {
  write_once("Data Masses") {
    @atom:C1 12.0
    # 缺少闭合括号
"""
        # 确保使用UTF-8编码写入
        invalid_lt_path.write_text(invalid_lt_content, encoding='utf-8')
        
        result = validate_lt_template(str(invalid_lt_path))
        
        # 验证验证失败
        assert result["success"] is True
        assert result["valid"] is False
        assert len(result["validation_errors"]) > 0
        # 验证括号不匹配错误
        assert any("括号不匹配" in err for err in result["validation_errors"])

    def test_validate_lt_template_nonexistent_file(self):
        """测试validate_lt_template函数不存在文件"""
        result = validate_lt_template("nonexistent.lt")
        
        # 验证验证失败
        assert result["success"] is False
        assert result["valid"] is False
        assert result["error"] is not None

    @pytest.fixture
    def forcefield_library(self):
        """创建测试力场库"""
        temp_dir = tempfile.mkdtemp(prefix="forcefield_lib_")
        
        # 创建OPLS-AA力场目录和文件
        oplsaa_dir = Path(temp_dir) / "oplsaa"
        oplsaa_dir.mkdir(parents=True, exist_ok=True)
        
        oplsaa_lt_content = """
# OPLS-AA Force Field
import "oplsaa.lt"
"""
        with open(oplsaa_dir / "oplsaa.lt", 'w') as f:
            f.write(oplsaa_lt_content)
        
        yield temp_dir
        # 清理
        shutil.rmtree(temp_dir, ignore_errors=True)

    def test_load_forcefield_file_success(self, forcefield_library):
        """测试load_forcefield_file函数成功场景"""
        result = load_forcefield_file("oplsaa", forcefield_library)
        
        # 验证加载成功
        assert result["success"] is True
        assert result["error"] is None
        
        # 验证力场文件路径
        assert result["forcefield_path"] is not None
        assert Path(result["forcefield_path"]).exists()

    def test_load_forcefield_file_missing(self, forcefield_library):
        """测试load_forcefield_file函数缺失力场文件"""
        result = load_forcefield_file("gaff", forcefield_library)
        
        # 验证加载失败
        assert result["success"] is False
        assert result["error"] is not None

    def test_load_forcefield_file_invalid_library(self):
        """测试load_forcefield_file函数无效力场库路径"""
        result = load_forcefield_file("oplsaa", "nonexistent_library")
        
        # 验证加载失败
        assert result["success"] is False
        assert result["error"] is not None


class TestMoleculeInstanceCreation:
    """分子实例创建功能测试类"""

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

    @pytest.fixture
    def template_library_for_instance(self):
        """为分子实例创建测试创建模板库"""
        temp_dir = tempfile.mkdtemp(prefix="template_lib_instance_")
        
        # 创建EC模板目录和文件
        ec_dir = Path(temp_dir) / "EC"
        ec_dir.mkdir(parents=True, exist_ok=True)
        
        ec_lt_content = """
EC {
  write_once("Data Masses") {
    @atom:C1 12.0
    @atom:C2 12.0
    @atom:O1 16.0
  }
  
  write('Data Atoms') {
    $atom:1 @atom:C1 $mol:... 0.0 0.0 0.0 0.0
    $atom:2 @atom:C2 $mol:... 0.0 1.0 0.0 0.0
    $atom:3 @atom:O1 $mol:... 0.0 2.0 0.0 0.0
  }
}
"""
        with open(ec_dir / "EC.lt", 'w') as f:
            f.write(ec_lt_content)
        
        yield temp_dir
        # 清理
        shutil.rmtree(temp_dir, ignore_errors=True)

    @pytest.fixture
    def sample_pdb_parse_result(self, sample_pdb_file):
        """获取PDB解析结果"""
        return parse_pdb_file(sample_pdb_file)

    def test_create_molecule_instances_success(self, sample_pdb_parse_result):
        """测试create_molecule_instances函数成功场景"""
        molecule_names = ["EC", "Li"]
        molecule_counts = {"EC": 2, "Li": 1}
        
        result = create_molecule_instances(
            molecule_names,
            molecule_counts,
            sample_pdb_parse_result
        )
        
        # 验证创建成功
        assert result["success"] is True
        assert result["error"] is None
        
        # 验证实例总数
        assert result["total_instances"] == 3
        assert result["total_atoms"] == 7
        
        # 验证实例列表
        instances = result["instances"]
        assert len(instances) == 3
        
        # 验证第一个实例（EC_1）
        instance1 = instances[0]
        assert instance1.molecule_name == "EC"
        assert instance1.instance_id == 1
        assert len(instance1.atoms) == 3
        
        # 验证第二个实例（EC_2）
        instance2 = instances[1]
        assert instance2.molecule_name == "EC"
        assert instance2.instance_id == 2
        assert len(instance2.atoms) == 3
        
        # 验证第三个实例（Li_1）
        instance3 = instances[2]
        assert instance3.molecule_name == "Li"
        assert instance3.instance_id == 1
        assert len(instance3.atoms) == 1
        
        # 验证实例统计摘要
        instance_summary = result["instance_summary"]
        assert instance_summary["EC"] == 2
        assert instance_summary["Li"] == 1

    def test_create_molecule_instances_invalid_pdb_result(self):
        """测试create_molecule_instances函数无效PDB解析结果"""
        molecule_names = ["EC", "Li"]
        molecule_counts = {"EC": 2, "Li": 1}
        
        # 使用失败的PDB解析结果
        invalid_pdb_result = {
            "success": False,
            "error": "PDB解析失败",
            "atoms": []
        }
        
        result = create_molecule_instances(
            molecule_names,
            molecule_counts,
            invalid_pdb_result
        )
        
        # 验证创建失败
        assert result["success"] is False
        assert result["error"] is not None
        assert len(result["instances"]) == 0

    def test_assign_atom_coordinates_success(self, sample_pdb_parse_result):
        """测试assign_atom_coordinates函数成功场景"""
        atoms = sample_pdb_parse_result["atoms"]
        ter_records = sample_pdb_parse_result["ter_records"]
        
        # 识别分子边界
        boundaries_result = identify_molecule_boundaries(atoms, ter_records)
        boundaries = boundaries_result["boundaries"]
        
        # 映射分子ID
        map_result = map_molecule_ids(atoms, boundaries)
        molecule_id_map = map_result["atom_molecule_map"]
        
        result = assign_atom_coordinates(atoms, molecule_id_map)
        
        # 验证分配成功
        assert result["success"] is True
        assert result["error"] is None
        
        # 验证分子-原子映射
        molecule_atoms_map = result["molecule_atoms_map"]
        assert len(molecule_atoms_map) == 3
        assert "EC_1" in molecule_atoms_map
        assert "EC_2" in molecule_atoms_map
        assert "Li_1" in molecule_atoms_map
        
        # 验证每个分子的原子数量
        assert len(molecule_atoms_map["EC_1"]) == 3
        assert len(molecule_atoms_map["EC_2"]) == 3
        assert len(molecule_atoms_map["Li_1"]) == 1
        
        # 验证原子分配列表
        atom_assignment_list = result["atom_assignment_list"]
        assert len(atom_assignment_list) == 7
        
        # 验证未分配的原子
        assert len(result["unassigned_atoms"]) == 0

    def test_assign_atom_coordinates_empty_atoms(self):
        """测试assign_atom_coordinates函数空原子列表"""
        result = assign_atom_coordinates([], {})
        
        # 验证分配失败
        assert result["success"] is False
        assert result["error"] is not None

    def test_generate_atom_names_success(self, sample_pdb_parse_result, template_library_for_instance):
        """测试generate_atom_names函数成功场景"""
        # 创建分子实例
        molecule_names = ["EC", "Li"]
        molecule_counts = {"EC": 2, "Li": 1}
        
        instance_result = create_molecule_instances(
            molecule_names,
            molecule_counts,
            sample_pdb_parse_result
        )
        instances = instance_result["instances"]
        
        # 使用EC模板文件路径
        ec_lt_path = str(Path(template_library_for_instance) / "EC" / "EC.lt")
        
        result = generate_atom_names(ec_lt_path, instances)
        
        # 验证生成成功
        assert result["success"] is True
        assert result["error"] is None
        
        # 验证原子名称映射
        atom_names_map = result["atom_names_map"]
        assert len(atom_names_map) > 0
        
        # 验证模板原子名称
        template_atom_names = result["template_atom_names"]
        assert len(template_atom_names) > 0

    def test_validate_instance_count_valid(self, sample_pdb_parse_result):
        """测试validate_instance_count函数有效实例数量"""
        molecule_names = ["EC", "Li"]
        molecule_counts = {"EC": 2, "Li": 1}
        
        # 创建分子实例
        instance_result = create_molecule_instances(
            molecule_names,
            molecule_counts,
            sample_pdb_parse_result
        )
        instances = instance_result["instances"]
        
        result = validate_instance_count(instances, molecule_counts)
        
        # 验证验证成功（validate_instance_count返回的是valid字段，不是success）
        assert result["valid"] is True
        assert len(result["differences"]) == 0
        
        # 验证统计信息
        assert result["total_instances"] == 3
        assert result["total_expected"] == 3

    def test_validate_instance_count_invalid(self, sample_pdb_parse_result):
        """测试validate_instance_count函数无效实例数量"""
        molecule_names = ["EC", "Li"]
        molecule_counts = {"EC": 5, "Li": 10}  # 与实际不符
        
        # 创建分子实例
        instance_result = create_molecule_instances(
            molecule_names,
            molecule_counts,
            sample_pdb_parse_result
        )
        instances = instance_result["instances"]
        
        result = validate_instance_count(instances, molecule_counts)
        
        # 验证验证失败（validate_instance_count返回的是valid字段，不是success）
        assert result["valid"] is False
        assert len(result["differences"]) > 0
        
        # 验证差异信息
        differences = result["differences"]
        assert len(differences) > 0


class TestSystemLtFileGeneration:
    """system.lt文件生成功能测试类"""

    @pytest.fixture
    def template_files(self):
        """创建测试模板文件"""
        temp_dir = tempfile.mkdtemp(prefix="templates_")
        
        # 创建EC.lt文件
        ec_lt_path = Path(temp_dir) / "EC.lt"
        ec_lt_content = """
EC {
  write_once("Data Masses") {
    @atom:C1 12.0
    @atom:C2 12.0
    @atom:O1 16.0
  }
  
  write('Data Atoms') {
    $atom:1 @atom:C1 $mol:... 0.0 0.0 0.0 0.0
    $atom:2 @atom:C2 $mol:... 0.0 1.0 0.0 0.0
    $atom:3 @atom:O1 $mol:... 0.0 2.0 0.0 0.0
  }
}
"""
        ec_lt_path.write_text(ec_lt_content)
        
        # 创建Li.lt文件
        li_lt_path = Path(temp_dir) / "Li.lt"
        li_lt_content = """
Li {
  write_once("Data Masses") {
    @atom:LI 6.9
  }
  
  write('Data Atoms') {
    $atom:1 @atom:LI $mol:... 1.0 0.0 0.0 0.0
  }
}
"""
        li_lt_path.write_text(li_lt_content)
        
        yield {
            "EC": str(ec_lt_path),
            "Li": str(li_lt_path),
            "temp_dir": temp_dir
        }
        # 清理
        shutil.rmtree(temp_dir, ignore_errors=True)

    @pytest.fixture
    def molecule_instances(self):
        """创建测试分子实例"""
        # 创建EC_1实例
        ec1_atoms = [
            AtomRecord(
                serial=1, name="C1", alt_loc="", res_name="EC", chain_id="",
                res_seq=1, icode="", x=0.0, y=0.0, z=0.0,
                occupancy=1.0, temp_factor=0.0, element="C", charge="",
                record_type="ATOM"
            ),
            AtomRecord(
                serial=2, name="C2", alt_loc="", res_name="EC", chain_id="",
                res_seq=1, icode="", x=1.0, y=0.0, z=0.0,
                occupancy=1.0, temp_factor=0.0, element="C", charge="",
                record_type="ATOM"
            ),
            AtomRecord(
                serial=3, name="O1", alt_loc="", res_name="EC", chain_id="",
                res_seq=1, icode="", x=2.0, y=0.0, z=0.0,
                occupancy=1.0, temp_factor=0.0, element="O", charge="",
                record_type="ATOM"
            )
        ]
        
        ec1_instance = MoleculeInstance(
            molecule_name="EC",
            instance_id=1,
            atoms=ec1_atoms,
            chain_id="",
            start_line=1,
            end_line=3
        )
        
        # 创建Li_1实例
        li1_atoms = [
            AtomRecord(
                serial=4, name="LI", alt_loc="", res_name="Li", chain_id="",
                res_seq=1, icode="", x=10.0, y=0.0, z=0.0,
                occupancy=1.0, temp_factor=0.0, element="Li", charge="",
                record_type="ATOM"
            )
        ]
        
        li1_instance = MoleculeInstance(
            molecule_name="Li",
            instance_id=1,
            atoms=li1_atoms,
            chain_id="",
            start_line=4,
            end_line=4
        )
        
        return [ec1_instance, li1_instance]

    @pytest.fixture
    def box_size(self):
        """创建测试盒子尺寸"""
        return {"x": 50.0, "y": 50.0, "z": 50.0}

    @pytest.fixture
    def work_dir(self):
        """创建测试工作目录"""
        temp_dir = tempfile.mkdtemp(prefix="work_dir_")
        yield temp_dir
        # 清理
        shutil.rmtree(temp_dir, ignore_errors=True)

    def test_generate_template_imports_success(self, template_files):
        """测试generate_template_imports函数成功场景"""
        # 使用相对文件名
        template_file_names = {
            "EC": "EC.lt",
            "Li": "Li.lt"
        }
        
        result = generate_template_imports(template_file_names)
        
        # 验证生成成功
        assert result["success"] is True
        assert result["error"] is None
        
        # 验证导入块内容
        import_block = result["import_block"]
        assert len(import_block) > 0
        assert "import" in import_block
        assert "EC.lt" in import_block
        assert "Li.lt" in import_block

    def test_generate_template_imports_with_forcefield(self, template_files):
        """测试generate_template_imports函数带力场文件"""
        template_file_names = {
            "EC": "EC.lt",
            "Li": "Li.lt"
        }
        forcefield_file = "oplsaa.lt"
        
        result = generate_template_imports(template_file_names, forcefield_file)
        
        # 验证生成成功
        assert result["success"] is True
        
        # 验证导入块包含力场文件
        import_block = result["import_block"]
        assert "oplsaa.lt" in import_block

    def test_generate_molecule_instance_definitions_success(self):
        """测试generate_molecule_instance_definitions函数成功场景"""
        molecule_counts = {"EC": 500, "Li": 50}
        
        result = generate_molecule_instance_definitions(molecule_counts)
        
        # 验证生成成功
        assert result["success"] is True
        assert result["error"] is None
        
        # 验证实例定义块内容（变量名是小写的）
        instance_block = result["instance_block"]
        assert len(instance_block) > 0
        assert "ecs" in instance_block  # 小写的 ecs
        assert "new EC[500]" in instance_block
        assert "lis" in instance_block  # 小写的 lis
        assert "new Li[50]" in instance_block
        
        # 验证实例定义列表
        instance_lines = result["instance_lines"]
        assert len(instance_lines) > 0

    def test_generate_coordinate_data_success(self, molecule_instances):
        """测试generate_coordinate_data函数成功场景"""
        result = generate_coordinate_data(molecule_instances)
        
        # 验证生成成功
        assert result["success"] is True
        assert result["error"] is None
        
        # 验证坐标数据块内容
        coordinate_block = result["coordinate_block"]
        assert len(coordinate_block) > 0
        assert "Data Atoms" in coordinate_block
        
        # 验证原子总数
        assert result["total_atoms"] == 4
        
        # 验证坐标行数（包含注释行和空行，所以会比原子数多）
        coordinate_lines = result["coordinate_lines"]
        assert len(coordinate_lines) >= 4  # 至少包含4个原子行

    def test_generate_box_boundary_success(self, box_size):
        """测试generate_box_boundary函数成功场景"""
        result = generate_box_boundary(box_size)
        
        # 验证生成成功
        assert result["success"] is True
        assert result["error"] is None
        
        # 验证边界数据块内容
        boundary_block = result["boundary_block"]
        assert len(boundary_block) > 0
        assert "Data Boundary" in boundary_block
        
        # 验证边界尺寸
        assert "50.0" in boundary_block
        assert "0.0" in boundary_block

    def test_generate_system_lt_file_success(
        self,
        template_files,
        molecule_instances,
        box_size,
        work_dir
    ):
        """测试generate_system_lt_file函数成功场景"""
        # 使用相对文件名
        template_file_names = {
            "EC": "EC.lt",
            "Li": "Li.lt"
        }
        
        output_path = Path(work_dir) / "system.lt"
        
        result = generate_system_lt_file(
            template_file_names,
            molecule_instances,
            box_size,
            forcefield_type="oplsaa",
            output_path=str(output_path)
        )
        
        # 验证生成成功
        assert result["success"] is True
        assert result["error"] is None
        
        # 验证system.lt文件路径
        assert result["system_lt_path"] is not None
        assert Path(result["system_lt_path"]).exists()
        
        # 验证system.lt文件内容
        system_lt_content = result["system_lt_content"]
        assert len(system_lt_content) > 0
        assert "system = {" in system_lt_content
        assert "import" in system_lt_content
        assert "Data Atoms" in system_lt_content
        assert "Data Boundary" in system_lt_content
        
        # 验证生成摘要
        generation_summary = result["generation_summary"]
        assert generation_summary["total_molecules"] == 2
        assert generation_summary["total_atoms"] == 4
        
        # 验证文件验证结果
        validation_result = result["validation_result"]
        assert validation_result.get("valid", False) is True

    def test_generate_system_lt_file_empty_templates(self, molecule_instances, box_size):
        """测试generate_system_lt_file函数空模板文件"""
        result = generate_system_lt_file({}, molecule_instances, box_size)
        
        # 验证生成失败
        assert result["success"] is False
        assert result["error"] is not None

    def test_generate_system_lt_file_empty_instances(self, template_files, box_size):
        """测试generate_system_lt_file函数空分子实例"""
        template_file_names = {"EC": "EC.lt"}
        
        result = generate_system_lt_file(template_file_names, [], box_size)
        
        # 验证生成失败
        assert result["success"] is False
        assert result["error"] is not None

    def test_validate_system_lt_valid(self, work_dir):
        """测试validate_system_lt函数有效文件"""
        # 创建一个有效的system.lt文件（使用UTF-8编码）
        system_lt_path = Path(work_dir) / "system.lt"
        # 使用正确的格式：import语句带注释，分子实例定义无缩进（正则表达式要求行首）
        system_lt_content = """# Moltemplate system description file
import "EC.lt" # EC molecule template
import "Li.lt" # Li molecule template

system = {
ecs = new EC[2]
lis = new Li[1]

write("Data Atoms") {
$atom:EC_1:C1 @atom:C1 $mol:... 0.0 0.0 0.0 0.0
$atom:EC_1:C2 @atom:C2 $mol:... 0.0 1.0 0.0 0.0
}

write_once("Data Boundary") {
0.0 50.0 xlo xhi
0.0 50.0 ylo yhi
0.0 50.0 zlo zhi
}
}
"""
        # 确保使用UTF-8编码写入
        system_lt_path.write_text(system_lt_content, encoding='utf-8')
        
        result = validate_system_lt(str(system_lt_path))
        
        # 验证验证成功
        assert result["success"] is True
        assert result["valid"] is True
        assert len(result["validation_errors"]) == 0
        
        # 验证文件信息（使用正确的字段名）
        assert result["file_size"] > 0
        assert result["has_imports"] is True
        assert result["has_molecule_instances"] is True
        assert result["molecule_counts"]["EC"] == 2
        assert result["molecule_counts"]["Li"] == 1

    def test_validate_system_lt_invalid_syntax(self, work_dir):
        """测试validate_system_lt函数无效语法"""
        # 创建一个语法错误的system.lt文件（使用UTF-8编码）
        system_lt_path = Path(work_dir) / "invalid_system.lt"
        invalid_content = """import "EC.lt"

system = {
  ecs = new EC[2]
  # 缺少闭合括号
"""
        # 确保使用UTF-8编码写入
        system_lt_path.write_text(invalid_content, encoding='utf-8')
        
        result = validate_system_lt(str(system_lt_path))
        
        # 验证验证失败
        assert result["success"] is True
        assert result["valid"] is False
        assert len(result["validation_errors"]) > 0

    def test_validate_system_lt_nonexistent_file(self):
        """测试validate_system_lt函数不存在文件"""
        result = validate_system_lt("nonexistent_system.lt")
        
        # 验证验证失败
        assert result["success"] is False
        assert result["valid"] is False
        assert result["error"] is not None


class TestIntegrationWorkflow:
    """完整流程整合测试类"""

    @pytest.fixture
    def full_test_environment(self):
        """创建完整测试环境"""
        temp_root = tempfile.mkdtemp(prefix="moltemplate_full_test_")
        
        # 创建PDB文件
        pdb_file = Path(temp_root) / "packed_system.pdb"
        pdb_content = """
ATOM      1  C1  EC     1       0.000   0.000   0.000  1.00  0.00           C
ATOM      2  C2  EC     1       1.000   0.000   0.000  1.00  0.00           C
ATOM      3  O1  EC     1       2.000   0.000   0.000  1.00  0.00           O
TER
ATOM      4  LI  Li     2      10.000   0.000   0.000  1.00  0.00           Li
TER
END
"""
        pdb_file.write_text(pdb_content)
        
        # 创建模板库
        template_lib = Path(temp_root) / "templates"
        template_lib.mkdir(parents=True, exist_ok=True)
        
        # 创建EC模板
        ec_dir = template_lib / "EC"
        ec_dir.mkdir(parents=True, exist_ok=True)
        
        ec_lt = ec_dir / "EC.lt"
        ec_lt_content = """
EC {
  write_once("Data Masses") {
    @atom:C1 12.0
    @atom:C2 12.0
    @atom:O1 16.0
  }
  
  write('Data Atoms') {
    $atom:1 @atom:C1 $mol:... 0.0 0.0 0.0 0.0
    $atom:2 @atom:C2 $mol:... 0.0 1.0 0.0 0.0
    $atom:3 @atom:O1 $mol:... 0.0 2.0 0.0 0.0
  }
}
"""
        ec_lt.write_text(ec_lt_content)
        
        # 创建Li模板
        li_dir = template_lib / "Li"
        li_dir.mkdir(parents=True, exist_ok=True)
        
        li_lt = li_dir / "Li.lt"
        li_lt_content = """
Li {
  write_once("Data Masses") {
    @atom:LI 6.9
  }
  
  write('Data Atoms') {
    $atom:1 @atom:LI $mol:... 1.0 0.0 0.0 0.0
  }
}
"""
        li_lt.write_text(li_lt_content)
        
        # 创建工作目录
        work_dir = Path(temp_root) / "work"
        work_dir.mkdir(parents=True, exist_ok=True)
        
        # 创建配方JSON文件
        formula_json = Path(temp_root) / "formula.json"
        formula_data = {
            "molecules": [
                {"name": "EC", "count": 1},
                {"name": "Li", "count": 1}
            ],
            "box_size": {"x": 50.0, "y": 50.0, "z": 50.0}
        }
        formula_json.write_text(json.dumps(formula_data))
        
        yield {
            "pdb_file": str(pdb_file),
            "template_lib": str(template_lib),
            "work_dir": str(work_dir),
            "formula_json": str(formula_json),
            "temp_root": temp_root
        }
        
        # 清理
        shutil.rmtree(temp_root, ignore_errors=True)

    def test_full_workflow_from_pdb_to_system_lt(self, full_test_environment):
        """测试从PDB文件到system.lt文件的完整流程"""
        env = full_test_environment
        
        # 步骤1: 解析PDB文件
        pdb_result = parse_pdb_file(env["pdb_file"])
        assert pdb_result["success"] is True
        assert pdb_result["total_atoms"] == 4
        
        # 步骤2: 获取分子模板
        molecule_names = ["EC", "Li"]
        template_result = fetch_molecule_lt_templates(molecule_names, env["template_lib"])
        assert template_result["success"] is True
        
        # 步骤3: 复制模板文件到工作目录
        copy_result = copy_lt_files_to_workdir(
            template_result["template_paths"],
            env["work_dir"]
        )
        assert copy_result["success"] is True
        
        # 步骤4: 创建分子实例
        molecule_counts = {"EC": 1, "Li": 1}
        instance_result = create_molecule_instances(
            molecule_names,
            molecule_counts,
            pdb_result
        )
        assert instance_result["success"] is True
        assert instance_result["total_instances"] == 2
        
        # 步骤5: 生成system.lt文件
        # 使用相对文件名（因为已复制到inputs目录）
        template_file_names = {"EC": "EC.lt", "Li": "Li.lt"}
        box_size = {"x": 50.0, "y": 50.0, "z": 50.0}
        output_path = Path(env["work_dir"]) / "inputs" / "system.lt"
        
        system_lt_result = generate_system_lt_file(
            template_file_names,
            instance_result["instances"],
            box_size,
            forcefield_type="oplsaa",
            output_path=str(output_path)
        )
        assert system_lt_result["success"] is True
        
        # 步骤6: 验证system.lt文件
        validation_result = validate_system_lt(str(output_path))
        assert validation_result["success"] is True
        assert validation_result["valid"] is True
        
        # 验证最终文件存在
        assert output_path.exists()
        
        # 验证文件内容包含必要元素
        content = output_path.read_text()
        assert "import" in content
        assert "system = {" in content
        assert "Data Atoms" in content
        assert "Data Boundary" in content

    def test_workflow_with_missing_template(self, full_test_environment):
        """测试缺失模板的流程处理"""
        env = full_test_environment
        
        # 使用不存在的分子名称
        molecule_names = ["EC", "DMC"]  # DMC模板不存在
        
        # 获取分子模板（应该失败）
        template_result = fetch_molecule_lt_templates(molecule_names, env["template_lib"])
        assert template_result["success"] is False
        assert "DMC" in template_result["missing_molecules"]

    def test_workflow_with_invalid_pdb(self, full_test_environment):
        """测试无效PDB文件的流程处理"""
        env = full_test_environment
        
        # 使用不存在的PDB文件
        pdb_result = parse_pdb_file("nonexistent.pdb")
        assert pdb_result["success"] is False
        
        # 后续步骤应该无法执行
        molecule_names = ["EC", "Li"]
        molecule_counts = {"EC": 1, "Li": 1}
        
        instance_result = create_molecule_instances(
            molecule_names,
            molecule_counts,
            pdb_result
        )
        assert instance_result["success"] is False


class TestErrorHandling:
    """错误处理和异常情况测试类"""

    def test_empty_molecule_names(self):
        """测试空分子名称列表"""
        # 创建临时模板库
        temp_dir = tempfile.mkdtemp(prefix="empty_test_")
        try:
            result = fetch_molecule_lt_templates([], temp_dir)
            
            # 验证处理空列表（空列表应该视为成功）
            assert result["success"] is True
            assert len(result["found_molecules"]) == 0
            assert len(result["missing_molecules"]) == 0
        finally:
            shutil.rmtree(temp_dir, ignore_errors=True)

    def test_invalid_box_size(self):
        """测试无效盒子尺寸"""
        # 测试空盒子尺寸（函数会返回失败）
        result = generate_box_boundary({})
        # generate_box_boundary对空字典会返回失败
        assert result["success"] is False
        assert result["error"] is not None
        
        # 测试缺失维度（函数会使用默认值50.0，返回成功）
        result = generate_box_boundary({"x": 50.0})
        assert result["success"] is True  # 函数会使用默认值

    def test_mismatched_molecule_counts(self):
        """测试分子数量不匹配"""
        # 创建简单的PDB解析结果
        pdb_result = {
            "success": True,
            "atoms": [],
            "ter_records": [],
            "total_atoms": 0
        }
        
        molecule_names = ["EC"]
        molecule_counts = {"EC": 100}  # 与实际不符
        
        result = create_molecule_instances(molecule_names, molecule_counts, pdb_result)
        
        # 验证处理失败
        assert result["success"] is False
        assert result["error"] is not None

    def test_concurrent_file_operations(self):
        """测试并发文件操作（模拟）"""
        temp_dir = tempfile.mkdtemp(prefix="concurrent_test_")
        
        try:
            # 创建多个工作目录，模拟并发场景
            work_dirs = []
            for i in range(3):
                work_dir = Path(temp_dir) / f"work_{i}"
                work_dir.mkdir(parents=True, exist_ok=True)
                work_dirs.append(str(work_dir))
            
            # 创建模板文件
            template_paths = {}
            for molecule_name in ["EC", "Li"]:
                lt_file = Path(temp_dir) / f"{molecule_name}.lt"
                lt_file.write_text(f"{molecule_name} {{ }}")
                template_paths[molecule_name] = str(lt_file)
            
            # 并发复制到不同工作目录
            results = []
            for work_dir in work_dirs:
                result = copy_lt_files_to_workdir(template_paths, work_dir)
                results.append(result)
            
            # 验证所有操作都成功
            for result in results:
                assert result["success"] is True
                
        finally:
            shutil.rmtree(temp_dir, ignore_errors=True)


if __name__ == "__main__":
    pytest.main([__file__, "-v", "-s"])