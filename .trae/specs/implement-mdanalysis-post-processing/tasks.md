# Tasks

- [x] 任务1：扩展配置模块，添加后处理相关配置参数
  - [x] 子任务1.1：在`config/config.py`中添加`PostProcessingConfig`数据类，包含：平衡阶段排除比例（默认0.2）、Green-Kubo相关时间参数、RDF计算参数（bins数、范围）、收敛阈值、GPU加速开关
  - [x] 子任务1.2：在`config/config.py`的`Config`类中添加`post_processing`字段，默认使用`PostProcessingConfig`
  - [x] 子任务1.3：更新`config/__init__.py`导出新配置

- [x] 任务2：实现MDAnalysis轨迹解析工具类
  - [x] 子任务2.1：创建`utils/mdanalysis_utils.py`，实现`TrajectoryLoader`类
  - [x] 子任务2.2：实现`load_lammps_trajectory()`方法 - 加载dump.lammpstrj轨迹文件，返回MDAnalysis Universe对象
  - [x] 子任务2.3：实现`load_lammps_trajectory_with_charges()`方法 - 加载dump.charge.lammpstrj带电荷轨迹
  - [x] 子任务2.4：实现`unwrap_coordinates()`方法 - 基于MDAnalysis的周期性边界条件坐标解包裹
  - [x] 子任务2.5：实现`extract_box_dimensions()`方法 - 从轨迹中提取盒子尺寸
  - [x] 子任务2.6：实现`get_atom_selection()`方法 - 按原子类型选择原子组
  - [x] 子任务2.7：更新`utils/__init__.py`导出新工具类

- [x] 任务3：实现GPU加速计算工具
  - [x] 子任务3.1：在`utils/mdanalysis_utils.py`中实现`GPUBackend`类，封装CuPy/PyTorch GPU运算
  - [x] 子任务3.2：实现`auto_select_backend()`方法 - 自动检测GPU可用性，选择CuPy > PyTorch > NumPy回退
  - [x] 子任务3.3：实现GPU加速的自相关函数计算`gpu_autocorrelation()`
  - [x] 子任务3.4：实现GPU加速的数值积分`gpu_trapezoid_integral()`
  - [x] 子任务3.5：实现GPU加速的距离计算`gpu_pairwise_distances()`

- [x] 任务4：实现性质计算器
  - [x] 子任务4.1：创建`utils/property_calculator.py`，实现`DensityCalculator`类 - 从log.lammps解析密度时间序列，计算均值/标准差/标准误差，收敛判断（std/mean < 5%）
  - [x] 子任务4.2：实现`ConductivityCalculator`类 - Green-Kubo方法从电流密度自相关函数计算电导率，支持Einstein方法备用，输出电导率张量和离子贡献
  - [x] 子任务4.3：实现`ViscosityCalculator`类 - Green-Kubo方法从应力张量自相关函数计算剪切粘度，输出粘度值和运动粘度
  - [x] 子任务4.4：实现`DielectricCalculator`类 - 偶极矩涨落方法计算静态介电常数，输出介电张量和组分贡献
  - [x] 子任务4.5：实现`SolvationCalculator`类 - MDAnalysis计算RDF，积分计算配位数，输出RDF曲线和溶剂化壳层信息
  - [x] 子任务4.6：实现`ConvergenceChecker`类 - 统一的收敛性校验逻辑

- [x] 任务5：实现后处理阶段脚本
  - [x] 子任务5.1：创建`stages/stage5_post_processing.py`，实现6步后处理流程：读取任务配置→加载输出文件→轨迹预处理→分性质计算→收敛性校验→结果标准化输出
  - [x] 子任务5.2：实现`run_post_processing()`主函数，接受work_dir、output_dir、target_properties、temperature等参数
  - [x] 子任务5.3：实现结果标准化输出函数 - 将计算结果写入`post_processing/{property}_result.json`
  - [x] 子任务5.4：实现可视化数据输出函数 - 将曲线数据写入`visualization/charts/{type}_curve.json`
  - [x] 子任务5.5：更新`stages/__init__.py`导出后处理函数

- [x] 任务6：更新run_modeling.py入口脚本
  - [x] 子任务6.1：添加`--mode post-processing`命令行参数支持
  - [x] 子任务6.2：添加`--target-properties`参数传递
  - [x] 子任务6.3：添加`--temperature`和`--time-step-fs`参数
  - [x] 子任务6.4：实现post-processing模式的调用逻辑

- [x] 任务7：实现Java PostProcessingService
  - [x] 子任务7.1：创建`PostProcessingService.java`，注入DockerService、CalculationResultService、FileService、SimulationService
  - [x] 子任务7.2：实现`executePostProcessing(Long jobId)`方法 - 构建Python命令，通过DockerService在容器中执行
  - [x] 子任务7.3：实现`parseAndStoreResults(Long jobId)`方法 - 读取post_processing/目录下的JSON结果文件，解析并存入数据库
  - [x] 子任务7.4：实现`storeMainResult()`和`storeSubTableResult()` - 分别写入calculation_result_table主表和子表
  - [x] 子任务7.5：实现错误处理和日志记录

- [x] 任务8：实现Java后处理API端点
  - [x] 子任务8.1：在`CalculationResultController.java`或新建`PostProcessingController.java`中添加触发后处理的API端点
  - [x] 子任务8.2：实现`POST /api/results/job/{jobId}/post-processing`端点 - 手动触发后处理
  - [x] 子任务8.3：在SimulationJob状态枚举中添加POST_PROCESSING状态

- [x] 任务9：集成测试与验证
  - [x] 子任务9.1：编写Python端集成测试 - 使用小规模测试数据验证各性质计算流程
  - [x] 子任务9.2：编写Java端集成测试 - 验证PostProcessingService调用和结果存储
  - [x] 子任务9.3：端到端验证 - 从LAMMPS输出到数据库存储的完整流程

# Task Dependencies
- 任务1是基础配置，任务2-6均依赖任务1
- 任务2（轨迹解析）是任务4（性质计算）的前置依赖
- 任务3（GPU工具）是任务4的前置依赖
- 任务4（性质计算）是任务5（阶段脚本）的前置依赖
- 任务5是任务6（入口脚本）的前置依赖
- 任务7（Java服务）依赖任务5完成（需要Python脚本可用）
- 任务8（API端点）依赖任务7
- 任务9（测试）依赖所有其他任务完成
- 任务2和任务3可以并行开发
