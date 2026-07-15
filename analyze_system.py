"""分析system.data文件（Packmol原始输出）的原子距离分布"""
import math
import sys

def analyze_data(filename):
    """读取LAMMPS data文件并分析原子距离与键合关系"""
    atoms = []
    bonds = set()
    with open(filename) as f:
        section = None
        for line in f:
            if line.startswith('Atoms'):
                section = 'atoms'
                next(f)
                continue
            elif line.startswith('Bonds'):
                section = 'bonds'
                next(f)
                continue
            elif line.startswith('Velocities') or line.startswith('Angles') or \
                 line.startswith('Dihedrals') or line.startswith('Impropers'):
                section = None
                continue

            if section == 'atoms':
                if line.strip() == '' or not line[0].isdigit():
                    continue
                parts = line.split()
                if len(parts) >= 6:
                    atoms.append((int(parts[0]), int(parts[1]), int(parts[2]),
                                  float(parts[3]), float(parts[4]), float(parts[5])))
            elif section == 'bonds':
                if line.strip() == '' or not line[0].isdigit():
                    continue
                parts = line.split()
                if len(parts) >= 4:
                    bonds.add((int(parts[2]), int(parts[3])))

    n = len(atoms)
    print(f'File: {filename}')
    print(f'Total atoms: {n}')
    print(f'Total bonds: {len(bonds)}')

    # 统计距离分布
    dist_bins = [0.01, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0]
    dist_counts = [0] * len(dist_bins)
    zero_dist_count = 0

    for i in range(n):
        for j in range(i+1, n):
            dx = atoms[i][3] - atoms[j][3]
            dy = atoms[i][4] - atoms[j][4]
            dz = atoms[i][5] - atoms[j][5]
            d = math.sqrt(dx*dx + dy*dy + dz*dz)
            if d == 0.0:
                zero_dist_count += 1
            for k, threshold in enumerate(dist_bins):
                if d < threshold:
                    dist_counts[k] += 1
                    break

    print(f'Pairs at exactly distance 0.0: {zero_dist_count}')
    print('Distance distribution:')
    for k in range(len(dist_bins)):
        print(f'  d < {dist_bins[k]}A: {dist_counts[k]} pairs')

    # 检查盒子尺寸
    with open(filename) as f:
        for line in f:
            if 'xlo' in line and 'xhi' in line:
                parts = line.split()
                xlo, xhi = float(parts[0]), float(parts[1])
                print(f'Box X: {xlo} to {xhi} (length={xhi-xlo})')
            if 'ylo' in line and 'yhi' in line:
                parts = line.split()
                ylo, yhi = float(parts[0]), float(parts[1])
                print(f'Box Y: {ylo} to {yhi} (length={yhi-ylo})')
            if 'zlo' in line and 'zhi' in line:
                parts = line.split()
                zlo, zhi = float(parts[0]), float(parts[1])
                print(f'Box Z: {zlo} to {zhi} (length={zhi-zlo})')
            if line.startswith('Atoms'):
                break

    # 检查前5个原子的位置
    print('\nFirst 5 atoms:')
    for i in range(min(5, n)):
        print(f'  id={atoms[i][0]} mol={atoms[i][1]} type={atoms[i][2]} pos=({atoms[i][3]:.4f}, {atoms[i][4]:.4f}, {atoms[i][5]:.4f})')

if __name__ == '__main__':
    filename = sys.argv[1] if len(sys.argv) > 1 else 'system.data'
    analyze_data(filename)
