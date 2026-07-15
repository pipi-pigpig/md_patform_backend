# Tasks

## DockerService重构

- [x] Task 1: 重构DockerService.runLAMMPS()方法签名和实现
  - [x] SubTask 1.1: 修改runLAMMPS方法签名，添加containerWorkDir和containerOutputDir参数
  - [x] SubTask 1.2: 修改LAMMPS命令构建逻辑，使用传入的工作目录和输出目录路径
  - [x] SubTask 1.3: 保留GPU/CPU模式选择逻辑
  - [x] SubTask 1.4: 确保log.lammps输出到指定的输出目录
  - [x] SubTask 1.5: 添加详细的执行日志记录

## MDExecutorService重构

- [x] Task 2: 重构MDExecutorService实现三阶段LAMMPS模拟编排
  - [x] SubTask 2.1: 注入PathUtil、SimulationRawOutputRepository等依赖
  - [x] SubTask 2.2: 重构executeSimulation方法，使用PathUtil生成所有路径
  - [x] SubTask 2.3: 实现三阶段顺序执行逻辑（minimization→equilibrium→production）
  - [x] SubTask 2.4: 实现阶段间数据传递验证（检查minimized.data、equilibrated.data是否存在）
  - [x] SubTask 2.5: 实现阶段失败时停止执行并标记任务为FAILED的逻辑
  - [x] SubTask 2.6: 删除硬编码路径（data/inputs、data/results等）
  - [x] SubTask 2.7: 添加详细的执行日志记录

## 性质专属输出文件收集

- [x] Task 3: 实现根据target_properties收集性质专属输出文件
  - [x] SubTask 3.1: 定义target_properties与输出文件的映射关系
  - [x] SubTask 3.2: 实现collectOutputFiles方法，根据target_properties收集对应的输出文件
  - [x] SubTask 3.3: 将输出文件从inputs工作目录移动到raw_outputs目录
  - [x] SubTask 3.4: 始终收集通用输出文件（log.lammps、dump.trajectory.lammpstrj、final.data）
  - [x] SubTask 3.5: 实现文件移动的原子操作和错误处理

## SimulationRawOutput实体更新

- [x] Task 4: 更新SimulationRawOutput实体添加性质专属文件路径字段
  - [x] SubTask 4.1: 添加chargeTrajectoryFilePath字段（dump.charge.lammpstrj路径）
  - [x] SubTask 4.2: 添加solvationTrajectoryFilePath字段（dump.solvation.lammpstrj路径）
  - [x] SubTask 4.3: 添加pressureFilePath字段（pressure.dat路径）
  - [x] SubTask 4.4: 添加dipoleFilePath字段（dipole.dat路径）
  - [x] SubTask 4.5: 添加msdFilePath字段（msd.dat路径）
  - [x] SubTask 4.6: 添加finalDataFilePath字段（final.data路径）
  - [x] SubTask 4.7: 更新数据库DDL脚本

## Python阶段脚本

- [x] Task 5: 创建stage4_lammps_execution.py阶段脚本
  - [x] SubTask 5.1: 创建stages/stage4_lammps_execution.py文件
  - [x] SubTask 5.2: 实现execute_lammps_stages()函数，封装三阶段执行逻辑
  - [x] SubTask 5.3: 实现collect_property_outputs()函数，根据target_properties收集输出文件
  - [x] SubTask 5.4: 实现move_outputs_to_raw_dir()函数，将输出文件移动到raw_outputs目录
  - [x] SubTask 5.5: 在run_modeling.py中添加--mode lammps-execution入口
  - [x] SubTask 5.6: 在stages/__init__.py中导出新阶段

## 集成与验证

- [x] Task 6: 集成LAMMPS执行到完整建模流程
  - [x] SubTask 6.1: 在MoltemplateService.executeFullModeling()中添加步骤8（LAMMPS执行）
  - [x] SubTask 6.2: 步骤8在步骤7.5（Jinja2脚本生成）之后执行
  - [x] SubTask 6.3: 步骤8调用MDExecutorService执行三阶段模拟
  - [x] SubTask 6.4: 步骤8收集输出文件并更新SimulationRawOutput记录
  - [x] SubTask 6.5: 更新FullModelingResult DTO添加LAMMPS执行结果字段

- [x] Task 7: 验证
  - [x] SubTask 7.1: 验证三阶段LAMMPS模拟顺序执行正确
  - [x] SubTask 7.2: 验证性质专属输出文件正确收集到raw_outputs目录
  - [x] SubTask 7.3: 验证SimulationRawOutput正确记录所有文件路径
  - [x] SubTask 7.4: 验证文件路径遵循目录结构规则（无硬编码路径）
  - [x] SubTask 7.5: Spring Boot编译成功

# Task Dependencies

- Task 1 依赖无（可独立开始）
- Task 2 依赖 Task 1（需要重构后的runLAMMPS方法）
- Task 3 依赖 Task 2（需要三阶段执行完成后才能收集输出文件）
- Task 4 依赖无（可与Task 1并行）
- Task 5 依赖无（可与Task 1-4并行）
- Task 6 依赖 Task 2、Task 3、Task 4
- Task 7 依赖 Task 6
