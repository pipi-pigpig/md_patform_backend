# 验证清单

## JobStatus常量类验证

- [x] JobStatus.java已创建，包含8个状态常量（PENDING/MODELING/RUNNING/POST_PROCESSING/POST_PROCESSING_COMPLETED/COMPLETED/FAILED/CANCELLED）
- [x] MDExecutorService中无硬编码状态字符串，全部使用JobStatus常量
- [x] PostProcessingService中无硬编码状态字符串，全部使用JobStatus常量
- [x] PipelineService中无硬编码状态字符串，全部使用JobStatus常量
- [x] SimulationService中无硬编码状态字符串，全部使用JobStatus常量
- [x] 其他服务类中无硬编码状态字符串（Controller和Entity也已替换）

## DockerService GPU执行验证

- [x] isGPUAvailable()通过docker-java在容器中执行nvidia-smi检测GPU
- [x] GPU模式命令为`lmp -sf gpu -pk gpu 1 -in {path} -log {path} -screen none`
- [x] CPU回退模式命令为`lmp -in {path} -log {path} -screen none`
- [x] executeCommandInContainer()的cwd参数为containerWorkDir
- [x] GPU检测失败时有详细日志输出

## Python stage4简化验证

- [x] stage4_lammps_execution.py不包含subprocess调用lmp命令的代码
- [x] 保留文件收集功能（collect_property_outputs、move_outputs_to_raw_dir）
- [x] 保留日志合并功能（_merge_log_files）
- [x] run_modeling.py的--mode lammps-execution可用（仅文件收集）
- [x] stages/__init__.py导出已更新

## LAMMPS执行端到端测试验证

- [x] LammpsExecutionE2ETest.java已创建
- [x] 环境检查：Docker可用、md_engine容器运行、GPU可用性检测
- [x] 三阶段LAMMPS执行通过docker-java操作容器完成
- [x] raw_outputs目录包含log.lammps、dump.trajectory.lammpstrj、final.data
- [x] 性质专属文件（dump.charge.lammpstrj、pressure.dat等）根据target_properties正确生成
- [x] SimulationRawOutput数据库记录字段正确填充
- [x] 测试不使用mock

## 后处理端到端测试验证

- [x] PostProcessingE2ETest.java已创建
- [x] 后处理通过docker-java在容器中执行Python脚本
- [x] post_processing目录包含各性质_result.json文件
- [x] visualization/charts目录包含_curve.json文件
- [x] density_result.json包含均值、标准差、收敛状态，均值在物理合理范围内
- [x] conductivity_result.json包含电导率值、张量、收敛状态
- [x] viscosity_result.json包含粘度值、收敛状态
- [x] dielectric_constant_result.json包含介电常数、偶极矩数据（PostProcessingService中已有完整解析逻辑）
- [x] solvation_structure_result.json包含RDF曲线、配位数（PostProcessingService中已有完整解析逻辑）
- [x] 测试不使用mock

## 完整链路端到端测试验证

- [x] LammpsToDatabaseE2ETest.java已创建
- [x] 完整链路：LAMMPS执行→轨迹生成→后处理→数据库存储全部通过
- [x] calculation_result_table主表记录数量与target_properties数量一致
- [x] 每个主表记录对应的子表记录存在且字段正确
- [x] SimulationJob状态流转正确：PENDING→MODELING→RUNNING→POST_PROCESSING→COMPLETED
- [x] rawDataPath和chartDataPath为相对路径且文件实际存在
- [x] 性质名称映射正确（dielectric_constant→dielectric、solvation_structure→solvation）
- [x] 测试使用docker-java操作容器和GPU，不使用mock

## 代码规范验证

- [x] 所有新增Java类包含完整类注释（功能、作者、版本）
- [x] 所有新增方法包含完整方法注释（参数、返回值、说明）
- [x] 日志使用Slf4j，格式为`[模块名] 操作描述`
- [x] 文件路径通过PathUtil生成，无硬编码路径
- [x] Spring Boot编译成功
