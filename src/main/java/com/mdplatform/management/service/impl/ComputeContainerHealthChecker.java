package com.mdplatform.management.service.impl;

import com.mdplatform.management.dto.ComponentHealthResponse;
import com.mdplatform.management.service.HealthChecker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 计算容器运行状态检测（F-S001 步骤3）。
 *
 * <p>计算容器由配方及计算引擎模块（engine 包）维护，常规功能模块不直接操作 engine。
 * 本检测器当前返回占位状态 unhealthy，待与 engine 模块对接时通过 Apifox 定义接口集成。</p>
 */
@Component
@Slf4j
public class ComputeContainerHealthChecker implements HealthChecker {

    @Override
    public String getName() {
        return "compute-container";
    }

    @Override
    public ComponentHealthResponse check() {
        long start = System.currentTimeMillis();
        long elapsed = System.currentTimeMillis() - start;
        return ComponentHealthResponse.builder()
                .name(getName())
                .status("unhealthy")
                .responseTimeMs(elapsed)
                .lastCheck(LocalDateTime.now())
                .errorMessage("compute container health check not yet integrated with engine module")
                .build();
    }
}
