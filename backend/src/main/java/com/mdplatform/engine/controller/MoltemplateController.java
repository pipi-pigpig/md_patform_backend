package com.mdplatform.engine.controller;

import com.mdplatform.common.security.SecurityUtils;
import com.mdplatform.engine.dto.FormulaRequest;
import com.mdplatform.engine.service.MoltemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Moltemplate建模控制器，提供Moltemplate分子建模和力场生成接口
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api/moltemplate")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Moltemplate建模", description = "Moltemplate分子建模和力场生成接口")
public class MoltemplateController {

    private final MoltemplateService moltemplateService;

    /**
     * 创建建模任务，序列化配方配置到JSON文件
     *
     * @param request 配方请求参数
     * @param userId  用户ID（可选，默认使用当前登录用户）
     * @param jobId   任务ID（可选，默认使用时间戳生成）
     * @return 建模任务创建结果
     */
    @PostMapping("/calculate")
    @Operation(summary = "创建建模任务")
    public ResponseEntity<Map<String, Object>> createModelingTask(
            @Valid @RequestBody FormulaRequest request,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long jobId) {

        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null && userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Long effectiveUserId = userId != null ? userId : currentUserId;

        try {
            Long effectiveJobId = jobId != null ? jobId : System.currentTimeMillis();

            Path jsonPath = moltemplateService.serializeFormulaToJSON(effectiveUserId, effectiveJobId, request);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("jobId", effectiveJobId);
            response.put("userId", effectiveUserId);
            response.put("configPath", jsonPath.toString());
            response.put("message", "配方配置已保存，准备执行建模");

            log.info("建模任务创建成功: userId={}, jobId={}", effectiveUserId, effectiveJobId);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (IOException e) {
            log.error("创建建模任务失败: userId={}, error={}", effectiveUserId, e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 查询建模任务状态
     *
     * @param jobId 任务ID
     * @return 建模任务状态信息
     */
    @GetMapping("/status/{jobId}")
    @Operation(summary = "查询建模任务状态")
    public ResponseEntity<Map<String, Object>> getModelingTaskStatus(@PathVariable Long jobId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            boolean configExists = moltemplateService.formulaFileExists(currentUserId, jobId);

            Map<String, Object> response = new HashMap<>();
            response.put("jobId", jobId);
            response.put("userId", currentUserId);
            response.put("configExists", configExists);
            response.put("status", configExists ? "READY" : "NOT_FOUND");

            if (configExists) {
                String formulaContent = moltemplateService.readFormulaJSON(currentUserId, jobId);
                response.put("configPreview", formulaContent.length() > 200
                        ? formulaContent.substring(0, 200) + "..."
                        : formulaContent);
            }

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("查询建模任务状态失败: userId={}, jobId={}, error={}",
                    currentUserId, jobId, e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("jobId", jobId);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 执行Python建模脚本
     *
     * @param jobId 任务ID
     * @return 执行结果
     */
    @PostMapping("/execute/{jobId}")
    @Operation(summary = "执行Python建模脚本")
    public ResponseEntity<Map<String, Object>> executeModeling(@PathVariable Long jobId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            Process process = moltemplateService.executePythonModeling(currentUserId, jobId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("jobId", jobId);
            response.put("userId", currentUserId);
            response.put("message", "Python建模脚本已启动");
            response.put("processId", process.pid());

            log.info("Python建模脚本已启动: userId={}, jobId={}, pid={}",
                    currentUserId, jobId, process.pid());
            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("执行Python建模脚本失败: userId={}, jobId={}, error={}",
                    currentUserId, jobId, e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("jobId", jobId);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
