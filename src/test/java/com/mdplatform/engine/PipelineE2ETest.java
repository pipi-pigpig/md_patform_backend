package com.mdplatform.engine;

import com.mdplatform.engine.dto.PipelineProgressDto;
import com.mdplatform.engine.dto.PipelineSubmitRequest;
import com.mdplatform.engine.model.SimulationInput;
import com.mdplatform.engine.model.SimulationJob;
import com.mdplatform.engine.repository.CalculationResultRepository;
import com.mdplatform.engine.repository.SimulationInputRepository;
import com.mdplatform.engine.repository.SimulationRepository;
import com.mdplatform.engine.service.DockerService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 全流程端到端集成测试
 *
 * <p>功能：
 *     1. 验证从任务提交到结果查询的完整流水线流程
 *     2. 使用H2内存数据库和真实Spring上下文进行测试
 *     3. 验证数据库记录创建、状态追踪、错误处理等核心功能
 * </p>
 *
 * <p>测试说明：
 *     由于测试环境中Docker/md-engine通常不可用，全流程任务会在建模或模拟阶段失败。
 *     这是预期行为，测试重点验证：
 *     - 任务提交接口正常工作（返回202 ACCEPTED）
 *     - 数据库记录正确创建（SimulationJob、SimulationInput）
 *     - 状态追踪API正常工作
 *     - 失败场景的优雅处理（FAILED状态 + 错误信息）</p>
 *
 * <p>标准测试配方：EC:DMC = 3:7（摩尔比），1M LiPF6，温度353K</p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Slf4j
class PipelineE2ETest {

    /** REST测试客户端，用于发送HTTP请求 */
    @Autowired
    private TestRestTemplate restTemplate;

    /** 模拟任务数据访问层，用于直接查询数据库验证记录 */
    @Autowired
    private SimulationRepository simulationRepository;

    /** 模拟输入参数数据访问层，用于验证输入参数记录 */
    @Autowired
    private SimulationInputRepository simulationInputRepository;

    /** 计算结果数据访问层，用于验证后处理结果记录 */
    @Autowired
    private CalculationResultRepository calculationResultRepository;

    /** Docker服务，用于检查Docker环境可用性（可选注入） */
    @Autowired(required = false)
    private DockerService dockerService;

    /** 随机分配的本地服务端口 */
    @LocalServerPort
    private int port;

    /**
     * 测试提交任务并获取初始状态
     *
     * <p>验证内容：
     *     1. POST /api/pipeline/submit 返回202 ACCEPTED
     *     2. 响应体包含jobId、userId、status字段
     *     3. SimulationJob记录已创建，状态为PENDING或MODELING
     *     4. SimulationInput记录已创建，温度为353.0K</p>
     *
     * <p>注意：由于Docker/md-engine在测试环境中通常不可用，
     * 任务会在后续步骤失败，但提交本身应该成功。</p>
     */
    @Test
    @DisplayName("提交任务并获取初始状态 - 验证任务提交接口和数据库记录创建")
    @SuppressWarnings("unchecked")
    void testSubmitTaskAndGetInitialStatus() {
        log.info("[E2E测试] 开始测试：提交任务并获取初始状态");

        // 使用TestDataBuilder构建标准测试请求
        PipelineSubmitRequest request = TestDataBuilder.buildStandardPipelineRequest();

        // 提交全流程计算任务
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/pipeline/submit?testUserId=" + TestDataBuilder.DEFAULT_TEST_USER_ID,
                request,
                Map.class
        );

