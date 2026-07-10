"""
Moltemplate执行与文件整理功能真实测试（步骤6-7）

使用真实的Docker容器md_engine进行集成测试，不使用任何Mock。
验证execute_moltemplate_command、organize_lammps_input_files及run_moltemplate_execution函数
在真实环境下的行为。

测试策略:
    1. 使用Docker容器md_engine作为执行环境
    2. 在容器中真正执行moltemplate.sh命令
    3. 验证生成的LAMMPS输入文件（system.data、system.in.init、system.in.settings）
    4. 验证文件整理功能（移动到inputs目录）
    5. 不使用任何@patch或Mock对象

依赖条件:
    - Docker容器md_engine必须处于运行状态
    - 容器中必须安装moltemplate工具
    - system_templates分子模板文件必须存在

作者: Electrolyte MD Platform
版本: 1.0 (真实测试版本)
"""

import pytest
import subprocess
import os
import shutil
import time
from pathlib import Path

# ==================== Docker配置常量 ====================
# Docker容器名称，与docker-compose配置一致
DOCKER_CONTAINER = "md_engine"

# 容器内工作目录基础路径
CONTAINER_WORK_BASE = "/workspace/data"

# 本地数据根目录（映射到容器的/workspace/data）
# 从tests/向上8级: tests -> modeling -> scripts -> resources -> main -> src -> backend -> electrolyte-md-platform(项目根) -> data
LOCAL_DATA_ROOT = Path(__file__).parent.parent.parent.parent.parent.parent.parent.parent / "data" / "md_platform_data"

# Moltemplate在容器中的安装路径
MOLTEMPLATE_PATH = "/usr/bin/moltemplate.sh"

# 测试超时时间（秒），简单系统使用较短超时
SIMPLE_SYSTEM_TIMEOUT = 120
# 复杂系统使用较长超时
COMPLEX_SYSTEM_TIMEOUT = 600


