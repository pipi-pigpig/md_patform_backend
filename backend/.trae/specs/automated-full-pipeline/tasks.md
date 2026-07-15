# Tasks

## 阶段一：修复审查发现的问题

- [x] Task 1: 修复MoltemplateService步骤1-2的Java端实现
  - [x] SubTask 1.1: 在MoltemplateService中实现调用Python脚本molecule-count模式的逻辑（通过DockerService在容器中执行`python3 run_modeling.py --mode molecule-count`）
  - [x] SubTask 1.2: 实现调用Python脚本box-size模式的逻辑（通过DockerService在容器中执行`python3 run_modeling.py --mode box-size`）
  - [x] SubTask 1.3: 解析Python脚本的JSON输出，提取分子数量和盒子尺寸结果
  - [x] SubTask 1.4: 将计算结果存入SimulationInput记录

- [x] Task 2: 修复PackmolService的Docker脚本路径
  - [x] SubTask 2.1: 将DOCKER_SCRIPT_PATH从`run_packmol.py`改为使用`run_modeling.py --mode packmol`
  - [x] SubTask 2.2: 更新buildDockerCommand()方法，构建正确的命令参数
  - [x] SubTask 2.3: 验证executePackmol()和executePackmolDirect()两种方式均可正常工作

- [x] Task 3: 修复DockerService.runLAMMPS()日志覆盖问题
  - [x] SubTask 3.1: 修改runLAMMPS()方法，支持指定日志文件名参数（默认log.lammps）
  - [x] SubTask 3.2: 在MDExecutorService中，为三个阶段分别指定不同的日志文件名（log.minimization、log.equilibrium、log.production）
  - [x] SubTask 3.3: 在MDExecutorService中新增mergeLammpsLogFiles()方法合并三个日志文件为raw_outputs/log.lammps

- [x] Task 4: 修复MDExecutorService降级执行模式
  - [x] SubTask 4.1: 修改executeFallbackSimulation()，不再返回假结果，而是标记任务为FAILED
  - [x] SubTask 4.2: 在errorMessage中明确提示"Docker服务不可用，请检查md-engine容器状态"
  - [x] SubTask 4.3: 移除模拟5秒的假执行逻辑

- [x] Task 5: 统一target_properties参数传递
  - [x] SubTask 5.1: 在FormulaRequest中新增targetProperties字段，在MoltemplateService中新增syncTargetPropertiesToJob()方法
  - [x] SubTask 5.2: 验证LammpsTemplateService、MDExecutorService、PostProcessingService均从SimulationJob.targetProperties读取
  - [x] SubTask 5.3: 确保target_properties的JSON格式一致（始终为JSON数组格式），PostProcessingService复用LammpsTemplateService.parseTargetProperties()

## 阶段二：扩展状态和进度追踪

- [x] Task 6: 扩展SimulationJob状态枚举
  - [x] SubTask 6.1: 在SimulationJob.status字段注释中新增MODELING状态说明
  - [x] SubTask 6.2: 修改SimulationService.updateSimulationStatus()，支持MODELING状态的时间记录逻辑（MODELING时设置startTime，RUNNING时不重置startTime）
  - [x] SubTask 6.3: 更新SimulationStatsDto，新增modelingCount字段

- [x] Task 7: 实现步骤级进度追踪
  - [x] SubTask 7.1: 创建PipelineProgressDto，包含jobId、status、currentStep、stepName、completedSteps、totalSteps、progressPercent、errorMessage、startTime字段
  - [x] SubTask 7.2: 在PipelineService中实现updateProgress()方法，更新SimulationJob.resultSummary为JSON格式的进度信息
  - [x] SubTask 7.3: 创建PipelineStepConstants类，定义步骤编号与名称的映射常量（STEP_NAMES），共11步

## 阶段三：创建全流程编排服务

