# Tasks

## Spring Boot部分

- [x] Task 1: 创建配方参数DTO类
  - [x] SubTask 1.1: 创建`FormulaRequest.java`（溶剂信息、锂盐信息、添加剂信息、盒子尺寸）
  - [x] SubTask 1.2: 创建`SolventInfo.java`（溶剂名称、摩尔分数）
  - [x] SubTask 1.3: 创建`SaltInfo.java`（阳离子、阴离子、浓度）
  - [x] SubTask 1.4: 创建`BoxSizeRequest.java`（尺寸或自动计算标志）
  - [x] SubTask 1.5: 添加参数校验注解（@NotNull, @Positive等）

- [x] Task 2: 创建计算结果DTO类
  - [x] SubTask 2.1: 创建`MoleculeCountResult.java`（分子名称、ID、数量、电荷）
  - [x] SubTask 2.2: 创建`BoxSizeResult.java`（盒子尺寸、体积）
  - [x] SubTask 2.3: 创建`FormulaCalculationResult.java`（聚合结果）

- [x] Task 3: 创建MoltemplateService核心服务（Spring Boot部分）
  - [x] SubTask 3.1: 创建`MoltemplateService.java`服务类
  - [x] SubTask 3.2: 实现配方参数序列化方法（写入JSON文件到任务目录）
  - [x] SubTask 3.3: 实现Python脚本调用方法
  - [x] SubTask 3.4: 实现脚本执行状态监控
  - [x] SubTask 3.5: 注入PathUtil、FileService、MoleculeTemplateRepository

- [x] Task 4: 创建建模任务Controller
  - [x] SubTask 4.1: 创建`MoltemplateController.java`
  - [x] SubTask 4.2: 实现创建建模任务接口
  - [x] SubTask 4.3: 实现查询建模任务状态接口

## Python部分

- [x] Task 5: 创建Python建模脚本目录结构
  - [x] SubTask 5.1: 创建`scripts/modeling/`目录
  - [x] SubTask 5.2: 创建`modeling.py`主模块
  - [x] SubTask 5.3: 创建`packmol_utils.py`Packmol封装
  - [x] SubTask 5.4: 创建`moltemplate_utils.py`Moltemplate封装
  - [x] SubTask 5.5: 创建`file_utils.py`文件操作封装
  - [x] SubTask 5.6: 创建`run_modeling.py`入口脚本

- [x] Task 6: 实现Python核心计算逻辑（步骤1-2）
  - [x] SubTask 6.1: 实现`calculate_molecule_counts()`函数
  - [x] SubTask 6.2: 实现`calculate_box_size()`函数
  - [x] SubTask 6.3: 实现`validate_electrical_neutrality()`函数
  - [x] SubTask 6.4: 实现`adjust_ion_counts()`函数
  - [x] SubTask 6.5: 锂盐拆分为阳离子和阴离子

- [x] Task 7: 创建Python单元测试
  - [x] SubTask 7.1: 创建`test_modeling.py`
  - [x] SubTask 7.2: 测试分子数量计算
  - [x] SubTask 7.3: 测试盒子尺寸计算
  - [x] SubTask 7.4: 测试电中性验证

## 验证

- [x] Task 8: 端到端验证
  - [x] SubTask 8.1: Python脚本单独测试（46个测试用例通过）
  - [x] SubTask 8.2: Spring Boot编译成功
  - [x] SubTask 8.3: 项目编译成功

# Task Dependencies
- Task 2 依赖 Task 1
- Task 3 依赖 Task 1, Task 2
- Task 4 依赖 Task 3
- Task 6 依赖 Task 5
- Task 7 依赖 Task 6
- Task 8 依赖 Task 1-7完成