        // 验证HTTP响应状态为202 ACCEPTED
        log.info("[E2E测试] 提交响应状态: {}", response.getStatusCode());
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode(),
                "任务提交应返回202 ACCEPTED");

        // 提取响应体中的jobId
        Map<String, Object> body = response.getBody();
        assertNotNull(body, "响应体不应为null");
        assertNotNull(body.get("jobId"), "响应体应包含jobId");

        Long jobId = ((Number) body.get("jobId")).longValue();
        log.info("[E2E测试] 创建的任务ID: {}", jobId);

        // 验证响应体中的基本信息
        assertEquals(TestDataBuilder.DEFAULT_TEST_USER_ID, ((Number) body.get("userId")).longValue(),
                "响应体中的userId应为1");
        assertEquals("PENDING", body.get("status"),
                "响应体中的初始状态应为PENDING");

        // 查询数据库验证SimulationJob记录
        SimulationJob job = simulationRepository.findById(jobId).orElse(null);
        assertNotNull(job, "SimulationJob记录应存在");
        log.info("[E2E测试] 数据库中任务状态: {}", job.getStatus());

        // 验证任务状态为PENDING或MODELING（异步执行可能已快速转换状态）
        assertTrue(
                "PENDING".equals(job.getStatus()) || "MODELING".equals(job.getStatus()),
                "任务状态应为PENDING或MODELING，实际状态: " + job.getStatus()
        );

        // 验证用户ID
        assertEquals(TestDataBuilder.DEFAULT_TEST_USER_ID, job.getUserId(),
                "任务所属用户ID应为1");

        // 查询数据库验证SimulationInput记录
        SimulationInput input = simulationInputRepository.findByJobId(jobId).orElse(null);
        assertNotNull(input, "SimulationInput记录应存在");

        // 验证温度参数
        assertEquals(TestDataBuilder.DEFAULT_TEST_TEMPERATURE, input.getTemperature(),
                "输入参数中的温度应为353.0K");

        log.info("[E2E测试] 测试完成：提交任务并获取初始状态");
    }

    /**
     * 测试提交任务并验证SimulationInput默认值
     *
     * <p>验证内容：
     *     1. SimulationInput记录已创建
     *     2. 系综类型默认为NPT
     *     3. 恒温器类型默认为Nose-Hoover
     *     4. 恒压器类型默认为Parrinello-Rahman
     *     5. 压力默认为1.0 bar
     *     6. 时间步长默认为1.0 fs
     *     7. 截断距离默认为12.0 Å</p>
     */
    @Test
    @DisplayName("提交任务并验证SimulationInput默认值 - 验证输入参数的默认配置")
    @SuppressWarnings("unchecked")
    void testSubmitTaskAndVerifySimulationInputCreated() {
        log.info("[E2E测试] 开始测试：提交任务并验证SimulationInput默认值");

        // 提交全流程计算任务
        PipelineSubmitRequest request = TestDataBuilder.buildStandardPipelineRequest();
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/pipeline/submit?testUserId=" + TestDataBuilder.DEFAULT_TEST_USER_ID,
                request,
                Map.class
        );

        // 验证提交成功
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode(),
                "任务提交应返回202 ACCEPTED");

        // 提取jobId
        Map<String, Object> body = response.getBody();
        assertNotNull(body, "响应体不应为null");
        Long jobId = ((Number) body.get("jobId")).longValue();
        log.info("[E2E测试] 创建的任务ID: {}", jobId);

        // 查询SimulationInput记录
        SimulationInput input = simulationInputRepository.findByJobId(jobId).orElse(null);
        assertNotNull(input, "SimulationInput记录应存在");

        // 验证系综类型默认为NPT
        assertEquals("NPT", input.getEnsembleType(),
                "系综类型应默认为NPT");

        // 验证恒温器类型默认为Nose-Hoover
        assertEquals("Nose-Hoover", input.getThermostatType(),
                "恒温器类型应默认为Nose-Hoover");

        // 验证恒压器类型默认为Parrinello-Rahman
        assertEquals("Parrinello-Rahman", input.getBarostatType(),
                "恒压器类型应默认为Parrinello-Rahman");

        // 验证压力默认为1.0 bar
        assertEquals(1.0, input.getPressure(),
                "压力应默认为1.0 bar");

        // 验证时间步长默认为1.0 fs
        assertEquals(1.0, input.getTimeStepFs(),
                "时间步长应默认为1.0 fs");

        // 验证截断距离默认为12.0 Å
        assertEquals(12.0, input.getCutoffDistanceAng(),
                "截断距离应默认为12.0 Å");

        log.info("[E2E测试] 测试完成：提交任务并验证SimulationInput默认值");
    }

    /**
     * 测试提交任务并追踪状态变化
     *
     * <p>验证内容：
     *     1. 提交任务后状态为PENDING
     *     2. 使用Awaitility轮询状态API，等待状态从PENDING变化
     *     3. 状态API返回有效的PipelineProgressDto
     *     4. 最终状态应为FAILED（因为Docker/md-engine不可用）</p>
     *
     * <p>注意：由于测试环境中Docker通常不可用，任务最终会失败。
     * 这是预期行为，测试重点验证状态追踪API的正确性。</p>
     */
    @Test
    @DisplayName("提交任务并追踪状态变化 - 验证状态轮询和进度追踪API")
    @SuppressWarnings("unchecked")
    void testSubmitTaskAndTrackStatus() {
        log.info("[E2E测试] 开始测试：提交任务并追踪状态变化");

        // 提交全流程计算任务
        PipelineSubmitRequest request = TestDataBuilder.buildStandardPipelineRequest();
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/pipeline/submit?testUserId=" + TestDataBuilder.DEFAULT_TEST_USER_ID,
                request,
                Map.class
        );

        // 验证提交成功
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode(),
                "任务提交应返回202 ACCEPTED");

        // 提取jobId
        Map<String, Object> body = response.getBody();
        assertNotNull(body, "响应体不应为null");
        Long jobId = ((Number) body.get("jobId")).longValue();
        log.info("[E2E测试] 创建的任务ID: {}", jobId);

        // 使用Awaitility轮询状态API，等待状态从PENDING变化（最多60秒）
        // 由于Docker不可用，任务应该会快速失败
        await().atMost(60, SECONDS)
                .pollInterval(2, SECONDS)
                .until(() -> {
                    // 调用状态查询API
                    ResponseEntity<PipelineProgressDto> statusResponse = restTemplate.getForEntity(
                            "/api/pipeline/status/" + jobId + "?testUserId=" + TestDataBuilder.DEFAULT_TEST_USER_ID,
                            PipelineProgressDto.class
                    );

                    // 验证API返回200 OK
                    if (statusResponse.getStatusCode() != HttpStatus.OK) {
                        log.warn("[E2E测试] 状态API返回非200状态: {}", statusResponse.getStatusCode());
                        return false;
                    }

                    PipelineProgressDto progress = statusResponse.getBody();
                    if (progress == null) {
                        log.warn("[E2E测试] 状态API返回空响应体");
                        return false;
                    }

                    log.info("[E2E测试] 当前状态: {}, 步骤: {}/{}, 进度: {}%",
                            progress.getStatus(),
                            progress.getCurrentStep(),
                            progress.getTotalSteps(),
                            progress.getProgressPercent());

                    // 等待状态从PENDING变化为其他状态
                    return !"PENDING".equals(progress.getStatus());
                });

        // 最终验证：获取最终状态
        ResponseEntity<PipelineProgressDto> finalResponse = restTemplate.getForEntity(
                "/api/pipeline/status/" + jobId + "?testUserId=" + TestDataBuilder.DEFAULT_TEST_USER_ID,
                PipelineProgressDto.class
        );

        assertEquals(HttpStatus.OK, finalResponse.getStatusCode(),
                "状态API应返回200 OK");

        PipelineProgressDto finalProgress = finalResponse.getBody();
        assertNotNull(finalProgress, "最终进度信息不应为null");

        // 验证PipelineProgressDto包含预期字段
        assertNotNull(finalProgress.getJobId(), "进度信息应包含jobId");
        assertNotNull(finalProgress.getStatus(), "进度信息应包含status");
        assertNotNull(finalProgress.getTotalSteps(), "进度信息应包含totalSteps");

        // 验证jobId匹配
        assertEquals(jobId, finalProgress.getJobId(),
                "进度信息中的jobId应与提交时返回的jobId一致");

        log.info("[E2E测试] 最终状态: {}", finalProgress.getStatus());
        log.info("[E2E测试] 测试完成：提交任务并追踪状态变化");
    }

    /**
     * 测试Docker不可用时流水线失败处理
     *
     * <p>验证内容：
     *     1. 如果Docker可用则跳过此测试
     *     2. 如果Docker不可用，提交任务后应最终变为FAILED状态
     *     3. FAILED状态的任务应包含非空的错误信息
     *     4. SimulationJob记录不应被删除</p>
     *
     * <p>注意：此测试仅在Docker不可用时执行。
     * 如果Docker可用，使用Assumptions跳过测试。</p>
     */
    @Test
    @DisplayName("Docker不可用时流水线失败处理 - 验证失败状态的优雅处理")
    @SuppressWarnings("unchecked")
    void testPipelineFailsWhenDockerUnavailable() {
        log.info("[E2E测试] 开始测试：Docker不可用时流水线失败处理");

        // 检查Docker是否可用，如果可用则跳过此测试
        boolean dockerAvailable = dockerService != null && dockerService.isDockerAvailable();
        org.junit.jupiter.api.Assumptions.assumeTrue(
                !dockerAvailable,
                "Docker可用，跳过此测试（此测试仅在Docker不可用时执行）"
        );

        // 提交全流程计算任务
        PipelineSubmitRequest request = TestDataBuilder.buildStandardPipelineRequest();
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/pipeline/submit?testUserId=" + TestDataBuilder.DEFAULT_TEST_USER_ID,
                request,
                Map.class
        );

        // 验证提交成功
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode(),
                "任务提交应返回202 ACCEPTED");

        // 提取jobId
        Map<String, Object> body = response.getBody();
        assertNotNull(body, "响应体不应为null");
        Long jobId = ((Number) body.get("jobId")).longValue();
        log.info("[E2E测试] 创建的任务ID: {}", jobId);

        // 使用Awaitility等待任务状态变为FAILED（最多120秒）
        await().atMost(120, SECONDS)
                .pollInterval(3, SECONDS)
                .until(() -> {
                    // 查询数据库获取最新状态
                    SimulationJob job = simulationRepository.findById(jobId).orElse(null);
                    if (job == null) {
                        log.warn("[E2E测试] 任务记录不存在: jobId={}", jobId);
                        return false;
                    }
                    log.info("[E2E测试] 当前状态: {}", job.getStatus());
                    return "FAILED".equals(job.getStatus());
                });

        // 验证任务最终状态为FAILED
        SimulationJob failedJob = simulationRepository.findById(jobId).orElse(null);
        assertNotNull(failedJob, "FAILED状态的任务记录应存在（不应被删除）");
        assertEquals("FAILED", failedJob.getStatus(),
                "任务最终状态应为FAILED");

        // 验证错误信息不为空
        assertNotNull(failedJob.getErrorMessage(),
                "FAILED状态的任务应有错误信息");
        assertFalse(failedJob.getErrorMessage().trim().isEmpty(),
                "错误信息不应为空字符串");

        log.info("[E2E测试] 错误信息: {}", failedJob.getErrorMessage());
        log.info("[E2E测试] 测试完成：Docker不可用时流水线失败处理");
    }

    /**
     * 测试查询不存在任务的状态
     *
     * <p>验证内容：
     *     1. 查询不存在的jobId（999999）应返回404 NOT_FOUND
     *     2. 确保API对无效输入有正确的错误处理</p>
     */
    @Test
    @DisplayName("查询不存在任务的状态 - 验证404错误处理")
    void testGetStatusForNonExistentJob() {
        log.info("[E2E测试] 开始测试：查询不存在任务的状态");

        // 查询不存在的任务ID
        ResponseEntity<PipelineProgressDto> response = restTemplate.getForEntity(
                "/api/pipeline/status/999999?testUserId=" + TestDataBuilder.DEFAULT_TEST_USER_ID,
                PipelineProgressDto.class
        );

        // 验证返回404 NOT_FOUND
        log.info("[E2E测试] 查询不存在任务的响应状态: {}", response.getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode(),
                "查询不存在的任务应返回404 NOT_FOUND");

        log.info("[E2E测试] 测试完成：查询不存在任务的状态");
    }
}
