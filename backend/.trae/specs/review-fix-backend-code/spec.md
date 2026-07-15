# 后端代码全面审查修补 Spec

## Why
当前后端代码存在大量系统性问题：约90%的类缺少类级Javadoc注释，约85%的public方法缺少方法级Javadoc，多个Service写操作缺少`@Transactional`注解，Controller的`@RequestBody`参数普遍缺少`@Valid`校验，存在硬编码路径、安全漏洞（数据库凭据泄露、JWT密钥硬编码、CORS配置不安全、密码明文存储等），以及缺少全局异常处理器。这些问题严重影响代码可维护性、安全性和可靠性。

## What Changes
- 为所有缺少Javadoc的类、方法、字段补充完整注释
- 为所有Service写操作方法补充`@Transactional`注解
- 为所有Controller的`@RequestBody`参数补充`@Valid`校验
- 为所有DTO补充验证注解（`@NotNull`、`@NotBlank`、`@Size`等）
- 消除所有硬编码路径，改用PathUtil生成
- 修复安全问题：移除硬编码凭据、修复JWT密钥配置、修复CORS配置、修复密码存储、添加`@JsonIgnore`等
- 创建全局异常处理器（`@RestControllerAdvice`）
- 为Controller补充OpenAPI文档注解（`@Tag`、`@Operation`等）
- 修复MDExecutorService中的线程池泄漏问题
- 统一CORS配置（消除SecurityConfig和WebConfig的重复冲突）
- 修复AuthController使用`Map<String, Object>`接收请求的问题，改为DTO
- 修复UserController直接返回SysUser实体的问题，改为DTO
- **BREAKING**：AuthController的login/register接口请求体从Map改为DTO对象

## Impact
- Affected specs: 无
- Affected code:
  - 所有Controller（8个文件）
  - 所有Service（17个文件）
  - 所有Model（10个文件）
  - 所有DTO（14个文件）
  - 所有Repository（10个文件）
  - 所有Config/Security类（11个文件）
  - 新增：全局异常处理器、Auth请求DTO、User响应DTO

## ADDED Requirements

### Requirement: Javadoc注释规范
所有Java类必须包含完整的Javadoc注释。

#### Scenario: 类级Javadoc
- **WHEN** 查看任意Java类
- **THEN** 必须包含类级Javadoc，说明类的功能、职责、作者、版本

#### Scenario: 方法级Javadoc
- **WHEN** 查看任意public方法
- **THEN** 必须包含方法级Javadoc，说明方法功能、参数、返回值、可能抛出的异常

#### Scenario: 字段级注释
- **WHEN** 查看Model或DTO的字段
- **THEN** 必须包含字段注释，说明字段含义、取值范围、单位等

### Requirement: 事务注解规范
所有Service的数据库写操作方法必须添加`@Transactional`注解。

#### Scenario: 写操作事务保护
- **WHEN** Service方法执行INSERT/UPDATE/DELETE操作
- **THEN** 必须添加`@Transactional`注解确保数据一致性

### Requirement: 参数校验规范
所有Controller的`@RequestBody`参数必须添加`@Valid`注解，所有DTO必须定义完整的验证规则。

#### Scenario: 请求参数校验
- **WHEN** Controller接收请求体参数
- **THEN** 必须使用`@Valid`注解触发Bean Validation

#### Scenario: DTO验证注解
- **WHEN** 定义请求DTO字段
- **THEN** 必须根据业务规则添加`@NotNull`、`@NotBlank`、`@Size`、`@DecimalMin`等验证注解

### Requirement: 全局异常处理
系统必须提供统一的全局异常处理器。

#### Scenario: 异常统一响应
- **WHEN** 任意接口抛出异常
- **THEN** 全局异常处理器捕获并返回统一格式的错误响应，包含错误码、错误信息和时间戳

### Requirement: 安全修复
系统必须修复所有已知安全漏洞。

#### Scenario: 凭据安全
- **WHEN** 代码中需要使用数据库密码、JWT密钥等敏感信息
- **THEN** 必须通过环境变量或配置文件注入，禁止硬编码在源代码中

#### Scenario: CORS安全
- **WHEN** 配置跨域访问策略
- **THEN** 生产环境必须限制为特定域名，禁止使用通配符`*`

#### Scenario: 密码安全
- **WHEN** 用户密码需要更新
- **THEN** 必须使用加密算法（BCrypt）存储，禁止明文存储

#### Scenario: API响应安全
- **WHEN** 返回用户信息
- **THEN** 密码字段必须添加`@JsonIgnore`防止泄露

### Requirement: 路径规范
所有文件路径必须通过PathUtil工具类生成，禁止硬编码。

#### Scenario: 路径生成
- **WHEN** 代码中需要引用文件路径
- **THEN** 必须使用PathUtil方法生成，禁止使用字符串拼接或硬编码路径

### Requirement: API文档注解
所有Controller必须添加OpenAPI文档注解。

#### Scenario: 接口文档
- **WHEN** 查看Controller类
- **THEN** 必须包含`@Tag`注解描述模块，每个方法必须包含`@Operation`注解描述操作

### Requirement: AuthController请求体DTO化
AuthController的login和register接口必须使用DTO对象替代Map接收请求。

#### Scenario: 登录请求
- **WHEN** 用户调用登录接口
- **THEN** 请求体必须是`LoginRequest` DTO对象，包含`@NotBlank`验证

#### Scenario: 注册请求
- **WHEN** 用户调用注册接口
- **THEN** 请求体必须是`RegisterRequest` DTO对象，包含完整的字段验证

### Requirement: UserController响应DTO化
UserController必须使用DTO对象替代直接返回SysUser实体。

#### Scenario: 用户信息响应
- **WHEN** 查询用户信息
- **THEN** 必须返回`UserDto`对象，排除密码等敏感字段

## MODIFIED Requirements
无

## REMOVED Requirements

### Requirement: DatabaseDataUpdater硬编码凭据
**Reason**: DatabaseDataUpdater.java中硬编码了TiDB Cloud生产数据库的URL、用户名和密码，是严重安全漏洞
**Migration**: 移除硬编码凭据，改为从配置文件/环境变量读取；如果该工具不再需要，则直接删除

### Requirement: CORS重复配置
**Reason**: SecurityConfig和WebConfig中存在重复且冲突的CORS配置
**Migration**: 统一在SecurityConfig中配置CORS，移除WebConfig中的重复配置
