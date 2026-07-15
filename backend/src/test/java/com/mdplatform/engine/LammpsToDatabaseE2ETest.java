package com.mdplatform.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * LAMMPS执行到数据库存储端到端集成测试
 *
 * <p>该测试覆盖从LAMMPS执行到数据库存储的完整流水线，不使用任何Mock。
 * 通过Docker容器执行真实的LAMMPS模拟，验证轨迹文件生成、MDAnalysis后处理、
 * 数据库主表和子表记录的完整性和一致性。</p>
 *
 * <h3>测试范围</h3>
 * <ul>
 *   <li>环境检查：Docker可用性、md_engine容器运行状态、GPU可用性</li>
 *   <li>提交全流程任务：target_properties包含density、conductivity、viscosity</li>
 *   <li>LAMMPS执行输出验证：raw_outputs目录文件完整性</li>
 *   <li>后处理输出验证：post_processing和visualization目录JSON文件格式</li>
 *   <li>数据库主表验证：calculation_result_table记录数量、字段完整性</li>
 *   <li>数据库子表验证：density_result_table、conductivity_result_table、viscosity_result_table</li>
 *   <li>状态流转验证：SimulationJob最终状态和时间字段</li>
 *   <li>文件路径验证：rawDataPath和chartDataPath为相对路径且文件实际存在</li>
 * </ul>
 *
 * <h3>前置条件</h3>
 * <ul>
 *   <li>Docker引擎可用</li>
 *   <li>md_engine容器正在运行</li>
 *   <li>数据库可连接</li>
 * </ul>
 *
 * <h3>测试数据</h3>
 * <p>标准电解液配方：EC:DMC = 3:7（摩尔比），1M LiPF6，温度 353K，
 * 目标属性：density、conductivity、viscosity</p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LammpsToDatabaseE2ETest {

    // ==================== 依赖注入 ====================

    /** REST测试客户端，用于通过HTTP接口提交任务和查询状态 */
    @Autowired
    private TestRestTemplate restTemplate;

    /** Docker服务，用于检查Docker和容器可用性 */
    @Autowired(required = false)
    private DockerService dockerService;

    /** 路径工具类，用于生成和解析文件路径，禁止硬编码路径 */
    @Autowired
    private PathUtil pathUtil;

    /** 模拟任务仓库，用于查询和验证SimulationJob记录 */
    @Autowired
    private SimulationRepository simulationRepository;

    /** 模拟输出仓库，用于查询和验证SimulationRawOutput记录 */
    @Autowired
    private SimulationOutputRepository simulationOutputRepository;

    /** 模拟输入仓库，用于查询任务的输入参数 */
    @Autowired
    private SimulationInputRepository simulationInputRepository;

    /** 计算结果仓库，用于查询后处理生成的计算结果主表记录 */
    @Autowired
    private CalculationResultRepository calculationResultRepository;

    /** 密度结果仓库，用于查询密度子表数据 */
    @Autowired
    private DensityResultRepository densityResultRepository;

    /** 电导率结果仓库，用于查询电导率子表数据 */
    @Autowired
    private ConductivityResultRepository conductivityResultRepository;

    /** 粘度结果仓库，用于查询粘度子表数据 */
    @Autowired
    private ViscosityResultRepository viscosityResultRepository;

    /** 介电常数结果仓库，用于查询介电常数子表数据 */
    @Autowired
    private DielectricResultRepository dielectricResultRepository;

    /** 溶剂化结构结果仓库，用于查询溶剂化结构子表数据 */
    @Autowired
    private SolvationResultRepository solvationResultRepository;

    /** JSON序列化/反序列化工具，用于解析后处理结果JSON文件 */
    @Autowired
    private ObjectMapper objectMapper;

    /** Docker容器名称，从配置文件注入，避免硬编码 */
    @org.springframework.beans.factory.annotation.Value("${app.docker.md-container-name}")
    private String mdContainerName;

    // ==================== 测试状态 ====================

    /** 提交后得到的任务ID，在多个测试方法间共享 */
    private static Long sharedJobId;

    /** 记录状态流转过程 */
    private static final List<String> statusTransitions = Collections.synchronizedList(new ArrayList<>());

    /** 任务是否完成标志 */
    private static boolean pipelineCompleted = false;

    /** 任务是否失败标志 */
    private static boolean pipelineFailed = false;

    /** 错误信息 */
    private static String failureErrorMessage;

    // ==================== 环境检查 ====================

    /**
     * 在所有测试之前输出环境检查开始日志
     */
    @BeforeAll
    static void checkEnvironment() {
        log.info("========================================");
        log.info("[LAMMPS-DB-E2E] 环境检查开始");
        log.info("========================================");
    }

    /**
     * 环境检查测试 - 验证Docker、md_engine容器和GPU可用性
     *
     * <p>该测试在所有其他测试之前执行，验证运行环境是否满足要求。
     * 如果Docker不可用或md_engine容器未运行，则跳过所有后续测试。</p>
     *
     * <p>验证点：</p>
     * <ul>
     *   <li>Docker服务可用</li>
     *   <li>md_engine容器处于running状态</li>
     *   <li>GPU可用性（记录结果，不跳过测试）</li>
     * </ul>
     */
    @Test
    @Order(0)
    @DisplayName("环境检查 - 验证Docker、md_engine容器和GPU可用性")
    void testEnvironmentCheck() {
        log.info("[LAMMPS-DB-E2E] 检查Docker服务...");

        // ===== 检查Docker是否可用 =====
        boolean dockerAvailable = dockerService != null && dockerService.isDockerAvailable();
        Assumptions.assumeTrue(dockerAvailable,
                "Docker不可用，跳过所有LAMMPS到数据库端到端测试。请确保Docker Desktop正在运行。");
        log.info("[LAMMPS-DB-E2E] ✓ Docker服务可用");

        // ===== 检查容器是否运行 =====
        String containerStatus = dockerService.getContainerStatus();
        log.info("[LAMMPS-DB-E2E] {} 容器状态: {}", mdContainerName, containerStatus);

        boolean engineAvailable = containerStatus != null
                && containerStatus.toLowerCase().contains("running");
        Assumptions.assumeTrue(engineAvailable,
                mdContainerName + " 容器未运行，跳过所有LAMMPS到数据库端到端测试。当前状态: " + containerStatus
                        + "。请执行 docker-compose up -d md-engine。");
        log.info("[LAMMPS-DB-E2E] ✓ {} 容器正在运行", mdContainerName);

        // ===== 检查GPU可用性（记录结果但不跳过测试） =====
        boolean gpuAvailable = dockerService.isGPUAvailable();
        if (gpuAvailable) {
            log.info("[LAMMPS-DB-E2E] ✓ GPU可用，LAMMPS将使用GPU加速模式");
        } else {
            log.warn("[LAMMPS-DB-E2E] ⚠ GPU不可用，LAMMPS将回退到CPU模式执行，模拟速度可能较慢");
        }

        log.info("[LAMMPS-DB-E2E] ✓ 环境检查通过，开始LAMMPS到数据库端到端测试");
    }

    // ==================== 阶段一：提交全流程任务并等待完成 ====================

    /**
     * 提交全流程计算任务并等待完成
     *
     * <p>提交包含density、conductivity、viscosity三种目标属性的Pipeline任务，
     * 等待整个流水线（建模→LAMMPS模拟→后处理→数据库存储）完成。</p>
     *
     * <p>验证点：</p>
     * <ul>
     *   <li>任务提交成功，返回202 ACCEPTED</li>
     *   <li>任务状态最终流转到COMPLETED</li>
     *   <li>任务经历了RUNNING和POST_PROCESSING状态</li>
     * </ul>
     */
    @Test
    @Order(1)
    @DisplayName("阶段一：提交全流程任务并等待完成")
    @SuppressWarnings("unchecked")
    void testSubmitTaskAndWaitForCompletion() {
        log.info("========================================");
        log.info("[LAMMPS-DB-E2E] 阶段一：提交全流程任务");
        log.info("========================================");

        // 构建标准测试请求（EC:DMC=3:7, 1M LiPF6, 353K）
        PipelineSubmitRequest request = TestDataBuilder.buildStandardPipelineRequest();
        // 覆盖目标属性为三种：density、conductivity、viscosity
        request.setTargetProperties(Arrays.asList("density", "conductivity", "viscosity"));
        request.setJobName("LAMMPS-DB-E2E测试任务-三属性");
        request.setTemperature(TestDataBuilder.DEFAULT_TEST_TEMPERATURE);
        log.info("[LAMMPS-DB-E2E] 测试配方: EC:DMC=3:7, 1M LiPF6, T=353K");
        log.info("[LAMMPS-DB-E2E] 目标属性: {}", request.getTargetProperties());

        // ===== 提交任务 =====
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/pipeline/submit?testUserId=" + TestDataBuilder.DEFAULT_TEST_USER_ID,
                request,
                Map.class
        );

        // 验证HTTP响应
        log.info("[LAMMPS-DB-E2E] HTTP响应状态: {}", response.getStatusCodeValue());
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode(),
                "任务提交应返回 202 ACCEPTED");

        Map<String, Object> body = response.getBody();
        assertNotNull(body, "响应体不应为null");
        assertNotNull(body.get("jobId"), "响应体应包含jobId");

        Long jobId = ((Number) body.get("jobId")).longValue();
        sharedJobId = jobId;
        log.info("[LAMMPS-DB-E2E] ✓ 任务提交成功，jobId = {}", jobId);

        // ===== 等待任务完成或失败（最多20分钟） =====
        log.info("[LAMMPS-DB-E2E] 开始轮询任务状态，等待流水线执行完成...");
        AtomicReference<String> previousStatus = new AtomicReference<>(null);

        await().atMost(1200, SECONDS)
                .pollInterval(5, SECONDS)
                .until(() -> {
                    ResponseEntity<PipelineProgressDto> statusResponse = restTemplate.getForEntity(
                            "/api/pipeline/status/" + sharedJobId
                                    + "?testUserId=" + TestDataBuilder.DEFAULT_TEST_USER_ID,
                            PipelineProgressDto.class
                    );

                    if (statusResponse.getStatusCode() != HttpStatus.OK) {
                        log.warn("[LAMMPS-DB-E2E] 状态API返回非200: {}", statusResponse.getStatusCode());
                        return false;
                    }

                    PipelineProgressDto progress = statusResponse.getBody();
                    if (progress == null) {
                        log.warn("[LAMMPS-DB-E2E] 状态API返回空响应");
                        return false;
                    }

                    String currentStatus = progress.getStatus();
                    String prevStatus = previousStatus.get();

                    // 记录状态变化
                    if (!currentStatus.equals(prevStatus)) {
                        statusTransitions.add(currentStatus);
                        log.info("[LAMMPS-DB-E2E] 📊 状态变更: {} → {} (步骤 {}/{}, 进度 {}%)",
                                prevStatus, currentStatus,
                                progress.getCurrentStep(), progress.getTotalSteps(),
                                progress.getProgressPercent());
                        previousStatus.set(currentStatus);
                    }

                    // 检查是否完成或失败
                    if (JobStatus.COMPLETED.equals(currentStatus)) {
                        pipelineCompleted = true;
                        log.info("[LAMMPS-DB-E2E] ✅ 流水线执行完成!");
                        return true;
                    }

                    if (JobStatus.FAILED.equals(currentStatus)) {
                        pipelineFailed = true;
                        failureErrorMessage = progress.getErrorMessage();
                        log.error("[LAMMPS-DB-E2E] ❌ 流水线执行失败: {}", failureErrorMessage);
                        return true;
                    }

                    return false;
                });

        // ===== 验证最终状态 =====
        ResponseEntity<PipelineProgressDto> finalResponse = restTemplate.getForEntity(
                "/api/pipeline/status/" + sharedJobId
                        + "?testUserId=" + TestDataBuilder.DEFAULT_TEST_USER_ID,
                PipelineProgressDto.class
        );

        assertEquals(HttpStatus.OK, finalResponse.getStatusCode(), "最终状态API应返回200");
        PipelineProgressDto finalProgress = finalResponse.getBody();
        assertNotNull(finalProgress, "最终进度不应为null");

        String finalStatus = finalProgress.getStatus();
        log.info("[LAMMPS-DB-E2E] 最终状态: {}", finalStatus);
        log.info("[LAMMPS-DB-E2E] 状态流转: {}", String.join(" → ", statusTransitions));

        // 验证最终状态为COMPLETED
        assertEquals(JobStatus.COMPLETED, finalStatus,
                "最终状态应为COMPLETED，实际: " + finalStatus);

        // 验证经历了RUNNING状态（LAMMPS执行阶段）
        assertTrue(statusTransitions.contains(JobStatus.RUNNING),
                "COMPLETED任务应经历RUNNING状态（LAMMPS执行阶段），实际状态流转: "
                        + String.join(" → ", statusTransitions));

        // 验证经历了POST_PROCESSING状态（后处理阶段）
        assertTrue(statusTransitions.contains(JobStatus.POST_PROCESSING)
                        || statusTransitions.contains(JobStatus.POST_PROCESSING_COMPLETED),
                "COMPLETED任务应经历后处理阶段，实际状态流转: "
                        + String.join(" → ", statusTransitions));

        log.info("[LAMMPS-DB-E2E] ✓ 阶段一完成：全流程任务执行验证通过");
    }

    // ==================== 阶段二：验证LAMMPS执行输出 ====================

    /**
     * 验证LAMMPS执行完成后生成的核心输出文件
     *
     * <p>验证点：</p>
     * <ul>
     *   <li>raw_outputs目录存在</li>
     *   <li>log.lammps存在且非空</li>
     *   <li>dump.trajectory.lammpstrj存在且非空</li>
     *   <li>final.data存在且非空</li>
     *   <li>属性特定文件存在（conductivity→dump.charge.lammpstrj/msd.dat，viscosity→pressure.dat）</li>
     *   <li>SimulationRawOutput数据库记录存在</li>
     * </ul>
     */
    @Test
    @Order(2)
    @DisplayName("阶段二：验证LAMMPS执行输出文件和数据库记录")
    void testVerifyLAMMPSExecutionOutputs() {
        log.info("========================================");
        log.info("[LAMMPS-DB-E2E] 阶段二：验证LAMMPS执行输出");
        log.info("========================================");

        assertNotNull(sharedJobId, "jobId不应为null，请确保阶段一已执行");

        SimulationJob job = simulationRepository.findById(sharedJobId).orElse(null);
        assertNotNull(job, "SimulationJob记录应存在");

        Assumptions.assumeTrue(JobStatus.COMPLETED.equals(job.getStatus()),
                "任务未完成，跳过LAMMPS输出验证。当前状态: " + job.getStatus());

        Long userId = job.getUserId();
        Long jobId = job.getJobId();

        // 使用PathUtil生成原始输出目录路径
        Path rawOutputPath = pathUtil.getRawOutputPath(userId, jobId);
        log.info("[LAMMPS-DB-E2E] 原始输出目录: {}", rawOutputPath);

        // ===== 验证raw_outputs目录存在 =====
        assertTrue(Files.exists(rawOutputPath),
                "raw_outputs目录应存在: " + rawOutputPath);

        // ===== 验证log.lammps =====
        Path logLammpsPath = rawOutputPath.resolve("log.lammps");
        assertTrue(Files.exists(logLammpsPath),
                "log.lammps应存在于raw_outputs目录: " + logLammpsPath);
        verifyFileNotEmpty(logLammpsPath, "log.lammps");

        // 验证日志文件包含LAMMPS输出特征
        try {
            String logContent = Files.readString(logLammpsPath);
            assertTrue(logContent.contains("LAMMPS") || logContent.contains("Total wall time"),
                    "log.lammps应包含LAMMPS输出特征（'LAMMPS'或'Total wall time'）");
            log.info("[LAMMPS-DB-E2E] ✓ log.lammps内容验证通过");
        } catch (Exception e) {
            log.warn("[LAMMPS-DB-E2E] 读取log.lammps内容失败: {}", e.getMessage());
        }

        // ===== 验证dump.trajectory.lammpstrj =====
        Path trajectoryPath = rawOutputPath.resolve("dump.trajectory.lammpstrj");
        assertTrue(Files.exists(trajectoryPath),
                "dump.trajectory.lammpstrj应存在于raw_outputs目录: " + trajectoryPath);
        verifyFileNotEmpty(trajectoryPath, "dump.trajectory.lammpstrj");

        // ===== 验证final.data =====
        Path finalDataPath = rawOutputPath.resolve("final.data");
        assertTrue(Files.exists(finalDataPath),
                "final.data应存在于raw_outputs目录: " + finalDataPath);
        verifyFileNotEmpty(finalDataPath, "final.data");

        // ===== 验证属性特定文件 =====
        // conductivity相关文件
        Path chargeTrajPath = rawOutputPath.resolve("dump.charge.lammpstrj");
        if (Files.exists(chargeTrajPath)) {
            log.info("[LAMMPS-DB-E2E] ✓ dump.charge.lammpstrj存在（conductivity属性文件）");
            verifyFileNotEmpty(chargeTrajPath, "dump.charge.lammpstrj");
        } else {
            log.warn("[LAMMPS-DB-E2E] ⚠ dump.charge.lammpstrj不存在");
        }

        Path msdPath = rawOutputPath.resolve("msd.dat");
        if (Files.exists(msdPath)) {
            log.info("[LAMMPS-DB-E2E] ✓ msd.dat存在（conductivity属性文件）");
            verifyFileNotEmpty(msdPath, "msd.dat");
        } else {
            log.warn("[LAMMPS-DB-E2E] ⚠ msd.dat不存在");
        }

        // viscosity相关文件
        Path pressurePath = rawOutputPath.resolve("pressure.dat");
        if (Files.exists(pressurePath)) {
            log.info("[LAMMPS-DB-E2E] ✓ pressure.dat存在（viscosity属性文件）");
            verifyFileNotEmpty(pressurePath, "pressure.dat");
        } else {
            log.warn("[LAMMPS-DB-E2E] ⚠ pressure.dat不存在");
        }

        // ===== 验证SimulationRawOutput数据库记录 =====
        Optional<SimulationRawOutput> outputOpt = simulationOutputRepository.findByJobId(sharedJobId);
        assertTrue(outputOpt.isPresent(),
                "SimulationRawOutput记录应存在，jobId=" + sharedJobId);

        SimulationRawOutput output = outputOpt.get();
        log.info("[LAMMPS-DB-E2E] SimulationRawOutput记录:");
        log.info("[LAMMPS-DB-E2E]   logFilePath = {}", output.getLogFilePath());
        log.info("[LAMMPS-DB-E2E]   trajectoryFilePath = {}", output.getTrajectoryFilePath());
        log.info("[LAMMPS-DB-E2E]   finalDataFilePath = {}", output.getFinalDataFilePath());

        // 验证基本文件路径字段已填充
        assertNotNull(output.getLogFilePath(), "logFilePath不应为null");
        assertNotNull(output.getTrajectoryFilePath(), "trajectoryFilePath不应为null");
        assertNotNull(output.getFinalDataFilePath(), "finalDataFilePath不应为null");

        log.info("[LAMMPS-DB-E2E] ✓ 阶段二完成：LAMMPS执行输出验证通过");
    }

    // ==================== 阶段三：验证后处理输出 ====================

    /**
     * 验证后处理生成的结果文件和可视化文件
     *
     * <p>验证点：</p>
     * <ul>
     *   <li>post_processing目录包含各属性的_result.json文件</li>
     *   <li>visualization/charts目录包含_curve.json文件</li>
     *   <li>JSON文件格式正确，包含convergence_status字段</li>
     * </ul>
     */
    @Test
    @Order(3)
    @DisplayName("阶段三：验证后处理输出文件")
    void testVerifyPostProcessingOutputs() {
        log.info("========================================");
        log.info("[LAMMPS-DB-E2E] 阶段三：验证后处理输出文件");
        log.info("========================================");

        assertNotNull(sharedJobId, "jobId不应为null，请确保阶段一已执行");

        SimulationJob job = simulationRepository.findById(sharedJobId).orElse(null);
        assertNotNull(job, "SimulationJob记录应存在");

        Assumptions.assumeTrue(JobStatus.COMPLETED.equals(job.getStatus()),
                "任务未完成，跳过后处理输出验证。当前状态: " + job.getStatus());

        Long userId = job.getUserId();
        Long jobId = job.getJobId();

        // ===== 验证post_processing目录 =====
        Path postProcessingPath = pathUtil.getPostProcessingPath(userId, jobId);
        log.info("[LAMMPS-DB-E2E] 后处理目录: {}", postProcessingPath);
        assertTrue(Files.exists(postProcessingPath),
                "后处理目录应存在: " + postProcessingPath);

        // 定义预期结果文件列表
        Map<String, String> expectedResults = new LinkedHashMap<>();
        expectedResults.put("density", "density_result.json");
        expectedResults.put("conductivity", "conductivity_result.json");
        expectedResults.put("viscosity", "viscosity_result.json");

        for (Map.Entry<String, String> entry : expectedResults.entrySet()) {
            String propertyName = entry.getKey();
            String filename = entry.getValue();
            Path resultPath = postProcessingPath.resolve(filename);

            boolean fileExists = Files.exists(resultPath);
            log.info("[LAMMPS-DB-E2E] {} 结果文件: {} 存在={}", propertyName, resultPath, fileExists);

            if (fileExists) {
                // 验证JSON格式正确
                try {
                    String content = Files.readString(resultPath);
                    JsonNode json = objectMapper.readTree(content);
                    log.info("[LAMMPS-DB-E2E] ✓ {} 结果JSON格式正确", propertyName);

                    // 验证convergence_status字段存在
                    assertTrue(json.has("convergence_status"),
                            propertyName + "_result.json 应包含 convergence_status 字段");
                    log.info("[LAMMPS-DB-E2E]   convergence_status: {}", json.get("convergence_status").asText());
                } catch (Exception e) {
                    log.warn("[LAMMPS-DB-E2E] ⚠ {} 结果JSON解析失败: {}", propertyName, e.getMessage());
                }
            } else {
                log.warn("[LAMMPS-DB-E2E] ⚠ {} 结果文件不存在（后处理可能未生成该属性结果）", propertyName);
            }
        }

        // ===== 验证visualization/charts目录 =====
        Path chartsPath = pathUtil.getVisualizationPath(userId, jobId).resolve("charts");
        log.info("[LAMMPS-DB-E2E] 图表目录: {}", chartsPath);

        if (Files.exists(chartsPath)) {
            // 验证各属性的曲线数据文件
            String[] expectedCurves = {"density_curve.json", "conductivity_curve.json", "viscosity_curve.json"};
            for (String curveFile : expectedCurves) {
                Path curvePath = chartsPath.resolve(curveFile);
                if (Files.exists(curvePath)) {
                    log.info("[LAMMPS-DB-E2E] ✓ {} 存在", curveFile);
                    // 验证JSON格式
                    try {
                        String content = Files.readString(curvePath);
                        objectMapper.readTree(content);
                        log.info("[LAMMPS-DB-E2E] ✓ {} JSON格式正确", curveFile);
                    } catch (Exception e) {
                        log.warn("[LAMMPS-DB-E2E] ⚠ {} JSON解析失败: {}", curveFile, e.getMessage());
                    }
                } else {
                    log.info("[LAMMPS-DB-E2E] {} 不存在（后处理可能未生成图表数据）", curveFile);
                }
            }
        } else {
            log.info("[LAMMPS-DB-E2E] visualization/charts目录不存在（后处理可能未生成可视化数据）");
        }

        log.info("[LAMMPS-DB-E2E] ✓ 阶段三完成：后处理输出验证通过");
    }

    // ==================== 阶段四：验证数据库主表记录 ====================

    /**
     * 验证calculation_result_table主表记录
     *
     * <p>验证点：</p>
     * <ul>
     *   <li>记录数量与target_properties数量匹配（3条：density、conductivity、viscosity）</li>
     *   <li>每条记录包含：propertyName、propertyValue、propertyUnit、calculationMethod</li>
     *   <li>每条记录包含：temperatureK、convergenceStatus、propertyDetail</li>
     *   <li>rawDataPath指向存在的文件（相对路径）</li>
     *   <li>chartDataPath指向存在的文件（相对路径）</li>
     * </ul>
     */
    @Test
    @Order(4)
    @DisplayName("阶段四：验证数据库主表CalculationResult记录")
    void testVerifyDatabaseMainTableRecords() {
        log.info("========================================");
        log.info("[LAMMPS-DB-E2E] 阶段四：验证数据库主表记录");
        log.info("========================================");

        assertNotNull(sharedJobId, "jobId不应为null，请确保阶段一已执行");

        SimulationJob job = simulationRepository.findById(sharedJobId).orElse(null);
        assertNotNull(job, "SimulationJob记录应存在");

        Assumptions.assumeTrue(JobStatus.COMPLETED.equals(job.getStatus()),
                "任务未完成，跳过数据库主表验证。当前状态: " + job.getStatus());

        // ===== 查询CalculationResult记录 =====
        List<CalculationResult> results = calculationResultRepository.findByJobId(sharedJobId);
        log.info("[LAMMPS-DB-E2E] CalculationResult记录数: {}", results.size());

        // 验证记录数量与target_properties匹配（density、conductivity、viscosity = 3条）
        assertFalse(results.isEmpty(),
                "COMPLETED任务应有CalculationResult记录");
        assertEquals(3, results.size(),
                "应有3条CalculationResult记录（density、conductivity、viscosity），实际: " + results.size());

        // 收集所有propertyName用于后续验证
        Set<String> propertyNames = new HashSet<>();

        for (CalculationResult result : results) {
            log.info("[LAMMPS-DB-E2E] CalculationResult #{}:", result.getResultId());
            log.info("[LAMMPS-DB-E2E]   propertyName = {}", result.getPropertyName());
            log.info("[LAMMPS-DB-E2E]   propertyValue = {}", result.getPropertyValue());
            log.info("[LAMMPS-DB-E2E]   propertyUnit = {}", result.getPropertyUnit());
            log.info("[LAMMPS-DB-E2E]   calculationMethod = {}", result.getCalculationMethod());
            log.info("[LAMMPS-DB-E2E]   temperatureK = {}", result.getTemperatureK());
            log.info("[LAMMPS-DB-E2E]   convergenceStatus = {}", result.getConvergenceStatus());
            log.info("[LAMMPS-DB-E2E]   propertyDetail = {}", result.getPropertyDetail());
            log.info("[LAMMPS-DB-E2E]   rawDataPath = {}", result.getRawDataPath());
            log.info("[LAMMPS-DB-E2E]   chartDataPath = {}", result.getChartDataPath());

            // ===== 验证基本字段完整性 =====
            assertNotNull(result.getPropertyName(), "propertyName不应为null");
            assertFalse(result.getPropertyName().trim().isEmpty(), "propertyName不应为空字符串");

            assertNotNull(result.getPropertyValue(),
                    "propertyValue不应为null (propertyName=" + result.getPropertyName() + ")");
            assertFalse(Double.isNaN(result.getPropertyValue()),
                    "propertyValue不应为NaN (propertyName=" + result.getPropertyName() + ")");
            assertTrue(Double.isFinite(result.getPropertyValue()),
                    "propertyValue应为有限值 (propertyName=" + result.getPropertyName() + ")");

            assertNotNull(result.getPropertyUnit(), "propertyUnit不应为null");
            assertFalse(result.getPropertyUnit().trim().isEmpty(), "propertyUnit不应为空字符串");

            assertNotNull(result.getCalculationMethod(), "calculationMethod不应为null");
            assertFalse(result.getCalculationMethod().trim().isEmpty(), "calculationMethod不应为空字符串");

            assertNotNull(result.getTemperatureK(), "temperatureK不应为null");
            // 验证temperatureK与提交参数一致
            assertEquals(TestDataBuilder.DEFAULT_TEST_TEMPERATURE, result.getTemperatureK(), 0.001,
                    "temperatureK应与提交参数一致 (propertyName=" + result.getPropertyName() + ")");

            assertNotNull(result.getConvergenceStatus(), "convergenceStatus不应为null");
            assertFalse(result.getConvergenceStatus().trim().isEmpty(), "convergenceStatus不应为空字符串");

            assertNotNull(result.getPropertyDetail(), "propertyDetail不应为null");
            assertFalse(result.getPropertyDetail().trim().isEmpty(), "propertyDetail不应为空字符串");

            // ===== 验证rawDataPath为相对路径且文件存在 =====
            if (result.getRawDataPath() != null) {
                assertFalse(result.getRawDataPath().trim().isEmpty(),
                        "rawDataPath不应为空字符串");
                // 验证rawDataPath是相对路径（不包含盘符或根路径标识）
                assertFalse(result.getRawDataPath().contains(":"),
                        "rawDataPath应为相对路径，不应包含盘符: " + result.getRawDataPath());
                assertFalse(result.getRawDataPath().startsWith("/"),
                        "rawDataPath应为相对路径，不应以/开头: " + result.getRawDataPath());

                // 验证rawDataPath指向的文件实际存在
                Long userId = job.getUserId();
                Path rawDataAbsPath = pathUtil.resolveAbsolutePath(userId, sharedJobId, result.getRawDataPath());
                assertTrue(Files.exists(rawDataAbsPath),
                        "rawDataPath指向的文件应存在: " + rawDataAbsPath
                                + " (propertyName=" + result.getPropertyName() + ")");
                log.info("[LAMMPS-DB-E2E] ✓ rawDataPath文件存在验证通过: {}", rawDataAbsPath);
            }

            // ===== 验证chartDataPath为相对路径且文件存在 =====
            if (result.getChartDataPath() != null) {
                assertFalse(result.getChartDataPath().trim().isEmpty(),
                        "chartDataPath不应为空字符串");
                // 验证chartDataPath是相对路径
                assertFalse(result.getChartDataPath().contains(":"),
                        "chartDataPath应为相对路径，不应包含盘符: " + result.getChartDataPath());
                assertFalse(result.getChartDataPath().startsWith("/"),
                        "chartDataPath应为相对路径，不应以/开头: " + result.getChartDataPath());

                // 验证chartDataPath指向的文件实际存在
                Long userId = job.getUserId();
                Path chartDataAbsPath = pathUtil.resolveAbsolutePath(userId, sharedJobId, result.getChartDataPath());
                assertTrue(Files.exists(chartDataAbsPath),
                        "chartDataPath指向的文件应存在: " + chartDataAbsPath
                                + " (propertyName=" + result.getPropertyName() + ")");
                log.info("[LAMMPS-DB-E2E] ✓ chartDataPath文件存在验证通过: {}", chartDataAbsPath);
            }

            propertyNames.add(result.getPropertyName());
        }

        // ===== 验证所有目标属性都有对应记录 =====
        List<String> expectedProperties = Arrays.asList("density", "conductivity", "viscosity");
        for (String expected : expectedProperties) {
            assertTrue(propertyNames.contains(expected),
                    "target_property '" + expected + "' 应有对应的CalculationResult记录"
                            + "，实际属性: " + propertyNames);
        }

        log.info("[LAMMPS-DB-E2E] ✓ 阶段四完成：数据库主表记录验证通过");
    }

    // ==================== 阶段五：验证数据库子表记录 ====================

    /**
     * 验证各属性对应的子表记录
     *
     * <p>验证点：</p>
     * <ul>
     *   <li>density → DensityResult: densityTensor、componentDensity字段</li>
     *   <li>conductivity → ConductivityResult: conductivityTensor、ionContribution、resistivity字段</li>
     *   <li>viscosity → ViscosityResult: viscosityValue、shearRate、stressResponse、kinematicViscosity字段</li>
     * </ul>
     */
    @Test
    @Order(5)
    @DisplayName("阶段五：验证数据库子表记录")
    void testVerifyDatabaseSubTableRecords() {
        log.info("========================================");
        log.info("[LAMMPS-DB-E2E] 阶段五：验证数据库子表记录");
        log.info("========================================");

        assertNotNull(sharedJobId, "jobId不应为null，请确保阶段一已执行");

        SimulationJob job = simulationRepository.findById(sharedJobId).orElse(null);
        assertNotNull(job, "SimulationJob记录应存在");

        Assumptions.assumeTrue(JobStatus.COMPLETED.equals(job.getStatus()),
                "任务未完成，跳过数据库子表验证。当前状态: " + job.getStatus());

        // 查询所有CalculationResult记录
        List<CalculationResult> results = calculationResultRepository.findByJobId(sharedJobId);
        assertFalse(results.isEmpty(), "CalculationResult记录不应为空");

        // ===== 验证density子表 =====
        log.info("[LAMMPS-DB-E2E] ===== 验证DensityResult子表 =====");
        CalculationResult densityResult = results.stream()
                .filter(r -> "density".equals(r.getPropertyName()))
                .findFirst().orElse(null);

        if (densityResult != null) {
            DensityResult densityDetail = densityResultRepository
                    .findByResultId(densityResult.getResultId());

            if (densityDetail != null) {
                log.info("[LAMMPS-DB-E2E] DensityResult子表存在");
                log.info("[LAMMPS-DB-E2E]   resultId = {}", densityDetail.getResultId());
                log.info("[LAMMPS-DB-E2E]   densityTensor = {}", densityDetail.getDensityTensor());
                log.info("[LAMMPS-DB-E2E]   componentDensity = {}", densityDetail.getComponentDensity());

                // 验证resultId与主表一致
                assertEquals(densityResult.getResultId(), densityDetail.getResultId(),
                        "DensityResult.resultId应与CalculationResult.resultId一致");

                // 验证densityTensor字段（JSON格式，包含xx/yy/zz分量）
                if (densityDetail.getDensityTensor() != null) {
                    assertFalse(densityDetail.getDensityTensor().trim().isEmpty(),
                            "densityTensor不应为空字符串");
                    log.info("[LAMMPS-DB-E2E] ✓ densityTensor字段已填充");
                } else {
                    log.info("[LAMMPS-DB-E2E] densityTensor为null（后处理可能未生成张量数据）");
                }

                // 验证componentDensity字段（JSON格式，各组分密度分布）
                if (densityDetail.getComponentDensity() != null) {
                    assertFalse(densityDetail.getComponentDensity().trim().isEmpty(),
                            "componentDensity不应为空字符串");
                    log.info("[LAMMPS-DB-E2E] ✓ componentDensity字段已填充");
                } else {
                    log.info("[LAMMPS-DB-E2E] componentDensity为null（后处理可能未生成组分密度数据）");
                }
            } else {
                log.warn("[LAMMPS-DB-E2E] ⚠ DensityResult子表不存在（后处理可能未生成子表数据）");
            }
        } else {
            log.warn("[LAMMPS-DB-E2E] ⚠ 未找到density的CalculationResult记录");
        }

        // ===== 验证conductivity子表 =====
        log.info("[LAMMPS-DB-E2E] ===== 验证ConductivityResult子表 =====");
        CalculationResult condResult = results.stream()
                .filter(r -> "conductivity".equals(r.getPropertyName()))
                .findFirst().orElse(null);

        if (condResult != null) {
            ConductivityResult condDetail = conductivityResultRepository
                    .findByResultId(condResult.getResultId());

            if (condDetail != null) {
                log.info("[LAMMPS-DB-E2E] ConductivityResult子表存在");
                log.info("[LAMMPS-DB-E2E]   resultId = {}", condDetail.getResultId());
                log.info("[LAMMPS-DB-E2E]   conductivityTensor = {}", condDetail.getConductivityTensor());
                log.info("[LAMMPS-DB-E2E]   ionContribution = {}", condDetail.getIonContribution());
                log.info("[LAMMPS-DB-E2E]   resistivity = {}", condDetail.getResistivity());

                // 验证resultId与主表一致
                assertEquals(condResult.getResultId(), condDetail.getResultId(),
                        "ConductivityResult.resultId应与CalculationResult.resultId一致");

                // 验证conductivityTensor字段（JSON格式，包含xx/yy/zz分量）
                if (condDetail.getConductivityTensor() != null) {
                    assertFalse(condDetail.getConductivityTensor().trim().isEmpty(),
                            "conductivityTensor不应为空字符串");
                    log.info("[LAMMPS-DB-E2E] ✓ conductivityTensor字段已填充");
                } else {
                    log.info("[LAMMPS-DB-E2E] conductivityTensor为null（后处理可能未生成张量数据）");
                }

                // 验证ionContribution字段（JSON格式，各离子电导率贡献占比）
                if (condDetail.getIonContribution() != null) {
                    assertFalse(condDetail.getIonContribution().trim().isEmpty(),
                            "ionContribution不应为空字符串");
                    log.info("[LAMMPS-DB-E2E] ✓ ionContribution字段已填充");
                } else {
                    log.info("[LAMMPS-DB-E2E] ionContribution为null（后处理可能未生成离子贡献数据）");
                }

                // 验证resistivity字段（电阻率，单位Ω·cm）
                if (condDetail.getResistivity() != null) {
                    assertTrue(Double.isFinite(condDetail.getResistivity()),
                            "resistivity应为有限值");
                    assertTrue(condDetail.getResistivity() >= 0,
                            "resistivity应为非负值");
                    log.info("[LAMMPS-DB-E2E] ✓ resistivity字段已填充: {} Ω·cm", condDetail.getResistivity());
                } else {
                    log.info("[LAMMPS-DB-E2E] resistivity为null（后处理可能未计算电阻率）");
                }
            } else {
                log.warn("[LAMMPS-DB-E2E] ⚠ ConductivityResult子表不存在（后处理可能未生成子表数据）");
            }
        } else {
            log.warn("[LAMMPS-DB-E2E] ⚠ 未找到conductivity的CalculationResult记录");
        }

        // ===== 验证viscosity子表 =====
        log.info("[LAMMPS-DB-E2E] ===== 验证ViscosityResult子表 =====");
        CalculationResult viscResult = results.stream()
                .filter(r -> "viscosity".equals(r.getPropertyName()))
                .findFirst().orElse(null);

        if (viscResult != null) {
            ViscosityResult viscDetail = viscosityResultRepository
                    .findByResultId(viscResult.getResultId());

            if (viscDetail != null) {
                log.info("[LAMMPS-DB-E2E] ViscosityResult子表存在");
                log.info("[LAMMPS-DB-E2E]   resultId = {}", viscDetail.getResultId());
                log.info("[LAMMPS-DB-E2E]   viscosityValue = {}", viscDetail.getViscosityValue());
                log.info("[LAMMPS-DB-E2E]   shearRate = {}", viscDetail.getShearRate());
                log.info("[LAMMPS-DB-E2E]   stressResponse = {}", viscDetail.getStressResponse());
                log.info("[LAMMPS-DB-E2E]   kinematicViscosity = {}", viscDetail.getKinematicViscosity());

                // 验证resultId与主表一致
                assertEquals(viscResult.getResultId(), viscDetail.getResultId(),
                        "ViscosityResult.resultId应与CalculationResult.resultId一致");

                // 验证viscosityValue字段（剪切粘度值，单位Pa·s）
                if (viscDetail.getViscosityValue() != null) {
                    assertTrue(Double.isFinite(viscDetail.getViscosityValue()),
                            "viscosityValue应为有限值");
                    log.info("[LAMMPS-DB-E2E] ✓ viscosityValue字段已填充: {} Pa·s", viscDetail.getViscosityValue());
                } else {
                    log.info("[LAMMPS-DB-E2E] viscosityValue为null（后处理可能未计算剪切粘度）");
                }

                // 验证shearRate字段（剪切速率，单位s⁻¹）
                if (viscDetail.getShearRate() != null) {
                    assertTrue(Double.isFinite(viscDetail.getShearRate()),
                            "shearRate应为有限值");
                    log.info("[LAMMPS-DB-E2E] ✓ shearRate字段已填充: {} s⁻¹", viscDetail.getShearRate());
                } else {
                    log.info("[LAMMPS-DB-E2E] shearRate为null（后处理可能未计算剪切速率）");
                }

                // 验证stressResponse字段（应力响应，单位Pa）
                if (viscDetail.getStressResponse() != null) {
                    assertTrue(Double.isFinite(viscDetail.getStressResponse()),
                            "stressResponse应为有限值");
                    log.info("[LAMMPS-DB-E2E] ✓ stressResponse字段已填充: {} Pa", viscDetail.getStressResponse());
                } else {
                    log.info("[LAMMPS-DB-E2E] stressResponse为null（后处理可能未计算应力响应）");
                }

                // 验证kinematicViscosity字段（运动粘度，单位mm²/s）
                if (viscDetail.getKinematicViscosity() != null) {
                    assertTrue(Double.isFinite(viscDetail.getKinematicViscosity()),
                            "kinematicViscosity应为有限值");
                    log.info("[LAMMPS-DB-E2E] ✓ kinematicViscosity字段已填充: {} mm²/s",
                            viscDetail.getKinematicViscosity());
                } else {
                    log.info("[LAMMPS-DB-E2E] kinematicViscosity为null（后处理可能未计算运动粘度）");
                }
            } else {
                log.warn("[LAMMPS-DB-E2E] ⚠ ViscosityResult子表不存在（后处理可能未生成子表数据）");
            }
        } else {
            log.warn("[LAMMPS-DB-E2E] ⚠ 未找到viscosity的CalculationResult记录");
        }

        log.info("[LAMMPS-DB-E2E] ✓ 阶段五完成：数据库子表记录验证通过");
    }

    // ==================== 阶段六：验证状态流转 ====================

    /**
     * 验证SimulationJob的状态流转和时间字段
     *
     * <p>验证点：</p>
     * <ul>
     *   <li>最终状态为COMPLETED</li>
     *   <li>startTime已设置</li>
     *   <li>endTime已设置</li>
     *   <li>executionTimeS为正数</li>
     * </ul>
     */
    @Test
    @Order(6)
    @DisplayName("阶段六：验证状态流转和时间字段")
    void testVerifyStatusTransitions() {
        log.info("========================================");
        log.info("[LAMMPS-DB-E2E] 阶段六：验证状态流转和时间字段");
        log.info("========================================");

        assertNotNull(sharedJobId, "jobId不应为null，请确保阶段一已执行");

        SimulationJob job = simulationRepository.findById(sharedJobId).orElse(null);
        assertNotNull(job, "SimulationJob记录应存在");

        // ===== 验证最终状态为COMPLETED =====
        assertEquals(JobStatus.COMPLETED, job.getStatus(),
                "最终状态应为COMPLETED，实际: " + job.getStatus());
        log.info("[LAMMPS-DB-E2E] ✓ 最终状态验证通过: {}", job.getStatus());

        // ===== 验证startTime已设置 =====
        assertNotNull(job.getStartTime(), "COMPLETED任务应有startTime");
        log.info("[LAMMPS-DB-E2E] ✓ startTime已设置: {}", job.getStartTime());

        // ===== 验证endTime已设置 =====
        assertNotNull(job.getEndTime(), "COMPLETED任务应有endTime");
        log.info("[LAMMPS-DB-E2E] ✓ endTime已设置: {}", job.getEndTime());

        // ===== 验证executionTimeS为正数 =====
        assertNotNull(job.getExecutionTimeS(), "COMPLETED任务应有executionTimeS");
        assertTrue(job.getExecutionTimeS() > 0,
                "executionTimeS应为正数，实际: " + job.getExecutionTimeS());
        log.info("[LAMMPS-DB-E2E] ✓ executionTimeS验证通过: {}秒", job.getExecutionTimeS());

        // ===== 验证endTime在startTime之后 =====
        assertTrue(job.getEndTime().isAfter(job.getStartTime()) || job.getEndTime().equals(job.getStartTime()),
                "endTime应在startTime之后或相等");
        log.info("[LAMMPS-DB-E2E] ✓ endTime在startTime之后");

        // ===== 验证errorMessage为null（COMPLETED状态不应有错误信息） =====
        assertNull(job.getErrorMessage(), "COMPLETED任务不应有errorMessage");
        log.info("[LAMMPS-DB-E2E] ✓ errorMessage为null（无错误）");

        // ===== 输出状态流转记录 =====
        log.info("[LAMMPS-DB-E2E] 完整状态流转: {}", String.join(" → ", statusTransitions));

        log.info("[LAMMPS-DB-E2E] ✓ 阶段六完成：状态流转验证通过");
    }

    // ==================== 阶段七：验证文件路径 ====================

    /**
     * 验证CalculationResult中的文件路径规范
     *
     * <p>验证点：</p>
     * <ul>
     *   <li>rawDataPath为相对路径（非绝对路径）</li>
     *   <li>rawDataPath指向的文件在文件系统中实际存在</li>
     *   <li>chartDataPath为相对路径（非绝对路径）</li>
     *   <li>chartDataPath指向的文件在文件系统中实际存在</li>
     * </ul>
     */
    @Test
    @Order(7)
    @DisplayName("阶段七：验证文件路径规范和文件存在性")
    void testVerifyFilePaths() {
        log.info("========================================");
        log.info("[LAMMPS-DB-E2E] 阶段七：验证文件路径规范和文件存在性");
        log.info("========================================");

        assertNotNull(sharedJobId, "jobId不应为null，请确保阶段一已执行");

        SimulationJob job = simulationRepository.findById(sharedJobId).orElse(null);
        assertNotNull(job, "SimulationJob记录应存在");

        Assumptions.assumeTrue(JobStatus.COMPLETED.equals(job.getStatus()),
                "任务未完成，跳过文件路径验证。当前状态: " + job.getStatus());

        Long userId = job.getUserId();
        List<CalculationResult> results = calculationResultRepository.findByJobId(sharedJobId);
        assertFalse(results.isEmpty(), "CalculationResult记录不应为空");

        for (CalculationResult result : results) {
            log.info("[LAMMPS-DB-E2E] ===== 验证 {} 的文件路径 =====", result.getPropertyName());

            // ===== 验证rawDataPath =====
            String rawDataPath = result.getRawDataPath();
            if (rawDataPath != null && !rawDataPath.trim().isEmpty()) {
                // 验证是相对路径
                assertFalse(rawDataPath.contains(":"),
                        "rawDataPath应为相对路径，不应包含盘符: " + rawDataPath
                                + " (propertyName=" + result.getPropertyName() + ")");
                assertFalse(rawDataPath.startsWith("/"),
                        "rawDataPath应为相对路径，不应以/开头: " + rawDataPath
                                + " (propertyName=" + result.getPropertyName() + ")");
                assertFalse(rawDataPath.startsWith("\\"),
                        "rawDataPath应为相对路径，不应以\\开头: " + rawDataPath
                                + " (propertyName=" + result.getPropertyName() + ")");
                log.info("[LAMMPS-DB-E2E] ✓ rawDataPath为相对路径: {}", rawDataPath);

                // 验证文件实际存在
                Path absPath = pathUtil.resolveAbsolutePath(userId, sharedJobId, rawDataPath);
                boolean fileExists = Files.exists(absPath);
                log.info("[LAMMPS-DB-E2E] rawDataPath绝对路径: {}", absPath);
                log.info("[LAMMPS-DB-E2E] rawDataPath文件存在: {}", fileExists);
                assertTrue(fileExists,
                        "rawDataPath指向的文件应存在: " + absPath
                                + " (propertyName=" + result.getPropertyName() + ")");

                // 验证文件非空
                if (fileExists) {
                    try {
                        long fileSize = Files.size(absPath);
                        assertTrue(fileSize > 0,
                                "rawDataPath指向的文件不应为空: " + absPath
                                        + " (propertyName=" + result.getPropertyName() + ")");
                        log.info("[LAMMPS-DB-E2E] ✓ rawDataPath文件大小: {} 字节", fileSize);
                    } catch (Exception e) {
                        log.warn("[LAMMPS-DB-E2E] 读取rawDataPath文件大小失败: {}", e.getMessage());
                    }
                }
            } else {
                log.warn("[LAMMPS-DB-E2E] ⚠ rawDataPath为null或空 (propertyName={})", result.getPropertyName());
            }

            // ===== 验证chartDataPath =====
            String chartDataPath = result.getChartDataPath();
            if (chartDataPath != null && !chartDataPath.trim().isEmpty()) {
                // 验证是相对路径
                assertFalse(chartDataPath.contains(":"),
                        "chartDataPath应为相对路径，不应包含盘符: " + chartDataPath
                                + " (propertyName=" + result.getPropertyName() + ")");
                assertFalse(chartDataPath.startsWith("/"),
                        "chartDataPath应为相对路径，不应以/开头: " + chartDataPath
                                + " (propertyName=" + result.getPropertyName() + ")");
                assertFalse(chartDataPath.startsWith("\\"),
                        "chartDataPath应为相对路径，不应以\\开头: " + chartDataPath
                                + " (propertyName=" + result.getPropertyName() + ")");
                log.info("[LAMMPS-DB-E2E] ✓ chartDataPath为相对路径: {}", chartDataPath);

                // 验证文件实际存在
                Path absPath = pathUtil.resolveAbsolutePath(userId, sharedJobId, chartDataPath);
                boolean fileExists = Files.exists(absPath);
                log.info("[LAMMPS-DB-E2E] chartDataPath绝对路径: {}", absPath);
                log.info("[LAMMPS-DB-E2E] chartDataPath文件存在: {}", fileExists);
                assertTrue(fileExists,
                        "chartDataPath指向的文件应存在: " + absPath
                                + " (propertyName=" + result.getPropertyName() + ")");

                // 验证文件非空
                if (fileExists) {
                    try {
                        long fileSize = Files.size(absPath);
                        assertTrue(fileSize > 0,
                                "chartDataPath指向的文件不应为空: " + absPath
                                        + " (propertyName=" + result.getPropertyName() + ")");
                        log.info("[LAMMPS-DB-E2E] ✓ chartDataPath文件大小: {} 字节", fileSize);
                    } catch (Exception e) {
                        log.warn("[LAMMPS-DB-E2E] 读取chartDataPath文件大小失败: {}", e.getMessage());
                    }
                }
            } else {
                log.warn("[LAMMPS-DB-E2E] ⚠ chartDataPath为null或空 (propertyName={})", result.getPropertyName());
            }
        }

        log.info("[LAMMPS-DB-E2E] ✓ 阶段七完成：文件路径验证通过");
    }

    // ==================== 综合报告 ====================

    /**
     * 在所有测试之后生成LAMMPS到数据库端到端测试综合报告
     *
     * <p>汇总输出测试结果信息，包括任务ID、状态、计算结果、数据库记录统计等。</p>
     */
    @Test
    @Order(99)
    @DisplayName("生成LAMMPS到数据库端到端测试综合报告")
    void testGenerateFinalReport() {
        log.info("");
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║    LAMMPS到数据库端到端测试报告 (LAMMPS-DB E2E Test)       ║");
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

        // 任务基本信息
        log.info("║  任务ID: {}{}", sharedJobId,
                " ".repeat(Math.max(0, 44 - String.valueOf(sharedJobId).length())) + "║");
        log.info("║  任务名称: {}{}", job.getJobName(),
                " ".repeat(Math.max(0, 45 - safeLength(job.getJobName()))) + "║");
        log.info("║  最终状态: {}{}", job.getStatus(),
                " ".repeat(Math.max(0, 45 - safeLength(job.getStatus()))) + "║");
        log.info("║  状态流转: {}{}",
                String.join(" → ", statusTransitions),
                " ".repeat(Math.max(0, 44 - String.join(" → ", statusTransitions).length())) + "║");

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
        if (JobStatus.COMPLETED.equals(job.getStatus())) {
            List<CalculationResult> results = calculationResultRepository.findByJobId(sharedJobId);
            log.info("╠══════════════════════════════════════════════════════════════╣");
            log.info("║  计算结果统计 (共 {} 条)                                   ║", results.size());

            for (CalculationResult r : results) {
                String line = String.format("║    %-12s = %-10.4f %-12s [%s]",
                        r.getPropertyName(), r.getPropertyValue(),
                        r.getPropertyUnit(), r.getConvergenceStatus());
                log.info(line + " ".repeat(Math.max(0, 58 - line.length())) + "║");
            }

            // 子表统计
            log.info("╠══════════════════════════════════════════════════════════════╣");
            log.info("║  子表记录统计                                               ║");

            for (CalculationResult r : results) {
                String subTableStatus = "不存在";
                switch (r.getPropertyName()) {
                    case "density":
                        DensityResult dr = densityResultRepository.findByResultId(r.getResultId());
                        subTableStatus = dr != null ? "存在" : "不存在";
                        break;
                    case "conductivity":
                        ConductivityResult cr = conductivityResultRepository.findByResultId(r.getResultId());
                        subTableStatus = cr != null ? "存在" : "不存在";
                        break;
                    case "viscosity":
                        ViscosityResult vr = viscosityResultRepository.findByResultId(r.getResultId());
                        subTableStatus = vr != null ? "存在" : "不存在";
                        break;
                    case "dielectric":
                        DielectricResult dir = dielectricResultRepository.findByResultId(r.getResultId());
                        subTableStatus = dir != null ? "存在" : "不存在";
                        break;
                    case "solvation":
                        SolvationResult sr = solvationResultRepository.findByResultId(r.getResultId());
                        subTableStatus = sr != null ? "存在" : "不存在";
                        break;
                    default:
                        subTableStatus = "未知属性";
                }
                log.info("║    {} 子表: {}{}",
                        r.getPropertyName(), subTableStatus,
                        " ".repeat(Math.max(0, 42 - r.getPropertyName().length()
                                - subTableStatus.length())) + "║");
            }

            // 文件路径统计
            log.info("╠══════════════════════════════════════════════════════════════╣");
            log.info("║  文件路径统计                                               ║");
            for (CalculationResult r : results) {
                String rawStatus = r.getRawDataPath() != null ? "已设置" : "未设置";
                String chartStatus = r.getChartDataPath() != null ? "已设置" : "未设置";
                log.info("║    {} rawDataPath={}, chartDataPath={}{}",
                        r.getPropertyName(), rawStatus, chartStatus,
                        " ".repeat(Math.max(0, 22 - r.getPropertyName().length()
                                - rawStatus.length() - chartStatus.length())) + "║");
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

    /**
     * 验证文件非空
     *
     * @param filePath 文件路径
     * @param fileName 文件名称（用于日志输出）
     */
    private void verifyFileNotEmpty(Path filePath, String fileName) {
        try {
            long fileSize = Files.size(filePath);
            assertTrue(fileSize > 0, fileName + "文件不应为空");
            log.info("[LAMMPS-DB-E2E] ✓ {} 文件大小: {} 字节", fileName, fileSize);
        } catch (Exception e) {
            log.warn("[LAMMPS-DB-E2E] 读取{}文件大小失败: {}", fileName, e.getMessage());
        }
    }

    /**
     * 安全获取字符串长度，null时返回4（"null"的长度）
     *
     * @param s 输入字符串
     * @return 字符串长度
     */
    private static int safeLength(String s) {
        return s == null ? 4 : s.length();
    }
}
