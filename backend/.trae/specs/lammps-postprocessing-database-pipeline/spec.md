# LAMMPS模拟执行→轨迹生成→MDAnalysis后处理→数据库存储 流水线 Spec

## Why

前序流水线（分子计算→Packmol→system.lt→Moltemplate执行→文件整理）已完成，但后续的LAMMPS模拟执行、轨迹文件生成、MDAnalysis后处理分析、计算结果存库这一完整链路尚未经过Docker+GPU真实环境验证。当前代码中MDExecutorService、PostProcessingService、CalculationResultService虽已实现，但存在Java/Python LAMMPS执行逻辑重复、状态枚举不统一、缺少真实Docker+GPU的端到端测试等问题。需要完成该流水线的集成验证与问题修复，确保从LAMMPS执行到结果存库的完整链路在Docker容器+GPU环境下可靠运行。

## What Changes

- **统一LAMMPS执行入口**：移除Python stage4_lammps_execution.py中的重复LAMMPS执行逻辑，Java端MDExecutorService作为唯一LAMMPS执行入口，通过docker-java操作容器执行`lmp`命令
- **修复GPU执行命令**：确保DockerService.runLAMMPS()在GPU模式下使用正确的LAMMPS GPU命令（`lmp -sf gpu -pk gpu 1`），并验证容器内GPU可用性检测逻辑
- **统一任务状态枚举**：创建JobStatus常量类，统一所有状态字符串（PENDING/MODELING/RUNNING/POST_PROCESSING/POST_PROCESSING_COMPLETED/COMPLETED/FAILED/CANCELLED），消除硬编码字符串
- **验证轨迹文件生成与收集**：确保LAMMPS三阶段模拟执行后，性质专属轨迹文件（dump.trajectory.lammpstrj、dump.charge.lammpstrj、pressure.dat等）正确生成到inputs工作目录，并被收集到raw_outputs目录
- **验证MDAnalysis后处理**：确保PostProcessingService通过docker-java在容器中执行Python后处理脚本，正确读取raw_outputs中的轨迹文件，计算5种理化性质，输出结果JSON到post_processing目录
- **验证数据库存储**：确保PostProcessingService.parseAndStoreResults()正确解析JSON结果文件，通过CalculationResultService写入calculation_result_table主表和5个子表
- **编写Docker+GPU端到端集成测试**：使用docker-java操作md_engine容器执行真实LAMMPS模拟和后处理，验证完整流水线

## Impact

- Affected specs: lammps-multi-stage-execution（LAMMPS执行逻辑）、implement-mdanalysis-post-processing（后处理逻辑）
- Affected code:
  - 修改：`DockerService.java`（GPU命令验证与修复）
  - 修改：`MDExecutorService.java`（使用JobStatus常量替换硬编码字符串）
  - 修改：`PostProcessingService.java`（使用JobStatus常量替换硬编码字符串）
  - 修改：`PipelineService.java`（使用JobStatus常量替换硬编码字符串）
  - 修改：`SimulationService.java`（使用JobStatus常量替换硬编码字符串）
  - 新增：`JobStatus.java`（任务状态常量类）
  - 修改：`stage4_lammps_execution.py`（移除重复的LAMMPS执行逻辑，仅保留文件收集功能）
  - 新增：`LammpsExecutionE2ETest.java`（LAMMPS执行端到端测试）
  - 新增：`PostProcessingE2ETest.java`（后处理端到端测试）
  - 新增：`LammpsToDatabaseE2ETest.java`（LAMMPS→后处理→数据库完整链路测试）

## ADDED Requirements

### Requirement: JobStatus任务状态常量类

系统 SHALL 提供JobStatus常量类，统一管理所有任务状态字符串，消除代码中的硬编码状态值。

#### Scenario: 状态常量定义完整

- **WHEN** 查看JobStatus类
- **THEN** 包含以下常量：PENDING、MODELING、RUNNING、POST_PROCESSING、POST_PROCESSING_COMPLETED、COMPLETED、FAILED、CANCELLED
- **AND** 每个常量值为对应的字符串（如PENDING = "PENDING"）

#### Scenario: 所有服务使用JobStatus常量

- **WHEN** MDExecutorService、PostProcessingService、PipelineService、SimulationService中需要设置或比较任务状态
- **THEN** 使用JobStatus常量而非硬编码字符串
- **AND** 代码中不存在任务状态的硬编码字符串字面量

### Requirement: LAMMPS GPU执行验证与修复

系统 SHALL 确保DockerService.runLAMMPS()在GPU模式下正确执行LAMMPS命令，通过docker-java操作容器。

