package com.mdplatform.common.security;

import com.mdplatform.management.model.SysUser;
import lombok.Getter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collections;
import java.util.List;

/**
 * 自定义用户详情类，扩展Spring Security的User类，添加用户ID等额外信息
 *
 * <p>功能：
 *     1. 扩展Spring Security用户详情，添加用户ID、邮箱、真实姓名、机构等字段
 *     2. 支持从数据库角色信息构建权限列表，替代硬编码角色
 *     3. 提供从SysUser实体创建CustomUserDetails的工厂方法
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Getter
public class CustomUserDetails extends User {

    /** 用户ID，用于业务逻辑中的用户标识 */
    private final Long userId;
    /** 用户邮箱 */
    private final String email;
    /** 用户真实姓名 */
    private final String realName;
    /** 用户所属机构 */
    private final String organization;

    /**
     * 构造方法 - 使用指定权限列表创建CustomUserDetails
     *
     * @param userId       用户ID
     * @param username     用户名
     * @param password     加密后的密码
     * @param email        用户邮箱
     * @param realName     用户真实姓名
     * @param organization 用户所属机构
     * @param authorities  用户权限列表
     */
    public CustomUserDetails(Long userId, String username, String password,
                             String email, String realName, String organization,
                             List<SimpleGrantedAuthority> authorities) {
        super(username, password, authorities);
        this.userId = userId;
        this.email = email;
        this.realName = realName;
        this.organization = organization;
    }

    /**
     * 从SysUser实体和角色编码创建CustomUserDetails实例
     *
     * <p>使用数据库中的角色编码构建权限列表，角色编码格式如ROLE_ADMIN、ROLE_USER等，
     * 若角色编码不以"ROLE_"前缀开头则自动添加</p>
     *
     * @param user     SysUser实体对象
     * @param roleCode 角色编码（来自数据库sys_role_table）
     * @return 包含用户信息和角色权限的CustomUserDetails实例
     */
    public static CustomUserDetails fromEntity(SysUser user, String roleCode) {
        // 根据数据库角色信息构建权限列表，替代硬编码ROLE_USER
        List<SimpleGrantedAuthority> authorities;
        if (roleCode != null && !roleCode.isEmpty()) {
            // 确保角色编码以ROLE_前缀开头，符合Spring Security规范
            if (!roleCode.startsWith("ROLE_")) {
                roleCode = "ROLE_" + roleCode;
            }
            authorities = Collections.singletonList(new SimpleGrantedAuthority(roleCode));
        } else {
            // 若用户无角色信息，默认赋予ROLE_USER权限
            authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
        }

        return new CustomUserDetails(
                user.getUserId(),
                user.getUsername(),
                user.getPassword(),
                user.getEmail(),
                user.getRealName(),
                user.getOrganization(),
                authorities
        );
    }
}