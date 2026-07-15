# Jinja2动态脚本生成与Packmol坐标替换 Spec

## Why

当前平台存在两个关键缺陷：
1. LAMMPS输入脚本（in.minimization/equilibrium/production）是静态硬编码的，没有使用Jinja2模板引擎根据用户勾选的计算性质动态生成。设计文档4.5节已规划了完整的Jinja2模板架构，但尚未实现。
2. Moltemplate执行时未使用Packmol生成的packed_system.pdb坐标，导致所有分子初始坐标在原点附近，原子重叠，能量最小化极慢。

## What Changes

- **完善Jinja2模板文件**：将3个现有模板（in.minimization.j2、in.equilibrium.j2、in.production.j2）扩展为设计文档要求的完整版本，新增5个计算配置模板（compute_density.j2等）
- **实现LammpsTemplateService**：创建Jinja2模板渲染服务，根据target_properties动态生成LAMMPS输入脚本
- **修复Moltemplate坐标替换**：在执行moltemplate.sh时添加`-pdb packed_system.pdb`参数，使Moltemplate自动从PDB文件读取坐标写入system.data
- **修复system.in.init**：moltemplate生成的system.in.init缺少atom_style full和kspace_style，需要在文件整理步骤中自动修复

## Impact

- Affected specs: moltemplate-execution-steps6-7（步骤6-7需要修改）
- Affected code:
  - 新增：`data/md_platform_data/system_templates/lammps_templates/compute_*.j2`（5个计算配置模板）
  - 修改：`data/md_platform_data/system_templates/lammps_templates/in.*.j2`（3个现有模板扩展）
  - 新增：`backend/src/main/java/com/mdplatform/engine/service/LammpsTemplateService.java`
  - 修改：`backend/src/main/java/com/mdplatform/engine/service/MoltemplateExecutionService.java`（添加-pdb参数）
  - 修改：`backend/src/main/resources/scripts/modeling/utils/moltemplate_utils.py`（添加-pdb参数和init文件修复）

## ADDED Requirements

### Requirement: Jinja2动态LAMMPS脚本生成

系统必须根据用户勾选的计算性质（target_properties），使用Jinja2模板引擎动态生成LAMMPS输入脚本。

#### Scenario: 根据target_properties生成生产模拟脚本

- **WHEN** 用户勾选了density和conductivity性质
- **THEN** 生成的in.production脚本中自动包含：
  - density所需的thermo_style custom ... density输出
  - conductivity所需的compute property/atom charge和dump charge轨迹
- **AND** 未勾选的性质不会生成对应的计算命令

#### Scenario: 生成能量最小化脚本

- **WHEN** 调用LammpsTemplateService生成最小化脚本
- **THEN** 脚本包含完整的初始化设置（atom_style full、力场样式等）
- **AND** 包含read_data system.data
- **AND** 包含min_style cg和minimize命令
- **AND** 输出minimized.data

#### Scenario: 生成平衡模拟脚本

- **WHEN** 调用LammpsTemplateService生成平衡脚本
- **THEN** 脚本包含完整的初始化设置
- **AND** 包含velocity初始化
- **AND** 包含NVT和NPT平衡阶段
- **AND** 输出equilibrated.data

### Requirement: Moltemplate使用Packmol坐标

系统必须在执行Moltemplate时使用Packmol生成的packed_system.pdb坐标，避免原子重叠。

#### Scenario: 执行Moltemplate时使用PDB坐标

- **WHEN** 执行moltemplate.sh命令
- **AND** packed_system.pdb文件存在
- **THEN** 命令包含`-pdb packed_system.pdb`参数
- **AND** 生成的system.data文件包含Packmol堆积后的3D坐标
- **AND** 原子不再全部在原点附近

#### Scenario: packed_system.pdb不存在时回退

- **WHEN** 执行moltemplate.sh命令
- **AND** packed_system.pdb文件不存在
- **THEN** 不添加-pdb参数，使用模板默认坐标
- **AND** 记录警告日志

### Requirement: 自动修复system.in.init

系统必须在文件整理步骤中自动修复moltemplate生成的system.in.init文件。

#### Scenario: 修复缺失的atom_style和kspace_style

- **WHEN** moltemplate执行完成
- **THEN** 检查system.in.init是否包含atom_style full
- **AND** 如果缺失，添加atom_style full
- **AND** 检查是否包含kspace_style
- **AND** 如果缺失，添加kspace_style pppm 1.0e-4
- **AND** 去除重复的力场样式定义

## MODIFIED Requirements

### Requirement: Moltemplate命令执行

Moltemplate命令执行必须支持-pdb参数。

#### Scenario: 构建包含-pdb参数的命令

- **WHEN** packed_system.pdb存在
- **THEN** 执行命令：`moltemplate.sh -atomstyle full -pdb packed_system.pdb system.lt`
- **AND** Moltemplate自动从PDB文件读取坐标

## REMOVED Requirements

无移除的需求。