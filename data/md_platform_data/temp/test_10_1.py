#!/usr/bin/env python3
"""
Task 10.1 验证脚本 - Jinja2模板渲染生成正确的LAMMPS脚本
"""

import sys
import importlib.util

# 直接加载lammps_template_utils模块，避免包导入问题
spec = importlib.util.spec_from_file_location(
    "lammps_template_utils",
    "/workspace/scripts/modeling/utils/lammps_template_utils.py"
)
ltu = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ltu)

import json

context = {
    'job_id': 'test_001',
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
    'target_properties': ['density', 'conductivity']
}

result = ltu.render_lammps_templates(
    template_dir='/workspace/data/system_templates/lammps_templates',
    output_dir='/workspace/data/user_1/jobs/job_1/inputs',
    context=context
)
print(json.dumps(result, ensure_ascii=False, indent=2))
