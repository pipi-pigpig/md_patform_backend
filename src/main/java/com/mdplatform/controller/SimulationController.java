package com.mdplatform.controller;

import com.mdplatform.dto.SimulationDto;
import com.mdplatform.dto.SimulationStatsDto;
import com.mdplatform.model.SimulationJob;
import com.mdplatform.security.SecurityUtils;
import com.mdplatform.service.SimulationService;
import com.mdplatform.service.SystemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/simulations")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class SimulationController {

    private final SimulationService simulationService;
    private final SystemService systemService;

    /**
     * 获取当前用户的所有模拟任务（优化版：使用JOIN避免N+1查询）
     */
    @GetMapping
    public ResponseEntity<List<SimulationDto>> getAllSimulations() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 使用优化查询：一次JOIN获取任务和描述
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

    @GetMapping("/{id}")
    public ResponseEntity<SimulationDto> getSimulationById(@PathVariable Long id) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return simulationService.getSimulationById(id)
                .filter(job -> job.getUserId().equals(currentUserId)) // 确保只能访问自己的任务
                .map(job -> {
                    java.util.Optional<com.mdplatform.model.ElectrolyteSystem> system = systemService.getSystemById(job.getSystemId());
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
     * 创建模拟任务 - 自动关联当前用户
     */
    @PostMapping
    public ResponseEntity<SimulationJob> createSimulation(@RequestBody SimulationJob job) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            job.setUserId(userId); // 自动设置当前用户 ID
            SimulationJob created = simulationService.createSimulation(job);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            log.error("Failed to create simulation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<SimulationJob> updateSimulation(
            @PathVariable Long id,
            @RequestBody SimulationJob job) {
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

    @DeleteMapping("/{id}")
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

    @GetMapping("/status/{status}")
    public ResponseEntity<List<SimulationJob>> getSimulationsByStatus(@PathVariable String status) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 只返回当前用户的指定状态任务
        List<SimulationJob> simulations = simulationService.getSimulationsByUserIdAndStatus(userId, status);
        return ResponseEntity.ok(simulations);
    }

    @GetMapping("/software/{softwareName}")
    public ResponseEntity<List<SimulationJob>> getSimulationsBySoftwareName(@PathVariable String softwareName) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 只返回当前用户的指定软件任务
        List<SimulationJob> simulations = simulationService.getSimulationsByUserIdAndSoftware(userId, softwareName);
        return ResponseEntity.ok(simulations);
    }

    @PutMapping("/{id}/status")
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

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelSimulation(@PathVariable Long id) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return simulationService.getSimulationById(id)
                .filter(job -> job.getUserId().equals(currentUserId))
                .map(job -> {
                    simulationService.updateSimulationStatus(id, "CANCELLED");
                    return ResponseEntity.ok().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 获取当前用户的任务统计
     */
    @GetMapping("/stats")
    public ResponseEntity<SimulationStatsDto> getSystemStatistics() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        SimulationStatsDto stats = simulationService.getStatsByUserId(userId);
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/system/{systemId}")
    public ResponseEntity<List<SimulationJob>> getSimulationsBySystemId(@PathVariable Long systemId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // 只返回当前用户的指定系统的任务
        List<SimulationJob> simulations = simulationService.getSimulationsByUserIdAndSystemId(currentUserId, systemId);
        return ResponseEntity.ok(simulations);
    }
}
