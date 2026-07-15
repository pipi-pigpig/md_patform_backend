import re
from collections import Counter

# Check job_1 system.data
atoms = []
in_atoms = False
with open('/workspace/data/user_1/jobs/job_1/inputs/system.data', 'r') as f:
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

total_charge = sum(a[3] for a in atoms)
print("Job 1 system.data analysis:")
print("  Total atoms: {}".format(len(atoms)))
print("  Total charge: {:.3f}".format(total_charge))

mol_charges = {}
mol_types = {}
for a in atoms:
    mol_id = a[1]
    if mol_id not in mol_charges:
        mol_charges[mol_id] = 0.0
        mol_types[mol_id] = Counter()
    mol_charges[mol_id] += a[3]
    mol_types[mol_id][a[2]] += 1

mol_sizes = {}
for m in mol_charges:
    size = sum(mol_types[m].values())
    if size not in mol_sizes:
        mol_sizes[size] = []
    mol_sizes[size].append(m)

print("\nMolecule type analysis:")
for size in sorted(mol_sizes.keys()):
    mids = mol_sizes[size]
    total_charge = sum(mol_charges[m] for m in mids)
    sample_charges = [mol_charges[m] for m in mids[:5]]
    print("  {}-atom molecules ({} of them): total_charge={:+.3f}, sample={}".format(size, len(mids), total_charge, sample_charges))
    # Show one molecule's type distribution
    if len(mids) > 0:
        m = mids[0]
        print("    Mol {} types: {}".format(m, dict(mol_types[m])))