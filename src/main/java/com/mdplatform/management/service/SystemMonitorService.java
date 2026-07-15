package com.mdplatform.management.service;

import com.mdplatform.management.dto.JobDashboardStatsResponse;
import com.mdplatform.management.dto.JobDistributionStatsResponse;
import com.mdplatform.management.dto.ServiceHealthResponse;

/**
 * 系统管理与监控服务（F-S001 ~ F-S004）。
 *
 * <p>看板与分布统计按当前用户任务范围统计；健康状态为平台组件级，不区分用户。</p>
 */
public interface SystemMonitorService {

    /** F-S001 服务健康状态查询 */
    ServiceHealthResponse getServiceHealth();

    /** F-S003 任务看板统计（按用户范围） */
    JobDashboardStatsResponse getJobDashboardStats(Long userId);

    /**
     * F-S004 任务分布统计（按用户范围）
     *
     * @param timeRange today / week / month；为空时默认 month
     */
    JobDistributionStatsResponse getJobDistribution(Long userId, String timeRange);
}
