package com.mdplatform.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * F-S004 任务分布统计响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDistributionStatsResponse {

    /** 时间范围：today / week / month */
    private String timeRange;

    /** 按状态分组：{ PENDING: 3, RUNNING: 1, ... } */
    private Map<String, Long> statusCounts;

    /** 按日期分组（ISO yyyy-MM-dd）：[{ date: "2026-06-01", count: 5 }, ...] */
    private List<DailyCount> dailyCounts;

    /** 时间范围内任务总数 */
    private long totalInRange;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyCount {
        private String date;
        private long count;
    }
}
