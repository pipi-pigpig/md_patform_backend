# Backend模块化架构重构规范

## Why
当前backend采用传统的分层架构（controller/service/model/repository），所有业务逻辑混在一起，导致计算引擎核心逻辑与平台管理功能耦合，代码边界不清晰，不利于独立开发和未来微服务拆分。

## What Changes
- 将backend代码按业务领域重构为模块化架构
- 创建`engine`模块：计算引擎核心功能（模拟任务、计算结果、电解液系统、分子模板等）
- 创建`management`模块：平台管理功能（用户管理、认证、操作日志等）
- 创建`common`模块：共享基础设施（配置、安全、异常处理、工具类等）
- 每个模块内部保持完整的分层结构（controller/service/model/repository/dto）

## Impact
- Affected specs: 无
- Affected code: 
  - 所有controller、service、model、repository、dto类需要重新组织目录
  - 所有类的package声明需要修改
  - 所有import语句需要更新

## ADDED Requirements

### Requirement: 模块化架构设计
系统应采用模块化架构，将计算引擎与平台管理功能解耦。

#### Scenario: 目录结构重构
- **WHEN** 查看backend目录结构
- **THEN** 应包含以下模块：
  - `common/` - 共享基础设施
  - `engine/` - 计算引擎模块
  - `management/` - 平台管理模块

### Requirement: 计算引擎模块(engine)
计算引擎模块应包含所有MD模拟相关的核心功能。

#### Scenario: Engine模块结构
- **WHEN** 查看engine模块目录
- **THEN** 应包含：
  - `controller/`: SimulationController, CalculationResultController, SystemController, MoleculeTemplateController
  - `service/`: SimulationService, CalculationResultService, SystemService, MoleculeTemplateService, MDExecutorService, DockerService, FileService, SimulationInputService, SimulationOutputService, JobLogService
  - `model/`: SimulationJob, SimulationInput, SimulationRawOutput, CalculationResult, ElectrolyteSystem, MoleculeTemplate, JobExecutionLog
  - `repository/`: 对应的Repository接口
  - `dto/`: SimulationDto, SimulationStatsDto, SystemDto, SystemCreateRequest

### Requirement: 平台管理模块(management)
平台管理模块应包含用户管理和系统运维相关功能。

#### Scenario: Management模块结构
- **WHEN** 查看management模块目录
- **THEN** 应包含：
  - `controller/`: UserController, AuthController, HealthController
  - `service/`: UserService, OperationLogService
  - `model/`: SysUser, SysRole, SysOperationLog
  - `repository/`: UserRepository, RoleRepository, OperationLogRepository

### Requirement: 共享基础设施模块(common)
共享模块应包含所有模块共用的基础设施代码。

#### Scenario: Common模块结构
- **WHEN** 查看common模块目录
- **THEN** 应包含：
  - `config/`: DockerConfig, SecurityConfig, WebConfig
  - `security/`: CustomUserDetails, CustomUserDetailsService, JwtAuthenticationFilter, JwtTokenProvider, SecurityUtils
  - `exception/`: 全局异常处理类
  - `util/`: 共享工具类

### Requirement: 包路径规范
所有类的package声明应反映新的模块结构。

#### Scenario: Engine模块包路径
- **WHEN** 查看engine模块中的类
- **THEN** package应为`com.mdplatform.engine.*`

#### Scenario: Management模块包路径
- **WHEN** 查看management模块中的类
- **THEN** package应为`com.mdplatform.management.*`

#### Scenario: Common模块包路径
- **WHEN** 查看common模块中的类
- **THEN** package应为`com.mdplatform.common.*`

## 方案评估

### 优点
1. **清晰的业务边界**: 计算引擎与管理功能分离，职责明确
2. **更好的可维护性**: 模块内代码高度相关，修改影响范围可控
3. **便于团队协作**: 不同团队可独立开发不同模块
4. **支持未来演进**: 可逐步拆分为独立微服务
5. **提高测试效率**: 可针对模块独立测试

### 潜在风险
1. **重构工作量**: 需要修改所有类的package和import
2. **短期开发中断**: 重构期间可能影响其他开发工作
3. **学习成本**: 团队需要适应新的代码组织方式

### 建议
1. 采用渐进式重构，先创建新目录结构，再逐步迁移
2. 保持API路径不变（`/api/simulations`等），只改变内部组织
3. 重构后进行全面测试，确保功能不受影响