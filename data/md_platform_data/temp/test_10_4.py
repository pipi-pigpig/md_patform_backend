#!/usr/bin/env python3
"""
Task 10.4 验证脚本 - system.in.init自动修复
"""

import importlib.util
import os

# 直接加载moltemplate_utils模块
spec = importlib.util.spec_from_file_location(
    "moltemplate_utils",
    "/workspace/scripts/modeling/utils/moltemplate_utils.py"
)
mtu = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mtu)

input_dir = '/workspace/data/user_1/jobs/job_1/inputs'
init_file = os.path.join(input_dir, 'system.in.init')

# 显示修复前的内容
print("=== 修复前的system.in.init ===")
with open(init_file, 'r') as f:
    before = f.read()
print(before)

# 执行修复
result = mtu.fix_system_in_init(input_dir)
print(f"\n修复结果: {result}")

# 显示修复后的内容
print("\n=== 修复后的system.in.init ===")
with open(init_file, 'r') as f:
    after = f.read()
print(after)

# 验证修复结果
print("\n=== 验证 ===")
has_atom_style = 'atom_style' in after and 'full' in after
has_kspace_style = 'kspace_style' in after and 'pppm' in after
has_duplicates = False

# 检查是否有重复定义
style_keywords = ['units', 'bond_style', 'angle_style', 'dihedral_style', 'improper_style', 'pair_style', 'special_bonds']
for keyword in style_keywords:
    count = sum(1 for line in after.split('\n') if line.strip().startswith(keyword))
    if count > 1:
        has_duplicates = True
        print(f"  WARN: {keyword} 出现了 {count} 次")

if has_atom_style:
    print("  PASS: 包含 atom_style full")
else:
    print("  FAIL: 不包含 atom_style full")

if has_kspace_style:
    print("  PASS: 包含 kspace_style pppm 1.0e-4")
else:
    print("  FAIL: 不包含 kspace_style pppm 1.0e-4")

if not has_duplicates:
    print("  PASS: 没有重复的力场样式定义")
else:
    print("  FAIL: 有重复的力场样式定义")
