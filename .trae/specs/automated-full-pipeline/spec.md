# 全流程自动化审查与串联规范

## Why

当前平台各计算流程（moltemplate-steps1-2、moltemplate-execution-steps6-7、jinja2-lammps-scripts-and-pdb-coords、implement-mdanalysis-post-processing）已独立实现并通过验证，但存在以下关键问题：

1. **流程未串联**：各阶段需要手动分步触发，没有从用户提交任务到最终获得计算结果的自动化流水线
2. **缺少统一入口**：没有单一API端点能触发"配方提交→建模→模拟→后处理→结果存储"的完整流程
3. **后处理未集成**：PostProcessingService未接入MoltemplateService.executeFullModeling()，LAMMPS完成后不会自动触发后处理
4. **状态追踪不完整**：SimulationJob缺少MODELING状态，无法区分任务处于建模阶段还是模拟阶段
5. **进度不可见**：前端无法获知当前执行到哪个步骤，无法实时追踪进度
6. **参数传递断裂**：target_properties、temperature等参数在各服务间传递不一致，SimulationInput记录未自动创建

## What Changes

- **创建全流程编排服务PipelineService**：统一编排步骤0-9（配方序列化→分子数量计算→盒子尺寸计算→Packmol堆积→system.lt生成→Moltemplate执行→文件整理→Jinja2脚本生成→LAMMPS模拟→后处理分析），实现一键提交、自动执行
- **创建全流程API端点**：提供`POST /api/pipeline/submit`一键提交接口和`GET /api/pipeline/status/{jobId}`进度查询接口
- **扩展任务状态枚举**：新增MODELING状态，完善状态流转PENDING→MODELING→RUNNING→POST_PROCESSING→COMPLETED
- **集成后处理到全流程**：LAMMPS模拟完成后自动触发后处理，无需手动调用
- **实现步骤级进度追踪**：在PipelineService中记录当前执行步骤，通过API返回进度信息
- **自动创建SimulationInput记录**：在建模流程开始时根据配方参数自动创建SimulationInput
- **统一参数传递机制**：确保target_properties、temperature等参数在所有服务间一致传递

## Impact

- Affected specs:
  - moltemplate-steps1-2（步骤1-2的Java端调用需完善）
  - moltemplate-execution-steps6-7（步骤6-7已集成到MoltemplateService）
  - jinja2-lammps-scripts-and-pdb-coords（步骤7.5已集成到MoltemplateService）
  - lammps-multi-stage-execution（步骤8已集成到MDExecutorService）
  - implement-mdanalysis-post-processing（步骤9需集成到全流程）
- Affected code:
  - 新增：`engine/service/PipelineService.java`（全流程编排服务）
  - 新增：`engine/controller/PipelineController.java`（全流程API端点）
  - 新增：`engine/dto/PipelineProgressDto.java`（进度信息DTO）
  - 修改：`engine/model/SimulationJob.java`（新增MODELING状态支持）
  - 修改：`engine/service/MoltemplateService.java`（完善步骤1-2的Java端实现，集成后处理触发）
  - 修改：`engine/service/MDExecutorService.java`（LAMMPS完成后触发后处理）
  - 修改：`engine/service/PostProcessingService.java`（适配自动触发模式）
  - 修改：`engine/service/SimulationService.java`（支持MODELING状态）

## ADDED Requirements

### Requirement: 全流程编排服务PipelineService

系统 SHALL 提供PipelineService，统一编排从配方提交到最终结果输出的完整计算流程。

#### Scenario: 一键提交完整计算任务

- **WHEN** 用户通过API提交计算任务，包含配方参数和目标性质
- **THEN** PipelineService自动按顺序执行以下步骤：
  1. 创建SimulationJob记录（状态=PENDING）
  2. 创建任务目录结构
  3. 序列化配方到JSON文件（步骤0）
  4. 计算分子数量和盒子尺寸（步骤1-2）
  5. 执行Packmol分子堆积（步骤3）
  6. 生成system.lt系统文件（步骤4-5）
  7. 执行Moltemplate命令（步骤6）
  8. 整理LAMMPS输入文件（步骤7）
  9. 生成Jinja2 LAMMPS输入脚本（步骤7.5）
  10. 执行LAMMPS三阶段模拟（步骤8）
  11. 执行后处理分析（步骤9）
  12. 更新任务状态为COMPLETED
