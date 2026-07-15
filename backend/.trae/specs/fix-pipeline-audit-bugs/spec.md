# 修复全流程代码审查报告问题 Spec

## Why
全流程代码审查报告发现了6个致命Bug、多个中/低优先级问题，导致整个计算流水线无法端到端运行。需要修复所有阻断性Bug和关键功能问题，使平台能够从用户提交任务到最终产出计算结果完整运行。

## What Changes
- 修复MoltemplateSystemService脚本路径错误（指向不存在的文件）
- 修复moltemplate_utils.py中`coords_result`未定义变量和`validate_system_lt()`与新格式不兼容
- 修复PackmolService传递Python端不认识的`--output`参数
- 修复modeling.py中`calculate_molecule_counts()`对box_size的循环依赖
- 修复Dockerfile缺少MDAnalysis/CuPy/PyTorch/Jinja2依赖
- 修复property_calculator.py中ConductivityCalculator/ViscosityCalculator的O(n²)性能问题
- 修复MoltemplateExecutionService的`--job-dir`路径双重嵌套问题
- 修复MDExecutorService中mergeLammpsLogFiles命令注入风险和markJobAsFailed JPA脱管问题
- 修复PipelineService中hardwareUsed默认值和状态更新竞态条件
- 修复DockerService中nvidia-smi PATH问题
- 修复stage5_post_processing.py中硬编码默认体积
- 修复packmol_utils.py中文件句柄泄漏
- 统一extractJsonFromOutput解析逻辑
- 统一convertToDockerPath到PathUtil

## Impact
- Affected specs: moltemplate-steps1-2, packmol-packing-step3, moltemplate-system-generation-step4, moltemplate-execution-steps6-7, jinja2-lammps-scripts-and-pdb-coords, lammps-multi-stage-execution, implement-mdanalysis-post-processing, automated-full-pipeline
- Affected code:
  - Java: MoltemplateSystemService, PackmolService, MoltemplateExecutionService, MDExecutorService, PipelineService, DockerService, PathUtil
  - Python: moltemplate_utils.py, modeling.py, property_calculator.py, packmol_utils.py, stage5_post_processing.py
  - Docker: Dockerfile

## ADDED Requirements

### Requirement: 脚本路径一致性
所有Java Service调用Python脚本时，必须使用统一的入口脚本`run_modeling.py`配合`--mode`参数，禁止指向单独的Python脚本文件。

#### Scenario: MoltemplateSystemService脚本调用
- **WHEN** MoltemplateSystemService需要调用moltemplate系统生成功能
- **THEN** 必须调用`run_modeling.py --mode moltemplate-system`，而非不存在的`run_moltemplate_system.py`

### Requirement: moltemplate_utils.py变量完整性
`generate_system_lt_file()`函数中所有引用的变量必须在函数内定义，禁止引用未定义变量。

#### Scenario: coords_result变量
- **WHEN** `generate_system_lt_file()`需要原子总数信息
- **THEN** 必须从`instances_result`或`molecule_counts`中计算，而非引用不存在的`coords_result`

#### Scenario: validate_system_lt兼容性
- **WHEN** 验证新生成的system.lt文件
- **THEN** 验证逻辑必须兼容不使用`system = { }`包裹的格式，检查导入语句和分子实例定义即可

### Requirement: Java-Python参数对齐
Java端构建的Docker命令参数必须与Python端`run_modeling.py`的argparse定义完全一致。

#### Scenario: Packmol参数
- **WHEN** Java端调用Packmol模式
- **THEN** 只传递Python端定义的参数（`--mode packmol`、`--formula-file`、`--job-dir`），禁止传递未定义的`--output`

### Requirement: 分子数量计算独立性
`calculate_molecule_counts()`必须能够在没有box_size的情况下独立完成计算。

#### Scenario: 无box_size计算
- **WHEN** 配方中没有预设box_size
- **THEN** 应基于溶剂密度和配方组成估算体积，而非抛出异常

### Requirement: Docker依赖完整性
Dockerfile必须安装所有Python脚本依赖的库。

#### Scenario: 后处理依赖
- **WHEN** 构建md_engine Docker镜像
- **THEN** 必须安装MDAnalysis、CuPy（或PyTorch）、Jinja2

### Requirement: 后处理性能
ConductivityCalculator和ViscosityCalculator的运行积分计算必须使用O(n)算法。

#### Scenario: GK积分计算
- **WHEN** 计算Green-Kubo运行积分
- **THEN** 必须使用`scipy.integrate.cumulative_trapezoid`替代O(n²)的for循环

### Requirement: 路径传递正确性
Java端传递给Python端的路径参数必须与Python端期望的语义一致。

#### Scenario: --job-dir参数
- **WHEN** 传递`--job-dir`参数给Python脚本
- **THEN** 必须传递任务根目录，而非inputs子目录

### Requirement: JPA实体线程安全
异步线程中修改JPA实体时，必须重新获取托管实体。

#### Scenario: markJobAsFailed
- **WHEN** 在异步线程中标记任务失败
- **THEN** 必须通过`findById`重新获取最新托管实体再修改保存

### Requirement: 状态更新原子性
PipelineService的状态更新必须避免竞态条件。

#### Scenario: 并发状态更新
- **WHEN** 多个线程同时更新SimulationJob状态
- **THEN** 必须使用乐观锁或直接UPDATE语句，避免丢失更新

### Requirement: GPU检测健壮性
Docker容器内GPU检测必须使用完整路径。

#### Scenario: nvidia-smi检测
- **WHEN** 在Docker容器内检测GPU
- **THEN** 必须使用`/usr/bin/nvidia-smi`完整路径

### Requirement: 文件句柄安全
subprocess调用中打开的文件句柄必须在finally块中关闭。

#### Scenario: Packmol文件句柄
- **WHEN** PackmolRunner执行subprocess
- **THEN** stdin文件句柄必须使用try/finally确保关闭

### Requirement: 配置化硬编码值
后处理中的默认体积等硬编码值必须从配置读取。

#### Scenario: 默认体积
- **WHEN** 后处理无法从轨迹提取盒子尺寸
- **THEN** 必须从config.py的PostProcessingConfig读取默认体积

## MODIFIED Requirements

### Requirement: convertToDockerPath统一
将PackmolService、MoltemplateService、MoltemplateExecutionService中重复的`convertToDockerPath()`方法统一到PathUtil中。

### Requirement: extractJsonFromOutput统一
统一所有Service中的JSON输出解析逻辑，优先使用`===JSON_RESULT===`标记，回退时增加JSON有效性验证。

## REMOVED Requirements
无
