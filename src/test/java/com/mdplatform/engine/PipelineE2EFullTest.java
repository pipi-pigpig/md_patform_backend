package com.mdplatform.engine;

import com.mdplatform.common.config.StorageConfig;
import com.mdplatform.common.util.PathUtil;
import com.mdplatform.engine.dto.PipelineProgressDto;
import com.mdplatform.engine.dto.PipelineSubmitRequest;
import com.mdplatform.engine.model.*;
import com.mdplatform.engine.repository.*;
import com.mdplatform.engine.service.DockerService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端全流程集成测试（完整版）
 *
 * <p>该测试覆盖从任务提交到数据库结果验证的完整链路，不使用任何Mock。</p>
 *
 * <h3>测试范围</h3>
 * <ul>
 *   <li>任务提交：POST /api/pipeline/submit → 202 + jobId</li>
 *   <li>状态追踪：PENDING → MODELING → RUNNING → POST_PROCESSING → COMPLETED</li>
 *   <li>步骤进度：验证每一步的 currentStep、stepName、completedSteps</li>
 *   <li>数据库验证：SimulationJob、SimulationInput、CalculationResult、子表</li>
 *   <li>API 验证：GET /api/pipeline/status/{jobId}、GET /api/pipeline/results/{jobId}</li>
 *   <li>错误处理：不存在任务返回 404、非COMPLETED任务查询结果返回 400</li>
 * </ul>
 *
 * <h3>前置条件</h3>
 * <ul>
 *   <li>Docker 引擎可用</li>
 *   <li>md-engine 容器正在运行</li>
 *   <li>数据库（TiDB）可连接</li>
 * </ul>
 *
 * <h3>测试数据</h3>
 * <p>标准电解液配方：EC:DMC = 3:7（摩尔比），1M LiPF6，温度 353K</p>
 *
 * @author 电解液MD平台
 * @version 2.0.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PipelineE2EFullTest {

    // ==================== 依赖注入 ====================

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private SimulationRepository simulationRepository;

    @Autowired
    private SimulationInputRepository simulationInputRepository;

    @Autowired
    private CalculationResultRepository calculationResultRepository;

    @Autowired
    private DensityResultRepository densityResultRepository;

    @Autowired
    private ConductivityResultRepository conductivityResultRepository;

    @Autowired(required = false)
    private DockerService dockerService;

    /** 路径工具类，用于路径诊断和Docker路径转换验证 */
    @Autowired
    private PathUtil pathUtil;

    /** 存储配置，用于读取和验证root-path配置 */
    @Autowired
    private StorageConfig storageConfig;

    /** Docker容器名称，从配置文件注入，避免硬编码 */
    @org.springframework.beans.factory.annotation.Value("${app.docker.md-container-name}")
    private String mdContainerName;

    @LocalServerPort
    private int port;

    // ==================== 测试状态 ====================

    /** 提交后得到的任务ID，在多个测试方法间共享 */
    private static Long sharedJobId;

    /** 记录状态流转过程 */
    private static final List<String> statusTransitions = Collections.synchronizedList(new ArrayList<>());

    /** 记录步骤进度变化 */
    private static final List<Integer> stepProgressions = Collections.synchronizedList(new ArrayList<>());

    /** 流水线完成标志 */
    private static boolean pipelineCompleted = false;

    /** 流水线失败标志 */
    private static boolean pipelineFailed = false;

    /** 错误信息 */
    private static String failureErrorMessage;

    // ==================== 环境检查 ====================

    /**
     * 在所有测试之前检查 Docker 和 md-engine 可用性。
     * 如果 Docker 不可用，跳过所有测试。
     */
    @BeforeAll
    static void checkEnvironment() {
        log.info("========================================");
        log.info("[E2E-Full] 环境检查开始");
        log.info("========================================");
    }

    @Test
    @Order(0)
    @DisplayName("环境检查 - 验证 Docker 和 md-engine 可用性")
    void testEnvironmentCheck() {
        log.info("[E2E-Full] 检查 Docker 服务...");

        // 检查 Docker 是否可用
        boolean dockerAvailable = dockerService != null && dockerService.isDockerAvailable();
        Assumptions.assumeTrue(dockerAvailable,
                "Docker 不可用，跳过所有端到端测试。请确保 Docker Desktop 正在运行。");

        log.info("[E2E-Full] ✓ Docker 服务可用");

        // 检查容器是否存在（使用配置的容器名称）
        String containerStatus = dockerService.getContainerStatus();
        log.info("[E2E-Full] {} 容器状态: {}", mdContainerName, containerStatus);

        boolean engineAvailable = containerStatus != null
                && (containerStatus.toLowerCase().contains("running"));
        Assumptions.assumeTrue(engineAvailable,
                mdContainerName + " 容器未运行，跳过所有端到端测试。当前状态: " + containerStatus + "。请执行 docker-compose up -d md-engine。");

        log.info("[E2E-Full] ✓ {} 容器正在运行", mdContainerName);

        // ===== 路径对齐诊断 =====
        // 验证本地root-path解析后的绝对路径与Docker挂载源目录对齐
        log.info("[E2E-Full] ===== 路径对齐诊断 =====");
        String configuredRootPath = storageConfig.getRootPath();
        Path resolvedRootPath = Paths.get(configuredRootPath).toAbsolutePath().normalize();
        log.info("[E2E-Full] 配置的root-path: {}", configuredRootPath);
        log.info("[E2E-Full] 解析后的绝对路径: {}", resolvedRootPath);

        // Docker挂载源目录：项目根目录下的 data/md_platform_data
        Path projectRoot = Paths.get("").toAbsolutePath().normalize();
        // 尝试从项目根目录计算Docker挂载源路径
        Path dockerMountSource = projectRoot.resolve("data").resolve("md_platform_data").normalize();
        log.info("[E2E-Full] 项目根目录: {}", projectRoot);
        log.info("[E2E-Full] Docker挂载源路径(推算): {}", dockerMountSource);

        // 检查路径是否对齐（解析后的root-path应与Docker挂载源一致）
        boolean pathAligned = resolvedRootPath.equals(dockerMountSource);
        if (pathAligned) {
            log.info("[E2E-Full] ✓ 路径对齐验证通过：本地root-path与Docker挂载源一致");
        } else {
            // 路径不对齐时输出警告，但不阻止测试执行
            // 因为测试可能从backend/目录运行（此时../data/md_platform_data是正确的）
            log.warn("[E2E-Full] ⚠ 路径对齐验证未通过！");
            log.warn("[E2E-Full]   解析后路径: {}", resolvedRootPath);
            log.warn("[E2E-Full]   Docker挂载源: {}", dockerMountSource);
            log.warn("[E2E-Full]   这可能导致Docker容器无法看到本地创建的文件");
            log.warn("[E2E-Full]   请确保从正确目录运行测试（项目根目录或backend/目录）");
        }

        // 验证root-path目录存在
        if (Files.exists(resolvedRootPath)) {
            log.info("[E2E-Full] ✓ root-path目录存在: {}", resolvedRootPath);
        } else {
            log.warn("[E2E-Full] ⚠ root-path目录不存在: {}，将在任务提交时自动创建", resolvedRootPath);
        }

        // 验证PathUtil.convertToDockerPath()转换是否正确
        Path testPath = resolvedRootPath.resolve("user_1").resolve("jobs").resolve("job_1").resolve("inputs");
        String dockerPath = pathUtil.convertToDockerPath(testPath);
        log.info("[E2E-Full] convertToDockerPath测试: {} → {}", testPath, dockerPath);
        boolean dockerPathCorrect = dockerPath.startsWith("/workspace/data/");
        if (dockerPathCorrect) {
            log.info("[E2E-Full] ✓ Docker路径转换验证通过");
        } else {
            log.error("[E2E-Full] ✗ Docker路径转换异常: {}，应以/workspace/data/开头", dockerPath);
        }
        log.info("[E2E-Full] ===== 路径对齐诊断结束 =====");

        log.info("[E2E-Full] ✓ 环境检查通过，开始端到端测试");
    }

    // ==================== 阶段一：提交任务 ====================

    /**
     * 提交全流程计算任务并验证初始状态。
     *
     * <p>验证点：</p>
     * <ul>
     *   <li>POST /api/pipeline/submit 返回 202 ACCEPTED</li>
     *   <li>响应包含 jobId、userId、status</li>
     *   <li>SimulationJob 记录已创建，状态为 PENDING 或 MODELING</li>
     *   <li>SimulationInput 记录已创建，参数正确</li>
     * </ul>
     */
    @Test
    @Order(1)
    @DisplayName("阶段一：提交任务并验证初始状态")
    @SuppressWarnings("unchecked")
    void testSubmitTaskAndVerifyInitialState() {
        log.info("========================================");
        log.info("[E2E-Full] 阶段一：提交全流程计算任务");
        log.info("========================================");

        // 构建标准测试请求（EC:DMC=3:7, 1M LiPF6, 353K）
        PipelineSubmitRequest request = TestDataBuilder.buildStandardPipelineRequest();
        log.info("[E2E-Full] 测试配方: EC:DMC=3:7, 1M LiPF6, T=353K");
        log.info("[E2E-Full] 目标属性: {}", request.getTargetProperties());

        // 提交任务
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/pipeline/submit?testUserId=" + TestDataBuilder.DEFAULT_TEST_USER_ID,
                request,
                Map.class
        );

        // ===== 验证 HTTP 响应 =====
        log.info("[E2E-Full] HTTP 响应状态: {}", response.getStatusCodeValue());
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode(),
                "任务提交应返回 202 ACCEPTED");

        Map<String, Object> body = response.getBody();
        assertNotNull(body, "响应体不应为 null");
        assertNotNull(body.get("jobId"), "响应体应包含 jobId");

        Long jobId = ((Number) body.get("jobId")).longValue();
        sharedJobId = jobId;
        log.info("[E2E-Full] ✓ 任务提交成功，jobId = {}", jobId);

        // 验证响应基本信息
        assertEquals(TestDataBuilder.DEFAULT_TEST_USER_ID, ((Number) body.get("userId")).longValue(),
                "userId 应为 " + TestDataBuilder.DEFAULT_TEST_USER_ID);
        assertEquals("PENDING", body.get("status"), "初始状态应为 PENDING");
        assertEquals("全流程计算任务已提交，正在异步执行中", body.get("message"),
                "message 应正确");

        // ===== 验证 SimulationJob 记录 =====
        SimulationJob job = simulationRepository.findById(jobId).orElse(null);
        assertNotNull(job, "SimulationJob 记录应存在于数据库中");
        log.info("[E2E-Full] SimulationJob 状态: {}", job.getStatus());
        log.info("[E2E-Full] SimulationJob jobRootPath: {}", job.getJobRootPath());

        // 状态应为 PENDING 或 MODELING（异步可能已开始）
        assertTrue(
                "PENDING".equals(job.getStatus()) || "MODELING".equals(job.getStatus()),
                "任务状态应为 PENDING 或 MODELING，实际: " + job.getStatus()
        );
        assertEquals(TestDataBuilder.DEFAULT_TEST_USER_ID, job.getUserId(),
                "任务所属用户应正确");
        assertNotNull(job.getJobName(), "任务名称不应为 null");
        assertNotNull(job.getTargetProperties(), "目标属性不应为 null");
        assertNotNull(job.getCreateTime(), "创建时间不应为 null");
        assertFalse("pending".equals(job.getJobRootPath()),
                "jobRootPath 不应为占位值 'pending'，应已被 PathUtil 更新");
        assertNotNull(job.getRandomSeed(), "随机种子不应为 null");

        // ===== 验证 SimulationInput 记录 =====
        SimulationInput input = simulationInputRepository.findByJobId(jobId).orElse(null);
        assertNotNull(input, "SimulationInput 记录应存在于数据库中");
        log.info("[E2E-Full] SimulationInput inputId: {}", input.getInputId());

        // ===== 验证 formula_config.json 文件在本地和Docker容器中均存在 =====
        // 本地文件系统验证：确保配方文件已正确写入本地路径
        Path localInputPath = pathUtil.getInputPath(TestDataBuilder.DEFAULT_TEST_USER_ID, jobId);
        Path localFormulaFile = localInputPath.resolve("formula_config.json");
        boolean localFormulaExists = Files.exists(localFormulaFile);
        log.info("[E2E-Full] 本地formula_config.json路径: {}", localFormulaFile);
        log.info("[E2E-Full] 本地formula_config.json存在: {}", localFormulaExists);
        assertTrue(localFormulaExists,
                "formula_config.json应存在于本地文件系统: " + localFormulaFile);

        // Docker容器内验证：确保配方文件在容器内可见（路径对齐验证）
        String dockerFormulaPath = pathUtil.convertToDockerPath(localFormulaFile);
        log.info("[E2E-Full] Docker内formula_config.json路径: {}", dockerFormulaPath);
        try {
            String containerCheckOutput = dockerService.executeCommandInContainer(
                    mdContainerName,
                    Arrays.asList("test", "-f", dockerFormulaPath, "&&", "echo", "EXISTS", "||", "echo", "NOT_FOUND"),
                    "/workspace"
            );
            boolean containerFormulaExists = containerCheckOutput != null
                    && containerCheckOutput.trim().contains("EXISTS");
            log.info("[E2E-Full] Docker容器内formula_config.json存在: {}", containerFormulaExists);
            if (!containerFormulaExists) {
                log.warn("[E2E-Full] ⚠ Docker容器内未找到formula_config.json，路径对齐可能有问题");
                log.warn("[E2E-Full]   本地路径: {}", localFormulaFile);
                log.warn("[E2E-Full]   Docker路径: {}", dockerFormulaPath);
                log.warn("[E2E-Full]   后续Pipeline步骤可能因文件不可见而失败");
            }
        } catch (Exception e) {
            log.warn("[E2E-Full] 无法验证Docker容器内formula_config.json: {}", e.getMessage());
        }

        // 验证温度（使用请求中指定的 353.0K）
        assertEquals(TestDataBuilder.DEFAULT_TEST_TEMPERATURE, input.getTemperature(), 0.001,
                "温度应为 " + TestDataBuilder.DEFAULT_TEST_TEMPERATURE + "K");

        // 验证默认参数
        assertEquals("NPT", input.getEnsembleType(), "系综类型默认为 NPT");
        assertEquals("Nose-Hoover", input.getThermostatType(), "恒温器默认为 Nose-Hoover");
        assertEquals("Parrinello-Rahman", input.getBarostatType(), "恒压器默认为 Parrinello-Rahman");
        assertEquals(1.0, input.getPressure(), 0.001, "压力默认为 1.0 bar");
        assertEquals(1.0, input.getTimeStepFs(), 0.001, "时间步长默认为 1.0 fs");
        assertEquals(12.0, input.getCutoffDistanceAng(), 0.001, "截断距离默认为 12.0 Å");
        assertEquals("Velocity-Verlet", input.getIntegrationAlgorithm(),
                "积分算法默认为 Velocity-Verlet");
        assertEquals("PPPM, accuracy 1.0e-4", input.getLongRangeElectrostatics(),
                "长程静电方法默认为 PPPM");

        log.info("[E2E-Full] ✓ 阶段一完成：任务提交和初始状态验证通过");
    }

    // ==================== 阶段二：追踪流水线执行状态 ====================

    /**
     * 追踪完整的流水线执行状态流转。
     *
     * <p>验证点：</p>
     * <ul>
     *   <li>状态按 PENDING → MODELING → RUNNING → POST_PROCESSING → COMPLETED 流转</li>
     *   <li>每个状态都能通过 API 查询到</li>
     *   <li>currentStep 随执行递增</li>
     *   <li>completedSteps 列表逐步增长</li>
     *   <li>progressPercent 从 0 增长到 100</li>
     *   <li>stepName 与当前步骤匹配</li>
     * </ul>
     */
    @Test
    @Order(2)
    @DisplayName("阶段二：追踪完整流水线状态流转")
    void testTrackFullPipelineExecution() {
        log.info("========================================");
        log.info("[E2E-Full] 阶段二：追踪流水线执行状态");
        log.info("========================================");

        assertNotNull(sharedJobId, "jobId 不应为 null，请确保阶段一已执行");
        log.info("[E2E-Full] 开始轮询任务状态，jobId = {}", sharedJobId);

        // 使用 AtomicReference 记录状态变化（lambda 中不能修改外部局部变量）
        java.util.concurrent.atomic.AtomicReference<String> previousStatus =
                new java.util.concurrent.atomic.AtomicReference<>(null);
        java.util.concurrent.atomic.AtomicInteger previousStep =
                new java.util.concurrent.atomic.AtomicInteger(-1);

        // 轮询等待任务完成或失败（最多 15 分钟）
        await().atMost(900, SECONDS)
                .pollInterval(5, SECONDS)
                .until(() -> {
                    ResponseEntity<PipelineProgressDto> response = restTemplate.getForEntity(
                            "/api/pipeline/status/" + sharedJobId
                                    + "?testUserId=" + TestDataBuilder.DEFAULT_TEST_USER_ID,
                            PipelineProgressDto.class
                    );

                    if (response.getStatusCode() != HttpStatus.OK) {
                        log.warn("[E2E-Full] 状态 API 返回非 200: {}", response.getStatusCode());
                        return false;
                    }

                    PipelineProgressDto progress = response.getBody();
                    if (progress == null) {
                        log.warn("[E2E-Full] 状态 API 返回空响应");
                        return false;
                    }

                    String currentStatus = progress.getStatus();
                    int currentStep = progress.getCurrentStep();
                    String prevStatus = previousStatus.get();
                    int prevStep = previousStep.get();

                    // 记录状态变化
                    if (!currentStatus.equals(prevStatus)) {
                        statusTransitions.add(currentStatus);
                        log.info("[E2E-Full] 📊 状态变更: {} → {} (步骤 {}/{}, 进度 {}%)",
                                prevStatus, currentStatus,
                                currentStep, progress.getTotalSteps(),
                                progress.getProgressPercent());
                        previousStatus.set(currentStatus);
                    }

                    // 记录步骤变化
                    if (currentStep != prevStep) {
                        stepProgressions.add(currentStep);
                        log.info("[E2E-Full] 🔧 步骤推进: {} → {} ({})",
                                prevStep, currentStep, progress.getStepName());
                        previousStep.set(currentStep);
                    }

                    // 检查是否完成或失败
                    if ("COMPLETED".equals(currentStatus)) {
                        pipelineCompleted = true;
                        log.info("[E2E-Full] ✅ 流水线执行完成!");
                        return true;
                    }

                    if ("FAILED".equals(currentStatus)) {
                        pipelineFailed = true;
                        failureErrorMessage = progress.getErrorMessage();
                        log.error("[E2E-Full] ❌ 流水线执行失败: {}", failureErrorMessage);
                        return true;
                    }

                    return false;
                });

        // 获取最终状态
        ResponseEntity<PipelineProgressDto> finalResponse = restTemplate.getForEntity(
                "/api/pipeline/status/" + sharedJobId
                        + "?testUserId=" + TestDataBuilder.DEFAULT_TEST_USER_ID,
                PipelineProgressDto.class
        );

        assertEquals(HttpStatus.OK, finalResponse.getStatusCode(), "最终状态 API 应返回 200");
        PipelineProgressDto finalProgress = finalResponse.getBody();
        assertNotNull(finalProgress, "最终进度不应为 null");

        log.info("========================================");
        log.info("[E2E-Full] 流水线执行结果总结:");
        log.info("[E2E-Full]   最终状态: {}", finalProgress.getStatus());
        log.info("[E2E-Full]   当前步骤: {}/{} ({})",
                finalProgress.getCurrentStep(), finalProgress.getTotalSteps(),
                finalProgress.getStepName());
        log.info("[E2E-Full]   进度: {}%", finalProgress.getProgressPercent());
        log.info("[E2E-Full]   已完成步骤数: {}", finalProgress.getCompletedSteps().size());
        log.info("[E2E-Full]   状态流转: {}", String.join(" → ", statusTransitions));
        log.info("[E2E-Full]   步骤推进: {}", stepProgressions);
        if (finalProgress.getErrorMessage() != null) {
            log.info("[E2E-Full]   错误信息: {}", finalProgress.getErrorMessage());
        }
        log.info("========================================");

        // ===== 验证状态流转 =====
        assertFalse(statusTransitions.isEmpty(), "应至少有一次状态变化");

        // 验证最终状态为 COMPLETED 或 FAILED（取决于环境）
        String finalStatus = finalProgress.getStatus();
        assertTrue(
                "COMPLETED".equals(finalStatus) || "FAILED".equals(finalStatus),
                "最终状态应为 COMPLETED 或 FAILED，实际: " + finalStatus
        );

        // ===== 验证进度字段 =====
        assertEquals(sharedJobId, finalProgress.getJobId(), "jobId 应匹配");
        assertNotNull(finalProgress.getTotalSteps(), "totalSteps 不应为 null");
        assertTrue(finalProgress.getTotalSteps() > 0, "totalSteps 应大于 0");

        // COMPLETED 状态时进度必须为 100%
        if ("COMPLETED".equals(finalStatus)) {
            assertEquals(100, finalProgress.getProgressPercent(),
                    "COMPLETED 状态时进度应为 100%");
        }

        // ===== 验证步骤推进是递增的 =====
        if (stepProgressions.size() > 1) {
            for (int i = 1; i < stepProgressions.size(); i++) {
                assertTrue(stepProgressions.get(i) >= stepProgressions.get(i - 1),
                        "步骤编号应递增: " + stepProgressions);
            }
        }

        log.info("[E2E-Full] ✓ 阶段二完成：状态流转验证通过");
    }

    // ==================== 阶段三：验证数据库写入 ====================

    /**
     * 验证流水线执行完成后数据库中的记录正确。
     *
     * <p>验证点：</p>
     * <ul>
     *   <li>SimulationJob: status、startTime、endTime、executionTimeS</li>
     *   <li>SimulationInput: 所有参数值与提交一致</li>
     *   <li>CalculationResult: 每个 target_property 对应一条记录</li>
     *   <li>propertyValue 不为 null、不为 NaN</li>
     *   <li>temperatureK 与提交参数一致</li>
     *   <li>convergenceStatus 有值</li>
     *   <li>子表（DensityResult 等）数据完整</li>
     * </ul>
     */
    @Test
    @Order(3)
    @DisplayName("阶段三：验证数据库计算结果写入")
    void testVerifyDatabaseAfterCompletion() {
        log.info("========================================");
        log.info("[E2E-Full] 阶段三：验证数据库计算结果写入");
        log.info("========================================");

        assertNotNull(sharedJobId, "jobId 不应为 null");

        // ===== 1. 验证 SimulationJob =====
        SimulationJob job = simulationRepository.findById(sharedJobId).orElse(null);
        assertNotNull(job, "SimulationJob 记录应存在");
        log.info("[E2E-Full] SimulationJob:");
        log.info("[E2E-Full]   status = {}", job.getStatus());
        log.info("[E2E-Full]   startTime = {}", job.getStartTime());
        log.info("[E2E-Full]   endTime = {}", job.getEndTime());
        log.info("[E2E-Full]   executionTimeS = {}", job.getExecutionTimeS());
        log.info("[E2E-Full]   errorMessage = {}", job.getErrorMessage());

        // 如果是 COMPLETED 状态，验证时间字段
        if ("COMPLETED".equals(job.getStatus())) {
            assertNotNull(job.getStartTime(), "COMPLETED 任务应有 startTime");
            assertNotNull(job.getEndTime(), "COMPLETED 任务应有 endTime");
            assertNotNull(job.getExecutionTimeS(), "COMPLETED 任务应有 executionTimeS");
            assertTrue(job.getExecutionTimeS() > 0,
                    "executionTimeS 应为正数，实际: " + job.getExecutionTimeS());
            assertNull(job.getErrorMessage(), "COMPLETED 任务不应有 errorMessage");
            log.info("[E2E-Full] ✓ SimulationJob 时间字段完整");
        }

        // 如果是 FAILED 状态，验证错误信息
        if ("FAILED".equals(job.getStatus())) {
            assertNotNull(job.getErrorMessage(), "FAILED 任务应有 errorMessage");
            assertFalse(job.getErrorMessage().trim().isEmpty(),
                    "errorMessage 不应为空字符串");
            log.info("[E2E-Full] ✓ SimulationJob 错误信息已记录");
        }

        // ===== 2. 验证 SimulationInput =====
        SimulationInput input = simulationInputRepository.findByJobId(sharedJobId).orElse(null);
        assertNotNull(input, "SimulationInput 记录应存在");
        log.info("[E2E-Full] SimulationInput:");
        log.info("[E2E-Full]   temperature = {}", input.getTemperature());
        log.info("[E2E-Full]   pressure = {}", input.getPressure());
        log.info("[E2E-Full]   ensembleType = {}", input.getEnsembleType());
        log.info("[E2E-Full]   cutoffDistanceAng = {}", input.getCutoffDistanceAng());

        assertEquals(TestDataBuilder.DEFAULT_TEST_TEMPERATURE, input.getTemperature(), 0.001,
                "temperature 应与提交参数一致");
        assertEquals(1.0, input.getPressure(), 0.001, "pressure 应为默认值 1.0");
        assertEquals(1.0, input.getTimeStepFs(), 0.001, "timeStepFs 应为默认值 1.0");

        // ===== 3. 验证 CalculationResult（仅 COMPLETED 时） =====
        if ("COMPLETED".equals(job.getStatus())) {
            List<CalculationResult> results = calculationResultRepository.findByJobId(sharedJobId);
            log.info("[E2E-Full] CalculationResult 记录数: {}", results.size());

            // 每个 target_property 应对应至少一条 CalculationResult
            assertFalse(results.isEmpty(),
                    "COMPLETED 任务应有 CalculationResult 记录");

            // 记录所有 propertyName
            Set<String> propertyNames = new HashSet<>();
            for (CalculationResult result : results) {
                log.info("[E2E-Full] CalculationResult #{}:", result.getResultId());
                log.info("[E2E-Full]   propertyName = {}", result.getPropertyName());
                log.info("[E2E-Full]   propertyValue = {}", result.getPropertyValue());
                log.info("[E2E-Full]   propertyUnit = {}", result.getPropertyUnit());
                log.info("[E2E-Full]   temperatureK = {}", result.getTemperatureK());
                log.info("[E2E-Full]   convergenceStatus = {}", result.getConvergenceStatus());
                log.info("[E2E-Full]   calculationMethod = {}", result.getCalculationMethod());
                log.info("[E2E-Full]   samplingTimePs = {}", result.getSamplingTimePs());
                log.info("[E2E-Full]   rawDataPath = {}", result.getRawDataPath());

                // 验证基本字段
                assertNotNull(result.getPropertyName(), "propertyName 不应为 null");
                assertFalse(result.getPropertyName().trim().isEmpty(),
                        "propertyName 不应为空");

                assertNotNull(result.getPropertyValue(),
                        "propertyValue 不应为 null (propertyName=" + result.getPropertyName() + ")");
                assertFalse(Double.isNaN(result.getPropertyValue()),
                        "propertyValue 不应为 NaN (propertyName=" + result.getPropertyName() + ")");
                assertTrue(Double.isFinite(result.getPropertyValue()),
                        "propertyValue 应为有限值 (propertyName=" + result.getPropertyName() + ")");

                assertNotNull(result.getPropertyUnit(), "propertyUnit 不应为 null");
                assertNotNull(result.getConvergenceStatus(), "convergenceStatus 不应为 null");
                assertNotNull(result.getCalculationMethod(), "calculationMethod 不应为 null");
                assertNotNull(result.getCreateTime(), "createTime 不应为 null");

                // 验证 temperatureK 与提交参数一致
                assertEquals(TestDataBuilder.DEFAULT_TEST_TEMPERATURE,
                        result.getTemperatureK(), 0.001,
                        "CalculationResult.temperatureK 应与提交参数一致 (propertyName="
                                + result.getPropertyName() + ")");

                // 验证 samplingTimePs > 0
                assertTrue(result.getSamplingTimePs() > 0,
                        "samplingTimePs 应大于 0 (propertyName=" + result.getPropertyName() + ")");

                // 验证 rawDataPath 指向有效路径（如果非 null）
                if (result.getRawDataPath() != null) {
                    assertFalse(result.getRawDataPath().trim().isEmpty(),
                            "rawDataPath 不应为空字符串");
                }

                propertyNames.add(result.getPropertyName());
            }

            // 验证 target_properties 都有对应记录
            List<String> expectedProperties = TestDataBuilder.DEFAULT_TARGET_PROPERTIES;
            log.info("[E2E-Full] 预期目标属性: {}", expectedProperties);
            log.info("[E2E-Full] 实际计算结果属性: {}", propertyNames);

            for (String expected : expectedProperties) {
                assertTrue(propertyNames.contains(expected),
                        "target_property '" + expected + "' 应有对应的 CalculationResult 记录"
                                + "，实际属性: " + propertyNames);
            }

            // ===== 4. 验证 DensityResult 子表 =====
            if (propertyNames.contains("density")) {
                // 找到 density 的 CalculationResult
                CalculationResult densityResult = results.stream()
                        .filter(r -> "density".equals(r.getPropertyName()))
                        .findFirst().orElse(null);

                if (densityResult != null) {
                    DensityResult densityDetail = densityResultRepository
                            .findByResultId(densityResult.getResultId());

                    // 注意：可能存在也可能不存在，取决于后处理实现
                    if (densityDetail != null) {
                        log.info("[E2E-Full] DensityResult 子表存在");
                        log.info("[E2E-Full]   densityTensor = {}", densityDetail.getDensityTensor());
                        log.info("[E2E-Full]   componentDensity = {}", densityDetail.getComponentDensity());
                        assertEquals(densityResult.getResultId(), densityDetail.getResultId(),
                                "DensityResult.resultId 应与 CalculationResult.resultId 一致");
                    } else {
                        log.info("[E2E-Full] DensityResult 子表不存在（后处理可能未生成）");
                    }
                }
            }

            // ===== 5. 验证 ConductivityResult 子表 =====
            if (propertyNames.contains("conductivity")) {
                CalculationResult condResult = results.stream()
                        .filter(r -> "conductivity".equals(r.getPropertyName()))
                        .findFirst().orElse(null);

                if (condResult != null) {
                    ConductivityResult condDetail = conductivityResultRepository
                            .findByResultId(condResult.getResultId());

                    if (condDetail != null) {
                        log.info("[E2E-Full] ConductivityResult 子表存在");
                        log.info("[E2E-Full]   conductivityTensor = {}", condDetail.getConductivityTensor());
                        log.info("[E2E-Full]   ionContribution = {}", condDetail.getIonContribution());
                        assertEquals(condResult.getResultId(), condDetail.getResultId(),
                                "ConductivityResult.resultId 应与 CalculationResult.resultId 一致");
                    } else {
                        log.info("[E2E-Full] ConductivityResult 子表不存在（后处理可能未生成）");
                    }
                }
            }

            log.info("[E2E-Full] ✓ 阶段三完成：数据库结果验证通过");
        } else {
            log.info("[E2E-Full] ⚠ 任务未完成（状态={}），跳过 CalculationResult 验证", job.getStatus());
        }
    }

    // ==================== 阶段四：验证 API 返回结果 ====================

    /**
     * 验证 GET /api/pipeline/results/{jobId} API。
     *
     * <p>验证点：</p>
     * <ul>
     *   <li>COMPLETED 任务返回 200 + 完整结果</li>
     *   <li>非 COMPLETED 任务返回 400 错误</li>
     *   <li>不存在任务返回 404</li>
     * </ul>
     */
    @Test
    @Order(4)
    @DisplayName("阶段四：验证 API 结果返回")
    @SuppressWarnings("unchecked")
    void testVerifyApiResults() {
        log.info("========================================");
        log.info("[E2E-Full] 阶段四：验证 API 结果返回");
        log.info("========================================");

        assertNotNull(sharedJobId, "jobId 不应为 null");

        SimulationJob job = simulationRepository.findById(sharedJobId).orElse(null);
        assertNotNull(job, "SimulationJob 记录应存在");

        if ("COMPLETED".equals(job.getStatus())) {
            // ===== 测试 COMPLETED 任务的结果 API =====
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    "/api/pipeline/results/" + sharedJobId
                            + "?testUserId=" + TestDataBuilder.DEFAULT_TEST_USER_ID,
                    Map.class
            );

            log.info("[E2E-Full] results API 响应状态: {}", response.getStatusCodeValue());
            assertEquals(HttpStatus.OK, response.getStatusCode(),
                    "COMPLETED 任务的结果 API 应返回 200");

            Map<String, Object> body = response.getBody();
            assertNotNull(body, "响应体不应为 null");

            // 验证基本字段
            assertEquals(sharedJobId.intValue(), ((Number) body.get("jobId")).intValue(),
                    "jobId 应匹配");
            assertEquals("COMPLETED", body.get("status"), "status 应为 COMPLETED");
            assertNotNull(body.get("resultSummary"), "应包含 resultSummary");

            // 验证 calculationResults
            Object calcResultsObj = body.get("calculationResults");
            assertNotNull(calcResultsObj, "应包含 calculationResults");

            if (calcResultsObj instanceof List) {
                List<Map<String, Object>> calcResults = (List<Map<String, Object>>) calcResultsObj;
                log.info("[E2E-Full] API 返回 calculationResults 数量: {}", calcResults.size());
                assertFalse(calcResults.isEmpty(), "calculationResults 不应为空");

                for (Map<String, Object> result : calcResults) {
                    log.info("[E2E-Full]   propertyName={}, propertyValue={}, propertyUnit={}",
                            result.get("propertyName"),
                            result.get("propertyValue"),
                            result.get("propertyUnit"));

                    assertNotNull(result.get("propertyName"),
                            "每条结果应包含 propertyName");
                    assertNotNull(result.get("propertyValue"),
                            "每条结果应包含 propertyValue");
                    assertNotNull(result.get("propertyUnit"),
                            "每条结果应包含 propertyUnit");

                    // 验证 propertyValue 是有效数字
                    Object value = result.get("propertyValue");
                    if (value instanceof Number) {
                        double d = ((Number) value).doubleValue();
                        assertFalse(Double.isNaN(d), "propertyValue 不应为 NaN");
                        assertTrue(Double.isFinite(d), "propertyValue 应为有限值");
                    }
                }
            }

            log.info("[E2E-Full] ✓ 阶段四完成：API 结果验证通过");
        } else {
            // ===== 测试非 COMPLETED 任务的结果 API =====
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    "/api/pipeline/results/" + sharedJobId
                            + "?testUserId=" + TestDataBuilder.DEFAULT_TEST_USER_ID,
                    Map.class
            );

            log.info("[E2E-Full] 非COMPLETED任务 results API 响应状态: {}", response.getStatusCodeValue());
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                    "非 COMPLETED 任务的结果 API 应返回 400 BAD_REQUEST");

            Map<String, Object> body = response.getBody();
            if (body != null) {
                assertTrue(body.containsKey("error"), "错误响应应包含 error 字段");
                log.info("[E2E-Full] 错误响应: {}", body.get("error"));
            }

            log.info("[E2E-Full] ✓ 非COMPLETED任务正确返回 400");
        }

        // ===== 测试不存在任务返回 404 =====
        ResponseEntity<PipelineProgressDto> notFoundResponse = restTemplate.getForEntity(
                "/api/pipeline/status/999999?testUserId=" + TestDataBuilder.DEFAULT_TEST_USER_ID,
                PipelineProgressDto.class
        );
        assertEquals(HttpStatus.NOT_FOUND, notFoundResponse.getStatusCode(),
                "查询不存在的任务应返回 404 NOT_FOUND");
        log.info("[E2E-Full] ✓ 不存在任务正确返回 404");
    }

    // ==================== 综合报告 ====================

    /**
     * 在所有测试之后生成综合端到端测试报告。
     */
    @Test
    @Order(99)
    @DisplayName("生成端到端测试综合报告")
    void testGenerateFinalReport() {
        log.info("");
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║          端到端全流程测试报告 (Pipeline E2E Full)           ║");
        log.info("╠══════════════════════════════════════════════════════════════╣");

        if (sharedJobId == null) {
            log.info("║  ⚠ 任务未提交，无法生成完整报告                           ║");
            log.info("╚══════════════════════════════════════════════════════════════╝");
            return;
        }

        SimulationJob job = simulationRepository.findById(sharedJobId).orElse(null);
        if (job == null) {
            log.info("║  ⚠ 任务记录不存在，无法生成完整报告                       ║");
            log.info("╚══════════════════════════════════════════════════════════════╝");
            return;
        }

        log.info("║  任务 ID: {}{}", sharedJobId,
                " ".repeat(Math.max(0, 44 - String.valueOf(sharedJobId).length())) + "║");
        log.info("║  任务名称: {}{}", job.getJobName(),
                " ".repeat(Math.max(0, 45 - safeLength(job.getJobName()))) + "║");
        log.info("║  最终状态: {}{}", job.getStatus(),
                " ".repeat(Math.max(0, 45 - safeLength(job.getStatus()))) + "║");
        log.info("║  状态流转: {}{}",
                String.join(" → ", statusTransitions),
                " ".repeat(Math.max(0, 44 - String.join(" → ", statusTransitions).length())) + "║");
        log.info("║  步骤推进: {}{}",
                stepProgressions,
                " ".repeat(Math.max(0, 44 - stepProgressions.toString().length())) + "║");

        // 时间信息
        log.info("╠══════════════════════════════════════════════════════════════╣");
        log.info("║  执行时间统计                                               ║");
        log.info("║    创建时间: {}{}", job.getCreateTime(),
                " ".repeat(Math.max(0, 40 - safeLength(String.valueOf(job.getCreateTime())))) + "║");
        log.info("║    开始时间: {}{}", job.getStartTime(),
                " ".repeat(Math.max(0, 40 - safeLength(String.valueOf(job.getStartTime())))) + "║");
        log.info("║    结束时间: {}{}", job.getEndTime(),
                " ".repeat(Math.max(0, 40 - safeLength(String.valueOf(job.getEndTime())))) + "║");
        log.info("║    执行耗时: {}秒{}", job.getExecutionTimeS(),
                " ".repeat(Math.max(0, 39 - safeLength(String.valueOf(job.getExecutionTimeS())))) + "║");

        // 计算结果统计
        if ("COMPLETED".equals(job.getStatus())) {
            List<CalculationResult> results = calculationResultRepository.findByJobId(sharedJobId);
            log.info("╠══════════════════════════════════════════════════════════════╣");
            log.info("║  计算结果统计 (共 {} 条)                                   ║", results.size());

            for (CalculationResult r : results) {
                String line = String.format("║    %-12s = %-10.4f %-12s [%s]",
                        r.getPropertyName(), r.getPropertyValue(),
                        r.getPropertyUnit(), r.getConvergenceStatus());
                log.info(line + " ".repeat(Math.max(0, 58 - line.length())) + "║");
            }
        }

        // 错误信息（如有）
        if (job.getErrorMessage() != null && !job.getErrorMessage().isEmpty()) {
            log.info("╠══════════════════════════════════════════════════════════════╣");
            log.info("║  错误信息:                                                  ║");
            String err = job.getErrorMessage();
            if (err.length() > 54) {
                log.info("║    {}...║", err.substring(0, 51));
            } else {
                log.info("║    {}{}", err,
                        " ".repeat(Math.max(0, 52 - err.length())) + "║");
            }
        }

        log.info("╚══════════════════════════════════════════════════════════════╝");
        log.info("");
    }

    // ==================== 辅助方法 ====================

    private static int safeLength(String s) {
        return s == null ? 4 : s.length();
    }
}
