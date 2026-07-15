package com.mdplatform.engine.model;

import javax.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 模拟输入参数实体类，对应simulation_input_table表，存储模拟的所有输入参数
 *
 * <p>功能：
 *     1. 存储分子动力学模拟的全部输入参数配置
 *     2. 包含系综类型、恒温器、恒压器等热力学控制参数
 *     3. 包含力场拓扑、分子模板等力场相关配置
 *     4. 包含能量最小化、平衡模拟、生产模拟三阶段参数
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Data
@Entity
@Table(name = "simulation_input_table")
public class SimulationInput {

    /** 输入参数ID，主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "input_id")
    private Long inputId;

    /** 任务ID，关联simulation_jobs_table表，唯一约束 */
    @Column(name = "job_id", nullable = false, unique = true)
    private Long jobId;

    /** 系综类型（如NVT-正则系综、NPT-等温等压系综、NVE-微正则系综） */
    @Column(name = "ensemble_type", nullable = false)
    private String ensembleType;

    /** 恒温器类型（如Nose-Hoover、Berendsen、Langevin等） */
    @Column(name = "thermostat_type", nullable = false)
    private String thermostatType;

    /** 恒压器类型（如Parrinello-Rahman、Berendsen等），NPT系综时必填 */
    @Column(name = "barostat_type")
    private String barostatType;

    // ==================== 温度与压力 ====================

    /** 目标温度(K)，默认298.15 */
    @Column(name = "temperature", nullable = false)
    private Double temperature;

    /** 目标压强(bar) */
    @Column(name = "pressure")
    private Double pressure;

    // ==================== 时间步长与积分算法 ====================

    /** 时间步长（单位：fs），默认1.0 */
    @Column(name = "time_step_fs", nullable = false)
    private Double timeStepFs = 1.0;

    /** 积分算法（如Velocity-Verlet），默认Velocity-Verlet */
    @Column(name = "integration_algorithm", nullable = false)
    private String integrationAlgorithm = "Velocity-Verlet";

    /** 截断距离（单位：Å），默认10.0 */
    @Column(name = "cutoff_distance_ang", nullable = false)
    private Double cutoffDistanceAng = 10.0;

    /** 长程静电方法（如PPPM, accuracy 1.0e-4），默认PPPM */
    @Column(name = "long_range_electrostatics", nullable = false)
    private String longRangeElectrostatics = "PPPM, accuracy 1.0e-4";

    // ==================== 力场拓扑相关字段（v17）====================

    /** 力场拓扑来源URI，默认'uri://...' */
    @Column(name = "force_field_topology_source", length = 500, nullable = false)
    private String forceFieldTopologySource;

    /** 分子拓扑模板（JSON格式） */
    @Column(name = "molecule_topology_templates", columnDefinition = "JSON")
    private String moleculeTopologyTemplates;

    /** 非键排除与缩放规则（JSON格式） */
    @Column(name = "nonbonded_exclusion_rules", columnDefinition = "JSON")
    private String nonbondedExclusionRules;

    /** 跨分子拓扑（JSON格式） */
    @Column(name = "cross_molecule_topology", columnDefinition = "JSON")
    private String crossMoleculeTopology;

    /** 溶剂化半径(Å) */
    @Column(name = "solvation_radium")
    private Double solvationRadium;

    /** 电极/电解液界面结构 */
    @Column(name = "interface_structure", length = 255)
    private String interfaceStructure;

    /** 空间浓度梯度 */
    @Column(name = "concentration_gradient", length = 255)
    private String concentrationGradient;

    // ==================== 输出控制 ====================

    /** 输出频率（每隔多少步输出一次轨迹和热力学数据） */
    @Column(name = "output_frequency_step", nullable = false)
    private Integer outputFrequencyStep;

    /** 能量最小化参数（JSON格式，包含最小化算法和收敛标准） */
    @Column(name = "minimization_params", columnDefinition = "JSON", nullable = false)
    private String minimizationParams;

    // ==================== 能量最小化参数 ====================

    /** 力收敛阈值(force units)，默认1.0e-4（与etol一致） */
    @Column(name = "minimization_force_threshold", nullable = false)
    private Double minimizationForceThreshold = 1.0e-4;

    /** 最大迭代次数，默认150000 */
    @Column(name = "minimization_max_steps", nullable = false)
    private Long minimizationMaxSteps = 150000L;

    // ==================== 平衡模拟参数 ====================

    /** 平衡模拟参数（JSON格式，包含平衡阶段的时间步数和控制参数） */
    @Column(name = "equilibrium_params", columnDefinition = "JSON", nullable = false)
    private String equilibriumParams;

    /** 密度收敛阈值(g/cm³)，默认0.01 */
    @Column(name = "equilibrium_density_std_threshold", nullable = false)
    private Double equilibriumDensityStdThreshold = 0.01;

    /** 温度波动范围(±K)，默认5.0 */
    @Column(name = "equilibrium_temp_fluctuation_range", nullable = false)
    private Double equilibriumTempFluctuationRange = 5.0;

    /** 最短平衡时间(ps)，默认5000.0 */
    @Column(name = "minimum_equilibrium_time", nullable = false)
    private Double minimumEquilibriumTime = 5000.0;

    // ==================== 生产模拟参数 ====================

    /** 生产模拟参数（JSON格式，包含生产阶段的模拟时间和输出配置） */
    @Column(name = "production_params", columnDefinition = "JSON", nullable = false)
    private String productionParams;

    /** 最小生产模拟时间(ns)，默认50.0 */
    @Column(name = "minimum_production_time", nullable = false)
    private Double minimumProductionTime = 50.0;

    /** 初始构型来源（如packmol-从Packmol堆积生成、restart-从重启文件读取） */
    @Column(name = "initial_config_source", nullable = false)
    private String initialConfigSource;

    /** 初始速度分布方式（如Maxwell-Boltzmann-麦克斯韦-玻尔兹曼分布、uniform-均匀分布） */
    @Column(name = "initial_velocity_distribution", nullable = false)
    private String initialVelocityDistribution;

    /** 输入文件列表（JSON格式，记录所有输入文件的相对路径） */
    @Column(name = "input_file_list", columnDefinition = "JSON")
    private String inputFileList;

    /** 创建时间，实体持久化时自动设置 */
    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
    }
}