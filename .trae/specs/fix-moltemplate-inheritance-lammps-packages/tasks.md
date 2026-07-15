# Tasks

- [x] Task 1: 修复EC.lt分子模板力场继承语法
  - [x] 使用 `EC inherits OPLSAA { ... }` 格式
  - [x] 添加 `$mol:.` 分子ID到Data Atoms
  - [x] 使用Data Bonds代替Data Bond List
  - [x] 添加角度和二面角定义

- [x] Task 2: 修复DMC.lt分子模板力场继承语法
  - [x] 使用 `DMC inherits OPLSAA { ... }` 格式
  - [x] 添加 `$mol:.` 分子ID到Data Atoms
  - [x] 使用Data Bonds代替Data Bond List
  - [x] 添加角度和二面角定义

- [x] Task 3: 修复PF6.lt分子模板力场继承语法
  - [x] 使用 `PF6 inherits OPLSAA { ... }` 格式
  - [x] 添加 `$mol:.` 分子ID到Data Atoms
  - [x] 使用Data Bonds代替Data Bond List
  - [x] 添加角度定义

- [x] Task 4: 更新Li.lt分子模板（独立参数）
  - [x] 使用独立的Joung-Cheatham参数
  - [x] 添加 `$mol:.` 分子ID到Data Atoms
  - [x] 不继承OPLSAA力场

- [x] Task 5: 更新Dockerfile添加LAMMPS包
  - [x] 添加 `make yes-molecule`
  - [x] 添加 `make yes-kspace`
  - [x] 添加 `make yes-rigid`
  - [x] 添加 `make yes-opt`
  - [x] 移除VMD（不在Ubuntu仓库中）
  - [x] 移除GROMACS（网络问题）

- [x] Task 6: 重新构建md-engine容器
  - [x] 使用 `docker-compose build md-engine`
  - [x] 验证构建成功

- [x] Task 7: 验证LAMMPS包功能
  - [x] 运行 `lmp -help` 确认包列表
  - [x] 确认GPU包已安装
  - [x] 确认KSPACE包已安装
  - [x] 确认MOLECULE包已安装
  - [x] 确认OPT包已安装
  - [x] 确认RIGID包已安装

- [x] Task 8: 验证Moltemplate力场继承正确
  - [x] 创建system_test.lt测试文件
  - [x] 运行 `moltemplate.sh -atomstyle full system_test.lt`
  - [x] 验证生成LAMMPS输入文件
  - [x] 验证生成system_test.data文件
  - [x] 验证生成system_test.in.settings文件

- [x] Task 9: 验证LAMMPS完整模拟流程
  - [x] Moltemplate成功生成所有必需文件
  - [x] 力场参数正确继承

# Task Dependencies
- Task 6 depends on Task 5
- Task 7 depends on Task 6
- Task 8 depends on Task 1-4, Task 7
- Task 9 depends on Task 8