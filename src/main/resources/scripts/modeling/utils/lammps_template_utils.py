"""
LAMMPS模板渲染工具模块

功能：
    1. 使用Jinja2模板引擎渲染LAMMPS输入脚本
    2. 根据target_properties动态添加计算命令
    3. 支持include子模板（compute_*.j2）

使用方法：
    python lammps_template_utils.py --template-dir /path/to/templates --output-dir /path/to/output --context-json '{"temp": 300, "target_properties": ["density"]}'

作者: 电解液MD平台
版本: 1.0.0
"""

import os
import sys
import json
import re
import argparse
import logging
from pathlib import Path
from typing import Dict, List, Any, Optional

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger('lammps_template_utils')

# 尝试导入Jinja2，如果不可用则使用简单字符串替换降级方案
try:
    from jinja2 import Environment, FileSystemLoader, StrictUndefined
    JINJA2_AVAILABLE = True
    logger.info("Jinja2库已加载，将使用Jinja2模板引擎渲染")
except ImportError:
    JINJA2_AVAILABLE = False
    Environment = None
    FileSystemLoader = None
    logger.warning("Jinja2库未安装，将使用简单字符串替换降级方案")


def render_lammps_templates(
    template_dir: str,
    output_dir: str,
    context: dict
) -> dict:
    """
    渲染LAMMPS输入脚本模板

    根据模板目录中的Jinja2模板文件和上下文参数，生成LAMMPS输入脚本。
    优先使用Jinja2模板引擎渲染，如果Jinja2不可用则降级为简单字符串替换。

    参数:
        template_dir: 模板文件目录路径（包含in.minimization.j2等模板文件）
        output_dir: 输出文件目录路径（渲染后的脚本文件存放位置）
        context: 模板上下文参数字典（包含温度、压力、步数等参数）

    返回:
        渲染结果字典，包含以下字段：
        - success: bool - 是否全部渲染成功
        - rendered_files: List[str] - 成功渲染的文件名列表
        - errors: List[str] - 渲染过程中的错误信息列表
    """
    logger.info("开始渲染LAMMPS模板")
    logger.info("模板目录: %s", template_dir)
    logger.info("输出目录: %s", output_dir)
    logger.info("上下文参数键: %s", list(context.keys()))

    # 确保输出目录存在
    os.makedirs(output_dir, exist_ok=True)

    # 渲染的模板文件列表（按LAMMPS模拟流程顺序排列）
    templates = [
        "in.minimization.j2",
        "in.equilibrium.j2",
        "in.production.j2"
    ]

    # 检查模板目录是否存在
    if not os.path.isdir(template_dir):
        error_msg = "模板目录不存在: {}".format(template_dir)
        logger.error(error_msg)
        return {
            "success": False,
            "rendered_files": [],
            "errors": [error_msg]
        }

    results = {
        "success": True,
        "rendered_files": [],
        "errors": []
    }

    if JINJA2_AVAILABLE:
        # 使用Jinja2模板引擎渲染
        results = _render_with_jinja2(template_dir, output_dir, context, templates)
    else:
        # 使用简单字符串替换降级方案
        results = _render_with_simple_replace(template_dir, output_dir, context, templates)

    # 输出渲染结果摘要
    if results["success"]:
        logger.info("LAMMPS模板渲染完成，成功生成 %d 个文件: %s",
                     len(results["rendered_files"]), results["rendered_files"])
    else:
        logger.error("LAMMPS模板渲染失败，错误: %s", results["errors"])

    return results


