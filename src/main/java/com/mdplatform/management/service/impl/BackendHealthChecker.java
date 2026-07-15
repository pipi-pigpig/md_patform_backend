package com.mdplatform.management.service.impl;

import com.mdplatform.management.dto.ComponentHealthResponse;
import com.mdplatform.management.service.HealthChecker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 后端服务自检（F-S001 步骤1）。
 *
 * <p>后端进程本身即在运行，因此本检测器总是返回 healthy，并测量一次方法执行耗时作为响应时间。</p>
 */
@Component
@Slf4j
public class BackendHealthChecker implements HealthChecker {

    @Override
    public String getName() {
        return "backend";
    }

    @Override
    public ComponentHealthResponse check() {
        long start = System.currentTimeMillis();
        // 后端进程自身，无需额外检测；耗时即方法执行耗时
        long elapsed = System.currentTimeMillis() - start;
        return ComponentHealthResponse.builder()
                .name(getName())
                .status("healthy")
                .responseTimeMs(elapsed)
                .lastCheck(LocalDateTime.now())
                .build();
    }
}