- **AND** 任何步骤失败时停止执行，标记任务为FAILED，记录错误信息
- **AND** 每个步骤开始前更新任务状态和当前步骤信息

#### Scenario: 步骤失败时记录详细错误

- **WHEN** 某步骤执行失败
- **THEN** 任务状态更新为FAILED
- **AND** errorMessage字段记录失败步骤名称和具体错误信息
- **AND** resultSummary字段记录已成功完成的步骤列表
- **AND** 保留已生成的文件，不删除

#### Scenario: 异步执行不阻塞API响应

- **WHEN** 用户提交计算任务
- **THEN** API立即返回任务ID和状态（PENDING→MODELING）
- **AND** 计算流程在后台线程中异步执行
- **AND** 用户可通过状态查询API获取实时进度

### Requirement: 全流程API端点

系统 SHALL 提供全流程API端点，支持一键提交和进度查询。

#### Scenario: 一键提交计算任务

- **WHEN** 用户调用`POST /api/pipeline/submit`
- **AND** 请求体包含：配方参数（FormulaRequest）、目标性质（targetProperties）、温度（temperature）、硬件配置
- **THEN** 系统创建SimulationJob和SimulationInput记录
- **AND** 自动创建任务目录结构
- **AND** 异步启动全流程执行
- **AND** 返回202 Accepted，包含jobId和初始状态

#### Scenario: 查询任务进度

- **WHEN** 用户调用`GET /api/pipeline/status/{jobId}`
- **THEN** 返回PipelineProgressDto，包含：
  - jobId、userId
  - 当前状态（PENDING/MODELING/RUNNING/POST_PROCESSING/COMPLETED/FAILED）
  - 当前步骤编号和名称（如"步骤3: Packmol分子堆积"）
  - 已完成步骤列表
  - 总步骤数（11步）
  - 进度百分比
  - 错误信息（如有）
  - 开始时间、预计剩余时间

#### Scenario: 查询任务最终结果

- **WHEN** 用户调用`GET /api/pipeline/results/{jobId}`
- **AND** 任务状态为COMPLETED
- **THEN** 返回完整的计算结果，包含：
  - 建模结果（分子数量、盒子尺寸）
  - 模拟结果（热力学数据摘要）
  - 后处理结果（各性质的计算值和收敛状态）
  - 文件路径列表

### Requirement: 扩展任务状态枚举

SimulationJob的状态 SHALL 支持完整的流程状态流转。

#### Scenario: 完整状态流转

- **WHEN** 任务经历完整流程
- **THEN** 状态按以下顺序流转：
  - PENDING（任务创建）
  - MODELING（步骤0-7.5：配方序列化→文件整理→Jinja2脚本生成）
  - RUNNING（步骤8：LAMMPS三阶段模拟）
  - POST_PROCESSING（步骤9：后处理分析）
  - COMPLETED（全部完成）
- **AND** 任何阶段失败时状态变为FAILED
- **AND** 用户可取消任务，状态变为CANCELLED

#### Scenario: 步骤级进度记录

- **WHEN** PipelineService执行每个步骤
- **THEN** 在SimulationJob的resultSummary字段中记录当前步骤信息
- **AND** 格式为JSON：`{"currentStep": 3, "stepName": "Packmol分子堆积", "completedSteps": [0,1,2], "totalSteps": 11}`

### Requirement: 后处理自动触发

LAMMPS模拟完成后 SHALL 自动触发后处理分析，无需手动调用。

#### Scenario: LAMMPS完成后自动触发后处理

- **WHEN** LAMMPS三阶段模拟成功完成
- **AND** 任务配置了target_properties
- **THEN** 自动调用PostProcessingService.executePostProcessing()
- **AND** 任务状态从RUNNING更新为POST_PROCESSING
- **AND** 后处理完成后状态更新为COMPLETED

