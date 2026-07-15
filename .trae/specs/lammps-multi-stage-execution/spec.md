# LAMMPS多阶段模拟执行与性质轨迹输出 Spec

## Why

当前平台的LAMMPS执行层存在严重缺陷：`DockerService.runLAMMPS()`仅执行单条`lmp`命令，不支持minimization→equilibrium→production三阶段顺序执行；文件路径使用硬编码的`/workspace/inputs`和`/workspace/results`，不符合文件输入输出规则中定义的`user_{userId}/jobs/job_{jobId}/inputs/`和`raw_outputs/`目录结构；结果文件收集仅复制`log.lammps`和`traj.dump`，不根据target_properties收集性质专属轨迹文件（如`dump.charge.lammpstrj`、`pressure.dat`、`dipole.dat`、`dump.solvation.lammpstrj`等）。需要重构执行层，使LAMMPS能正确运行三阶段模拟并输出各性质所需的轨迹文件。

## What Changes

- **重构DockerService.runLAMMPS()**：支持指定工作目录和输出目录，支持单阶段执行（由上层编排多阶段）
- **重构MDExecutorService**：实现三阶段LAMMPS模拟编排（minimization→equilibrium→production），使用PathUtil生成所有路径
- **实现性质专属输出文件收集**：根据target_properties收集对应的输出文件到raw_outputs目录
- **更新SimulationRawOutput实体**：添加性质专属文件路径字段（chargeTrajectoryFilePath、solvationTrajectoryFilePath等）
- **创建Python阶段脚本**：在stages/目录下创建stage4_lammps_execution.py，封装LAMMPS执行逻辑
- **修复硬编码路径**：所有路径通过PathUtil生成，容器内路径与本地路径正确映射

## Impact

- Affected specs: jinja2-lammps-scripts-and-pdb-coords（Jinja2模板已生成的脚本将被正确执行）
- Affected code:
  - 修改：`backend/src/main/java/com/mdplatform/engine/service/DockerService.java`（重构runLAMMPS方法）
  - 修改：`backend/src/main/java/com/mdplatform/engine/service/MDExecutorService.java`（重构执行编排）
  - 修改：`backend/src/main/java/com/mdplatform/engine/model/SimulationRawOutput.java`（添加性质专属文件路径字段）
  - 新增：`backend/src/main/resources/scripts/modeling/stages/stage4_lammps_execution.py`（LAMMPS执行阶段脚本）
  - 修改：`backend/src/main/resources/scripts/modeling/stages/__init__.py`（导出新阶段）
  - 修改：`backend/src/main/resources/scripts/modeling/run_modeling.py`（添加--mode lammps-execution）

## ADDED Requirements

### Requirement: 三阶段LAMMPS模拟顺序执行

系统必须支持LAMMPS三阶段模拟的顺序执行：minimization→equilibrium→production，每个阶段的输出作为下一阶段的输入。

#### Scenario: 三阶段顺序执行

- **WHEN** 用户启动模拟任务
- **AND** inputs目录中存在in.minimization、in.equilibrium、in.production脚本和system.data文件
- **THEN** 系统按顺序执行三个阶段：
  1. 执行`lmp -in in.minimization`，输出minimized.data到工作目录
  2. 执行`lmp -in in.equilibrium`，读取minimized.data，输出equilibrated.data到工作目录
  3. 执行`lmp -in in.production`，读取equilibrated.data，输出final.data和性质专属文件到工作目录
- **AND** 每个阶段的log.lammps追加写入到raw_outputs/log.lammps
- **AND** 任何阶段失败时停止执行并标记任务为FAILED

#### Scenario: 阶段间数据传递

- **WHEN** minimization阶段完成
- **THEN** minimized.data文件存在于工作目录
- **AND** equilibrium脚本中的`read_data minimized.data`能正确读取
- **WHEN** equilibrium阶段完成
- **THEN** equilibrated.data文件存在于工作目录
- **AND** production脚本中的`read_data equilibrated.data`能正确读取

### Requirement: 性质专属轨迹文件输出

系统必须根据target_properties收集LAMMPS输出的性质专属轨迹文件，并将其存储到raw_outputs目录。

#### Scenario: density性质输出

- **WHEN** target_properties包含"density"
- **THEN** LAMMPS生产模拟输出包含thermo_style中的density列
- **AND** 收集log.lammps到raw_outputs目录

#### Scenario: conductivity性质输出

- **WHEN** target_properties包含"conductivity"
- **THEN** LAMMPS生产模拟输出dump.charge.lammpstrj和msd.dat
- **AND** 收集dump.charge.lammpstrj到raw_outputs目录
- **AND** 收集msd.dat到raw_outputs目录

#### Scenario: viscosity性质输出

- **WHEN** target_properties包含"viscosity"
- **THEN** LAMMPS生产模拟输出pressure.dat
- **AND** 收集pressure.dat到raw_outputs目录

#### Scenario: dielectric性质输出

