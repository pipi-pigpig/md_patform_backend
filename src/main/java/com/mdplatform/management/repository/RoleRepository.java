package com.mdplatform.management.repository;

import com.mdplatform.management.model.SysRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 角色数据访问层，提供角色信息的查询操作
 *
 * <p>功能：
 *     1. 根据角色编码查询角色
 *     2. 根据角色名称查询角色
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Repository
public interface RoleRepository extends JpaRepository<SysRole, Long> {

    /**
     * 根据角色编码查询角色
     *
     * <p>角色编码具有唯一性约束（如ROLE_ADMIN、ROLE_USER等），最多返回一条记录</p>
     *
     * @param roleCode 角色编码
     * @return 匹配的角色，若不存在则返回空的Optional
     */
    Optional<SysRole> findByRoleCode(String roleCode);

    /**
     * 根据角色名称查询角色
     *
     * <p>角色名称具有唯一性约束（如管理员、普通用户等），最多返回一条记录</p>
     *
     * @param roleName 角色名称
     * @return 匹配的角色，若不存在则返回空的Optional
     */
    Optional<SysRole> findByRoleName(String roleName);
}