def _render_with_jinja2(
    template_dir: str,
    output_dir: str,
    context: dict,
    templates: List[str]
) -> dict:
    """
    使用Jinja2模板引擎渲染LAMMPS模板

    Jinja2提供了完整的模板语法支持，包括：
    - 变量替换：{{ variable }}
    - 条件判断：{% if condition %} ... {% endif %}
    - 循环：{% for item in list %} ... {% endfor %}
    - 模板继承和包含：{% include "sub_template.j2" %}
    - 过滤器：{{ value | default("fallback") }}

    参数:
        template_dir: 模板文件目录路径
        output_dir: 输出文件目录路径
        context: 模板上下文参数字典
        templates: 需要渲染的模板文件名列表

    返回:
        渲染结果字典
    """
    results = {
        "success": True,
        "rendered_files": [],
        "errors": []
    }

    # 创建Jinja2环境，配置模板加载器
    # keep_trailing_newline=True 保留模板末尾的换行符（LAMMPS脚本需要）
    env = Environment(
        loader=FileSystemLoader(template_dir),
        keep_trailing_newline=True,
        undefined=StrictUndefined  # 严格模式，未定义变量会抛出异常
    )

    for template_name in templates:
        try:
            # 检查模板文件是否存在
            template_path = os.path.join(template_dir, template_name)
            if not os.path.isfile(template_path):
                logger.warning("模板文件不存在，跳过: %s", template_name)
                continue

            logger.info("正在渲染模板: %s", template_name)

            # 加载并渲染模板
            template = env.get_template(template_name)
            rendered = template.render(**context)

            # 输出文件名（去掉.j2后缀）
            output_name = template_name.replace('.j2', '')
            output_path = os.path.join(output_dir, output_name)

            # 写入渲染结果
            with open(output_path, 'w', encoding='utf-8') as f:
                f.write(rendered)

            results["rendered_files"].append(output_name)
            logger.info("模板渲染成功: %s -> %s", template_name, output_name)

        except Exception as e:
            error_msg = "渲染模板 {} 失败: {}".format(template_name, str(e))
            logger.error(error_msg)
            results["errors"].append(error_msg)
            results["success"] = False

    return results


def _render_with_simple_replace(
    template_dir: str,
    output_dir: str,
    context: dict,
    templates: List[str]
) -> dict:
    """
    使用简单字符串替换渲染LAMMPS模板（Jinja2不可用时的降级方案）

    降级方案支持：
    - 简单变量替换：{{ variable }}
    - 带默认值的变量替换：{{ variable | default(value) }}
    - 条件判断块移除：{% if ... %} ... {% endif %}（保留所有内容）
    - 列表join过滤器移除：{{ list | join(', ') }}

    注意：降级方案不支持复杂的Jinja2语法，仅作为应急使用。

    参数:
        template_dir: 模板文件目录路径
        output_dir: 输出文件目录路径
        context: 模板上下文参数字典
        templates: 需要渲染的模板文件名列表

    返回:
        渲染结果字典
    """
    results = {
        "success": True,
        "rendered_files": [],
        "errors": []
    }

    for template_name in templates:
        try:
            template_path = os.path.join(template_dir, template_name)

            # 检查模板文件是否存在
            if not os.path.isfile(template_path):
                logger.warning("模板文件不存在，跳过: %s", template_name)
                continue

            logger.info("正在使用简单替换渲染模板: %s", template_name)

            # 读取模板内容
            with open(template_path, 'r', encoding='utf-8') as f:
                content = f.read()

            # 替换变量
            for key, value in context.items():
                # 替换 {{ key | default(value) }} 格式
                pattern = r'\{\{\s*' + re.escape(str(key)) + r'\s*\|\s*default\([^)]*\)\s*\}\}'
                content = re.sub(pattern, str(value), content)

                # 替换 {{ key }} 格式
                pattern = r'\{\{\s*' + re.escape(str(key)) + r'\s*\}\}'
                content = re.sub(pattern, str(value), content)

            # 处理条件逻辑（简单方式：保留所有内容）
            # 移除 {% if ... %}、{% endif %}、{% else %} 标记
            content = re.sub(r'\{%\s*if\s+.*?\s*%\}', '', content)
            content = re.sub(r'\{%\s*endif\s*%\}', '', content)
            content = re.sub(r'\{%\s*else\s*%\}', '', content)

            # 处理 {% for ... %} 循环（简单方式：保留循环体内容）
            content = re.sub(r'\{%\s*for\s+.*?\s*in\s+.*?\s*%\}', '', content)
            content = re.sub(r'\{%\s*endfor\s*%\}', '', content)

            # 处理 {{ list | join(', ') }} 格式
            # 如果context中有对应的列表，则用join结果替换
            for key, value in context.items():
                if isinstance(value, list):
                    join_pattern = r'\{\{\s*' + re.escape(str(key)) + r'\s*\|\s*join\([^)]*\)\s*\}\}'
                    content = re.sub(join_pattern, ', '.join(str(v) for v in value), content)

            # 处理未替换的变量（设为空字符串）
            content = re.sub(r'\{\{.*?\}\}', '', content)

            # 输出文件名（去掉.j2后缀）
            output_name = template_name.replace('.j2', '')
            output_path = os.path.join(output_dir, output_name)

            # 写入渲染结果
            with open(output_path, 'w', encoding='utf-8') as f:
                f.write(content)

            results["rendered_files"].append(output_name)
            logger.info("简单替换渲染成功: %s -> %s", template_name, output_name)

        except Exception as e:
            error_msg = "渲染模板 {} 失败: {}".format(template_name, str(e))
            logger.error(error_msg)
            results["errors"].append(error_msg)
            results["success"] = False

    return results


