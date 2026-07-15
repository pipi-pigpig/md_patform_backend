"""
check_overlaps.py - 检查 minimized.data 和 packed_system.pdb 中的原子重叠

功能：
    1. 读取 LAMMPS data 文件或 PDB 文件
    2. 计算所有原子对之间的距离
    3. 统计距离小于阈值的原子对
    4. 输出最小的原子间距和对应的原子

作者: Electrolyte MD Platform
版本: 1.0
"""

import sys
import numpy as np


def read_lammps_data(filename):
    """
    读取 LAMMPS data 文件中的原子坐标

    参数:
        filename: LAMMPS data 文件路径

    返回:
        coords: numpy array (N, 3)，原子坐标
        types: list，原子类型
    """
    coords = []
    types = []
    with open(filename) as f:
        lines = f.readlines()

    # 找到 Atoms 部分
    atoms_start = -1
    atoms_count = 0
    for i, line in enumerate(lines):
        if line.strip().startswith("Atoms"):
            atoms_start = i + 2  # 跳过空行
            break
        if "atoms" in line.lower() and "=" not in line:
            parts = line.split()
            if parts and parts[0].isdigit():
                atoms_count = int(parts[0])

    if atoms_start < 0:
        raise ValueError("未找到 Atoms 部分")

    # 读取原子坐标
    # atom_style full: id mol type q x y z
    for i in range(atoms_start, min(atoms_start + atoms_count, len(lines))):
        parts = lines[i].split()
        if len(parts) >= 7:
            atom_type = int(parts[2])
            x, y, z = float(parts[4]), float(parts[5]), float(parts[6])
            coords.append([x, y, z])
            types.append(atom_type)

    return np.array(coords), types


def read_pdb(filename):
    """
    读取 PDB 文件中的原子坐标

    参数:
        filename: PDB 文件路径

    返回:
        coords: numpy array (N, 3)
        names: list，原子名称
    """
    coords = []
    names = []
    with open(filename) as f:
        for line in f:
            if line.startswith("ATOM"):
                parts = line.split()
                name = parts[2]
                x, y, z = float(parts[5]), float(parts[6]), float(parts[7])
                coords.append([x, y, z])
                names.append(name)
    return np.array(coords), names


def check_overlaps(coords, threshold=1.5, name=""):
    """
    检查原子重叠

    参数:
        coords: 坐标数组
        threshold: 重叠阈值 (Å)
        name: 文件标识名
    """
    n = len(coords)
    min_dist = float('inf')
    min_pair = None
    overlaps = []

    for i in range(n):
        for j in range(i + 1, n):
            d = np.linalg.norm(coords[i] - coords[j])
            if d < min_dist:
                min_dist = d
                min_pair = (i, j)
            if d < threshold:
                overlaps.append((i, j, d))

    print(f"\n{'=' * 60}")
    print(f"文件: {name}")
    print(f"原子数: {n}")
    print(f"最小原子间距: {min_dist:.4f} Å (原子 {min_pair[0]} 和 {min_pair[1]})")
    print(f"重叠原子对（< {threshold} Å）: {len(overlaps)}")

    if overlaps:
        print(f"\n重叠原子对详情（前 20 个）:")
        for i, (a, b, d) in enumerate(sorted(overlaps, key=lambda x: x[2])[:20]):
            print(f"  {a + 1:4d} - {b + 1:4d}: {d:.4f} Å")

    # 统计距离分布
    dists = []
    for i in range(n):
        for j in range(i + 1, n):
            d = np.linalg.norm(coords[i] - coords[j])
            dists.append(d)

    dists = np.array(dists)
    print(f"\n距离分布:")
    print(f"  < 0.5 Å: {np.sum(dists < 0.5)}")
    print(f"  < 1.0 Å: {np.sum(dists < 1.0)}")
    print(f"  < 1.5 Å: {np.sum(dists < 1.5)}")
    print(f"  < 2.0 Å: {np.sum(dists < 2.0)}")
    print(f"  < 2.5 Å: {np.sum(dists < 2.5)}")
    print(f"  平均距离: {np.mean(dists):.2f} Å")
    print(f"  中位数: {np.median(dists):.2f} Å")

    return min_dist, overlaps


def main():
    """主函数"""
    base = r"d:\electrolyte-md-platform\data\md_platform_data\user_1\jobs\job_1140001\inputs"

    # 检查 minimized.data
    min_file = base + r"\minimized.data"
    try:
        coords, types = read_lammps_data(min_file)
        check_overlaps(coords, threshold=1.5, name="minimized.data")
    except Exception as e:
        print(f"读取 minimized.data 失败: {e}")

    # 检查 packed_system.pdb
    pdb_file = base + r"\packed_system.pdb"
    try:
        coords, names = read_pdb(pdb_file)
        check_overlaps(coords, threshold=1.5, name="packed_system.pdb")
    except Exception as e:
        print(f"读取 packed_system.pdb 失败: {e}")

    # 检查 system.data
    sys_file = base + r"\system.data"
    try:
        coords, types = read_lammps_data(sys_file)
        check_overlaps(coords, threshold=1.5, name="system.data")
    except Exception as e:
        print(f"读取 system.data 失败: {e}")


if __name__ == "__main__":
    main()
