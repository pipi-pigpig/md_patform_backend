"""
stage4_lammps_execution - LAMMPS模拟输出文件收集

功能：
    1. 收集LAMMPS模拟产生的输出文件（由Java端MDExecutorService通过docker-java执行LAMMPS生成）
    2. 根据target_properties收集性质专属输出文件
    3. 合并各阶段log文件
    4. 将输出文件移动到raw_outputs目录

注意：
    本模块仅负责文件收集和整理，不执行LAMMPS模拟。
    LAMMPS的实际执行由Java端MDExecutorService通过docker-java在容器中完成。

使用方法：
    python stage4_lammps_execution.py --work-dir <inputs_dir> --output-dir <raw_outputs_dir> --target-properties density,conductivity

作者: 电解液MD平台
版本: 2.0.0
"""

import argparse
import json
import logging
import shutil
import sys
from pathlib import Path

# 配置日志
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - [%(levelname)s] - %(message)s'
)
logger = logging.getLogger(__name__)


# target_properties与输出文件的映射关系
PROPERTY_OUTPUT_MAP = {
    'density': [],  # density通过thermo_style输出，在log.lammps中
    'conductivity': ['dump.charge.lammpstrj', 'msd.dat'],
    'viscosity': ['pressure.dat'],
    'dielectric': ['dipole.dat', 'total_dipole.dat'],
    'solvation_structure': ['dump.solvation.lammpstrj'],
}

# 通用输出文件（始终收集）
COMMON_OUTPUT_FILES = [
    'log.lammps',
    'dump.trajectory.lammpstrj',
    'final.data',
]

# LAMMPS三阶段名称（用于合并log文件时识别各阶段日志）
LAMMPS_STAGE_NAMES = ['minimization', 'equilibrium', 'production']


def execute_lammps_stages(work_dir: Path, output_dir: Path, target_properties: list, use_gpu: bool = False) -> dict:
    """
    收集LAMMPS模拟输出文件并整理到raw_outputs目录

    注意：本函数不执行LAMMPS模拟，仅负责收集和整理已由Java端MDExecutorService
    通过docker-java执行LAMMPS生成的输出文件。

    参数:
        work_dir: 工作目录（inputs目录，LAMMPS输出文件所在目录）
        output_dir: 输出目录（raw_outputs目录）
        target_properties: 目标性质列表
        use_gpu: 是否使用GPU加速（保留参数，实际LAMMPS执行由Java端控制）

    返回:
        文件收集结果字典，包含success、collected_files、work_dir、output_dir等字段
    """
    logger.info('[LAMMPS文件收集] 开始收集LAMMPS模拟输出文件')
    logger.info('[LAMMPS文件收集] 工作目录: %s', work_dir)
    logger.info('[LAMMPS文件收集] 输出目录: %s', output_dir)
    logger.info('[LAMMPS文件收集] 目标性质: %s', target_properties)

    # 确保输出目录存在
    output_dir.mkdir(parents=True, exist_ok=True)

    # 合并各阶段的log文件（如果存在分阶段的log文件）
    combined_log_path = output_dir / 'log.lammps'
    _merge_log_files(work_dir, combined_log_path)

    # 收集性质专属输出文件
    collected_files = collect_property_outputs(work_dir, output_dir, target_properties)

    # 检查是否至少收集到了一些关键文件
    has_log = (output_dir / 'log.lammps').exists() or (work_dir / 'log.lammps').exists()
    has_trajectory = (output_dir / 'dump.trajectory.lammpstrj').exists() or (work_dir / 'dump.trajectory.lammpstrj').exists()

    if not has_log and not has_trajectory:
        logger.warning('[LAMMPS文件收集] 未找到LAMMPS输出文件，可能LAMMPS尚未执行或输出路径不正确')
        logger.warning('[LAMMPS文件收集] 请确认Java端MDExecutorService已通过docker-java执行LAMMPS模拟')

    result = {
        'success': True,
        'collected_files': collected_files,
        'work_dir': str(work_dir),
        'output_dir': str(output_dir)
    }

    logger.info('[LAMMPS文件收集] 文件收集完成，共收集 %d 个文件', len(collected_files))
    return result


