# 验证清单

## DockerService重构验证

- [x] DockerService.runLAMMPS()方法签名包含containerWorkDir和containerOutputDir参数
- [x] LAMMPS命令使用传入的工作目录路径，不再使用硬编码的/workspace/inputs
- [x] log.lammps输出到指定的输出目录，不再使用硬编码的/workspace/results
- [x] GPU/CPU模式选择逻辑正常工作
- [x] 执行日志记录完整（包含容器路径、命令、退出码）

## MDExecutorService重构验证

- [x] executeSimulation方法使用PathUtil生成所有路径
- [x] 三阶段LAMMPS模拟按minimization→equilibrium→production顺序执行
- [x] minimization阶段输出minimized.data，equilibrium阶段能正确读取
- [x] equilibrium阶段输出equilibrated.data，production阶段能正确读取
- [x] 任何阶段失败时停止执行并标记任务为FAILED
- [x] 代码中不存在硬编码路径（data/inputs、data/results等）

## 性质专属输出文件收集验证

- [x] target_properties包含density时，收集log.lammps和dump.trajectory.lammpstrj
- [x] target_properties包含conductivity时，收集dump.charge.lammpstrj和msd.dat
- [x] target_properties包含viscosity时，收集pressure.dat
- [x] target_properties包含dielectric时，收集dipole.dat和total_dipole.dat
- [x] target_properties包含solvation_structure时，收集dump.solvation.lammpstrj
- [x] 多性质组合时，收集所有对应性质的输出文件
- [x] 通用输出文件（log.lammps、dump.trajectory.lammpstrj、final.data）始终被收集
- [x] 输出文件存储在raw_outputs目录下，文件命名符合规范

## SimulationRawOutput实体验证

- [x] SimulationRawOutput包含chargeTrajectoryFilePath字段
- [x] SimulationRawOutput包含solvationTrajectoryFilePath字段
- [x] SimulationRawOutput包含pressureFilePath字段
- [x] SimulationRawOutput包含dipoleFilePath字段
- [x] SimulationRawOutput包含msdFilePath字段
- [x] SimulationRawOutput包含finalDataFilePath字段
- [x] 所有文件路径存储为相对路径（相对于任务根目录）
- [x] 数据库DDL脚本已更新

## Python阶段脚本验证

- [x] stages/stage4_lammps_execution.py文件已创建
- [x] execute_lammps_stages()函数实现三阶段执行逻辑
- [x] collect_property_outputs()函数根据target_properties收集输出文件
- [x] move_outputs_to_raw_dir()函数将输出文件移动到raw_outputs目录
- [x] run_modeling.py支持--mode lammps-execution入口
- [x] stages/__init__.py导出新阶段

## 集成验证

- [x] MoltemplateService.executeFullModeling()包含步骤8（LAMMPS执行）
- [x] 步骤8在步骤7.5之后执行
- [x] 步骤8调用MDExecutorService执行三阶段模拟
- [x] 步骤8收集输出文件并更新SimulationRawOutput记录
- [x] FullModelingResult DTO包含LAMMPS执行结果字段
- [x] Spring Boot编译成功
