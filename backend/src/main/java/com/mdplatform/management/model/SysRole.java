package com.mdplatform.management.model;

import javax.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系统角色实体类，对应sys_role_table表，存储角色权限信息
 *
 * <p>功能：
 *     1. 定义系统角色（如管理员、普通用户等）
 *     2. 管理角色对应的资源配额和并发限制
 *     3. 通过角色编码实现权限控制
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Data
@Entity
@Table(name = "sys_role_table")
public class SysRole {

    /** 角色ID，主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Long roleId;

    /** 角色名称（如系统管理员、普通用户、高级用户等） */
    @Column(name = "role_name", unique = true, nullable = false)
    private String roleName;

    /** 角色编码（如ADMIN、USER、ADVANCED_USER等，用于权限判断） */
    @Column(name = "role_code", unique = true, nullable = false)
    private String roleCode;

    /** 角色描述（详细说明角色的权限范围和适用场景） */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** 资源配额（单位：GB），该角色用户的最大存储空间限制 */
    @Column(name = "resource_quota_gb")
    private Long resourceQuotaGb;

    /** 最大并行任务数，该角色用户可同时运行的最大任务数 */
    @Column(name = "max_parallel_jobs")
    private Integer maxParallelJobs;

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