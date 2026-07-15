#!/usr/bin/env python3
"""
Task 10.3 详细检查 - 分析原子坐标分布
"""

data_path = '/workspace/data/user_1/jobs/job_1/inputs/system.data'

with open(data_path, 'r') as f:
    content = f.read()

# 提取Atoms部分
atoms_section = False
coords = []
for line in content.split('\n'):
    if line.strip() == 'Atoms':
        atoms_section = True
        continue
    if atoms_section and line.strip() == '':
        if coords:
            break
        continue
    if atoms_section:
        parts = line.split()
        if len(parts) >= 6:
            try:
                atom_id = int(parts[0])
                mol_id = int(parts[1])
                x, y, z = float(parts[3]), float(parts[4]), float(parts[5])
                coords.append((atom_id, mol_id, x, y, z))
            except ValueError:
                break

print(f"总原子数: {len(coords)}")

# 打印前20个原子的坐标
print("\n前20个原子坐标:")
for c in coords[:20]:
    print(f"  atom {c[0]:3d} mol {c[1]:3d}  x={c[2]:8.3f}  y={c[3]:8.3f}  z={c[4]:8.3f}")

# 按分子分组统计
from collections import defaultdict
mol_coords = defaultdict(list)
for c in coords:
    mol_coords[c[1]].append((c[2], c[3], c[4]))

print(f"\n分子数: {len(mol_coords)}")
print("\n各分子质心坐标:")
for mol_id in sorted(mol_coords.keys())[:10]:
    mc = mol_coords[mol_id]
    cx = sum(c[0] for c in mc) / len(mc)
    cy = sum(c[1] for c in mc) / len(mc)
    cz = sum(c[2] for c in mc) / len(mc)
    print(f"  分子 {mol_id:3d}: 质心 ({cx:8.3f}, {cy:8.3f}, {cz:8.3f}) 原子数={len(mc)}")

# 检查X坐标分布
xs = [c[2] for c in coords]
ys = [c[3] for c in coords]
zs = [c[4] for c in coords]

print(f"\nX坐标分布: min={min(xs):.3f}, max={max(xs):.3f}, mean={sum(xs)/len(xs):.3f}")
print(f"Y坐标分布: min={min(ys):.3f}, max={max(ys):.3f}, mean={sum(ys)/len(ys):.3f}")
print(f"Z坐标分布: min={min(zs):.3f}, max={max(zs):.3f}, mean={sum(zs)/len(zs):.3f}")

# 检查超出盒子的原子
out_of_box = []
for c in coords:
    if c[2] < 0 or c[2] > 48 or c[3] < 0 or c[3] > 48 or c[4] < 0 or c[4] > 48:
        out_of_box.append(c)

print(f"\n超出盒子的原子数: {len(out_of_box)}")
if out_of_box:
    print("超出盒子范围的原子示例:")
    for c in out_of_box[:5]:
        print(f"  atom {c[0]:3d} mol {c[1]:3d}  x={c[2]:8.3f}  y={c[3]:8.3f}  z={c[4]:8.3f}")
