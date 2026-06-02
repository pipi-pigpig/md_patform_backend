package com.mdplatform.engine.controller;

import com.mdplatform.common.security.SecurityUtils;
import com.mdplatform.engine.dto.FormulaRequest;
import com.mdplatform.engine.service.MoltemplateService;
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

@RestController
@RequestMapping("/api/moltemplate")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class MoltemplateController {

    private final MoltemplateService moltemplateService;

    @PostMapping("/calculate")
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

    @GetMapping("/status/{jobId}")
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

    @PostMapping("/execute/{jobId}")
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