#!/usr/bin/env python3
"""Build LAMMPS minimization input files for testing."""

import os
import subprocess
import sys

workdir = '/workspace/data/user_1/jobs/job_720003/inputs'

# Test 1: Soft potential + FIRE
content = """# Soft potential minimization to resolve overlaps
units           real
atom_style      full
dimension       3
boundary        p p p

pair_style      soft 1.0
bond_style      harmonic
angle_style     harmonic
dihedral_style  none
improper_style  none
special_bonds   lj 1.0 1.0 1.0 coul 1.0 1.0 1.0
kspace_style    none

read_data       system.data

pair_coeff      * * 10.0
include         system.in.bond_angle_only

neighbor        2.0 bin
neigh_modify    every 1 delay 0 check yes
thermo          1000
thermo_style    custom step temp pe ke etotal press vol

min_style       fire
minimize 0.0 1.0e-4 100 1000

write_restart   soft.restart
"""

with open(os.path.join(workdir, 'in.min_soft_py'), 'w') as f:
    f.write(content)

print("Written in.min_soft_py")

# Run LAMMPS
os.chdir(workdir)
result = subprocess.run(
    ['timeout', '30', 'lmp', '-in', 'in.min_soft_py', '-log', '../raw_outputs/log.min_soft_py', '-nonbuf'],
    capture_output=True, text=True, timeout=35
)
print("STDOUT:", result.stdout[-500:] if result.stdout else "(empty)")
print("STDERR:", result.stderr[-500:] if result.stderr else "(empty)")
print("Return code:", result.returncode)