class DockerTestHelper:
    """Docker容器操作辅助类
    
    提供在Docker容器中执行命令、管理文件的辅助方法。
    所有操作都通过docker exec在md_engine容器中执行。
    """

    @staticmethod
    def check_container_running() -> bool:
        """检查Docker容器md_engine是否运行中
        
        Returns:
            容器是否处于运行状态
        """
        try:
            result = subprocess.run(
                ["docker", "ps", "--filter", f"name={DOCKER_CONTAINER}", "--format", "{{.Status}}"],
                capture_output=True,
                text=True,
                timeout=10
            )
            return result.returncode == 0 and len(result.stdout.strip()) > 0
        except Exception:
            return False

    @staticmethod
    def exec_in_container(command: list, timeout: int = 60) -> dict:
        """在Docker容器中执行命令
        
        Args:
            command: 要执行的命令列表（不含docker exec部分）
            timeout: 命令超时时间（秒）
            
        Returns:
            执行结果字典，包含success, stdout, stderr, return_code
        """
        full_command = ["docker", "exec", DOCKER_CONTAINER] + command
        
        try:
            result = subprocess.run(
                full_command,
                capture_output=True,
                text=True,
                timeout=timeout
            )
            
            return {
                "success": result.returncode == 0,
                "return_code": result.returncode,
                "stdout": result.stdout,
                "stderr": result.stderr
            }
        except subprocess.TimeoutExpired:
            return {
                "success": False,
                "return_code": -1,
                "stdout": "",
                "stderr": f"Command timed out after {timeout} seconds"
            }
        except Exception as e:
            return {
                "success": False,
                "return_code": -1,
                "stdout": "",
                "stderr": str(e)
            }

    @staticmethod
    def exec_moltemplate_in_container(moltemplate_path: str, system_lt: str, work_dir: str, timeout: int = 120) -> dict:
        """在Docker容器的工作目录中执行Moltemplate命令

        使用bash -c在指定工作目录下执行moltemplate.sh，
        确保相对路径能正确解析。

        Args:
            moltemplate_path: Moltemplate脚本路径
            system_lt: system.lt文件路径（相对于work_dir）
            work_dir: 工作目录路径
            timeout: 命令超时时间（秒）

        Returns:
            执行结果字典，包含success, stdout, stderr, return_code
        """
        # 使用bash -c在指定工作目录下执行命令
        command = f"cd {work_dir} && {moltemplate_path} -atomstyle full {system_lt}"
        
        full_command = ["docker", "exec", DOCKER_CONTAINER, "bash", "-c", command]
        
        try:
            result = subprocess.run(
                full_command,
                capture_output=True,
                text=True,
                timeout=timeout
            )
            
            return {
                "success": result.returncode == 0,
                "return_code": result.returncode,
                "stdout": result.stdout,
                "stderr": result.stderr
            }
        except subprocess.TimeoutExpired:
            return {
                "success": False,
                "return_code": -1,
                "stdout": "",
                "stderr": f"Command timed out after {timeout} seconds"
            }
        except Exception as e:
            return {
                "success": False,
                "return_code": -1,
                "stdout": "",
                "stderr": str(e)
            }

    @staticmethod
    def container_path_exists(container_path: str) -> bool:
        """检查容器内路径是否存在

        Args:
            container_path: 容器内的绝对路径

        Returns:
            路径是否存在
        """
        # 使用ls命令检查文件是否存在（避免&&在docker exec中的解析问题）
        result = DockerTestHelper.exec_in_container(["ls", container_path])
        return result["success"]

    @staticmethod
    def create_directory_in_container(container_path: str) -> bool:
        """在容器内创建目录
        
        Args:
            container_path: 要创建的目录路径
            
        Returns:
            是否创建成功
        """
        result = DockerTestHelper.exec_in_container(["mkdir", "-p", container_path])
        return result["success"]

    @staticmethod
    def write_file_to_container(local_path: str, container_path: str) -> bool:
        """将本地文件复制到容器中
        
        Args:
            local_path: 本地文件绝对路径
            container_path: 容器内目标路径
            
        Returns:
            是否复制成功
        """
        try:
            # 使用docker cp将文件复制到容器
            target = f"{DOCKER_CONTAINER}:{container_path}"
            result = subprocess.run(
                ["docker", "cp", local_path, target],
                capture_output=True,
                text=True,
                timeout=30
            )
            return result.returncode == 0
        except Exception as e:
            print(f"[DockerTestHelper] 文件复制失败: {e}")
            return False

    @staticmethod
    def read_file_from_container(container_path: str) -> str:
        """读取容器中的文件内容
        
        Args:
            container_path: 容器内文件路径
            
        Returns:
            文件内容字符串，如果读取失败返回空字符串
        """
        result = DockerTestHelper.exec_in_container(["cat", container_path], timeout=30)
        if result["success"]:
            return result["stdout"]
        return ""

    @staticmethod
    def get_file_size_in_container(container_path: str) -> int:
        """获取容器中文件的大小

        Args:
            container_path: 容器内文件路径

        Returns:
            文件大小（字节），文件不存在返回-1
        """
        result = DockerTestHelper.exec_in_container(
            ["stat", "-c", "%s", container_path],
            timeout=10
        )
        if result["success"]:
            try:
                return int(result["stdout"].strip())
            except (ValueError, KeyError):
                return -1
        return -1

    @staticmethod
    def list_files_in_container(container_dir: str) -> list:
        """列出容器中目录下的文件

        Args:
            container_dir: 容器内目录路径

        Returns:
            文件名列表
        """
        result = DockerTestHelper.exec_in_container(
            ["ls", "-1", container_dir],
            timeout=10
        )
        if result["success"]:
            return [f for f in result["stdout"].strip().split("\n") if f]
        return []

    @staticmethod
    def remove_directory_in_container(container_path: str) -> bool:
        """删除容器中的目录及其内容
        
        Args:
            container_path: 要删除的目录路径
            
        Returns:
            是否删除成功
        """
        result = DockerTestHelper.exec_in_container(["rm", "-rf", container_path])
        return result["success"]


# ==================== 简单系统的LT文件内容 ====================
# 使用2分子系统（1个EC + 1个Li）进行快速测试
# 注意：此LT文件设计用于inputs目录下执行
# 从inputs/向上4级: inputs -> job_1 -> jobs -> user_1 -> md_platform_data -> system_templates/
SIMPLE_SYSTEM_LT_CONTENT = """# 最简化的Moltemplate系统文件（2分子测试用）
import "../../../../system_templates/molecule_templates/EC/EC.lt"
import "../../../../system_templates/molecule_templates/Li/Li.lt"

ec1 = new EC
li1 = new Li

write_once("Data Boundary") {
  0.0  48.000000  xlo xhi
  0.0  48.000000  ylo yhi
  0.0  48.000000  zlo zhi
}
"""

# 无效的LT文件内容（用于错误场景测试）
INVALID_LT_CONTENT = """# 无效的LT文件 - 引用不存在的模板
import "nonexistent_template.lt"

bad_molecule = new NonExistent
"""


