```mysql
-- =============================================
-- 0. 数据库初始化
-- =============================================
CREATE DATABASE IF NOT EXISTS md_database
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;
USE md_database;

-- =============================================
-- 1. 分子模板表 molecule_template_table
-- 核心基础表，存储所有分子的拓扑、力场参数
-- =============================================
CREATE TABLE molecule_template_table (
    molecule_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '分子唯一ID',
    molecule_name VARCHAR(100) NOT NULL UNIQUE COMMENT '分子标准名称（如EC、LiPF₆、DMC）',
    molecule_type VARCHAR(20) NOT NULL COMMENT '分子类型：溶剂/锂盐阳离子/锂盐阴离子/添加剂',
    formula VARCHAR(50) COMMENT '分子式',
    smiles VARCHAR(500) NOT NULL COMMENT '分子SMILES表达式',
    molecular_weight DOUBLE NOT NULL COMMENT '分子量，单位amu',
    atom_count INT NOT NULL COMMENT '单分子原子总数',
    net_charge DOUBLE NOT NULL COMMENT '分子净电荷，单位e',
    force_field_type VARCHAR(50) NOT NULL COMMENT '力场类型及版本（如OPLS-AA 2021）',
    lt_file_path VARCHAR(500) NOT NULL COMMENT 'Moltemplate .lt模板文件存储路径',
    single_pdb_path VARCHAR(500) NOT NULL COMMENT '单分子pdb结构文件存储路径',
    force_field_params JSON NOT NULL COMMENT '完整力场参数（键/角/二面角/LJ参数等）',
    force_field_topology_source VARCHAR(500) NULL COMMENT '【v17】力场拓扑来源文件URI',
    default_nonbonded_exclusion_rules JSON NULL COMMENT '【v17】默认非键排除与缩放规则',
    description TEXT COMMENT '分子描述、适用场景',
    is_system_template TINYINT NOT NULL DEFAULT 0 COMMENT '是否系统预置：0-用户上传 1-系统预置',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_molecule_name (molecule_name),
    INDEX idx_molecule_type (molecule_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='【v17】分子模板表：自动化建模核心，对齐几何模型+原子间势参数规范';

-- =============================================
-- 2. 电解液配方体系表 electrolyte_systems_table
-- 存储电解液配方信息，支持混合溶剂、多添加剂
-- =============================================
CREATE TABLE electrolyte_systems_table (
    system_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '配方体系唯一ID',
    system_name VARCHAR(200) NOT NULL COMMENT '配方名称',
    task_description VARCHAR(500) COMMENT '【v17】计算任务说明',
    solvent_info JSON NOT NULL COMMENT '【v17】体系组分-混合溶剂：[{"name":"EC","molecule_id":1,"mole_ratio":3,"molecule_count":250}]',
    salt_info JSON COMMENT '锂盐信息：[{"name":"LiPF6","cation_id":2,"anion_id":3,"concentration_mol_L":1.0,"molecule_count":85}]',
    additive_info JSON COMMENT '添加剂信息：[{"name":"FEC","molecule_id":4,"mass_fraction":0.05,"molecule_count":40}]',
    molecule_statistics JSON NULL COMMENT '【v17】分子数统计',
    temperature DOUBLE NOT NULL COMMENT '目标模拟温度，单位K',
    pressure DOUBLE NOT NULL COMMENT '目标模拟压强，单位bar',
    box_size JSON NOT NULL COMMENT '【v17】模拟盒子尺寸：{"x":40.0,"y":40.0,"z":40.0,"xy":0.0,"xz":0.0,"yz":0.0}，单位Å（支持三斜晶胞）',
    boundary_conditions VARCHAR(50) NOT NULL DEFAULT 'p p p' COMMENT '【v17】周期性边界条件 p1 p2 p3',
    total_atom_count INT COMMENT '体系总原子数',
    is_public_template TINYINT NOT NULL DEFAULT 0 COMMENT '是否公开模板：0-私有 1-公开',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='【v17】电解液配方体系表：对齐几何模型体系配置规范';

-- =============================================
-- 3. 模拟任务主表 simulation_jobs_table
-- 任务全生命周期管理核心表，覆盖所有管理元数据
-- =============================================
CREATE TABLE simulation_jobs_table (
    job_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '任务唯一ID',
    job_name VARCHAR(200) NOT NULL COMMENT '任务名称',
    system_id BIGINT NOT NULL COMMENT '关联的电解液配方ID',
    software_name VARCHAR(50) NOT NULL COMMENT '【v17】计算软件名称（如LAMMPS）',
    software_version VARCHAR(50) NOT NULL COMMENT '【v17】计算软件版本号',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：PENDING/RUNNING/COMPLETED/FAILED/CANCELLED',
    target_properties JSON NOT NULL COMMENT '目标计算性质：["density","conductivity","viscosity"]',
    hardware_environment VARCHAR(500) NOT NULL COMMENT '【v17】完整计算硬件环境（CPU型号/核心数/内存/GPU型号/数量）',
    job_root_path VARCHAR(500) NOT NULL COMMENT '任务文件根目录路径',
    start_time DATETIME COMMENT '任务开始执行时间',
    end_time DATETIME COMMENT '任务结束时间',
    execution_time_s BIGINT COMMENT '总执行时长，单位秒',
    result_summary JSON COMMENT '计算结果核心摘要（如密度、电导率核心数值）',
    error_message TEXT COMMENT '任务失败错误信息',
    random_seed INT NOT NULL COMMENT '随机数种子（确保可复现）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '任务状态更新时间',
    FOREIGN KEY (system_id) REFERENCES electrolyte_systems_table(system_id) ON DELETE RESTRICT,
    INDEX idx_status (status, create_time),
    INDEX idx_system_job (system_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='【v17】模拟任务主表：完整覆盖管理元数据+模拟条件设置规范';

-- =============================================
-- 4. 模拟任务输入参数详情表 simulation_input_table
-- 存储所有模拟输入参数，确保100%可复现
-- =============================================
CREATE TABLE simulation_input_table (
    input_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '输入参数唯一ID',
    job_id BIGINT NOT NULL UNIQUE COMMENT '关联任务ID',
    ensemble_type VARCHAR(20) NOT NULL COMMENT '系综类型：NVT/NPT/NVE',
    temperature DOUBLE NOT NULL COMMENT '目标温度，单位K（NVT/NPT必需）',
    pressure DOUBLE COMMENT '目标压强，单位bar（NPT必需）',
    thermostat_type VARCHAR(50) NOT NULL COMMENT '热浴类型及耦合常数（如Nose-Hoover, 100fs）',
    barostat_type VARCHAR(50) COMMENT '压浴类型及耦合常数（如Nose-Hoover, 1000fs）',
    time_step_fs DOUBLE NOT NULL DEFAULT 1.0 COMMENT '积分时间步长，单位fs',
    integration_algorithm VARCHAR(50) NOT NULL DEFAULT 'Velocity-Verlet' COMMENT '运动方程积分算法',
    cutoff_distance_ang DOUBLE NOT NULL DEFAULT 10.0 COMMENT '非键相互作用截断距离，单位Å',
    long_range_electrostatics VARCHAR(100) NOT NULL DEFAULT 'PPPM, accuracy 1.0e-4' COMMENT '长程静电算法及精度',
    output_frequency_step INT NOT NULL COMMENT '轨迹/热力学数据输出间隔步数',
    
    -- 【v17】体系拓扑连接（强制）
    force_field_topology_source VARCHAR(500) NOT NULL COMMENT '【v17】力场拓扑来源URI',
    molecule_topology_templates JSON NULL COMMENT '【v17】分子拓扑模板（无外部文件时必填）',
    nonbonded_exclusion_rules JSON NOT NULL COMMENT '【v17】非键排除与缩放规则 1-2/1-3/1-4',
    cross_molecule_topology JSON NULL COMMENT '【v17】跨分子/界面拓扑（聚合物/界面用）',
    
    -- 【v17】初始构型数据（强制）
    solvation_radium DOUBLE NULL COMMENT '【v17】溶剂化半径 单位Å',
    interface_structure VARCHAR(255) NULL COMMENT '【v17】电极/电解液界面结构',
    concentration_gradient VARCHAR(255) NULL COMMENT '【v17】空间浓度梯度',
    
    -- 【v17】能量最小化（显式字段）
    minimization_force_threshold DOUBLE NOT NULL DEFAULT 10.0 COMMENT '【v17】力收敛阈值 单位kJ/(mol·Å) 默认10',
    minimization_max_steps BIGINT NOT NULL DEFAULT 150000 COMMENT '【v17】能量最小化最大迭代次数 默认15万',
    
    -- 【v17】平衡模拟（显式字段）
    equilibrium_density_std_threshold DOUBLE NOT NULL DEFAULT 0.01 COMMENT '【v17】密度收敛阈值 单位g/cm³ 默认0.01',
    equilibrium_temp_fluctuation_range DOUBLE NOT NULL DEFAULT 5.0 COMMENT '【v17】温度波动范围 ±K 默认±5',
    minimum_equilibrium_time DOUBLE NOT NULL DEFAULT 5000.0 COMMENT '【v17】最短平衡时间 单位ps 默认5000',
    
    -- 【v17】生产模拟（显式字段）
    minimum_production_time DOUBLE NOT NULL DEFAULT 50.0 COMMENT '【v17】最小生产模拟时间 单位ns 默认50',
    
    input_file_list JSON COMMENT '所有输入文件路径列表（.in/.data/.pdb等）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (job_id) REFERENCES simulation_jobs_table(job_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='【v17】模拟输入参数表：对齐4大类输入数据规范，保证模拟100%可复现';

-- =============================================
-- 5. 模拟原始输出数据表 simulation_raw_output_table
-- 存储原始轨迹、热力学数据的存储信息
-- =============================================
CREATE TABLE simulation_raw_output_table (
    output_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '原始输出唯一ID',
    job_id BIGINT NOT NULL UNIQUE COMMENT '关联任务ID',
    log_file_path VARCHAR(500) NOT NULL COMMENT 'LAMMPS log.lammps日志文件路径',
    trajectory_file_path VARCHAR(500) NOT NULL COMMENT '原子轨迹dump文件路径',
    stress_tensor_file_path VARCHAR(500) NOT NULL COMMENT '【v17】应力张量分量文件 用于粘度计算',
    dipole_moment_file_path VARCHAR(500) COMMENT '偶极矩输出文件路径',
    box_tilt_factor JSON NOT NULL COMMENT '【v17】盒子倾斜因子 xy/xz/yz 单位Å',
    total_frames BIGINT NOT NULL COMMENT '轨迹总帧数',
    total_simulation_time_ns DOUBLE NOT NULL COMMENT '【v17】总模拟时间 单位ns',
    wrapped_coords_included TINYINT NOT NULL DEFAULT 1 COMMENT '【v17】是否包含缠绕坐标',
    periodic_image_flag_included TINYINT NOT NULL DEFAULT 1 COMMENT '【v17】是否包含周期性镜像标记',
    file_size_gb DECIMAL(10,4) NOT NULL COMMENT '原始输出文件总大小，单位GB',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    FOREIGN KEY (job_id) REFERENCES simulation_jobs_table(job_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='【v17】模拟原始输出数据表：对齐模拟计算原始输出数据规范';

-- =============================================
-- 6. 计算结果详情表 calculation_result_table
-- 存储所有6大类性质计算结果，每个性质一条记录
-- =============================================
CREATE TABLE calculation_result_table (
    result_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '结果唯一ID',
    job_id BIGINT NOT NULL COMMENT '关联任务ID',
    property_name VARCHAR(50) NOT NULL COMMENT '【v17】性质类型：density/viscosity/conductivity/dielectric/solvation',
    property_value DOUBLE NOT NULL COMMENT '性质核心数值结果',
    property_unit VARCHAR(20) NOT NULL COMMENT '数值单位（严格遵循标准附录SI单位）',
    calculation_method VARCHAR(100) NOT NULL COMMENT '计算方法（如Green-Kubo、Nernst-Einstein）',
    temperature_k DOUBLE NOT NULL COMMENT '计算对应的温度，单位K',
    pressure_bar DOUBLE COMMENT '计算对应的压强，单位bar',
    sampling_time_ps DOUBLE NOT NULL COMMENT '【v17】采样时长 单位ps',
    convergence_status VARCHAR(20) NOT NULL COMMENT '收敛性状态：converged/unconverged',
    property_detail JSON NOT NULL COMMENT '性质详细结果（如电导率张量、RDF特征峰等）',
    raw_data_path VARCHAR(500) COMMENT '原始计算数据文件路径',
    chart_data_path VARCHAR(500) COMMENT '可视化图表数据源文件路径',
    
    -- 【v17】密度专属字段
    density_tensor JSON NULL COMMENT '【v17】密度张量 kg/m³',
    component_density JSON NULL COMMENT '【v17】组分密度 溶剂/离子贡献',
    
    -- 【v17】粘度专属字段
    viscosity_value DOUBLE NULL COMMENT '【v17】剪切粘度 单位Pa·s',
    shear_rate DOUBLE NULL COMMENT '【v17】剪切速率 单位s⁻¹（NEMD方法）',
    stress_response DOUBLE NULL COMMENT '【v17】应力响应 单位Pa（NEMD方法）',
    kinematic_viscosity DOUBLE NULL COMMENT '【v17】运动粘度 单位mm²/s',
    
    -- 【v17】电导率专属字段
    conductivity_tensor JSON NULL COMMENT '【v17】电导率张量 单位S/m',
    ion_contribution JSON NULL COMMENT '【v17】各离子电导率贡献占比',
    electric_field_strength DOUBLE NULL COMMENT '【v17】电场强度 单位V/Å',
    resistivity DOUBLE NULL COMMENT '【v17】电阻率 单位Ω·cm',
    
    -- 【v17】介电常数专属字段
    dielectric_constant_tensor JSON NULL COMMENT '【v17】介电常数张量',
    static_dielectric_constant DOUBLE NULL COMMENT '【v17】静态介电常数',
    dielectric_spectrum_data JSON NULL COMMENT '【v17】介电谱数据 频率相关',
    dipole_moment_data JSON NULL COMMENT '【v17】偶极矩数据 单位Debye',
    component_contribution JSON NULL COMMENT '【v17】组分介电贡献',
    system_size JSON NULL COMMENT '【v17】体系尺寸 单位Å',
    
    -- 【v17】溶剂化结构专属字段
    central_ion_type VARCHAR(50) NULL COMMENT '【v17】中心离子类型 Li+/Na+等',
    solvation_shell_structure VARCHAR(255) NULL COMMENT '【v17】溶剂化壳层组成',
    average_coordination_number DOUBLE NULL COMMENT '【v17】平均配位数',
    coordination_distance DOUBLE NULL COMMENT '【v17】配位距离 单位Å',
    rdf_characteristic_peak VARCHAR(255) NULL COMMENT '【v17】RDF特征峰位置&强度',
    hydrogen_bond_feature VARCHAR(255) NULL COMMENT '【v17】氢键网络特征',
    solvation_stability DOUBLE NULL COMMENT '【v17】溶剂化壳层寿命 单位ps',
    ion_solvent_interaction_energy DOUBLE NULL COMMENT '【v17】离子-溶剂相互作用能 单位kJ/mol',
    
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '计算完成时间',
    FOREIGN KEY (job_id) REFERENCES simulation_jobs_table(job_id) ON DELETE CASCADE,
    INDEX idx_job_property (job_id, property_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='【v17】计算结果详情表：100%对齐6大类计算性质数据规范';

-- =============================================
-- 标准示例数据插入（v17规范）
-- =============================================

-- 1. 插入系统预置分子模板（EC、DMC、Li+、PF6-）
INSERT INTO molecule_template_table 
(molecule_name, molecule_type, formula, smiles, molecular_weight, atom_count, net_charge, force_field_type, lt_file_path, single_pdb_path, force_field_params, force_field_topology_source, default_nonbonded_exclusion_rules, is_system_template)
VALUES
('EC', '溶剂', 'C3H4O3', 'C1COC(=O)O1', 88.06, 10, 0.0, 'OPLS-AA 2021', '/system_templates/molecules/ec.lt', '/system_templates/molecules/ec.pdb', 
'{"bonds":[{"type":"C-O","params":[450.0,1.43]}],"angles":[{"type":"O-C-O","params":[70.0,120.0]}],"lj":[{"type":"O-O","params":[0.17,3.5]}]}',
'uri://cstmd/forcefields/opls-aa/2021/ec.top',
'{"exclude_12":true,"exclude_13":true,"vdw_scale_14":0.5,"coulomb_scale_14":0.5}',
1),
('DMC', '溶剂', 'C3H6O3', 'COC(=O)OC', 90.08, 12, 0.0, 'OPLS-AA 2021', '/system_templates/molecules/dmc.lt', '/system_templates/molecules/dmc.pdb',
'{"bonds":[{"type":"C-O","params":[440.0,1.42]}],"angles":[{"type":"O-C-O","params":[65.0,120.0]}],"lj":[{"type":"O-O","params":[0.16,3.4]}]}',
'uri://cstmd/forcefields/opls-aa/2021/dmc.top',
'{"exclude_12":true,"exclude_13":true,"vdw_scale_14":0.5,"coulomb_scale_14":0.5}',
1),
('Li+', '锂盐阳离子', 'Li', '[Li+]', 6.94, 1, 1.0, 'OPLS-AA 2021', '/system_templates/molecules/li.lt', '/system_templates/molecules/li.pdb',
'{"lj":[{"type":"Li-O","params":[0.01,2.1]}]}',
'uri://cstmd/forcefields/opls-aa/2021/li.top',
'{"exclude_12":false,"exclude_13":false,"vdw_scale_14":1.0,"coulomb_scale_14":1.0}',
1),
('PF6-', '锂盐阴离子', 'PF6', '[P-](F)(F)(F)(F)(F)F', 145.0, 7, -1.0, 'OPLS-AA 2021', '/system_templates/molecules/pf6.lt', '/system_templates/molecules/pf6.pdb',
'{"bonds":[{"type":"P-F","params":[500.0,1.58]}],"angles":[{"type":"F-P-F","params":[80.0,90.0]}],"lj":[{"type":"F-O","params":[0.20,3.2]}]}',
'uri://cstmd/forcefields/opls-aa/2021/pf6.top',
'{"exclude_12":true,"exclude_13":true,"vdw_scale_14":0.5,"coulomb_scale_14":0.5}',
1);

-- 2. 插入标准电解液配方（1M LiPF6 in EC:DMC=3:7）
INSERT INTO electrolyte_systems_table
(system_name, task_description, solvent_info, salt_info, temperature, pressure, box_size, total_atom_count)
VALUES
('1M LiPF6 EC:DMC(3:7) 298K', '标准碳酸酯电解液基准测试',
'[{"name":"EC","molecule_id":1,"mole_ratio":0.3,"molecule_count":300},{"name":"DMC","molecule_id":2,"mole_ratio":0.7,"molecule_count":700}]',
'[{"name":"LiPF6","cation_id":3,"anion_id":4,"concentration_mol_L":1.0,"molecule_count":85}]',
298.15, 1.0, '{"x":50.0,"y":50.0,"z":50.0,"xy":0.0,"xz":0.0,"yz":0.0}', 15000);

-- 3. 插入模拟任务示例
INSERT INTO simulation_jobs_table
(job_name, system_id, software_name, software_version, status, target_properties, hardware_environment, job_root_path, random_seed)
VALUES
('基准测试-1M LiPF6 EC:DMC', 1, 'LAMMPS', '29 Sep 2021', 'PENDING',
'["density","conductivity","viscosity","dielectric_constant","solvation_structure"]',
'Intel Xeon Gold 6248R × 32核, 128GB内存, NVIDIA A100 × 1',
'/mnt/md_platform_data/user_1/jobs/job_1', 123456789);

-- 4. 插入模拟输入参数示例（v17标准参数）
INSERT INTO simulation_input_table
(job_id, ensemble_type, temperature, pressure, thermostat_type, barostat_type, time_step_fs, integration_algorithm, cutoff_distance_ang, long_range_electrostatics, output_frequency_step,
force_field_topology_source, nonbonded_exclusion_rules, minimization_force_threshold, minimization_max_steps,
equilibrium_density_std_threshold, equilibrium_temp_fluctuation_range, minimum_equilibrium_time, minimum_production_time)
VALUES
(1, 'NPT', 298.15, 1.0, 'Nose-Hoover, 100fs', 'Nose-Hoover, 1000fs', 1.0, 'Velocity-Verlet', 10.0, 'PPPM, accuracy 1.0e-4', 1000,
'uri://cstmd/forcefields/opls-aa/2021/system.top',
'{"exclude_12":true,"exclude_13":true,"vdw_scale_14":0.5,"coulomb_scale_14":0.5}',
10.0, 150000, 0.01, 5.0, 5000.0, 50.0);
```

