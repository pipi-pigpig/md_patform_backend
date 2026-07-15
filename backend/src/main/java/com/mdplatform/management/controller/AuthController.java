package com.mdplatform.management.controller;

import com.mdplatform.management.dto.LoginRequest;
import com.mdplatform.management.dto.RegisterRequest;
import com.mdplatform.management.model.SysUser;
import com.mdplatform.common.security.JwtTokenProvider;
import com.mdplatform.management.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 认证管理控制器，提供用户登录、注册、登出接口
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "认证管理", description = "用户登录、注册、登出接口")
public class AuthController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 用户登录，验证用户名和密码并返回JWT令牌
     *
     * @param loginRequest 登录请求参数，包含用户名和密码
     * @return 登录结果，包含令牌和用户信息
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest loginRequest) {
        Map<String, Object> response = new HashMap<>();
        String username = loginRequest.getUsername();
        String password = loginRequest.getPassword();

        Optional<SysUser> userOptional = userService.getUserByUsername(username);
        if (userOptional.isPresent()) {
            SysUser user = userOptional.get();
            if (passwordEncoder.matches(password, user.getPassword())) {
                String token = jwtTokenProvider.generateToken(user.getUserId(), user.getUsername());

                response.put("success", true);
                response.put("message", "登录成功");
                response.put("token", token);
                response.put("user", buildUserResponse(user));
                log.info("User logged in: {}", username);
                return ResponseEntity.ok(response);
            }
        }

        response.put("success", false);
        response.put("message", "用户名或密码错误");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    /**
     * 用户注册，创建新用户并返回JWT令牌
     *
     * @param registerRequest 注册请求参数，包含用户名、密码、邮箱等
     * @return 注册结果，包含令牌和用户信息
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterRequest registerRequest) {
        Map<String, Object> response = new HashMap<>();
        String username = registerRequest.getUsername();
        String password = registerRequest.getPassword();
        String email = registerRequest.getEmail();
        String realName = registerRequest.getRealName();
        String organization = registerRequest.getOrganization();

        if (userService.existsByUsername(username)) {
            response.put("success", false);
            response.put("message", "用户名已存在");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if (email != null && userService.existsByEmail(email)) {
            response.put("success", false);
            response.put("message", "邮箱已被注册");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setEmail(email);
        user.setRealName(realName);
        user.setOrganization(organization);
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());

        SysUser createdUser = userService.createUser(user);

        String token = jwtTokenProvider.generateToken(createdUser.getUserId(), createdUser.getUsername());

        response.put("success", true);
        response.put("message", "注册成功");
        response.put("token", token);
        response.put("user", buildUserResponse(createdUser));
        log.info("User registered: {}", username);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 用户登出
     *
     * @return 登出结果
     */
    @PostMapping("/logout")
    @Operation(summary = "用户登出")
    public ResponseEntity<Map<String, Object>> logout() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "登出成功");
        return ResponseEntity.ok(response);
    }

    /**
     * 构建用户响应Map，排除密码等敏感字段
     *
     * @param user 用户实体
     * @return 用户信息Map
     */
    private Map<String, Object> buildUserResponse(SysUser user) {
        Map<String, Object> userResponse = new HashMap<>();
        userResponse.put("userId", user.getUserId());
        userResponse.put("username", user.getUsername());
        userResponse.put("email", user.getEmail());
        userResponse.put("realName", user.getRealName());
        userResponse.put("organization", user.getOrganization());
        userResponse.put("phone", user.getPhone());
        userResponse.put("roleId", user.getRoleId());
        userResponse.put("status", user.getStatus());
        return userResponse;
    }
}
