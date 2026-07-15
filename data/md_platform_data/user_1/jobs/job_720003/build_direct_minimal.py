#!/usr/bin/env python3
"""Build direct input with minimal improper_coeff (no comments, no empty lines)"""
import os

workdir = "/workspace/data/user_1/jobs/job_720003/inputs"
min_file = os.path.join(workdir, "in.minimization.orig")
settings_file = os.path.join(workdir, "system.in.settings.full")
output_file = os.path.join(workdir, "in.min_direct_minimal")

# Read the full settings
with open(settings_file, "r") as f:
    all_lines = f.readlines()

# Remove comment line 31, and simplify improper_coeff section
working_lines = []
for i, line in enumerate(all_lines):
    if i == 30:  # Line 31: comment
        continue
    if 121 <= i <= 125:  # Lines 122-126: improper_coeff section
        continue
    working_lines.append(line)

# Add minimal improper_coeff at the end (before pair_coeff 31)
# Remove the last line (pair_coeff 31 31) and re-add it after improper_coeff
working_lines.pop()  # Remove pair_coeff 31 31
working_lines.append("improper_coeff 1  10.0  0.0\n")
working_lines.append("improper_coeff 2  10.0  0.0\n")
working_lines.append("pair_coeff 31 31 0.00034 2.126\n")

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