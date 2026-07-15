# Moltemplate执行与文件整理规范（步骤6-7）

## Why

在之前的Moltemplate自动化建模流程中，已经完成了步骤1-5（接收配方参数、计算分子数量、调取分子模板、Packmol堆积、生成system.lt），但缺少关键的步骤6和步骤7：

1. 步骤6：执行Moltemplate命令，将system.lt文件转换为LAMMPS可识别的输入文件（system.data、system.in.init、system.in.settings）
2. 步骤7：文件整理输出，将生成的LAMMPS输入文件整理到正确的目录结构，符合文件输入输出规则

这两个步骤是Moltemplate自动化建模流程的最后关键步骤，直接影响后续的LAMMPS模拟计算。

## What Changes

* **实现Moltemplate执行功能（步骤6）**：
  * 在Python端实现`execute_moltemplate_command()`函数，调用moltemplate.sh命令
  * 支持命令参数配置（-atomstyle full、-pdb等）
  * 实现执行结果解析和错误处理
  * 验证生成的文件（system.data、system.in.init、system.in.settings）

* **实现文件整理输出功能（步骤7）**：
  * 在Python端实现`organize_lammps_input_files()`函数
  * 将生成的文件移动到正确的目录结构（inputs/目录）
  * 使用PathUtil生成正确的文件路径
  * 验证文件完整性（文件存在、大小正确）

* **集成到完整流程**：
  * 更新`run_full_mode()`函数，包含步骤6和步骤7
  * 新增`run_moltemplate_execution_mode()`执行模式
  * 更新`run_modeling.py`入口脚本，支持新执行模式

* **Spring Boot集成**：
  * 创建`MoltemplateExecutionService`服务类
  * 实现完整的建模流程编排（步骤1-7）
  * 使用PathUtil生成文件路径，遵守文件输入输出规则

## Impact

* Affected specs: moltemplate-system-generation-step4（需要在其后执行）
* Affected specs: 文件输入输出规则（必须遵守目录结构规范）

* Affected code:
  * 新增：`backend/src/main/resources/scripts/modeling/utils/moltemplate_utils.py`（新增执行和整理函数）
  * 修改：`backend/src/main/resources/scripts/modeling/run_modeling.py`（新增执行模式）
  * 新增：`backend/src/main/java/com/electrolyte/backend/service/MoltemplateExecutionService.java`
  * 修改：`backend/src/main/java/com/electrolyte/backend/service/MoltemplateService.java`（集成完整流程）

## ADDED Requirements

### Requirement: Moltemplate命令执行

系统必须能够执行Moltemplate命令，将system.lt文件转换为LAMMPS输入文件。

#### Scenario: 执行moltemplate.sh命令

* **WHEN** 调用execute_moltemplate_command()函数
* **THEN** 执行命令：`moltemplate.sh -atomstyle full system.lt`
* **AND** 命令在工作目录中执行
* **AND** 捕获命令输出和错误信息
* **AND** 返回执行结果（成功/失败、输出文件列表）

#### Scenario: 验证生成文件

* **WHEN** Moltemplate命令执行成功
* **THEN** 验证以下文件已生成：
  * system.data（LAMMPS结构文件）
  * system.in.init（初始化设置）
  * system.in.settings（力场设置）
* **AND** 验证文件大小大于0
* **AND** 验证文件格式正确

#### Scenario: 处理执行失败

* **WHEN** Moltemplate命令执行失败
* **THEN** 记录详细错误信息
* **AND** 返回失败状态和错误原因
* **AND** 不执行后续步骤

### Requirement: 文件整理输出

系统必须将生成的LAMMPS输入文件整理到正确的目录结构。

#### Scenario: 移动文件到inputs目录

* **WHEN** 调用organize_lammps_input_files()函数
* **THEN** 将以下文件移动到inputs/目录：
  * system.lt
  * system.data
  * system.in.init
  * system.in.settings
  * packmol.inp
  * packed_system.pdb
* **AND** 使用PathUtil生成正确的文件路径
* **AND** 遵守文件输入输出规则的目录结构

#### Scenario: 验证文件完整性

* **WHEN** 文件移动完成
* **THEN** 验证所有必需文件存在
* **AND** 验证文件大小正确
* **AND** 记录文件路径到数据库
* **AND** 返回整理结果（成功/失败、文件列表）

#### Scenario: 处理文件缺失

* **WHEN** 发现文件缺失或损坏
* **THEN** 记录缺失文件列表
* **AND** 返回失败状态
* **AND** 提供修复建议

### Requirement: 完整流程集成

系统必须支持完整的Moltemplate自动化建模流程（步骤1-7）。

#### Scenario: 执行完整流程

* **WHEN** 调用run_full_mode()函数
* **THEN** 按顺序执行步骤1-7：
  * 步骤1：接收配方参数
  * 步骤2：计算分子数量
  * 步骤3：调取分子模板
  * 步骤4：Packmol堆积
  * 步骤5：生成system.lt
  * 步骤6：执行Moltemplate
  * 步骤7：文件整理输出
* **AND** 每个步骤失败时停止后续执行
* **AND** 记录每个步骤的执行结果

#### Scenario: 单独执行步骤6-7

* **WHEN** 调用run_moltemplate_execution_mode()函数
* **THEN** 仅执行步骤6和步骤7
* **AND** 需要提供system.lt文件路径
* **AND** 返回执行结果

### Requirement: Spring Boot服务集成

Spring Boot后端必须能够调用完整的Moltemplate建模流程。

#### Scenario: 创建MoltemplateExecutionService

* **WHEN** 创建MoltemplateExecutionService服务类
* **THEN** 实现executeMoltemplate()方法
* **AND** 实现organizeInputFiles()方法
* **AND** 使用PathUtil生成文件路径
* **AND** 调用Python脚本执行命令

#### Scenario: 集成完整流程

* **WHEN** MoltemplateService调用完整流程
* **THEN** 按顺序调用各个步骤的服务方法
* **AND** 处理每个步骤的执行结果
* **AND** 更新任务状态到数据库

## MODIFIED Requirements

### Requirement: run_modeling.py入口脚本

run_modeling.py必须支持新的执行模式。

#### Scenario: 新增moltemplate-execution模式

* **WHEN** 使用--mode moltemplate-execution参数
* **THEN** 执行步骤6和步骤7
* **AND** 需要提供--system-lt-file参数
* **AND** 需要提供--job-dir参数

#### Scenario: 更新full模式

* **WHEN** 使用--mode full参数
* **THEN** 执行完整的步骤1-7流程
* **AND** 包含Moltemplate执行和文件整理步骤

## REMOVED Requirements

无移除的需求。