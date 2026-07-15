# 使用你已有的 CUDA 12.8.0 镜像
FROM nvcr.io/nvidia/cuda:12.8.0-runtime-ubuntu22.04

# 设置非交互式安装
ENV DEBIAN_FRONTEND=noninteractive

# 使用阿里云镜像源加速下载
RUN sed -i 's@archive.ubuntu.com@mirrors.aliyun.com@g' /etc/apt/sources.list && \
    sed -i 's@security.ubuntu.com@mirrors.aliyun.com@g' /etc/apt/sources.list

# 安装基础依赖（分步进行，便于调试）
RUN apt-get update && apt-get install -y \
    wget \
    curl \
    git \
    build-essential \
    ca-certificates \
    bc \
    && rm -rf /var/lib/apt/lists/*

# 安装 MPI 和数学库
RUN apt-get update && apt-get install -y \
    openmpi-bin \
    libopenmpi-dev \
    libfftw3-dev \
    liblapack-dev \
    libblas-dev \
    cmake \
    gfortran \
    && rm -rf /var/lib/apt/lists/*

# 安装CUDA编译器（nvcc）
RUN apt-get update && apt-get install -y \
    cuda-nvcc-12-8 \
    && rm -rf /var/lib/apt/lists/*

# 安装 Python 和相关工具
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    python3-dev \
    python3-numpy \
    python3-matplotlib \
    vim \
    && rm -rf /var/lib/apt/lists/*

# 设置工作目录
WORKDIR /opt

# 安装 LAMMPS - 启用GPU支持和分子动力学所需包
RUN echo "开始安装 LAMMPS（启用GPU支持和分子动力学包）..." && \
    wget --no-check-certificate https://download.lammps.org/tars/lammps-23Jun2022.tar.gz && \
    tar -xzvf lammps-23Jun2022.tar.gz && \
    cd lammps-23Jun2022/src && \
    make yes-standard && \
    make yes-gpu && \
    make yes-molecule && \
    make yes-kspace && \
    make yes-rigid && \
    make yes-opt && \
    ln -sf /usr/bin/python3 /usr/bin/python && \
    cd ../lib/gpu && \
    sed -i 's/CUDA_ARCH =.*/CUDA_ARCH = -arch=sm_89/' Makefile.linux && \
    sed -i 's/CUDA_HOME =.*/CUDA_HOME = \/usr\/local\/cuda/' Makefile.linux && \
    make -f Makefile.linux && \
    cd ../../src && \
    make -j4 mpi LMP_INC="-DLAMMPS_EXCEPTIONS" && \
    cp lmp_mpi /usr/local/bin/lmp && \
    cd /opt && \
    rm lammps-23Jun2022.tar.gz && \
    echo "LAMMPS安装完成（已启用: GPU, MOLECULE, KSPACE, RIGID, OPT包）"

# 设置环境变量
ENV LD_LIBRARY_PATH=/usr/local/cuda/lib64:$LD_LIBRARY_PATH
ENV CUDA_HOME=/usr/local/cuda

# 使用清华 pip 镜像
RUN pip3 config set global.index-url https://pypi.tuna.tsinghua.edu.cn/simple && \
    pip3 config set global.trusted-host pypi.tuna.tsinghua.edu.cn

# 安装 Python 包
# 注意: cupy-cuda12x 需要匹配 CUDA 12.x toolkit 版本，若安装失败可降级为仅使用 torch/numpy 后端
RUN pip3 install --no-cache-dir --timeout 100 \
    numpy==1.26.4 \
    matplotlib==3.7.2 \
    pandas==2.0.3 \
    scipy==1.11.0 \
    MDAnalysis \
    cupy-cuda12x \
    Jinja2 \
    torch

# 安装 Moltemplate 和其他分子动力学工具
RUN pip3 install --no-cache-dir --timeout 100 \
    moltemplate \
    ase \
    mbuild

# 安装 packmol
# 注意: VMD和GROMACS需要从官方网站单独下载安装，不在Ubuntu仓库中
RUN apt-get update && apt-get install -y \
    packmol \
    && rm -rf /var/lib/apt/lists/*

# 创建测试脚本
RUN echo '#!/bin/bash' > /usr/local/bin/test-md && \
    echo 'echo "=== LAMMPS MD Platform Test ==="' >> /usr/local/bin/test-md && \
    echo 'echo "CUDA:" $(nvcc --version 2>/dev/null | grep "release" || echo "CUDA runtime only")' >> /usr/local/bin/test-md && \
    echo 'echo "LAMMPS:" $(which lmp 2>/dev/null && lmp -help 2>&1 | head -1 || echo "not found")' >> /usr/local/bin/test-md && \
    echo 'echo "Python:" $(python3 --version)' >> /usr/local/bin/test-md && \
    echo 'python3 -c "import numpy; print(\"NumPy:\", numpy.__version__)"' >> /usr/local/bin/test-md && \
    echo 'python3 -c "import matplotlib; print(\"Matplotlib:\", matplotlib.__version__)"' >> /usr/local/bin/test-md && \
    echo 'echo "Moltemplate:" $(python3 -c "import moltemplate; print(\"installed\")" 2>/dev/null || echo "not found")' >> /usr/local/bin/test-md && \
    echo 'echo "packmol:" $(which packmol 2>/dev/null && packmol --help 2>&1 | head -1 || echo "not found")' >> /usr/local/bin/test-md && \
    echo 'echo "GROMACS:" $(which gmx 2>/dev/null && gmx --version 2>&1 | head -1 || echo "not found")' >> /usr/local/bin/test-md && \
    echo 'echo "所有组件安装完成！"' >> /usr/local/bin/test-md && \
    chmod +x /usr/local/bin/test-md

# 创建工作目录
WORKDIR /workspace

# 设置默认命令
CMD ["tail", "-f", "/dev/null"]