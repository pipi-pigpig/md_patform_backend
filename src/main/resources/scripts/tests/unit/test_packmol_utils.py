"""
Packmol工具模块单元测试

测试modeling.packmol子模块中的所有功能:
- PackmolRunner类功能测试
- tolerance参数固定为2.0测试
- 输入脚本生成测试
- 输出和错误信息捕获测试
- 临时文件清理测试
- PDB文件验证测试
- 分子模板获取测试
"""

import pytest
import os
import tempfile
import shutil
from pathlib import Path
from unittest.mock import Mock, patch, MagicMock
import subprocess

# 导入Packmol模块（使用相对导入或绝对导入）
import sys
sys.path.insert(0, str(Path(__file__).parent.parent.parent))

from modeling.packmol import (
    PackmolRunner,
    PACKMOL_TOLERANCE_FIXED,
    PACKMOL_MAX_ITERATIONS_DEFAULT,
    PACKMOL_DEFAULT_TIMEOUT,
    PACKMOL_MAX_RETRY_COUNT,
    PACKMOL_BOX_SCALE_FACTOR,
    run_packmol_packing,
    fetch_molecule_templates,
    copy_pdb_files_to_workdir,
    generate_packmol_input,
    run_packmol,
    create_pdb_from_molecule,
)


class TestPackmolRunner:
    """PackmolRunner类测试"""
    
    def test_tolerance_fixed_value(self):
        """测试tolerance参数固定为2.0"""
        runner = PackmolRunner()
        assert runner.tolerance == 2.0
        assert runner.tolerance == PACKMOL_TOLERANCE_FIXED
    
    def test_tolerance_cannot_be_modified_via_constructor(self):
        """测试构造函数不接受tolerance参数"""
        runner = PackmolRunner()
        assert runner.tolerance == 2.0
        
        runner2 = PackmolRunner(max_iterations=200)
        assert runner2.tolerance == 2.0
    
    def test_default_max_iterations(self):
        """测试默认最大迭代次数"""
        runner = PackmolRunner()
        assert runner.max_iterations == PACKMOL_MAX_ITERATIONS_DEFAULT
    
    def test_custom_max_iterations(self):
        """测试自定义最大迭代次数"""
        runner = PackmolRunner(max_iterations=500)
        assert runner.max_iterations == 500
    
    def test_temp_dir_creation(self):
        """测试临时目录创建"""
        with tempfile.TemporaryDirectory() as tmpdir:
            runner = PackmolRunner(temp_dir=tmpdir)
            assert runner.temp_dir.exists()
            assert runner.temp_dir == Path(tmpdir)
    
    def test_default_temp_dir(self):
        """测试默认临时目录"""
        runner = PackmolRunner()
        expected_dir = Path("temp") / "packmol_temp"
        assert runner.temp_dir == expected_dir


