package com.mdplatform.management.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查控制器，提供系统健康检查接口
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api")
@Tag(name = "健康检查", description = "系统健康检查接口")
public class HealthController {

    /**
     * 健康检查接口，返回系统运行状态
     *
     * @return 系统状态字符串
     */
    @GetMapping("/health")
    @Operation(summary = "健康检查")
    public String health() {
        return "OK";
    }
}
