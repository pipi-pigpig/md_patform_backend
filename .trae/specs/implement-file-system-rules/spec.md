# 计算引擎文件系统规则实现规范

## Why
当前backend代码违反了计算引擎文件系统规则的核心原则：路径硬编码、缺少统一路径生成工具、文件组织不规范、缺少原子写入机制。需要开发完整的文件系统基础设施代码来强制执行这些规则。

## What Changes
- 创建`PathUtil`工具类：统一路径生成，禁止硬编码
- 创建`StorageConfig`配置类：从application.yml读取配置
- 创建`AtomicFileService`：原子写入操作（临时文件→重命名）
- 创建`FileCleanupScheduler`：临时文件定时清理
- 更新`application.yml`：添加`md-platform.file-storage`配置
- 重构`FileService`：使用PathUtil替代硬编码路径

## Impact
- Affected specs: 无
- Affected code:
  - 新增：`common/util/PathUtil.java`
  - 新增：`common/config/StorageConfig.java`
  - 新增：`engine/service/AtomicFileService.java`
  - 新增：`engine/service/FileCleanupScheduler.java`
  - 修改：`application.yml`
  - 重构：`engine/service/FileService.java`

## ADDED Requirements

### Requirement: PathUtil路径生成工具
系统必须提供统一的路径生成工具类，禁止任何硬编码路径。

#### Scenario: 任务根目录生成
- **WHEN** 调用`PathUtil.getJobRootPath(userId, jobId)`
- **THEN** 返回格式为`{rootPath}/user_{userId}/jobs/job_{jobId}/`的路径

#### Scenario: 输入文件目录生成
- **WHEN** 调用`PathUtil.getInputPath(userId, jobId)`
- **THEN** 返回格式为`{rootPath}/user_{userId}/jobs/job_{jobId}/inputs/`的路径

#### Scenario: 输出文件目录生成
- **WHEN** 调用`PathUtil.getOutputPath(userId, jobId)`
- **THEN** 返回格式为`{rootPath}/user_{userId}/jobs/job_{jobId}/outputs/`的路径

#### Scenario: 后处理结果目录生成
- **WHEN** 调用`PathUtil.getPostProcessingPath(userId, jobId)`
- **THEN** 返回格式为`{rootPath}/user_{userId}/jobs/job_{jobId}/post_processing/`的路径

#### Scenario: 临时文件目录生成
- **WHEN** 调用`PathUtil.getTempPath(userId, jobId)`
- **THEN** 返回格式为`{rootPath}/user_{userId}/jobs/job_{jobId}/temp/`的路径

#### Scenario: LAMMPS脚本文件名生成
- **WHEN** 调用`PathUtil.getLammpsScriptFilename(stage)`
- **THEN** 返回格式为`in.{stage}`的文件名（如`in.production`）

#### Scenario: 轨迹文件名生成
- **WHEN** 调用`PathUtil.getTrajectoryFilename(type)`
- **THEN** 返回格式为`dump.{type}.lammpstrj`的文件名

#### Scenario: 后处理结果文件名生成
- **WHEN** 调用`PathUtil.getResultFilename(property)`
- **THEN** 返回格式为`{property}_result.json`的文件名

#### Scenario: 临时文件名生成
- **WHEN** 调用`PathUtil.getTempFilename(jobId, name)`
- **THEN** 返回格式为`job_{jobId}_{name}.tmp`的文件名

### Requirement: StorageConfig配置类
系统必须从application.yml读取文件存储配置。

#### Scenario: 配置读取
- **WHEN** 应用启动时
- **THEN** StorageConfig从`md-platform.file-storage.root-path`读取根目录配置
- **AND** 如果配置不存在，使用默认值`./data/md_platform_data`

### Requirement: 原子写入操作
所有文件写入必须采用原子操作，避免崩溃损坏。

#### Scenario: 原子写入流程
- **WHEN** 调用`AtomicFileService.writeAtomic(targetPath, content)`
- **THEN** 先写入临时文件`{targetPath}.tmp`
- **AND** 成功后将临时文件重命名为目标文件
- **AND** 失败时删除临时文件

#### Scenario: 原子复制流程
- **WHEN** 调用`AtomicFileService.copyAtomic(sourcePath, targetPath)`
- **THEN** 先复制到临时文件
- **AND** 成功后重命名为目标文件

### Requirement: 临时文件清理
系统必须定时清理过期临时文件。

#### Scenario: 定时清理
- **WHEN** 每天凌晨2点执行清理任务
- **THEN** 删除所有超过24小时的临时文件（`*.tmp`）
- **AND** 记录清理日志

### Requirement: 任务目录自动创建
任务创建时必须自动创建所有必需子目录。

#### Scenario: 目录创建
- **WHEN** 创建新任务时调用`PathUtil.createJobDirectories(userId, jobId)`
- **THEN** 创建以下目录：
  - `inputs/` - 输入文件目录
  - `outputs/` - 输出文件目录
  - `post_processing/` - 后处理结果目录
  - `temp/` - 临时文件目录
  - `visualization/` - 可视化目录
  - `report/` - 报告目录

### Requirement: 相对路径存储
数据库中只存储相对路径，绝对路径运行时动态生成。

#### Scenario: 相对路径格式
- **WHEN** 保存文件路径到数据库
- **THEN** 只存储相对于任务根目录的路径（如`inputs/in.production`）
- **AND** 绝对路径通过`PathUtil.resolveAbsolutePath(userId, jobId, relativePath)`动态生成

## MODIFIED Requirements

### Requirement: FileService重构
FileService必须使用PathUtil生成路径，禁止硬编码。

#### Scenario: 存储输入文件
- **WHEN** 调用`FileService.storeInputFile(userId, jobId, file)`
- **THEN** 使用`PathUtil.getInputPath(userId, jobId)`生成目标目录
- **AND** 使用原子写入操作

#### Scenario: 存储输出文件
- **WHEN** 调用`FileService.storeOutputFile(userId, jobId, file)`
- **THEN** 使用`PathUtil.getOutputPath(userId, jobId)`生成目标目录