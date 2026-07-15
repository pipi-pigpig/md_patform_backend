package com.mdplatform.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * F-S003 任务看板统计响应。
 *
 * <p>totalJobs 为主要状态（PENDING/RUNNING/COMPLETED/FAILED）之和，
 * 保证看板四个状态卡片数字相加等于总数。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDashboardStatsResponse {

    /** 总任务数（= 排队 + 运行 + 完成 + 失败） */
    private long totalJobs;

    /** 排队中任务数 */
    private long pendingJobs;

    /** 运行中任务数 */
    private long runningJobs;

    /** 已完成任务数 */
    private long completedJobs;

    /** 失败任务数 */
    private long failedJobs;
}
