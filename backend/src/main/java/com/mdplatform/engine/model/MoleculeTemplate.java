package com.mdplatform.engine.model;

import javax.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 分子模板实体类，对应molecule_template_table表，存储分子模板的力场和结构信息
 *
 * <p>功能：
 *     1. 存储分子的基本信息（名称、分子式、SMILES等）
 *     2. 记录分子的力场类型和力场参数
 *     3. 管理分子的结构文件路径（Moltemplate模板文件、PDB文件）
 *     4. 支持系统预置模板和用户自定义模板
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Data
@Entity
@Table(name = "molecule_template_table")
public class MoleculeTemplate {

    /** 分子ID，主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "molecule_id")
    private Long moleculeId;

    /** 分子名称（唯一，如EC、DMC、LiPF6等） */
    @Column(name = "molecule_name", unique = true, nullable = false)
    private String moleculeName;

    /** 分子类型（如solvent-溶剂、salt-盐、additive-添加剂） */
    @Column(name = "molecule_type")
    private String moleculeType;

    /** 分子式（如C3H4O3、LiPF6等） */
    private String formula;

    /** SMILES表示（分子的简化分子线性输入规范） */
    private String smiles;

    /** 分子量（单位：g/mol） */
    @Column(name = "molecular_weight")
    private Double molecularWeight;

    /** 原子数（分子中包含的原子总数） */
    @Column(name = "atom_count")
    private Integer atomCount;

    /** 净电荷（单位：e，如Li⁺为+1.0、PF6⁻为-1.0） */
    @Column(name = "net_charge")
    private Double netCharge;

    /** 力场类型（如OPLS-AA、GAFF、AMBER等） */
    @Column(name = "force_field_type")
    private String forceFieldType;

    /** Moltemplate模板文件路径（相对路径） */
    @Column(name = "lt_file_path", length = 500)
    private String ltFilePath;

    /** 单分子PDB结构文件路径（相对路径） */
    @Column(name = "single_pdb_path", length = 500)
    private String singlePdbPath;

    /** 力场参数（JSON格式，包含键参数、非键参数等） */
    @Column(name = "force_field_params", columnDefinition = "JSON")
    private String forceFieldParams;

    // ==================== 力场拓扑相关字段（v17）====================

    /** 力场拓扑来源文件URI */
    @Column(name = "force_field_topology_source", length = 500)
    private String forceFieldTopologySource;

    /** 默认非键排除与缩放规则（JSON格式） */
    @Column(name = "default_nonbonded_exclusion_rules", columnDefinition = "JSON")
    private String defaultNonbondedExclusionRules;

    // ==================== 文件与描述信息 ====================

    /** 分子描述信息（详细说明分子的用途、来源等） */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 是否为系统预置模板（true-系统预置，所有用户可用；false-用户自定义） */
    @Column(name = "is_system_template")
    private Boolean isSystemTemplate;

    /** 创建用户ID，关联sys_user_table表 */
    @Column(name = "create_user_id")
    private Long createUserId;

    /** 创建时间 */
    @Column(name = "create_time")
    private LocalDateTime createTime = LocalDateTime.now();

    /** 更新时间，实体更新时自动设置 */
    @Column(name = "update_time")
    private LocalDateTime updateTime = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}