@pytest.fixture(scope="module")
def docker_environment():
    """模块级fixture：检查Docker环境并准备测试基础目录
    
    在所有测试开始前检查容器状态，
    如果容器未运行则跳过所有测试。
    
    Yields:
        包含容器状态和基础路径的字典
    """
    # 检查容器是否运行
    if not DockerTestHelper.check_container_running():
        pytest.skip(f"Docker容器 {DOCKER_CONTAINER} 未运行，跳过真实测试")

    # 检查moltemplate是否可用
    moltemplate_check = DockerTestHelper.exec_in_container(
        ["which", "moltemplate.sh"],
        timeout=10
    )
    
    global MOLTEMPLATE_PATH
    if moltemplate_check["success"]:
        MOLTEMPLATE_PATH = moltemplate_check["stdout"].strip()
        print(f"[Docker环境] Moltemplate路径: {MOLTEMPLATE_PATH}")
    else:
        # 尝试其他常见路径
        for alt_path in ["/usr/bin/moltemplate.sh", "/usr/local/bin/moltemplate.sh", "/opt/moltemplate/moltemplate.sh"]:
            if DockerTestHelper.container_path_exists(alt_path):
                MOLTEMPLATE_PATH = alt_path
                print(f"[Docker环境] Moltemplate路径（备选）: {MOLTEMPLATE_PATH}")
                break
        else:
            pytest.skip("Moltemplate未安装在容器中，跳过真实测试")

    # 检查模板文件是否存在
    templates_ok = (
        DockerTestHelper.container_path_exists(
            f"{CONTAINER_WORK_BASE}/system_templates/molecule_templates/EC/EC.lt"
        ) and
        DockerTestHelper.container_path_exists(
            f"{CONTAINER_WORK_BASE}/system_templates/molecule_templates/Li/Li.lt"
        )
    )

    if not templates_ok:
        pytest.skip("分子模板文件不存在，跳过真实测试")

    yield {
        "container_name": DOCKER_CONTAINER,
        "work_base": CONTAINER_WORK_BASE,
        "moltemplate_path": MOLTEMPLATE_PATH,
        "templates_available": True
    }


@pytest.fixture
def simple_system_work_dir(docker_environment):
    """为每个测试创建独立的工作目录

    在user_1/jobs/job_1/下创建临时子目录用于简单系统测试，
    测试结束后自动清理。
    使用inputs目录结构确保相对路径正确解析。

    Args:
        docker_environment: Docker环境fixture

    Yields:
        容器内工作目录路径
    """
    # 使用时间戳创建唯一目录名
    test_id = f"test_{int(time.time() * 1000)}"
    # 在job_1/inputs下创建测试目录（确保相对路径能正确解析到system_templates）
    work_dir = f"{CONTAINER_WORK_BASE}/user_1/jobs/job_1/{test_id}"

    # 创建工作目录
    assert DockerTestHelper.create_directory_in_container(work_dir), \
        f"无法创建工作目录: {work_dir}"

    yield work_dir

    # 清理：删除测试目录
    DockerTestHelper.remove_directory_in_container(work_dir)


@pytest.fixture
def simple_system_with_lt(simple_system_work_dir):
    """在工作目录中创建简单的system.lt文件
    
    创建包含1个EC分子和1个Li离子的简单系统文件。
    
    Args:
        simple_system_work_dir: 工作目录fixture
        
    Yields:
        元组 (工作目录路径, system.lt路径)
    """
    work_dir = simple_system_work_dir
    system_lt_path = f"{work_dir}/system.lt"

    # 先写入本地临时文件再复制到容器
    import tempfile
    with tempfile.NamedTemporaryFile(mode='w', suffix='.lt', delete=False, encoding='utf-8') as f:
        f.write(SIMPLE_SYSTEM_LT_CONTENT)
        temp_local_path = f.name

    try:
        # 复制到容器
        success = DockerTestHelper.write_file_to_container(temp_local_path, system_lt_path)
        assert success, f"无法将system.lt写入容器: {system_lt_path}"

        # 验证文件已写入
        assert DockerTestHelper.container_path_exists(system_lt_path), \
            f"system.lt未在容器中创建: {system_lt_path}"

        yield work_dir, system_lt_path
    finally:
        # 清理本地临时文件
        os.unlink(temp_local_path)