#### Scenario: 无目标性质时跳过后处理

- **WHEN** LAMMPS模拟完成
- **AND** target_properties为空或仅包含"density"（从log.lammps中直接获取）
- **THEN** 仍然执行后处理（至少计算密度）
- **AND** 后处理完成后状态更新为COMPLETED

#### Scenario: 后处理失败不影响模拟结果

- **WHEN** 后处理执行失败
- **THEN** 任务状态更新为FAILED
- **AND** errorMessage记录后处理失败原因
- **AND** LAMMPS模拟输出文件保留不删除
- **AND** 已成功计算的性质结果保留

### Requirement: SimulationInput自动创建

在建模流程开始时 SHALL 自动创建SimulationInput记录，确保后续服务可获取模拟参数。

#### Scenario: 从配方参数创建SimulationInput

- **WHEN** PipelineService开始执行建模流程
- **THEN** 根据FormulaRequest中的参数创建SimulationInput记录
- **AND** 包含温度、压力、时间步长、截断距离等参数
- **AND** 包含target_properties
- **AND** 关联到对应的jobId
- **AND** LammpsTemplateService可从数据库读取这些参数

#### Scenario: 使用默认参数填充缺失值

- **WHEN** FormulaRequest中未指定某些模拟参数
- **THEN** 使用默认值填充：
  - temperature: 300.0 K
  - pressure: 1.0 bar
  - timeStepFs: 1.0 fs
  - cutoffDistanceAng: 12.0 Å
  - ensembleType: NPT
  - thermostatType: Nose-Hoover
  - barostatType: Parrinello-Rahman

### Requirement: 统一参数传递机制

target_properties和temperature等关键参数 SHALL 在所有服务间一致传递，避免信息丢失。

#### Scenario: target_properties贯穿全流程

- **WHEN** 用户在提交任务时指定target_properties=["density","conductivity"]
- **THEN** 该参数在以下环节中被正确使用：
  - LammpsTemplateService：生成包含对应计算命令的LAMMPS脚本
  - MDExecutorService：收集对应性质的输出文件
  - PostProcessingService：执行对应性质的后处理计算
- **AND** 任何环节不丢失或篡改target_properties

#### Scenario: temperature贯穿全流程

- **WHEN** 用户指定temperature=353.0 K
- **THEN** 该参数在以下环节中被正确使用：
  - MoltemplateService：序列化到配方JSON
  - LammpsTemplateService：写入LAMMPS脚本中的temp变量
  - PostProcessingService：用于Green-Kubo公式计算
- **AND** 任何环节不使用默认值覆盖用户指定值

## MODIFIED Requirements

### Requirement: MoltemplateService.executeFullModeling()

executeFullModeling()方法 SHALL 集成后处理触发，实现步骤0-9的完整编排。

#### Scenario: 完整流程包含后处理

- **WHEN** 调用executeFullModeling()
- **THEN** 在步骤8（LAMMPS模拟）成功完成后
- **AND** 自动调用PostProcessingService.executePostProcessing()
- **AND** 实现步骤0-9的完整编排

#### Scenario: 步骤1-2的Java端实现

- **WHEN** 执行步骤1-2时
- **THEN** 调用Python脚本的molecule-count模式计算分子数量
- **AND** 调用Python脚本的box-size模式计算盒子尺寸
- **AND** 将计算结果存入SimulationInput记录

### Requirement: MDExecutorService.executeSimulation()

executeSimulation()方法 SHALL 在LAMMPS模拟完成后支持回调触发后处理。

#### Scenario: 模拟完成后触发回调

- **WHEN** LAMMPS三阶段模拟成功完成
- **THEN** 调用注册的回调函数（如有）
- **AND** PipelineService通过回调机制触发后处理

### Requirement: SimulationService状态更新

SimulationService SHALL 支持MODELING状态。

#### Scenario: MODELING状态的时间记录

