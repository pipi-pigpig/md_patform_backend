# Tasks

- [x] Task 1: 修复6个致命Bug（阻塞性问题）
  - [x] SubTask 1.1: 修复MoltemplateSystemService脚本路径 — 将`DOCKER_SCRIPT_PATH`改为`run_modeling.py`，`buildDockerCommand`添加`--mode moltemplate-system`参数
  - [x] SubTask 1.2: 修复moltemplate_utils.py中`coords_result`未定义变量 — 从`instances_result`计算原子总数替代`coords_result["total_atoms"]`
  - [x] SubTask 1.3: 修复`validate_system_lt()`与新system.lt格式不兼容 — 移除对`system = { }`块的强制检查，改为检查导入语句和分子实例
  - [x] SubTask 1.4: 修复PackmolService `--output`参数 — 移除Python端不认识的`--output`参数，改用`--job-dir`让Python自行确定输出路径
  - [x] SubTask 1.5: 修复modeling.py `calculate_molecule_counts()`循环依赖 — 当无box_size时基于溶剂密度估算体积而非抛异常
  - [x] SubTask 1.6: 修复Dockerfile缺少Python依赖 — 添加MDAnalysis、CuPy、Jinja2的pip安装

- [x] Task 2: 修复中优先级功能性问题
  - [x] SubTask 2.1: 修复property_calculator.py O(n²)性能 — ConductivityCalculator和ViscosityCalculator的运行积分改用`cumulative_trapezoid`
  - [x] SubTask 2.2: 修复MoltemplateExecutionService `--job-dir`路径双重嵌套 — 传递任务根目录而非inputs子目录
  - [x] SubTask 2.3: 修复MDExecutorService `mergeLammpsLogFiles`命令注入风险 — 对路径添加引号转义
  - [x] SubTask 2.4: 修复MDExecutorService `markJobAsFailed` JPA脱管问题 — 通过`findById`重新获取托管实体
  - [x] SubTask 2.5: 修复PipelineService `hardwareUsed`默认值 — 在LAMMPS执行后根据GPU检测结果更新
  - [x] SubTask 2.6: 修复PipelineService状态更新竞态条件 — 使用乐观锁或直接UPDATE语句
  - [x] SubTask 2.7: 修复DockerService nvidia-smi PATH问题 — 使用完整路径`/usr/bin/nvidia-smi`
  - [x] SubTask 2.8: 修复packmol_utils.py文件句柄泄漏 — 使用try/finally确保stdin文件句柄关闭
  - [x] SubTask 2.9: 修复stage5_post_processing.py硬编码默认体积 — 从config.py读取配置

- [x] Task 3: 统一重复代码
  - [x] SubTask 3.1: 统一`convertToDockerPath()`到PathUtil — 将3个Service中的重复实现提取为PathUtil公共方法，各Service改为调用PathUtil
  - [x] SubTask 3.2: 统一`extractJsonFromOutput()`解析逻辑 — 优先使用`===JSON_RESULT===`标记，回退时增加JSON有效性验证

- [x] Task 4: 编译验证
  - [x] SubTask 4.1: 运行`mvn compile`确保所有Java修改编译通过

- [x] Task 5: 修复验证报告发现的新引入Bug（Java-Python参数对齐）
  - [x] SubTask 5.1: 在run_modeling.py的argparse中添加`--user-id`参数定义，解决3个Service传递但Python不认识的问题
  - [x] SubTask 5.2: 修复MoltemplateSystemService的`--output`参数改为`--output-file`（Python端已定义）
  - [x] SubTask 5.3: 修复MoltemplateSystemService的`--pdb-file`改为`--packed-pdb-file`（Python端moltemplate-system模式期望的参数名）
  - [x] SubTask 5.4: 在MoltemplateSystemService的buildDockerCommand中添加缺失的`--template-library-path`参数（Python端必需参数）
  - [x] SubTask 5.5: 编译验证

# Task Dependencies
- [Task 1] 必须最先执行（致命Bug阻塞性最高）
- [Task 2] 依赖 [Task 1] 完成后执行
- [Task 3] 可与 [Task 2] 并行执行
- [Task 4] 必须在所有Java修改完成后执行