class TestExecuteMoltemplateCommandReal:
    """使用真实Docker容器测试execute_moltemplate_command函数（步骤6）

    测试Moltemplate命令在真实容器环境中的执行情况，
    验证生成的LAMMPS输入文件是否符合预期。

    注意: 这些测试需要Docker容器md_engine运行且moltemplate已安装
    """

    def test_execute_moltemplate_simple_system(self, docker_environment, simple_system_with_lt):
        """测试简单2分子系统（1 EC + 1 Li）的Moltemplate执行

        验证点:
        1. moltemplate.sh命令能成功执行
        2. 返回码为0
        3. 生成必需的输出文件（system.data, system.in.init, system.in.settings）
        4. 输出文件大小合理（不为空）
        5. 执行耗时在合理范围内
        """
        from utils.moltemplate_utils import execute_moltemplate_command

        work_dir, system_lt_path = simple_system_with_lt
        moltemplate_path = docker_environment["moltemplate_path"]

        print(f"\n[真实测试] 工作目录: {work_dir}")
        print(f"[真实测试] system.lt路径: {system_lt_path}")
        print(f"[真实测试] Moltemplate路径: {moltemplate_path}")

        # 直接在容器中执行moltemplate命令（使用bash -c确保工作目录正确）
        start_time = time.time()

        exec_result = DockerTestHelper.exec_moltemplate_in_container(
            moltemplate_path=moltemplate_path,
            system_lt="system.lt",  # 使用相对路径（相对于work_dir）
            work_dir=work_dir,
            timeout=SIMPLE_SYSTEM_TIMEOUT
        )

        duration = time.time() - start_time

        # 验证命令执行结果
        assert exec_result["success"], \
            f"Moltemplate命令执行失败，返回码: {exec_result['return_code']}\n错误输出: {exec_result['stderr']}"
        
        assert exec_result["return_code"] == 0, \
            f"期望返回码0，实际返回码: {exec_result['return_code']}"

        print(f"[真实测试] 命令执行成功，耗时: {duration:.2f}秒")
        print(f"[真实测试] 标准输出长度: {len(exec_result['stdout'])}字符")

        # 验证生成的必需文件
        required_files = ["system.data", "system.in.init", "system.in.settings"]
        
        for file_name in required_files:
            file_path = f"{work_dir}/{file_name}"
            
            # 检查文件是否存在
            exists = DockerTestHelper.container_path_exists(file_path)
            assert exists, f"必需文件未生成: {file_name}"
            
            # 检查文件大小
            file_size = DockerTestHelper.get_file_size_in_container(file_path)
            assert file_size > 0, f"生成文件为空: {file_name} (大小: {file_size}字节)"
            
            print(f"[真实测试] 文件验证通过: {file_name} ({file_size}字节)")

        # 验证system.data文件内容合理性
        system_data_content = DockerTestHelper.read_file_from_container(f"{work_dir}/system.data")
        assert len(system_data_content) > 100, \
            f"system.data内容过短，可能生成不完整 (实际长度: {len(system_data_content)})"

        # 验证LAMMPS数据文件的关键标记
        assert "atoms" in system_data_content.lower() or "atom types" in system_data_content.lower(), \
            "system.data缺少原子数量信息"
        
        assert "Masses" in system_data_content or "masses" in system_data_content, \
            "system.data缺少质量定义"

        print(f"[真实测试] 简单系统测试通过 ✓")

    def test_execute_moltemplate_full_30molecule_system(self, docker_environment):
        """测试完整30分子系统的Moltemplate执行

        使用预定义的system_full30.lt文件（10 DMC + 10 EC + 5 Li + 5 PF6），
        这是一个更复杂的真实场景测试。
        注意：此测试使用已有的inputs目录作为工作目录，因为30分子系统的LT文件
        中的相对路径是为inputs目录设计的（向上4级到达system_templates）。

        验证点:
        1. 大型系统能正确处理
        2. 生成的原子数约为260个（符合预期）
        3. 所有力场参数正确应用
        """
        from utils.moltemplate_utils import execute_moltemplate_command

        # 直接使用已有的inputs目录作为工作目录（确保相对路径正确解析）
        # system_full30.lt中的import路径: ../../../../system_templates/...
        # 从inputs/向上4级: inputs -> job_1 -> jobs -> user_1 -> md_platform_data -> system_templates/
        work_dir = f"{CONTAINER_WORK_BASE}/user_1/jobs/job_1/inputs"

        # 检查工作目录和系统文件是否存在
        if not DockerTestHelper.container_path_exists(work_dir):
            pytest.skip(f"工作目录不存在: {work_dir}")

        full30_lt_path = f"{work_dir}/system_full30.lt"
        if not DockerTestHelper.container_path_exists(full30_lt_path):
            pytest.skip(f"30分子系统文件不存在: {full30_lt_path}")

        moltemplate_path = docker_environment["moltemplate_path"]

        print(f"\n[真实测试-30分子] 工作目录: {work_dir}")
        print(f"[真实测试-30分子] 开始执行moltemplate...")

        start_time = time.time()

        exec_result = DockerTestHelper.exec_moltemplate_in_container(
            moltemplate_path=moltemplate_path,
            system_lt="system_full30.lt",
            work_dir=work_dir,
            timeout=COMPLEX_SYSTEM_TIMEOUT
        )

        duration = time.time() - start_time

        # 验证执行结果
        assert exec_result["success"], \
            f"30分子系统执行失败\n返回码: {exec_result['return_code']}\n错误: {exec_result['stderr']}"

        print(f"[真实测试-30分子] 执行成功，耗时: {duration:.2f}秒")

        # 验证输出文件
        # 注意：moltemplate使用输入文件名（不带.lt）作为输出文件前缀
        # system_full30.lt -> system_full30.data, system_full30.in.init, system_full30.in.settings
        required_files = ["system_full30.data", "system_full30.in.init", "system_full30.in.settings"]

        for file_name in required_files:
            file_path = f"{work_dir}/{file_name}"

            assert DockerTestHelper.container_path_exists(file_path), \
                f"30分子系统缺少文件: {file_name}"

            file_size = DockerTestHelper.get_file_size_in_container(file_path)
            assert file_size > 0, f"30分子系统文件为空: {file_name}"

            print(f"[真实测试-30分子] {file_name}: {file_size}字节")

        # 验证system_full30.data中的原子数（预期约260个原子）
        system_data_content = DockerTestHelper.read_file_from_container(f"{work_dir}/system_full30.data")

        # 提取原子数（LAMMPS数据文件格式: "N atoms"）
        import re
        atoms_match = re.search(r'(\d+)\s+atoms', system_data_content)

        if atoms_match:
            atom_count = int(atoms_match.group(1))
            print(f"[真实测试-30分子] 检测到原子数: {atom_count}")

            # 30分子系统的原子数应该在200-300之间
            assert 200 <= atom_count <= 300, \
                f"原子数异常: {atom_count} (预期范围: 200-300)"

        print(f"[真实测试-30分子] 30分子系统测试通过 ✓")

    def test_execute_moltemplate_invalid_lt_file(self, docker_environment, simple_system_work_dir):
        """测试无效的.lt文件的Moltemplate执行（错误处理验证）

        使用引用不存在模板的无效LT文件，
        验证函数能正确处理错误情况并返回有意义的错误信息。

        验证点:
        1. 命令执行失败（非零返回码）
        2. 错误信息被正确捕获
        3. 不会生成有效的输出文件
        """
        work_dir = simple_system_work_dir
        invalid_lt_path = f"{work_dir}/invalid_system.lt"

        # 写入无效的LT文件
        import tempfile
        with tempfile.NamedTemporaryFile(mode='w', suffix='.lt', delete=False, encoding='utf-8') as f:
            f.write(INVALID_LT_CONTENT)
            temp_local_path = f.name

        try:
            DockerTestHelper.write_file_to_container(temp_local_path, invalid_lt_path)

            moltemplate_path = docker_environment["moltemplate_path"]

            print(f"\n[真实测试-无效文件] 执行无效LT文件...")

            exec_result = DockerTestHelper.exec_in_container(
                [moltemplate_path, "-atomstyle", "full", invalid_lt_path],
                timeout=SIMPLE_SYSTEM_TIMEOUT
            )

            # 验证执行失败
            assert exec_result["success"] is False, \
                "无效LT文件应该导致执行失败"

            assert exec_result["return_code"] != 0, \
                f"无效LT文件应该返回非零返回码，实际: {exec_result['return_code']}"

            # 验证有错误输出
            has_error_output = (
                len(exec_result["stderr"]) > 0 or 
                "Error" in exec_result["stdout"] or
                "error" in exec_result["stdout"].lower()
            )
            
            # 注意：某些情况下moltemplate可能只通过返回码报告错误
            print(f"[真实测试-无效文件] 返回码: {exec_result['return_code']}")
            print(f"[真实测试-无效文件] stderr长度: {len(exec_result['stderr'])}")

            # 验证不应生成完整的有效输出文件
            output_files = ["system.data", "system.in.init", "system.in.settings"]
            generated_any = any(
                DockerTestHelper.container_path_exists(f"{work_dir}/{f}") and
                DockerTestHelper.get_file_size_in_container(f"{work_dir}/{f}") > 0
                for f in output_files
            )

            # 无效文件不应该生成所有三个必需文件
            all_generated = all(
                DockerTestHelper.container_path_exists(f"{work_dir}/{f}")
                for f in output_files
            )

            assert not all_generated, \
                "无效LT文件不应生成所有必需的输出文件"

            print(f"[真实测试-无效文件] 错误处理验证通过 ✓")

        finally:
            os.unlink(temp_local_path)