class TestGeneratePackmolInputScript:
    """Packmol输入脚本生成测试"""
    
    def test_generate_input_script_basic(self):
        """测试基本输入脚本生成"""
        with tempfile.TemporaryDirectory() as tmpdir:
            runner = PackmolRunner(temp_dir=tmpdir)
            
            molecules = [
                {"name": "EC", "count": 100, "pdb_file": "EC.pdb"},
                {"name": "Li", "count": 50, "pdb_file": "Li.pdb"},
            ]
            
            box_size = {"x": 50.0, "y": 50.0, "z": 50.0}
            output_path = "packed_system.pdb"
            
            input_file = runner.generate_packmol_input_script(
                molecules=molecules,
                box_size=box_size,
                output_pdb_path=output_path,
                seed=12345,
            )
            
            assert Path(input_file).exists()
            assert Path(input_file).name == "packmol.inp"
            
            with open(input_file, "r") as f:
                content = f.read()
            
            assert "tolerance 2.0" in content
            assert "seed 12345" in content
            assert "structure EC.pdb" in content
            assert "number 100" in content
            assert "inside box 0. 0. 0. 50.00 50.00 50.00" in content
            assert "structure Li.pdb" in content
            assert "number 50" in content
            assert "output packed_system.pdb" in content
    
    def test_tolerance_always_2_0_in_script(self):
        """测试脚本中tolerance始终为2.0"""
        with tempfile.TemporaryDirectory() as tmpdir:
            runner = PackmolRunner(temp_dir=tmpdir)
            
            molecules = [{"name": "EC", "count": 10, "pdb_file": "EC.pdb"}]
            box_size = {"x": 30.0, "y": 30.0, "z": 30.0}
            
            input_file = runner.generate_packmol_input_script(
                molecules=molecules,
                box_size=box_size,
                output_pdb_path="output.pdb",
            )
            
            with open(input_file, "r") as f:
                content = f.read()
            
            assert "tolerance 2.0" in content
            assert "tolerance 3.0" not in content
            assert "tolerance 1.0" not in content
    
    def test_input_script_with_default_seed(self):
        """测试使用默认随机种子"""
        with tempfile.TemporaryDirectory() as tmpdir:
            runner = PackmolRunner(temp_dir=tmpdir)
            
            molecules = [{"name": "EC", "count": 10, "pdb_file": "EC.pdb"}]
            box_size = {"x": 30.0, "y": 30.0, "z": 30.0}
            
            input_file = runner.generate_packmol_input_script(
                molecules=molecules,
                box_size=box_size,
                output_pdb_path="output.pdb",
            )
            
            with open(input_file, "r") as f:
                content = f.read()
            
            assert "seed " in content
    
    def test_input_script_format(self):
        """测试输入脚本格式正确性"""
        with tempfile.TemporaryDirectory() as tmpdir:
            runner = PackmolRunner(temp_dir=tmpdir)
            
            molecules = [
                {"name": "EC", "count": 100, "pdb_file": "EC.pdb"},
                {"name": "PF6", "count": 50, "pdb_file": "PF6.pdb"},
            ]
            
            box_size = {"x": 60.0, "y": 60.0, "z": 60.0}
            
            input_file = runner.generate_packmol_input_script(
                molecules=molecules,
                box_size=box_size,
                output_pdb_path="packed.pdb",
            )
            
            with open(input_file, "r") as f:
                lines = f.readlines()
            
            assert lines[0].strip() == "tolerance 2.0"
            assert lines[1].strip().startswith("seed")
            assert lines[2].strip().startswith("maxit")
            
            assert "end structure" in [l.strip() for l in lines]


class TestExecutePackmol:
    """Packmol执行测试"""
    
    @patch('subprocess.run')
    def test_execute_packmol_success(self, mock_run):
        """测试Packmol执行成功"""
        mock_result = Mock()
        mock_result.returncode = 0
        mock_result.stdout = "Packmol completed successfully"
        mock_result.stderr = ""
        mock_run.return_value = mock_result
        
        runner = PackmolRunner()
        
        input_file = runner.temp_dir / "packmol.inp"
        input_file.parent.mkdir(parents=True, exist_ok=True)
        with open(input_file, "w") as f:
            f.write("tolerance 2.0\nseed 12345\n")
        
        result = runner.execute_packmol(str(input_file))
        
        assert result["success"] is True
        assert result["return_code"] == 0
        assert result["stdout"] == "Packmol completed successfully"
        assert result["stderr"] == ""
        assert result["error_type"] is None
    
    @patch('subprocess.run')
    def test_execute_packmol_failure(self, mock_run):
        """测试Packmol执行失败"""
        mock_result = Mock()
        mock_result.returncode = 1
        mock_result.stdout = ""
        mock_result.stderr = "Error: Overlap detected"
        mock_run.return_value = mock_result
        
        runner = PackmolRunner()
        
        input_file = runner.temp_dir / "packmol.inp"
        input_file.parent.mkdir(parents=True, exist_ok=True)
        with open(input_file, "w") as f:
            f.write("tolerance 2.0\nseed 12345\n")
        
        result = runner.execute_packmol(str(input_file))
        
        assert result["success"] is False
        assert result["return_code"] == 1
        assert result["stderr"] == "Error: Overlap detected"
        assert result["error_type"] == "execution_error"
    
    @patch('subprocess.run')
    def test_execute_packmol_timeout(self, mock_run):
        """测试Packmol执行超时"""
        mock_run.side_effect = subprocess.TimeoutExpired(
            cmd="packmol",
            timeout=3600,
        )
        
        runner = PackmolRunner()
        
        input_file = runner.temp_dir / "packmol.inp"
        input_file.parent.mkdir(parents=True, exist_ok=True)
        with open(input_file, "w") as f:
            f.write("tolerance 2.0\nseed 12345\n")
        
        result = runner.execute_packmol(str(input_file), timeout=3600)
        
        assert result["success"] is False
        assert result["return_code"] == -1
        assert result["error_type"] == "timeout"
    
    @patch('subprocess.run')
    def test_execute_packmol_not_found(self, mock_run):
        """测试Packmol可执行文件未找到"""
        mock_run.side_effect = FileNotFoundError("packmol not found")
        
        runner = PackmolRunner(packmol_path="nonexistent_packmol")
        
        input_file = runner.temp_dir / "packmol.inp"
        input_file.parent.mkdir(parents=True, exist_ok=True)
        with open(input_file, "w") as f:
            f.write("tolerance 2.0\nseed 12345\n")
        
        result = runner.execute_packmol(str(input_file))
        
        assert result["success"] is False
        assert result["return_code"] == -2
        assert result["error_type"] == "executable_not_found"
    
    @patch('subprocess.run')
    def test_execute_captures_stdout_stderr(self, mock_run):
        """测试捕获stdout和stderr输出"""
        mock_result = Mock()
        mock_result.returncode = 0
        mock_result.stdout = "Line 1\nLine 2\nLine 3\nSuccess!"
        mock_result.stderr = "Warning: small overlap"
        mock_run.return_value = mock_result
        
        runner = PackmolRunner()
        
        input_file = runner.temp_dir / "packmol.inp"
        input_file.parent.mkdir(parents=True, exist_ok=True)
        with open(input_file, "w") as f:
            f.write("tolerance 2.0\n")
        
        result = runner.execute_packmol(str(input_file))
        
        assert "Line 1" in result["stdout"]
        assert "Success!" in result["stdout"]
        assert "Warning" in result["stderr"]


