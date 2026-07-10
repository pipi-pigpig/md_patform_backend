"""
Moltemplate完整流程整合功能测试

测试run_moltemplate_system_generation及相关辅助函数
"""

import tempfile
import os
import json
import pytest
from pathlib import Path

from utils.moltemplate_utils import (
    StepExecutionResult,
    run_moltemplate_system_generation,
    run_moltemplate_system_generation_simple,
    parse_pdb_file,
    fetch_molecule_lt_templates,
    copy_lt_files_to_workdir,
    create_molecule_instances,
    generate_system_lt_file,
    validate_system_lt,
)


class TestMoltemplateSystemGeneration:
    """Moltemplate系统生成完整流程测试类"""

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
    def sample_formula_data(self):
        """创建测试配方数据"""
        return {
            "molecules": [
                {"name": "EC", "count": 2},
                {"name": "Li", "count": 1}
            ],
            "box_size": {"x": 50.0, "y": 50.0, "z": 50.0}
        }

    @pytest.fixture
    def sample_template_library(self):
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
        import shutil
        shutil.rmtree(temp_dir, ignore_errors=True)

    @pytest.fixture
    def sample_work_dir(self):
        """创建测试工作目录"""
        temp_dir = tempfile.mkdtemp(prefix="work_dir_")
        yield temp_dir
        # 清理
        import shutil
        shutil.rmtree(temp_dir, ignore_errors=True)

    @pytest.fixture
    def sample_formula_json_file(self, sample_formula_data):
        """创建测试配方JSON文件"""
        temp_file = tempfile.NamedTemporaryFile(mode='w', suffix='.json', delete=False)
        json.dump(sample_formula_data, temp_file)
        temp_file.close()
        yield temp_file.name
        # 清理
        os.unlink(temp_file.name)

    def test_step_execution_result_dataclass(self):
        """测试StepExecutionResult数据类"""
        step_result = StepExecutionResult(
            step_name="test_step",
            success=True,
            start_time=100.0,
            end_time=110.0,
            duration=10.0,
            result_data={"test": "data"},
            error_message=None
        )
        
        # 验证数据类属性
        assert step_result.step_name == "test_step"
        assert step_result.success is True
        assert step_result.duration == 10.0
        assert step_result.error_message is None

    def test_run_moltemplate_system_generation_success(
        self,
        sample_pdb_file,
        sample_formula_data,
        sample_work_dir,
        sample_template_library
    ):
        """测试run_moltemplate_system_generation成功场景"""
        result = run_moltemplate_system_generation(
            packed_pdb_path=sample_pdb_file,
            formula_data=sample_formula_data,
            work_dir_path=sample_work_dir,
            template_library_path=sample_template_library,
            forcefield_type="oplsaa"
        )
        
        # 验证执行成功
        assert result["success"] is True
        assert result["error"] is None
        assert result["failed_step"] is None
        
        # 验证system.lt文件路径
        assert result["system_lt_path"] != ""
        assert Path(result["system_lt_path"]).exists()
        
        # 验证执行摘要
        execution_summary = result["execution_summary"]
        assert execution_summary["total_steps"] == 5
        assert execution_summary["successful_steps"] >= 4  # 前4步必须成功
        
        # 验证步骤结果
        step_results = result["step_results"]
        assert len(step_results) == 5
        
        # 验证统计信息
        statistics = result["statistics"]
        assert statistics["total_atoms"] == 7
        assert statistics["total_molecules"] == 3
        assert statistics["molecule_types"] == 2
        assert statistics["forcefield_type"] == "oplsaa"

    def test_run_moltemplate_system_generation_invalid_pdb(
        self,
        sample_formula_data,
        sample_work_dir,
        sample_template_library
    ):
        """测试run_moltemplate_system_generation无效PDB文件"""
        result = run_moltemplate_system_generation(
            packed_pdb_path="nonexistent.pdb",
            formula_data=sample_formula_data,
            work_dir_path=sample_work_dir,
            template_library_path=sample_template_library,
            forcefield_type="oplsaa"
        )
        
        # 验证执行失败
        assert result["success"] is False
        assert result["error"] is not None
        assert result["failed_step"] == "parse_pdb_file"
        
        # 验证步骤结果
        step_results = result["step_results"]
        assert len(step_results) == 1  # 只有第一步执行了
        assert step_results[0]["success"] is False

    def test_run_moltemplate_system_generation_missing_template(
        self,
        sample_pdb_file,
        sample_work_dir
    ):
        """测试run_moltemplate_system_generation缺失模板文件"""
        # 使用不存在的模板库路径
        result = run_moltemplate_system_generation(
            packed_pdb_path=sample_pdb_file,
            formula_data={
                "molecules": [{"name": "EC", "count": 2}],
                "box_size": {"x": 50.0, "y": 50.0, "z": 50.0}
            },
            work_dir_path=sample_work_dir,
            template_library_path="nonexistent_template_lib",
            forcefield_type="oplsaa"
        )
        
        # 验证执行失败
        assert result["success"] is False
        assert result["error"] is not None
        assert result["failed_step"] in ["fetch_molecule_templates", "load_molecule_templates"]

    def test_run_moltemplate_system_generation_simple_success(
        self,
        sample_pdb_file,
        sample_formula_json_file,
        sample_work_dir,
        sample_template_library
    ):
        """测试run_moltemplate_system_generation_simple成功场景"""
        result = run_moltemplate_system_generation_simple(
            packed_pdb_path=sample_pdb_file,
            formula_json_path=sample_formula_json_file,
            work_dir_path=sample_work_dir,
            template_library_path=sample_template_library,
            forcefield_type="oplsaa"
        )
        
        # 验证执行成功
        assert result["success"] is True
        assert result["error"] is None

    def test_run_moltemplate_system_generation_simple_invalid_json(
        self,
        sample_pdb_file,
        sample_work_dir,
        sample_template_library
    ):
        """测试run_moltemplate_system_generation_simple无效JSON文件"""
        result = run_moltemplate_system_generation_simple(
            packed_pdb_path=sample_pdb_file,
            formula_json_path="nonexistent.json",
            work_dir_path=sample_work_dir,
            template_library_path=sample_template_library,
            forcefield_type="oplsaa"
        )
        
        # 验证执行失败
        assert result["success"] is False
        assert result["error"] is not None
        assert result["failed_step"] == "read_formula_json"

    def test_output_json_file(
        self,
        sample_pdb_file,
        sample_formula_data,
        sample_work_dir,
        sample_template_library
    ):
        """测试输出JSON结果文件"""
        output_json_path = Path(sample_work_dir) / "result.json"
        
        result = run_moltemplate_system_generation(
            packed_pdb_path=sample_pdb_file,
            formula_data=sample_formula_data,
            work_dir_path=sample_work_dir,
            template_library_path=sample_template_library,
            forcefield_type="oplsaa",
            output_json_path=str(output_json_path)
        )
        
        # 验证执行成功
        assert result["success"] is True
        
        # 验证JSON文件已生成
        assert output_json_path.exists()
        
        # 验证JSON文件内容
        with open(output_json_path, 'r') as f:
            json_result = json.load(f)
        
        assert json_result["success"] is True
        assert "statistics" in json_result
        assert "step_results" in json_result

    def test_execution_time_tracking(
        self,
        sample_pdb_file,
        sample_formula_data,
        sample_work_dir,
        sample_template_library
    ):
        """测试执行时间跟踪"""
        result = run_moltemplate_system_generation(
            packed_pdb_path=sample_pdb_file,
            formula_data=sample_formula_data,
            work_dir_path=sample_work_dir,
            template_library_path=sample_template_library,
            forcefield_type="oplsaa"
        )
        
        # 验证执行时间统计
        statistics = result["statistics"]
        execution_time = statistics["execution_time"]
        
        assert "total_duration" in execution_time
        # 注意：在mock/测试环境中，total_duration可能为0，这是正常的
        assert execution_time["total_duration"] >= 0
        
        assert "step_durations" in execution_time
        step_durations = execution_time["step_durations"]
        
        # 验证每个步骤都有耗时记录
        assert "parse_pdb_file" in step_durations
        assert "load_molecule_templates" in step_durations
        assert "create_molecule_instances" in step_durations
        assert "generate_system_lt" in step_durations
        assert "validate_system_lt" in step_durations
        
        # 验证每个步骤耗时都是非负数（测试环境可能为0）
        for step_name, duration in step_durations.items():
            assert duration >= 0


