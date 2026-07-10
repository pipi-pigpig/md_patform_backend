package com.mdplatform.engine.model;

import javax.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 计算结果实体类，对应calculation_result_table表，存储后处理计算的公共结果信息
 *
 * <p>功能：
 *     1. 存储各类物性计算的公共结果（数值、单位、计算方法、收敛状态等）
 *     2. 记录计算条件（温度、压强、采样时间）
 *     3. 存储统计摘要信息（property_detail字段）
 *     4. 各性质的专属数据存储在对应的子表中（density_result_table、viscosity_result_table等）
 * </p>
 *
 * <p>子表关系（按property_name区分）：</p>
 * <ul>
 *     <li>density → density_result_table</li>
 *     <li>viscosity → viscosity_result_table</li>
 *     <li>conductivity → conductivity_result_table</li>
 *     <li>dielectric → dielectric_result_table</li>
 *     <li>solvation → solvation_result_table</li>
 * </ul>
 *
 * @author 电解液MD平台
 * @version 2.0.0
 */
@Data
@Entity
@Table(name = "calculation_result_table")
public class CalculationResult {

    /** 计算结果ID，主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "result_id")
    private Long resultId;

    /** 任务ID，关联simulation_jobs_table表 */
    @Column(name = "job_id", nullable = false)
    private Long jobId;

    /** 属性名称（如density-密度、conductivity-电导率、viscosity-粘度、dielectric-介电常数、solvation-溶剂化结构） */
    @Column(name = "property_name", nullable = false)
    private String propertyName;

    /** 属性值（主计算结果数值） */
    @Column(name = "property_value", nullable = false)
    private Double propertyValue;

    /** 属性单位（如kg/m³、S/m、Pa·s等） */
    @Column(name = "property_unit", nullable = false)
    private String propertyUnit;

    /** 计算方法（如Green-Kubo、Einstein、直接统计等） */
    @Column(name = "calculation_method", nullable = false)
    private String calculationMethod;

    /** 计算温度（单位：K） */
    @Column(name = "temperature_k", nullable = false)
    private Double temperatureK;

    /** 计算压强（单位：bar） */
    @Column(name = "pressure_bar")
    private Double pressureBar;

    /** 采样时间（单位：ps），用于计算该结果的数据采样时长 */
    @Column(name = "sampling_time_ps", nullable = false)
    private Double samplingTimePs;

    /** 收敛状态（CONVERGED-已收敛、NOT_CONVERGED-未收敛、PARTIALLY_CONVERGED-部分收敛） */
    @Column(name = "convergence_status", nullable = false)
    private String convergenceStatus;

    /** 性质统计摘要（JSON格式，包含mean、standard_deviation、standard_error、sample_size、equilibration_fraction等统计信息） */
    @Column(name = "property_detail", columnDefinition = "JSON", nullable = false)
    private String propertyDetail;

    /** 原始数据文件路径（相对路径） */
    @Column(name = "raw_data_path")
    private String rawDataPath;

    /** 图表数据文件路径（相对路径，存储可视化图表所需的数据） */
    @Column(name = "chart_data_path")
    private String chartDataPath;

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
