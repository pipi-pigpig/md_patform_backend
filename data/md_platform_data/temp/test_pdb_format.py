#!/usr/bin/env python3
"""
检查PDB文件的列格式是否正确
PDB格式中，坐标应该在固定列位置：
  X: columns 31-38 (0-indexed: 30-37)
  Y: columns 39-46 (0-indexed: 38-45)
  Z: columns 47-54 (0-indexed: 46-53)
"""

pdb_path = '/workspace/data/user_1/jobs/job_1/inputs/packed_system.pdb'

with open(pdb_path, 'r') as f:
    for i, line in enumerate(f):
        if line.startswith('ATOM') or line.startswith('HETATM'):
            # 按PDB固定列格式解析
            x_pdb = line[30:38].strip()
            y_pdb = line[38:46].strip()
            z_pdb = line[46:54].strip()

            # 按awk默认字段分隔解析
            fields = line.split()
            x_awk = fields[4] if len(fields) > 4 else 'N/A'
            y_awk = fields[5] if len(fields) > 5 else 'N/A'
            z_awk = fields[6] if len(fields) > 6 else 'N/A'

            print(f"Line {i+1}: PDB格式({x_pdb}, {y_pdb}, {z_pdb})  AWK格式({x_awk}, {y_awk}, {z_awk})")

            if i >= 5:
                break

# 检查moltemplate提取的坐标
import subprocess
result = subprocess.run(
    ["awk", '/^ATOM  |^HETATM/{print substr($0,31,8)" "substr($0,39,8)" "substr($0,47,8)}', pdb_path],
    capture_output=True, text=True
)
print("\nmoltemplate提取的坐标（前10行）:")
for i, line in enumerate(result.stdout.strip().split('\n')[:10]):
    print(f"  {line}")