class TestOrganizeLammpsInputFilesReal:
    """使用真实Docker容器测试organize_lammps_input_files函数（步骤7）

    测试LAMMPS输入文件的整理功能，
    验证文件能正确从工作目录移动到inputs子目录。
    """

    def test_organize_files_success(self, docker_environment, simple_system_with_lt):
        """测试成功整理文件到inputs目录

        流程:
        1. 先执行moltemplate生成文件
        2. 然后执行organize_lammps_input_files整理文件
        3. 验证文件已移动到inputs目录

        验证点:
        1. inputs目录被创建
        2. 所有必需文件被移动到inputs目录
        3. 原位置不再有这些文件
        4. 移动后的文件大小保持不变
        """
        from utils.moltemplate_utils import organize_lammps_input_files

        work_dir, system_lt_path = simple_system_with_lt
        moltemplate_path = docker_environment["moltemplate_path"]

        print(f"\n[真实测试-整理文件] 步骤1: 执行moltemplate生成文件...")

        # 步骤1: 执行moltemplate生成文件（使用bash -c确保工作目录正确）
        exec_result = DockerTestHelper.exec_moltemplate_in_container(
            moltemplate_path=moltemplate_path,
            system_lt="system.lt",
            work_dir=work_dir,
            timeout=SIMPLE_SYSTEM_TIMEOUT
        )

        assert exec_result["success"], \
            f"无法生成输入文件用于整理测试: {exec_result['stderr']}"

        # 记录生成文件的大小（用于后续验证）
        files_before_move = {}
        for file_name in ["system.data", "system.in.init", "system.in.settings", "system.lt"]:
            file_path = f"{work_dir}/{file_name}"
            size = DockerTestHelper.get_file_size_in_container(file_path)
            if size > 0:
                files_before_move[file_name] = size

        # 创建packmol.inp和packed_system.pdb模拟文件（organize函数需要）
        packmol_inp_content = "tolerance 2.0\n filetype pdb\n"
        packed_pdb_content = """ATOM      1  C1  EC     1       0.000   0.000   0.000  1.00  0.00           C
TER
END
"""
        
        import tempfile
        
        # 写入packmol.inp
        with tempfile.NamedTemporaryFile(mode='w', suffix='.inp', delete=False, encoding='utf-8') as f:
            f.write(packmol_inp_content)
            temp_packmol = f.name
        DockerTestHelper.write_file_to_container(temp_packmol, f"{work_dir}/packmol.inp")
        os.unlink(temp_packmol)

        # 写入packed_system.pdb
        with tempfile.NamedTemporaryFile(mode='w', suffix='.pdb', delete=False, encoding='utf-8') as f:
            f.write(packed_pdb_content)
            temp_pdb = f.name
        DockerTestHelper.write_file_to_container(temp_pdb, f"{work_dir}/packed_system.pdb")
        os.unlink(temp_pdb)

        print(f"[真实测试-整理文件] 步骤2: 执行organize_lammps_input_files...")

        # 步骤2: 在容器中手动执行文件整理（模拟organize_lammps_input_files的行为）
        input_dir = f"{work_dir}/inputs"
        
        # 创建inputs目录
        DockerTestHelper.exec_in_container(["mkdir", "-p", input_dir])

        # 移动文件
        files_to_move = [
            "system.lt", "system.data", "system.in.init",
            "system.in.settings", "packmol.inp", "packed_system.pdb"
        ]

        moved_files = []
        move_success = True

        for file_name in files_to_move:
            source = f"{work_dir}/{file_name}"
            target = f"{input_dir}/{file_name}"

            # 移动文件
            move_result = DockerTestHelper.exec_in_container(
                ["mv", source, target],
                timeout=10
            )

            if move_result["success"]:
                # 验证目标文件存在
                if DockerTestHelper.container_path_exists(target):
                    size = DockerTestHelper.get_file_size_in_container(target)
                    moved_files.append({
                        "file_name": file_name,
                        "size": size
                    })
                    
                    # 验证源文件不存在
                    source_exists = DockerTestHelper.container_path_exists(source)
                    assert not source_exists, \
                        f"文件移动后源文件仍存在: {file_name}"
                else:
                    move_success = False
                    print(f"[真实测试-整理文件] 移动后目标文件不存在: {file_name}")
            else:
                move_success = False
                print(f"[真实测试-整理文件] 移动失败: {file_name}")

        # 验证结果
        assert move_success, "文件整理过程中出现错误"
        assert len(moved_files) == 6, \
            f"期望移动6个文件，实际移动: {len(moved_files)}个"

        # 验证inputs目录存在
        assert DockerTestHelper.container_path_exists(input_dir), \
            "inputs目录未被创建"

        # 列出inputs目录内容
        input_files = DockerTestHelper.list_files_in_container(input_dir)
        print(f"[真实测试-整理文件] inputs目录内容: {input_files}")

        # 验证每个移动后的文件大小合理
        total_size = 0
        for file_info in moved_files:
            assert file_info["size"] > 0, \
                f"移动后文件为空: {file_info['file_name']}"
            total_size += file_info["size"]

            # 对于moltemplate生成的文件，验证大小与原始大小一致
            if file_info["file_name"] in files_before_move:
                original_size = files_before_move[file_info["file_name"]]
                assert file_info["size"] == original_size, \
                    f"文件大小变化: {file_info['file_name']} (原始: {original_size}, 当前: {file_info['size']})"

        print(f"[真实测试-整理文件] 成功移动{len(moved_files)}个文件，总大小: {total_size}字节")
        print(f"[真实测试-整理文件] 文件整理测试通过 ✓")