#### Scenario: GPU模式LAMMPS命令正确

- **WHEN** 容器内GPU可用（isGPUAvailable()返回true）
- **THEN** 执行命令为 `lmp -sf gpu -pk gpu 1 -in {inputPath} -log {logPath} -screen none`
- **AND** LAMMPS使用GPU加速执行模拟

#### Scenario: GPU不可用时回退到CPU

- **WHEN** 容器内GPU不可用（isGPUAvailable()返回false）
- **THEN** 执行命令为 `lmp -in {inputPath} -log {logPath} -screen none`
- **AND** 记录警告日志说明GPU不可用

#### Scenario: GPU可用性检测通过docker-java实现

- **WHEN** 调用isGPUAvailable()方法
- **THEN** 通过docker-java在md_engine容器中执行`nvidia-smi`命令检测GPU
- **AND** 命令执行成功且退出码为0时返回true
- **AND** 命令执行失败或抛出异常时返回false

### Requirement: LAMMPS三阶段模拟执行端到端验证

系统 SHALL 通过docker-java在md_engine容器中执行LAMMPS三阶段模拟，验证轨迹文件正确生成。

#### Scenario: 三阶段顺序执行成功

- **WHEN** inputs目录中存在in.minimization、in.equilibrium、in.production脚本和system.data文件
- **AND** 通过docker-java调用DockerService.runLAMMPS()执行三阶段
- **THEN** 阶段1输出minimized.data到inputs目录
- **AND** 阶段2输出equilibrated.data到inputs目录
- **AND** 阶段3输出final.data和性质专属文件到inputs目录

#### Scenario: 通用轨迹文件生成

- **WHEN** LAMMPS生产模拟完成
- **THEN** raw_outputs目录包含log.lammps（三阶段合并日志）
- **AND** raw_outputs目录包含dump.trajectory.lammpstrj（原子轨迹文件）
- **AND** raw_outputs目录包含final.data（最终构型文件）

#### Scenario: 性质专属轨迹文件生成

- **WHEN** target_properties包含"conductivity"
- **THEN** raw_outputs目录包含dump.charge.lammpstrj和msd.dat
- **WHEN** target_properties包含"viscosity"
- **THEN** raw_outputs目录包含pressure.dat
- **WHEN** target_properties包含"dielectric"
- **THEN** raw_outputs目录包含dipole.dat和total_dipole.dat
- **WHEN** target_properties包含"solvation_structure"
- **THEN** raw_outputs目录包含dump.solvation.lammpstrj

#### Scenario: SimulationRawOutput记录更新

- **WHEN** LAMMPS模拟完成并收集输出文件后
- **THEN** SimulationRawOutput记录包含所有已收集文件的相对路径
- **AND** totalFrames和totalSimulationTimeNs字段被正确填充

### Requirement: MDAnalysis后处理端到端验证

系统 SHALL 通过docker-java在md_engine容器中执行Python后处理脚本，验证5种理化性质的计算结果。

#### Scenario: 后处理脚本执行成功

- **WHEN** LAMMPS模拟完成且raw_outputs目录包含必要的轨迹文件
- **AND** 通过docker-java调用PostProcessingService.executePostProcessing()
- **THEN** Python后处理脚本在容器中成功执行
- **AND** post_processing目录包含各性质的_result.json文件
- **AND** visualization/charts目录包含各性质的_curve.json文件

#### Scenario: 密度计算结果正确

- **WHEN** target_properties包含"density"
- **THEN** post_processing/density_result.json包含density均值(g/cm³)、标准差、标准误差、收敛状态
- **AND** 结果值在物理合理范围内（电解液密度通常0.9-1.5 g/cm³）

#### Scenario: 电导率计算结果正确

- **WHEN** target_properties包含"conductivity"
- **THEN** post_processing/conductivity_result.json包含电导率值(S/m)、电导率张量、离子贡献、收敛状态

#### Scenario: 粘度计算结果正确

- **WHEN** target_properties包含"viscosity"
- **THEN** post_processing/viscosity_result.json包含粘度值(mPa·s)、运动粘度、收敛状态

#### Scenario: 介电常数计算结果正确

- **WHEN** target_properties包含"dielectric"
- **THEN** post_processing/dielectric_constant_result.json包含介电常数、介电张量、偶极矩数据、收敛状态

#### Scenario: 溶剂化结构计算结果正确

- **WHEN** target_properties包含"solvation_structure"
- **THEN** post_processing/solvation_structure_result.json包含RDF曲线数据、配位数、配位距离、特征峰

### Requirement: 计算结果数据库存储验证

