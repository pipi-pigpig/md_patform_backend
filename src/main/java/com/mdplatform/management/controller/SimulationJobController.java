package com.mdplatform.management.controller;

import com.mdplatform.common.security.SecurityUtils;
import com.mdplatform.management.dto.*;
import com.mdplatform.management.service.SimulationJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class SimulationJobController {

    private final SimulationJobService jobService;

    // F-J001 任务创建
    @PostMapping
    public ResponseEntity<Map<String, Object>> createJob(@RequestBody SimulationJobCreateRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            SimulationJobResponse response = jobService.createJob(userId, request);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "任务创建成功");
            result.put("data", response);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }
    }

    // F-J002 状态查询
    @GetMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> getJobStatus(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            SimulationJobStatusResponse response = jobService.getJobStatus(userId, id);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
        }
    }

    // F-J003 日志查看
    @GetMapping("/{id}/logs")
    public ResponseEntity<Map<String, Object>> getJobLogs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "100") int lines,
            @RequestParam(defaultValue = "0") int offset) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            SimulationJobLogResponse response = jobService.getJobLogs(userId, id, lines, offset);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
        }
    }

    // F-J004 任务取消
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelJob(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            jobService.cancelJob(userId, id);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "任务取消成功");
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }
    }

    // F-J005 任务删除
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteJob(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            jobService.deleteJob(userId, id);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "任务删除成功");
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }
    }

    // F-J006 任务重试
    @PostMapping("/{id}/retry")
    public ResponseEntity<Map<String, Object>> retryJob(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            jobService.retryJob(userId, id);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "任务重试成功");
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }
    }

    // F-J007 列表查询
    @GetMapping
    public ResponseEntity<Map<String, Object>> listJobs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int page_size) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        PageResponse<SimulationJobListResponse> response = jobService.listJobs(userId, status, keyword, page, page_size);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", response);
        return ResponseEntity.ok(result);
    }

    // F-J008 详情查询
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getJobDetail(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            SimulationJobResponse response = jobService.getJobDetail(userId, id);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
        }
    }

    // 统计信息
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getJobStats() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        SimulationJobStatsResponse response = jobService.getJobStats(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", response);
        return ResponseEntity.ok(result);
    }

    // 按状态筛选
    @GetMapping("/status/{status}")
    public ResponseEntity<Map<String, Object>> getByStatus(
            @PathVariable String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int page_size) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        PageResponse<SimulationJobListResponse> response = jobService.listJobs(userId, status, null, page, page_size);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", response);
        return ResponseEntity.ok(result);
    }
}