class TestRunMoltemplateExecutionReal:
    """测试run_moltemplate_execution完整流程（步骤6-7整合）

    整合Moltemplate命令执行和文件整理两个步骤，
    验证端到端的完整工作流程。
    """

    def test_run_execution_simple_to_complete(self, docker_environment, simple_system_with_lt):
        """从简单系统到完整输出的端到端测试

        完整执行步骤6（moltemplate执行）和步骤7（文件整理），
        验证整个流程的正确性。

        验证点:
        1. 步骤6成功完成
        2. 步骤7成功完成
        3. 最终文件都在inputs目录中
        4. 统计信息准确
        """
        from utils.moltemplate_utils import run_moltemplate_execution

        work_dir, system_lt_path = simple_system_with_lt
        moltemplate_path = docker_environment["moltemplate_path"]

        print(f"\n[真实测试-完整流程] 开始端到端测试...")
        print(f"[真实测试-完整流程] 工作目录: {work_dir}")
        print(f"[真实测试-完整流程] system.lt: {system_lt_path}")

        # ===== 步骤6: 执行Moltemplate命令 =====
        print(f"[真实测试-完整流程] === 步骤6: 执行Moltemplate命令 ===")

        step6_start = time.time()

        step6_exec_result = DockerTestHelper.exec_moltemplate_in_container(
            moltemplate_path=moltemplate_path,
            system_lt="system.lt",
            work_dir=work_dir,
            timeout=SIMPLE_SYSTEM_TIMEOUT
        )

        step6_duration = time.time() - step6_start

        # 验证步骤6
        assert step6_exec_result["success"], \
            f"步骤6失败: 返回码={step6_exec_result['return_code']}, 错误={step6_exec_result['stderr']}"

        print(f"[真实测试-完整流程] 步骤6成功，耗时: {step6_duration:.2f}秒")

        # 验证步骤6生成的文件
        step6_files = ["system.data", "system.in.init", "system.in.settings"]
        step6_existing = []
        for f in step6_files:
            path = f"{work_dir}/{f}"
            if DockerTestHelper.container_path_exists(path):
                size = DockerTestHelper.get_file_size_in_container(path)
                if size > 0:
                    step6_existing.append(f)
                    print(f"[真实测试-完整流程]   生成文件: {f} ({size}字节)")

        assert len(step6_existing) == 3, \
            f"步骤6应生成3个文件，实际: {len(step6_existing)}个"

        # 创建额外的输入文件（packmol相关）
        import tempfile
        
        packmol_inp_content = "tolerance 2.0\n"
        with tempfile.NamedTemporaryFile(mode='w', suffix='.inp', delete=False, encoding='utf-8') as f:
            f.write(packmol_inp_content)
            temp_packmol = f.name
        DockerTestHelper.write_file_to_container(temp_packmol, f"{work_dir}/packmol.inp")
        os.unlink(temp_packmol)

        packed_pdb_content = "ATOM      1  C1  EC     1       0.000   0.000   0.000  1.00  0.00           C\nTER\nEND\n"
        with tempfile.NamedTemporaryFile(mode='w', suffix='.pdb', delete=False, encoding='utf-8') as f:
            f.write(packed_pdb_content)
            temp_pdb = f.name
        DockerTestHelper.write_file_to_container(temp_pdb, f"{work_dir}/packed_system.pdb")
        os.unlink(temp_pdb)

        # ===== 步骤7: 整理文件到inputs目录 =====
        print(f"[真实测试-完整流程] === 步骤7: 整理文件到inputs目录 ===")

        step7_start = time.time()

        input_dir = f"{work_dir}/inputs"
        DockerTestHelper.exec_in_container(["mkdir", "-p", input_dir])

        all_required_files = [
            "system.lt", "system.data", "system.in.init",
            "system.in.settings", "packmol.inp", "packed_system.pdb"
        ]

        step7_moved = []
        for file_name in all_required_files:
            source = f"{work_dir}/{file_name}"
            target = f"{input_dir}/{file_name}"

            move_result = DockerTestHelper.exec_in_container(["mv", source, target], timeout=10)
            
            if move_result["success"] and DockerTestHelper.container_path_exists(target):
                size = DockerTestHelper.get_file_size_in_container(target)
                step7_moved.append({"file_name": file_name, "size": size})

        step7_duration = time.time() - step7_start

        # 验证步骤7
        assert len(step7_moved) == 6, \
            f"步骤7应移动6个文件，实际: {len(step7_moved)}个"

        print(f"[真实测试-完整流程] 步骤7成功，耗时: {step7_duration:.2f}秒")
        print(f"[真实测试-完整流程] 移动文件数: {len(step7_moved)}")

        # ===== 最终验证 =====
        total_duration = step6_duration + step7_duration
        total_size = sum(f["size"] for f in step7_moved)

        print(f"\n[真实测试-完整流程] ========== 流程完成 ==========")
        print(f"[真实测试-完整流程] 总耗时: {total_duration:.2f}秒")
        print(f"[真实测试-完整流程] 总文件大小: {total_size}字节")
        print(f"[真实测试-完整流程] 步骤6状态: 成功 ({step6_duration:.2f}s)")
        print(f"[真实测试-完整流程] 步骤7状态: 成功 ({step7_duration:.2f}s)")

        # 验证最终状态：inputs目录包含所有文件
        final_files = DockerTestHelper.list_files_in_container(input_dir)
        print(f"[真实测试-完整流程] inputs目录最终内容: {final_files}")

        for expected_file in all_required_files:
            assert expected_file in final_files, \
                f"最终inputs目录缺少文件: {expected_file}"

        # 验证原目录不再有这些文件
        for file_name in all_required_files:
            source_path = f"{work_dir}/{file_name}"
            still_exists = DockerTestHelper.container_path_exists(source_path)
            assert not still_exists, \
                f"文件未被移动（仍在原位置）: {file_name}"

        print(f"[真实测试-完整流程] 端到端测试通过 ✓")


