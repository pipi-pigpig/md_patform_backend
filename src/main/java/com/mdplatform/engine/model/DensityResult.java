package com.mdplatform.engine.model;

import javax.persistence.*;
import lombok.Data;

/**
 * 密度计算结果实体类，对应density_result_table表，存储密度计算的专属数据
 *
 * <p>功能：存储密度计算的专属结果数据，包括密度张量和组分密度分布。
 * 通过result_id与calculation_result_table主表一对一关联。</p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Data
@Entity
@Table(name = "density_result_table")
public class DensityResult {

    /** 计算结果ID，主键同时为外键关联calculation_result_table */
    @Id
    @Column(name = "result_id")
    private Long resultId;

    /** 密度张量（JSON格式，单位：kg/m³，包含xx/yy/zz分量） */
    @Column(name = "density_tensor", columnDefinition = "JSON")
    private String densityTensor;

    /** 组分密度（JSON格式，各组分密度分布，如溶剂/盐的密度贡献） */
    @Column(name = "component_density", columnDefinition = "JSON")
    private String componentDensity;
}