- **WHEN** 任务状态更新为MODELING
- **THEN** 记录startTime
- **AND** 当状态从MODELING变为RUNNING时，不重置startTime

## REMOVED Requirements

无移除的需求。

## 审查发现的问题清单

### 问题1：MoltemplateService步骤1-2标记为TODO

**位置**：MoltemplateService.java executeFullModeling()方法
**现状**：步骤1（分子数量计算）和步骤2（盒子尺寸计算）标记为TODO，暂使用配方文件中的数据
**影响**：Java端未调用Python计算逻辑，依赖配方中预计算的数据
**修复**：通过DockerService在容器中执行Python脚本的molecule-count和box-size模式

### 问题2：PostProcessingService未集成到全流程

**位置**：MoltemplateService.java executeFullModeling()方法
**现状**：步骤8（LAMMPS模拟）完成后直接标记COMPLETED，不触发后处理
**影响**：用户需手动调用`POST /api/post-processing/job/{jobId}`触发后处理
**修复**：在executeFullModeling()中步骤8完成后自动调用PostProcessingService

### 问题3：SimulationInput记录未自动创建

**位置**：MoltemplateService.java
**现状**：LammpsTemplateService.generateInputScripts()依赖SimulationInput记录，但该记录未在流程中自动创建
**影响**：LammpsTemplateService可能获取不到模拟参数，回退到默认值
**修复**：在PipelineService开始时根据FormulaRequest创建SimulationInput记录

### 问题4：缺少统一的全流程API端点

**位置**：MoltemplateController.java、SimulationController.java、PostProcessingController.java
**现状**：三个控制器各自独立，前端需分步调用多个API
**影响**：前端实现复杂，无法一键提交完整计算任务
**修复**：创建PipelineController，提供submit和status端点

### 问题5：SimulationJob缺少MODELING状态

**位置**：SimulationJob.java status字段
**现状**：状态枚举为PENDING/RUNNING/COMPLETED/POST_PROCESSING/FAILED/CANCELLED
**影响**：无法区分任务处于建模阶段还是模拟阶段
**修复**：新增MODELING状态，在步骤0-7.5期间使用

### 问题6：步骤级进度不可追踪

**位置**：MoltemplateService.java
**现状**：executeFullModeling()只更新整体状态，不记录当前步骤
**影响**：前端无法显示"正在执行步骤3: Packmol分子堆积"等进度信息
**修复**：在resultSummary中记录步骤级进度信息

### 问题7：MDExecutorService降级执行模式存在风险

**位置**：MDExecutorService.java executeFallbackSimulation()
**现状**：当Docker不可用时使用降级模式，模拟5秒后返回假结果
**影响**：可能误导用户以为计算已完成
**修复**：降级模式应标记任务为FAILED并提示Docker不可用，而非返回假结果

### 问题8：PackmolService通过不存在的Python脚本执行

**位置**：PackmolService.java DOCKER_SCRIPT_PATH
**现状**：引用`/workspace/scripts/modeling/run_packmol.py`，但该文件不存在
**影响**：executePackmol()方法可能执行失败
**修复**：改用executePackmolDirect()或使用正确的脚本路径run_modeling.py --mode packmol

### 问题9：DockerService.runLAMMPS()日志输出到单一文件

**位置**：DockerService.java runLAMMPS()
**现状**：三个阶段都使用`-log containerOutputDir + "/log.lammps"`，会覆盖前一阶段的日志
**影响**：minimization和equilibrium的日志被production覆盖
**修复**：每个阶段使用不同的日志文件名，或在MDExecutorService中合并日志

### 问题10：target_properties参数传递不一致

**位置**：多个服务间
**现状**：MoltemplateService从FormulaRequest获取，LammpsTemplateService从SimulationJob获取，MDExecutorService从SimulationJob获取，PostProcessingService从SimulationJob获取
**影响**：如果SimulationJob的targetProperties未正确设置，后续环节可能出错
**修复**：在PipelineService中统一设置SimulationJob.targetProperties，确保所有服务读取同一来源
