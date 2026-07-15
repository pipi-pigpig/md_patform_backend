#!/usr/bin/env python3
"""Check atom overlaps in minimized_soft.data"""
import sys, math, os

os.chdir('/workspace/data/user_1/jobs/job_720003/inputs')

with open('minimized_soft.data', 'r') as f:
    lines = f.readlines()

# Find Atoms section
in_atoms = False
atoms = []
for line in lines:
    if 'Atoms' in line and 'atom types' not in line and 'Atoms#' not in line:
        in_atoms = True
        continue
    if in_atoms:
        if line.strip() == '':
            break
        if 'Velocities' in line or 'Bonds' in line:
            break
        parts = line.split()
        if len(parts) >= 7:
            try:
                atoms.append({
                    'id': int(parts[0]),
                    'mol': int(parts[1]),
                    'type': int(parts[2]),
                    'charge': float(parts[3]),
                    'x': float(parts[4]),
                    'y': float(parts[5]),
                    'z': float(parts[6])
                })
            except ValueError:
                pass

print(f"Read {len(atoms)} atoms")

# Check for extremely close atoms (sample first 1000)
close_count = 0
min_dist = float('inf')
sample = atoms[:1000]
for i in range(len(sample)):
    for j in range(i+1, len(sample)):
        dx = sample[i]['x'] - sample[j]['x']
        dy = sample[i]['y'] - sample[j]['y']
        dz = sample[i]['z'] - sample[j]['z']
        dist = math.sqrt(dx*dx + dy*dy + dz*dz)
        min_dist = min(min_dist, dist)
        if dist < 0.5:
            close_count += 1
            if close_count <= 10:
                print(f"CLOSE: atom {sample[i]['id']} (mol {sample[i]['mol']}, type {sample[i]['type']}) and atom {sample[j]['id']} (mol {sample[j]['mol']}, type {sample[j]['type']}) distance = {dist:.4f}")

print(f"Total close pairs (<0.5A) in first 1000 atoms: {close_count}")
print(f"Minimum distance in first 1000 atoms: {min_dist:.4f}")
print("Done")