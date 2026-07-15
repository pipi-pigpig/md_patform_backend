package com.mdplatform.management.repository;

import com.mdplatform.management.model.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户数据访问层，提供用户信息的CRUD操作和自定义查询
 *
 * <p>功能：
 *     1. 根据用户名、邮箱查询用户
 *     2. 根据机构、角色查询用户列表
 *     3. 检查用户名和邮箱是否已存在
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Repository
public interface UserRepository extends JpaRepository<SysUser, Long> {

    /**
     * 根据用户名查询用户
     *
     * <p>用户名具有唯一性约束，最多返回一条记录</p>
     *
     * @param username 用户名
     * @return 匹配的用户，若不存在则返回空的Optional
     */
    Optional<SysUser> findByUsername(String username);

    /**
     * 根据邮箱查询用户
     *
     * <p>邮箱具有唯一性约束，最多返回一条记录</p>
     *
     * @param email 用户邮箱
     * @return 匹配的用户，若不存在则返回空的Optional
     */
    Optional<SysUser> findByEmail(String email);

    /**
     * 根据机构查询用户列表
     *
     * @param organization 机构名称
     * @return 属于该机构的用户列表
     */
    List<SysUser> findByOrganization(String organization);

    /**
     * 根据角色ID查询用户列表
     *
     * @param roleId 角色ID
     * @return 拥有该角色的用户列表
     */
    List<SysUser> findByRoleId(Long roleId);

    /**
     * 检查用户名是否已存在
     *
     * @param username 用户名
     * @return 若用户名已存在则返回true，否则返回false
     */
    boolean existsByUsername(String username);

    /**
     * 检查邮箱是否已存在
     *
     * @param email 用户邮箱
     * @return 若邮箱已存在则返回true，否则返回false
     */
    boolean existsByEmail(String email);
}