class TestValidatePdbOutput:
    """PDB输出验证测试"""
    
    def test_validate_valid_pdb(self):
        """测试验证有效PDB文件"""
        with tempfile.TemporaryDirectory() as tmpdir:
            pdb_content = """
ATOM      1  C   EC     1       1.000   2.000   3.000  1.00  0.00           C
ATOM      2  H   EC     1       1.500   2.500   3.500  1.00  0.00           H
ATOM      3  O   EC     1       2.000   3.000   4.000  1.00  0.00           O
END
"""
            pdb_file = Path(tmpdir) / "packed_system.pdb"
            pdb_file.write_text(pdb_content)
            
            runner = PackmolRunner(temp_dir=tmpdir)
            result = runner.validate_pdb_output(str(pdb_file))
            
            assert result["valid"] is True
            assert result["atom_count"] == 3
            assert result["errors"] == []
    
    def test_validate_empty_pdb(self):
        """测试验证空PDB文件"""
        with tempfile.TemporaryDirectory() as tmpdir:
            pdb_file = Path(tmpdir) / "empty.pdb"
            pdb_file.write_text("")
            
            runner = PackmolRunner(temp_dir=tmpdir)
            result = runner.validate_pdb_output(str(pdb_file))
            
            assert result["valid"] is False
            assert "PDB文件为空" in result["errors"]
    
    def test_validate_nonexistent_pdb(self):
        """测试验证不存在PDB文件"""
        with tempfile.TemporaryDirectory() as tmpdir:
            runner = PackmolRunner(temp_dir=tmpdir)
            result = runner.validate_pdb_output(str(Path(tmpdir) / "nonexistent.pdb"))
            
            assert result["valid"] is False
            assert any("PDB文件不存在" in e for e in result["errors"])
    
    def test_validate_pdb_with_expected_atoms(self):
        """测试验证PDB文件原子数量"""
        with tempfile.TemporaryDirectory() as tmpdir:
            pdb_content = """
ATOM      1  C   EC     1       1.000   2.000   3.000  1.00  0.00           C
ATOM      2  H   EC     1       1.500   2.500   3.500  1.00  0.00           H
ATOM      3  O   EC     1       2.000   3.000   4.000  1.00  0.00           O
END
"""
            pdb_file = Path(tmpdir) / "packed.pdb"
            pdb_file.write_text(pdb_content)
            
            runner = PackmolRunner(temp_dir=tmpdir)
            
            result_match = runner.validate_pdb_output(str(pdb_file), expected_atoms=3)
            assert result_match["valid"] is True
            
            result_no_match = runner.validate_pdb_output(str(pdb_file), expected_atoms=100)
            assert len(result_no_match["errors"]) > 0
    
    def test_validate_pdb_counts_residues_and_chains(self):
        """测试PDB验证统计残基和链"""
        with tempfile.TemporaryDirectory() as tmpdir:
            pdb_content = """
ATOM      1  C   EC     1       1.000   2.000   3.000  1.00  0.00           C
ATOM      2  H   EC     1       1.500   2.500   3.500  1.00  0.00           H
ATOM      3  C   EC     2       3.000   4.000   5.000  1.00  0.00           C
ATOM      4  H   EC     2       3.500   4.500   5.500  1.00  0.00           H
ATOM      5  C   Li     3 A     4.000   5.000   6.000  1.00  0.00           C
ATOM      6  H   Li     3 A     4.500   5.500   6.500  1.00  0.00           H
END
"""
            pdb_file = Path(tmpdir) / "multi.pdb"
            pdb_file.write_text(pdb_content)
            
            runner = PackmolRunner(temp_dir=tmpdir)
            result = runner.validate_pdb_output(str(pdb_file))
            
            assert result["atom_count"] == 6
            assert result["residue_count"] >= 1
            assert result["chain_count"] >= 1


