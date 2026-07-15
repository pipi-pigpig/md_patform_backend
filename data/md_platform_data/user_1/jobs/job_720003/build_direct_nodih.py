#!/usr/bin/env python3
"""Build direct input with improper_coeff but NO dihedral_coeff"""
import os

workdir = "/workspace/data/user_1/jobs/job_720003/inputs"
min_file = os.path.join(workdir, "in.minimization.orig")
settings_file = os.path.join(workdir, "system.in.settings.full")
output_file = os.path.join(workdir, "in.min_direct_nodih")

# Read the full settings
with open(settings_file, "r") as f:
    all_lines = f.readlines()

# Remove comment line 31, dihedral_coeff section (lines 101-121), keep improper_coeff
working_lines = []
for i, line in enumerate(all_lines):
    if i == 30:  # Line 31: comment
        continue
    if 100 <= i <= 120:  # Lines 101-121: dihedral_coeff section
        continue
    working_lines.append(line)

settings_content = "".join(working_lines)

with open(min_file, "r") as f:
    lines = f.readlines()

with open(output_file, "w") as f:
    for line in lines:
        if line.strip() == "include         system.in.settings":
            f.write(settings_content)
            if not settings_content.endswith("\n"):
                f.write("\n")
        elif line.startswith("minimize"):
            f.write("minimize 0.0 1.0e10 1 1\n")
        elif line.startswith("write_data"):
            f.write("write_data minimized.data\n")
        else:
            f.write(line)

print(f"Written {output_file}")
print(f"Lines: {len(open(output_file).readlines())}")