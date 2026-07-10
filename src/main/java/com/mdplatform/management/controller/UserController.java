package com.mdplatform.management.controller;

import com.mdplatform.management.dto.UserDto;
import com.mdplatform.management.model.SysUser;
import com.mdplatform.management.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户管理控制器，提供用户信息的查询和管理接口
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "用户管理", description = "用户信息的查询和管理接口")
public class UserController {

    private final UserService userService;

    /**
     * 获取所有用户列表
     *
     * @return 用户DTO列表
     */
    @GetMapping
    @Operation(summary = "获取所有用户列表")
    public ResponseEntity<List<UserDto>> getAllUsers() {
        List<SysUser> users = userService.getAllUsers();
        List<UserDto> dtoList = users.stream()
                .map(UserDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtoList);
    }

    /**
     * 根据ID获取用户信息
     *
     * @param id 用户ID
     * @return 用户DTO
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取用户信息")
    public ResponseEntity<UserDto> getUser(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(user -> ResponseEntity.ok(UserDto.fromEntity(user)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 根据用户名获取用户信息
     *
     * @param username 用户名
     * @return 用户DTO
     */
    @GetMapping("/username/{username}")
    @Operation(summary = "根据用户名获取用户信息")
    public ResponseEntity<UserDto> getUserByUsername(@PathVariable String username) {
        return userService.getUserByUsername(username)
                .map(user -> ResponseEntity.ok(UserDto.fromEntity(user)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 创建新用户
     *
     * @param user 用户实体
     * @return 创建的用户DTO
     */
    @PostMapping
    @Operation(summary = "创建新用户")
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody SysUser user) {
        try {
            if (userService.existsByUsername(user.getUsername())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }
            if (user.getEmail() != null && userService.existsByEmail(user.getEmail())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }
            SysUser created = userService.createUser(user);
            return ResponseEntity.status(HttpStatus.CREATED).body(UserDto.fromEntity(created));
        } catch (Exception e) {
            log.error("Failed to create user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 更新用户信息
     *
     * @param id   用户ID
     * @param user 更新后的用户实体
     * @return 更新后的用户DTO
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新用户信息")
    public ResponseEntity<UserDto> updateUser(@PathVariable Long id, @Valid @RequestBody SysUser user) {
        return userService.updateUser(id, user)
                .map(updated -> ResponseEntity.ok(UserDto.fromEntity(updated)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 删除用户
     *
     * @param id 用户ID
     * @return 无内容响应
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        boolean deleted = userService.deleteUser(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
