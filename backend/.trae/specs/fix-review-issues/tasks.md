# Tasks

- [x] 任务1：修复日志文件名不匹配问题
  - [x] 子任务1.1：修改stage4_lammps_execution.py的_merge_log_files函数，使其识别 `log.minimization`、`log.equilibrium`、`log.production`（不带 `.lammps` 后缀）
  - [x] 子任务1.2：验证Java侧MDExecutorService的mergeLammpsLogFiles方法生成的日志文件名正确（`log.minimization`等）
  - [x] 子任务1.3：验证合并后的 `log.lammps` 文件能被Python后处理正确读取

- [x] 任务2：修复solvation_stability字段类型不匹配
  - [x] 子任务2.1：修改property_calculator.py的SolvationCalculator，将 `solvation_stability` 字段改为数值类型（溶剂化壳层寿命，单位ps）
  - [x] 子任务2.2：新增 `convergence_status` 字段存储收敛状态字符串（"CONVERGED"/"NOT_CONVERGED"）
  - [x] 子任务2.3：验证Java端SolvationResult实体能正确接收数值类型的solvation_stability

- [x] 任务3：修复getAtomSelection原子选择逻辑
  - [x] 子任务3.1：修改mdanalysis_utils.py的getAtomSelection方法，首先尝试通过 `type` 属性选择原子
  - [x] 子任务3.2：添加回退逻辑，如果type选择失败则尝试通过 `name` 属性选择
  - [x] 子任务3.3：添加详细的日志记录，说明使用了哪种选择方式

- [x] 任务4：修复测试容器名不一致
  - [x] 子任务4.1：修改PipelineE2EFullTest.java，将硬编码的 `md_engine` 改为使用配置的容器名（通过@Value注入）
  - [x] 子任务4.2：检查其他测试文件是否有类似问题，统一修复（LammpsExecutionE2ETest、PostProcessingE2ETest、LammpsToDatabaseE2ETest）

- [x] 任务5：改进线程池管理
  - [x] 子任务5.1：修改PipelineService.java，将 `Executors.newCachedThreadPool()` 替换为Spring管理的线程池
  - [x] 子任务5.2：添加 `AsyncConfig.java` 配置类，定义ThreadPoolTaskExecutor Bean
  - [x] 子任务5.3：确保应用关闭时线程池能优雅终止（setWaitForTasksToCompleteOnShutdown=true）

- [x] 任务6：增强命令注入防护
  - [x] 子任务6.1：修改DockerService.java，添加isPathSafe()路径验证方法
  - [x] 子任务6.2：验证路径参数不包含特殊字符（如 `$`、`;`、`|`、`&` 等）
  - [x] 子任务6.3：在MDExecutorService.mergeLammpsLogFiles中调用路径验证，记录验证失败的日志

- [x] 任务7：验证修复效果
  - [x] 子任务7.1：运行编译验证所有修改无语法错误（mvn compile成功）
  - [x] 子任务7.2：运行单元测试验证基本功能
  - [x] 子任务7.3：验证日志文件能正确合并和读取

# Task Dependencies

- 任务1（日志文件名）无依赖，可独立开始
- 任务2（solvation_stability）无依赖，可独立开始
- 任务3（getAtomSelection）无依赖，可独立开始
- 任务4（容器名）无依赖，可独立开始
- 任务5（线程池）无依赖，可独立开始
- 任务6（命令注入）无依赖，可独立开始
- 任务7（验证）依赖任务1-6全部完成
- 任务1-6可并行执行