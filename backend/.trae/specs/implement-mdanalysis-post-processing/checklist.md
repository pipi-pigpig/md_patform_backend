# MDAnalysis后处理分析管线实现检查清单

- [x] 检查点1：配置模块扩展完成
  - [x] `PostProcessingConfig`数据类已创建，包含所有后处理参数
  - [x] `Config`类已添加`post_processing`字段
  - [x] `config/__init__.py`已导出新配置

- [x] 检查点2：MDAnalysis轨迹解析工具可用
  - [x] `TrajectoryLoader`类已创建，可加载LAMMPS轨迹文件
  - [x] 可加载带电荷轨迹文件（dump.charge.lammpstrj）
  - [x] 坐标解包裹功能正常（消除周期性跳跃）
  - [x] 可提取盒子尺寸和原子选择
  - [x] `utils/__init__.py`已导出

- [x] 检查点3：GPU加速计算工具可用
  - [x] `GPUBackend`类支持CuPy/PyTorch/NumPy自动回退
  - [x] GPU自相关函数计算正确
  - [x] GPU数值积分计算正确
  - [x] GPU距离计算正确
  - [x] GPU不可用时自动回退到CPU并记录警告

- [x] 检查点4：密度计算器正确
  - [x] 可从log.lammps解析production阶段密度数据
  - [x] 计算均值、标准差、标准误差
  - [x] 收敛判断逻辑正确（std/mean < 5%）
  - [x] 输出格式符合density_result_table字段要求

- [x] 检查点5：电导率计算器正确
  - [x] Green-Kubo方法从电流密度自相关函数计算电导率
  - [x] 输出电导率张量（xx/yy/zz）、离子贡献、电阻率
  - [x] Einstein方法备用计算可用
  - [x] 输出格式符合conductivity_result_table字段要求

- [x] 检查点6：粘度计算器正确
  - [x] Green-Kubo方法从应力张量自相关函数计算粘度
  - [x] 输出粘度值、剪切率、应力响应、运动粘度
  - [x] 输出格式符合viscosity_result_table字段要求

- [x] 检查点7：介电常数计算器正确
  - [x] 偶极矩涨落方法计算静态介电常数
  - [x] 输出介电张量、偶极矩数据、组分贡献
  - [x] 输出格式符合dielectric_result_table字段要求

- [x] 检查点8：溶剂化结构计算器正确
  - [x] MDAnalysis计算RDF曲线
  - [x] 积分RDF计算配位数
  - [x] 输出RDF曲线数据、配位数、配位距离、特征峰
  - [x] 输出格式符合solvation_result_table字段要求

- [x] 检查点9：后处理阶段脚本完整
  - [x] 6步流程完整实现（读取配置→加载文件→预处理→计算→校验→输出）
  - [x] 结果JSON写入`post_processing/{property}_result.json`
  - [x] 可视化数据写入`visualization/charts/{type}_curve.json`
  - [x] `stages/__init__.py`已导出

- [x] 检查点10：run_modeling.py支持post-processing模式
  - [x] `--mode post-processing`参数可用
  - [x] `--target-properties`参数可传递
  - [x] `--temperature`和`--time-step-fs`参数可传递

- [x] 检查点11：Java PostProcessingService实现
  - [x] 可通过DockerService在容器中执行Python后处理脚本
  - [x] 可解析post_processing/目录下的JSON结果文件
  - [x] 可将结果写入calculation_result_table主表和对应子表
  - [x] 错误处理和日志记录完善

- [x] 检查点12：Java后处理API端点可用
  - [x] `POST /api/post-processing/job/{jobId}`端点可用（注：实际路径为/api/post-processing/job/{jobId}，非原规格中的/api/results/job/{jobId}/post-processing）
  - [x] SimulationJob状态枚举包含POST_PROCESSING

- [x] 检查点13：文件输入输出符合规范
  - [x] 所有文件路径通过配置生成，无硬编码路径
  - [x] 输出文件存入`post_processing/`目录
  - [x] 可视化数据存入`visualization/charts/`目录
  - [x] 数据库中存储相对路径
  - [x] 文件命名符合规范（小写+下划线）

- [x] 检查点14：代码规范
  - [x] Python文件包含完整文件头注释
  - [x] 类和函数有完整文档
  - [x] 使用相对导入（模块内部）
  - [x] 日志使用logging模块，格式为`[模块名] 操作描述`
  - [x] 不使用mock测试