def validate_template_context(context: dict) -> dict:
    """
    验证模板上下文参数的完整性

    检查必要的模板参数是否存在，并为缺失的参数设置默认值。

    参数:
        context: 模板上下文参数字典

    返回:
        验证结果字典，包含以下字段：
        - valid: bool - 是否验证通过
        - missing_keys: List[str] - 缺失的必要参数列表
        - context: dict - 补充默认值后的上下文参数
    """
    # 必要参数及其默认值
    required_keys_with_defaults = {
        "temp": 300.0,
        "press": 1.0,
        "timestep": 1.0,
        "cutoff": 12.0,
        "tau_t": 100.0,
        "tau_p": 1000.0,
        "nsteps_nvt": 25000,
        "nsteps_npt": 25000,
        "nsteps": 100000,
        "thermo_freq": 100,
        "dump_freq": 1000,
        "ave_freq": 100,
        "min_style": "cg",
        "etol": 1.0e-4,
        "ftol": 1.0e-4,
        "maxiter": 1000,
        "maxeval": 10000,
        "seed": 12345,
    }

    missing_keys = []
    for key, default_value in required_keys_with_defaults.items():
        if key not in context:
            missing_keys.append(key)
            context[key] = default_value
            logger.warning("模板上下文缺少参数 '%s'，使用默认值: %s", key, default_value)

    # target_properties是特殊参数，必须为列表
    if "target_properties" not in context:
        context["target_properties"] = ["density"]
        logger.warning("模板上下文缺少参数 'target_properties'，使用默认值: ['density']")
    elif not isinstance(context["target_properties"], list):
        context["target_properties"] = [context["target_properties"]]

    return {
        "valid": len(missing_keys) == 0,
        "missing_keys": missing_keys,
        "context": context
    }


def list_available_templates(template_dir: str) -> List[str]:
    """
    列出模板目录中所有可用的Jinja2模板文件

    参数:
        template_dir: 模板文件目录路径

    返回:
        可用模板文件名列表
    """
    templates = []
    if not os.path.isdir(template_dir):
        logger.warning("模板目录不存在: %s", template_dir)
        return templates

    for filename in sorted(os.listdir(template_dir)):
        if filename.endswith('.j2'):
            templates.append(filename)

    logger.info("找到 %d 个模板文件: %s", len(templates), templates)
    return templates


if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="LAMMPS模板渲染工具 - 使用Jinja2模板引擎生成LAMMPS输入脚本"
    )
    parser.add_argument(
        "--template-dir",
        required=True,
        help="模板文件目录路径（包含in.minimization.j2等模板文件）"
    )
    parser.add_argument(
        "--output-dir",
        required=True,
        help="输出文件目录路径（渲染后的脚本文件存放位置）"
    )
    parser.add_argument(
        "--context-json",
        required=True,
        help="模板上下文参数（JSON格式字符串）"
    )
    parser.add_argument(
        "--validate-only",
        action="store_true",
        default=False,
        help="仅验证上下文参数，不执行渲染"
    )

    args = parser.parse_args()

    # 解析上下文JSON
    try:
        context = json.loads(args.context_json)
        logger.info("成功解析上下文JSON，参数数量: %d", len(context))
    except json.JSONDecodeError as e:
        logger.error("上下文JSON解析失败: %s", str(e))
        result = {
            "success": False,
            "rendered_files": [],
            "errors": ["上下文JSON解析失败: {}".format(str(e))]
        }
        print("===JSON_RESULT===")
        print(json.dumps(result, ensure_ascii=False, indent=2))
        print("===END_JSON===")
        sys.exit(1)

    # 验证上下文参数
    validation = validate_template_context(context)
    context = validation["context"]

    if validation["missing_keys"]:
        logger.warning("上下文参数验证发现缺失参数: %s", validation["missing_keys"])

    # 仅验证模式
    if args.validate_only:
        print(json.dumps({
            "validation": validation,
            "available_templates": list_available_templates(args.template_dir)
        }, ensure_ascii=False, indent=2))
        sys.exit(0)

    # 执行模板渲染
    result = render_lammps_templates(args.template_dir, args.output_dir, context)

    # 输出JSON结果（最后一行，供Java端解析）
    print(json.dumps(result, ensure_ascii=False, indent=2))

    # 如果渲染失败，退出码为1
    if not result["success"]:
        sys.exit(1)