class TestCleanupTempFiles:
    """临时文件清理测试"""
    
    def test_cleanup_basic_temp_files(self):
        """测试清理基本临时文件"""
        with tempfile.TemporaryDirectory() as tmpdir:
            runner = PackmolRunner(temp_dir=tmpdir)
            
            temp_file1 = Path(tmpdir) / "temp1.tmp"
            temp_file2 = Path(tmpdir) / "temp2.tmp"
            temp_file1.write_text("content1")
            temp_file2.write_text("content2")
            
            runner._temp_files = [temp_file1, temp_file2]
            
            result = runner.cleanup_temp_files()
            
            assert len(result["deleted_files"]) == 2
            assert not temp_file1.exists()
            assert not temp_file2.exists()
    
    def test_cleanup_keep_input_script(self):
        """测试保留packmol.inp"""
        with tempfile.TemporaryDirectory() as tmpdir:
            runner = PackmolRunner(temp_dir=tmpdir)
            
            input_script = Path(tmpdir) / "packmol.inp"
            input_script.write_text("tolerance 2.0")
            
            runner._temp_files = [input_script]
            
            result = runner.cleanup_temp_files(keep_input_script=True)
            
            assert len(result["kept_files"]) == 1
            assert input_script.exists()
    
    def test_cleanup_keep_output_pdb(self):
        """测试保留packed_system.pdb"""
        with tempfile.TemporaryDirectory() as tmpdir:
            runner = PackmolRunner(temp_dir=tmpdir)
            
            output_pdb = Path(tmpdir) / "packed_system.pdb"
            output_pdb.write_text("ATOM...")
            
            runner._temp_files = [output_pdb]
            
            result = runner.cleanup_temp_files(keep_output_pdb=True)
            
            assert len(result["kept_files"]) == 1
            assert output_pdb.exists()
    
    def test_cleanup_nonexistent_files(self):
        """测试清理不存在的文件"""
        with tempfile.TemporaryDirectory() as tmpdir:
            runner = PackmolRunner(temp_dir=tmpdir)
            
            nonexistent = Path(tmpdir) / "nonexistent.tmp"
            runner._temp_files = [nonexistent]
            
            result = runner.cleanup_temp_files()
            
            assert len(result["deleted_files"]) == 0
            assert len(result["errors"]) == 0
    
    def test_cleanup_clears_temp_files_list(self):
        """测试清理后清空临时文件列表"""
        with tempfile.TemporaryDirectory() as tmpdir:
            runner = PackmolRunner(temp_dir=tmpdir)
            
            temp_file = Path(tmpdir) / "temp.tmp"
            temp_file.write_text("content")
            runner._temp_files = [temp_file]
            
            runner.cleanup_temp_files()
            
            assert len(runner._temp_files) == 0


