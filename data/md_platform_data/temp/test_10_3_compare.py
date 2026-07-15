#!/usr/bin/env python3
"""
检查PDB文件中原子坐标与system.data中坐标的对应关系
"""

# 读取PDB文件中的原子坐标
pdb_path = '/workspace/data/user_1/jobs/job_1/inputs/packed_system.pdb'
pdb_coords = []
with open(pdb_path, 'r') as f:
    for line in f:
        if line.startswith('ATOM') or line.startswith('HETATM'):
            x = float(line[30:38].strip())
            y = float(line[38:46].strip())
            z = float(line[46:54].strip())
            pdb_coords.append((x, y, z))

print(f"PDB文件原子数: {len(pdb_coords)}")
if pdb_coords:
    xs = [c[0] for c in pdb_coords]
    ys = [c[1] for c in pdb_coords]
    zs = [c[2] for c in pdb_coords]
    print(f"PDB X坐标范围: {min(xs):.3f} ~ {max(xs):.3f}")
    print(f"PDB Y坐标范围: {min(ys):.3f} ~ {max(ys):.3f}")
    print(f"PDB Z坐标范围: {min(zs):.3f} ~ {max(zs):.3f}")
    print(f"\nPDB前10个原子坐标:")
    for i, c in enumerate(pdb_coords[:10]):
        print(f"  atom {i+1}: x={c[0]:8.3f}  y={c[1]:8.3f}  z={c[2]:8.3f}")

# 读取system.data中的原子坐标
data_path = '/workspace/data/user_1/jobs/job_1/inputs/system.data'
data_coords = []
atoms_section = False
for line in open(data_path, 'r'):
    if line.strip() == 'Atoms':
        atoms_section = True
        continue
    if atoms_section and line.strip() == '':
        if data_coords:
            break
        continue
    if atoms_section:
        parts = line.split()
        if len(parts) >= 6:
            try:
                x, y, z = float(parts[3]), float(parts[4]), float(parts[5])
                data_coords.append((x, y, z))
            except ValueError:
                break

print(f"\nsystem.data原子数: {len(data_coords)}")
if data_coords:
    xs = [c[0] for c in data_coords]
    ys = [c[1] for c in data_coords]
    zs = [c[2] for c in data_coords]
    print(f"DATA X坐标范围: {min(xs):.3f} ~ {max(xs):.3f}")
    print(f"DATA Y坐标范围: {min(ys):.3f} ~ {max(ys):.3f}")
    print(f"DATA Z坐标范围: {min(zs):.3f} ~ {max(zs):.3f}")
    print(f"\nDATA前10个原子坐标:")
    for i, c in enumerate(data_coords[:10]):
        print(f"  atom {i+1}: x={c[0]:8.3f}  y={c[1]:8.3f}  z={c[2]:8.3f}")

# 比较前10个原子
print("\n坐标对比（PDB vs DATA）:")
for i in range(min(10, len(pdb_coords), len(data_coords))):
    px, py, pz = pdb_coords[i]
    dx, dy, dz = data_coords[i]
    print(f"  atom {i+1}: PDB({px:8.3f},{py:8.3f},{pz:8.3f}) -> DATA({dx:8.3f},{dy:8.3f},{dz:8.3f})  diff=({dx-px:8.3f},{dy-py:8.3f},{dz-pz:8.3f})")
