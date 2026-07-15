#!/usr/bin/env python3
"""Build direct input with dummy improper_coeff values"""
import os

workdir = "/workspace/data/user_1/jobs/job_720003/inputs"
min_file = os.path.join(workdir, "in.minimization.orig")
settings_file = os.path.join(workdir, "system.in.settings.full")
output_file = os.path.join(workdir, "in.min_direct_dummy")

# Read the full settings
with open(settings_file, "r") as f:
    all_lines = f.readlines()

# Remove comment line 31, and improper_coeff section, add dummy improper_coeff
working_lines = []
for i, line in enumerate(all_lines):
    if i == 30:  # Line 31: comment
        continue
    if 121 <= i <= 125:  # Lines 122-126: improper_coeff section
        continue
    working_lines.append(line)

# Remove pair_coeff 31 31, add dummy improper_coeff, then re-add pair_coeff 31
working_lines.pop()
working_lines.append("improper_coeff 1  50.0  0.0\n")
working_lines.append("improper_coeff 2  50.0  0.0\n")
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