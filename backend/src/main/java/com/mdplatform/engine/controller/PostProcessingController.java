package com.mdplatform.engine.controller;

import com.mdplatform.engine.dto.CalculationResultDto;
import com.mdplatform.engine.model.JobStatus;
import com.mdplatform.engine.service.CalculationResultService;
import com.mdplatform.engine.service.PostProcessingService;
import com.mdplatform.engine.service.SimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后处理控制器，提供模拟任务后处理的触发和查询接口
 *
 * <p>功能：
 *     1. 触发指定任务的后处理计算（密度、电导率、粘度等性质计算）
 *     2. 查询任务的后处理执行状态
 *     3. 查询任务的后处理计算结果
 * </p>
 *
 * <p>后处理仅当模拟任务状态为COMPLETED时才可触发，
 * 触发后任务状态将变更为POST_PROCESSING，后处理完成后恢复为COMPLETED。</p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api/post-processing")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "后处理", description = "模拟任务后处理的触发、状态查询和结果获取接口")
public class PostProcessingController {

    private final PostProcessingService postProcessingService;
    private final SimulationService simulationService;
    private final CalculationResultService calculationResultService;

    /**
     * 触发指定任务的后处理计算
     *
     * <p>仅当任务存在且状态为COMPLETED时允许触发后处理。
     * 后处理为异步执行，触发成功后返回202 Accepted。</p>
     *
     * @param jobId 任务ID
     * @return 包含状态信息的响应，异步触发返回202，参数错误返回400
     */
    @PostMapping("/job/{jobId}")
    @Operation(summary = "触发指定任务的后处理计算", description = "仅当任务状态为COMPLETED时可触发，后处理异步执行")
    public ResponseEntity<Map<String, Object>> triggerPostProcessing(@PathVariable Long jobId) {
        log.info("[后处理] 收到后处理触发请求: jobId={}", jobId);

        // 校验任务是否存在
        java.util.Optional<com.mdplatform.engine.model.SimulationJob> jobOptional = simulationService.getSimulationById(jobId);
        if (!jobOptional.isPresent()) {
            log.warn("[后处理] 任务不存在: jobId={}", jobId);
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("error", "任务不存在");
            errorBody.put("jobId", jobId);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody);
        }

        // 校验任务状态是否为COMPLETED
        com.mdplatform.engine.model.SimulationJob job = jobOptional.get();
        if (!"COMPLETED".equals(job.getStatus())) {
            log.warn("[后处理] 任务状态不允许后处理: jobId={}, status={}", jobId, job.getStatus());
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("error", "任务状态不允许后处理，当前状态: " + job.getStatus());
            errorBody.put("jobId", jobId);
            errorBody.put("currentStatus", job.getStatus());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody);
        }

        // 触发异步后处理
        try {
            postProcessingService.executePostProcessing(jobId);
        } catch (Exception e) {
            log.error("[后处理] 触发后处理失败: jobId={}", jobId, e);
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("error", "触发后处理失败: " + e.getMessage());
            errorBody.put("jobId", jobId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorBody);
        }

        // 返回202 Accepted，表示后处理已接受并异步执行
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("jobId", jobId);
        responseBody.put("status", "POST_PROCESSING");
        responseBody.put("message", "后处理已触发，正在异步执行中");
        log.info("[后处理] 后处理已触发: jobId={}", jobId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(responseBody);
    }

    /**
     * 获取指定任务的后处理状态
     *
     * <p>返回任务当前状态以及已完成的性质计算列表，
     * 便于前端展示后处理进度。</p>
     *
     * @param jobId 任务ID
     * @return 包含任务状态和已计算性质列表的响应
     */
    @GetMapping("/job/{jobId}/status")
    @Operation(summary = "获取指定任务的后处理状态", description = "返回任务当前状态及已完成的性质计算列表")
    public ResponseEntity<Map<String, Object>> getPostProcessingStatus(@PathVariable Long jobId) {
        log.info("[后处理] 查询后处理状态: jobId={}", jobId);

        // 校验任务是否存在
        java.util.Optional<com.mdplatform.engine.model.SimulationJob> jobOptional = simulationService.getSimulationById(jobId);
        if (!jobOptional.isPresent()) {
            log.warn("[后处理] 任务不存在: jobId={}", jobId);
            return ResponseEntity.notFound().build();
        }

        com.mdplatform.engine.model.SimulationJob job = jobOptional.get();

        // 查询该任务已有的计算结果，用于判断哪些性质已计算完成
        List<CalculationResultDto> results = calculationResultService.getResultsByJobId(jobId);
        List<String> calculatedProperties = results.stream()
                .map(CalculationResultDto::getPropertyName)
                .collect(java.util.stream.Collectors.toList());

        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("jobId", jobId);
        responseBody.put("status", job.getStatus());
        responseBody.put("calculatedProperties", calculatedProperties);
        responseBody.put("isPostProcessing", JobStatus.POST_PROCESSING.equals(job.getStatus()));

        return ResponseEntity.ok(responseBody);
    }

    /**
     * 获取指定任务的后处理计算结果
     *
     * <p>返回该任务所有性质的计算结果列表，
     * 每条结果包含主表数据和对应子表的专属数据。</p>
     *
     * @param jobId 任务ID
     * @return 计算结果DTO列表
     */
    @GetMapping("/job/{jobId}/results")
    @Operation(summary = "获取指定任务的后处理计算结果", description = "返回该任务所有性质的计算结果，包含子表专属数据")
    public ResponseEntity<List<CalculationResultDto>> getPostProcessingResults(@PathVariable Long jobId) {
        log.info("[后处理] 查询后处理结果: jobId={}", jobId);

        List<CalculationResultDto> results = calculationResultService.getResultsByJobId(jobId);
        return ResponseEntity.ok(results);
    }
}
