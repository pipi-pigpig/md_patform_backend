import sys
min_dist = float('inf')
close_pairs = []
atoms = []
with open('packed_system.pdb') as f:
    for line in f:
        if line.startswith('ATOM') or line.startswith('HETATM'):
            x = float(line[30:38])
            y = float(line[38:46])
            z = float(line[46:54])
            atoms.append((x,y,z))
print(f'Total atoms: {len(atoms)}')
for i in range(min(5000, len(atoms))):
    for j in range(i+1, min(5000, len(atoms))):
        dx = atoms[i][0] - atoms[j][0]
        dy = atoms[i][1] - atoms[j][1]
        dz = atoms[i][2] - atoms[j][2]
        dist = (dx*dx + dy*dy + dz*dz) ** 0.5
        if dist < min_dist:
            min_dist = dist
        if dist < 0.5:
            close_pairs.append((i, j, dist))
print(f'Min distance among first 5000 atoms: {min_dist:.4f}')
print(f'Close pairs (<0.5): {len(close_pairs)}')
for i, j, d in close_pairs[:5]:
    print(f'  atoms {i} and {j}: {d:.4f} A')
