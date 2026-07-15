#!/usr/bin/env python3
"""
Task 10.3 验证脚本 - Moltemplate使用-pdb参数生成正确的坐标
检查system.data中原子坐标是否不再是全部在原点附近
"""

data_path = '/workspace/data/user_1/jobs/job_1/inputs/system.data'

with open(data_path, 'r') as f:
    content = f.read()

# 提取Atoms部分
# atom_style full格式: atom-ID molecule-ID atom-type q x y z
# 索引(0-based):        0          1           2         3 4 5 6
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
        if len(parts) >= 7:
            try:
                # atom_style full: x=parts[4], y=parts[5], z=parts[6]
                x, y, z = float(parts[4]), float(parts[5]), float(parts[6])
                coords.append((x, y, z))
            except ValueError:
                break

print(f"总原子数: {len(coords)}")

if coords:
    xs = [c[0] for c in coords]
    ys = [c[1] for c in coords]
    zs = [c[2] for c in coords]

    print(f"X坐标范围: {min(xs):.3f} ~ {max(xs):.3f} (盒子: 0 ~ 48)")
    print(f"Y坐标范围: {min(ys):.3f} ~ {max(ys):.3f} (盒子: 0 ~ 48)")
    print(f"Z坐标范围: {min(zs):.3f} ~ {max(zs):.3f} (盒子: 0 ~ 48)")

    # 检查是否所有原子都在原点附近（0.5Å以内）
    near_origin = sum(1 for c in coords if abs(c[0]) < 0.5 and abs(c[1]) < 0.5 and abs(c[2]) < 0.5)
    print(f"原点附近原子数: {near_origin}/{len(coords)}")

    # 检查坐标分布是否合理（不在原点附近）
    if near_origin == 0:
        print("PASS: 原子坐标有合理的3D分布，不再全部在原点附近")
    else:
        print(f"FAIL: 有 {near_origin} 个原子仍在原点附近")

    # 检查坐标是否在盒子范围内
    out_of_box = sum(1 for c in coords if c[0] < 0 or c[0] > 48 or c[1] < 0 or c[1] > 48 or c[2] < 0 or c[2] > 48)
    if out_of_box == 0:
        print("PASS: 所有原子坐标都在盒子范围内")
    else:
        print(f"WARN: 有 {out_of_box} 个原子坐标超出盒子范围（可能是分子跨越边界）")

    # 打印前5个原子坐标
    print("\n前5个原子坐标:")
    for i, c in enumerate(coords[:5]):
        print(f"  atom {i+1}: x={c[0]:8.3f}  y={c[1]:8.3f}  z={c[2]:8.3f}")
else:
    print("FAIL: 未找到原子坐标数据")
