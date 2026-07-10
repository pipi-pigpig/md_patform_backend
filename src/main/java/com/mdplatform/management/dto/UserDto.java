package com.mdplatform.management.dto;

import com.mdplatform.management.model.SysUser;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户信息响应DTO - 用于返回用户信息，排除敏感字段
 *
 * <p>功能：封装用户信息响应数据，排除密码等敏感字段，确保API响应安全</p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
public class UserDto {

    /** 用户ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 真实姓名 */
    private String realName;

    /** 邮箱 */
    private String email;

    /** 所属机构 */
    private String organization;

    /** 电话 */
    private String phone;

    /** 角色ID */
    private Long roleId;

    /** 用户状态（1-正常，0-禁用） */
    private Integer status;

    /** 已使用存储空间（GB） */
    private BigDecimal usedStorageGb;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    public UserDto() {}

    /**
     * 从SysUser实体构建UserDto
     *
     * @param user SysUser实体
     * @return UserDto对象，不包含密码等敏感信息
     */
    public static UserDto fromEntity(SysUser user) {
        if (user == null) return null;
        UserDto dto = new UserDto();
        dto.userId = user.getUserId();
        dto.username = user.getUsername();
        dto.realName = user.getRealName();
        dto.email = user.getEmail();
        dto.organization = user.getOrganization();
        dto.phone = user.getPhone();
        dto.roleId = user.getRoleId();
        dto.status = user.getStatus();
        dto.usedStorageGb = user.getUsedStorageGb();
        dto.lastLoginTime = user.getLastLoginTime();
        dto.createTime = user.getCreateTime();
        dto.updateTime = user.getUpdateTime();
        return dto;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public BigDecimal getUsedStorageGb() { return usedStorageGb; }
    public void setUsedStorageGb(BigDecimal usedStorageGb) { this.usedStorageGb = usedStorageGb; }

    public LocalDateTime getLastLoginTime() { return lastLoginTime; }
    public void setLastLoginTime(LocalDateTime lastLoginTime) { this.lastLoginTime = lastLoginTime; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
