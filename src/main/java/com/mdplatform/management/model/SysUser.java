package com.mdplatform.management.model;

import javax.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sys_user_table")
public class SysUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", unique = true, nullable = false, length = 50)
    private String username;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "real_name", length = 50)
    private String realName;

    @Column(name = "email", unique = true, length = 100)
    private String email;

    @Column(name = "organization", length = 200)
    private String organization;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "role_id")
    private Long roleId;

    @Column(name = "status")
    private Integer status;

    @Column(name = "used_storage_gb", precision = 10, scale = 2)
    private BigDecimal usedStorageGb;

    @Column(name = "last_login_time")
    private LocalDateTime lastLoginTime;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}