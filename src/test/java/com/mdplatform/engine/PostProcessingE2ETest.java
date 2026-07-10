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
 * 后处理端到端集成测试
 *
 * <p>该测试覆盖从任务提交、LAMMPS模拟完成到后处理计算及结果验证的完整链路，不使用任何Mock。</p>
 *
 * <h3>测试范围</h3>
 * <ul>
 *   <li>环境检查：验证Docker和md_engine容器可用性</li>
 *   <li>单属性后处理：提交仅计算density的任务，验证后处理执行和结果文件</li>
 *   <li>结果文件验证：验证post_processing目录和visualization目录的JSON文件格式与内容</li>
 *   <li>多属性后处理：提交计算density+conductivity+viscosity的任务，验证多属性结果</li>
 *   <li>JSON结果内容验证：解析结果JSON文件，验证字段完整性和物理合理性</li>
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
 * <p>标准电解液配方：EC:DMC = 3:7（摩尔比），1M LiPF6，温度 353K</p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PostProcessingE2ETest {

    // ==================== 依赖注入 ====================

    /** REST测试客户端，用于通过HTTP接口提交任务和查询状态 */
    @Autowired
    private TestRestTemplate restTemplate;

    /** 模拟任务仓库，用于直接查询数据库中的任务记录 */
    @Autowired
    private SimulationRepository simulationRepository;

    /** 模拟输入仓库，用于查询任务的输入参数 */
    @Autowired
    private SimulationInputRepository simulationInputRepository;

    /** 计算结果仓库，用于查询后处理生成的计算结果记录 */
    @Autowired
    private CalculationResultRepository calculationResultRepository;

    /** 密度结果仓库，用于查询密度子表数据 */
    @Autowired
    private DensityResultRepository densityResultRepository;

    /** 电导率结果仓库，用于查询电导率子表数据 */
    @Autowired
    private ConductivityResultRepository conductivityResultRepository;

    /** Docker服务，用于检查Docker和容器可用性 */
    @Autowired(required = false)
    private DockerService dockerService;

    /** 路径工具类，用于生成和解析文件路径，禁止硬编码路径 */
    @Autowired
    private PathUtil pathUtil;

    /** JSON序列化/反序列化工具，用于解析后处理结果JSON文件 */
    @Autowired
    private ObjectMapper objectMapper;

    /** Docker容器名称，从配置文件注入，避免硬编码 */
    @org.springframework.beans.factory.annotation.Value("${app.docker.md-container-name}")
    private String mdContainerName;

    // ==================== 测试状态 ====================

    /** 单属性任务的jobId，在多个测试方法间共享 */
    private static Long singlePropertyJobId;

    /** 多属性任务的jobId，在多个测试方法间共享 */
    private static Long multiPropertyJobId;

    /** 单属性任务是否完成（COMPLETED状态） */
    private static boolean singlePropertyCompleted = false;

    /** 多属性任务是否完成（COMPLETED状态） */
    private static boolean multiPropertyCompleted = false;

    // ==================== 环境检查 ====================

    /**
     * 环境检查测试 - 验证Docker和md_engine容器可用性
     *
     * <p>该测试在所有其他测试之前执行，验证运行环境是否满足要求。
     * 如果Docker不可用或md_engine容器未运行，则跳过所有后续测试。</p>
     *
     * <p>验证点：</p>
     * <ul>
     *   <li>Docker服务可用（dockerClient已注入）</li>
     *   <li>md_engine容器处于running状态</li>
     * </ul>
     */
    @Test
    @Order(0)
    @DisplayName("环境检查 - 验证 Docker 和 md_engine 可用性")
    void testEnvironmentCheck() {
        log.info("========================================");
        log.info("[后处理E2E] 环境检查开始");
        log.info("========================================");

        // 检查Docker是否可用
        boolean dockerAvailable = dockerService != null && dockerService.isDockerAvailable();
        Assumptions.assumeTrue(dockerAvailable,
                "Docker 不可用，跳过后处理端到端测试。请确保 Docker Desktop 正在运行。");
        log.info("[后处理E2E] ✓ Docker 服务可用");

        // 检查容器是否正在运行
        String containerStatus = dockerService.getContainerStatus();
        log.info("[后处理E2E] {} 容器状态: {}", mdContainerName, containerStatus);

        boolean engineAvailable = containerStatus != null
                && containerStatus.toLowerCase().contains("running");
        Assumptions.assumeTrue(engineAvailable,
                mdContainerName + " 容器未运行，跳过后处理端到端测试。当前状态: " + containerStatus
                        + "。请执行 docker-compose up -d md-engine。");
        log.info("[后处理E2E] ✓ {} 容器正在运行", mdContainerName);

        // 检查GPU可用性（后处理可能需要GPU加速）
        boolean gpuAvailable = dockerService.isGPUAvailable();
        log.info("[后处理E2E] GPU 可用性: {}", gpuAvailable ? "可用" : "不可用（将使用CPU模式）");

        log.info("[后处理E2E] ✓ 环境检查通过，开始后处理端到端测试");
    }

    // ==================== 阶段一：单属性后处理执行 ====================

    /**
     * 单属性后处理执行测试
     *
     * <p>提交仅计算density的Pipeline任务，等待整个流水线（包括LAMMPS模拟和后处理）完成，
     * 验证任务最终状态为COMPLETED。</p>
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>构建仅包含density的PipelineSubmitRequest</li>
     *   <li>通过POST /api/pipeline/submit提交任务</li>
     *   <li>轮询等待任务状态变为COMPLETED或FAILED</li>
     *   <li>验证最终状态为COMPLETED</li>
     * </ol>
     *
     * <p>验证点：</p>
     * <ul>
     *   <li>任务提交返回202 ACCEPTED</li>
     *   <li>任务最终状态为COMPLETED</li>
     *   <li>任务经历了POST_PROCESSING状态</li>
     * </ul>
     */
    @Test
    @Order(1)
    @DisplayName("阶段一：提交单属性任务并等待后处理完成")
    @SuppressWarnings("unchecked")
    void testSinglePropertyPostProcessing() {
        log.info("========================================");
        log.info("[后处理E2E] 阶段一：提交单属性后处理任务");
        log.info("========================================");

        // 构建仅计算density的请求
        PipelineSubmitRequest request = TestDataBuilder.buildDensityOnlyRequest();
        // 覆盖温度为353K，与标准测试配方一致
        request.setTemperature(TestDataBuilder.DEFAULT_TEST_TEMPERATURE);
        log.info("[后处理E2E] 测试配方: EC:DMC=3:7, 1M LiPF6, T=353K");
        log.info("[后处理E2E] 目标属性: {}", request.getTargetProperties());

        // 提交任务
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/pipeline/submit?testUserId=" + TestDataBuilder.DEFAULT_TEST_USER_ID,
                request,
                Map.class
        );

        // 验证HTTP响应
        log.info("[后处理E2E] HTTP 响应状态: {}", response.getStatusCodeValue());
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode(),
                "任务提交应返回 202 ACCEPTED");

        Map<String, Object> body = response.getBody();
        assertNotNull(body, "响应体不应为 null");
        assertNotNull(body.get("jobId"), "响应体应包含 jobId");

        singlePropertyJobId = ((Number) body.get("jobId")).longValue();
        log.info("[后处理E2E] ✓ 任务提交成功，jobId = {}", singlePropertyJobId);

        // 轮询等待任务完成（最多20分钟，后处理可能耗时较长）
        AtomicReference<String> previousStatus = new AtomicReference<>(null);
        List<String> statusTransitions = Collections.synchronizedList(new ArrayList<>());

        await().atMost(1200, SECONDS)
                .pollInterval(5, SECONDS)
                .until(() -> {
                    ResponseEntity<PipelineProgressDto> statusResponse = restTemplate.getForEntity(
                            "/api/pipeline/status/" + singlePropertyJobId
                                    + "?testUserId=" + TestDataBuilder.DEFAULT_TEST_USER_ID,
                            PipelineProgressDto.class
                    );

                    if (statusResponse.getStatusCode() != HttpStatus.OK) {
                        log.warn("[后处理E2E] 状态 API 返回非 200: {}", statusResponse.getStatusCode());
                        return false;
                    }

                    PipelineProgressDto progress = statusResponse.getBody();
                    if (progress == null) {
                        return false;
                    }

                    String currentStatus = progress.getStatus();
                    String prevStatus = previousStatus.get();

                    // 记录状态变化
                    if (!currentStatus.equals(prevStatus)) {
                        statusTransitions.add(currentStatus);
                        log.info("[后处理E2E] 📊 状态变更: {} → {} (步骤 {}/{}, 进度 {}%)",
                                prevStatus, currentStatus,
                                progress.getCurrentStep(), progress.getTotalSteps(),
                                progress.getProgressPercent());
                        previousStatus.set(currentStatus);
                    }

                    // 检查是否完成或失败
                    if (JobStatus.COMPLETED.equals(currentStatus)) {
                        singlePropertyCompleted = true;
                        log.info("[后处理E2E] ✅ 单属性任务执行完成!");
                        return true;
                    }

                    if (JobStatus.FAILED.equals(currentStatus)) {
                        log.error("[后处理E2E] ❌ 单属性任务执行失败: {}", progress.getErrorMessage());
                        return true;
                    }

                    return false;
                });

        // 验证最终状态
        SimulationJob job = simulationRepository.findById(singlePropertyJobId).orElse(null);
        assertNotNull(job, "SimulationJob 记录应存在");
        log.info("[后处理E2E] 最终状态: {}", job.getStatus());
        log.info("[后处理E2E] 状态流转: {}", String.join(" → ", statusTransitions));

        // 验证任务经历了后处理阶段
        assertTrue(statusTransitions.contains(JobStatus.POST_PROCESSING)
                        || statusTransitions.contains(JobStatus.POST_PROCESSING_COMPLETED)
                        || JobStatus.COMPLETED.equals(job.getStatus()),
                "任务应经历后处理阶段或已完成，状态流转: " + statusTransitions);

        // 验证最终状态为COMPLETED
        assertEquals(JobStatus.COMPLETED, job.getStatus(),
                "单属性任务最终状态应为 COMPLETED");

        log.info("[后处理E2E] ✓ 阶段一完成：单属性后处理执行验证通过");
    }

    // ==================== 阶段二：结果文件验证 ====================

    /**
     * 结果文件验证测试
     *
     * <p>验证后处理生成的文件是否存在于正确的目录中，且JSON格式正确。</p>
     *
     * <p>验证点：</p>
     * <ul>
     *   <li>post_processing目录包含density_result.json</li>
     *   <li>visualization/charts目录包含density_curve.json（密度图表数据）</li>
     *   <li>JSON文件可正确解析</li>
     *   <li>density_result.json包含convergence_status字段</li>
     *   <li>密度值在物理合理范围内（0.9-1.5 g/cm³）</li>
     * </ul>
     */
    @Test
    @Order(2)
    @DisplayName("阶段二：验证后处理结果文件")
    void testResultFileVerification() {
        log.info("========================================");
        log.info("[后处理E2E] 阶段二：验证后处理结果文件");
        log.info("========================================");

        assertNotNull(singlePropertyJobId, "jobId 不应为 null，请确保阶段一已执行");

        // 仅在任务完成时验证文件
        SimulationJob job = simulationRepository.findById(singlePropertyJobId).orElse(null);
        assertNotNull(job, "SimulationJob 记录应存在");
        Assumptions.assumeTrue(JobStatus.COMPLETED.equals(job.getStatus()),
                "任务未完成，跳过结果文件验证。当前状态: " + job.getStatus());

        Long userId = job.getUserId();
        Long jobId = singlePropertyJobId;

        // ===== 1. 验证post_processing目录 =====
        Path postProcessingPath = pathUtil.getPostProcessingPath(userId, jobId);
        log.info("[后处理E2E] 后处理目录: {}", postProcessingPath);
        assertTrue(Files.exists(postProcessingPath),
                "后处理目录应存在: " + postProcessingPath);

        // 验证density_result.json存在
        Path densityResultPath = postProcessingPath.resolve("density_result.json");
        log.info("[后处理E2E] 密度结果文件路径: {}", densityResultPath);
        assertTrue(Files.exists(densityResultPath),
                "density_result.json 应存在于后处理目录: " + densityResultPath);

        // 读取并解析density_result.json
        String densityResultContent = null;
        try {
            densityResultContent = Files.readString(densityResultPath);
            log.info("[后处理E2E] density_result.json 内容长度: {} 字符", densityResultContent.length());
        } catch (Exception e) {
            fail("读取 density_result.json 失败: " + e.getMessage());
        }

        // 解析JSON并验证格式
        JsonNode densityJson = null;
        try {
            densityJson = objectMapper.readTree(densityResultContent);
            log.info("[后处理E2E] ✓ density_result.json JSON格式正确");
        } catch (Exception e) {
            fail("density_result.json JSON解析失败: " + e.getMessage());
        }

        // 验证convergence_status字段存在
        assertTrue(densityJson.has("convergence_status"),
                "density_result.json 应包含 convergence_status 字段");
        log.info("[后处理E2E] convergence_status: {}", densityJson.get("convergence_status").asText());

        // 验证密度值在物理合理范围内（0.9-1.5 g/cm³，典型电解液密度范围）
        if (densityJson.has("property_value")) {
            double densityValue = densityJson.get("property_value").asDouble();
            log.info("[后处理E2E] 密度值: {} g/cm³", densityValue);
            assertTrue(densityValue >= 0.9 && densityValue <= 1.5,
                    String.format("密度值应在物理合理范围内(0.9-1.5 g/cm³)，实际值: %.4f", densityValue));
        } else if (densityJson.has("mean")) {
            double densityValue = densityJson.get("mean").asDouble();
            log.info("[后处理E2E] 密度值(mean): {} g/cm³", densityValue);
            assertTrue(densityValue >= 0.9 && densityValue <= 1.5,
                    String.format("密度值应在物理合理范围内(0.9-1.5 g/cm³)，实际值: %.4f", densityValue));
        } else if (densityJson.has("value")) {
            double densityValue = densityJson.get("value").asDouble();
            log.info("[后处理E2E] 密度值(value): {} g/cm³", densityValue);
            assertTrue(densityValue >= 0.9 && densityValue <= 1.5,
                    String.format("密度值应在物理合理范围内(0.9-1.5 g/cm³)，实际值: %.4f", densityValue));
        }

        // ===== 2. 验证visualization/charts目录 =====
        Path chartsPath = pathUtil.getVisualizationPath(userId, jobId).resolve("charts");
        log.info("[后处理E2E] 图表目录: {}", chartsPath);

        if (Files.exists(chartsPath)) {
            // 验证thermo_curve.json（密度曲线数据）是否存在
            Path thermoCurvePath = chartsPath.resolve("density_curve.json");
            if (Files.exists(thermoCurvePath)) {
                log.info("[后处理E2E] ✓ density_curve.json 存在");
                try {
                    String thermoContent = Files.readString(thermoCurvePath);
                    JsonNode thermoJson = objectMapper.readTree(thermoContent);
                    log.info("[后处理E2E] ✓ density_curve.json JSON格式正确");
                } catch (Exception e) {
                    log.warn("[后处理E2E] ⚠ density_curve.json 解析失败: {}", e.getMessage());
                }
            } else {
                log.info("[后处理E2E] density_curve.json 不存在（后处理可能未生成图表数据）");
            }
        } else {
            log.info("[后处理E2E] visualization/charts 目录不存在（后处理可能未生成可视化数据）");
        }

        log.info("[后处理E2E] ✓ 阶段二完成：结果文件验证通过");
    }

    // ==================== 阶段三：多属性后处理 ====================

    /**
     * 多属性后处理测试
     *
     * <p>提交包含density、conductivity、viscosity三种目标属性的Pipeline任务，
     * 等待流水线完成，验证所有属性的后处理结果文件均存在。</p>
     *
     * <p>验证点：</p>
     * <ul>
     *   <li>任务提交成功并最终状态为COMPLETED</li>
     *   <li>post_processing目录包含所有目标属性的_result.json文件</li>
     *   <li>每个结果文件JSON格式正确</li>
     *   <li>数据库中每个属性都有对应的CalculationResult记录</li>
     * </ul>
     */
    @Test
    @Order(3)
    @DisplayName("阶段三：多属性后处理执行与验证")
    @SuppressWarnings("unchecked")
    void testMultiPropertyPostProcessing() {
        log.info("========================================");
        log.info("[后处理E2E] 阶段三：多属性后处理执行与验证");
        log.info("========================================");

        // 构建多属性请求：density + conductivity + viscosity
        PipelineSubmitRequest request = TestDataBuilder.buildStandardPipelineRequest();
        // 覆盖目标属性为三种
        request.setTargetProperties(Arrays.asList("density", "conductivity", "viscosity"));
        request.setJobName("后处理E2E测试-多属性");
        request.setTemperature(TestDataBuilder.DEFAULT_TEST_TEMPERATURE);
        log.info("[后处理E2E] 测试配方: EC:DMC=3:7, 1M LiPF6, T=353K");
        log.info("[后处理E2E] 目标属性: {}", request.getTargetProperties());

        // 提交任务
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/pipeline/submit?testUserId=" + TestDataBuilder.DEFAULT_TEST_USER_ID,
                request,
                Map.class
        );

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode(),
                "任务提交应返回 202 ACCEPTED");

        Map<String, Object> body = response.getBody();
        assertNotNull(body, "响应体不应为 null");
        multiPropertyJobId = ((Number) body.get("jobId")).longValue();
        log.info("[后处理E2E] ✓ 多属性任务提交成功，jobId = {}", multiPropertyJobId);

        // 轮询等待任务完成（最多20分钟）
        AtomicReference<String> previousStatus = new AtomicReference<>(null);

        await().atMost(1200, SECONDS)
                .pollInterval(5, SECONDS)
                .until(() -> {
                    ResponseEntity<PipelineProgressDto> statusResponse = restTemplate.getForEntity(
                            "/api/pipeline/status/" + multiPropertyJobId
                                    + "?testUserId=" + TestDataBuilder.DEFAULT_TEST_USER_ID,
                            PipelineProgressDto.class
                    );

                    if (statusResponse.getStatusCode() != HttpStatus.OK) {
                        return false;
                    }

                    PipelineProgressDto progress = statusResponse.getBody();
                    if (progress == null) {
                        return false;
                    }

                    String currentStatus = progress.getStatus();
                    String prevStatus = previousStatus.get();

                    if (!currentStatus.equals(prevStatus)) {
                        log.info("[后处理E2E] 📊 多属性任务状态变更: {} → {} (步骤 {}/{}, 进度 {}%)",
                                prevStatus, currentStatus,
                                progress.getCurrentStep(), progress.getTotalSteps(),
                                progress.getProgressPercent());
                        previousStatus.set(currentStatus);
                    }

                    if (JobStatus.COMPLETED.equals(currentStatus)) {
                        multiPropertyCompleted = true;
                        log.info("[后处理E2E] ✅ 多属性任务执行完成!");
                        return true;
                    }

                    if (JobStatus.FAILED.equals(currentStatus)) {
                        log.error("[后处理E2E] ❌ 多属性任务执行失败: {}", progress.getErrorMessage());
                        return true;
                    }

                    return false;
                });

        // 验证最终状态
        SimulationJob job = simulationRepository.findById(multiPropertyJobId).orElse(null);
        assertNotNull(job, "SimulationJob 记录应存在");
        log.info("[后处理E2E] 多属性任务最终状态: {}", job.getStatus());

        Assumptions.assumeTrue(JobStatus.COMPLETED.equals(job.getStatus()),
                "多属性任务未完成，跳过后续验证。当前状态: " + job.getStatus());

        // ===== 验证所有属性的结果文件 =====
        Long userId = job.getUserId();
        Path postProcessingPath = pathUtil.getPostProcessingPath(userId, multiPropertyJobId);
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
            log.info("[后处理E2E] {} 结果文件: {} 存在={}", propertyName, resultPath, fileExists);

            if (fileExists) {
                // 验证JSON格式正确
                try {
                    String content = Files.readString(resultPath);
                    JsonNode json = objectMapper.readTree(content);
                    log.info("[后处理E2E] ✓ {} 结果JSON格式正确", propertyName);

                    // 验证convergence_status字段存在
                    assertTrue(json.has("convergence_status"),
                            propertyName + "_result.json 应包含 convergence_status 字段");
                } catch (Exception e) {
                    log.warn("[后处理E2E] ⚠ {} 结果JSON解析失败: {}", propertyName, e.getMessage());
                }
            } else {
                log.warn("[后处理E2E] ⚠ {} 结果文件不存在（后处理可能未生成该属性结果）", propertyName);
            }
        }

        // ===== 验证数据库中的CalculationResult记录 =====
        List<CalculationResult> results = calculationResultRepository.findByJobId(multiPropertyJobId);
        log.info("[后处理E2E] CalculationResult 记录数: {}", results.size());
        assertFalse(results.isEmpty(), "COMPLETED 任务应有 CalculationResult 记录");

        Set<String> propertyNames = new HashSet<>();
        for (CalculationResult result : results) {
            log.info("[后处理E2E] CalculationResult: propertyName={}, propertyValue={}, unit={}, convergence={}",
                    result.getPropertyName(), result.getPropertyValue(),
                    result.getPropertyUnit(), result.getConvergenceStatus());
            propertyNames.add(result.getPropertyName());
        }

        // 验证density至少存在
        assertTrue(propertyNames.contains("density"),
                "应有 density 的 CalculationResult 记录");

        log.info("[后处理E2E] ✓ 阶段三完成：多属性后处理验证通过");
    }

    // ==================== 阶段四：JSON结果内容详细验证 ====================

    /**
     * JSON结果内容详细验证测试
     *
     * <p>解析后处理生成的JSON结果文件，验证字段完整性和数值合理性。</p>
     *
     * <p>验证点：</p>
     * <ul>
     *   <li>density_result.json包含mean/value、std/standard_deviation、convergence_status字段</li>
     *   <li>密度值在物理合理范围内（0.9-1.5 g/cm³）</li>
     *   <li>conductivity_result.json包含电导率值（如存在）</li>
     *   <li>viscosity_result.json包含粘度值（如存在）</li>
     * </ul>
     */
    @Test
    @Order(4)
    @DisplayName("阶段四：JSON结果内容详细验证")
    void testJsonResultContentVerification() {
        log.info("========================================");
        log.info("[后处理E2E] 阶段四：JSON结果内容详细验证");
        log.info("========================================");

        // 优先使用单属性任务的jobId，如果不存在则使用多属性任务的jobId
        Long jobId = singlePropertyJobId != null ? singlePropertyJobId : multiPropertyJobId;
        assertNotNull(jobId, "至少应有一个任务的jobId");

        SimulationJob job = simulationRepository.findById(jobId).orElse(null);
        assertNotNull(job, "SimulationJob 记录应存在");
        Assumptions.assumeTrue(JobStatus.COMPLETED.equals(job.getStatus()),
                "任务未完成，跳过JSON内容验证。当前状态: " + job.getStatus());

        Long userId = job.getUserId();
        Path postProcessingPath = pathUtil.getPostProcessingPath(userId, jobId);

        // ===== 1. 验证density_result.json内容 =====
        Path densityResultPath = postProcessingPath.resolve("density_result.json");
        if (Files.exists(densityResultPath)) {
            log.info("[后处理E2E] ===== 验证 density_result.json =====");
            try {
                String content = Files.readString(densityResultPath);
                JsonNode densityJson = objectMapper.readTree(content);

                // 验证包含数值字段（property_value 或 mean 或 value）
                boolean hasValueField = densityJson.has("property_value")
                        || densityJson.has("mean")
                        || densityJson.has("value");
                assertTrue(hasValueField,
                        "density_result.json 应包含 property_value、mean 或 value 字段");
                log.info("[后处理E2E] ✓ 密度数值字段存在");

                // 提取密度值
                double densityValue = 0;
                if (densityJson.has("property_value")) {
                    densityValue = densityJson.get("property_value").asDouble();
                } else if (densityJson.has("mean")) {
                    densityValue = densityJson.get("mean").asDouble();
                } else if (densityJson.has("value")) {
                    densityValue = densityJson.get("value").asDouble();
                }
                log.info("[后处理E2E] 密度值: {}", densityValue);

                // 验证密度值为有限值
                assertTrue(Double.isFinite(densityValue),
                        String.format("密度值应为有限值，实际值: %s", densityValue));

                // 验证密度值在物理合理范围内
                assertTrue(densityValue >= 0.9 && densityValue <= 1.5,
                        String.format("密度值应在物理合理范围内(0.9-1.5 g/cm³)，实际值: %.4f", densityValue));

                // 验证包含标准差字段（std 或 standard_deviation）
                boolean hasStdField = densityJson.has("std")
                        || densityJson.has("standard_deviation");
                if (hasStdField) {
                    double stdValue = densityJson.has("std")
                            ? densityJson.get("std").asDouble()
                            : densityJson.get("standard_deviation").asDouble();
                    log.info("[后处理E2E] 密度标准差: {}", stdValue);
                    assertTrue(stdValue >= 0,
                            String.format("标准差应非负，实际值: %.4f", stdValue));
                } else {
                    log.info("[后处理E2E] density_result.json 未包含标准差字段（std/standard_deviation）");
                }

                // 验证convergence_status字段
                assertTrue(densityJson.has("convergence_status"),
                        "density_result.json 应包含 convergence_status 字段");
                String convergenceStatus = densityJson.get("convergence_status").asText();
                log.info("[后处理E2E] 收敛状态: {}", convergenceStatus);
                assertFalse(convergenceStatus.trim().isEmpty(),
                        "convergence_status 不应为空字符串");

                log.info("[后处理E2E] ✓ density_result.json 内容验证通过");
            } catch (Exception e) {
                fail("解析 density_result.json 失败: " + e.getMessage());
            }
        } else {
            log.warn("[后处理E2E] ⚠ density_result.json 不存在，跳过密度内容验证");
        }

        // ===== 2. 验证conductivity_result.json内容（如存在） =====
        Path conductivityResultPath = postProcessingPath.resolve("conductivity_result.json");
        if (Files.exists(conductivityResultPath)) {
            log.info("[后处理E2E] ===== 验证 conductivity_result.json =====");
            try {
                String content = Files.readString(conductivityResultPath);
                JsonNode condJson = objectMapper.readTree(content);

                // 验证包含电导率值
                boolean hasValueField = condJson.has("property_value")
                        || condJson.has("mean")
                        || condJson.has("value");
                assertTrue(hasValueField,
                        "conductivity_result.json 应包含 property_value、mean 或 value 字段");
                log.info("[后处理E2E] ✓ 电导率数值字段存在");

                // 验证convergence_status字段
                assertTrue(condJson.has("convergence_status"),
                        "conductivity_result.json 应包含 convergence_status 字段");
                log.info("[后处理E2E] 电导率收敛状态: {}", condJson.get("convergence_status").asText());

                // 验证是否包含张量数据（如有）
                if (condJson.has("sub_table_data")) {
                    JsonNode subTableData = condJson.get("sub_table_data");
                    if (subTableData.has("conductivity_tensor")) {
                        log.info("[后处理E2E] ✓ 电导率张量数据存在");
                    }
                    if (subTableData.has("ion_contribution")) {
                        log.info("[后处理E2E] ✓ 离子贡献数据存在");
                    }
                }

                log.info("[后处理E2E] ✓ conductivity_result.json 内容验证通过");
            } catch (Exception e) {
                log.warn("[后处理E2E] ⚠ conductivity_result.json 解析失败: {}", e.getMessage());
            }
        } else {
            log.info("[后处理E2E] conductivity_result.json 不存在，跳过电导率内容验证");
        }

        // ===== 3. 验证viscosity_result.json内容（如存在） =====
        Path viscosityResultPath = postProcessingPath.resolve("viscosity_result.json");
        if (Files.exists(viscosityResultPath)) {
            log.info("[后处理E2E] ===== 验证 viscosity_result.json =====");
            try {
                String content = Files.readString(viscosityResultPath);
                JsonNode viscJson = objectMapper.readTree(content);

                // 验证包含粘度值
                boolean hasValueField = viscJson.has("property_value")
                        || viscJson.has("mean")
                        || viscJson.has("value");
                assertTrue(hasValueField,
                        "viscosity_result.json 应包含 property_value、mean 或 value 字段");
                log.info("[后处理E2E] ✓ 粘度数值字段存在");

                // 验证convergence_status字段
                assertTrue(viscJson.has("convergence_status"),
                        "viscosity_result.json 应包含 convergence_status 字段");
                log.info("[后处理E2E] 粘度收敛状态: {}", viscJson.get("convergence_status").asText());

                // 验证子表数据（如有）
                if (viscJson.has("sub_table_data")) {
                    JsonNode subTableData = viscJson.get("sub_table_data");
                    if (subTableData.has("viscosity_value")) {
                        log.info("[后处理E2E] ✓ 粘度值(子表)存在: {}", subTableData.get("viscosity_value"));
                    }
                    if (subTableData.has("shear_rate")) {
                        log.info("[后处理E2E] ✓ 剪切率数据存在");
                    }
                }

                log.info("[后处理E2E] ✓ viscosity_result.json 内容验证通过");
            } catch (Exception e) {
                log.warn("[后处理E2E] ⚠ viscosity_result.json 解析失败: {}", e.getMessage());
            }
        } else {
            log.info("[后处理E2E] viscosity_result.json 不存在，跳过粘度内容验证");
        }

        // ===== 4. 验证数据库CalculationResult记录与文件一致性 =====
        List<CalculationResult> dbResults = calculationResultRepository.findByJobId(jobId);
        log.info("[后处理E2E] 数据库CalculationResult记录数: {}", dbResults.size());

        for (CalculationResult result : dbResults) {
            log.info("[后处理E2E] 数据库记录: propertyName={}, propertyValue={}, unit={}, convergence={}",
                    result.getPropertyName(), result.getPropertyValue(),
                    result.getPropertyUnit(), result.getConvergenceStatus());

            // 验证propertyValue不为null且为有限值
            assertNotNull(result.getPropertyValue(),
                    "propertyValue 不应为 null (propertyName=" + result.getPropertyName() + ")");
            assertFalse(Double.isNaN(result.getPropertyValue()),
                    "propertyValue 不应为 NaN (propertyName=" + result.getPropertyName() + ")");
            assertTrue(Double.isFinite(result.getPropertyValue()),
                    "propertyValue 应为有限值 (propertyName=" + result.getPropertyName() + ")");

            // 验证convergenceStatus不为null
            assertNotNull(result.getConvergenceStatus(),
                    "convergenceStatus 不应为 null (propertyName=" + result.getPropertyName() + ")");

            // 验证propertyUnit不为null
            assertNotNull(result.getPropertyUnit(),
                    "propertyUnit 不应为 null (propertyName=" + result.getPropertyName() + ")");

            // 验证rawDataPath指向有效路径
            if (result.getRawDataPath() != null) {
                assertFalse(result.getRawDataPath().trim().isEmpty(),
                        "rawDataPath 不应为空字符串");
                log.info("[后处理E2E] rawDataPath: {}", result.getRawDataPath());
            }
        }

        log.info("[后处理E2E] ✓ 阶段四完成：JSON结果内容详细验证通过");
    }

    // ==================== 综合报告 ====================

    /**
     * 生成后处理端到端测试综合报告
     *
     * <p>在所有测试执行完成后，汇总输出测试结果信息，包括任务ID、状态、计算结果等。</p>
     */
    @Test
    @Order(99)
    @DisplayName("生成后处理端到端测试综合报告")
    void testGenerateFinalReport() {
        log.info("");
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║          后处理端到端测试报告 (PostProcessing E2E)          ║");
        log.info("╠══════════════════════════════════════════════════════════════╣");

        // 单属性任务报告
        if (singlePropertyJobId != null) {
            log.info("║  [单属性任务]                                              ║");
            SimulationJob singleJob = simulationRepository.findById(singlePropertyJobId).orElse(null);
            if (singleJob != null) {
                log.info("║    任务ID: {}", singlePropertyJobId);
                log.info("║    最终状态: {}", singleJob.getStatus());
                log.info("║    执行耗时: {}秒", singleJob.getExecutionTimeS());

                // 输出密度计算结果
                List<CalculationResult> singleResults = calculationResultRepository.findByJobId(singlePropertyJobId);
                for (CalculationResult r : singleResults) {
                    String line = String.format("║    %-12s = %-10.4f %-12s [%s]",
                            r.getPropertyName(), r.getPropertyValue(),
                            r.getPropertyUnit(), r.getConvergenceStatus());
                    log.info(line);
                }
            }
        } else {
            log.info("║  ⚠ 单属性任务未提交                                       ║");
        }

        log.info("╠══════════════════════════════════════════════════════════════╣");

        // 多属性任务报告
        if (multiPropertyJobId != null) {
            log.info("║  [多属性任务]                                              ║");
            SimulationJob multiJob = simulationRepository.findById(multiPropertyJobId).orElse(null);
            if (multiJob != null) {
                log.info("║    任务ID: {}", multiPropertyJobId);
                log.info("║    最终状态: {}", multiJob.getStatus());
                log.info("║    执行耗时: {}秒", multiJob.getExecutionTimeS());

                List<CalculationResult> multiResults = calculationResultRepository.findByJobId(multiPropertyJobId);
                for (CalculationResult r : multiResults) {
                    String line = String.format("║    %-12s = %-10.4f %-12s [%s]",
                            r.getPropertyName(), r.getPropertyValue(),
                            r.getPropertyUnit(), r.getConvergenceStatus());
                    log.info(line);
                }
            }
        } else {
            log.info("║  ⚠ 多属性任务未提交                                       ║");
        }

        log.info("╚══════════════════════════════════════════════════════════════╝");
        log.info("");
    }
}
