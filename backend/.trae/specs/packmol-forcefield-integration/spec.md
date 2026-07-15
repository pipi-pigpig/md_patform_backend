# Packmol堆积力场参数集成规范

## Why

用户已经为分子模板库中的所有分子（EC、DMC、Li、PF6）添加了完整的OPLS-AA力场参数，包括原子电荷、键参数、角度参数、二面角参数和LJ参数。现在需要验证和更新packmol堆积功能，使其能够正确使用这些力场参数进行分子堆积，并确保堆积后的构型能够被Moltemplate正确处理。

<br />

## What Changes

你记得是我本地运行backend操纵docker容器里的packmol工具等，python脚本也是在本地，然后容器运行对吧

* **验证力场参数完整性**：

  * 检查EC.lt、DMC.lt、Li.lt、PF6.lt文件的力场参数是否完整

  * 验证原子电荷总和是否正确（电中性）

  * 验证键参数、角度参数、LJ参数是否齐全

* **更新packmol\_utils.py**：

  * 确保Packmol能够正确读取.lt文件中的PDB坐标

  * 增加力场参数验证功能

  * 增加堆积后构型与力场参数的匹配验证

* **创建集成测试**：

  * 使用真实力场参数进行完整的Packmol堆积测试

  * 验证堆积后的packed\_system.pdb能够被Moltemplate正确处理

  * 验证LAMMPS能够使用生成的system.data运行模拟

* **更新文档**：

  * 更新分子模板使用说明

  * 更新力场参数来源说明

## Impact

* Affected specs: packmol-packing-step3（需要验证其与新力场参数的兼容性）

* Affected specs: moltemplate-steps1-2（需要验证Moltemplate能正确处理新力场参数）

* Affected code:

  * 修改：`scripts/modeling/utils/packmol_utils.py`（增加力场参数验证）

  * 修改：`scripts/modeling/utils/moltemplate_utils.py`（确保与新.lt文件兼容）

  * 新增：`scripts/tests/integration/test_packmol_forcefield.py`（力场参数集成测试）

  * 新增：`data/md_platform_data/system_templates/force_fields/opls-aa/oplsaa.lt`（主力场文件）

## ADDED Requirements

### Requirement: 力场参数完整性验证

系统必须能够验证分子模板的力场参数完整性。

#### Scenario: 验证原子电荷

* **WHEN** 加载分子模板.lt文件

* **THEN** 验证原子电荷总和是否等于分子净电荷

* **AND** EC分子电荷总和应为0（电中性）

* **AND** Li+电荷应为+1.0

* **AND** PF6-电荷应为-1.0

#### Scenario: 验证键参数

* **WHEN** 加载分子模板.lt文件

* **THEN** 验证所有键都有对应的力场参数

* **AND** 键参数包含力常数K和平衡距离r0

#### Scenario: 验证LJ参数

* **WHEN** 加载分子模板.lt文件

* **THEN** 验证所有原子类型都有LJ参数（epsilon和sigma）

* **AND** LJ参数符合OPLS-AA标准

### Requirement: Packmol堆积与力场参数匹配

系统必须确保Packmol堆积后的构型能够与力场参数正确匹配。

#### Scenario: 堆积构型原子类型匹配

* **WHEN** Packmol生成packed\_system.pdb文件

* **THEN** 每个原子的坐标必须与.lt文件中的原子类型定义匹配

* **AND** Moltemplate能够正确识别每个原子的力场参数

#### Scenario: 堆积构型残基信息

* **WHEN** Packmol生成packed\_system.pdb文件

* **THEN** 每个分子的残基名称必须与.lt文件中的分子名称一致

* **AND** 残基编号必须正确分配

### Requirement: 完整流程集成测试

系统必须提供完整的Packmol+Moltemplate+LAMMPS流程测试。

#### Scenario: Packmol堆积测试

* **WHEN** 使用真实力场参数进行Packmol堆积

* **THEN** packed\_system.pdb文件成功生成

* **AND** 原子总数与预期一致

* **AND** 无原子重叠

#### Scenario: Moltemplate拓扑构建测试

* **WHEN** 使用packed\_system.pdb和.lt文件进行Moltemplate处理

* **THEN** system.data文件成功生成

* **AND** 包含完整的力场参数

* **AND** 包含正确的拓扑信息

#### Scenario: LAMMPS模拟测试

* **WHEN** 使用system.data运行LAMMPS能量最小化

* **THEN** LAMMPS能够正确读取文件

* **AND** 能量计算正常

* **AND** 无错误输出

### Requirement: 力场文件集成

系统必须提供完整的OPLS-AA力场文件。

#### Scenario: 力场文件结构

* **WHEN** 加载OPLS-AA力场

* **THEN** 包含以下文件：

  * `oplsaa.lt`：主力场入口文件

  * `ffbonded.lt`：键参数文件

  * `ffnonbonded.lt`：非键参数文件

#### Scenario: 力场参数来源

* **WHEN** 使用力场参数

* **THEN** 参数来源明确标注：

  * EC: J. Phys. Chem. B 2004, 108, 203

  * Li+: Joung-Cheatham (J. Phys. Chem. B 2006)

  * PF6-: J. Phys. Chem. B 2006

## MODIFIED Requirements

### Requirement: packmol\_utils.py增强

原有packmol\_utils.py需要增加力场参数验证功能。

#### Scenario: 力场参数验证方法

* **WHEN** 执行Packmol堆积前

* **THEN** 验证所有分子模板的力场参数完整性

* **AND** 如果参数缺失，返回错误信息

#### Scenario: 堆积结果验证增强

* **WHEN** Packmol堆积完成

* **THEN** 验证packed\_system.pdb与力场参数的匹配性

* **AND** 验证原子类型分配正确

## REMOVED Requirements

无移除的需求。
