package com.mdplatform.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * 统一错误响应DTO - 用于全局异常处理器的错误响应
 *
 * <p>功能：提供统一的错误响应格式，包含错误码、错误信息、时间戳和详细信息</p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    /** 错误码 */
    private int code;

    /** 错误信息 */
    private String message;

    /** 错误发生时间 */
    private LocalDateTime timestamp;

    /** 详细错误信息（仅开发环境使用） */
    private String details;

    public ErrorResponse() {
        this.timestamp = LocalDateTime.now();
    }

    public ErrorResponse(int code, String message) {
        this.code = code;
        this.message = message;
        this.timestamp = LocalDateTime.now();
    }

    public ErrorResponse(int code, String message, String details) {
        this.code = code;
        this.message = message;
        this.details = details;
        this.timestamp = LocalDateTime.now();
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
