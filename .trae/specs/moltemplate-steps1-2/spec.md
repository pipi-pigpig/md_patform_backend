# Moltemplate自动化建模步骤1-2实现规范

<br />

前置要求我忘记更你说了，请你使用Spring Boot + Python 混合架构

**Spring Boot 部分**：

* 编写`ElectrolyteSystemService`，提供根据 ID 查询配方的方法
* 编写`MoleculeTemplateService`，提供根据 ID 查询分子模板的方法
* 编写任务创建接口，接收用户的配方参数，保存到数据库
* 创建任务目录，将配方参数和分子模板信息序列化为 JSON 文件写入任务目录
* 调用 Python 建模脚本，传递任务 ID 和任务目录路径
* 监控脚本执行状态，更新任务状态

&#x20;

* **Python 部分**：
  * 编写`modeling.py`模块，实现 4.2 节的 6 个步骤
  * 编写`packmol_utils.py`，封装 Packmol 调用逻辑
  * 编写`moltemplate_utils.py`，封装 Moltemplate 调用逻辑
  * 编写`file_utils.py`，封装文件操作逻辑
  * 编写单元测试，验证每个函数的正确性
  * 编写入口脚本`run_modeling.py`，接收命令行参数，执行建模流程
* **验证方式**：
  * 先在 Python 中单独测试建模流程
  * 再在 Spring Boot 中调用 Python 脚本，测试端到端的流程

## Why

Moltemplate自动化建模是计算引擎的核心功能，需要根据用户输入的电解液配方参数，自动计算分子数量和盒子尺寸，为后续的Packmol堆积和Moltemplate建模提供基础数据。

## What Changes

* 创建配方参数DTO类：`FormulaRequest.java`

* 创建分子数量计算结果DTO类：`MoleculeCountResult.java`

* 创建盒子尺寸计算结果DTO类：`BoxSizeResult.java`

* 创建Moltemplate建模服务类：`MoltemplateService.java`

* 实现核心计算逻辑：分子数量计算、盒子尺寸计算、电中性验证

## Impact

* Affected specs: implement-file-system-rules（需要使用PathUtil）

* Affected code:

  * 新增：`engine/dto/FormulaRequest.java`

  * 新增：`engine/dto/MoleculeCountResult.java`

  * 新增：`engine/dto/BoxSizeResult.java`

  * 新增：`engine/service/MoltemplateService.java`

## ADDED Requirements

### Requirement: 配方参数接收（步骤1）

系统必须能够接收电解液配方参数，包括溶剂信息、锂盐信息、添加剂信息和盒子尺寸。

#### Scenario: 接收配方参数

* **WHEN** 用户提交电解液配方

* **THEN** 系统接收以下参数：

  * `solvent_info`: 混合溶剂信息，格式 `[{"name":"EC","mole_fraction":0.5},{"name":"DMC","mole_fraction":0.5}]`

  * `salt_info`: 锂盐信息，格式 `{"cation":"Li","anion":"PF6","concentration":1.0}`

  * `additive_info`: 添加剂信息（可选）

  * `box_size`: 模拟盒子尺寸，格式 `{"x":40.0,"y":40.0,"z":40.0}` 或 `{"auto":true}`

  * `temperature`: 目标温度（K）

### Requirement: 分子数量计算（步骤2）

系统必须根据配方参数计算各分子的具体数量。

#### Scenario: 计算盒子体积

* **WHEN** 给定盒子尺寸

* **THEN** 计算盒子体积 V = box\_x × box\_y × box\_z (Å³)

#### Scenario: 估算溶剂总摩尔数

* **WHEN** 给定盒子体积

* **THEN** 根据电解液典型密度（约1.2 g/cm³）估算溶剂总摩尔数

* **AND** 公式：`总摩尔数 = (体积 × 密度) / 平均分子量`

#### Scenario: 计算各溶剂分子数

* **WHEN** 给定溶剂摩尔分数

* **THEN** 根据摩尔比计算各溶剂分子数

* **AND** 公式：`分子数 = 总摩尔数 × 摩尔分数 × 阿伏伽德罗常数`

#### Scenario: 计算锂盐分子数

* **WHEN** 给定锂盐浓度

* **THEN** 根据盐浓度计算锂盐分子数

* **AND** 锂盐拆分为阳离子和阴离子分别计算数量

#### Scenario: 验证电中性

* **WHEN** 计算完所有分子数量

* **THEN** 验证体系电中性：总正电荷 = 总负电荷

* **AND** 如果不平衡，自动调整离子数量

### Requirement: 盒子尺寸自动计算

当用户选择自动计算盒子尺寸时，系统必须根据分子数量和密度计算合适的盒子尺寸。

#### Scenario: 自动计算盒子尺寸

* **WHEN** 用户选择自动计算盒子尺寸（`box_size.auto=true`）

* **THEN** 根据各分子的分子量和密度，计算模拟盒子的体积

* **AND** 计算盒子边长（假设为立方体）

* **AND** 盒子尺寸适当放大（放大系数1.2-1.5），避免Packmol堆积问题

### Requirement: 输出结果格式

系统必须输出包含分子ID、数量、盒子尺寸的字典。

#### Scenario: 输出分子数量结果

* **WHEN** 计算完成

* **THEN** 输出格式：

```json
{
  "molecules": [
    {"name": "EC", "moleculeId": 1, "count": 200, "charge": 0},
    {"name": "DMC", "moleculeId": 2, "count": 200, "charge": 0},
    {"name": "Li", "moleculeId": 3, "count": 50, "charge": 1},
    {"name": "PF6", "moleculeId": 4, "count": 50, "charge": -1}
  ],
  "boxSize": {"x": 48.0, "y": 48.0, "z": 48.0},
  "totalAtoms": 1234,
  "totalCharge": 0,
  "isElectricallyNeutral": true
}
```

### Requirement: 异常处理

系统必须处理配方参数异常情况。

#### Scenario: 分子模板缺失

* **WHEN** 分子库中无对应分子模板

* **THEN** 返回错误提示，建议用户上传自定义分子

#### Scenario: 电荷不平衡

* **WHEN** 体系总电荷不为零

* **THEN** 自动调整离子数量或返回错误提示

#### Scenario: 参数校验失败

* **WHEN** 输入参数不合法（如负数、空值）

* **THEN** 返回详细的参数校验错误信息

