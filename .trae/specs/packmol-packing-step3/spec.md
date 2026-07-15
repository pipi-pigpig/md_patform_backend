# Packmol堆积步骤3实现规范

## Why

Packmol堆积是Moltemplate自动化建模子流程的核心步骤，需要根据分子数量计算结果，自动生成Packmol输入脚本，执行分子随机堆积，生成无原子重叠的初始构型（packed_system.pdb文件），为后续的Moltemplate拓扑构建提供基础数据。

## What Changes

- **Python部分**：
  - 增强`packmol_utils.py`模块，实现完整的Packmol堆积流程
  - 实现临时文件管理，所有临时文件存放在`temp/packmol_temp/`目录
  - 实现Packmol输出和错误信息捕获，方便调试
  - 实现tolerance参数固定为2.0Å，避免原子重叠
  - 实现packed_system.pdb文件生成和验证

- **Spring Boot部分**：
  - 创建`PackmolService.java`服务类，封装Packmol调用逻辑
  - 创建`PackmolResult.java`结果DTO类
  - 实现与Python脚本的集成调用

## Impact

- Affected specs: moltemplate-steps1-2（依赖其输出的分子数量计算结果）
- Affected specs: implement-file-system-rules（需要使用PathUtil生成文件路径）
- Affected code:
  - 新增：`engine/service/PackmolService.java`
  - 新增：`engine/dto/PackmolResult.java`
  - 修改：`scripts/modeling/packmol_utils.py`
  - 修改：`scripts/modeling/run_modeling.py`

## ADDED Requirements

### Requirement: Packmol输入脚本生成（步骤3）

系统必须能够根据分子数量计算结果，自动生成Packmol输入脚本。

#### Scenario: 生成Packmol输入脚本

- **WHEN** 给定分子列表和盒子尺寸
- **THEN** 生成Packmol输入脚本（packmol.inp），格式如下：
```
tolerance 2.0
filetype pdb
output packed_system.pdb

structure EC.pdb
  number 200
  inside box 0. 0. 0. 40.0 40.0 40.0
end structure

structure DMC.pdb
  number 200
  inside box 0. 0. 0. 40.0 40.0 40.0
end structure

structure Li.pdb
  number 50
  inside box 0. 0. 0. 40.0 40.0 40.0
end structure

structure PF6.pdb
  number 50
  inside box 0. 0. 0. 40.0 40.0 40.0
end structure
```

#### Scenario: tolerance参数设置

- **WHEN** 生成Packmol输入脚本
- **THEN** tolerance参数固定设置为2.0Å
- **AND** 该参数不可由用户修改，确保避免原子重叠

### Requirement: 分子模板文件调取

系统必须能够从分子模板库调取单分子PDB文件。

#### Scenario: 调取分子模板

- **WHEN** 给定分子名称列表
- **THEN** 从`molecule_template_table`查询所有分子的单分子PDB文件路径
- **AND** 将所有单分子PDB文件复制到工作目录

#### Scenario: 分子模板缺失

- **WHEN** 分子库中无对应分子模板
- **THEN** 返回错误提示，建议用户上传自定义分子

### Requirement: Packmol执行与输出捕获

系统必须能够执行Packmol并捕获其输出和错误信息。

#### Scenario: 执行Packmol

- **WHEN** Packmol输入脚本已生成
- **THEN** 在工作目录执行Packmol命令：`packmol < packmol.inp`
- **AND** 捕获标准输出和标准错误信息
- **AND** 将执行日志保存到文件

#### Scenario: Packmol执行成功

- **WHEN** Packmol执行完成且返回码为0
- **THEN** 检查输出文件`packed_system.pdb`是否存在且大小>0
- **AND** 返回成功状态和输出文件路径

#### Scenario: Packmol执行失败

- **WHEN** Packmol执行失败（返回码非0）
- **THEN** 记录详细错误日志
- **AND** 返回失败状态和错误信息
- **AND** 可选择增大盒子尺寸或减小tolerance后重试

### Requirement: 临时文件管理

系统必须正确管理Packmol执行过程中的临时文件。

#### Scenario: 临时文件存放

- **WHEN** 执行Packmol堆积
- **THEN** 所有临时文件存放在`temp/packmol_temp/`目录
- **AND** 临时文件命名格式：`job_{jobId}_*.tmp`

#### Scenario: 临时文件清理

- **WHEN** Packmol执行完成（成功或失败）
- **THEN** 清理所有临时文件
- **AND** 保留packmol.inp和packed_system.pdb到任务inputs目录

### Requirement: 输出文件验证

系统必须验证Packmol输出的PDB文件有效性。

#### Scenario: PDB文件验证

- **WHEN** Packmol执行完成
- **THEN** 验证packed_system.pdb文件：
  - 文件存在且大小>0
  - 原子总数与预期一致
  - 无明显原子重叠（通过tolerance检查）

#### Scenario: 输出结果格式

- **WHEN** Packmol堆积完成
- **THEN** 输出结果包含：
```json
{
  "success": true,
  "pdb_file_path": "user_{userId}/jobs/job_{jobId}/inputs/packed_system.pdb",
  "input_script_path": "user_{userId}/jobs/job_{jobId}/inputs/packmol.inp",
  "atom_count": 1234,
  "execution_log": "Packmol execution log...",
  "error_message": "",
  "elapsed_time_seconds": 12.5
}
```

### Requirement: 异常处理

系统必须处理Packmol执行过程中的异常情况。

#### Scenario: Packmol堆积失败

- **WHEN** 无法生成无重叠构型
- **THEN** 尝试增大盒子尺寸（放大系数1.1）后重试
- **AND** 最大重试次数为3次
- **AND** 若仍失败，返回错误信息

#### Scenario: 执行超时

- **WHEN** Packmol执行超过指定时间（默认3600秒）
- **THEN** 终止执行进程
- **AND** 返回超时错误信息

#### Scenario: 分子数量过多

- **WHEN** 分子总数超过10000
- **THEN** 返回警告信息，建议减小盒子尺寸或分子数量
- **AND** 继续执行（用户可选择取消）

## MODIFIED Requirements

### Requirement: run_modeling.py入口脚本增强

原有入口脚本需要增加Packmol堆积执行模式。

#### Scenario: 新增packmol执行模式

- **WHEN** 用户指定`--mode packmol`
- **THEN** 执行Packmol堆积流程
- **AND** 输出packed_system.pdb文件

#### Scenario: 完整建模模式

- **WHEN** 用户指定`--mode full`
- **THEN** 执行完整建模流程：分子数量计算 -> Packmol堆积 -> Moltemplate拓扑构建

## REMOVED Requirements

无移除的需求。