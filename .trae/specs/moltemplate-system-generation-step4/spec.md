# Moltemplate系统文件生成（步骤4）实现规范

## Why

Moltemplate系统文件生成是自动化建模流程的关键步骤，需要根据Packmol堆积生成的packed_system.pdb文件，加载分子模板文件，创建分子实例，并生成system.lt系统描述文件，为后续的LAMMPS模拟提供拓扑结构和力场参数。

## What Changes

- **Python部分**：
  - 增强`moltemplate_utils.py`模块，实现PDB文件解析功能
  - 实现分子模板文件加载功能，从分子模板库获取.lt文件
  - 实现分子实例创建功能，根据PDB坐标创建分子实例
  - 实现system.lt文件生成功能，包含分子实例定义和力场继承
  - 实现分子ID映射和坐标提取功能

- **Spring Boot部分**：
  - 创建`MoltemplateSystemService.java`服务类，封装系统文件生成逻辑
  - 创建`MoltemplateSystemResult.java`结果DTO类
  - 实现与Python脚本的集成调用

## Impact

- Affected specs: packmol-packing-step3（依赖其输出的packed_system.pdb文件）
- Affected specs: moltemplate-steps1-2（依赖其输出的分子数量计算结果）
- Affected specs: implement-file-system-rules（需要使用PathUtil生成文件路径）
- Affected code:
  - 新增：`engine/service/MoltemplateSystemService.java`
  - 新增：`engine/dto/MoltemplateSystemResult.java`
  - 修改：`scripts/modeling/utils/moltemplate_utils.py`
  - 修改：`scripts/modeling/run_modeling.py`

## ADDED Requirements

### Requirement: PDB文件解析（步骤4.1）

系统必须能够解析Packmol生成的packed_system.pdb文件，提取分子坐标和分子ID信息。

#### Scenario: 解析PDB文件

- **WHEN** 给定packed_system.pdb文件路径
- **THEN** 解析PDB文件，提取以下信息：
  - 原子坐标（ATOM/HETATM记录）
  - 分子ID（通过residue number或chain ID识别）
  - 原子类型和元素信息
  - 分子边界识别（通过TER记录或residue变化）

#### Scenario: 分子ID映射

- **WHEN** 解析PDB文件时
- **THEN** 将每个分子实例映射到唯一的分子ID（molecule instance ID）
- **AND** 分子ID格式：`{分子名称}_{实例序号}`（如`EC_1`, `Li_50`）
- **AND** 分子实例序号从1开始递增

#### Scenario: 分子边界识别

- **WHEN** 解析PDB文件时
- **THEN** 通过以下方式识别分子边界：
  - TER记录分隔符
  - residue number变化
  - chain ID变化
- **AND** 每个分子实例包含完整的原子集合

### Requirement: 分子模板文件加载（步骤4.2）

系统必须能够从分子模板库加载分子模板文件（.lt文件）。

#### Scenario: 加载分子模板

- **WHEN** 给定分子名称列表
- **THEN** 从`molecule_template_table`查询所有分子的.lt文件路径
- **AND** 将所有.lt文件复制到工作目录的inputs目录
- **AND** 验证.lt文件格式正确性

#### Scenario: 分子模板缺失

- **WHEN** 分子库中无对应分子模板
- **THEN** 返回错误提示，建议用户上传自定义分子模板
- **AND** 记录缺失的分子名称列表

#### Scenario: 力场继承

- **WHEN** 加载分子模板时
- **THEN** 继承力场参数（OPLS-AA、GAFF等）
- **AND** 确保力场参数文件（如oplsaa.lt）存在于模板目录

### Requirement: 分子实例创建（步骤4.3）

系统必须能够根据PDB坐标创建分子实例。

#### Scenario: 创建分子实例

- **WHEN** 给定分子名称、分子数量和PDB坐标
- **THEN** 为每个分子实例创建坐标数据：
  - 分子实例名称：`{分子名称}_{实例序号}`
  - 原子坐标列表：从PDB文件提取
  - 分子类型引用：指向分子模板

#### Scenario: 坐标提取

- **WHEN** 创建分子实例时
- **THEN** 从PDB文件提取每个分子的原子坐标
- **AND** 坐标格式：`$atom:{原子名称} @atom:{原子类型} {x} {y} {z}`
- **AND** 坐标单位：Å（埃）

#### Scenario: 分子实例数量验证

- **WHEN** 创建分子实例时
- **THEN** 验证分子实例数量与配方计算结果一致
- **AND** 如果不一致，返回错误信息

