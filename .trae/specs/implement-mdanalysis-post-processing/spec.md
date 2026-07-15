# MDAnalysis后处理分析管线实现 Spec

## Why
LAMMPS模拟完成后产生的轨迹文件和输出数据需要经过后处理分析才能得到有意义的理化性质。MDSuite因依赖兼容性问题无法使用，需要使用MDAnalysis替代，实现完整的后处理分析管线：解析轨迹文件、计算目标理化性质（密度、电导率、粘度、介电常数、溶剂化结构）、生成标准化结果并存入数据库与文件系统。

## What Changes
- **新增Python后处理阶段脚本**：在`stages/`目录下新增`stage5_post_processing.py`，作为后处理主入口
- **新增MDAnalysis工具类**：在`utils/`目录下新增`mdanalysis_utils.py`，封装轨迹加载、坐标解包裹等通用操作
- **新增性质计算工具类**：在`utils/`目录下新增`property_calculator.py`，实现5种理化性质的GPU加速计算
- **新增后处理配置**：在`config/config.py`中添加后处理相关配置参数
- **新增Java PostProcessingService**：Java后端服务，通过DockerService在容器中执行Python后处理脚本，解析结果并存入数据库
- **更新run_modeling.py**：添加`post-processing`模式支持
- **更新stages/__init__.py**：导出后处理阶段函数

## Impact
- 受影响的功能：分子动力学轨迹后处理分析、理化性质计算
- 受影响的代码：
  - `scripts/modeling/` - 新增后处理Python脚本
  - `scripts/modeling/config/config.py` - 添加后处理配置
  - `scripts/modeling/utils/` - 新增MDAnalysis工具和性质计算器
  - `scripts/modeling/stages/` - 新增stage5
  - `scripts/modeling/run_modeling.py` - 添加post-processing模式
  - Java后端 `engine/service/` - 新增PostProcessingService
  - Java后端 `engine/controller/` - 新增后处理触发API端点

## ADDED Requirements

### Requirement: MDAnalysis轨迹解析工具
系统 SHALL 提供基于MDAnalysis的轨迹文件解析工具，支持加载LAMMPS格式的轨迹文件和拓扑文件，提取原子坐标、速度、力、电荷等信息。

#### Scenario: 成功加载LAMMPS轨迹文件
- **WHEN** 调用轨迹加载函数，传入`dump.trajectory.lammpstrj`文件路径
- **THEN** 返回MDAnalysis Universe对象，包含正确的原子数量、帧数和盒子尺寸

#### Scenario: 加载带电荷轨迹文件
- **WHEN** 调用轨迹加载函数，传入`dump.charge.lammpstrj`文件路径
- **THEN** 返回Universe对象，原子携带正确的电荷信息

#### Scenario: 坐标解包裹
- **WHEN** 对周期性边界条件下的回绕坐标执行解包裹操作
- **THEN** 原子坐标被正确还原为连续轨迹，消除周期性跳跃

### Requirement: 密度计算
系统 SHALL 从`log.lammps`文件中解析密度时间序列数据，计算平衡后的平均密度及统计误差，使用GPU加速数值计算。

#### Scenario: 成功计算密度
- **WHEN** 传入log.lammps文件路径和平衡起始比例（默认20%）
- **THEN** 返回密度均值(g/cm³)、标准差、标准误差、采样点数，收敛判断（std/mean < 5%）

#### Scenario: log.lammps格式解析
- **WHEN** log.lammps包含多阶段thermo数据（minimization/equilibrium/production）
- **THEN** 仅提取production阶段的密度数据用于计算

### Requirement: 电导率计算
系统 SHALL 使用Green-Kubo方法从电流密度自相关函数计算离子电导率，使用GPU加速矩阵运算。

#### Scenario: 成功计算电导率
- **WHEN** 传入带电荷轨迹文件路径和模拟参数（温度、体积）
- **THEN** 返回电导率值(S/m)、电导率张量(xx/yy/zz)、离子贡献比例、电阻率、收敛状态

#### Scenario: Einstein方法备用计算
- **WHEN** Green-Kubo方法积分不收敛时
- **THEN** 自动切换到Einstein关系法（MSD方法），从msd.dat文件计算电导率

