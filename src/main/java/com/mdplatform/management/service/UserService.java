package com.mdplatform.management.service;

import com.mdplatform.management.model.SysUser;
import com.mdplatform.management.repository.UserRepository;
import com.mdplatform.management.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public List<SysUser> getAllUsers() {
        return userRepository.findAll();
    }

    public Optional<SysUser> getUserById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<SysUser> getUserByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional
    public SysUser createUser(SysUser user) {
        log.info("Creating user: {}", user.getUsername());
        return userRepository.save(user);
    }

    @Transactional
    public Optional<SysUser> updateUser(Long id, SysUser user) {
        return userRepository.findById(id).map(existingUser -> {
            existingUser.setUsername(user.getUsername());
            existingUser.setEmail(user.getEmail());
            existingUser.setRealName(user.getRealName());
            existingUser.setOrganization(user.getOrganization());
            if (user.getPassword() != null && !user.getPassword().isEmpty()) {
                existingUser.setPassword(user.getPassword());
            }
            if (user.getRoleId() != null) {
                existingUser.setRoleId(user.getRoleId());
            }
            log.info("Updated user with id: {}", id);
            return userRepository.save(existingUser);
        });
    }

    @Transactional
    public boolean deleteUser(Long id) {
        if (userRepository.existsById(id)) {
            userRepository.deleteById(id);
            log.info("Deleted user with id: {}", id);
            return true;
        }
        return false;
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
}