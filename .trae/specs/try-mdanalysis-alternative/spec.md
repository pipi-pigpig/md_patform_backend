# 尝试 MDAnalysis 替代 MDSuite Spec

## Why
MDSuite 在 md-engine 容器中存在严重的依赖兼容性问题（numpy 2.2.6 与 pandas 二进制不兼容），导致无法正常导入和使用。用户建议尝试 MDAnalysis 作为替代方案，MDAnalysis 是一个成熟的分子动力学轨迹分析库，可能具有更好的依赖兼容性。

## What Changes
- **安装 MDAnalysis**：在 md-engine 容器中通过 pip 安装 MDAnalysis 及其依赖。
- **验证 MDAnalysis 可用性**：测试 MDAnalysis 是否可以正常导入和执行基本操作。
- **更新测试脚本**：将 `verify_md_tools.sh` 中的 MDSuite 验证替换或添加为 MDAnalysis 验证。
- **对比分析**：简要对比 MDAnalysis 和 MDSuite 的功能差异，确认是否满足项目需求。

## Impact
- **受影响的功能**：分子动力学轨迹后处理功能。
- **受影响的代码**：
  - md-engine 容器的 Python 环境（安装新包）
  - `scripts/verify_md_tools.sh` 测试脚本
- **受影响的容器**：md-engine。

## ADDED Requirements
### Requirement: MDAnalysis 工具集成
md-engine 容器 SHALL 已安装 MDAnalysis，并可通过 Python 导入使用。

#### Scenario: 成功导入 MDAnalysis
- **WHEN** 在 md-engine 容器中执行 `python3 -c "import MDAnalysis; print(MDAnalysis.__version__)"`
- **THEN** 命令成功执行并输出 MDAnalysis 版本号

#### Scenario: 基本功能验证
- **WHEN** 使用 MDAnalysis 加载一个简单的轨迹文件（如 .xyz 或 .lammpstrj）
- **THEN** 能够成功读取并访问原子坐标信息

## MODIFIED Requirements
### Requirement: 工具验证测试脚本
测试脚本 SHALL 包含 MDAnalysis 验证（替代或补充 MDSuite 验证）。

#### Scenario: 验证 MDAnalysis 状态
- **WHEN** 运行 `verify_md_tools.sh` 脚本
- **THEN** 输出包含 MDAnalysis 的验证状态（成功/失败）

## REMOVED Requirements
无（MDSuite 验证保留但标记为可选或已知问题）