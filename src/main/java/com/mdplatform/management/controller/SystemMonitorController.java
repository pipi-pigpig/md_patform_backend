package com.mdplatform.management.controller;

import com.mdplatform.common.security.SecurityUtils;
import com.mdplatform.management.dto.JobDashboardStatsResponse;
import com.mdplatform.management.dto.JobDistributionStatsResponse;
import com.mdplatform.management.dto.ServiceHealthResponse;
import com.mdplatform.management.service.SystemMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 系统管理与监控（F-S001 ~ F-S004）。
 *
 * <p>看板与分布统计按当前登录用户任务范围统计；健康状态为平台组件级，不区分用户。</p>
 */
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class SystemMonitorController {

    private final SystemMonitorService systemMonitorService;

    /** F-S001 服务健康状态查询 */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        ServiceHealthResponse response = systemMonitorService.getServiceHealth();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", response);
        return ResponseEntity.ok(result);
    }

    /** F-S003 任务看板统计（当前用户任务范围） */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        JobDashboardStatsResponse response = systemMonitorService.getJobDashboardStats(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", response);
        return ResponseEntity.ok(result);
    }

    /** F-S004 任务分布统计（当前用户任务范围） */
    @GetMapping("/distribution")
    public ResponseEntity<Map<String, Object>> getDistribution(
            @RequestParam(name = "time_range", required = false) String timeRange) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        JobDistributionStatsResponse response = systemMonitorService.getJobDistribution(userId, timeRange);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", response);
        return ResponseEntity.ok(result);
    }
}
