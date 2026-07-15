# 后端代码审查修补检查清单

## 安全问题修复
- [x] DatabaseDataUpdater.java中硬编码的数据库凭据已移除（改为环境变量读取）
- [x] JwtTokenProvider.java中JWT密钥不再有硬编码默认值，必须从配置文件读取
- [x] UserService.updateUser中密码使用BCrypt加密存储，不再明文存储
- [x] SysUser.password字段已添加@JsonIgnore注解
- [x] SecurityConfig中/api/systems/**已改为需要认证访问
- [x] CORS配置已统一到SecurityConfig中，且不再使用通配符*
- [x] WebConfig中H2控制台路径已移除
- [x] SystemCreateRequest.toEntity()不再硬编码默认用户ID
- [x] JwtAuthenticationFilter认证失败时返回401状态码

## 全局异常处理
- [x] GlobalExceptionHandler已创建，能处理校验异常、业务异常、未知异常
- [x] ErrorResponse统一错误响应DTO已创建
- [x] LoginRequest DTO已创建，包含@NotBlank验证
- [x] RegisterRequest DTO已创建，包含完整字段验证
- [x] UserDto DTO已创建，排除密码等敏感字段

## Service事务和注释
- [x] SystemService的createSystem/updateSystem/deleteSystem已添加@Transactional
- [x] CalculationResultService的createResult/deleteResult已添加@Transactional
- [x] SimulationInputService的createInput/updateInput/deleteInput已添加@Transactional
- [x] SimulationOutputService的createOutput/updateOutput/deleteOutput已添加@Transactional
- [x] 所有17个Service已补充类级Javadoc
- [x] 所有Service的public方法已补充方法级Javadoc
- [x] JobLogService和OperationLogService已补充日志记录

## Controller校验和文档
- [x] 所有Controller的@RequestBody参数已添加@Valid注解
- [x] 所有Controller已添加@Tag类级注解
- [x] 所有Controller方法已添加@Operation方法级注解
- [x] 所有Controller已补充类级Javadoc和方法级Javadoc
- [x] AuthController已重构为使用LoginRequest/RegisterRequest DTO
- [x] UserController已重构为使用UserDto替代直接返回SysUser

## DTO验证和注释
- [x] SystemCreateRequest已添加完整验证注解
- [x] SimulationDto已添加字段注释
- [x] BoxSizeRequest已补充@NotNull注解
- [x] PackmolResult已补充验证注解
- [x] 其余DTO已补充验证注解
- [x] 所有DTO已补充类级Javadoc和字段注释

## Model注释
- [x] 所有10个Model已补充类级Javadoc
- [x] 所有Model的无注释字段已补充字段注释
- [x] ElectrolyteSystem已补充@PrePersist/@PreUpdate回调

## Repository注释
- [x] 所有10个Repository已补充类级Javadoc
- [x] 所有Repository的自定义查询方法已补充Javadoc

## Config和Security注释
- [x] DockerConfig、SecurityConfig、StorageConfig、WebConfig已补充类级Javadoc和字段注释
- [x] CustomUserDetails等5个Security类已补充类级Javadoc和方法Javadoc
- [x] CustomUserDetails角色不再硬编码，使用数据库角色信息

## 硬编码路径和线程问题
- [x] MDExecutorService中硬编码路径已改用PathUtil
- [x] MoltemplateService中硬编码路径已改用配置注入
- [x] MDExecutorService线程池泄漏问题已修复
- [x] SimulationDto.fromEntity()中ObjectMapper性能问题已修复

## PathUtil和AtomicFileService
- [x] AtomicFileService已补充类级Javadoc和方法Javadoc
- [x] AtomicFileService的writeAtomic已添加Windows降级处理
- [x] PathUtil已补充getUserUploadPath和getUserRootPath方法