class TestFetchMoleculeTemplates:
    """分子模板获取测试"""
    
    def test_fetch_existing_templates(self):
        """测试获取存在的模板"""
        with tempfile.TemporaryDirectory() as tmpdir:
            template_dir = Path(tmpdir) / "templates"
            
            ec_dir = template_dir / "EC"
            ec_dir.mkdir(parents=True)
            ec_pdb = ec_dir / "EC.pdb"
            ec_pdb.write_text("ATOM...")
            
            li_dir = template_dir / "Li"
            li_dir.mkdir(parents=True)
            li_pdb = li_dir / "Li.pdb"
            li_pdb.write_text("ATOM...")
            
            result = fetch_molecule_templates(["EC", "Li"], str(template_dir))
            
            assert len(result["found"]) == 2
            assert "EC" in result["found"]
            assert "Li" in result["found"]
            assert len(result["missing"]) == 0
    
    def test_fetch_missing_templates(self):
        """测试检测缺失模板"""
        with tempfile.TemporaryDirectory() as tmpdir:
            template_dir = Path(tmpdir) / "templates"
            template_dir.mkdir(parents=True)
            
            result = fetch_molecule_templates(["EC", "Li", "PF6"], str(template_dir))
            
            assert len(result["missing"]) == 3
            assert "EC" in result["missing"]
            assert "Li" in result["missing"]
            assert "PF6" in result["missing"]
    
    def test_fetch_partial_templates(self):
        """测试部分模板存在"""
        with tempfile.TemporaryDirectory() as tmpdir:
            template_dir = Path(tmpdir) / "templates"
            
            ec_dir = template_dir / "EC"
            ec_dir.mkdir(parents=True)
            ec_pdb = ec_dir / "EC.pdb"
            ec_pdb.write_text("ATOM...")
            
            result = fetch_molecule_templates(["EC", "Li"], str(template_dir))
            
            assert len(result["found"]) == 1
            assert "EC" in result["found"]
            assert len(result["missing"]) == 1
            assert "Li" in result["missing"]


class TestCopyPdbFilesToWorkdir:
    """PDB文件复制测试"""
    
    def test_copy_existing_pdb_files(self):
        """测试复制存在的PDB文件"""
        with tempfile.TemporaryDirectory() as tmpdir:
            template_dir = Path(tmpdir) / "templates"
            work_dir = Path(tmpdir) / "work"
            
            ec_dir = template_dir / "EC"
            ec_dir.mkdir(parents=True)
            ec_pdb = ec_dir / "EC.pdb"
            ec_pdb.write_text("ATOM content EC")
            
            result = copy_pdb_files_to_workdir(
                ["EC"],
                str(template_dir),
                str(work_dir),
            )
            
            assert result["success"] is True
            assert len(result["copied_files"]) == 1
            assert (work_dir / "EC.pdb").exists()
    
    def test_copy_missing_pdb_files(self):
        """测试复制缺失PDB文件"""
        with tempfile.TemporaryDirectory() as tmpdir:
            template_dir = Path(tmpdir) / "templates"
            work_dir = Path(tmpdir) / "work"
            
            result = copy_pdb_files_to_workdir(
                ["EC", "Li"],
                str(template_dir),
                str(work_dir),
            )
            
            assert result["success"] is False
            assert len(result["missing_templates"]) == 2
            assert "EC" in result["missing_templates"]
            assert "Li" in result["missing_templates"]
    
    def test_copy_creates_work_directory(self):
        """测试复制时创建工作目录"""
        with tempfile.TemporaryDirectory() as tmpdir:
            template_dir = Path(tmpdir) / "templates"
            work_dir = Path(tmpdir) / "new_work"
            
            assert not work_dir.exists()
            
            ec_dir = template_dir / "EC"
            ec_dir.mkdir(parents=True)
            ec_pdb = ec_dir / "EC.pdb"
            ec_pdb.write_text("ATOM")
            
            copy_pdb_files_to_workdir(["EC"], str(template_dir), str(work_dir))
            
            assert work_dir.exists()


