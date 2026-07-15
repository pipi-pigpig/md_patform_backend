"""分析minimized.data中的原子距离分布"""
import math

def analyze_data(filename):
    """读取LAMMPS data文件并分析最近原子对距离"""
    atoms = []
    with open(filename) as f:
        in_atoms = False
        for line in f:
            if line.startswith('Atoms'):
                in_atoms = True
                next(f)
                continue
            if in_atoms:
                if line.strip() == '' or not line[0].isdigit():
                    if atoms:
                        break
                    continue
                parts = line.split()
                if len(parts) >= 6:
                    atoms.append((int(parts[0]), int(parts[1]),
                                  float(parts[3]), float(parts[4]), float(parts[5])))

    n = len(atoms)
    print(f'Total atoms: {n}')

    # 找出所有距离 < 1.0A 的原子对
    close_pairs = []
    for i in range(n):
        for j in range(i+1, n):
            dx = atoms[i][2] - atoms[j][2]
            dy = atoms[i][3] - atoms[j][3]
            dz = atoms[i][4] - atoms[j][4]
            d = math.sqrt(dx*dx + dy*dy + dz*dz)
            if d < 1.0:
                close_pairs.append((d, atoms[i][1], atoms[j][1]))

    close_pairs.sort()
    print(f'Pairs with distance < 1.0A: {len(close_pairs)}')
    print('Closest 15 pairs (dist, type1, type2):')
    for d, t1, t2 in close_pairs[:15]:
        print(f'  {d:.4f} type {t1} - type {t2}')

    # 统计距离分布
    dist_bins = [0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0]
    dist_counts = [0] * (len(dist_bins))
    for i in range(n):
        for j in range(i+1, n):
            dx = atoms[i][2] - atoms[j][2]
            dy = atoms[i][3] - atoms[j][3]
            dz = atoms[i][4] - atoms[j][4]
            d = math.sqrt(dx*dx + dy*dy + dz*dz)
            for k, threshold in enumerate(dist_bins):
                if d < threshold:
                    dist_counts[k] += 1
                    break

    print('\nDistance distribution:')
    for k in range(len(dist_bins)):
        print(f'  d < {dist_bins[k]}A: {dist_counts[k]} pairs')

if __name__ == '__main__':
    analyze_data('minimized.data')