class TestMoltemplateIntegration:
    """Moltemplate集成测试类"""

    @pytest.fixture
    def full_test_environment(self):
        """创建完整测试环境"""
        # 创建临时目录
        temp_root = tempfile.mkdtemp(prefix="moltemplate_test_")
        
        # 创建PDB文件
        pdb_file = Path(temp_root) / "packed_system.pdb"
        pdb_content = """
ATOM      1  C1  EC     1       0.000   0.000   0.000  1.00  0.00           C
ATOM      2  C2  EC     1       1.000   0.000   0.000  1.00  0.00           C
ATOM      3  O1  EC     1       2.000   0.000   0.000  1.00  0.00           O
TER
END
"""
        pdb_file.write_text(pdb_content)
        
        # 创建模板库
        template_lib = Path(temp_root) / "templates"
        template_lib.mkdir(parents=True, exist_ok=True)
        
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
}
"""
        ec_lt.write_text(ec_lt_content)
        
        # 创建工作目录
        work_dir = Path(temp_root) / "work"
        work_dir.mkdir(parents=True, exist_ok=True)
        
        # 创建配方JSON文件
        formula_json = Path(temp_root) / "formula.json"
        formula_data = {
            "molecules": [{"name": "EC", "count": 1}],
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
        import shutil
        shutil.rmtree(temp_root, ignore_errors=True)

    def test_full_workflow_integration(self, full_test_environment):
        """测试完整工作流集成"""
        result = run_moltemplate_system_generation_simple(
            packed_pdb_path=full_test_environment["pdb_file"],
            formula_json_path=full_test_environment["formula_json"],
            work_dir_path=full_test_environment["work_dir"],
            template_library_path=full_test_environment["template_lib"],
            forcefield_type="oplsaa"
        )
        
        # 验证执行成功
        assert result["success"] is True
        
        # 验证system.lt文件已生成
        system_lt_path = Path(result["system_lt_path"])
        assert system_lt_path.exists()
        
        # 验证inputs目录已创建
        inputs_dir = Path(full_test_environment["work_dir"]) / "inputs"
        assert inputs_dir.exists()
        
        # 根据文件输入输出规则：分子模板文件不复制到inputs目录
        # inputs目录只存放任务输入文件（system.lt、packed_system.pdb等）
        # 分子模板保持在system_templates/molecule_templates/目录中
        
        # 验证system.lt文件内容包含正确的import路径
        with open(system_lt_path, 'r', encoding='utf-8') as f:
            content = f.read()
            # 应该使用相对路径指向system_templates目录
            assert "import" in content
            assert "EC.lt" in content or "ec.lt" in content


if __name__ == "__main__":
    pytest.main([__file__, "-v"])