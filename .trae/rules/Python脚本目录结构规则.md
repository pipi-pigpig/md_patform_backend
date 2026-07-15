# Python脚本目录结构规则

## 一、目录结构规范

所有Python脚本必须按照以下目录结构组织：

```
backend/src/main/resources/scripts/modeling/
├── config/                     # 配置模块
│   ├── __init__.py             # 模块入口，导出配置对象
│   └── config.py               # 全局配置定义
│
├── utils/                      # 核心工具模块
│   ├── __init__.py             # 模块入口，导出所有工具类
│   ├── file_utils.py           # 文件操作工具
│   ├── packmol_utils.py        # Packmol分子打包工具
│   └── moltemplate_utils.py    # Moltemplate力场生成工具
│   └── [其他工具].py           # 新增工具文件
│
├── tests/                      # 测试模块
│   ├── __init__.py             # 测试模块入口
│   ├── test_[模块名].py        # 单元测试文件
│   ├── integration_test_*.py   # 集成测试文件
│   └── conftest.py             # pytest配置（可选）
│
├── diagnostics/                # 诊断工具模块
│   ├── __init__.py             # 诊断模块入口
│   ├── check_docker_env.py     # Docker环境检查
│   └── [其他诊断工具].py       # 新增诊断工具
│
├── stages/                     # 建模流程阶段模块（可选）
│   ├── __init__.py             # 阶段模块入口
│   ├── stage1_molecule_count.py    # 步骤1：分子数量计算
│   ├── stage2_packmol_packing.py   # 步骤2：Packmol堆积
│   ├── stage3_moltemplate_build.py # 步骤3：Moltemplate拓扑构建
│   └── [其他阶段].py           # 新增阶段脚本
│
├── __init__.py                 # modeling模块总入口
├── modeling.py                 # 核心建模逻辑类
└── run_modeling.py             # 主入口脚本（命令行入口）
```

## 二、文件分类规则

### 2.1 配置文件 (config/)
- **存放内容**：全局配置、常量定义、路径配置
- **命名规范**：`config.py`、`constants.py`、`paths.py`
- **导入方式**：`from config import config`

### 2.2 工具模块 (utils/)
- **存放内容**：可复用的工具类、辅助函数
- **命名规范**：`[功能]_utils.py`，如 `packmol_utils.py`
- **导入方式**：`from utils.packmol_utils import PackmolRunner`
- **要求**：每个工具文件必须包含完整的类和函数文档

### 2.3 测试文件 (tests/)
- **存放内容**：单元测试、集成测试、测试配置
- **命名规范**：
  - 单元测试：`test_[被测模块名].py`
  - 集成测试：`integration_test_[功能名].py`
- **运行方式**：`pytest tests/`

### 2.4 诊断工具 (diagnostics/)
- **存放内容**：环境检查、问题诊断、调试工具
- **命名规范**：`check_[检查项].py`、`diagnose_[问题].py`
- **运行方式**：独立运行，如 `python diagnostics/check_docker_env.py`

### 2.5 建模阶段 (stages/)
- **存放内容**：建模流程的各个阶段实现
- **命名规范**：`stage[序号]_[阶段名].py`
- **导入方式**：`from stages.stage1_molecule_count import calculate_molecule_count`

### 2.6 入口脚本 (根目录)
- **存放内容**：主入口脚本、核心逻辑类
- **命名规范**：`run_[功能].py`、`[功能].py`
- **运行方式**：`python run_modeling.py --mode [模式]`

## 三、导入规范

### 3.1 相对导入（模块内部）
```python
# utils/packmol_utils.py 内部导入
from ..config import config
from .file_utils import ensure_directory
```

### 3.2 绝对导入（外部调用）
```python
# Java调用或外部脚本
from modeling.utils.packmol_utils import PackmolRunner
from modeling.config import config
```

### 3.3 __init__.py 导出规范
每个子目录的 `__init__.py` 必须导出该目录下的主要类和函数：

```python
# utils/__init__.py 示例
from .packmol_utils import PackmolRunner, run_packmol_packing
from .moltemplate_utils import MoltemplateRunner
from .file_utils import ensure_directory, atomic_write

__all__ = [
    'PackmolRunner',
    'run_packmol_packing',
    'MoltemplateRunner',
    'ensure_directory',
    'atomic_write',
]
```

## 四、新增脚本规则

### 4.1 新增工具类
1. 在 `utils/` 目录下创建 `[功能]_utils.py`
2. 添加完整的类文档和函数文档
3. 在 `utils/__init__.py` 中导出

### 4.2 新增测试文件
1. 在 `tests/` 目录下创建 `test_[模块名].py`
2. 使用 pytest 框架编写测试
3. 测试类命名：`Test[功能名称]`

### 4.3 新增诊断工具
1. 在 `diagnostics/` 目录下创建 `check_[检查项].py`
2. 添加独立的 `main()` 函数入口
3. 输出格式清晰，包含修复建议

### 4.4 新增建模阶段
1. 在 `stages/` 目录下创建 `stage[序号]_[阶段名].py`
2. 实现该阶段的完整逻辑
3. 在 `stages/__init__.py` 中导出

## 五、禁止事项

1. **禁止在根目录直接放置工具类文件**（应放入 `utils/`）
2. **禁止在根目录直接放置测试文件**（应放入 `tests/`）
3. **禁止使用硬编码路径**（应使用 `config.py` 中的配置）
4. **禁止循环导入**（合理设计模块依赖关系）
5. **禁止在工具类中包含业务逻辑**（工具类只提供通用功能）

## 六、文件命名规范

| 文件类型 | 前缀/后缀 | 示例 |
|---------|----------|------|
| 工具模块 | `_utils.py` | `packmol_utils.py` |
| 单元测试 | `test_*.py` | `test_packmol_utils.py` |
| 集成测试 | `integration_test_*.py` | `integration_test_packmol.py` |
| 诊断工具 | `check_*.py` | `check_docker_env.py` |
| 建模阶段 | `stage*_*.py` | `stage1_molecule_count.py` |
| 入口脚本 | `run_*.py` | `run_modeling.py` |
| 配置文件 | 无特殊要求 | `config.py`, `constants.py` |

## 七、文档规范

每个Python文件必须包含：
1. **文件头注释**：说明文件功能、作者、版本
2. **类文档**：说明类的用途、属性、使用示例
3. **函数文档**：说明参数、返回值、异常、使用示例

```python
"""
[模块名称] - [功能描述]

功能：
    1. [功能1]
    2. [功能2]

使用方法：
    python [文件名].py --[参数]

作者: [作者]
版本: [版本号]
"""
```