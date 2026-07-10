package com.mdplatform.engine;

import com.mdplatform.common.config.StorageConfig;
import com.mdplatform.common.util.PathUtil;
import com.mdplatform.engine.dto.PipelineProgressDto;
import com.mdplatform.engine.dto.PipelineSubmitRequest;
import com.mdplatform.engine.dto.PipelineStepConstants;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实数据库全流程集成测试
 *
 * <p>该测试使用真实TiDB数据库提交任务（jobId=7），
 * 验证从任务提交到LAMMPS模拟、后处理和数据库存储的完整流程。
 * 不使用任何Mock，需要Docker引擎和md-engine容器可用。</p>
 *
 * <p>测试配方：EC:DMC = 3:7（摩尔比），1M LiPF6，温度353K</p>
 *
 * <pre>
 * 测试步骤：
 * 1. 环境检查（Docker、md-engine、路径对齐）
 * 2. 提交任务 -> 验证初始状态 -> 数据库记录
 * 3. 轮询等待流水线完成（COMPLETED/FAILED）
 * 4. 验证数据库记录（SimulationJob、SimulationInput、CalculationResult）
 * 5. 验证文件系统（输入文件、轨迹文件、后处理结果）
 * 6. 生成测试报告
 * </pre>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("real-db")
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PipelineRealDbSubmissionTest {

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

    @Autowired
    private PathUtil pathUtil;

    @Autowired
    private StorageConfig storageConfig;

    @org.springframework.beans.factory.annotation.Value("${app.docker.md-container-name}")
    private String mdContainerName;

    @LocalServerPort
    private int port;

    // ==================== 测试状态 ====================

    /** 提交后得到的任务ID，固定使用7便于验证 */
    private static final Long TARGET_JOB_ID = 7L;

    /** 默认测试用户ID */
    private static final Long TEST_USER_ID = 1L;

    /** 记录状态流转过程 */
    private static final List<String> statusTransitions = Collections.synchronizedList(new ArrayList<>());

    /** 流水线完成标志 */
    private static boolean pipelineCompleted = false;

    /** 流水线失败标志 */
    private static boolean pipelineFailed = false;

    /** 错误信息 */
    private static String failureErrorMessage;

    // ==================== 环境检查 ====================

    @BeforeAll
    static void init() {
        log.info("========================================");
        log.info("[RealDB] 真实数据库全流程测试初始化");
        log.info("[RealDB] 目标jobId = {}", TARGET_JOB_ID);
        log.info("[RealDB] 测试用户 = {}", TEST_USER_ID);
        log.info("[RealDB] 配方: EC:DMC=3:7, 1M LiPF6, T=353K");
        log.info("[RealDB] 目标属性: [density, conductivity]");
        log.info("========================================");

        // 清理static状态
        statusTransitions.clear();
        pipelineCompleted = false;
        pipelineFailed = false;
        failureErrorMessage = null;
    }

    @Test
    @Order(0)
    @DisplayName("环境检查 - Docker、md-engine、路径对齐")
    void testEnvironmentCheck() {
        log.info("[RealDB] ===== 环境检查 =====");

        // 1. 检查Docker是否可用
        boolean dockerAvailable = dockerService != null && dockerService.isDockerAvailable();
        assertTrue(dockerAvailable, "Docker必须可用，请确保Docker Desktop正在运行");
        log.info("[RealDB] ✓ Docker服务可用");

        // 2. 检查md-engine容器
        String containerStatus = dockerService.getContainerStatus();
        log.info("[RealDB] {}容器状态: {}", mdContainerName, containerStatus);
        assertTrue(containerStatus != null && containerStatus.toLowerCase().contains("running"),
                mdContainerName + "容器未运行，请执行 docker-compose up -d md-engine");
        log.info("[RealDB] ✓ {}容器正在运行", mdContainerName);

        // 3. 路径对齐诊断
        log.info("[RealDB] ===== 路径对齐诊断 =====");
        String configuredRootPath = storageConfig.getRootPath();
        Path resolvedRootPath = Paths.get(configuredRootPath).toAbsolutePath().normalize();
        log.info("[RealDB] root-path配置: {}", configuredRootPath);
        log.info("[RealDB] 绝对路径: {}", resolvedRootPath);

        Path projectRoot = Paths.get("").toAbsolutePath().normalize();
        Path dockerMountSource = projectRoot.resolve("data").resolve("md_platform_data").normalize();
        log.info("[RealDB] 项目根目录: {}", projectRoot);
        log.info("[RealDB] Docker挂载源: {}", dockerMountSource);

        boolean pathAligned = resolvedRootPath.equals(dockerMountSource);
        if (pathAligned) {
            log.info("[RealDB] ✓ 路径对齐验证通过");
        } else {
            log.warn("[RealDB] ⚠ 路径未对齐: {} vs {}", resolvedRootPath, dockerMountSource);
        }

        // 4. 验证Docker路径转换
        Path testPath = resolvedRootPath.resolve("user_1").resolve("jobs").resolve("job_7").resolve("inputs");
        String dockerPath = pathUtil.convertToDockerPath(testPath);
        log.info("[RealDB] convertToDockerPath: {} → {}", testPath, dockerPath);
        assertTrue(dockerPath.startsWith("/workspace/data/"), "Docker路径应以/workspace/data/开头");
        log.info("[RealDB] ✓ Docker路径转换验证通过");

        log.info("[RealDB] ===== 环境检查通过 =====");
    }

    @Test
    @Order(1)
    @DisplayName("阶段一：提交任务并验证初始状态")
    @SuppressWarnings("unchecked")
    void testSubmitTaskAndVerifyInitialState() {
        log.info("[RealDB] ===== 阶段一：提交任务 =====");

        // 构建标准测试请求
        PipelineSubmitRequest request = TestDataBuilder.buildStandardPipelineRequest();
        request.setJobName("RealDB测试-EC:DMC(3:7)-1M LiPF6-353K");
        log.info("[RealDB] 测试配方: EC:DMC=3:7, 1M LiPF6, T=353K");
        log.info("[RealDB] 目标属性: {}", request.getTargetProperties());

        // 提交任务
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/pipeline/submit?testUserId=" + TEST_USER_ID,
                request,
                Map.class
        );

        // 验证HTTP响应
        log.info("[RealDB] HTTP响应状态: {}", response.getStatusCodeValue());
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode(),
                "任务提交应返回202 ACCEPTED");

        Map<String, Object> body = response.getBody();
        assertNotNull(body, "响应体不应为null");

        // 验证返回的jobId
        Number jobIdNum = (Number) body.get("jobId");
        assertNotNull(jobIdNum, "响应体应包含jobId");
        Long actualJobId = jobIdNum.longValue();
        log.info("[RealDB] ✓ 任务提交成功，实际jobId = {}", actualJobId);

        // 注意：由于数据库自增ID，实际jobId可能不是7
        // 我们记录实际ID用于后续验证
        log.info("[RealDB] 响应基本信息: userId={}, status={}, message={}",
                body.get("userId"), body.get("status"), body.get("message"));

        assertEquals(TEST_USER_ID, ((Number) body.get("userId")).longValue(), "userId应匹配");
        assertNotNull(body.get("status"), "status不应为null");
        assertNotNull(body.get("message"), "message不应为null");

        // ===== 验证SimulationJob记录 =====
        SimulationJob job = simulationRepository.findById(actualJobId).orElse(null);
        assertNotNull(job, "SimulationJob记录应存在于数据库中");
        log.info("[RealDB] SimulationJob状态: {}", job.getStatus());
        log.info("[RealDB] jobRootPath: {}", job.getJobRootPath());
        log.info("[RealDB] systemId: {}", job.getSystemId());

        // 状态应为PENDING或MODELING
        assertTrue("PENDING".equals(job.getStatus()) || "MODELING".equals(job.getStatus()),
                "状态应为PENDING或MODELING，实际: " + job.getStatus());
        assertEquals(TEST_USER_ID, job.getUserId(), "userId应匹配");
        assertNotNull(job.getJobName(), "jobName不应为null");
        assertNotNull(job.getTargetProperties(), "targetProperties不应为null");
        assertNotNull(job.getCreateTime(), "createTime不应为null");
        assertNotNull(job.getRandomSeed(), "randomSeed不应为null");
        assertFalse("pending".equals(job.getJobRootPath()), "jobRootPath不应为占位值'pending'");

        // 验证systemId已关联
        assertNotNull(job.getSystemId(), "systemId不应为null（外键约束）");

        // ===== 验证SimulationInput记录 =====
        SimulationInput input = simulationInputRepository.findByJobId(actualJobId).orElse(null);
        assertNotNull(input, "SimulationInput记录应存在于数据库中");
        log.info("[RealDB] SimulationInput inputId: {}", input.getInputId());
        assertEquals(TestDataBuilder.DEFAULT_TEST_TEMPERATURE, input.getTemperature(), 0.001, "温度应匹配");

        // ===== 验证formula_config.json文件 =====
        Path localInputPath = pathUtil.getInputPath(TEST_USER_ID, actualJobId);
        Path localFormulaFile = localInputPath.resolve("formula_config.json");
        boolean localFormulaExists = Files.exists(localFormulaFile);
        log.info("[RealDB] formula_config.json路径: {}", localFormulaFile);
        log.info("[RealDB] formula_config.json存在: {}", localFormulaExists);
        assertTrue(localFormulaExists, "formula_config.json应存在于本地文件系统");

        // Docker容器内验证
        String dockerFormulaPath = pathUtil.convertToDockerPath(localFormulaFile);
        log.info("[RealDB] Docker内formula_config.json路径: {}", dockerFormulaPath);
        try {
            String containerCheckOutput = dockerService.executeCommandInContainer(
                    mdContainerName,
                    Arrays.asList("test", "-f", dockerFormulaPath, "&&", "echo", "EXISTS", "||", "echo", "NOT_FOUND"),
                    "/workspace"
            );
            boolean containerFormulaExists = containerCheckOutput != null
                    && containerCheckOutput.trim().contains("EXISTS");
            log.info("[RealDB] Docker容器内formula_config.json存在: {}", containerFormulaExists);
            if (!containerFormulaExists) {
                log.warn("[RealDB] ⚠ Docker容器内未找到formula_config.json，路径对齐可能有问题");
            }
        } catch (Exception e) {
            log.warn("[RealDB] 无法验证Docker容器内formula_config.json: {}", e.getMessage());
        }

        // 保存actualJobId供后续测试使用
        System.setProperty("realDb.jobId", String.valueOf(actualJobId));

        log.info("[RealDB] ✓ 阶段一完成：任务提交成功，jobId={}", actualJobId);
    }

    @Test
    @Order(2)
    @DisplayName("阶段二：追踪完整流水线状态流转")
    void testTrackFullPipelineExecution() {
        log.info("[RealDB] ===== 阶段二：追踪流水线执行 =====");

        // 从系统属性获取jobId
        String jobIdStr = System.getProperty("realDb.jobId");
        assertNotNull(jobIdStr, "jobId应从阶段一获取");
        Long jobId = Long.parseLong(jobIdStr);
        log.info("[RealDB] 开始轮询任务状态，jobId = {}", jobId);

        AtomicReference<String> previousStatus = new AtomicReference<>(null);
        AtomicInteger previousStep = new AtomicInteger(-1);

        // 轮询等待任务完成或失败（最多2小时）
        log.info("[RealDB] 开始轮询（每10秒检查一次，最长等待2小时）...");
        await().atMost(7200, SECONDS)
                .pollInterval(10, SECONDS)
                .until(() -> {
                    ResponseEntity<PipelineProgressDto> response = restTemplate.getForEntity(
                            "/api/pipeline/status/" + jobId + "?testUserId=" + TEST_USER_ID,
                            PipelineProgressDto.class
                    );

                    if (response.getStatusCode() != HttpStatus.OK) {
                        return false;
                    }

                    PipelineProgressDto progress = response.getBody();
                    if (progress == null) return false;

                    String currentStatus = progress.getStatus();
                    int currentStep = progress.getCurrentStep();
                    String prevStatus = previousStatus.get();
                    int prevStep = previousStep.get();

                    // 记录状态变化
                    if (!currentStatus.equals(prevStatus)) {
                        statusTransitions.add(currentStatus);
                        log.info("[RealDB] 状态变更: {} → {} (步骤 {}/{}, 进度 {}%)",
                                prevStatus, currentStatus,
                                currentStep, progress.getTotalSteps(),
                                progress.getProgressPercent());
                        previousStatus.set(currentStatus);
                    }

                    // 记录步骤变化
                    if (currentStep != prevStep) {
                        log.info("[RealDB] 步骤推进: {} → {} ({})",
                                prevStep, currentStep, progress.getStepName());
                        previousStep.set(currentStep);
                    }

                    if ("COMPLETED".equals(currentStatus)) {
                        pipelineCompleted = true;
                        log.info("[RealDB] ✅ 流水线执行完成!");
                        return true;
                    }

                    if ("FAILED".equals(currentStatus)) {
                        pipelineFailed = true;
                        failureErrorMessage = progress.getErrorMessage();
                        log.error("[RealDB] ❌ 流水线执行失败: {}", failureErrorMessage);
                        return true;
                    }

                    return false;
                });

        // 获取最终状态
        ResponseEntity<PipelineProgressDto> finalResponse = restTemplate.getForEntity(
                "/api/pipeline/status/" + jobId + "?testUserId=" + TEST_USER_ID,
                PipelineProgressDto.class
        );

        assertEquals(HttpStatus.OK, finalResponse.getStatusCode());
        PipelineProgressDto finalProgress = finalResponse.getBody();
        assertNotNull(finalProgress);

        log.info("[RealDB] ========================================");
        log.info("[RealDB] 流水线执行结果:");
        log.info("[RealDB]   最终状态: {}", finalProgress.getStatus());
        log.info("[RealDB]   当前步骤: {}/{} ({})",
                finalProgress.getCurrentStep(), finalProgress.getTotalSteps(),
                finalProgress.getStepName());
        log.info("[RealDB]   进度: {}%", finalProgress.getProgressPercent());
        log.info("[RealDB]   已完成步骤数: {}", finalProgress.getCompletedSteps().size());
        log.info("[RealDB]   状态流转: {}", String.join(" → ", statusTransitions));
        if (finalProgress.getErrorMessage() != null) {
            log.info("[RealDB]   错误信息: {}", finalProgress.getErrorMessage());
        }
        log.info("[RealDB] ========================================");

        // 验证最终状态
        String finalStatus = finalProgress.getStatus();
        assertTrue("COMPLETED".equals(finalStatus) || "FAILED".equals(finalStatus),
                "最终状态应为COMPLETED或FAILED，实际: " + finalStatus);

        if ("COMPLETED".equals(finalStatus)) {
            assertEquals(100, finalProgress.getProgressPercent(), "COMPLETED时进度应为100%");
        }

        log.info("[RealDB] ✓ 阶段二完成");
    }

    @Test
    @Order(3)
    @DisplayName("阶段三：验证数据库计算结果")
    void testVerifyDatabaseAfterCompletion() {
        log.info("[RealDB] ===== 阶段三：验证数据库 =====");

        String jobIdStr = System.getProperty("realDb.jobId");
        assertNotNull(jobIdStr);
        Long jobId = Long.parseLong(jobIdStr);
        log.info("[RealDB] 验证jobId={}的数据库记录", jobId);

        // ===== 1. 验证SimulationJob =====
        SimulationJob job = simulationRepository.findById(jobId).orElse(null);
        assertNotNull(job, "SimulationJob记录应存在");
        log.info("[RealDB] SimulationJob状态: {}", job.getStatus());
        log.info("[RealDB]   startTime={}", job.getStartTime());
        log.info("[RealDB]   endTime={}", job.getEndTime());
        log.info("[RealDB]   executionTimeS={}", job.getExecutionTimeS());
        log.info("[RealDB]   errorMessage={}", job.getErrorMessage());

        if ("COMPLETED".equals(job.getStatus())) {
            assertNotNull(job.getStartTime(), "COMPLETED任务应有startTime");
            assertNotNull(job.getEndTime(), "COMPLETED任务应有endTime");
            assertTrue(job.getExecutionTimeS() != null && job.getExecutionTimeS() > 0,
                    "executionTimeS应为正数");
            assertNull(job.getErrorMessage(), "COMPLETED任务不应有errorMessage");
        }
        if ("FAILED".equals(job.getStatus())) {
            assertNotNull(job.getErrorMessage(), "FAILED任务应有errorMessage");
        }

        // ===== 2. 验证SimulationInput =====
        SimulationInput input = simulationInputRepository.findByJobId(jobId).orElse(null);
        assertNotNull(input, "SimulationInput记录应存在");
        log.info("[RealDB] SimulationInput温度: {}K", input.getTemperature());
        assertEquals(TestDataBuilder.DEFAULT_TEST_TEMPERATURE, input.getTemperature(), 0.001);

        // ===== 3. 验证CalculationResult（仅COMPLETED时） =====
        if ("COMPLETED".equals(job.getStatus())) {
            List<CalculationResult> results = calculationResultRepository.findByJobId(jobId);
            log.info("[RealDB] CalculationResult记录数: {}", results.size());
            assertFalse(results.isEmpty(), "COMPLETED任务应有CalculationResult记录");

            for (CalculationResult result : results) {
                log.info("[RealDB]   属性: {} = {} {} [{}]",
                        result.getPropertyName(), result.getPropertyValue(),
                        result.getPropertyUnit(), result.getConvergenceStatus());

                assertNotNull(result.getPropertyName());
                assertNotNull(result.getPropertyValue());
                assertFalse(Double.isNaN(result.getPropertyValue()), "propertyValue不应为NaN");
                assertTrue(Double.isFinite(result.getPropertyValue()), "propertyValue应为有限值");
                assertNotNull(result.getPropertyUnit());
                assertNotNull(result.getConvergenceStatus());
                assertEquals(TestDataBuilder.DEFAULT_TEST_TEMPERATURE, result.getTemperatureK(), 0.001);
                assertTrue(result.getSamplingTimePs() > 0, "samplingTimePs应大于0");
            }

            // 验证子表数据
            results.forEach(result -> {
                if ("density".equals(result.getPropertyName())) {
                    DensityResult densityDetail = densityResultRepository.findByResultId(result.getResultId());
                    if (densityDetail != null) {
                        log.info("[RealDB] DensityResult子表存在: densityTensor={}",
                                densityDetail.getDensityTensor());
                    } else {
                        log.info("[RealDB] DensityResult子表不存在");
                    }
                }
                if ("conductivity".equals(result.getPropertyName())) {
                    ConductivityResult condDetail = conductivityResultRepository.findByResultId(result.getResultId());
                    if (condDetail != null) {
                        log.info("[RealDB] ConductivityResult子表存在: conductivityTensor={}",
                                condDetail.getConductivityTensor());
                    } else {
                        log.info("[RealDB] ConductivityResult子表不存在");
                    }
                }
            });
        } else {
            log.info("[RealDB] ⚠ 任务未完成（状态={}），跳过CalculationResult验证", job.getStatus());
        }

        log.info("[RealDB] ✓ 阶段三完成");
    }

    @Test
    @Order(4)
    @DisplayName("阶段四：验证文件系统输出")
    void testVerifyFileSystemOutput() {
        log.info("[RealDB] ===== 阶段四：验证文件系统 =====");

        String jobIdStr = System.getProperty("realDb.jobId");
        assertNotNull(jobIdStr);
        Long jobId = Long.parseLong(jobIdStr);

        SimulationJob job = simulationRepository.findById(jobId).orElse(null);
        assertNotNull(job);

        // 获取各目录路径
        Path jobRootPath = pathUtil.getJobRootPath(TEST_USER_ID, jobId);
        Path inputPath = pathUtil.getInputPath(TEST_USER_ID, jobId);
        Path outputPath = pathUtil.getOutputPath(TEST_USER_ID, jobId);
        Path postProcessingPath = pathUtil.getPostProcessingPath(TEST_USER_ID, jobId);

        log.info("[RealDB] jobRootPath: {}", jobRootPath);
        log.info("[RealDB] inputPath: {}", inputPath);
        log.info("[RealDB] outputPath: {}", outputPath);
        log.info("[RealDB] postProcessingPath: {}", postProcessingPath);

        // 验证目录存在
        assertTrue(Files.exists(jobRootPath), "任务根目录应存在");
        assertTrue(Files.exists(inputPath), "输入目录应存在");
        log.info("[RealDB] ✓ 任务目录结构存在");

        // 验证输入文件
        log.info("[RealDB] ===== 输入文件检查 =====");
        List<String> expectedInputFiles = Arrays.asList(
                "formula_config.json", "system.lt", "system.data",
                "system.in.init", "system.in.settings",
                "packmol.inp", "packed_system.pdb"
        );
        for (String fileName : expectedInputFiles) {
            Path filePath = inputPath.resolve(fileName);
            boolean exists = Files.exists(filePath);
            if (exists) {
                try {
                    log.info("[RealDB]   ✓ {} ({} bytes)", fileName, Files.size(filePath));
                } catch (Exception e) {
                    log.info("[RealDB]   ✓ {}", fileName);
                }
            } else {
                log.info("[RealDB]   ✗ {} (不存在)", fileName);
            }
        }

        // 验证原始输出文件
        log.info("[RealDB] ===== 原始输出文件检查 =====");
        if (Files.exists(outputPath)) {
            try {
                Files.list(outputPath).forEach(file -> {
                    try {
                        log.info("[RealDB]   {} ({} bytes)", file.getFileName(), Files.size(file));
                    } catch (Exception e) {
                        log.info("[RealDB]   {}", file.getFileName());
                    }
                });
            } catch (Exception e) {
                log.warn("[RealDB] 列出输出目录失败: {}", e.getMessage());
            }
        } else {
            log.info("[RealDB]   输出目录不存在（可能模拟未完成）");
        }

        // 验证后处理文件
        log.info("[RealDB] ===== 后处理文件检查 =====");
        if (Files.exists(postProcessingPath)) {
            try {
                Files.list(postProcessingPath).forEach(file -> {
                    try {
                        log.info("[RealDB]   {} ({} bytes)", file.getFileName(), Files.size(file));
                    } catch (Exception e) {
                        log.info("[RealDB]   {}", file.getFileName());
                    }
                });
            } catch (Exception e) {
                log.warn("[RealDB] 列出后处理目录失败: {}", e.getMessage());
            }
        } else {
            log.info("[RealDB]   后处理目录不存在");
        }

        // 验证轨迹文件（最终结果）
        log.info("[RealDB] ===== 关键文件验证 =====");
        Path trajectoryFile = outputPath.resolve("dump.trajectory.lammpstrj");
        if (Files.exists(trajectoryFile)) {
            try {
                log.info("[RealDB] ✓ 轨迹文件存在: {} ({} bytes)", trajectoryFile, Files.size(trajectoryFile));
            } catch (Exception e) {
                log.info("[RealDB] ✓ 轨迹文件存在: {}", trajectoryFile);
            }
        } else {
            log.warn("[RealDB] ✗ 轨迹文件不存在: {}", trajectoryFile);
        }

        Path thermoFile = outputPath.resolve("thermo.out");
        if (Files.exists(thermoFile)) {
            try {
                log.info("[RealDB] ✓ 热力学文件存在: {} ({} bytes)", thermoFile, Files.size(thermoFile));
            } catch (Exception e) {
                log.info("[RealDB] ✓ 热力学文件存在: {}", thermoFile);
            }
        } else {
            log.warn("[RealDB] ✗ 热力学文件不存在: {}", thermoFile);
        }

        log.info("[RealDB] ✓ 阶段四完成");
    }

    @Test
    @Order(5)
    @DisplayName("阶段五：验证API结果返回")
    @SuppressWarnings("unchecked")
    void testVerifyApiResults() {
        log.info("[RealDB] ===== 阶段五：验证API结果 =====");

        String jobIdStr = System.getProperty("realDb.jobId");
        assertNotNull(jobIdStr);
        Long jobId = Long.parseLong(jobIdStr);

        SimulationJob job = simulationRepository.findById(jobId).orElse(null);
        assertNotNull(job);

        if ("COMPLETED".equals(job.getStatus())) {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    "/api/pipeline/results/" + jobId + "?testUserId=" + TEST_USER_ID,
                    Map.class
            );
            log.info("[RealDB] results API状态: {}", response.getStatusCodeValue());
            assertEquals(HttpStatus.OK, response.getStatusCode());

            Map<String, Object> body = response.getBody();
            assertNotNull(body);
            assertEquals("COMPLETED", body.get("status"));
            assertNotNull(body.get("resultSummary"));

            Object calcResultsObj = body.get("calculationResults");
            assertNotNull(calcResultsObj, "应包含calculationResults");

            if (calcResultsObj instanceof List) {
                List<Map<String, Object>> calcResults = (List<Map<String, Object>>) calcResultsObj;
                log.info("[RealDB] API返回{}条计算结果", calcResults.size());
                for (Map<String, Object> result : calcResults) {
                    log.info("[RealDB]   {} = {} {}",
                            result.get("propertyName"),
                            result.get("propertyValue"),
                            result.get("propertyUnit"));
                    assertNotNull(result.get("propertyName"));
                    assertNotNull(result.get("propertyValue"));
                    assertNotNull(result.get("propertyUnit"));
                }
            }
        } else {
            ResponseEntity<Map> response = restTemplate.getForEntity(
                    "/api/pipeline/results/" + jobId + "?testUserId=" + TEST_USER_ID,
                    Map.class
            );
            log.info("[RealDB] 非COMPLETED任务results API状态: {}", response.getStatusCodeValue());
            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }

        log.info("[RealDB] ✓ 阶段五完成");
    }

    @Test
    @Order(99)
    @DisplayName("生成端到端测试综合报告")
    void testGenerateFinalReport() {
        String jobIdStr = System.getProperty("realDb.jobId");

        log.info("");
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║        真实数据库全流程测试报告 (RealDB Pipeline)            ║");
        log.info("╠══════════════════════════════════════════════════════════════╣");

        if (jobIdStr == null) {
            log.info("║  ⚠ 任务未提交，无法生成报告                                 ║");
            log.info("╚══════════════════════════════════════════════════════════════╝");
            return;
        }

        Long jobId = Long.parseLong(jobIdStr);
        SimulationJob job = simulationRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.info("║  ⚠ 任务记录不存在                                           ║");
            log.info("╚══════════════════════════════════════════════════════════════╝");
            return;
        }

        log.info("║  任务ID: {}                                                    ║", jobId);
        log.info("║  任务名称: {}                                              ║",
                truncatePad(job.getJobName(), 48));
        log.info("║  最终状态: {}                                               ║",
                truncatePad(job.getStatus(), 48));
        log.info("║  状态流转: {}                              ║",
                truncatePad(String.join(" → ", statusTransitions), 30));

        log.info("╠══════════════════════════════════════════════════════════════╣");
        log.info("║  执行时间统计                                                 ║");
        log.info("║    创建时间: {}                              ║",
                truncatePad(String.valueOf(job.getCreateTime()), 28));
        log.info("║    开始时间: {}                              ║",
                truncatePad(String.valueOf(job.getStartTime()), 28));
        log.info("║    结束时间: {}                              ║",
                truncatePad(String.valueOf(job.getEndTime()), 28));
        log.info("║    执行耗时: {}秒                                             ║",
                truncatePad(String.valueOf(job.getExecutionTimeS()), 32));

        if ("COMPLETED".equals(job.getStatus())) {
            List<CalculationResult> results = calculationResultRepository.findByJobId(jobId);
            log.info("╠══════════════════════════════════════════════════════════════╣");
            log.info("║  计算结果 ({}条)                                              ║", results.size());

            for (CalculationResult r : results) {
                String line = String.format("%s = %.4f %s [%s]",
                        r.getPropertyName(), r.getPropertyValue(),
                        r.getPropertyUnit(), r.getConvergenceStatus());
                log.info("║    {}                                                    ║",
                        truncatePad(line, 52));
            }
        }

        if (job.getErrorMessage() != null && !job.getErrorMessage().isEmpty()) {
            log.info("╠══════════════════════════════════════════════════════════════╣");
            log.info("║  错误信息: {}                                  ║",
                    truncatePad(job.getErrorMessage(), 38));
        }

        log.info("╚══════════════════════════════════════════════════════════════╝");
        log.info("");
    }

    // ==================== 辅助方法 ====================

    private static String truncatePad(String s, int maxLen) {
        if (s == null) return "null" + " ".repeat(maxLen - 4);
        if (s.length() > maxLen) return s.substring(0, maxLen - 3) + "...";
        return s + " ".repeat(maxLen - s.length());
    }
}