class TestMoveOutputsToInputs:
    """输出文件移动测试"""
    
    def test_move_outputs_success(self):
        """测试成功移动输出文件"""
        with tempfile.TemporaryDirectory() as tmpdir:
            runner = PackmolRunner(temp_dir=tmpdir)
            
            input_script = Path(tmpdir) / "packmol.inp"
            input_script.write_text("tolerance 2.0")
            
            output_pdb = Path(tmpdir) / "packed_system.pdb"
            output_pdb.write_text("ATOM...")
            
            inputs_dir = Path(tmpdir) / "inputs"
            
            result = runner.move_outputs_to_inputs(
                str(input_script),
                str(output_pdb),
                str(inputs_dir),
            )
            
            assert result["success"] is True
            assert len(result["moved_files"]) == 2
            assert (inputs_dir / "packmol.inp").exists()
            assert (inputs_dir / "packed_system.pdb").exists()
    
    def test_move_outputs_creates_inputs_dir(self):
        """测试移动时创建inputs目录"""
        with tempfile.TemporaryDirectory() as tmpdir:
            runner = PackmolRunner(temp_dir=tmpdir)
            
            input_script = Path(tmpdir) / "packmol.inp"
            input_script.write_text("tolerance 2.0")
            
            output_pdb = Path(tmpdir) / "packed_system.pdb"
            output_pdb.write_text("ATOM...")
            
            inputs_dir = Path(tmpdir) / "new_inputs"
            assert not inputs_dir.exists()
            
            runner.move_outputs_to_inputs(
                str(input_script),
                str(output_pdb),
                str(inputs_dir),
            )
            
            assert inputs_dir.exists()


class TestCheckInstallation:
    """Packmol安装检查测试"""
    
    @patch('subprocess.run')
    def test_check_installation_installed(self, mock_run):
        """测试Packmol已安装"""
        mock_result = Mock()
        mock_result.returncode = 0
        mock_result.stdout = "packmol version 20.3.0"
        mock_run.return_value = mock_result
        
        runner = PackmolRunner()
        assert runner.check_installation() is True
    
    @patch('subprocess.run')
    def test_check_installation_not_installed(self, mock_run):
        """测试Packmol未安装"""
        mock_run.side_effect = FileNotFoundError()
        
        runner = PackmolRunner(packmol_path="nonexistent")
        assert runner.check_installation() is False


class TestRunPackmolPacking:
    """Packmol堆积主函数测试"""
    
    @patch('packmol_utils.PackmolRunner')
    def test_run_packmol_packing_missing_templates(self, mock_runner_class):
        """测试缺失模板时的处理"""
        with tempfile.TemporaryDirectory() as tmpdir:
            result = run_packmol_packing(
                molecules=[{"name": "EC", "count": 100}],
                box_size={"x": 50.0, "y": 50.0, "z": 50.0},
                job_dir=tmpdir,
                template_dir=None,
            )
            
            assert result["success"] is False
            assert len(result["errors"]) > 0
            assert "缺失" in result["errors"][0]
    
    @patch('packmol_utils.PackmolRunner')
    @patch('packmol_utils.fetch_molecule_templates')
    @patch('packmol_utils.copy_pdb_files_to_workdir')
    def test_run_packmol_packing_flow(self, mock_copy, mock_fetch, mock_runner_class):
        """测试Packmol堆积流程"""
        with tempfile.TemporaryDirectory() as tmpdir:
            mock_fetch.return_value = {
                "found": ["EC"],
                "missing": [],
                "templates": {"EC": {"pdb_path": "EC.pdb", "exists": True}},
            }
            
            mock_copy.return_value = {
                "success": True,
                "copied_files": ["EC.pdb"],
                "missing_templates": [],
                "errors": [],
            }
            
            mock_runner = Mock()
            mock_runner.check_installation.return_value = True
            mock_runner.generate_packmol_input_script.return_value = str(Path(tmpdir) / "packmol.inp")
            mock_runner.execute_packmol.return_value = {
                "success": True,
                "stdout": "Success",
                "stderr": "",
                "return_code": 0,
            }
            mock_runner.validate_pdb_output.return_value = {
                "valid": True,
                "atom_count": 100,
                "errors": [],
            }
            mock_runner.move_outputs_to_inputs.return_value = {
                "success": True,
                "moved_files": ["packmol.inp", "packed_system.pdb"],
                "errors": [],
            }
            mock_runner.cleanup_temp_files.return_value = {
                "deleted_files": [],
                "kept_files": [],
                "errors": [],
            }
            mock_runner.save_execution_log = Mock()
            
            mock_runner_class.return_value = mock_runner
            
            molecules = [{"name": "EC", "count": 100}]
            box_size = {"x": 50.0, "y": 50.0, "z": 50.0}
            
            result = run_packmol_packing(
                molecules=molecules,
                box_size=box_size,
                job_dir=tmpdir,
                template_dir=str(Path(tmpdir) / "templates"),
            )
            
            mock_fetch.assert_called_once()
            mock_copy.assert_called_once()
            mock_runner.check_installation.assert_called_once()
            mock_runner.generate_packmol_input_script.assert_called_once()
            mock_runner.execute_packmol.assert_called_once()
            mock_runner.validate_pdb_output.assert_called_once()


