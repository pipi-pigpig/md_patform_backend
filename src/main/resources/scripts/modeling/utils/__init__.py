"""
核心工具模块

提供建模过程中使用的核心工具功能，包括：
- file_utils: 文件操作工具
- packmol_utils: Packmol分子打包工具
- moltemplate_utils: Moltemplate力场生成工具
"""

from .file_utils import (
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
)

from .packmol_utils import (
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
)

from .moltemplate_utils import (
    MoltemplateRunner,
    generate_lt_template,
    run_moltemplate,
    create_lammps_data,
)

__all__ = [
    # 文件工具
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
    # Packmol工具
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
    # Moltemplate工具
    "MoltemplateRunner",
    "generate_lt_template",
    "run_moltemplate",
    "create_lammps_data",
]