- **WHEN** target_properties包含"dielectric"
- **THEN** LAMMPS生产模拟输出dipole.dat和total_dipole.dat
- **AND** 收集dipole.dat到raw_outputs目录
- **AND** 收集total_dipole.dat到raw_outputs目录

#### Scenario: solvation_structure性质输出

- **WHEN** target_properties包含"solvation_structure"
- **THEN** LAMMPS生产模拟输出dump.solvation.lammpstrj
- **AND** 收集dump.solvation.lammpstrj到raw_outputs目录

#### Scenario: 多性质组合输出

- **WHEN** target_properties包含["conductivity", "viscosity", "dielectric"]
- **THEN** 收集所有对应性质所需的输出文件：dump.charge.lammpstrj、msd.dat、pressure.dat、dipole.dat、total_dipole.dat
- **AND** 始终收集通用输出文件：log.lammps、dump.trajectory.lammpstrj、final.data

### Requirement: 文件路径遵循目录结构规则

系统必须使用PathUtil生成所有文件路径，容器内路径与本地路径正确映射。

#### Scenario: 容器内路径映射

- **WHEN** 执行LAMMPS模拟
- **THEN** 容器内工作目录为`/workspace/data/user_{userId}/jobs/job_{jobId}/inputs/`
- **AND** 容器内输出目录为`/workspace/data/user_{userId}/jobs/job_{jobId}/raw_outputs/`
- **AND** 本地路径`data/md_platform_data/user_{userId}/jobs/job_{jobId}/inputs/`与容器路径正确映射
- **AND** 本地路径`data/md_platform_data/user_{userId}/jobs/job_{jobId}/raw_outputs/`与容器路径正确映射

#### Scenario: 输出文件存储到raw_outputs目录

- **WHEN** LAMMPS模拟完成
- **THEN** 所有输出文件存储在`user_{userId}/jobs/job_{jobId}/raw_outputs/`目录下
- **AND** 文件命名遵循规范：log.lammps、dump.trajectory.lammpstrj、dump.charge.lammpstrj、pressure.dat等

### Requirement: SimulationRawOutput记录性质专属文件路径

系统必须在SimulationRawOutput实体中记录所有性质专属输出文件的路径。

#### Scenario: 记录性质专属文件路径

- **WHEN** LAMMPS模拟完成并收集输出文件后
- **THEN** SimulationRawOutput记录以下文件路径（相对路径）：
  - logFilePath → raw_outputs/log.lammps
  - trajectoryFilePath → raw_outputs/dump.trajectory.lammpstrj
  - chargeTrajectoryFilePath → raw_outputs/dump.charge.lammpstrj（仅conductivity/dielectric时）
  - solvationTrajectoryFilePath → raw_outputs/dump.solvation.lammpstrj（仅solvation_structure时）
  - pressureFilePath → raw_outputs/pressure.dat（仅viscosity时）
  - dipoleFilePath → raw_outputs/dipole.dat（仅dielectric时）
  - msdFilePath → raw_outputs/msd.dat（仅conductivity时）
  - finalDataFilePath → raw_outputs/final.data

### Requirement: Python阶段脚本stage4_lammps_execution.py

系统必须在stages/目录下创建stage4_lammps_execution.py，封装LAMMPS执行逻辑。

#### Scenario: 通过Python脚本执行LAMMPS

- **WHEN** Java端调用`python3 run_modeling.py --mode lammps-execution`
- **AND** 传入参数：--job-id、--user-id、--target-properties、--use-gpu
- **THEN** Python脚本执行三阶段LAMMPS模拟
- **AND** 收集性质专属输出文件到raw_outputs目录
- **AND** 返回JSON格式的执行结果

## MODIFIED Requirements

### Requirement: DockerService.runLAMMPS方法

DockerService.runLAMMPS方法必须支持指定工作目录和输出目录，不再使用硬编码路径。

#### Scenario: 使用PathUtil路径执行LAMMPS

- **WHEN** 调用runLAMMPS方法
- **THEN** 方法签名变更为`runLAMMPS(String inputFilename, String jobId, String containerWorkDir, String containerOutputDir)`
- **AND** LAMMPS在工作目录中执行
- **AND** log.lammps输出到输出目录
- **AND** 不再使用硬编码的`/workspace/inputs`和`/workspace/results`路径

### Requirement: MDExecutorService执行编排

MDExecutorService必须实现三阶段LAMMPS模拟编排，使用PathUtil生成所有路径。

#### Scenario: 三阶段编排执行

- **WHEN** 调用executeSimulation方法
- **THEN** 使用PathUtil生成inputs和raw_outputs路径
- **AND** 按顺序执行minimization→equilibrium→production三个阶段
- **AND** 每个阶段使用前一阶段的输出文件
- **AND** 根据target_properties收集性质专属输出文件
- **AND** 更新SimulationRawOutput记录所有文件路径

## REMOVED Requirements

无移除的需求。
