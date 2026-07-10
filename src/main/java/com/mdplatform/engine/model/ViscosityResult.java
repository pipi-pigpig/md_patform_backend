package com.mdplatform.engine.model;

import javax.persistence.*;
import lombok.Data;

/**
 * 粘度计算结果实体类，对应viscosity_result_table表，存储粘度计算的专属数据
 *
 * <p>功能：存储粘度计算的专属结果数据，包括剪切粘度、剪切速率、应力响应和运动粘度。
 * 通过result_id与calculation_result_table主表一对一关联。</p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Data
@Entity
@Table(name = "viscosity_result_table")
public class ViscosityResult {

    /** 计算结果ID，主键同时为外键关联calculation_result_table */
    @Id
    @Column(name = "result_id")
    private Long resultId;

    /** 剪切粘度值（单位：Pa·s） */
    @Column(name = "viscosity_value")
    private Double viscosityValue;

    /** 剪切速率（单位：s⁻¹） */
    @Column(name = "shear_rate")
    private Double shearRate;

    /** 应力响应（单位：Pa） */
    @Column(name = "stress_response")
    private Double stressResponse;

    /** 运动粘度（单位：mm²/s） */
    @Column(name = "kinematic_viscosity")
    private Double kinematicViscosity;
}
