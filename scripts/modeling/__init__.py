"""
电解液MD计算平台 - 建模模块

该模块提供分子动力学模拟的初始建模功能，包括：
- 分子数量计算
- 盒子尺寸计算
- 电中性验证
- Packmol分子打包（通过utils.packmol_utils）
- Moltemplate力场生成（通过utils.moltemplate_utils）

模块结构:
    modeling/
    ├── config/                 # 配置参数
    │   ├── __init__.py
    │   └── config.py
    ├── utils/                  # 核心工具模块
    │   ├── __init__.py
    │   ├── file_utils.py       # 文件操作工具
    │   ├── packmol_utils.py    # Packmol核心功能
    │   └── moltemplate_utils.py # Moltemplate核心功能
    ├── tests/                  # 测试文件
    │   └── __init__.py
    ├── diagnostics/            # 诊断工具
    │   └── __init__.py
    ├── __init__.py             # 模块入口
    ├── modeling.py             # 核心建模逻辑
    └── run_modeling.py         # 主入口脚本
"""

from .modeling import (
    calculate_molecule_counts,
    calculate_box_size,
    validate_electrical_neutrality,
    adjust_ion_counts,
    MoleculeInfo,
    generate_molecule_summary,
)

from .config import Config, config

from .utils import (
    # 文件工具
    read_formula_json,
    write_result_json,
    read_json,
    write_json,
    file_exists,
    delete_file,
    get_file_size,
    list_files,
    ensure_directory,
    copy_template_files,
    # Packmol功能
    PackmolRunner,
    PACKMOL_TOLERANCE_FIXED,
    PACKMOL_MAX_ITERATIONS_DEFAULT,
    PACKMOL_DEFAULT_TIMEOUT,
    PACKMOL_MAX_RETRY_COUNT,
    PACKMOL_BOX_SCALE_FACTOR,
    run_packmol_packing,
    fetch_molecule_templates,
    copy_pdb_files_to_workdir,
    generate_packmol_input,
    run_packmol,
    create_pdb_from_molecule,
    # Moltemplate功能
    MoltemplateRunner,
    generate_lt_template,
    run_moltemplate,
    create_lammps_data,
)

__version__ = "1.0.0"
__all__ = [
    # 核心建模功能
    "calculate_molecule_counts",
    "calculate_box_size",
    "validate_electrical_neutrality",
    "adjust_ion_counts",
    "MoleculeInfo",
    "generate_molecule_summary",
    # 配置和工具
    "Config",
    "config",
    "read_formula_json",
    "write_result_json",
    "read_json",
    "write_json",
    "file_exists",
    "delete_file",
    "get_file_size",
    "list_files",
    "ensure_directory",
    "copy_template_files",
    # Packmol功能
    "PackmolRunner",
    "PACKMOL_TOLERANCE_FIXED",
    "PACKMOL_MAX_ITERATIONS_DEFAULT",
    "PACKMOL_DEFAULT_TIMEOUT",
    "PACKMOL_MAX_RETRY_COUNT",
    "PACKMOL_BOX_SCALE_FACTOR",
    "run_packmol_packing",
    "fetch_molecule_templates",
    "copy_pdb_files_to_workdir",
    "generate_packmol_input",
    "run_packmol",
    "create_pdb_from_molecule",
    # Moltemplate功能
    "MoltemplateRunner",
    "generate_lt_template",
    "run_moltemplate",
    "create_lammps_data",
]