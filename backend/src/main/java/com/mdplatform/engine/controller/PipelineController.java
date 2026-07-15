package com.mdplatform.engine.controller;

import com.mdplatform.common.security.SecurityUtils;
import com.mdplatform.engine.dto.CalculationResultDto;
import com.mdplatform.engine.dto.PipelineProgressDto;
import com.mdplatform.engine.dto.PipelineSubmitRequest;
import com.mdplatform.engine.model.JobStatus;
import com.mdplatform.engine.model.SimulationJob;
import com.mdplatform.engine.service.CalculationResultService;
import com.mdplatform.engine.service.PipelineService;
import com.mdplatform.engine.service.SimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 全流程计算控制器，提供一键提交计算任务和查询进度的接口
 *
 * <p>功能：
 *     1. 一键提交全流程计算任务（建模→模拟→后处理）
 *     2. 查询全流程计算任务的进度信息
 *     3. 查询全流程计算任务的最终结果
 * </p>
 *
 * <p>全流程计算包含以下阶段：
 *     PENDING → MODELING → RUNNING → POST_PROCESSING → COMPLETED
 *     任一阶段失败则状态变为FAILED。</p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api/pipeline")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "全流程计算", description = "一键提交计算任务和查询进度的接口")
public class PipelineController {

    private final PipelineService pipelineService;
    private final SimulationService simulationService;
    private final CalculationResultService calculationResultService;

    /**
     * 提交全流程计算任务
     *
     * <p>接收配方参数和目标计算性质，创建并异步执行全流程计算任务。
     * 全流程包括：分子建模（Packmol+Moltemplate）→ MD模拟（LAMMPS）→ 后处理计算。
     * 提交成功后返回202 Accepted，任务异步执行。</p>
     *
     * @param request 全流程计算任务提交请求，包含配方参数、目标性质等
     * @return 包含jobId和状态的响应，异步提交返回202
     */
    @PostMapping("/submit")
    @Operation(summary = "提交全流程计算任务", description = "一键提交从建模到后处理的完整计算流程，异步执行")
    public ResponseEntity<Map<String, Object>> submitPipelineTask(
            @Valid @RequestBody PipelineSubmitRequest request,
            @RequestParam(required = false) Long testUserId) {  // 测试参数，认证禁用时使用
        log.info("[全流程] 收到全流程计算任务提交请求");

        // 获取当前登录用户ID（认证禁用时使用testUserId或默认用户1）
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            // 认证禁用时的测试模式
            currentUserId = testUserId != null ? testUserId : 1L;
            log.info("[全流程] 测试模式: userId={}", currentUserId);
        }

        try {
            // 调用PipelineService提交全流程任务
            Long jobId = pipelineService.submitPipelineTask(currentUserId, request);

            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("jobId", jobId);
            responseBody.put("userId", currentUserId);
            responseBody.put("status", "PENDING");
            responseBody.put("message", "全流程计算任务已提交，正在异步执行中");

            log.info("[全流程] 任务提交成功: userId={}, jobId={}", currentUserId, jobId);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(responseBody);

        } catch (Exception e) {
            log.error("[全流程] 任务提交失败: userId={}, error={}", currentUserId, e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "任务提交失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 查询全流程计算任务进度
     *
     * <p>返回任务的当前步骤、已完成步骤、进度百分比等详细信息，
     * 便于前端展示全流程执行进度。</p>
     *
     * @param jobId 任务ID
     * @return 全流程进度信息DTO
     */
    @GetMapping("/status/{jobId}")
    @Operation(summary = "查询全流程计算任务进度", description = "返回当前步骤、已完成步骤和进度百分比")
    public ResponseEntity<PipelineProgressDto> getPipelineStatus(
            @PathVariable Long jobId,
            @RequestParam(required = false) Long testUserId) {  // 测试参数
        log.info("[全流程] 查询任务进度: jobId={}", jobId);

        // 获取当前登录用户ID（认证禁用时使用testUserId或默认用户1）
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            currentUserId = testUserId != null ? testUserId : 1L;
            log.info("[全流程] 测试模式: userId={}", currentUserId);
        }

        // 校验任务是否存在
        Optional<SimulationJob> jobOptional = simulationService.getSimulationById(jobId);
        if (!jobOptional.isPresent()) {
            log.warn("[全流程] 任务不存在: jobId={}", jobId);
            return ResponseEntity.notFound().build();
        }

        // 校验任务是否属于当前用户
        SimulationJob job = jobOptional.get();
        if (!job.getUserId().equals(currentUserId)) {
            log.warn("[全流程] 用户无权访问该任务: userId={}, jobId={}", currentUserId, jobId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            PipelineProgressDto progress = pipelineService.getPipelineProgress(jobId);
            return ResponseEntity.ok(progress);
        } catch (Exception e) {
            log.error("[全流程] 查询任务进度失败: jobId={}, error={}", jobId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 查询全流程计算任务最终结果
     *
     * <p>仅当任务状态为COMPLETED时返回结果，包含SimulationJob的resultSummary
     * 和CalculationResultService中的详细计算结果。</p>
     *
     * @param jobId 任务ID
     * @return 包含结果摘要和详细计算结果的响应
     */
    @GetMapping("/results/{jobId}")
    @Operation(summary = "查询全流程计算任务最终结果", description = "仅当任务状态为COMPLETED时返回，包含结果摘要和详细计算结果")
    public ResponseEntity<Map<String, Object>> getPipelineResults(
            @PathVariable Long jobId,
            @RequestParam(required = false) Long testUserId) {  // 测试参数
        log.info("[全流程] 查询任务结果: jobId={}", jobId);

        // 获取当前登录用户ID（认证禁用时使用testUserId或默认用户1）
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (currentUserId == null) {
            currentUserId = testUserId != null ? testUserId : 1L;
            log.info("[全流程] 测试模式: userId={}", currentUserId);
        }

        // 校验任务是否存在
        Optional<SimulationJob> jobOptional = simulationService.getSimulationById(jobId);
        if (!jobOptional.isPresent()) {
            log.warn("[全流程] 任务不存在: jobId={}", jobId);
            return ResponseEntity.notFound().build();
        }

        // 校验任务是否属于当前用户
        SimulationJob job = jobOptional.get();
        if (!job.getUserId().equals(currentUserId)) {
            log.warn("[全流程] 用户无权访问该任务结果: userId={}, jobId={}", currentUserId, jobId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        // 校验任务状态是否为COMPLETED
        if (!JobStatus.COMPLETED.equals(job.getStatus())) {
            log.warn("[全流程] 任务状态不允许查询结果: jobId={}, status={}", jobId, job.getStatus());
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("error", "任务状态不允许查询结果，当前状态: " + job.getStatus());
            errorBody.put("jobId", jobId);
            errorBody.put("currentStatus", job.getStatus());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorBody);
        }

        try {
            // 获取结果摘要（来自SimulationJob.resultSummary）
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("jobId", jobId);
            responseBody.put("status", job.getStatus());
            responseBody.put("resultSummary", job.getResultSummary());

            // 获取详细计算结果（来自CalculationResultService）
            List<CalculationResultDto> calculationResults = calculationResultService.getResultsByJobId(jobId);
            responseBody.put("calculationResults", calculationResults);

            log.info("[全流程] 查询任务结果成功: jobId={}, 结果数={}", jobId, calculationResults.size());
            return ResponseEntity.ok(responseBody);

        } catch (Exception e) {
            log.error("[全流程] 查询任务结果失败: jobId={}, error={}", jobId, e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "查询结果失败: " + e.getMessage());
            errorResponse.put("jobId", jobId);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
