package com.mdplatform.management.service.impl;

import com.mdplatform.management.dto.ComponentHealthResponse;
import com.mdplatform.management.dto.JobDashboardStatsResponse;
import com.mdplatform.management.dto.JobDistributionStatsResponse;
import com.mdplatform.management.dto.ServiceHealthResponse;
import com.mdplatform.management.repository.SimulationJobRepository;
import com.mdplatform.management.service.HealthChecker;
import com.mdplatform.management.service.SystemMonitorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SystemMonitorServiceImpl implements SystemMonitorService {

    private static final String DEFAULT_TIME_RANGE = "all";

    private final List<HealthChecker> healthCheckers;
    private final SimulationJobRepository jobRepository;

    @Override
    public ServiceHealthResponse getServiceHealth() {
        List<ComponentHealthResponse> components = healthCheckers.stream()
                .map(HealthChecker::check)
                .collect(Collectors.toList());

        String overall = components.stream()
                .allMatch(c -> "healthy".equalsIgnoreCase(c.getStatus()))
                ? "healthy" : "unhealthy";

        return ServiceHealthResponse.builder()
                .components(components)
                .overallStatus(overall)
                .build();
    }

    @Override
    public JobDashboardStatsResponse getJobDashboardStats(Long userId) {
        long pending = jobRepository.countByUserIdAndStatus(userId, "PENDING");
        long running = jobRepository.countByUserIdAndStatus(userId, "RUNNING");
        long completed = jobRepository.countByUserIdAndStatus(userId, "COMPLETED");
        long failed = jobRepository.countByUserIdAndStatus(userId, "FAILED");
        long total = pending + running + completed + failed;

        return JobDashboardStatsResponse.builder()
                .totalJobs(total)
                .pendingJobs(pending)
                .runningJobs(running)
                .completedJobs(completed)
                .failedJobs(failed)
                .build();
    }

    @Override
    public JobDistributionStatsResponse getJobDistribution(Long userId, String timeRange) {
        String range = normalizeRange(timeRange);
        LocalDateTime since = computeSince(range);

        Map<String, Long> statusCounts = new LinkedHashMap<>();
        List<Object[]> rows = jobRepository.countByUserIdAndStatusSince(userId, since);
        for (Object[] row : rows) {
            String status = String.valueOf(row[0]);
            Long count = ((Number) row[1]).longValue();
            statusCounts.put(status, count);
        }
        long totalInRange = statusCounts.values().stream().mapToLong(Long::longValue).sum();

        List<JobDistributionStatsResponse.DailyCount> dailyCounts =
                jobRepository.countDailyByUserIdSince(userId, since).stream()
                        .map(row -> JobDistributionStatsResponse.DailyCount.builder()
                                .date(String.valueOf(row[0]))
                                .count(((Number) row[1]).longValue())
                                .build())
                        .collect(Collectors.toList());

        return JobDistributionStatsResponse.builder()
                .timeRange(range)
                .statusCounts(statusCounts)
                .dailyCounts(dailyCounts)
                .totalInRange(totalInRange)
                .build();
    }

    private String normalizeRange(String timeRange) {
        if (timeRange == null || timeRange.trim().isEmpty()) {
            return DEFAULT_TIME_RANGE;
        }
        String lower = timeRange.trim().toLowerCase();
        if ("today".equals(lower) || "3days".equals(lower) || "week".equals(lower)
                || "14days".equals(lower) || "month".equals(lower) || "all".equals(lower)) {
            return lower;
        }
        return DEFAULT_TIME_RANGE;
    }

    private LocalDateTime computeSince(String range) {
        LocalDate today = LocalDate.now();
        switch (range) {
            case "today":
                return today.atStartOfDay();
            case "3days":
                return today.minusDays(2).atStartOfDay();
            case "week":
                return today.minusDays(6).atStartOfDay();
            case "14days":
                return today.minusDays(13).atStartOfDay();
            case "month":
                return today.minusDays(29).atStartOfDay();
            default: // all
                return LocalDateTime.of(1970, 1, 1, 0, 0);
        }
    }
}
