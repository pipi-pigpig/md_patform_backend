package com.mdplatform.management.controller;

import com.mdplatform.common.security.JwtTokenProvider;
import com.mdplatform.management.model.SysUser;
import com.mdplatform.management.service.PasswordResetService;
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
    private final PasswordResetService passwordResetService;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        // 兼容前端 username 字段与旧 account 字段，优先 username
        String account = request.get("username");
        if (account == null) {
            account = request.get("account");
        }
        String password = request.get("password");

        if (account == null || password == null) {
            response.put("success", false);
            response.put("message", "账号和密码不能为空");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        // 支持用户名或邮箱登录
        Optional<SysUser> userOptional = userService.getUserByUsername(account);
        if (userOptional.isEmpty()) {
            userOptional = userService.getUserByEmail(account);
        }

        if (userOptional.isPresent()) {
            SysUser user = userOptional.get();
            if (passwordEncoder.matches(password, user.getPassword())) {
                String accessToken = jwtTokenProvider.generateToken(user.getUserId(), user.getUsername());
                String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUserId(), user.getUsername());

                // 更新最后登录时间
                userService.updateLastLoginTime(user.getUserId());

                response.put("success", true);
                response.put("message", "登录成功");
                response.put("accessToken", accessToken);
                response.put("refreshToken", refreshToken);
                response.put("tokenType", "Bearer");
                response.put("expiresIn", jwtTokenProvider.getExpirationTime() / 1000);
                response.put("userInfo", buildUserInfo(user));
                log.info("User logged in: {}", account);
                return ResponseEntity.ok(response);
            }
        }

        response.put("success", false);
        response.put("message", "用户名/邮箱或密码错误");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        String username = request.get("username");
        String password = request.get("password");
        String email = request.get("email");
        // 兼容前端驼峰 realName 与旧下划线 real_name
        String realName = request.get("realName");
        if (realName == null) {
            realName = request.get("real_name");
        }
        String organization = request.get("organization");

        // 必填校验
        if (username == null || username.isBlank()) {
            response.put("success", false);
            response.put("message", "用户名不能为空");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        if (password == null || password.isBlank()) {
            response.put("success", false);
            response.put("message", "密码不能为空");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        if (email == null || email.isBlank()) {
            response.put("success", false);
            response.put("message", "邮箱不能为空");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        // 用户名格式校验
        if (!username.matches("^[a-zA-Z0-9_]{4,50}$")) {
            response.put("success", false);
            response.put("message", "用户名需4-50位，仅允许字母数字下划线");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        // 邮箱格式校验
        if (!email.matches("^[\\w.-]+@[\\w.-]+\\.[a-zA-Z]{2,}$")) {
            response.put("success", false);
            response.put("message", "邮箱格式不正确");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        // 唯一性校验
        if (userService.existsByUsername(username)) {
            response.put("success", false);
            response.put("message", "用户名已被注册");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        if (userService.existsByEmail(email)) {
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
        user.setRoleId(1L);
        user.setStatus(1);
        user.setCreateTime(LocalDateTime.now());

        SysUser createdUser = userService.createUser(user);

        response.put("success", true);
        response.put("message", "注册成功");
        response.put("userId", createdUser.getUserId());
        response.put("username", createdUser.getUsername());
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

    @PostMapping("/password-reset/request")
    public ResponseEntity<Map<String, Object>> requestPasswordReset(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        String email = request.get("email");

        if (email == null || email.isBlank()) {
            response.put("success", false);
            response.put("message", "邮箱不能为空");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if (!userService.existsByEmail(email)) {
            // 不暴露邮箱是否注册，统一返回成功提示
            response.put("success", true);
            response.put("message", "如果该邮箱已注册，重置邮件已发送");
            return ResponseEntity.ok(response);
        }

        String token = passwordResetService.createResetToken(email);
        // TODO: 实际发送邮件，当前仅生成令牌
        log.info("Password reset requested for email: {}, token: {}", email, token);

        response.put("success", true);
        response.put("message", "如果该邮箱已注册，重置邮件已发送");
        response.put("reset_token", token);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Map<String, Object>> confirmPasswordReset(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        String token = request.get("token");
        // 兼容前端驼峰 newPassword 与旧下划线 new_password
        String newPassword = request.get("newPassword");
        if (newPassword == null) {
            newPassword = request.get("new_password");
        }

        if (token == null || newPassword == null) {
            response.put("success", false);
            response.put("message", "令牌和新密码不能为空");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        String email = passwordResetService.validateToken(token);
        if (email == null) {
            response.put("success", false);
            response.put("message", "重置令牌无效或已过期");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        Optional<SysUser> userOptional = userService.getUserByEmail(email);
        if (userOptional.isEmpty()) {
            response.put("success", false);
            response.put("message", "用户不存在");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        SysUser user = userOptional.get();
        userService.updatePassword(user.getUserId(), passwordEncoder.encode(newPassword));
        passwordResetService.consumeToken(token);

        response.put("success", true);
        response.put("message", "密码重置成功");
        log.info("Password reset confirmed for email: {}", email);
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> buildUserInfo(SysUser user) {
        Map<String, Object> info = new HashMap<>();
        info.put("userId", user.getUserId());
        info.put("username", user.getUsername());
        info.put("email", user.getEmail());
        info.put("realName", user.getRealName());
        info.put("organization", user.getOrganization());
        info.put("phone", user.getPhone());
        info.put("roleId", user.getRoleId());
        return info;
    }
}
