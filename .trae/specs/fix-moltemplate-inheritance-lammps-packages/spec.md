# Moltemplate力场继承修复与LAMMPS包扩展规范

## Why

在之前的力场参数集成测试中，发现以下问题：

1. 分子模板.lt文件的力场继承机制不正确，没有正确继承OPLS-AA力场的参数
2. LAMMPS容器缺少MOLECULE和KSPACE包，无法支持完整的分子动力学模拟（键、角度、二面角拓扑和长程静电）

## What Changes

* **修复分子模板力场继承**：

  * 修改EC.lt、DMC.lt、Li.lt、PF6.lt文件，使其正确继承OPLS-AA力场

  * 使用`inherits("oplsaa")`语法实现力场参数继承

  * 确保原子类型命名符合OPLS-AA标准

* **扩展LAMMPS包**：

  * **BREAKING**：重新编译LAMMPS，添加MOLECULE、KSPACE、RIGID、OPT包

  * 更新Dockerfile以包含新的LAMMPS包

  * 验证新LAMMPS版本的功能完整性

## Impact

* Affected specs: packmol-forcefield-integration（需要重新验证力场继承）

* Affected specs: verify-lammps-gpu（需要重新验证LAMMPS包）

* Affected code:

  * 修改：`data/md_platform_data/system_templates/molecule_templates/EC/EC.lt`

  * 修改：`data/md_platform_data/system_templates/molecule_templates/DMC/DMC.lt`

  * 修改：`data/md_platform_data/system_templates/molecule_templates/LiPF6/Li.lt`

  * 修改：`data/md_platform_data/system_templates/molecule_templates/LiPF6/PF6.lt`

  * 修改：`docker/md-engine/Dockerfile`（添加LAMMPS包）

  * 新增：验证脚本确认LAMMPS包功能

## ADDED Requirements

### Requirement: Moltemplate力场继承机制

分子模板必须正确继承OPLS-AA力场参数。

#### Scenario: 使用inherits语法

* **WHEN** 定义分子模板.lt文件

* **THEN** 使用`inherits("oplsaa")`语法继承力场

* **AND** 原子类型使用OPLS-AA标准命名（如`opls_155`）

* **AND** 不需要在分子模板中重复定义通用力场参数

#### Scenario: 原子类型映射

* **WHEN** 定义分子原子

* **THEN** 每个原子类型映射到OPLS-AA力场的对应类型

* **AND** 电荷参数在分子模板中覆盖定义

* **AND** LJ参数从力场文件继承

### Requirement: LAMMPS包扩展

LAMMPS必须包含分子动力学模拟所需的所有包。

#### Scenario: MOLECULE包

* **WHEN** 运行包含键、角度、二面角的分子模拟

* **THEN** LAMMPS能够正确处理分子拓扑

* **AND** 支持bond、angle、dihedral样式

* **AND** 支持分子约束（fix shake/rattle）

#### Scenario: KSPACE包

* **WHEN** 运行包含长程静电的模拟

* **THEN** LAMMPS支持PPPM/Ewald长程静电方法

* **AND** 支持kspace\_style pppm命令

* **AND** 能够正确处理离子系统的静电相互作用

#### Scenario: RIGID包

* **WHEN** 运行包含刚体分子的模拟

* **THEN** LAMMPS支持fix rigid命令

* **AND** 能够约束分子的内部运动

#### Scenario: OPT包

* **WHEN** 运行大规模模拟

* **THEN** LAMMPS提供优化版本的计算样式

* **AND** 提升计算性能

### Requirement: Dockerfile更新

Dockerfile必须正确编译包含所有包的LAMMPS。

#### Scenario: LAMMPS编译配置

* **WHEN** 构建md-engine容器

* **THEN** LAMMPS编译时启用以下包：

  * MOLECULE：分子拓扑支持

  * KSPACE：长程静电支持

  * RIGID：刚体支持

  * OPT：性能优化

  * GPU：GPU加速（已有）

* **AND** 编译成功无错误

## MODIFIED Requirements

<br />

### Requirement: 分子模板.lt文件结构

原有分子模板需要修改为正确的力场继承结构。

#### Scenario: EC.lt修改

* **WHEN** 加载EC.lt文件

* **THEN** 文件结构包含：

  ```
  EC inherits("oplsaa") {
    # 只定义分子特定的参数
    # 电荷覆盖
    # 原子坐标
    # 键连接（类型从力场继承）
  }
  ```

#### Scenario: Li.lt修改

* **WHEN** 加载Li.lt文件

* **THEN** Li+使用Joung-Cheatham参数

* **AND** 不继承OPLS-AA（离子参数独立）

并且要重新完成packmol堆积

## REMOVED Requirements

无移除的需求。
