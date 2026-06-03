package com.mdplatform.common.security;

import com.mdplatform.management.model.SysUser;
import lombok.Getter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collections;

@Getter
public class CustomUserDetails extends User {

    private final Long userId;
    private final String email;
    private final String realName;
    private final String organization;

    public CustomUserDetails(Long userId, String username, String password,
                             String email, String realName, String organization) {
        super(username, password, Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        this.userId = userId;
        this.email = email;
        this.realName = realName;
        this.organization = organization;
    }

    public static CustomUserDetails fromEntity(SysUser user) {
        return new CustomUserDetails(
                user.getUserId(),
                user.getUsername(),
                user.getPassword(),
                user.getEmail(),
                user.getRealName(),
                user.getOrganization()
        );
    }
}