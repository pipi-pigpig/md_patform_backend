package com.mdplatform.management.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * F-S001 服务健康状态查询响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceHealthResponse {

    private List<ComponentHealthResponse> components;

    /** 整体状态：healthy（全部健康）/ unhealthy（任一异常） */
    private String overallStatus;
}
