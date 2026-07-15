# Tasks

- [x] Task 1: 创建StorageConfig配置类
  - [x] SubTask 1.1: 创建`common/config/StorageConfig.java`
  - [x] SubTask 1.2: 配置属性：rootPath, tempFileRetentionHours等
  - [x] SubTask 1.3: 添加@ConfigurationProperties注解

- [x] Task 2: 创建PathUtil路径生成工具类
  - [x] SubTask 2.1: 创建`common/util/PathUtil.java`
  - [x] SubTask 2.2: 实现任务根目录生成方法
  - [x] SubTask 2.3: 实现各子目录生成方法（inputs, outputs, post_processing, temp, visualization, report）
  - [x] SubTask 2.4: 实现文件名生成方法（LAMMPS脚本、轨迹文件、结果文件、临时文件）
  - [x] SubTask 2.5: 实现相对路径解析方法
  - [x] SubTask 2.6: 实现目录自动创建方法
  - [x] SubTask 2.7: 添加路径校验方法（检查存在性、权限）

- [x] Task 3: 创建AtomicFileService原子写入服务
  - [x] SubTask 3.1: 创建`engine/service/AtomicFileService.java`
  - [x] SubTask 3.2: 实现原子写入方法（writeAtomic）
  - [x] SubTask 3.3: 实现原子复制方法（copyAtomic）
  - [x] SubTask 3.4: 实现原子移动方法（moveAtomic）
  - [x] SubTask 3.5: 添加异常处理和日志记录

- [x] Task 4: 创建FileCleanupScheduler临时文件清理调度器
  - [x] SubTask 4.1: 创建`engine/service/FileCleanupScheduler.java`
  - [x] SubTask 4.2: 配置定时任务（每天凌晨2点执行）
  - [x] SubTask 4.3: 实现临时文件扫描逻辑
  - [x] SubTask 4.4: 实现过期文件删除逻辑（超过24小时）
  - [x] SubTask 4.5: 添加清理日志记录

- [x] Task 5: 更新application.yml配置
  - [x] SubTask 5.1: 添加`md-platform.file-storage.root-path`配置
  - [x] SubTask 5.2: 添加`md-platform.file-storage.temp-retention-hours`配置
  - [x] SubTask 5.3: 配置默认值

- [x] Task 6: 重构FileService
  - [x] SubTask 6.1: 修改FileService使用PathUtil替代硬编码
  - [x] SubTask 6.2: 修改方法签名添加userId参数
  - [x] SubTask 6.3: 使用AtomicFileService进行原子写入
  - [x] SubTask 6.4: 更新相关调用代码

- [x] Task 7: 创建单元测试
  - [x] SubTask 7.1: 创建PathUtilTest测试路径生成逻辑
  - [x] SubTask 7.2: 创建AtomicFileServiceTest测试原子写入
  - [x] SubTask 7.3: 创建FileCleanupSchedulerTest测试清理逻辑

# Task Dependencies
- Task 2 依赖 Task 1（PathUtil需要StorageConfig）
- Task 3 依赖 Task 2（AtomicFileService需要PathUtil）
- Task 4 依赖 Task 1, Task 2
- Task 6 依赖 Task 2, Task 3
- Task 7 依赖 Task 1-6完成