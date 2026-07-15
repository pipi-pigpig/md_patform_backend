# Tasks

- [x] 任务1：创建JobStatus常量类，统一任务状态管理
  - [x] 子任务1.1：在engine/model/目录下创建JobStatus.java常量类，定义PENDING、MODELING、RUNNING、POST_PROCESSING、POST_PROCESSING_COMPLETED、COMPLETED、FAILED、CANCELLED共8个常量
  - [x] 子任务1.2：替换MDExecutorService中所有硬编码状态字符串为JobStatus常量
  - [x] 子任务1.3：替换PostProcessingService中所有硬编码状态字符串为JobStatus常量
  - [x] 子任务1.4：替换PipelineService中所有硬编码状态字符串为JobStatus常量
  - [x] 子任务1.5：替换SimulationService中所有硬编码状态字符串为JobStatus常量
  - [x] 子任务1.6：替换其他服务类（如有）中的硬编码状态字符串为JobStatus常量

- [x] 任务2：验证并修复DockerService.runLAMMPS() GPU执行逻辑
  - [x] 子任务2.1：验证isGPUAvailable()方法通过docker-java在容器中执行nvidia-smi检测GPU的逻辑
  - [x] 子任务2.2：验证GPU模式命令`lmp -sf gpu -pk gpu 1 -in {path} -log {path} -screen none`的正确性
  - [x] 子任务2.3：验证CPU回退模式命令`lmp -in {path} -log {path} -screen none`的正确性
  - [x] 子任务2.4：验证executeCommandInContainer()的cwd参数设置为containerWorkDir而非/workspace
  - [x] 子任务2.5：添加GPU检测失败的详细日志，包含nvidia-smi命令输出

- [x] 任务3：简化Python stage4_lammps_execution.py，移除重复LAMMPS执行逻辑
  - [x] 子任务3.1：移除stage4_lammps_execution.py中通过subprocess调用lmp命令的代码
  - [x] 子任务3.2：保留文件收集功能（collect_property_outputs、move_outputs_to_raw_dir）
  - [x] 子任务3.3：保留日志合并功能（_merge_log_files）
  - [x] 子任务3.4：更新run_modeling.py中--mode lammps-execution的说明注释，标注该模式仅执行文件收集
  - [x] 子任务3.5：更新stages/__init__.py导出

- [x] 任务4：编写LammpsExecutionE2ETest端到端测试
  - [x] 子任务4.1：创建LammpsExecutionE2ETest.java，使用@SpringBootTest和真实Spring上下文
  - [x] 子任务4.2：实现环境检查测试方法：验证Docker可用、md_engine容器运行、GPU可用性检测
  - [x] 子任务4.3：实现LAMMPS三阶段执行测试：通过docker-java调用DockerService.runLAMMPS()执行minimization→equilibrium→production
  - [x] 子任务4.4：实现轨迹文件验证：检查raw_outputs目录下log.lammps、dump.trajectory.lammpstrj、final.data等文件存在
  - [x] 子任务4.5：实现性质专属文件验证：根据target_properties检查dump.charge.lammpstrj、pressure.dat等文件存在
  - [x] 子任务4.6：实现SimulationRawOutput数据库记录验证：检查文件路径字段正确填充
  - [x] 子任务4.7：所有测试使用docker-java操作容器，不使用mock

- [x] 任务5：编写PostProcessingE2ETest端到端测试
  - [x] 子任务5.1：创建PostProcessingE2ETest.java，使用@SpringBootTest和真实Spring上下文
  - [x] 子任务5.2：实现环境检查测试方法
  - [x] 子任务5.3：实现后处理执行测试：通过docker-java调用PostProcessingService.executePostProcessing()
  - [x] 子任务5.4：实现结果文件验证：检查post_processing目录下各性质_result.json文件存在且格式正确
  - [x] 子任务5.5：实现可视化数据验证：检查visualization/charts目录下_curve.json文件存在
  - [x] 子任务5.6：实现JSON结果内容验证：验证density均值在物理合理范围内、收敛状态字段存在
  - [x] 子任务5.7：所有测试使用docker-java操作容器，不使用mock

- [x] 任务6：编写LammpsToDatabaseE2ETest完整链路端到端测试
  - [x] 子任务6.1：创建LammpsToDatabaseE2ETest.java，使用@SpringBootTest和真实Spring上下文
  - [x] 子任务6.2：实现环境检查测试方法
  - [x] 子任务6.3：实现完整链路测试：提交全流程任务→等待LAMMPS执行完成→等待后处理完成→验证数据库
  - [x] 子任务6.4：实现数据库主表验证：查询calculation_result_table，验证记录数量与target_properties一致
  - [x] 子任务6.5：实现数据库子表验证：查询各子表，验证字段值与JSON结果文件一致
  - [x] 子任务6.6：实现状态流转验证：验证SimulationJob状态从PENDING最终变为COMPLETED
  - [x] 子任务6.7：实现文件路径验证：验证rawDataPath和chartDataPath为相对路径且文件实际存在
  - [x] 子任务6.8：所有测试使用docker-java操作容器和GPU，不使用mock

- [x] 任务7：验证与修复完整流水线集成
  - [x] 子任务7.1：运行完整流水线测试，修复发现的问题
  - [x] 子任务7.2：验证GPU模式下LAMMPS执行正常
  - [x] 子任务7.3：验证5种性质的后处理计算结果在物理合理范围内
  - [x] 子任务7.4：验证数据库主表和子表记录完整性
  - [x] 子任务7.5：Spring Boot编译成功

# Task Dependencies

- 任务1（JobStatus常量类）无依赖，可独立开始
- 任务2（DockerService GPU验证）无依赖，可与任务1并行
- 任务3（Python stage4简化）无依赖，可与任务1、2并行
- 任务4（LAMMPS E2E测试）依赖任务1、2（需要JobStatus常量和修复后的GPU逻辑）
- 任务5（后处理E2E测试）依赖任务1（需要JobStatus常量），可与任务4并行
- 任务6（完整链路E2E测试）依赖任务4、5（需要LAMMPS和后处理测试验证通过）
- 任务7（集成验证）依赖任务6
