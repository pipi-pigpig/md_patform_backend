package com.mdplatform.engine.model;

import javax.persistence.*;
import lombok.Data;

/**
 * 电导率计算结果实体类，对应conductivity_result_table表，存储电导率计算的专属数据
 *
 * <p>功能：存储电导率计算的专属结果数据，包括电导率张量、离子贡献、电场强度和电阻率。
 * 通过result_id与calculation_result_table主表一对一关联。</p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Data
@Entity
@Table(name = "conductivity_result_table")
public class ConductivityResult {

    /** 计算结果ID，主键同时为外键关联calculation_result_table */
    @Id
    @Column(name = "result_id")
    private Long resultId;

    /** 电导率张量（JSON格式，单位：S/m，包含xx/yy/zz分量） */
    @Column(name = "conductivity_tensor", columnDefinition = "JSON")
    private String conductivityTensor;

    /** 各离子电导率贡献占比（JSON格式，如Li⁺和PF₆⁻的分别贡献） */
    @Column(name = "ion_contribution", columnDefinition = "JSON")
    private String ionContribution;

    /** 电场强度（单位：V/Å） */
    @Column(name = "electric_field_strength")
    private Double electricFieldStrength;

    /** 电阻率（单位：Ω·cm） */
    @Column(name = "resistivity")
    private Double resistivity;
}
