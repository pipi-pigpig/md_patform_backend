package com.mdplatform.common.security;

import com.mdplatform.management.model.SysRole;
import com.mdplatform.management.model.SysUser;
import com.mdplatform.management.repository.RoleRepository;
import com.mdplatform.management.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * 自定义用户详情服务，实现Spring Security的UserDetailsService接口
 *
 * <p>功能：
 *     1. 根据用户名加载用户详情（实现UserDetailsService接口）
 *     2. 根据用户ID加载用户详情
 *     3. 查询用户实体和角色信息
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    /** 用户数据访问层 */
    private final UserRepository userRepository;
    /** 角色数据访问层，用于查询用户角色编码 */
    private final RoleRepository roleRepository;

    /**
     * 根据用户名加载用户详情（实现UserDetailsService接口方法）
     *
     * <p>查询用户信息并关联查询角色编码，构建包含数据库角色权限的CustomUserDetails</p>
     *
     * @param username 用户名
     * @return 包含用户信息和角色权限的UserDetails对象
     * @throws UsernameNotFoundException 用户不存在时抛出
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));

        // 根据用户roleId查询角色编码
        String roleCode = resolveRoleCode(user.getRoleId());

        return CustomUserDetails.fromEntity(user, roleCode);
    }

    /**
     * 根据用户ID加载用户详情
     *
     * <p>查询用户信息并关联查询角色编码，构建包含数据库角色权限的CustomUserDetails</p>
     *
     * @param userId 用户ID
     * @return 包含用户信息和角色权限的UserDetails对象
     * @throws UsernameNotFoundException 用户不存在时抛出
     */
    public UserDetails loadUserById(Long userId) {
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + userId));

        // 根据用户roleId查询角色编码
        String roleCode = resolveRoleCode(user.getRoleId());

        return CustomUserDetails.fromEntity(user, roleCode);
    }

    /**
     * 根据用户名获取用户实体
     *
     * @param username 用户名
     * @return 用户实体对象
     * @throws UsernameNotFoundException 用户不存在时抛出
     */
    public SysUser getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
    }

    /**
     * 根据用户ID获取用户实体
     *
     * @param userId 用户ID
     * @return 用户实体对象
     * @throws UsernameNotFoundException 用户不存在时抛出
     */
    public SysUser getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + userId));
    }

    /**
     * 根据角色ID解析角色编码
     *
     * <p>通过RoleRepository查询角色表获取角色编码，若角色不存在则返回null</p>
     *
     * @param roleId 角色ID
     * @return 角色编码字符串，角色不存在时返回null
     */
    private String resolveRoleCode(Long roleId) {
        if (roleId == null) {
            return null;
        }
        return roleRepository.findById(roleId)
                .map(SysRole::getRoleCode)
                .orElse(null);
    }
}