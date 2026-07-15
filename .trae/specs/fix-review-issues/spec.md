# 修复审查报告问题 Spec

## Why

根据《Trae 全流程工作审查报告》，发现了7个需要修复的问题，其中1个严重问题（日志文件名不匹配）、3个中等问题（文件收集逻辑重复、solvation_stability字段类型不匹配、getAtomSelection可能失败）、2个低优先级问题（线程池管理、容器名不一致）和1个安全问题（命令注入风险）。这些问题会导致后处理读取日志失败、溶剂化结构结果写入失败、RDF计算跳过等严重后果，必须立即修复。

## What Changes

- **修复日志文件名不匹配**：统一Java侧和Python侧的日志文件命名，Java侧生成的日志文件名为 `log.minimization`，Python侧期望 `log.minimization.lammps`，需要统一为 `log.{stage}` 格式
- **修复solvation_stability字段类型不匹配**：Python侧输出字符串（convergence_status），Java侧期望Double（溶剂化壳层寿命），需要将Python输出改为数值类型或添加新字段存储收敛状态
- **修复getAtomSelection原子选择逻辑**：LAMMPSDUMP格式使用atom type（整数索引）而非name，需要改进原子选择逻辑以正确处理LAMMPS格式
- **修复测试容器名不一致**：PipelineE2EFullTest中使用 `md_engine`，配置中默认为 `md-engine`，需要统一
- **改进线程池管理**：将 `newCachedThreadPool()` 改为Spring管理的线程池
- **增强命令注入防护**：对路径参数进行更严格的验证

## Impact

- Affected specs: lammps-postprocessing-database-pipeline
- Affected code:
  - 修改：`MDExecutorService.java`（日志合并逻辑）
  - 修改：`stage4_lammps_execution.py`（日志合并逻辑）
  - 修改：`property_calculator.py`（SolvationCalculator输出字段）
  - 修改：`mdanalysis_utils.py`（getAtomSelection方法）
  - 修改：`PipelineE2EFullTest.java`（容器名）
  - 修改：`PipelineService.java`（线程池）
  - 修改：`DockerService.java`（命令注入防护）

## ADDED Requirements

### Requirement: 日志文件命名统一

系统 SHALL 使用统一的日志文件命名格式，确保Java侧和Python侧能正确识别和合并日志文件。

#### Scenario: Java侧日志文件命名

- **WHEN** LAMMPS执行各阶段（minimization、equilibrium、production）
- **THEN** 日志文件命名为 `log.minimization`、`log.equilibrium`、`log.production`（不带 `.lammps` 后缀）
- **AND** 合并后的日志文件命名为 `log.lammps`

#### Scenario: Python侧日志文件识别

- **WHEN** Python后处理脚本读取日志文件
- **THEN** 正确识别 `log.minimization`、`log.equilibrium`、`log.production` 文件
- **AND** 正确识别合并后的 `log.lammps` 文件

### Requirement: solvation_stability字段类型匹配

系统 SHALL 确保Python输出的solvation_stability字段与Java端SolvationResult实体类型匹配。

#### Scenario: solvation_stability输出数值类型

- **WHEN** SolvationCalculator计算溶剂化结构
- **THEN** `solvation_stability` 字段输出数值类型（溶剂化壳层寿命，单位ps）
- **AND** 新增 `convergence_status` 字段存储收敛状态字符串

### Requirement: LAMMPSDUMP格式原子选择

系统 SHALL 正确处理LAMMPSDUMP格式的原子选择，使用atom type而非name。

#### Scenario: 通过type属性选择原子

- **WHEN** 使用getAtomSelection选择原子
- **THEN** 首先尝试通过 `type` 属性选择（整数索引）
- **AND** 如果失败，回退到通过 `name` 属性选择
- **AND** 记录选择结果日志

### Requirement: 测试容器名统一

系统 SHALL 在所有测试中使用统一的容器名称，与配置保持一致。

#### Scenario: 使用配置的容器名

- **WHEN** 测试代码中引用容器名
- **THEN** 使用配置中的默认容器名 `md-engine`（连字符）
- **AND** 不使用硬编码的 `md_engine`（下划线）

### Requirement: Spring管理的线程池

系统 SHALL 使用Spring管理的线程池替代手动创建的线程池。

#### Scenario: 使用Spring异步执行

- **WHEN** PipelineService需要异步执行任务
- **THEN** 使用 `@Async` 注解或Spring管理的 `TaskExecutor`
- **AND** 不使用 `Executors.newCachedThreadPool()`

## MODIFIED Requirements

### Requirement: 命令注入防护增强

DockerService中的命令构建 SHALL 对路径参数进行更严格的验证，确保不包含特殊字符。

## REMOVED Requirements

无