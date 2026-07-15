#!/usr/bin/env python3
"""Clean non-ASCII characters from system.in.settings"""
import sys

filepath = "inputs/system.in.settings"
with open(filepath, "r", encoding="utf-8") as f:
    data = f.read()

cleaned = "".join(c if ord(c) <= 127 else " " for c in data)

with open(filepath, "w", encoding="utf-8") as f:
    f.write(cleaned)

non_ascii = sum(1 for c in data if ord(c) > 127)
print(f"Cleaned {len(data)} chars, removed {non_ascii} non-ASCII chars")