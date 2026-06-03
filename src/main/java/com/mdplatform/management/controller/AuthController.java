package com.mdplatform.management.controller;

import com.mdplatform.management.model.SysUser;
import com.mdplatform.common.security.JwtTokenProvider;
import com.mdplatform.management.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        String username = (String) request.get("username");
        String password = (String) request.get("password");

        if (username == null || password == null) {
            response.put("success", false);
            response.put("message", "用户名和密码不能为空");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

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

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        String username = (String) request.get("username");
        String password = (String) request.get("password");
        String email = (String) request.get("email");
        String realName = (String) request.get("realName");
        String organization = (String) request.get("organization");

        if (username == null || password == null) {
            response.put("success", false);
            response.put("message", "用户名和密码不能为空");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

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

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "登出成功");
        return ResponseEntity.ok(response);
    }

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