### Requirement: 粘度计算
系统 SHALL 使用Green-Kubo方法从应力张量自相关函数计算剪切粘度，使用GPU加速。

#### Scenario: 成功计算粘度
- **WHEN** 传入pressure.dat文件路径和模拟参数
- **THEN** 返回粘度值(mPa·s)、剪切率、应力响应、运动粘度、收敛状态

### Requirement: 介电常数计算
系统 SHALL 使用偶极矩涨落方法从总偶极矩时间序列计算静态介电常数，使用GPU加速。

#### Scenario: 成功计算介电常数
- **WHEN** 传入dipole.dat和total_dipole.dat文件路径及模拟参数
- **THEN** 返回介电常数、介电张量、偶极矩数据、组分贡献、收敛状态

### Requirement: 溶剂化结构计算
系统 SHALL 使用MDAnalysis计算径向分布函数(RDF)，并通过积分RDF计算配位数，使用GPU加速距离计算。

#### Scenario: 成功计算RDF和配位数
- **WHEN** 传入溶剂化轨迹文件路径和原子选择条件（如Li-O对）
- **THEN** 返回RDF曲线数据、配位数、配位距离、RDF特征峰、溶剂化壳层结构

#### Scenario: 多离子对RDF
- **WHEN** 体系包含多种离子（如Li+和PF6-）
- **THEN** 自动识别中心离子和配位原子，计算所有相关离子-溶剂RDF

### Requirement: 收敛性校验
系统 SHALL 对每种性质的计算结果进行收敛性判断，标记收敛状态。

#### Scenario: 密度收敛判断
- **WHEN** 密度标准差/均值 < 5%
- **THEN** 收敛状态为CONVERGED

#### Scenario: Green-Kubo积分收敛判断
- **WHEN** 自相关函数积分在相关时间后趋于平台值
- **THEN** 收敛状态为CONVERGED，否则为NOT_CONVERGED

### Requirement: 结果标准化输出
系统 SHALL 将计算结果以标准化JSON格式输出到`post_processing/`目录，可视化数据输出到`visualization/charts/`目录。

#### Scenario: 性质结果JSON输出
- **WHEN** 某性质计算完成
- **THEN** 在`post_processing/{property}_result.json`中写入标准化结果，包含主表字段和子表字段

#### Scenario: 可视化数据输出
- **WHEN** 某性质计算完成且包含曲线数据
- **THEN** 在`visualization/charts/{type}_curve.json`中写入图表数据（如RDF曲线、MSD曲线、电导率积分曲线）

### Requirement: Java后处理服务
系统 SHALL 提供Java PostProcessingService，通过DockerService在md-engine容器中执行Python后处理脚本，解析输出结果并存入数据库。

#### Scenario: 触发后处理
- **WHEN** LAMMPS模拟完成且存在目标性质配置
- **THEN** 自动触发后处理流程，更新任务状态为POST_PROCESSING

#### Scenario: 结果存入数据库
- **WHEN** Python后处理脚本输出标准化JSON结果
- **THEN** Java服务解析JSON，写入calculation_result_table主表和对应子表

#### Scenario: 后处理失败处理
- **WHEN** Python后处理脚本执行失败
- **THEN** 记录错误日志，任务状态更新为FAILED，保留已有输出文件

### Requirement: GPU加速计算
所有性质计算 SHALL 使用GPU加速，通过CuPy或PyTorch替代NumPy进行数值运算。

#### Scenario: GPU可用时使用GPU
- **WHEN** 容器内GPU可用
- **THEN** 自动使用CuPy/PyTorch进行矩阵运算和数值积分

#### Scenario: GPU不可用时回退
- **WHEN** 容器内GPU不可用
- **THEN** 回退到NumPy/CPU计算，并记录警告日志

## MODIFIED Requirements

### Requirement: run_modeling.py入口脚本
run_modeling.py SHALL 支持`--mode post-processing`模式，接受`--target-properties`参数指定计算目标性质。

### Requirement: 任务状态扩展
SimulationJob的状态枚举 SHALL 包含`POST_PROCESSING`状态，表示任务正在执行后处理分析。

## REMOVED Requirements
无
