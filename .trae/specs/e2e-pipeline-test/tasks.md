# Tasks

## 阶段一：测试基础设施准备

- [x] Task 1: 更新测试配置和依赖
  - [x] SubTask 1.1: 在pom.xml中添加Awaitility依赖（用于异步测试等待）
  - [x] SubTask 1.2: 检查并更新application-test.yml，确保H2配置正确、Docker容器名与实际一致
  - [x] SubTask 1.3: 创建测试用的PipelineSubmitRequest构建工具方法（标准电解液配方数据）

## 阶段二：数据库结果验证测试（不依赖Docker）

- [x] Task 2: 创建DatabaseResultVerificationTest
  - [x] SubTask 2.1: 创建测试类，使用@SpringBootTest + @ActiveProfiles("test")，注入SimulationJobRepository、SimulationInputRepository、CalculationResultRepository
  - [x] SubTask 2.2: 实现testSimulationJobStatusTransition——创建SimulationJob，手动更新状态PENDING→MODELING→RUNNING→COMPLETED，验证每个状态下的字段值（startTime、endTime、executionTimeS）
  - [x] SubTask 2.3: 实现testSimulationInputPersistence——创建SimulationInput记录，设置temperature=353.0、targetProperties等参数，保存后重新查询验证字段值一致
  - [x] SubTask 2.4: 实现testCalculationResultStorage——创建CalculationResult记录（含子表DensityResult），保存后查询验证主表和子表数据完整
  - [x] SubTask 2.5: 实现testDefaultParameterValues——通过PipelineService.submitPipelineTask()提交任务（仅验证SimulationInput创建部分），验证未指定参数使用默认值

## 阶段三：流水线状态追踪测试

- [x] Task 3: 创建PipelineStatusTrackingTest
  - [x] SubTask 3.1: 创建测试类，使用@SpringBootTest + @ActiveProfiles("test")，注入PipelineService、SimulationJobRepository、MockMvc
  - [x] SubTask 3.2: 实现testProgressUpdateFormat——提交任务后，查询SimulationJob.resultSummary，验证JSON格式包含currentStep/stepName/completedSteps/totalSteps字段
  - [x] SubTask 3.3: 实现testProgressPercentCalculation——验证PipelineProgressDto.progressPercent = completedSteps.size() / totalSteps * 100
  - [x] SubTask 3.4: 实现testErrorHandling——模拟Docker不可用场景（停止md-engine容器），提交任务，验证任务最终状态为FAILED且errorMessage非空
  - [x] SubTask 3.5: 实现testStatusApiReturnsCorrectInfo——调用GET /api/pipeline/status/{jobId}，验证返回的PipelineProgressDto字段完整

## 阶段四：端到端流水线集成测试

- [x] Task 4: 创建PipelineE2ETest
  - [x] SubTask 4.1: 创建测试类，使用@SpringBootTest(webEnvironment = RANDOM_PORT) + @ActiveProfiles("test")，注入TestRestTemplate、SimulationJobRepository、CalculationResultRepository
  - [x] SubTask 4.2: 添加Docker可用性检查——使用`Assumptions.assumeTrue()`检查md-engine容器是否运行，不可用时跳过测试
  - [x] SubTask 4.3: 实现testSubmitTaskAndGetInitialStatus——调用POST /api/pipeline/submit，验证返回202和jobId，查询数据库确认SimulationJob和SimulationInput记录已创建
  - [x] SubTask 4.4: 实现testTrackPipelineExecution——提交任务后，使用Awaitility轮询GET /api/pipeline/status/{jobId}，验证状态从MODELING开始流转，记录每个状态变化
  - [x] SubTask 4.5: 实现testVerifyFinalResultsInDatabase——等待任务COMPLETED后，查询CalculationResult表验证记录存在且propertyValue有效，调用GET /api/pipeline/results/{jobId}验证API返回结果
  - [x] SubTask 4.6: 实现testDockerUnavailableScenario——停止md-engine容器后提交任务，验证任务最终状态为FAILED，errorMessage包含Docker相关提示

## 阶段五：验证与清理

- [x] Task 5: 运行全部测试并验证
  - [x] SubTask 5.1: 运行DatabaseResultVerificationTest（不依赖Docker），确保全部通过
  - [x] SubTask 5.2: 运行PipelineStatusTrackingTest，确保通过
  - [x] SubTask 5.3: 运行PipelineE2ETest（需要Docker），确保通过
  - [x] SubTask 5.4: 修复发现的任何问题

# Task Dependencies

- Task 1 无依赖，首先执行
- Task 2 依赖 Task 1（需要测试配置和工具方法）
- Task 3 依赖 Task 1
- Task 4 依赖 Task 1 和 Task 3（E2E测试需要状态追踪的基础验证）
- Task 5 依赖 Task 2、3、4（所有测试编写完成后统一运行验证）
- Task 2 和 Task 3 可并行执行
