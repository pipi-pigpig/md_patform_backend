package com.mdplatform.management.service.impl;

import com.mdplatform.management.dto.ComponentHealthResponse;
import com.mdplatform.management.service.HealthChecker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;

/**
 * MySQL 数据库连通性检测（F-S001 步骤2）。
 *
 * <p>通过执行 SELECT 1 验证连接可用性，超时由 JDBC 驱动默认值决定。</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MysqlHealthChecker implements HealthChecker {

    private final DataSource dataSource;

    @Override
    public String getName() {
        return "mysql";
    }

    @Override
    public ComponentHealthResponse check() {
        long start = System.currentTimeMillis();
        ComponentHealthResponse.ComponentHealthResponseBuilder builder = ComponentHealthResponse.builder()
                .name(getName())
                .lastCheck(LocalDateTime.now());

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT 1");
            long elapsed = System.currentTimeMillis() - start;
            return builder.status("healthy").responseTimeMs(elapsed).build();
        } catch (Exception e) {
            log.warn("MySQL health check failed: {}", e.getMessage());
            long elapsed = System.currentTimeMillis() - start;
            return builder.status("unhealthy")
                    .responseTimeMs(elapsed)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }
}
