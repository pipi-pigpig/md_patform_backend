"""分析packed_system.pdb文件中分子位置分布"""
import math

def analyze_pdb(filename):
    """读取PDB文件并分析分子位置"""
    atoms = []
    with open(filename) as f:
        for line in f:
            if line.startswith('ATOM') or line.startswith('HETATM'):
                # PDB格式: ATOM  serial name resName resID x y z ...
                serial = int(line[6:11].strip())
                res_id = int(line[22:26].strip())
                x = float(line[30:38].strip())
                y = float(line[38:46].strip())
                z = float(line[46:54].strip())
                atoms.append((serial, res_id, x, y, z))

    n = len(atoms)
    print(f'File: {filename}')
    print(f'Total atoms: {n}')

    # 按resID分组
    mols = {}
    for atom in atoms:
        res_id = atom[1]
        if res_id not in mols:
            mols[res_id] = []
        mols[res_id].append(atom)

    print(f'Total molecules (resID): {len(mols)}')

    # 计算每个分子的质心
    centroids = []
    for res_id, mol_atoms in mols.items():
        cx = sum(a[2] for a in mol_atoms) / len(mol_atoms)
        cy = sum(a[3] for a in mol_atoms) / len(mol_atoms)
        cz = sum(a[4] for a in mol_atoms) / len(mol_atoms)
        centroids.append((res_id, cx, cy, cz))

    # 显示前10个分子的质心
    print('\nFirst 10 molecule centroids:')
    for res_id, cx, cy, cz in centroids[:10]:
        print(f'  mol {res_id}: ({cx:.4f}, {cy:.4f}, {cz:.4f})')

    # 检查分子间最小距离
    min_dist = float('inf')
    min_pair = None
    overlap_count = 0
    for i in range(len(centroids)):
        for j in range(i+1, len(centroids)):
            dx = centroids[i][1] - centroids[j][1]
            dy = centroids[i][2] - centroids[j][2]
            dz = centroids[i][3] - centroids[j][3]
            d = math.sqrt(dx*dx + dy*dy + dz*dz)
            if d < min_dist:
                min_dist = d
                min_pair = (centroids[i][0], centroids[j][0])
            if d < 1.0:
                overlap_count += 1

    print(f'\nMin inter-molecular centroid distance: {min_dist:.4f}A (mols {min_pair})')
    print(f'Molecule pairs with centroid distance < 1.0A: {overlap_count}')

    # 统计原子距离分布
    dist_bins = [0.01, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0]
    dist_counts = [0] * len(dist_bins)
    zero_count = 0

    for i in range(n):
        for j in range(i+1, n):
            dx = atoms[i][2] - atoms[j][2]
            dy = atoms[i][3] - atoms[j][3]
            dz = atoms[i][4] - atoms[j][4]
            d = math.sqrt(dx*dx + dy*dy + dz*dz)
            if d == 0.0:
                zero_count += 1
            for k, threshold in enumerate(dist_bins):
                if d < threshold:
                    dist_counts[k] += 1
                    break

    print(f'\nAtom pairs at exactly distance 0.0: {zero_count}')
    print('Atom distance distribution:')
    cumulative = 0
    for k in range(len(dist_bins)):
        cumulative += dist_counts[k]
        print(f'  d < {dist_bins[k]}A: {dist_counts[k]} (cumulative: {cumulative})')

if __name__ == '__main__':
    analyze_pdb('packed_system.pdb')
