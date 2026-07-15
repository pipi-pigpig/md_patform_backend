#!/bin/bash
echo "=== LAMMPS GPU 测试 ==="
echo "当前目录: $(pwd)"
echo "测试输入文件: test_gpu.in"

# 检查LAMMPS是否可用
if ! command -v lmp &> /dev/null; then
    echo "错误: lmp 命令未找到"
    exit 1
fi

# 运行LAMMPS GPU测试
echo "运行LAMMPS GPU测试..."
lmp -in test_gpu.in -log test_gpu.log

# 检查日志文件中是否包含GPU相关输出
echo "检查日志文件..."
if grep -i "gpu" test_gpu.log; then
    echo "✅ 检测到GPU使用信息"
else
    echo "⚠️  未检测到GPU使用信息，可能未使用GPU加速"
fi

# 检查是否有错误
if grep -i "error" test_gpu.log; then
    echo "❌ 检测到错误"
else
    echo "✅ 未检测到错误"
fi

# 显示最后几行输出
echo "=== 测试结果摘要 ==="
tail -20 test_gpu.log

echo "=== 测试完成 ==="