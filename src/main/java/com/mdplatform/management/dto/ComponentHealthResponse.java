package com.mdplatform.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 单个组件的健康状态（F-S001 输出）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComponentHealthResponse {

    /** 组件名称：backend / mysql / compute-container */
    private String name;

    /** 状态：healthy / unhealthy */
    private String status;

    /** 响应时间(ms)；不可达时为 null */
    private Long responseTimeMs;

    /** 最后检测时间 */
    private LocalDateTime lastCheck;

    /** 异常时的错误信息 */
    private String errorMessage;
}