class TestConstants:
    """常量测试"""
    
    def test_tolerance_fixed_constant(self):
        """测试tolerance固定常量"""
        assert PACKMOL_TOLERANCE_FIXED == 2.0
    
    def test_default_timeout(self):
        """测试默认超时时间"""
        assert PACKMOL_DEFAULT_TIMEOUT == 3600
    
    def test_max_retry_count(self):
        """测试最大重试次数"""
        assert PACKMOL_MAX_RETRY_COUNT == 3
    
    def test_box_scale_factor(self):
        """测试盒子放大系数"""
        assert PACKMOL_BOX_SCALE_FACTOR == 1.1


class TestConvenienceFunctions:
    """便捷函数测试"""
    
    def test_generate_packmol_input_convenience(self):
        """测试便捷函数generate_packmol_input"""
        runner = PackmolRunner()
        
        molecules = [{"name": "EC", "count": 10, "pdb_file": "EC.pdb"}]
        box_size = {"x": 30.0, "y": 30.0, "z": 30.0}
        
        input_file = generate_packmol_input(
            molecules=molecules,
            box_size=box_size,
            output_path="output.pdb",
        )
        
        assert Path(input_file).exists()
    
    @patch('packmol_utils.PackmolRunner')
    def test_run_packmol_convenience(self, mock_runner_class):
        """测试便捷函数run_packmol"""
        mock_runner = Mock()
        mock_runner.execute_packmol.return_value = {"success": True}
        mock_runner_class.return_value = mock_runner
        
        result = run_packmol("packmol.inp")
        
        mock_runner.execute_packmol.assert_called_once()
        assert result["success"] is True
    
    def test_create_pdb_from_molecule_success(self):
        """测试create_pdb_from_molecule成功"""
        with tempfile.TemporaryDirectory() as tmpdir:
            template_dir = Path(tmpdir) / "templates"
            ec_dir = template_dir / "EC"
            ec_dir.mkdir(parents=True)
            ec_pdb = ec_dir / "EC.pdb"
            ec_pdb.write_text("ATOM content")
            
            output_dir = Path(tmpdir) / "output"
            
            result = create_pdb_from_molecule(
                "EC",
                str(output_dir),
                str(template_dir),
            )
            
            assert result is not None
            assert Path(result).exists()
            assert Path(result).name == "EC.pdb"
    
    def test_create_pdb_from_molecule_missing(self):
        """测试create_pdb_from_molecule模板缺失"""
        with tempfile.TemporaryDirectory() as tmpdir:
            result = create_pdb_from_molecule(
                "Nonexistent",
                tmpdir,
                tmpdir,
            )
            
            assert result is None


class TestExecutionLog:
    """执行日志测试"""
    
    def test_execution_log_recording(self):
        """测试执行日志记录"""
        runner = PackmolRunner()
        
        input_file = runner.temp_dir / "packmol.inp"
        input_file.parent.mkdir(parents=True, exist_ok=True)
        with open(input_file, "w") as f:
            f.write("tolerance 2.0")
        
        runner._execution_log = []
        
        with patch('subprocess.run') as mock_run:
            mock_result = Mock()
            mock_result.returncode = 0
            mock_result.stdout = "Success"
            mock_result.stderr = ""
            mock_run.return_value = mock_result
            
            runner.execute_packmol(str(input_file))
        
        assert len(runner._execution_log) > 0
        assert "开始执行Packmol" in runner._execution_log[0]
    
    def test_save_execution_log(self):
        """测试保存执行日志"""
        with tempfile.TemporaryDirectory() as tmpdir:
            runner = PackmolRunner(temp_dir=tmpdir)
            
            runner._execution_log = [
                "Line 1",
                "Line 2",
                "Line 3",
            ]
            
            log_path = Path(tmpdir) / "execution.log"
            runner.save_execution_log(str(log_path))
            
            assert log_path.exists()
            
            content = log_path.read_text()
            assert "Line 1" in content
            assert "Line 2" in content
            assert "Line 3" in content


if __name__ == "__main__":
    pytest.main([__file__, "-v"])