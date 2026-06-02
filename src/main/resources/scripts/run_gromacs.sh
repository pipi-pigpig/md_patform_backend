#!/bin/bash
# GROMACS运行脚本

JOB_ID=$1
USE_GPU=$2

echo "Starting GROMACS simulation for job $JOB_ID"
echo "GPU: $USE_GPU"

# 进入工作目录
cd /workspace/inputs/$JOB_ID

# 检查必要的文件是否存在
if [ ! -f "system.top" ] || [ ! -f "system.gro" ]; then
    echo "Error: Required files (system.top, system.gro) not found"
    exit 1
fi

# 设置GPU参数
GPU_FLAGS=""
if [ "$USE_GPU" = "true" ]; then
    GPU_FLAGS="-nb gpu -pme gpu"
    echo "Using GPU acceleration"
fi

# 执行GROMACS模拟流程
echo "Step 1: Energy minimization"
gmx grompp -f em.mdp -c system.gro -p system.top -o em.tpr
gmx mdrun -v -deffnm em $GPU_FLAGS

echo "Step 2: NVT equilibration"
gmx grompp -f nvt.mdp -c em.gro -r em.gro -p system.top -o nvt.tpr
gmx mdrun -v -deffnm nvt $GPU_FLAGS

echo "Step 3: NPT equilibration"
gmx grompp -f npt.mdp -c nvt.gro -r nvt.gro -t nvt.cpt -p system.top -o npt.tpr
gmx mdrun -v -deffnm npt $GPU_FLAGS

echo "Step 4: Production MD"
gmx grompp -f md.mdp -c npt.gro -t npt.cpt -p system.top -o md.tpr
gmx mdrun -v -deffnm md $GPU_FLAGS

# 检查执行状态
if [ $? -eq 0 ]; then
    echo "GROMACS simulation completed successfully"

    # 复制结果文件
    cp md.* /workspace/results/$JOB_ID/ 2>/dev/null || true

    exit 0
else
    echo "GROMACS simulation failed"
    exit 1
fi