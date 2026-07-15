import re
from collections import Counter

atoms = []
in_atoms = False
with open('/workspace/data/user_1/jobs/job_630001/inputs/system.data', 'r') as f:
    for line in f:
        stripped = line.strip()
        if 'Atoms' in stripped and 'full' in stripped:
            in_atoms = True
            continue
        if in_atoms:
            if stripped == '':
                continue
            if stripped.startswith('Bonds') or stripped.startswith('Velocities'):
                break
            parts = stripped.split()
            if len(parts) >= 7 and parts[0].isdigit():
                atom_id = int(parts[0])
                mol_id = int(parts[1])
                atom_type = int(parts[2])
                charge = float(parts[3].replace('+', ''))
                atoms.append((atom_id, mol_id, atom_type, charge))

mol_charges = {}
mol_types = {}
for a in atoms:
    mol_id = a[1]
    if mol_id not in mol_charges:
        mol_charges[mol_id] = 0.0
        mol_types[mol_id] = Counter()
    mol_charges[mol_id] += a[3]
    mol_types[mol_id][a[2]] += 1

print("First 5 molecules charge breakdown:")
mol_ids = sorted(mol_charges.keys())
for mid in mol_ids[:5]:
    c = mol_charges[mid]
    types = dict(mol_types[mid])
    print("  Mol {}: total_charge={:+.3f}, type_dist={}".format(mid, c, types))

mol_sizes = {}
for m in mol_ids:
    size = sum(mol_types[m].values())
    if size not in mol_sizes:
        mol_sizes[size] = []
    mol_sizes[size].append(m)

print("\nMolecule size distribution:")
for size in sorted(mol_sizes.keys()):
    mids = mol_sizes[size]
    all_charges = [mol_charges[m] for m in mids]
    print("  {} atoms/mol: count={}, sample_charges={}".format(size, len(mids), all_charges[:5]))

neutral_count = sum(1 for c in mol_charges.values() if abs(c) < 0.01)
non_neutral = {m: c for m, c in mol_charges.items() if abs(c) >= 0.01}
print("\nNeutral molecules: {} / {}".format(neutral_count, len(mol_charges)))
print("Non-neutral molecules: {} / {}".format(len(non_neutral), len(mol_charges)))

print("\n=== Molecule type analysis ===")
for size in sorted(mol_sizes.keys()):
    mids = mol_sizes[size]
    total_charge = sum(mol_charges[m] for m in mids)
    print("  {}-atom molecules ({} of them): total_charge={:+.3f}".format(size, len(mids), total_charge))
    if len(mids) < 10:
        for m in mids:
            print("    Mol {}: charge={:+.3f}, types={}".format(m, mol_charges[m], dict(mol_types[m])))