"""
核心工具模块

提供建模过程中使用的核心工具功能，包括：
- file_utils: 文件操作工具
- packmol_utils: Packmol分子打包工具
- moltemplate_utils: Moltemplate力场生成工具及PDB解析功能
- lammps_template_utils: LAMMPS模板渲染工具（Jinja2模板引擎）
- mdanalysis_utils: MDAnalysis轨迹解析与GPU加速计算工具
- property_calculator: 物性计算器（密度、电导率、粘度、介电常数、溶剂化结构）
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
    # PDB解析功能
    AtomRecord,
    MoleculeInstance,
    parse_pdb_file,
    extract_molecule_coordinates,
    identify_molecule_boundaries,
    map_molecule_ids,
    validate_pdb_structure,
    # 分子模板文件加载功能
    fetch_molecule_lt_templates,
    copy_lt_files_to_workdir,
    validate_lt_template,
    load_forcefield_file,
    # 分子实例创建功能
    create_molecule_instances,
    assign_atom_coordinates,
    generate_atom_names,
    validate_instance_count,
    # System.lt文件生成功能
    generate_template_imports,
    generate_molecule_instance_definitions,
    generate_coordinate_data,
    generate_box_boundary,
    generate_system_lt_file,
    validate_system_lt,
    # 完整流程整合功能
    StepExecutionResult,
    run_moltemplate_system_generation,
    run_moltemplate_system_generation_simple,
    # Moltemplate命令执行功能（步骤6）
    execute_moltemplate_command,
    validate_generated_files,
    # 文件整理输出功能（步骤7）
    organize_lammps_input_files,
    # init文件修复功能
    fix_system_in_init,
    # 完整流程整合功能（步骤6-7）
    run_moltemplate_execution,
)

from .lammps_template_utils import (
    render_lammps_templates,
    validate_template_context,
    list_available_templates,
)

from .mdanalysis_utils import (
    TrajectoryLoader,
    GPUBackend,
    LogFileParser,
)

from .property_calculator import (
    ConvergenceChecker,
    DensityCalculator,
    ConductivityCalculator,
    ViscosityCalculator,
    DielectricCalculator,
    SolvationCalculator,
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
    # PDB解析功能
    "AtomRecord",
    "MoleculeInstance",
    "parse_pdb_file",
    "extract_molecule_coordinates",
    "identify_molecule_boundaries",
    "map_molecule_ids",
    "validate_pdb_structure",
    # 分子模板文件加载功能
    "fetch_molecule_lt_templates",
    "copy_lt_files_to_workdir",
    "validate_lt_template",
    "load_forcefield_file",
    # 分子实例创建功能
    "create_molecule_instances",
    "assign_atom_coordinates",
    "generate_atom_names",
    "validate_instance_count",
    # System.lt文件生成功能
    "generate_template_imports",
    "generate_molecule_instance_definitions",
    "generate_coordinate_data",
    "generate_box_boundary",
    "generate_system_lt_file",
    "validate_system_lt",
    # 完整流程整合功能
    "StepExecutionResult",
    "run_moltemplate_system_generation",
    "run_moltemplate_system_generation_simple",
    # Moltemplate命令执行功能（步骤6）
    "execute_moltemplate_command",
    "validate_generated_files",
    # 文件整理输出功能（步骤7）
    "organize_lammps_input_files",
    # init文件修复功能
    "fix_system_in_init",
    # 完整流程整合功能（步骤6-7）
    "run_moltemplate_execution",
    # LAMMPS模板渲染工具
    "render_lammps_templates",
    "validate_template_context",
    "list_available_templates",
    # MDAnalysis轨迹解析与GPU加速计算工具
    "TrajectoryLoader",
    "GPUBackend",
    "LogFileParser",
    # 物性计算器
    "ConvergenceChecker",
    "DensityCalculator",
    "ConductivityCalculator",
    "ViscosityCalculator",
    "DielectricCalculator",
    "SolvationCalculator",
]