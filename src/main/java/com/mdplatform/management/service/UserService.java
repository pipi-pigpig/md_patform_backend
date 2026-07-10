package com.mdplatform.management.service;

import com.mdplatform.management.model.SysUser;
import com.mdplatform.management.repository.UserRepository;
import com.mdplatform.management.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 用户服务 - 处理用户相关的业务逻辑
 *
 * <p>功能：
 *     1. 用户的增删改查操作
 *     2. 用户密码加密存储
 *     3. 用户名和邮箱唯一性校验
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    /** 用户数据访问仓库 */
    private final UserRepository userRepository;
    /** 角色数据访问仓库 */
    private final RoleRepository roleRepository;
    /** 密码编码器，用于密码加密 */
    private final PasswordEncoder passwordEncoder;

    /**
     * 获取所有用户列表
     *
     * @return 所有用户的列表
     */
    public List<SysUser> getAllUsers() {
        return userRepository.findAll();
    }

    /**
     * 根据用户ID查询用户
     *
     * @param id 用户ID
     * @return 包含用户的Optional对象，若不存在则为空
     */
    public Optional<SysUser> getUserById(Long id) {
        return userRepository.findById(id);
    }

    /**
     * 根据用户名查询用户
     *
     * @param username 用户名
     * @return 包含用户的Optional对象，若不存在则为空
     */
    public Optional<SysUser> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * 创建新用户
     *
     * @param user 待创建的用户对象
     * @return 保存后的用户对象
     */
    @Transactional
    public SysUser createUser(SysUser user) {
        log.info("Creating user: {}", user.getUsername());
        return userRepository.save(user);
    }

    /**
     * 更新用户信息
     *
     * <p>当密码字段不为空时，使用BCrypt加密后存储，禁止明文保存密码</p>
     *
     * @param id   待更新用户的ID
     * @param user 包含更新信息的用户对象
     * @return 包含更新后用户的Optional对象，若用户不存在则为空
     */
    @Transactional
    public Optional<SysUser> updateUser(Long id, SysUser user) {
        return userRepository.findById(id).map(existingUser -> {
            existingUser.setUsername(user.getUsername());
            existingUser.setEmail(user.getEmail());
            existingUser.setRealName(user.getRealName());
            existingUser.setOrganization(user.getOrganization());
            // 密码必须加密存储，禁止明文保存
            if (user.getPassword() != null && !user.getPassword().isEmpty()) {
                existingUser.setPassword(passwordEncoder.encode(user.getPassword()));
            }
            if (user.getRoleId() != null) {
                existingUser.setRoleId(user.getRoleId());
            }
            log.info("Updated user with id: {}", id);
            return userRepository.save(existingUser);
        });
    }

    /**
     * 删除用户
     *
     * @param id 待删除用户的ID
     * @return true表示删除成功，false表示用户不存在
     */
    @Transactional
    public boolean deleteUser(Long id) {
        if (userRepository.existsById(id)) {
            userRepository.deleteById(id);
            log.info("Deleted user with id: {}", id);
            return true;
        }
        return false;
    }

    /**
     * 检查用户名是否已存在
     *
     * @param username 待检查的用户名
     * @return true表示用户名已存在
     */
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    /**
     * 检查邮箱是否已存在
     *
     * @param email 待检查的邮箱
     * @return true表示邮箱已存在
     */
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
}
