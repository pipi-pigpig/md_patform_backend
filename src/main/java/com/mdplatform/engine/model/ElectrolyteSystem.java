package com.mdplatform.engine.model;

import javax.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 电解液系统实体类，对应electrolyte_systems_table表，存储电解液配方信息
 *
 * <p>功能：
 *     1. 定义电解液体系的化学组成（溶剂、盐、添加剂）
 *     2. 存储体系的热力学状态（温度、压强）
 *     3. 记录模拟盒子的几何尺寸和边界条件
 *     4. 管理配方模板的公开/私有状态
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Data
@Entity
@Table(name = "electrolyte_systems_table")
public class ElectrolyteSystem {

    /** 系统ID，主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "system_id")
    private Long systemId;

    /** 系统名称（如"1M LiPF6 EC:DMC 1:1"） */
    @Column(name = "system_name", nullable = false)
    private String systemName;

    /** 用户ID，关联sys_user_table表 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 任务描述（说明该配方的用途和背景） */
    @Column(name = "task_description")
    private String taskDescription;

    /** 溶剂信息（JSON格式，包含溶剂种类、摩尔比、分子数等） */
    @Column(name = "solvent_info", nullable = false, columnDefinition = "JSON")
    private String solventInfo;

    /** 盐类信息（JSON格式，包含盐的种类、浓度、分子数等） */
    @Column(name = "salt_info", columnDefinition = "JSON")
    private String saltInfo;

    /** 添加剂信息（JSON格式，包含添加剂的种类、浓度、分子数等） */
    @Column(name = "additive_info", columnDefinition = "JSON")
    private String additiveInfo;

    /** 体系温度（单位：K） */
    @Column(name = "temperature", nullable = false)
    private Double temperature;

    /** 体系压强（单位：bar） */
    @Column(name = "pressure", nullable = false)
    private Double pressure;

    /** 模拟盒子尺寸（JSON格式，包含长宽高，单位：Å） */
    @Column(name = "box_size", nullable = false, columnDefinition = "JSON")
    private String boxSize;

    /** 边界条件（如"p p p"表示三个方向均为周期性边界） */
    @Column(name = "boundary_conditions", nullable = false)
    private String boundaryConditions = "p p p";

    /**
     * 体系总原子数
     */
    @Column(name = "total_atom_count")
    private Integer totalAtomCount;

    /**
     * 分子数统计（JSON格式，包含各组分分子数量）
     */
    @Column(name = "molecule_statistics", columnDefinition = "JSON")
    private String moleculeStatistics;

    /** 是否为公共模板（true-公开模板，所有用户可见；false-私有模板，仅创建者可见） */
    @Column(name = "is_public_template", nullable = false)
    private Boolean isPublicTemplate = false;

    /** 创建时间，实体持久化时自动设置 */
    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    /** 更新时间，实体更新时自动设置 */
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
        if (updateTime == null) {
            updateTime = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}