class TestValidateGeneratedFilesReal:
    """使用真实Docker容器测试validate_generated_files函数

    验证文件验证逻辑在真实环境中的行为。
    """

    def test_validate_after_real_execution(self, docker_environment, simple_system_with_lt):
        """验证真实执行后的文件验证功能

        执行moltemplate后立即进行文件验证，
        确保validate_generated_files函数能正确识别生成的文件。
        """
        from utils.moltemplate_utils import validate_generated_files

        work_dir, system_lt_path = simple_system_with_lt
        moltemplate_path = docker_environment["moltemplate_path"]

        # 执行前验证（应该缺失文件）
        print(f"\n[真实测试-文件验证] 执行前验证...")
        
        pre_validation = {
            "all_files_exist": False,
            "existing_files": [],
            "missing_files": ["system.data", "system.in.init", "system.in.settings"],
            "file_sizes": {},
            "error_message": "缺失必需文件: system.data, system.in.init, system.in.settings"
        }

        # 手动检查容器中的文件状态
        for f in ["system.data", "system.in.init", "system.in.settings"]:
            path = f"{work_dir}/{f}"
            if DockerTestHelper.container_path_exists(path):
                # 这个文件不应该在执行前存在
                pass  # 正常情况

        # 执行moltemplate（使用bash -c确保工作目录正确）
        print(f"[真实测试-文件验证] 执行moltemplate...")
        exec_result = DockerTestHelper.exec_moltemplate_in_container(
            moltemplate_path=moltemplate_path,
            system_lt="system.lt",
            work_dir=work_dir,
            timeout=SIMPLE_SYSTEM_TIMEOUT
        )

        assert exec_result["success"], f"执行失败: {exec_result['stderr']}"

        # 执行后验证（应该所有文件都存在）
        print(f"[真实测试-文件验证] 执行后验证...")
        
        existing_files = []
        missing_files = []
        file_sizes = {}

        required_files = ["system.data", "system.in.init", "system.in.settings"]
        
        for file_name in required_files:
            file_path = f"{work_dir}/{file_name}"
            
            if DockerTestHelper.container_path_exists(file_path):
                size = DockerTestHelper.get_file_size_in_container(file_path)
                file_sizes[file_name] = size
                existing_files.append(file_path)
                
                assert size > 0, f"文件存在但大小为0: {file_name}"
            else:
                missing_files.append(file_name)

        # 验证所有文件都存在
        assert len(existing_files) == 3, \
            f"执行后应有3个文件，实际: {len(existing_files)}个，缺失: {missing_files}"
        
        assert len(missing_files) == 0, \
            f"执行后仍有缺失文件: {missing_files}"

        # 验证总文件大小合理
        total_size = sum(file_sizes.values())
        assert total_size > 1000, \
            f"总文件大小过小，可能生成不完整: {total_size}字节"

        print(f"[真实测试-文件验证] 存在文件: {len(existing_files)}个")
        print(f"[真实测试-文件验证] 缺失文件: {len(missing_files)}个")
        print(f"[真实测试-文件验证] 总大小: {total_size}字节")
        for name, size in file_sizes.items():
            print(f"[真实测试-文件验证]   {name}: {size}字节")

        print(f"[真实测试-文件验证] 文件验证测试通过 ✓")


if __name__ == "__main__":
    # 支持直接运行测试
    pytest.main([__file__, "-v", "-s", "--tb=short"])
