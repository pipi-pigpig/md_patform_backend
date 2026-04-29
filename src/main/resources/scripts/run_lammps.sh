#!/bin/bash
# LAMMPS运行脚本

JOB_ID=$1
INPUT_FILE=$2
NUM_PROCESSORS=$3
USE_GPU=$4

echo "Starting LAMMPS simulation for job $JOB_ID"
echo "Input file: $INPUT_FILE"
echo "Processors: $NUM_PROCESSORS"
echo "GPU: $USE_GPU"

# 进入工作目录
cd /workspace/inputs/$JOB_ID

# 根据是否使用GPU选择命令
if [ "$USE_GPU" = "true" ]; then
    echo "Using GPU acceleration"
    mpirun -np $NUM_PROCESSORS lmp -sf gpu -pk gpu 1 -in $INPUT_FILE -log /workspace/results/$JOB_ID/lammps.log
else
    echo "Using CPU only"
    mpirun -np $NUM_PROCESSORS lmp -in $INPUT_FILE -log /workspace/results/$JOB_ID/lammps.log
fi

# 检查执行状态
if [ $? -eq 0 ]; then
    echo "LAMMPS simulation completed successfully"
    exit 0
else
    echo "LAMMPS simulation failed"
    exit 1
fi