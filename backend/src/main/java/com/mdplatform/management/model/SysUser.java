package com.mdplatform.management.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import javax.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 系统用户实体类，对应sys_user_table表，存储用户账号信息
 *
 * <p>功能：
 *     1. 用户基本信息存储（用户名、姓名、邮箱等）
 *     2. 用户认证与授权数据（密码、角色）
 *     3. 用户存储配额管理（已用空间、资源限制）
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Data
@Entity
@Table(name = "sys_user_table")
public class SysUser {

    /** 用户ID，主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    /** 用户名，唯一且不可为空 */
    @Column(name = "username", unique = true, nullable = false, length = 50)
    private String username;

    /** 用户密码（BCrypt加密），序列化时忽略以防止泄露 */
    @JsonIgnore
    @Column(name = "password", nullable = false, length = 255)
    private String password;

    /** 用户真实姓名 */
    @Column(name = "real_name", length = 50)
    private String realName;

    /** 用户邮箱，唯一 */
    @Column(name = "email", unique = true, length = 100)
    private String email;

    /** 用户所属机构 */
    @Column(name = "organization", length = 200)
    private String organization;

    /** 用户手机号 */
    @Column(name = "phone", length = 20)
    private String phone;

    /** 用户角色ID */
    @Column(name = "role_id")
    private Long roleId;

    /** 用户状态（0-禁用，1-启用） */
    @Column(name = "status")
    private Integer status;

    /** 已使用存储空间（GB） */
    @Column(name = "used_storage_gb", precision = 10, scale = 2)
    private BigDecimal usedStorageGb;

    /** 最后登录时间 */
    @Column(name = "last_login_time")
    private LocalDateTime lastLoginTime;

    /** 创建时间 */
    @Column(name = "create_time")
    private LocalDateTime createTime;

    /** 更新时间，自动维护 */
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    /** 实体更新时自动设置更新时间 */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
