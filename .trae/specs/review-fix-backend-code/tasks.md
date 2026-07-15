# Tasks

- [x] Task 1: 修复P0级安全问题
  - [x] SubTask 1.1: 移除DatabaseDataUpdater.java中的硬编码数据库凭据，改为从环境变量读取
  - [x] SubTask 1.2: 修复JwtTokenProvider.java中JWT密钥硬编码默认值问题，确保必须从配置文件读取且无默认值
  - [x] SubTask 1.3: 修复UserService.updateUser中明文密码存储问题，添加BCrypt加密
  - [x] SubTask 1.4: 为SysUser.password字段添加`@JsonIgnore`注解防止密码泄露
  - [x] SubTask 1.5: 修复SecurityConfig中`/api/systems/**`的permitAll配置，改为需要认证
  - [x] SubTask 1.6: 修复SecurityConfig和WebConfig的CORS配置，统一在SecurityConfig中配置并限制为特定域名
  - [x] SubTask 1.7: 移除WebConfig中暴露的H2控制台路径
  - [x] SubTask 1.8: 修复SystemCreateRequest.toEntity()中硬编码默认用户ID为1的问题
  - [x] SubTask 1.9: 修复JwtAuthenticationFilter认证失败时未返回401状态码的问题

- [x] Task 2: 创建全局异常处理器和新增DTO
  - [x] SubTask 2.1: 创建GlobalExceptionHandler（@RestControllerAdvice），统一处理校验异常、业务异常、未知异常
  - [x] SubTask 2.2: 创建ErrorResponse统一错误响应DTO
  - [x] SubTask 2.3: 创建LoginRequest DTO（替代AuthController的Map参数）
  - [x] SubTask 2.4: 创建RegisterRequest DTO（替代AuthController的Map参数）
  - [x] SubTask 2.5: 创建UserDto DTO（替代UserController直接返回SysUser实体）

- [x] Task 3: 为所有Service补充@Transactional注解和Javadoc注释
  - [x] SubTask 3.1: 为SystemService的createSystem/updateSystem/deleteSystem添加@Transactional
  - [x] SubTask 3.2: 为CalculationResultService的createResult/deleteResult添加@Transactional
  - [x] SubTask 3.3: 为SimulationInputService的createInput/updateInput/deleteInput添加@Transactional
  - [x] SubTask 3.4: 为SimulationOutputService的createOutput/updateOutput/deleteOutput添加@Transactional
  - [x] SubTask 3.5: 为所有17个Service补充类级Javadoc和public方法Javadoc
  - [x] SubTask 3.6: 为JobLogService和OperationLogService补充日志记录

- [x] Task 4: 为所有Controller补充@Valid、OpenAPI注解和Javadoc
  - [x] SubTask 4.1: 为所有Controller的@RequestBody参数添加@Valid注解
  - [x] SubTask 4.2: 为所有Controller添加@Tag类级注解和@Operation方法级注解
  - [x] SubTask 4.3: 为所有Controller补充类级Javadoc和方法级Javadoc
  - [x] SubTask 4.4: 重构AuthController使用LoginRequest/RegisterRequest DTO替代Map
  - [x] SubTask 4.5: 重构UserController使用UserDto替代直接返回SysUser

- [x] Task 5: 为所有DTO补充验证注解和注释
  - [x] SubTask 5.1: 为SystemCreateRequest添加完整验证注解（@NotBlank name, @NotNull temperature/pressure等）
  - [x] SubTask 5.2: 为SimulationDto添加字段注释
  - [x] SubTask 5.3: 为BoxSizeRequest补充@NotNull注解
  - [x] SubTask 5.4: 为PackmolResult补充验证注解
  - [x] SubTask 5.5: 为其余缺少验证注解的DTO补充（SimulationStatsDto, SystemDto, BoxSizeResult, FormulaCalculationResult, MoleculeCountResult）
  - [x] SubTask 5.6: 为所有DTO补充类级Javadoc和字段注释

- [x] Task 6: 为所有Model补充注释
  - [x] SubTask 6.1: 为所有10个Model补充类级Javadoc
  - [x] SubTask 6.2: 为所有Model的无注释字段补充字段注释
  - [x] SubTask 6.3: 为ElectrolyteSystem补充@PrePersist/@PreUpdate回调（与SimulationJob保持一致）

- [x] Task 7: 为所有Repository补充注释
  - [x] SubTask 7.1: 为所有10个Repository补充类级Javadoc
  - [x] SubTask 7.2: 为所有Repository的自定义查询方法补充Javadoc（说明查询逻辑和返回结构）

- [x] Task 8: 为Config和Security类补充注释
  - [x] SubTask 8.1: 为DockerConfig、SecurityConfig、StorageConfig、WebConfig补充类级Javadoc和字段注释
  - [x] SubTask 8.2: 为CustomUserDetails、CustomUserDetailsService、JwtAuthenticationFilter、JwtTokenProvider、SecurityUtils补充类级Javadoc和方法Javadoc
  - [x] SubTask 8.3: 修复CustomUserDetails中角色硬编码为ROLE_USER的问题，使用数据库角色信息

- [x] Task 9: 消除硬编码路径和修复线程问题
  - [x] SubTask 9.1: 修复MDExecutorService中的硬编码路径（data/inputs, data/results, templates），改用PathUtil
  - [x] SubTask 9.2: 修复MoltemplateService中的硬编码路径（python/moltemplate_modeling.py），改用配置注入
  - [x] SubTask 9.3: 修复MDExecutorService中无界CachedThreadPool和ScheduledExecutorService线程泄漏问题
  - [x] SubTask 9.4: 修复SimulationDto.fromEntity()中每次新建ObjectMapper的性能问题

- [x] Task 10: 为PathUtil和AtomicFileService补充完善
  - [x] SubTask 10.1: 为AtomicFileService补充类级Javadoc和方法Javadoc
  - [x] SubTask 10.2: 为AtomicFileService的writeAtomic方法添加Windows上ATOMIC_MOVE失败的降级处理
  - [x] SubTask 10.3: 为PathUtil补充getUserUploadPath和getUserRootPath方法

# Task Dependencies
- [Task 2] 必须在 [Task 4] 之前完成（Controller重构依赖新DTO）
- [Task 1] 应最先执行（安全问题优先级最高）
- [Task 3, 5, 6, 7, 8, 9, 10] 可并行执行
- [Task 4] 依赖 [Task 2] 和 [Task 1]
