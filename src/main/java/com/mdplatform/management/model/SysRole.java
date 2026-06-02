package com.mdplatform.management.model;

import javax.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sys_role_table")
public class SysRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Long roleId;

    @Column(name = "role_name", unique = true, nullable = false)
    private String roleName;

    @Column(name = "role_code", unique = true, nullable = false)
    private String roleCode;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "resource_quota_gb")
    private Long resourceQuotaGb;

    @Column(name = "max_parallel_jobs")
    private Integer maxParallelJobs;

    @Column(name = "create_time")
    private LocalDateTime createTime = LocalDateTime.now();

    @Column(name = "update_time")
    private LocalDateTime updateTime = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}