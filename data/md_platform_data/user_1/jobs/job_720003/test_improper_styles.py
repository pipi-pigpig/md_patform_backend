#!/usr/bin/env python3
"""Test different improper_style combinations with opls dihedral"""
import os, subprocess

workdir = "/workspace/data/user_1/jobs/job_720003/inputs"
min_file = os.path.join(workdir, "in.minimization.orig")
settings_file = os.path.join(workdir, "system.in.settings.full")

styles = ["none", "umbrella", "class2", "fourier"]

with open(settings_file, "r") as f:
    settings_content = f.read()

with open(min_file, "r") as f:
    orig_lines = f.readlines()

for style in styles:
    output_file = os.path.join(workdir, f"in.min_test_{style}")
    with open(output_file, "w") as f:
        for line in orig_lines:
            if "improper_style" in line:
                f.write(f"improper_style  {style}\n")
            elif line.strip() == "include         system.in.settings":
                f.write(settings_content)
            elif line.startswith("minimize"):
                f.write("minimize 0.0 1.0e10 1 1\n")
            elif line.startswith("write_data"):
                f.write("write_data minimized.data\n")
            else:
                f.write(line)
    
    result = subprocess.run(
        ["timeout", "15", "lmp", "-in", output_file, "-log", "none"],
        capture_output=True, text=True, cwd=workdir, timeout=20
    )
    last_lines = result.stderr.strip().split("\n")[-3:] if result.stderr else []
    print(f"Style '{style}': exit={result.returncode}, last_lines={last_lines}")