系统 SHALL 将后处理计算结果正确写入calculation_result_table主表和对应子表。

#### Scenario: 主表记录创建

- **WHEN** 后处理完成且parseAndStoreResults()被调用
- **THEN** calculation_result_table中为每个target_property创建一条记录
- **AND** 每条记录包含：jobId、propertyName、propertyValue、propertyUnit、calculationMethod、temperatureK、convergenceStatus、propertyDetail(JSON)
- **AND** rawDataPath指向post_processing/{property}_result.json（相对路径）
- **AND** chartDataPath指向visualization/charts/{type}_curve.json（相对路径）

#### Scenario: 子表记录创建

- **WHEN** 主表记录创建后
- **THEN** 根据propertyName创建对应的子表记录：
  - density → density_result_table（densityTensor、componentDensity）
  - conductivity → conductivity_result_table（conductivityTensor、ionContribution、resistivity）
  - viscosity → viscosity_result_table（viscosityValue、shearRate、stressResponse、kinematicViscosity）
  - dielectric → dielectric_result_table（dielectricConstantTensor、staticDielectricConstant、dipoleMomentData、componentContribution）
  - solvation → solvation_result_table（centralIonType、averageCoordinationNumber、coordinationDistance、rdfCharacteristicPeak）

#### Scenario: 性质名称映射正确

- **WHEN** Python输出文件名为dielectric_constant_result.json
- **THEN** Java propertyName映射为"dielectric"
- **WHEN** Python输出文件名为solvation_structure_result.json
- **THEN** Java propertyName映射为"solvation"

### Requirement: Docker+GPU端到端集成测试

系统 SHALL 提供使用docker-java操作容器、使用GPU计算的端到端集成测试，不使用mock。

#### Scenario: LAMMPS执行端到端测试

- **WHEN** 运行LammpsExecutionE2ETest
- **THEN** 测试通过docker-java在md_engine容器中执行LAMMPS三阶段模拟
- **AND** 验证GPU可用性检测
- **AND** 验证minimized.data、equilibrated.data、final.data文件生成
- **AND** 验证性质专属轨迹文件生成到raw_outputs目录
- **AND** 验证SimulationRawOutput数据库记录正确

#### Scenario: 后处理端到端测试

- **WHEN** 运行PostProcessingE2ETest
- **THEN** 测试通过docker-java在md_engine容器中执行Python后处理脚本
- **AND** 验证post_processing目录下JSON结果文件生成
- **AND** 验证visualization/charts目录下图表数据文件生成
- **AND** 验证JSON结果格式符合规范

#### Scenario: 完整链路端到端测试

- **WHEN** 运行LammpsToDatabaseE2ETest
- **THEN** 测试覆盖LAMMPS执行→轨迹生成→后处理→数据库存储的完整链路
- **AND** 验证calculation_result_table主表记录数量与target_properties数量一致
- **AND** 验证每个主表记录对应的子表记录存在且字段正确
- **AND** 验证SimulationJob状态流转：PENDING→MODELING→RUNNING→POST_PROCESSING→COMPLETED

#### Scenario: 测试前置条件检查

- **WHEN** Docker不可用或md_engine容器未运行
- **THEN** 测试被跳过（使用Assumptions.assumeTrue），不报错
- **AND** 日志输出明确的跳过原因

### Requirement: Python stage4脚本简化

系统 SHALL 简化stage4_lammps_execution.py，移除与Java端重复的LAMMPS执行逻辑，仅保留文件收集功能供独立模式使用。

#### Scenario: stage4仅保留文件收集功能

- **WHEN** 查看stage4_lammps_execution.py
- **THEN** 不包含直接调用`lmp`或`packmol`等外部工具的subprocess逻辑
- **AND** 仅包含文件移动、日志合并、输出文件收集等辅助功能
- **AND** run_modeling.py的--mode lammps-execution仍然可用，但仅执行文件收集操作

## MODIFIED Requirements

### Requirement: DockerService.runLAMMPS GPU命令

DockerService.runLAMMPS()在GPU模式下的命令 SHALL 使用`lmp -sf gpu -pk gpu 1`参数，确保GPU加速正确启用。工作目录（cwd）参数 SHALL 设置为containerWorkDir而非/workspace，确保LAMMPS在正确的目录下执行。

### Requirement: SimulationJob状态枚举

SimulationJob的status字段 SHALL 支持POST_PROCESSING_COMPLETED状态，该状态表示后处理已完成但全流程尚未结束（PipelineService会在后处理完成后将状态更新为COMPLETED）。

## REMOVED Requirements

无
