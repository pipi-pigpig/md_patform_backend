#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Docker环境诊断脚本

功能：
    1. 检查Docker daemon连接状态
    2. 检查md-engine容器运行状态
    3. 检查Packmol在容器中的安装状态
    4. 输出诊断报告和建议修复措施

使用方法：
    python check_docker_env.py

作者: MD Platform Team
创建日期: 2024
"""

import subprocess
import sys
import platform
from typing import Tuple, Optional


class Colors:
    """终端颜色常量定义"""
    RED = '\033[91m'
    GREEN = '\033[92m'
    YELLOW = '\033[93m'
    BLUE = '\033[94m'
    MAGENTA = '\033[95m'
    CYAN = '\033[96m'
    WHITE = '\033[97m'
    RESET = '\033[0m'
    BOLD = '\033[1m'


def print_header(title: str) -> None:
    """
    打印带格式的标题头
    
    参数:
        title: 标题文本
    """
    print(f"\n{Colors.BOLD}{Colors.CYAN}{'=' * 60}{Colors.RESET}")
    print(f"{Colors.BOLD}{Colors.WHITE}  {title}{Colors.RESET}")
    print(f"{Colors.BOLD}{Colors.CYAN}{'=' * 60}{Colors.RESET}\n")


def print_status(status: str, message: str, details: Optional[str] = None) -> None:
    """
    打印状态信息，使用颜色区分不同状态
    
    参数:
        status: 状态类型 ('success', 'error', 'warning', 'info')
        message: 主要消息内容
        details: 可选的详细信息
    """
    status_config = {
        'success': (f"{Colors.GREEN}✓ 成功{Colors.RESET}", Colors.GREEN),
        'error': (f"{Colors.RED}✗ 失败{Colors.RESET}", Colors.RED),
        'warning': (f"{Colors.YELLOW}⚠ 警告{Colors.RESET}", Colors.YELLOW),
        'info': (f"{Colors.BLUE}ℹ 信息{Colors.RESET}", Colors.BLUE),
    }
    
    status_text, color = status_config.get(status, ('', Colors.WHITE))
    print(f"  {status_text}  {message}")
    if details:
        print(f"          {Colors.WHITE}详细信息: {details}{Colors.RESET}")


def run_command(cmd: list, timeout: int = 30) -> Tuple[int, str, str]:
    """
    执行系统命令并返回结果
    
    参数:
        cmd: 命令及参数列表
        timeout: 超时时间（秒）
    
    返回:
        元组 (返回码, 标准输出, 标准错误)
    """
    try:
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=timeout,
            encoding='utf-8',
            errors='replace'
        )
        return result.returncode, result.stdout, result.stderr
    except subprocess.TimeoutExpired:
        return -1, '', '命令执行超时'
    except FileNotFoundError:
        return -2, '', '命令未找到，请确认Docker已安装'
    except Exception as e:
        return -3, '', str(e)


def check_docker_daemon() -> bool:
    """
    检查Docker daemon连接状态
    
    通过执行 'docker info' 命令来验证Docker服务是否正常运行
    
    返回:
        bool: Docker daemon是否正常运行
    """
    print_header("1. 检查Docker Daemon连接状态")
    
    returncode, stdout, stderr = run_command(['docker', 'info'])
    
    if returncode == 0:
        print_status('success', 'Docker Daemon运行正常')
        
        if 'Server Version' in stdout:
            lines = stdout.split('\n')
            for line in lines:
                if 'Server Version' in line:
                    print(f"          {Colors.WHITE}{line.strip()}{Colors.RESET}")
                    break
        
        if 'Operating System' in stdout:
            lines = stdout.split('\n')
            for line in lines:
                if 'Operating System' in line:
                    print(f"          {Colors.WHITE}{line.strip()}{Colors.RESET}")
                    break
        
        return True
    
    elif returncode == -2:
        print_status('error', 'Docker命令未找到')
        print_suggestion([
            "请确认Docker已正确安装",
            "Windows: 从 https://www.docker.com/products/docker-desktop 下载安装",
            "Linux: 执行 'curl -fsSL https://get.docker.com | sh'",
            "安装后请重启终端或重新加载环境变量"
        ])
        return False
    
    elif returncode == -1:
        print_status('error', 'Docker命令执行超时')
        print_suggestion([
            "Docker可能正在启动中，请稍后重试",
            "检查Docker Desktop是否卡住，尝试重启"
        ])
        return False
    
    else:
        print_status('error', 'Docker Daemon连接失败', stderr.strip() if stderr else '未知错误')
        print_suggestion([
            "确认Docker Desktop是否正在运行（Windows/Mac）",
            "确认当前用户是否有Docker访问权限",
            "Windows: 确保Docker Desktop已启动并显示绿色状态",
            "Linux: 执行 'sudo systemctl start docker' 启动服务",
            "Linux: 执行 'sudo usermod -aG docker $USER' 添加用户到docker组",
            "尝试重启Docker服务或Docker Desktop"
        ])
        return False


def check_md_engine_container() -> Tuple[bool, Optional[str]]:
    """
    检查md-engine容器运行状态
    
    通过 'docker ps' 命令过滤名称为md-engine或md_engine的容器
    支持两种命名方式：md-engine（连字符）和md_engine（下划线）
    
    返回:
        Tuple[bool, Optional[str]]: (容器是否运行, 容器ID或None)
    """
    print_header("2. 检查md-engine容器运行状态")
    
    # 尝试两种命名方式：md-engine（连字符）和md_engine（下划线）
    container_names_to_try = ['md_engine', 'md-engine']
    
    for container_name in container_names_to_try:
        returncode, stdout, stderr = run_command(
            ['docker', 'ps', '--filter', f'name={container_name}', '--format', '{{.ID}}\t{{.Names}}\t{{.Status}}\t{{.Image}}']
        )
        
        if returncode != 0:
            continue
        
        lines = [line.strip() for line in stdout.strip().split('\n') if line.strip()]
        
        if lines:
            print_status('success', f'找到 {len(lines)} 个运行中的md容器（匹配名称: {container_name}）')
            
            for line in lines:
                parts = line.split('\t')
                if len(parts) >= 4:
                    container_id = parts[0][:12]
                    name = parts[1]
                    status = parts[2]
                    image = parts[3]
                    print(f"          {Colors.WHITE}容器ID: {container_id}{Colors.RESET}")
                    print(f"          {Colors.WHITE}名称: {name}{Colors.RESET}")
                    print(f"          {Colors.WHITE}状态: {status}{Colors.RESET}")
                    print(f"          {Colors.WHITE}镜像: {image}{Colors.RESET}")
                    print()
            
            container_id = lines[0].split('\t')[0]
            return True, container_id
    
    print_status('warning', '未找到运行中的md-engine/md_engine容器')
    
    # 检查所有容器（包括停止的）
    for container_name in container_names_to_try:
        returncode, stdout, stderr = run_command(
            ['docker', 'ps', '-a', '--filter', f'name={container_name}', '--format', '{{.ID}}\t{{.Names}}\t{{.Status}}']
        )
        
        if returncode == 0 and stdout.strip():
            lines = [line.strip() for line in stdout.strip().split('\n') if line.strip()]
            if lines:
                parts = lines[0].split('\t')
                if len(parts) >= 3:
                    status = parts[2]
                    print_status('info', f'容器存在但未运行，当前状态: {status}')
                    
                    if 'Exited' in status:
                        print_suggestion([
                            "容器已停止，尝试启动容器:",
                            f"  docker start {container_name}",
                            "或者重新创建容器"
                        ])
                    elif 'Created' in status:
                        print_suggestion([
                            "容器已创建但未启动，执行:",
                            f"  docker start {container_name}"
                        ])
                return False, None
    
    if returncode == 0 and stdout.strip():
        lines = [line.strip() for line in stdout.strip().split('\n') if line.strip()]
        if lines:
            parts = lines[0].split('\t')
            if len(parts) >= 3:
                status = parts[2]
                print_status('info', f'容器存在但未运行，当前状态: {status}')
                
                if 'Exited' in status:
                    print_suggestion([
                        "容器已停止，尝试启动容器:",
                        "  docker start md-engine",
                        "或者重新创建容器"
                    ])
                elif 'Created' in status:
                    print_suggestion([
                        "容器已创建但未启动，执行:",
                        "  docker start md-engine"
                    ])
    else:
        print_status('info', 'md-engine容器不存在')
        print_suggestion([
            "请先构建并运行md-engine容器:",
            "  1. 确保Dockerfile存在于项目目录中",
            "  2. 构建镜像: docker build -t md-engine .",
            "  3. 运行容器: docker run -d --name md-engine md-engine",
            "  或使用docker-compose: docker-compose up -d"
        ])
    
    return False, None


def check_packmol_in_container(container_id: str) -> bool:
    """
    检查Packmol在容器中的安装状态
    
    通过 'docker exec' 在容器内执行 'which packmol' 命令
    
    参数:
        container_id: 容器ID
    
    返回:
        bool: Packmol是否已安装
    """
    print_header("3. 检查Packmol安装状态")
    
    returncode, stdout, stderr = run_command(
        ['docker', 'exec', container_id, 'which', 'packmol']
    )
    
    if returncode == 0 and stdout.strip():
        packmol_path = stdout.strip()
        print_status('success', 'Packmol已安装', f'路径: {packmol_path}')
        
        returncode, stdout, stderr = run_command(
            ['docker', 'exec', container_id, 'packmol']
        )
        
        if returncode == 0 or 'PACKMOL' in stdout or 'PACKMOL' in stderr:
            print_status('success', 'Packmol可执行')
        
        return True
    
    print_status('warning', 'Packmol未安装或不在PATH中')
    
    returncode, stdout, stderr = run_command(
        ['docker', 'exec', container_id, 'find', '/usr', '-name', 'packmol*', '-type', 'f']
    )
    
    if stdout.strip():
        print_status('info', '找到可能的Packmol文件:')
        for line in stdout.strip().split('\n')[:5]:
            if line.strip():
                print(f"          {Colors.WHITE}{line.strip()}{Colors.RESET}")
    
    print_suggestion([
        "在容器中安装Packmol:",
        "  方式1 - Ubuntu/Debian:",
        "    docker exec -it md-engine apt-get update",
        "    docker exec -it md-engine apt-get install -y packmol",
        "",
        "  方式2 - 从源码编译:",
        "    docker exec -it md-engine bash",
        "    wget https://github.com/m3g/packmol/archive/refs/heads/master.zip",
        "    unzip master.zip && cd packmol-master",
        "    ./configure && make",
        "    cp packmol /usr/local/bin/",
        "",
        "  方式3 - 修改Dockerfile添加:",
        "    RUN apt-get update && apt-get install -y packmol",
        "    然后重新构建镜像: docker build -t md-engine ."
    ])
    
    return False


def check_other_tools(container_id: str) -> None:
    """
    检查其他常用工具的安装状态
    
    参数:
        container_id: 容器ID
    """
    print_header("4. 检查其他工具安装状态")
    
    tools = [
        ('lammps', 'lmp', 'LAMMPS分子动力学模拟'),
        ('gromacs', 'gmx', 'GROMACS分子动力学模拟'),
        ('vmd', 'vmd', 'VMD分子可视化'),
        ('python3', 'python3', 'Python运行环境'),
    ]
    
    for tool_name, cmd, description in tools:
        returncode, stdout, stderr = run_command(
            ['docker', 'exec', container_id, 'which', cmd],
            timeout=10
        )
        
        if returncode == 0 and stdout.strip():
            print_status('success', f'{tool_name} ({description})', f'路径: {stdout.strip()}')
        else:
            print_status('warning', f'{tool_name} ({description})', '未安装')


def print_suggestion(suggestions: list) -> None:
    """
    打印修复建议
    
    参数:
        suggestions: 建议列表
    """
    print(f"\n  {Colors.YELLOW}修复建议:{Colors.RESET}")
    for suggestion in suggestions:
        print(f"    {Colors.WHITE}{suggestion}{Colors.RESET}")


def print_summary(results: dict) -> None:
    """
    打印诊断总结报告
    
    参数:
        results: 各项检查结果字典
    """
    print_header("诊断总结报告")
    
    total = len(results)
    passed = sum(1 for v in results.values() if v)
    
    print(f"  {Colors.BOLD}检查项统计:{Colors.RESET}")
    print(f"    总计: {total} 项")
    print(f"    {Colors.GREEN}通过: {passed} 项{Colors.RESET}")
    print(f"    {Colors.RED}失败: {total - passed} 项{Colors.RESET}")
    
    print(f"\n  {Colors.BOLD}详细结果:{Colors.RESET}")
    
    status_map = {
        'docker_daemon': ('Docker Daemon', 'docker daemon连接'),
        'md_engine_container': ('md-engine容器', 'md-engine容器运行'),
        'packmol': ('Packmol', 'Packmol安装'),
    }
    
    for key, passed in results.items():
        if key in status_map:
            name, desc = status_map[key]
            if passed:
                print(f"    {Colors.GREEN}✓{Colors.RESET} {name}: 正常")
            else:
                print(f"    {Colors.RED}✗{Colors.RESET} {name}: 异常")
    
    if passed == total:
        print(f"\n  {Colors.GREEN}{Colors.BOLD}所有检查项均已通过！Docker环境配置正确。{Colors.RESET}")
    else:
        print(f"\n  {Colors.YELLOW}{Colors.BOLD}存在未通过的检查项，请根据上述建议进行修复。{Colors.RESET}")


def main():
    """
    主函数 - 执行所有诊断检查
    """
    print(f"\n{Colors.BOLD}{Colors.MAGENTA}")
    print("╔════════════════════════════════════════════════════════════╗")
    print("║          Docker环境诊断工具 - MD Platform                 ║")
    print("║          Docker Environment Diagnostic Tool                ║")
    print("╚════════════════════════════════════════════════════════════╝")
    print(f"{Colors.RESET}")
    
    print(f"  {Colors.WHITE}系统信息:{Colors.RESET}")
    print(f"    操作系统: {platform.system()} {platform.release()}")
    print(f"    Python版本: {platform.python_version()}")
    
    results = {
        'docker_daemon': False,
        'md_engine_container': False,
        'packmol': False,
    }
    
    results['docker_daemon'] = check_docker_daemon()
    
    if not results['docker_daemon']:
        print_header("诊断终止")
        print_status('error', '由于Docker Daemon未运行，无法继续后续检查')
        print_summary(results)
        sys.exit(1)
    
    container_running, container_id = check_md_engine_container()
    results['md_engine_container'] = container_running
    
    if container_running and container_id:
        results['packmol'] = check_packmol_in_container(container_id)
        check_other_tools(container_id)
    
    print_summary(results)
    
    if all(results.values()):
        sys.exit(0)
    else:
        sys.exit(1)


if __name__ == '__main__':
    main()