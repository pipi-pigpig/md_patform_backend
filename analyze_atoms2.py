"""分析minimized.data中的原子距离分布和键合关系"""
import math

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
                    if atoms:
                        continue
                    else:
                        continue
                parts = line.split()
                if len(parts) >= 6:
                    # atom_id, mol_id, atom_type, x, y, z
                    atoms.append((int(parts[0]), int(parts[1]), int(parts[2]),
                                  float(parts[3]), float(parts[4]), float(parts[5])))
            elif section == 'bonds':
                if line.strip() == '' or not line[0].isdigit():
                    continue
                parts = line.split()
                if len(parts) >= 4:
                    bonds.add((int(parts[2]), int(parts[3])))  # atom pair

    n = len(atoms)
    print(f'Total atoms: {n}')
    print(f'Total bonds: {len(bonds)}')

    # 建立原子ID到索引的映射
    atom_id_to_idx = {atom[0]: i for i, atom in enumerate(atoms)}

    # 找出所有距离 < 1.0A 的原子对，并判断是否键合
    close_bonded = []
    close_nonbonded = []
    for i in range(n):
        for j in range(i+1, n):
            dx = atoms[i][3] - atoms[j][3]
            dy = atoms[i][4] - atoms[j][4]
            dz = atoms[i][5] - atoms[j][5]
            d = math.sqrt(dx*dx + dy*dy + dz*dz)
            if d < 1.0:
                pair = (atoms[i][0], atoms[j][0])
                pair_rev = (atoms[j][0], atoms[i][0])
                if pair in bonds or pair_rev in bonds:
                    close_bonded.append((d, atoms[i][2], atoms[j][2],
                                         atoms[i][1], atoms[j][1]))
                else:
                    close_nonbonded.append((d, atoms[i][2], atoms[j][2],
                                            atoms[i][1], atoms[j][1]))

    print(f'\nPairs with distance < 1.0A: {len(close_bonded) + len(close_nonbonded)}')
    print(f'  Bonded pairs (1-2): {len(close_bonded)}')
    print(f'  Non-bonded pairs: {len(close_nonbonded)}')

    close_bonded.sort()
    close_nonbonded.sort()

    print('\nClosest 10 BONDED pairs (dist, type1, type2, mol1, mol2):')
    for d, t1, t2, m1, m2 in close_bonded[:10]:
        print(f'  {d:.4f} type {t1}-{t2} mol {m1}-{m2}')

    print('\nClosest 10 NON-BONDED pairs (dist, type1, type2, mol1, mol2):')
    for d, t1, t2, m1, m2 in close_nonbonded[:10]:
        print(f'  {d:.4f} type {t1}-{t2} mol {m1}-{m2}')

    # 检查分子内部重叠
    print('\nMolecular overlap analysis:')
    mol_atoms = {}
    for atom in atoms:
        mol_id = atom[1]
        if mol_id not in mol_atoms:
            mol_atoms[mol_id] = []
        mol_atoms[mol_id].append(atom)

    overlapping_mols = 0
    for mol_id, mol_atom_list in mol_atoms.items():
        has_overlap = False
        for i in range(len(mol_atom_list)):
            for j in range(i+1, len(mol_atom_list)):
                dx = mol_atom_list[i][3] - mol_atom_list[j][3]
                dy = mol_atom_list[i][4] - mol_atom_list[j][4]
                dz = mol_atom_list[i][5] - mol_atom_list[j][5]
                d = math.sqrt(dx*dx + dy*dy + dz*dz)
                if d < 0.5:
                    has_overlap = True
                    break
            if has_overlap:
                break
        if has_overlap:
            overlapping_mols += 1

    print(f'  Total molecules: {len(mol_atoms)}')
    print(f'  Molecules with internal overlap (d<0.5A): {overlapping_mols}')

if __name__ == '__main__':
    analyze_data('minimized.data')
