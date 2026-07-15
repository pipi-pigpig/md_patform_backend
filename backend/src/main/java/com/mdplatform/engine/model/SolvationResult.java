package com.mdplatform.engine.model;

import javax.persistence.*;
import lombok.Data;

/**
 * 溶剂化结构计算结果实体类，对应solvation_result_table表，存储溶剂化结构计算的专属数据
 *
 * <p>功能：存储溶剂化结构计算的专属结果数据，包括中心离子类型、壳层结构、配位数、RDF特征等。
 * 通过result_id与calculation_result_table主表一对一关联。</p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Data
@Entity
@Table(name = "solvation_result_table")
public class SolvationResult {

    /** 计算结果ID，主键同时为外键关联calculation_result_table */
    @Id
    @Column(name = "result_id")
    private Long resultId;

    /** 中心离子类型（如Li⁺、Na⁺等） */
    @Column(name = "central_ion_type", length = 50)
    private String centralIonType;

    /** 溶剂化壳层组成描述 */
    @Column(name = "solvation_shell_structure", length = 255)
    private String solvationShellStructure;

    /** 平均配位数 */
    @Column(name = "average_coordination_number")
    private Double averageCoordinationNumber;

    /** 配位距离（单位：Å） */
    @Column(name = "coordination_distance")
    private Double coordinationDistance;

    /** RDF特征峰位置及强度 */
    @Column(name = "rdf_characteristic_peak", length = 255)
    private String rdfCharacteristicPeak;

    /** 氢键网络特征描述 */
    @Column(name = "hydrogen_bond_feature", length = 255)
    private String hydrogenBondFeature;

    /** 溶剂化壳层寿命（单位：ps） */
    @Column(name = "solvation_stability")
    private Double solvationStability;

    /** 离子-溶剂相互作用能（单位：kJ/mol） */
    @Column(name = "ion_solvent_interaction_energy")
    private Double ionSolventInteractionEnergy;
}