def _merge_log_files(work_dir: Path, combined_log_path: Path):
    """
    合并所有阶段的log.lammps文件到输出目录

    如果work_dir中存在分阶段的log文件（如log.minimization、
    log.equilibrium、log.production），则将它们合并为
    一个完整的log.lammps文件。如果只存在log.lammps，则直接移动。

    参数:
        work_dir: 工作目录
        combined_log_path: 合并后的log文件路径
    """
    logger.info('[LAMMPS文件收集] 检查并合并log文件到: %s', combined_log_path)

    # 检查是否存在分阶段的log文件
    stage_log_files = []
    for stage_name in LAMMPS_STAGE_NAMES:
        stage_log = work_dir / f'log.{stage_name}'
        if stage_log.exists():
            stage_log_files.append((stage_name, stage_log))

    if stage_log_files:
        # 合并分阶段的log文件
        logger.info('[LAMMPS文件收集] 发现 %d 个分阶段log文件，开始合并', len(stage_log_files))
        with open(combined_log_path, 'w', encoding='utf-8') as outfile:
            for stage_name, stage_log in stage_log_files:
                outfile.write(f'\n# ====== {stage_name} stage log ======\n')
                with open(stage_log, 'r', encoding='utf-8') as infile:
                    outfile.write(infile.read())
                # 删除临时log文件
                stage_log.unlink()
                logger.info('[LAMMPS文件收集] 已合并 %s 阶段log', stage_name)
    else:
        # 没有分阶段log文件，检查是否存在统一的log.lammps
        single_log = work_dir / 'log.lammps'
        if single_log.exists():
            # log.lammps将由collect_property_outputs中的COMMON_OUTPUT_FILES收集
            logger.info('[LAMMPS文件收集] 未发现分阶段log文件，将使用统一的log.lammps')
        else:
            logger.warning('[LAMMPS文件收集] 未找到任何LAMMPS log文件')


def collect_property_outputs(work_dir: Path, output_dir: Path, target_properties: list) -> list:
    """
    根据target_properties收集性质专属输出文件

    将LAMMPS模拟产生的输出文件从工作目录移动到raw_outputs目录。
    如果文件不存在，记录警告并继续处理其他文件。

    参数:
        work_dir: 工作目录（LAMMPS输出文件所在目录）
        output_dir: 输出目录（raw_outputs目录）
        target_properties: 目标性质列表

    返回:
        已收集的文件列表
    """
    logger.info('[LAMMPS文件收集] 开始收集性质专属输出文件')
    logger.info('[LAMMPS文件收集] 目标性质: %s', target_properties)

    collected = []

    # 收集通用输出文件
    for filename in COMMON_OUTPUT_FILES:
        src = work_dir / filename
        if src.exists():
            dst = output_dir / filename
            shutil.move(str(src), str(dst))
            collected.append(filename)
            logger.info('[LAMMPS文件收集] 收集通用文件: %s', filename)
        else:
            logger.warning('[LAMMPS文件收集] 通用文件不存在: %s', filename)

    # 收集性质专属输出文件
    for prop in target_properties:
        prop = prop.strip().lower()
        if prop in PROPERTY_OUTPUT_MAP:
            for filename in PROPERTY_OUTPUT_MAP[prop]:
                src = work_dir / filename
                if src.exists():
                    dst = output_dir / filename
                    shutil.move(str(src), str(dst))
                    collected.append(filename)
                    logger.info('[LAMMPS文件收集] 收集性质 %s 的文件: %s', prop, filename)
                else:
                    logger.warning('[LAMMPS文件收集] 性质 %s 的文件不存在: %s', prop, filename)

    logger.info('[LAMMPS文件收集] 文件收集完成，共收集 %d 个文件', len(collected))
    return collected


def main():
    """主入口函数"""
    parser = argparse.ArgumentParser(description='LAMMPS模拟输出文件收集（不执行LAMMPS，仅收集输出文件）')
    parser.add_argument('--work-dir', required=True, help='工作目录（inputs目录，LAMMPS输出文件所在目录）')
    parser.add_argument('--output-dir', required=True, help='输出目录（raw_outputs目录）')
    parser.add_argument('--target-properties', default='density', help='目标性质列表，逗号分隔')
    parser.add_argument('--use-gpu', action='store_true', default=False, help='是否使用GPU加速（保留参数，实际由Java端控制）')

    args = parser.parse_args()

    work_dir = Path(args.work_dir)
    output_dir = Path(args.output_dir)
    target_properties = [p.strip() for p in args.target_properties.split(',')]

    result = execute_lammps_stages(work_dir, output_dir, target_properties, args.use_gpu)

    # 输出JSON结果
    print(json.dumps(result, ensure_ascii=False, indent=2))

    sys.exit(0 if result['success'] else 1)


if __name__ == '__main__':
    main()
