package com.mdplatform.common.security;

import com.mdplatform.management.model.SysUser;
import com.mdplatform.management.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));

        return CustomUserDetails.fromEntity(user);
    }

    public UserDetails loadUserById(Long userId) {
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + userId));

        return CustomUserDetails.fromEntity(user);
    }

    public SysUser getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
    }

    public SysUser getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + userId));
    }
}