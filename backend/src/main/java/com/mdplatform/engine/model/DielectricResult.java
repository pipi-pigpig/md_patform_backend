package com.mdplatform.engine.model;

import javax.persistence.*;
import lombok.Data;

/**
 * 介电常数计算结果实体类，对应dielectric_result_table表，存储介电常数计算的专属数据
 *
 * <p>功能：存储介电常数计算的专属结果数据，包括介电常数张量、静态介电常数、介电谱、偶极矩等。
 * 通过result_id与calculation_result_table主表一对一关联。</p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Data
@Entity
@Table(name = "dielectric_result_table")
public class DielectricResult {

    /** 计算结果ID，主键同时为外键关联calculation_result_table */
    @Id
    @Column(name = "result_id")
    private Long resultId;

    /** 介电常数张量（JSON格式，包含xx/yy/zz分量） */
    @Column(name = "dielectric_constant_tensor", columnDefinition = "JSON")
    private String dielectricConstantTensor;

    /** 静态介电常数 */
    @Column(name = "static_dielectric_constant")
    private Double staticDielectricConstant;

    /** 介电谱数据（JSON格式，频率-介电常数关系） */
    @Column(name = "dielectric_spectrum_data", columnDefinition = "JSON")
    private String dielectricSpectrumData;

    /** 偶极矩数据（JSON格式，单位：Debye） */
    @Column(name = "dipole_moment_data", columnDefinition = "JSON")
    private String dipoleMomentData;

    /** 组分介电贡献（JSON格式，各组分的介电常数贡献） */
    @Column(name = "component_contribution", columnDefinition = "JSON")
    private String componentContribution;

    /** 体系尺寸（JSON格式，单位：Å） */
    @Column(name = "system_size", columnDefinition = "JSON")
    private String systemSize;
}
