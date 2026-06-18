package com.mdplatform.management.controller;

import com.mdplatform.common.security.SecurityUtils;
import com.mdplatform.management.model.SysUser;
import com.mdplatform.management.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class UserController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return userService.getUserById(userId)
                .map(user -> ResponseEntity.ok(buildUserInfo(user)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/me")
    public ResponseEntity<Map<String, Object>> updateCurrentUser(@RequestBody Map<String, String> request) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String phone = request.get("phone");
        if (phone != null && !phone.matches("^1[3-9]\\d{9}$")) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "手机号格式不正确");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        // 兼容前端驼峰 realName 与旧下划线 real_name
        String realName = request.get("realName");
        if (realName == null) {
            realName = request.get("real_name");
        }

        SysUser updateData = new SysUser();
        updateData.setRealName(realName);
        updateData.setOrganization(request.get("organization"));
        updateData.setPhone(phone);

        return userService.updateUser(userId, updateData)
                .map(user -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("message", "更新成功");
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/me/password")
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody Map<String, String> request) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Map<String, Object> response = new HashMap<>();
        // 兼容前端驼峰与旧下划线
        String oldPassword = request.get("oldPassword");
        if (oldPassword == null) {
            oldPassword = request.get("old_password");
        }
        String newPassword = request.get("newPassword");
        if (newPassword == null) {
            newPassword = request.get("new_password");
        }

        if (oldPassword == null || newPassword == null) {
            response.put("success", false);
            response.put("message", "旧密码和新密码不能为空");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        Optional<SysUser> userOptional = userService.getUserById(userId);
        if (userOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        SysUser user = userOptional.get();
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            response.put("success", false);
            response.put("message", "旧密码不正确");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        userService.updatePassword(userId, passwordEncoder.encode(newPassword));
        response.put("success", true);
        response.put("message", "密码修改成功");
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        List<Map<String, Object>> users = userService.getAllUsers().stream()
                .map(this::buildUserInfo)
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getUser(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(user -> ResponseEntity.ok(buildUserInfo(user)))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        boolean deleted = userService.deleteUser(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
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
        info.put("createTime", user.getCreateTime());
        info.put("lastLoginTime", user.getLastLoginTime());
        return info;
    }
}
