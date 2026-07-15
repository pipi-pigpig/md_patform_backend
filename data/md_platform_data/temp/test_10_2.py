#!/usr/bin/env python3
"""
Task 10.2 验证脚本 - target_properties条件逻辑
测试4种场景：
1. ["density"] - 只包含density输出
2. ["conductivity", "dielectric"] - 包含charge dump和dipole计算
3. ["viscosity"] - 包含pressure输出
4. ["solvation_structure"] - 包含solvation轨迹dump
"""

import importlib.util
import json
import os

# 直接加载lammps_template_utils模块
spec = importlib.util.spec_from_file_location(
    "lammps_template_utils",
    "/workspace/scripts/modeling/utils/lammps_template_utils.py"
)
ltu = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ltu)

# 基础上下文参数
base_context = {
    'job_id': 'test_conditional',
    'generation_time': '2026-06-07',
    'temp': 300.0,
    'press': 1.0,
    'timestep': 1.0,
    'cutoff': 12.0,
    'tau_t': 100.0,
    'tau_p': 1000.0,
    'nsteps_nvt': 25000,
    'nsteps_npt': 25000,
    'nsteps': 100000,
    'thermo_freq': 100,
    'dump_freq': 1000,
    'ave_freq': 100,
    'min_style': 'cg',
    'etol': 1.0e-4,
    'ftol': 1.0e-6,
    'maxiter': 1000,
    'maxeval': 10000,
    'seed': 12345,
}

# 测试场景
test_cases = [
    {
        'name': '场景1: density only',
        'target_properties': ['density'],
        'output_dir': '/workspace/data/temp/test_density',
        'checks': {
            'should_contain': ['thermo_style    custom step temp pe ke etotal press vol density'],
            'should_not_contain': ['dump.charge', 'pressure.dat', 'dipole.dat', 'dump.solvation'],
        }
    },
    {
        'name': '场景2: conductivity + dielectric',
        'target_properties': ['conductivity', 'dielectric'],
        'output_dir': '/workspace/data/temp/test_conductivity_dielectric',
        'checks': {
            'should_contain': ['dump.charge', 'dipole.dat', 'c_dipole'],
            'should_not_contain': ['pressure.dat', 'dump.solvation'],
        }
    },
    {
        'name': '场景3: viscosity',
        'target_properties': ['viscosity'],
        'output_dir': '/workspace/data/temp/test_viscosity',
        'checks': {
            'should_contain': ['pressure.dat', 'c_pTensor'],
            'should_not_contain': ['dump.charge', 'dipole.dat', 'dump.solvation'],
        }
    },
    {
        'name': '场景4: solvation_structure',
        'target_properties': ['solvation_structure'],
        'output_dir': '/workspace/data/temp/test_solvation',
        'checks': {
            'should_contain': ['dump.solvation'],
            'should_not_contain': ['dump.charge', 'pressure.dat', 'dipole.dat'],
        }
    },
]

print("=" * 70)
print("Task 10.2: target_properties条件逻辑验证")
print("=" * 70)

all_passed = True

for tc in test_cases:
    print(f"\n--- {tc['name']} ---")
    context = {**base_context, 'target_properties': tc['target_properties']}

    result = ltu.render_lammps_templates(
        template_dir='/workspace/data/system_templates/lammps_templates',
        output_dir=tc['output_dir'],
        context=context
    )

    if not result['success']:
        print(f"  渲染失败: {result['errors']}")
        all_passed = False
        continue

    # 读取in.production内容
    production_path = os.path.join(tc['output_dir'], 'in.production')
    with open(production_path, 'r', encoding='utf-8') as f:
        content = f.read()

    # 检查should_contain
    for keyword in tc['checks']['should_contain']:
        if keyword in content:
            print(f"  PASS: 包含 '{keyword}'")
        else:
            print(f"  FAIL: 未包含 '{keyword}'")
            all_passed = False

    # 检查should_not_contain
    for keyword in tc['checks']['should_not_contain']:
        if keyword not in content:
            print(f"  PASS: 不包含 '{keyword}'")
        else:
            print(f"  FAIL: 不应包含 '{keyword}'")
            all_passed = False

print("\n" + "=" * 70)
if all_passed:
    print("10.2 验证结果: ALL PASSED")
else:
    print("10.2 验证结果: SOME FAILED")
print("=" * 70)