- [x] Task 8: 创建PipelineService全流程编排服务
  - [x] SubTask 8.1: 创建PipelineService.java，注入MoltemplateService、MDExecutorService、PostProcessingService、SimulationService、PathUtil、DockerService
  - [x] SubTask 8.2: 实现submitPipelineTask()方法——接收PipelineSubmitRequest，创建SimulationJob和SimulationInput记录，创建任务目录，异步启动全流程
  - [x] SubTask 8.3: 实现executeFullPipeline()方法——按顺序执行步骤0-9，每步更新进度，失败时停止并记录错误
  - [x] SubTask 8.4: 实现步骤0-7.5的编排（调用MoltemplateService.executeFullModeling()）
  - [x] SubTask 8.5: 实现步骤9（LAMMPS模拟）的编排（executeFullModeling内部已包含LAMMPS执行）
  - [x] SubTask 8.6: 实现步骤10（后处理）的编排（调用PostProcessingService.executePostProcessing(jobId, true)，完成后状态切换为COMPLETED）
  - [x] SubTask 8.7: 实现getPipelineProgress()方法——根据jobId查询任务状态和进度信息，返回PipelineProgressDto
  - [x] SubTask 8.8: 实现错误处理和日志记录，确保任何步骤失败都有详细的错误信息

- [x] Task 9: 自动创建SimulationInput记录
  - [x] SubTask 9.1: 在PipelineService.submitPipelineTask()中，根据FormulaRequest创建SimulationInput记录
  - [x] SubTask 9.2: 从FormulaRequest中提取temperature、pressure等参数，未指定的使用默认值
  - [x] SubTask 9.3: 设置targetProperties到SimulationInput
  - [x] SubTask 9.4: 关联SimulationInput到对应的jobId

## 阶段四：创建全流程API端点

- [x] Task 10: 创建PipelineController
  - [x] SubTask 10.1: 创建PipelineController.java，注入PipelineService、SimulationService、CalculationResultService
  - [x] SubTask 10.2: 实现`POST /api/pipeline/submit`端点——接收PipelineSubmitRequest，调用PipelineService.submitPipelineTask()，返回202 Accepted
  - [x] SubTask 10.3: 实现`GET /api/pipeline/status/{jobId}`端点——调用PipelineService.getPipelineProgress()，返回PipelineProgressDto
  - [x] SubTask 10.4: 实现`GET /api/pipeline/results/{jobId}`端点——查询任务最终结果，包含建模结果、模拟结果和后处理结果
  - [x] SubTask 10.5: 添加参数校验和权限验证（用户只能查询自己的任务）

## 阶段五：集成后处理到全流程

- [x] Task 11: 集成后处理自动触发
  - [x] SubTask 11.1: 在PipelineService.executeFullPipeline()中，步骤9完成后自动调用PostProcessingService.executePostProcessing()
  - [x] SubTask 11.2: 修改PostProcessingService.executePostProcessing()，新增重载方法支持autoTriggered参数，自动调用时不需要COMPLETED状态
  - [x] SubTask 11.3: 后处理完成后，PipelineService更新任务状态为COMPLETED
  - [x] SubTask 11.4: 后处理失败时，PipelineService更新任务状态为FAILED，但保留LAMMPS输出文件

## 阶段六：验证

- [x] Task 12: 编译验证
  - [x] SubTask 12.1: Spring Boot项目编译成功，无错误
  - [x] SubTask 12.2: 所有新增和修改的Java文件无语法错误
  - [x] SubTask 12.3: 所有依赖注入正确，无循环依赖

- [ ] Task 13: 功能验证
  - [ ] SubTask 13.1: 验证PipelineService.submitPipelineTask()能创建SimulationJob和SimulationInput记录
  - [ ] SubTask 13.2: 验证PipelineService.executeFullPipeline()能按顺序执行步骤0-9
  - [ ] SubTask 13.3: 验证步骤1-2的Java端调用Python脚本正确
  - [ ] SubTask 13.4: 验证后处理在LAMMPS完成后自动触发
  - [ ] SubTask 13.5: 验证步骤级进度追踪正确
  - [ ] SubTask 13.6: 验证PipelineController的API端点正确响应
  - [ ] SubTask 13.7: 验证错误处理：某步骤失败时任务标记为FAILED，错误信息记录正确

# Task Dependencies

- Task 1-5 可并行执行（修复审查问题，互不依赖）
- Task 6-7 可并行执行（状态和进度扩展，互不依赖）
- Task 8 依赖 Task 6（PipelineService需要MODELING状态）
- Task 9 依赖 Task 8（SimulationInput创建在PipelineService中）
- Task 10 依赖 Task 8（Controller依赖Service）
- Task 11 依赖 Task 8（后处理集成在PipelineService中）
- Task 12 依赖 Task 1-11（编译验证需所有代码完成）
- Task 13 依赖 Task 12（功能验证需编译通过）
