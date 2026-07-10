package com.mdplatform.engine.controller;

import com.mdplatform.engine.dto.SimulationDto;
import com.mdplatform.engine.dto.SimulationStatsDto;
import com.mdplatform.engine.model.JobStatus;
import com.mdplatform.engine.model.SimulationJob;
import com.mdplatform.common.security.SecurityUtils;
import com.mdplatform.engine.service.SimulationService;
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

/**
 * 模拟任务控制器，提供模拟任务的CRUD操作和状态管理接口
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api/simulations")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "模拟任务", description = "模拟任务的创建、查询、状态管理等接口")
public class SimulationController {

    private final SimulationService simulationService;
    private final SystemService systemService;

    /**
     * 获取当前用户的所有模拟任务列表
     *
     * @return 模拟任务DTO列表
     */
    @GetMapping
    @Operation(summary = "获取当前用户的所有模拟任务")
    public ResponseEntity<List<SimulationDto>> getAllSimulations() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<Object[]> results = simulationService.getSimulationsByUserIdWithDescription(userId);
        List<SimulationDto> dtoList = results.stream()
                .map(row -> {
                    SimulationJob job = (SimulationJob) row[0];
                    String taskDescription = (String) row[1];
                    if (taskDescription != null) {
                        return SimulationDto.fromEntityWithDescription(job, taskDescription);
                    } else {
                        return SimulationDto.fromEntity(job);
                    }
                })
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(dtoList);
    }

    /**
     * 根据ID获取模拟任务详情
     *
     * @param id 模拟任务ID
     * @return 模拟任务DTO
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取模拟任务详情")
    public ResponseEntity<SimulationDto> getSimulationById(@PathVariable Long id) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return simulationService.getSimulationById(id)
                .filter(job -> job.getUserId().equals(currentUserId))
                .map(job -> {
                    java.util.Optional<com.mdplatform.engine.model.ElectrolyteSystem> system = systemService.getSystemById(job.getSystemId());
                    if (system.isPresent()) {
                        return SimulationDto.fromEntityWithDescription(job, system.get().getTaskDescription());
                    } else {
                        return SimulationDto.fromEntity(job);
                    }
                })
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 创建新的模拟任务
     *
     * @param job 模拟任务实体
     * @return 创建的模拟任务
     */
    @PostMapping
    @Operation(summary = "创建新的模拟任务")
    public ResponseEntity<SimulationJob> createSimulation(@Valid @RequestBody SimulationJob job) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            job.setUserId(userId);
            SimulationJob created = simulationService.createSimulation(job);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            log.error("Failed to create simulation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 更新模拟任务
     *
     * @param id  模拟任务ID
     * @param job 更新后的模拟任务实体
     * @return 更新后的模拟任务
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新模拟任务")
    public ResponseEntity<SimulationJob> updateSimulation(
            @PathVariable Long id,
            @Valid @RequestBody SimulationJob job) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return simulationService.getSimulationById(id)
                .filter(existingJob -> existingJob.getUserId().equals(currentUserId))
                .flatMap(existingJob -> simulationService.updateSimulation(id, job))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 删除模拟任务
     *
     * @param id 模拟任务ID
     * @return 无内容响应
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除模拟任务")
    public ResponseEntity<Void> deleteSimulation(@PathVariable Long id) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return simulationService.getSimulationById(id)
                .filter(job -> job.getUserId().equals(currentUserId))
                .map(job -> {
                    simulationService.deleteSimulation(id);
                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 根据状态获取模拟任务列表
     *
     * @param status 模拟任务状态
     * @return 模拟任务列表
     */
    @GetMapping("/status/{status}")
    @Operation(summary = "根据状态获取模拟任务列表")
    public ResponseEntity<List<SimulationJob>> getSimulationsByStatus(@PathVariable String status) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<SimulationJob> simulations = simulationService.getSimulationsByUserIdAndStatus(userId, status);
        return ResponseEntity.ok(simulations);
    }

    /**
     * 根据软件名称获取模拟任务列表
     *
     * @param softwareName 软件名称
     * @return 模拟任务列表
     */
    @GetMapping("/software/{softwareName}")
    @Operation(summary = "根据软件名称获取模拟任务列表")
    public ResponseEntity<List<SimulationJob>> getSimulationsBySoftwareName(@PathVariable String softwareName) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<SimulationJob> simulations = simulationService.getSimulationsByUserIdAndSoftware(userId, softwareName);
        return ResponseEntity.ok(simulations);
    }

    /**
     * 更新模拟任务状态
     *
     * @param id     模拟任务ID
     * @param status 新状态
     * @return 更新后的模拟任务
     */
    @PutMapping("/{id}/status")
    @Operation(summary = "更新模拟任务状态")
    public ResponseEntity<SimulationJob> updateSimulationStatus(
            @PathVariable Long id,
            @RequestBody String status) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return simulationService.getSimulationById(id)
                .filter(job -> job.getUserId().equals(currentUserId))
                .flatMap(job -> simulationService.updateSimulationStatus(id, status))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 取消模拟任务
     *
     * @param id 模拟任务ID
     * @return 无内容响应
     */
    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消模拟任务")
    public ResponseEntity<Void> cancelSimulation(@PathVariable Long id) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return simulationService.getSimulationById(id)
                .filter(job -> job.getUserId().equals(currentUserId))
                .map(job -> {
                    simulationService.updateSimulationStatus(id, JobStatus.CANCELLED);
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 获取当前用户的模拟任务统计信息
     *
     * @return 模拟任务统计DTO
     */
    @GetMapping("/stats")
    @Operation(summary = "获取当前用户的模拟任务统计信息")
    public ResponseEntity<SimulationStatsDto> getSystemStatistics() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        SimulationStatsDto stats = simulationService.getStatsByUserId(userId);
        return ResponseEntity.ok(stats);
    }

    /**
     * 根据系统ID获取模拟任务列表
     *
     * @param systemId 电解液系统ID
     * @return 模拟任务列表
     */
    @GetMapping("/system/{systemId}")
    @Operation(summary = "根据系统ID获取模拟任务列表")
    public ResponseEntity<List<SimulationJob>> getSimulationsBySystemId(@PathVariable Long systemId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<SimulationJob> simulations = simulationService.getSimulationsByUserIdAndSystemId(currentUserId, systemId);
        return ResponseEntity.ok(simulations);
    }
}
