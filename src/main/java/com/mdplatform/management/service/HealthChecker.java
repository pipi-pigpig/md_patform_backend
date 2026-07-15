package com.mdplatform.management.service;

import com.mdplatform.management.dto.ComponentHealthResponse;

/**
 * 单个组件健康检测策略。
 */
public interface HealthChecker {

    /** 组件名称：backend / mysql / compute-container */
    String getName();

    /** 执行检测，返回健康状态；不可达时不抛异常，返回 unhealthy 状态 */
    ComponentHealthResponse check();
}