### Requirement: system.lt文件生成（步骤4.4）

系统必须能够生成完整的system.lt系统描述文件。

#### Scenario: 生成system.lt文件

- **WHEN** 给定分子模板列表、分子实例列表和盒子尺寸
- **THEN** 生成system.lt文件，包含以下内容：
```
# 分子模板导入
import "oplsaa.lt"  # 力场文件
import "EC.lt"      # 分子模板
import "DMC.lt"
import "Li.lt"
import "PF6.lt"

# 系统定义
system = {
  # 分子实例创建
  ECs = new EC[200]
  DMCs = new DMC[200]
  Lis = new Li[50]
  PF6s = new PF6[50]

  # 分子坐标（从PDB文件提取）
  # EC_1 分子实例
  $atom:EC_1:C1  @atom:opls_145  10.0  20.0  30.0
  $atom:EC_1:C2  @atom:opls_146  11.0  21.0  31.0
  ...

  # 盒子尺寸
  write_once("Data Boundary") {
    0.0  48.0  xlo xhi
    0.0  48.0  ylo yhi
    0.0  48.0  zlo zhi
  }
}
```

#### Scenario: 分子实例命名规范

- **WHEN** 生成system.lt文件时
- **THEN** 分子实例命名遵循规范：
  - 溶剂分子：`{分子名}s`（如`ECs`, `DMCs`）
  - 离子：`{离子名}s`（如`Lis`, `PF6s`）
  - 实例数组：`new {分子名}[数量]`

#### Scenario: 坐标数据格式

- **WHEN** 写入分子坐标时
- **THEN** 使用Moltemplate的write函数：
```
write("Data Atoms") {
  $atom:EC_1:C1  @atom:opls_145  0.0  10.0  20.0  30.0
  $atom:EC_1:C2  @atom:opls_146  0.0  11.0  21.0  31.0
  ...
}
```
- **AND** 格式：`$atom:{分子ID}:{原子名} @atom:{原子类型} {电荷} {x} {y} {z}`

### Requirement: 输出文件验证

系统必须验证生成的system.lt文件有效性。

#### Scenario: 文件验证

- **WHEN** system.lt文件生成完成
- **THEN** 验证以下内容：
  - 文件存在且大小>0
  - 包含所有分子模板导入语句
  - 包含所有分子实例定义
  - 包含盒子尺寸定义
  - 分子实例数量与配方一致

#### Scenario: 输出结果格式

- **WHEN** 系统文件生成完成
- **THEN** 输出结果包含：
```json
{
  "success": true,
  "system_file_path": "user_{userId}/jobs/job_{jobId}/inputs/system.lt",
  "molecule_templates": ["EC.lt", "DMC.lt", "Li.lt", "PF6.lt"],
  "molecule_instances": {
    "EC": 200,
    "DMC": 200,
    "Li": 50,
    "PF6": 50
  },
  "total_atoms": 1234,
  "box_size": {"x": 48.0, "y": 48.0, "z": 48.0},
  "forcefield": "oplsaa",
  "error_message": ""
}
```

### Requirement: 异常处理

系统必须处理系统文件生成过程中的异常情况。

#### Scenario: PDB文件解析失败

- **WHEN** packed_system.pdb文件格式错误或损坏
- **THEN** 返回详细的错误信息，指出解析失败的位置
- **AND** 建议检查Packmol堆积结果

#### Scenario: 分子模板不匹配

- **WHEN** PDB文件中的分子名称与模板库分子名称不一致
- **THEN** 返回错误信息，列出不匹配的分子名称
- **AND** 建议用户检查分子命名规范

#### Scenario: 坐标提取失败

- **WHEN** 无法从PDB文件提取完整的分子坐标
- **THEN** 返回错误信息，指出缺失的分子实例
- **AND** 记录缺失的原子数量

## MODIFIED Requirements

### Requirement: run_modeling.py入口脚本增强

原有入口脚本需要增加system.lt生成执行模式。

#### Scenario: 新增moltemplate-system执行模式

- **WHEN** 用户指定`--mode moltemplate-system`
- **THEN** 执行system.lt文件生成流程
- **AND** 输出system.lt文件路径

#### Scenario: 完整建模模式增强

- **WHEN** 用户指定`--mode full`
- **THEN** 执行完整建模流程：分子数量计算 -> Packmol堆积 -> system.lt生成

## REMOVED Requirements

无移除的需求。