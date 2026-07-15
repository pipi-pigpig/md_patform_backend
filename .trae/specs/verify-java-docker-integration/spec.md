# Java Docker集成验证与集成测试实现规范

## Why

当前Python单元测试（test_packmol_utils.py）使用mock技术模拟subprocess调用，无法验证真实的Packmol执行。用户运行测试后发现文件目录时间没有变化，说明功能没有真正执行。需要验证Java通过docker-java库操作Docker容器的完整链路是否正常工作，并创建真实的集成测试。

## What Changes

- **验证Java Docker集成**：
  - 确认DockerClient能否正确连接到本地Docker daemon（Windows使用npipe:////./pipe/docker_engine）
  - 确认DockerService.executeCommandInContainer()能否在md-engine容器中执行命令
  - 确认PackmolService能否通过DockerService成功调用Packmol

- **创建集成测试**：
  - 创建Java集成测试类验证Docker连接和命令执行
  - 创建Python集成测试脚本验证真实Packmol执行（非mock）
  - 添加测试配置和文档说明如何运行集成测试

- **诊断工具**：
  - 创建诊断脚本检查Docker环境状态
  - 检查md-engine容器是否运行
  - 检查Packmol是否在容器中可用

## Impact

- Affected specs: packmol-packing-step3（需要验证其Docker集成是否工作）
- Affected code:
  - 新增：`backend/src/test/java/.../DockerIntegrationTest.java`
  - 新增：`backend/src/main/resources/scripts/modeling/integration_test_packmol.py`
  - 新增：`backend/src/main/resources/scripts/modeling/check_docker_env.py`
  - 修改：`DockerService.java`（可能需要增强日志和错误处理）

## ADDED Requirements

### Requirement: Docker连接验证

系统必须能够验证Java DockerClient是否正确连接到本地Docker daemon。

#### Scenario: Windows Docker连接

- **WHEN** 应用运行在Windows系统
- **THEN** DockerClient使用`npipe:////./pipe/docker_engine`连接到Docker Desktop
- **AND** 连接成功时日志显示"✅ Docker client connected successfully"
- **AND** 连接失败时日志显示警告并返回null DockerClient

#### Scenario: Docker不可用时的降级处理

- **WHEN** Docker daemon不可用或Docker Desktop未运行
- **THEN** 应用仍能启动，但MD模拟功能被禁用
- **AND** DockerService.isDockerAvailable()返回false
- **AND** DockerService.isMDContainerRunning()返回mock状态true（避免阻塞测试）

### Requirement: 容器命令执行验证

系统必须能够验证在md-engine容器中执行命令的功能。

#### Scenario: 成功执行容器命令

- **WHEN** md-engine容器正在运行
- **AND** DockerClient可用
- **THEN** executeCommandInContainer()能够执行命令并返回输出
- **AND** 命令输出被正确捕获和记录

#### Scenario: 容器未运行时的错误处理

- **WHEN** md-engine容器未运行
- **THEN** executeCommandInContainer()返回错误信息
- **AND** PackmolService检查容器状态并返回错误结果

### Requirement: Packmol真实执行验证

系统必须能够验证Packmol在Docker容器中的真实执行。

#### Scenario: 通过Java调用Packmol

- **WHEN** PackmolService.executePackmol()被调用
- **AND** md-engine容器运行且Packmol可用
- **THEN** Packmol命令在容器中执行
- **AND** packed_system.pdb文件在本地inputs目录生成
- **AND** 文件修改时间更新

#### Scenario: Packmol执行失败诊断

- **WHEN** Packmol执行失败
- **THEN** 返回详细的错误信息，包括：
  - Docker连接状态
  - 容器运行状态
  - Packmol安装状态
  - 命令执行输出

### Requirement: 集成测试实现

系统必须提供真实的集成测试来验证完整功能链路。

#### Scenario: Java Docker集成测试

- **WHEN** 运行DockerIntegrationTest
- **THEN** 测试验证：
  - DockerClient连接状态
  - 容器列表获取
  - 命令执行能力
  - Packmol可用性

#### Scenario: Python真实执行测试

- **WHEN** 运行integration_test_packmol.py（非mock测试）
- **THEN** 测试验证：
  - Packmol真实执行（在Docker容器中）
  - PDB文件真实生成
  - 文件系统变化验证

### Requirement: Docker环境诊断工具

系统必须提供诊断工具帮助用户排查Docker集成问题。

#### Scenario: 环境检查脚本

- **WHEN** 运行check_docker_env.py
- **THEN** 输出以下信息：
  - Docker daemon连接状态
  - md-engine容器运行状态
  - Packmol在容器中的安装状态
  - 文件路径映射状态
  - 建议的修复措施

## MODIFIED Requirements

### Requirement: DockerService增强日志

原有DockerService需要增强日志输出，便于问题诊断。

#### Scenario: 详细执行日志

- **WHEN** 执行容器命令
- **THEN** 记录：
  - 容器ID/名称
  - 执行的完整命令
  - 命令输出（逐行）
  - 执行耗时
  - 退出码

## REMOVED Requirements

无移除的需求。