# 端到端流水线测试验证清单

## 测试基础设施

- [x] pom.xml中已添加Awaitility依赖
- [x] application-test.yml配置正确（H2内存数据库、Docker容器名、临时文件路径）
- [x] 测试用PipelineSubmitRequest构建工具方法可用

## 数据库结果验证测试

- [x] SimulationJob状态流转正确：PENDING→MODELING→RUNNING→COMPLETED，每个状态字段值正确
- [x] SimulationInput参数持久化正确：temperature=353.0、targetProperties与提交一致
- [x] CalculationResult主表和子表数据完整：propertyValue有效、propertyName正确、子表关联正确
- [x] 未指定参数使用默认值：pressure=1.0、timeStepFs=1.0、cutoffDistanceAng=12.0等

## 流水线状态追踪测试

- [x] resultSummary为JSON格式，包含currentStep/stepName/completedSteps/totalSteps字段
- [x] progressPercent计算正确：completedSteps.size() / totalSteps * 100
- [x] Docker不可用时任务标记为FAILED，errorMessage非空
- [x] GET /api/pipeline/status/{jobId}返回完整的PipelineProgressDto

## 端到端流水线集成测试

- [x] POST /api/pipeline/submit返回202 Accepted和jobId
- [x] 提交后数据库中存在SimulationJob和SimulationInput记录
- [x] 轮询GET /api/pipeline/status/{jobId}可观察到状态流转
- [x] 任务COMPLETED后CalculationResult表中有记录且propertyValue有效（需Docker环境完整运行）
- [x] GET /api/pipeline/results/{jobId}返回完整计算结果（需Docker环境完整运行）
- [x] Docker不可用时任务最终状态为FAILED，errorMessage包含Docker提示

## 测试执行

- [x] DatabaseResultVerificationTest全部通过（不依赖Docker）—— 5/5 pass
- [x] PipelineStatusTrackingTest全部通过 —— 6/6 pass
- [x] PipelineE2ETest全部通过（需要Docker和md-engine容器）—— 4/5 pass, 1 skipped（Docker可用时跳过不可用测试）
- [x] 所有测试不使用Mock（遵循项目规则）

## 修复的问题

- [x] H2 JSON列双转义问题：使用findById+save替代@Modifying查询写入resultSummary
- [x] PipelineService jobRootPath NOT NULL约束违反：首次保存前设置"pending"占位值
