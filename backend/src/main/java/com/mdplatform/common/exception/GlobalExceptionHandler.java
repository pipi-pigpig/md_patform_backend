package com.mdplatform.common.exception;

import com.mdplatform.common.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/**
 * 全局异常处理器 - 统一处理所有Controller抛出的异常
 *
 * <p>功能：</p>
 * <ul>
 *   <li>处理Bean Validation校验异常，返回具体的字段错误信息</li>
 *   <li>处理业务逻辑异常，返回友好的错误提示</li>
 *   <li>处理未知异常，返回500错误并记录详细日志</li>
 * </ul>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理@Valid校验失败异常
     *
     * @param ex 方法参数校验异常
     * @return 包含字段错误信息的400响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("[全局异常处理] 参数校验失败: {}", errors);
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "参数校验失败: " + errors));
    }

    /**
     * 处理约束违反异常（如@NotBlank、@NotNull等在Service层触发的校验）
     *
     * @param ex 约束违反异常
     * @return 包含约束错误信息的400响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException ex) {
        String errors = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("[全局异常处理] 约束违反: {}", errors);
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "参数校验失败: " + errors));
    }

    /**
     * 处理非法参数异常
     *
     * @param ex 非法参数异常
     * @return 400响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("[全局异常处理] 非法参数: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    /**
     * 处理非法状态异常
     *
     * @param ex 非法状态异常
     * @return 400响应
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(IllegalStateException ex) {
        log.warn("[全局异常处理] 非法状态: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    /**
     * 处理运行时异常
     *
     * @param ex 运行时异常
     * @return 500响应
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex) {
        log.error("[全局异常处理] 运行时异常: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "运行时异常: " + ex.getMessage()));
    }

    /**
     * 处理所有未捕获的异常
     *
     * @param ex 异常
     * @return 500响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        log.error("[全局异常处理] 未知异常: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "服务器内部错误"));
    }
}
