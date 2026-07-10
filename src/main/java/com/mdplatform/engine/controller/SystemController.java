package com.mdplatform.engine.controller;

import com.mdplatform.engine.dto.SystemCreateRequest;
import com.mdplatform.engine.dto.SystemDto;
import com.mdplatform.engine.model.ElectrolyteSystem;
import com.mdplatform.engine.service.SystemService;
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
 * 电解液系统控制器，提供电解液配方系统的管理接口
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api/systems")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "电解液系统", description = "电解液配方系统的管理接口")
public class SystemController {

    private final SystemService systemService;

    /**
     * 获取所有电解液系统列表
     *
     * @return 电解液系统DTO列表
     */
    @GetMapping
    @Operation(summary = "获取所有电解液系统列表")
    public ResponseEntity<List<SystemDto>> getAllSystems() {
        List<ElectrolyteSystem> systems = systemService.getAllSystems();
        List<SystemDto> dtoList = systems.stream()
                .map(SystemDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtoList);
    }

    /**
     * 根据ID获取电解液系统详情
     *
     * @param id 电解液系统ID
     * @return 电解液系统DTO
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取电解液系统详情")
    public ResponseEntity<SystemDto> getSystemById(@PathVariable Long id) {
        return systemService.getSystemById(id)
                .map(SystemDto::fromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 创建新的电解液系统
     *
     * @param request 电解液系统创建请求
     * @return 创建的电解液系统DTO
     */
    @PostMapping
    @Operation(summary = "创建新的电解液系统")
    public ResponseEntity<SystemDto> createSystem(@Valid @RequestBody SystemCreateRequest request) {
        try {
            ElectrolyteSystem system = request.toEntity();
            ElectrolyteSystem created = systemService.createSystem(system);
            return ResponseEntity.status(HttpStatus.CREATED).body(SystemDto.fromEntity(created));
        } catch (Exception e) {
            log.error("Failed to create system", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 更新电解液系统
     *
     * @param id      电解液系统ID
     * @param request 电解液系统更新请求
     * @return 更新后的电解液系统DTO
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新电解液系统")
    public ResponseEntity<SystemDto> updateSystem(
            @PathVariable Long id,
            @Valid @RequestBody SystemCreateRequest request) {
        ElectrolyteSystem system = request.toEntity();
        return systemService.updateSystem(id, system)
                .map(s -> ResponseEntity.ok(SystemDto.fromEntity(s)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 删除电解液系统
     *
     * @param id 电解液系统ID
     * @return 无内容响应
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除电解液系统")
    public ResponseEntity<Void> deleteSystem(@PathVariable Long id) {
        boolean deleted = systemService.deleteSystem(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /**
     * 搜索电解液系统
     *
     * @param keyword 搜索关键词
     * @return 电解液系统DTO列表
     */
    @GetMapping("/search")
    @Operation(summary = "搜索电解液系统")
    public ResponseEntity<List<SystemDto>> searchSystems(@RequestParam String keyword) {
        List<ElectrolyteSystem> systems = systemService.searchSystems(keyword);
        List<SystemDto> dtoList = systems.stream()
                .map(SystemDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtoList);
    }

    /**
     * 根据用户ID获取电解液系统列表
     *
     * @param userId 用户ID
     * @return 电解液系统DTO列表
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "根据用户ID获取电解液系统列表")
    public ResponseEntity<List<SystemDto>> getSystemsByUserId(@PathVariable Long userId) {
        List<ElectrolyteSystem> systems = systemService.getSystemsByUserId(userId);
        List<SystemDto> dtoList = systems.stream()
                .map(SystemDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtoList);
    }

    /**
     * 获取公开的电解液系统模板列表
     *
     * @return 电解液系统DTO列表
     */
    @GetMapping("/public")
    @Operation(summary = "获取公开的电解液系统模板列表")
    public ResponseEntity<List<SystemDto>> getPublicTemplates() {
        List<ElectrolyteSystem> systems = systemService.getPublicTemplates();
        List<SystemDto> dtoList = systems.stream()
                .map(SystemDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtoList);
    }
}
