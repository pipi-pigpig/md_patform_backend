# 端到端流水线测试 Spec

## Why

当前平台的全流程自动化（PipelineService）代码已实现，但缺少端到端集成测试验证。Task 13（功能验证）仍未完成，无法确认：提交任务后流水线是否正确执行、状态是否正确流转、计算结果是否正确写入数据库。需要创建真实的端到端测试（不使用Mock），覆盖从任务提交到结果验证的完整链路。

## What Changes

- **创建端到端集成测试类PipelineE2ETest**：使用Spring Boot测试上下文 + H2内存数据库 + 真实Docker容器，验证完整流水线
- **创建数据库结果验证测试类DatabaseResultVerificationTest**：验证SimulationJob状态流转、CalculationResult记录写入、SimulationInput参数持久化
- **创建流水线状态追踪测试类PipelineStatusTrackingTest**：验证步骤级进度更新、状态流转正确性、错误处理
- **更新测试配置**：确保application-test.yml支持端到端测试场景

## Impact

- Affected specs: automated-full-pipeline（Task 13功能验证）
- Affected code:
  - 新增：`src/test/java/com/mdplatform/engine/PipelineE2ETest.java`
  - 新增：`src/test/java/com/mdplatform/engine/DatabaseResultVerificationTest.java`
  - 新增：`src/test/java/com/mdplatform/engine/PipelineStatusTrackingTest.java`
  - 可能修改：`src/test/resources/application-test.yml`

## ADDED Requirements

### Requirement: 端到端流水线集成测试

系统 SHALL 提供PipelineE2ETest，验证从任务提交到结果获取的完整流程。

#### Scenario: 提交任务并获取初始状态

- **WHEN** 调用`POST /api/pipeline/submit`提交一个包含标准电解液配方（EC:DMC=3:7, 1M LiPF6）的计算任务
- **THEN** 返回202 Accepted，包含jobId
- **AND** 数据库中存在对应的SimulationJob记录，状态为PENDING或MODELING
- **AND** 数据库中存在对应的SimulationInput记录，参数与提交请求一致

#### Scenario: 跟踪流水线执行状态

- **WHEN** 任务提交后，轮询`GET /api/pipeline/status/{jobId}`
- **THEN** 返回PipelineProgressDto，包含当前步骤编号、步骤名称、已完成步骤列表
- **AND** 状态按PENDING→MODELING→RUNNING→POST_PROCESSING→COMPLETED流转
- **AND** 每个状态转换都能通过API查询到

#### Scenario: 验证最终计算结果写入数据库

- **WHEN** 任务状态变为COMPLETED
- **THEN** 数据库CalculationResult表中存在该jobId的记录
- **AND** 每个target_property对应一条CalculationResult记录
- **AND** CalculationResult的propertyValue不为null
- **AND** CalculationResult的convergenceStatus有值
- **AND** 通过`GET /api/pipeline/results/{jobId}`能获取到结果

#### Scenario: Docker不可用时任务标记为FAILED

- **WHEN** md-engine容器不可用
- **AND** 提交计算任务
- **THEN** 任务最终状态为FAILED
- **AND** errorMessage包含Docker相关错误信息
- **AND** SimulationJob记录保留，不删除

### Requirement: 数据库结果验证测试

系统 SHALL 提供DatabaseResultVerificationTest，验证计算结果正确写入数据库。

#### Scenario: SimulationJob状态流转验证

- **WHEN** 任务经历完整流程
- **THEN** SimulationJob.status字段按PENDING→MODELING→RUNNING→POST_PROCESSING→COMPLETED顺序更新
- **AND** startTime在MODELING状态时设置
- **AND** endTime在COMPLETED状态时设置
- **AND** executionTimeS为正数

#### Scenario: CalculationResult记录完整性验证

- **WHEN** 后处理完成并存储结果
- **THEN** CalculationResult表中存在对应记录
- **AND** propertyName与target_properties一致
- **AND** propertyValue为有效数值（非null、非NaN）
- **AND** temperatureK与提交参数一致
- **AND** rawDataPath指向有效的相对路径
- **AND** 子表（DensityResult等）中存在对应的详细数据

#### Scenario: SimulationInput参数持久化验证

- **WHEN** 任务提交时指定了temperature=353.0、targetProperties=["density","conductivity"]
- **THEN** SimulationInput记录中temperatureK=353.0
- **AND** SimulationJob.targetProperties包含["density","conductivity"]
- **AND** 未指定的参数使用默认值（pressure=1.0、timeStepFs=1.0等）

### Requirement: 流水线状态追踪测试

系统 SHALL 提供PipelineStatusTrackingTest，验证步骤级进度追踪和错误处理。

#### Scenario: 步骤级进度更新验证

- **WHEN** 流水线执行过程中
- **THEN** SimulationJob.resultSummary为JSON格式
- **AND** 包含currentStep、stepName、completedSteps、totalSteps字段
- **AND** currentStep随执行进度递增
- **AND** completedSteps列表逐步增长

#### Scenario: 错误处理验证

- **WHEN** 流水线某步骤执行失败
- **THEN** 任务状态更新为FAILED
- **AND** errorMessage包含失败步骤名称和具体错误
- **AND** 已完成的步骤结果保留

#### Scenario: 进度百分比计算验证

- **WHEN** 查询PipelineProgressDto
- **THEN** progressPercent = (completedSteps.size() / totalSteps) * 100
- **AND** 完成时progressPercent = 100

## MODIFIED Requirements

无修改的需求。

## REMOVED Requirements

无移除的需求。

## 测试策略说明

### 不使用Mock测试

根据项目规则，所有测试均不使用Mock。测试策略如下：

1. **数据库**：使用H2内存数据库（MySQL兼容模式），通过JPA自动建表，无需外部MySQL
2. **Docker**：连接真实Docker引擎和md-engine容器，测试前检查Docker可用性，不可用时跳过（使用`org.junit.jupiter.api.Assumptions`）
3. **文件系统**：使用临时目录（`@TempDir`），测试后自动清理
4. **HTTP调用**：使用`MockMvc`或`TestRestTemplate`调用真实Spring MVC端点（非Mock控制器）
5. **异步执行**：使用较短的超时时间，配合`Awaitility`库等待异步操作完成

### 测试分类

| 测试类 | 类型 | 依赖Docker | 执行时间 |
|--------|------|-----------|---------|
| PipelineE2ETest | 端到端集成测试 | 是 | 较长（分钟级） |
| DatabaseResultVerificationTest | 数据库集成测试 | 否 | 较短（秒级） |
| PipelineStatusTrackingTest | 状态追踪测试 | 是 | 中等 |

### 测试数据

使用标准电解液配方作为测试数据：
- 溶剂：EC:DMC = 3:7（摩尔比）
- 盐：LiPF6，浓度1M
- 温度：353.0 K
- 目标性质：density, conductivity
