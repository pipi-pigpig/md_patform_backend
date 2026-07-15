# Tasks

- [x] Task 1: 创建新的模块目录结构
  - [x] SubTask 1.1: 创建common模块目录（config, security, exception, util）
  - [x] SubTask 1.2: 创建engine模块目录（controller, service, model, repository, dto）
  - [x] SubTask 1.3: 创建management模块目录（controller, service, model, repository）

- [x] Task 2: 迁移common模块代码
  - [x] SubTask 2.1: 迁移config目录（DockerConfig, SecurityConfig, WebConfig）
  - [x] SubTask 2.2: 迁移security目录（所有安全相关类）
  - [x] SubTask 2.3: 更新所有类的package声明和import

- [x] Task 3: 迁移engine模块代码
  - [x] SubTask 3.1: 迁移controller（SimulationController, CalculationResultController, SystemController, MoleculeTemplateController）
  - [x] SubTask 3.2: 迁移service（所有计算引擎相关服务）
  - [x] SubTask 3.3: 迁移model（所有计算引擎相关实体）
  - [x] SubTask 3.4: 迁移repository（所有计算引擎相关Repository）
  - [x] SubTask 3.5: 迁移dto（所有计算引擎相关DTO）
  - [x] SubTask 3.6: 更新所有类的package声明和import

- [x] Task 4: 迁移management模块代码
  - [x] SubTask 4.1: 迁移controller（UserController, AuthController, HealthController）
  - [x] SubTask 4.2: 迁移service（UserService, OperationLogService）
  - [x] SubTask 4.3: 迁移model（SysUser, SysRole, SysOperationLog）
  - [x] SubTask 4.4: 迁移repository（UserRepository, RoleRepository, OperationLogRepository）
  - [x] SubTask 4.5: 更新所有类的package声明和import

- [x] Task 5: 清理旧目录结构
  - [x] SubTask 5.1: 删除旧的controller目录
  - [x] SubTask 5.2: 删除旧的service目录
  - [x] SubTask 5.3: 删除旧的model目录
  - [x] SubTask 5.4: 删除旧的repository目录
  - [x] SubTask 5.5: 删除旧的dto目录

- [x] Task 6: 验证重构结果
  - [x] SubTask 6.1: 编译验证（确保所有import正确）
  - [x] SubTask 6.2: 功能验证（确保API功能正常）

# Task Dependencies
- Task 2, Task 3, Task 4 可并行执行（都依赖 Task 1）
- Task 5 依赖 Task 2, Task 3, Task 4 完成
- Task 6 依赖 Task 5 完成