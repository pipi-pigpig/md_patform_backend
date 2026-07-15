#!/usr/bin/env python3
"""Build direct input with improper_coeff moved BEFORE angle_coeff"""
import os

workdir = "/workspace/data/user_1/jobs/job_720003/inputs"
min_file = os.path.join(workdir, "in.minimization.orig")
settings_file = os.path.join(workdir, "system.in.settings.full")
output_file = os.path.join(workdir, "in.min_direct_reorder")

# Read the full settings
with open(settings_file, "r") as f:
    all_lines = f.readlines()

# Move improper_coeff (lines 122-126) to before angle_coeff (line 65)
# Remove comment line 31
reordered = []
for i, line in enumerate(all_lines):
    if i == 30:  # Line 31: comment
        continue
    if 121 <= i <= 125:  # Lines 122-126: improper_coeff section
        continue
    if i == 64:  # Before line 65 (angle_coeff section starts at 66)
        # Insert improper_coeff here
        reordered.append("improper_coeff 1  10.0  0.0\n")
        reordered.append("improper_coeff 2  10.0  0.0\n")
    reordered.append(line)

settings_content = "".join(reordered)

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