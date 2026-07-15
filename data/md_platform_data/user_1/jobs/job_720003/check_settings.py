#!/usr/bin/env python3
"""Check system.in.settings for issues"""
import os

filepath = "/workspace/data/user_1/jobs/job_720003/inputs/system.in.settings"
with open(filepath, "rb") as f:
    data = f.read()

non_ascii = [(i, data[i]) for i in range(len(data)) if data[i] > 127]
print(f"File size: {len(data)} bytes")
print(f"Non-ASCII bytes: {len(non_ascii)}")
for idx, byte in non_ascii[:20]:
    ctx_start = max(0, idx - 5)
    ctx_end = min(len(data), idx + 6)
    print(f"  Position {idx}: byte=0x{byte:02x}, context={data[ctx_start:ctx_end]}")

print(f"\nLast 80 bytes: {data[-80:]!r}")

# Check line endings
cr = data.count(b'\r')
lf = data.count(b'\n')
crlf = data.count(b'\r\n')
print(f"\nLine endings: CR={cr}, LF={lf}, CRLF={crlf}")

# Check for empty lines at end
lines = data.decode('ascii', errors='replace').split('\n')
print(f"Total lines: {len(lines)}")
print(f"Last 5 lines:")
for i, line in enumerate(lines[-5:]):
    print(f"  Line {len(lines)-5+i+1}: {line!r}")