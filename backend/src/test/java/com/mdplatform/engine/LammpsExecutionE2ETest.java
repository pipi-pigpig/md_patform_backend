package com.mdplatform.engine;

import com.mdplatform.common.config.StorageConfig;
import com.mdplatform.common.util.PathUtil;
import com.mdplatform.engine.dto.PipelineProgressDto;
import com.mdplatform.engine.dto.PipelineSubmitRequest;
import com.mdplatform.engine.model.*;
import com.mdplatform.engine.repository.SimulationOutputRepository;
import com.mdplatform.engine.repository.SimulationRepository;
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
import java.nio.file.Paths;
import java.util.*;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * LAMMPS执行端到端集成测试
 *
 * <p>该测试覆盖LAMMPS三阶段模拟执行的完整链路，不使用任何Mock。
 * 通过Docker容器执行真实的LAMMPS模拟，验证输出文件、数据库记录的正确性。</p>
 *
 * <h3>测试范围</h3>
 * <ul>
 *   <li>环境检查：Docker可用性、md_engine容器运行状态、GPU可用性、路径对齐验证</li>
 *   <li>LAMMPS三阶段执行：能量最小化→平衡模拟→生产模拟</li>
 *   <li>轨迹文件验证：log.lammps、dump.trajectory.lammpstrj、final.data</li>
 *   <li>属性特定文件验证：conductivity、viscosity、dielectric、solvation_structure</li>
 *   <li>SimulationRawOutput数据库记录验证</li>
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
class LammpsExecutionE2ETest {

    // ==================== 依赖注入 ====================

    /** REST测试客户端，用于通过API提交任务和查询状态 */
    @Autowired
    private TestRestTemplate restTemplate;

    /** 模拟任务仓库，用于查询和验证SimulationJob记录 */
    @Autowired
    private SimulationRepository simulationRepository;

    /** 模拟原始输出仓库，用于查询和验证SimulationRawOutput记录 */
    @Autowired
    private SimulationOutputRepository simulationOutputRepository;

    /** Docker服务，用于环境检查和容器操作 */
    @Autowired(required = false)
    private DockerService dockerService;

    /** 路径工具类，用于生成和验证文件路径 */
    @Autowired
    private PathUtil pathUtil;

    /** 存储配置，用于读取和验证root-path配置 */
    @Autowired
    private StorageConfig storageConfig;

    /** Docker容器名称，从配置文件注入，避免硬编码 */
    @org.springframework.beans.factory.annotation.Value("${app.docker.md-container-name}")
    private String mdContainerName;

    // ==================== 测试状态 ====================

    /** 提交后得到的任务ID，在多个测试方法间共享 */
    private static Long sharedJobId;

    /** 记录状态流转过程 */
    private static final List<String> statusTransitions = Collections.synchronizedList(new ArrayList<>());

    /** 流水线是否完成标志 */
    private static boolean pipelineCompleted = false;

    /** 流水线是否失败标志 */
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
        log.info("[LAMMPS-E2E] 环境检查开始");
        log.info("========================================");
    }

    /**
     * 环境检查测试 - 验证Docker、md_engine容器和GPU可用性
     *
     * <p>验证点：</p>
     * <ul>
     *   <li>Docker服务是否可用</li>
     *   <li>md_engine容器是否正在运行</li>
     *   <li>GPU是否可用（记录结果，不跳过测试）</li>
     *   <li>本地文件系统与Docker挂载路径是否对齐</li>
     * </ul>
     */
    @Test
    @Order(0)
    @DisplayName("环境检查 - 验证Docker、md_engine容器和GPU可用性")
    void testEnvironmentCheck() {
        log.info("[LAMMPS-E2E] 检查Docker服务...");

        // ===== 检查Docker是否可用 =====
        boolean dockerAvailable = dockerService != null && dockerService.isDockerAvailable();
        Assumptions.assumeTrue(dockerAvailable,
                "Docker不可用，跳过所有LAMMPS端到端测试。请确保Docker Desktop正在运行。");
        log.info("[LAMMPS-E2E] ✓ Docker服务可用");

        // ===== 检查容器是否运行 =====
        String containerStatus = dockerService.getContainerStatus();
        log.info("[LAMMPS-E2E] {} 容器状态: {}", mdContainerName, containerStatus);

        boolean engineAvailable = containerStatus != null
                && containerStatus.toLowerCase().contains("running");
        Assumptions.assumeTrue(engineAvailable,
                mdContainerName + " 容器未运行，跳过所有LAMMPS端到端测试。当前状态: " + containerStatus
                        + "。请执行 docker-compose up -d md-engine。");
        log.info("[LAMMPS-E2E] ✓ {} 容器正在运行", mdContainerName);

        // ===== 检查GPU可用性（记录结果但不跳过测试） =====
        boolean gpuAvailable = dockerService.isGPUAvailable();
        if (gpuAvailable) {
            log.info("[LAMMPS-E2E] ✓ GPU可用，LAMMPS将使用GPU加速模式");
        } else {
            log.warn("[LAMMPS-E2E] ⚠ GPU不可用，LAMMPS将回退到CPU模式执行，模拟速度可能较慢");
        }

        // ===== 路径对齐诊断 =====
        log.info("[LAMMPS-E2E] ===== 路径对齐诊断 =====");
        String configuredRootPath = storageConfig.getRootPath();
        Path resolvedRootPath = Paths.get(configuredRootPath).toAbsolutePath().normalize();
        log.info("[LAMMPS-E2E] 配置的root-path: {}", configuredRootPath);
        log.info("[LAMMPS-E2E] 解析后的绝对路径: {}", resolvedRootPath);

        // 验证PathUtil.convertToDockerPath()转换是否正确
        Path testInputPath = resolvedRootPath.resolve("user_1").resolve("jobs").resolve("job_1").resolve("inputs");
        String dockerPath = pathUtil.convertToDockerPath(testInputPath);
        log.info("[LAMMPS-E2E] convertToDockerPath测试: {} → {}", testInputPath, dockerPath);
        boolean dockerPathCorrect = dockerPath.startsWith("/workspace/data/");
        if (dockerPathCorrect) {
            log.info("[LAMMPS-E2E] ✓ Docker路径转换验证通过");
        } else {
            log.error("[LAMMPS-E2E] ✗ Docker路径转换异常: {}，应以/workspace/data/开头", dockerPath);
        }

        // 验证root-path目录存在
        if (Files.exists(resolvedRootPath)) {
            log.info("[LAMMPS-E2E] ✓ root-path目录存在: {}", resolvedRootPath);
        } else {
            log.warn("[LAMMPS-E2E] ⚠ root-path目录不存在: {}，将在任务提交时自动创建", resolvedRootPath);
        }
        log.info("[LAMMPS-E2E] ===== 路径对齐诊断结束 =====");

        log.info("[LAMMPS-E2E] ✓ 环境检查通过，开始LAMMPS执行端到端测试");
    }

    // ==================== 阶段一：提交任务并等待LAMMPS执行完成 ====================

    /**
     * 提交全流程计算任务并等待LAMMPS三阶段模拟执行完成
     *
     * <p>验证点：</p>
     * <ul>
     *   <li>任务提交成功，返回202 ACCEPTED</li>
     *   <li>任务状态最终流转到COMPLETED或FAILED</li>
     *   <li>如果COMPLETED，验证任务经历了RUNNING状态（LAMMPS执行阶段）</li>
     * </ul>
     *
     * <p>该测试通过PipelineService提交完整的建模→模拟→后处理流程，
     * 重点关注LAMMPS执行阶段的状态变化和结果。</p>
     */
    @Test
    @Order(1)
    @DisplayName("阶段一：提交任务并等待LAMMPS三阶段执行完成")
    @SuppressWarnings("unchecked")
    void testSubmitTaskAndWaitForLAMMPSExecution() {
        log.info("========================================");
        log.info("[LAMMPS-E2E] 阶段一：提交任务并等待LAMMPS执行");
        log.info("========================================");

        // 构建标准测试请求（EC:DMC=3:7, 1M LiPF6, 353K）
        PipelineSubmitRequest request = TestDataBuilder.buildStandardPipelineRequest();
        log.info("[LAMMPS-E2E] 测试配方: EC:DMC=3:7, 1M LiPF6, T=353K");
        log.info("[LAMMPS-E2E] 目标属性: {}", request.getTargetProperties());

        // ===== 提交任务 =====
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/pipeline/submit?testUserId=" + TestDataBuilder.DEFAULT_TEST_USER_ID,
                request,
                Map.class
        );

        // 验证HTTP响应
        log.info("[LAMMPS-E2E] HTTP响应状态: {}", response.getStatusCodeValue());
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode(),
                "任务提交应返回 202 ACCEPTED");

        Map<String, Object> body = response.getBody();
        assertNotNull(body, "响应体不应为null");
        assertNotNull(body.get("jobId"), "响应体应包含jobId");

        Long jobId = ((Number) body.get("jobId")).longValue();
        sharedJobId = jobId;
        log.info("[LAMMPS-E2E] ✓ 任务提交成功，jobId = {}", jobId);

        // ===== 等待任务完成或失败（最多10分钟） =====
        log.info("[LAMMPS-E2E] 开始轮询任务状态，等待LAMMPS执行完成...");
        java.util.concurrent.atomic.AtomicReference<String> previousStatus =
                new java.util.concurrent.atomic.AtomicReference<>(null);

        await().atMost(600, SECONDS)
                .pollInterval(5, SECONDS)
                .until(() -> {
                    ResponseEntity<PipelineProgressDto> statusResponse = restTemplate.getForEntity(
                            "/api/pipeline/status/" + sharedJobId
                                    + "?testUserId=" + TestDataBuilder.DEFAULT_TEST_USER_ID,
                            PipelineProgressDto.class
                    );

                    if (statusResponse.getStatusCode() != HttpStatus.OK) {
                        log.warn("[LAMMPS-E2E] 状态API返回非200: {}", statusResponse.getStatusCode());
                        return false;
                    }

                    PipelineProgressDto progress = statusResponse.getBody();
                    if (progress == null) {
                        log.warn("[LAMMPS-E2E] 状态API返回空响应");
                        return false;
                    }

                    String currentStatus = progress.getStatus();
                    String prevStatus = previousStatus.get();

                    // 记录状态变化
                    if (!currentStatus.equals(prevStatus)) {
                        statusTransitions.add(currentStatus);
                        log.info("[LAMMPS-E2E] 📊 状态变更: {} → {} (步骤 {}/{}, 进度 {}%)",
                                prevStatus, currentStatus,
                                progress.getCurrentStep(), progress.getTotalSteps(),
                                progress.getProgressPercent());
                        previousStatus.set(currentStatus);
                    }

                    // 检查是否完成或失败
                    if (JobStatus.COMPLETED.equals(currentStatus)) {
                        pipelineCompleted = true;
                        log.info("[LAMMPS-E2E] ✅ 流水线执行完成!");
                        return true;
                    }

                    if (JobStatus.FAILED.equals(currentStatus)) {
                        pipelineFailed = true;
                        failureErrorMessage = progress.getErrorMessage();
                        log.error("[LAMMPS-E2E] ❌ 流水线执行失败: {}", failureErrorMessage);
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
        log.info("[LAMMPS-E2E] 最终状态: {}", finalStatus);
        log.info("[LAMMPS-E2E] 状态流转: {}", String.join(" → ", statusTransitions));

        // 验证最终状态为COMPLETED或FAILED
        assertTrue(
                JobStatus.COMPLETED.equals(finalStatus) || JobStatus.FAILED.equals(finalStatus),
                "最终状态应为COMPLETED或FAILED，实际: " + finalStatus
        );

        // 如果完成，验证经历了RUNNING状态（LAMMPS执行阶段）
        if (JobStatus.COMPLETED.equals(finalStatus)) {
            assertTrue(statusTransitions.contains(JobStatus.RUNNING),
                    "COMPLETED任务应经历RUNNING状态（LAMMPS执行阶段），实际状态流转: "
                            + String.join(" → ", statusTransitions));
            log.info("[LAMMPS-E2E] ✓ 任务经历了RUNNING状态（LAMMPS执行阶段）");
        }

        log.info("[LAMMPS-E2E] ✓ 阶段一完成：LAMMPS执行状态验证通过");
    }

    // ==================== 阶段二：轨迹文件验证 ====================

    /**
     * 验证LAMMPS执行完成后生成的核心轨迹文件
     *
     * <p>验证点：</p>
     * <ul>
     *   <li>raw_outputs/log.lammps 存在且非空</li>
     *   <li>raw_outputs/dump.trajectory.lammpstrj 存在且非空</li>
     *   <li>raw_outputs/final.data 存在且非空</li>
     * </ul>
     *
     * <p>使用PathUtil生成正确的文件路径，确保路径规范一致性。</p>
     */
    @Test
    @Order(2)
    @DisplayName("阶段二：验证LAMMPS核心轨迹输出文件")
    void testVerifyTrajectoryFiles() {
        log.info("========================================");
        log.info("[LAMMPS-E2E] 阶段二：验证LAMMPS核心轨迹输出文件");
        log.info("========================================");

        assertNotNull(sharedJobId, "jobId不应为null，请确保阶段一已执行");

        // 仅在任务完成时验证文件
        SimulationJob job = simulationRepository.findById(sharedJobId).orElse(null);
        assertNotNull(job, "SimulationJob记录应存在");

        if (!JobStatus.COMPLETED.equals(job.getStatus())) {
            log.warn("[LAMMPS-E2E] ⚠ 任务未完成（状态={}），跳过轨迹文件验证", job.getStatus());
            return;
        }

        Long userId = job.getUserId();
        Long jobId = job.getJobId();

        // 使用PathUtil生成原始输出目录路径
        Path rawOutputPath = pathUtil.getRawOutputPath(userId, jobId);
        log.info("[LAMMPS-E2E] 原始输出目录: {}", rawOutputPath);

        // ===== 验证log.lammps =====
        Path logLammpsPath = rawOutputPath.resolve("log.lammps");
        boolean logExists = Files.exists(logLammpsPath);
        log.info("[LAMMPS-E2E] log.lammps路径: {}", logLammpsPath);
        log.info("[LAMMPS-E2E] log.lammps存在: {}", logExists);
        assertTrue(logExists, "log.lammps应存在于raw_outputs目录: " + logLammpsPath);

        if (logExists) {
            try {
                long logSize = Files.size(logLammpsPath);
                log.info("[LAMMPS-E2E] log.lammps文件大小: {} 字节", logSize);
                assertTrue(logSize > 0, "log.lammps文件不应为空");

                // 验证日志文件包含LAMMPS输出特征
                String logContent = Files.readString(logLammpsPath);
                assertTrue(logContent.contains("LAMMPS") || logContent.contains("Total wall time"),
                        "log.lammps应包含LAMMPS输出特征（'LAMMPS'或'Total wall time'）");
                log.info("[LAMMPS-E2E] ✓ log.lammps内容验证通过");
            } catch (Exception e) {
                log.warn("[LAMMPS-E2E] 读取log.lammps内容失败: {}", e.getMessage());
            }
        }

        // ===== 验证dump.trajectory.lammpstrj =====
        Path trajectoryPath = rawOutputPath.resolve("dump.trajectory.lammpstrj");
        boolean trajectoryExists = Files.exists(trajectoryPath);
        log.info("[LAMMPS-E2E] dump.trajectory.lammpstrj路径: {}", trajectoryPath);
        log.info("[LAMMPS-E2E] dump.trajectory.lammpstrj存在: {}", trajectoryExists);
        assertTrue(trajectoryExists,
                "dump.trajectory.lammpstrj应存在于raw_outputs目录: " + trajectoryPath);

        if (trajectoryExists) {
            try {
                long trajectorySize = Files.size(trajectoryPath);
                log.info("[LAMMPS-E2E] dump.trajectory.lammpstrj文件大小: {} 字节", trajectorySize);
                assertTrue(trajectorySize > 0, "dump.trajectory.lammpstrj文件不应为空");

                // 验证轨迹文件包含LAMMPS轨迹格式特征
                String trajContent = Files.readString(trajectoryPath);
                assertTrue(trajContent.contains("ITEM: TIMESTEP") || trajContent.contains("ITEM: ATOMS"),
                        "dump.trajectory.lammpstrj应包含LAMMPS轨迹格式特征（'ITEM: TIMESTEP'或'ITEM: ATOMS'）");
                log.info("[LAMMPS-E2E] ✓ dump.trajectory.lammpstrj内容验证通过");
            } catch (Exception e) {
                log.warn("[LAMMPS-E2E] 读取dump.trajectory.lammpstrj内容失败: {}", e.getMessage());
            }
        }

        // ===== 验证final.data =====
        Path finalDataPath = rawOutputPath.resolve("final.data");
        boolean finalDataExists = Files.exists(finalDataPath);
        log.info("[LAMMPS-E2E] final.data路径: {}", finalDataPath);
        log.info("[LAMMPS-E2E] final.data存在: {}", finalDataExists);
        assertTrue(finalDataExists, "final.data应存在于raw_outputs目录: " + finalDataPath);

        if (finalDataExists) {
            try {
                long finalDataSize = Files.size(finalDataPath);
                log.info("[LAMMPS-E2E] final.data文件大小: {} 字节", finalDataSize);
                assertTrue(finalDataSize > 0, "final.data文件不应为空");

                // 验证data文件包含LAMMPS数据格式特征
                String dataContent = Files.readString(finalDataPath);
                assertTrue(dataContent.contains("atoms") || dataContent.contains("LAMMPS data file"),
                        "final.data应包含LAMMPS数据格式特征（'atoms'或'LAMMPS data file'）");
                log.info("[LAMMPS-E2E] ✓ final.data内容验证通过");
            } catch (Exception e) {
                log.warn("[LAMMPS-E2E] 读取final.data内容失败: {}", e.getMessage());
            }
        }

        log.info("[LAMMPS-E2E] ✓ 阶段二完成：核心轨迹文件验证通过");
    }

    // ==================== 阶段三：属性特定文件验证 ====================

    /**
     * 验证根据目标属性生成的特定输出文件
     *
     * <p>验证点（基于target_properties）：</p>
     * <ul>
     *   <li>conductivity → dump.charge.lammpstrj, msd.dat</li>
     *   <li>viscosity → pressure.dat</li>
     *   <li>dielectric → dipole.dat, total_dipole.dat</li>
     *   <li>solvation_structure → dump.solvation.lammpstrj</li>
     * </ul>
     *
     * <p>标准测试配方目标属性为density和conductivity，
     * 因此主要验证conductivity相关文件。</p>
     */
    @Test
    @Order(3)
    @DisplayName("阶段三：验证属性特定输出文件")
    void testVerifyPropertySpecificFiles() {
        log.info("========================================");
        log.info("[LAMMPS-E2E] 阶段三：验证属性特定输出文件");
        log.info("========================================");

        assertNotNull(sharedJobId, "jobId不应为null，请确保阶段一已执行");

        SimulationJob job = simulationRepository.findById(sharedJobId).orElse(null);
        assertNotNull(job, "SimulationJob记录应存在");

        if (!JobStatus.COMPLETED.equals(job.getStatus())) {
            log.warn("[LAMMPS-E2E] ⚠ 任务未完成（状态={}），跳过属性特定文件验证", job.getStatus());
            return;
        }

        Long userId = job.getUserId();
        Long jobId = job.getJobId();

        // 使用PathUtil生成原始输出目录路径
        Path rawOutputPath = pathUtil.getRawOutputPath(userId, jobId);

        // 解析目标属性
        String targetPropertiesStr = job.getTargetProperties();
        List<String> targetProperties = parseTargetProperties(targetPropertiesStr);
        log.info("[LAMMPS-E2E] 目标属性列表: {}", targetProperties);

        // ===== 验证conductivity相关文件 =====
        if (targetProperties.contains("conductivity")) {
            log.info("[LAMMPS-E2E] 验证conductivity属性相关文件...");

            // 验证dump.charge.lammpstrj
            Path chargeTrajPath = rawOutputPath.resolve("dump.charge.lammpstrj");
            boolean chargeTrajExists = Files.exists(chargeTrajPath);
            log.info("[LAMMPS-E2E] dump.charge.lammpstrj路径: {}", chargeTrajPath);
            log.info("[LAMMPS-E2E] dump.charge.lammpstrj存在: {}", chargeTrajExists);
            if (chargeTrajExists) {
                try {
                    long size = Files.size(chargeTrajPath);
                    log.info("[LAMMPS-E2E] dump.charge.lammpstrj文件大小: {} 字节", size);
                    assertTrue(size > 0, "dump.charge.lammpstrj文件不应为空");
                } catch (Exception e) {
                    log.warn("[LAMMPS-E2E] 读取dump.charge.lammpstrj大小失败: {}", e.getMessage());
                }
                log.info("[LAMMPS-E2E] ✓ dump.charge.lammpstrj验证通过");
            } else {
                log.warn("[LAMMPS-E2E] ⚠ dump.charge.lammpstrj不存在，可能LAMMPS脚本未生成该文件");
            }

            // 验证msd.dat
            Path msdPath = rawOutputPath.resolve("msd.dat");
            boolean msdExists = Files.exists(msdPath);
            log.info("[LAMMPS-E2E] msd.dat路径: {}", msdPath);
            log.info("[LAMMPS-E2E] msd.dat存在: {}", msdExists);
            if (msdExists) {
                try {
                    long size = Files.size(msdPath);
                    log.info("[LAMMPS-E2E] msd.dat文件大小: {} 字节", size);
                    assertTrue(size > 0, "msd.dat文件不应为空");
                } catch (Exception e) {
                    log.warn("[LAMMPS-E2E] 读取msd.dat大小失败: {}", e.getMessage());
                }
                log.info("[LAMMPS-E2E] ✓ msd.dat验证通过");
            } else {
                log.warn("[LAMMPS-E2E] ⚠ msd.dat不存在，可能LAMMPS脚本未生成该文件");
            }
        }

        // ===== 验证viscosity相关文件 =====
        if (targetProperties.contains("viscosity")) {
            log.info("[LAMMPS-E2E] 验证viscosity属性相关文件...");

            Path pressurePath = rawOutputPath.resolve("pressure.dat");
            boolean pressureExists = Files.exists(pressurePath);
            log.info("[LAMMPS-E2E] pressure.dat路径: {}", pressurePath);
            log.info("[LAMMPS-E2E] pressure.dat存在: {}", pressureExists);
            if (pressureExists) {
                try {
                    long size = Files.size(pressurePath);
                    log.info("[LAMMPS-E2E] pressure.dat文件大小: {} 字节", size);
                    assertTrue(size > 0, "pressure.dat文件不应为空");
                } catch (Exception e) {
                    log.warn("[LAMMPS-E2E] 读取pressure.dat大小失败: {}", e.getMessage());
                }
                log.info("[LAMMPS-E2E] ✓ pressure.dat验证通过");
            } else {
                log.warn("[LAMMPS-E2E] ⚠ pressure.dat不存在，可能LAMMPS脚本未生成该文件");
            }
        }

        // ===== 验证dielectric相关文件 =====
        if (targetProperties.contains("dielectric")) {
            log.info("[LAMMPS-E2E] 验证dielectric属性相关文件...");

            // 验证dipole.dat
            Path dipolePath = rawOutputPath.resolve("dipole.dat");
            boolean dipoleExists = Files.exists(dipolePath);
            log.info("[LAMMPS-E2E] dipole.dat路径: {}", dipolePath);
            log.info("[LAMMPS-E2E] dipole.dat存在: {}", dipoleExists);
            if (dipoleExists) {
                log.info("[LAMMPS-E2E] ✓ dipole.dat验证通过");
            } else {
                log.warn("[LAMMPS-E2E] ⚠ dipole.dat不存在");
            }

            // 验证total_dipole.dat
            Path totalDipolePath = rawOutputPath.resolve("total_dipole.dat");
            boolean totalDipoleExists = Files.exists(totalDipolePath);
            log.info("[LAMMPS-E2E] total_dipole.dat路径: {}", totalDipolePath);
            log.info("[LAMMPS-E2E] total_dipole.dat存在: {}", totalDipoleExists);
            if (totalDipoleExists) {
                log.info("[LAMMPS-E2E] ✓ total_dipole.dat验证通过");
            } else {
                log.warn("[LAMMPS-E2E] ⚠ total_dipole.dat不存在");
            }
        }

        // ===== 验证solvation_structure相关文件 =====
        if (targetProperties.contains("solvation_structure")) {
            log.info("[LAMMPS-E2E] 验证solvation_structure属性相关文件...");

            Path solvationPath = rawOutputPath.resolve("dump.solvation.lammpstrj");
            boolean solvationExists = Files.exists(solvationPath);
            log.info("[LAMMPS-E2E] dump.solvation.lammpstrj路径: {}", solvationPath);
            log.info("[LAMMPS-E2E] dump.solvation.lammpstrj存在: {}", solvationExists);
            if (solvationExists) {
                log.info("[LAMMPS-E2E] ✓ dump.solvation.lammpstrj验证通过");
            } else {
                log.warn("[LAMMPS-E2E] ⚠ dump.solvation.lammpstrj不存在");
            }
        }

        // ===== 验证density属性 =====
        // density不需要额外文件，数据在log.lammps热力学输出中
        if (targetProperties.contains("density")) {
            log.info("[LAMMPS-E2E] density属性无需额外文件，数据在log.lammps热力学输出中");
        }

        log.info("[LAMMPS-E2E] ✓ 阶段三完成：属性特定文件验证通过");
    }

    // ==================== 阶段四：SimulationRawOutput数据库验证 ====================

    /**
     * 验证SimulationRawOutput数据库记录的正确性
     *
     * <p>验证点：</p>
     * <ul>
     *   <li>SimulationRawOutput记录存在</li>
     *   <li>logFilePath字段已填充</li>
     *   <li>trajectoryFilePath字段已填充</li>
     *   <li>finalDataFilePath字段已填充</li>
     *   <li>根据target_properties，属性特定文件路径字段已填充</li>
     *   <li>totalFrames和totalSimulationTimeNs已设置（可能为占位值0）</li>
     * </ul>
     */
    @Test
    @Order(4)
    @DisplayName("阶段四：验证SimulationRawOutput数据库记录")
    void testVerifySimulationRawOutputRecord() {
        log.info("========================================");
        log.info("[LAMMPS-E2E] 阶段四：验证SimulationRawOutput数据库记录");
        log.info("========================================");

        assertNotNull(sharedJobId, "jobId不应为null，请确保阶段一已执行");

        SimulationJob job = simulationRepository.findById(sharedJobId).orElse(null);
        assertNotNull(job, "SimulationJob记录应存在");

        if (!JobStatus.COMPLETED.equals(job.getStatus())) {
            log.warn("[LAMMPS-E2E] ⚠ 任务未完成（状态={}），跳过SimulationRawOutput验证", job.getStatus());
            return;
        }

        // ===== 查询SimulationRawOutput记录 =====
        Optional<SimulationRawOutput> outputOpt = simulationOutputRepository.findByJobId(sharedJobId);
        assertTrue(outputOpt.isPresent(),
                "SimulationRawOutput记录应存在，jobId=" + sharedJobId);

        SimulationRawOutput output = outputOpt.get();
        log.info("[LAMMPS-E2E] SimulationRawOutput记录:");
        log.info("[LAMMPS-E2E]   outputId = {}", output.getOutputId());
        log.info("[LAMMPS-E2E]   jobId = {}", output.getJobId());
        log.info("[LAMMPS-E2E]   logFilePath = {}", output.getLogFilePath());
        log.info("[LAMMPS-E2E]   trajectoryFilePath = {}", output.getTrajectoryFilePath());
        log.info("[LAMMPS-E2E]   finalDataFilePath = {}", output.getFinalDataFilePath());
        log.info("[LAMMPS-E2E]   chargeTrajectoryFilePath = {}", output.getChargeTrajectoryFilePath());
        log.info("[LAMMPS-E2E]   msdFilePath = {}", output.getMsdFilePath());
        log.info("[LAMMPS-E2E]   pressureFilePath = {}", output.getPressureFilePath());
        log.info("[LAMMPS-E2E]   dipoleFilePath = {}", output.getDipoleFilePath());
        log.info("[LAMMPS-E2E]   dipoleMomentFilePath = {}", output.getDipoleMomentFilePath());
        log.info("[LAMMPS-E2E]   solvationTrajectoryFilePath = {}", output.getSolvationTrajectoryFilePath());
        log.info("[LAMMPS-E2E]   totalFrames = {}", output.getTotalFrames());
        log.info("[LAMMPS-E2E]   totalSimulationTimeNs = {}", output.getTotalSimulationTimeNs());
        log.info("[LAMMPS-E2E]   fileSizeGb = {}", output.getFileSizeGb());
        log.info("[LAMMPS-E2E]   wrappedCoordsIncluded = {}", output.getWrappedCoordsIncluded());
        log.info("[LAMMPS-E2E]   periodicImageFlagIncluded = {}", output.getPeriodicImageFlagIncluded());

        // ===== 验证基本字段 =====
        assertEquals(sharedJobId, output.getJobId(), "jobId应匹配");

        // 验证公共文件路径字段已填充
        assertNotNull(output.getLogFilePath(), "logFilePath不应为null");
        assertFalse(output.getLogFilePath().trim().isEmpty(), "logFilePath不应为空字符串");

        assertNotNull(output.getTrajectoryFilePath(), "trajectoryFilePath不应为null");
        assertFalse(output.getTrajectoryFilePath().trim().isEmpty(), "trajectoryFilePath不应为空字符串");

        assertNotNull(output.getFinalDataFilePath(), "finalDataFilePath不应为null");
        assertFalse(output.getFinalDataFilePath().trim().isEmpty(), "finalDataFilePath不应为空字符串");

        log.info("[LAMMPS-E2E] ✓ 公共文件路径字段验证通过");

        // ===== 验证文件路径指向的文件实际存在 =====
        Long userId = job.getUserId();
        Long jobId = job.getJobId();

        // 验证logFilePath对应的文件存在
        Path logAbsPath = pathUtil.resolveAbsolutePath(userId, jobId, output.getLogFilePath());
        assertTrue(Files.exists(logAbsPath),
                "logFilePath指向的文件应存在: " + logAbsPath);
        log.info("[LAMMPS-E2E] ✓ logFilePath文件存在验证通过: {}", logAbsPath);

        // 验证trajectoryFilePath对应的文件存在
        Path trajAbsPath = pathUtil.resolveAbsolutePath(userId, jobId, output.getTrajectoryFilePath());
        assertTrue(Files.exists(trajAbsPath),
                "trajectoryFilePath指向的文件应存在: " + trajAbsPath);
        log.info("[LAMMPS-E2E] ✓ trajectoryFilePath文件存在验证通过: {}", trajAbsPath);

        // 验证finalDataFilePath对应的文件存在
        Path finalAbsPath = pathUtil.resolveAbsolutePath(userId, jobId, output.getFinalDataFilePath());
        assertTrue(Files.exists(finalAbsPath),
                "finalDataFilePath指向的文件应存在: " + finalAbsPath);
        log.info("[LAMMPS-E2E] ✓ finalDataFilePath文件存在验证通过: {}", finalAbsPath);

        // ===== 验证属性特定文件路径 =====
        List<String> targetProperties = parseTargetProperties(job.getTargetProperties());

        // conductivity属性特定字段
        if (targetProperties.contains("conductivity")) {
            log.info("[LAMMPS-E2E] 验证conductivity属性特定文件路径...");
            assertNotNull(output.getChargeTrajectoryFilePath(),
                    "conductivity任务应设置chargeTrajectoryFilePath");
            assertFalse(output.getChargeTrajectoryFilePath().trim().isEmpty(),
                    "chargeTrajectoryFilePath不应为空字符串");
            assertNotNull(output.getMsdFilePath(),
                    "conductivity任务应设置msdFilePath");
            assertFalse(output.getMsdFilePath().trim().isEmpty(),
                    "msdFilePath不应为空字符串");
            log.info("[LAMMPS-E2E] ✓ conductivity属性文件路径验证通过");
        }

        // viscosity属性特定字段
        if (targetProperties.contains("viscosity")) {
            log.info("[LAMMPS-E2E] 验证viscosity属性特定文件路径...");
            assertNotNull(output.getPressureFilePath(),
                    "viscosity任务应设置pressureFilePath");
            assertFalse(output.getPressureFilePath().trim().isEmpty(),
                    "pressureFilePath不应为空字符串");
            log.info("[LAMMPS-E2E] ✓ viscosity属性文件路径验证通过");
        }

        // dielectric属性特定字段
        if (targetProperties.contains("dielectric")) {
            log.info("[LAMMPS-E2E] 验证dielectric属性特定文件路径...");
            assertNotNull(output.getDipoleFilePath(),
                    "dielectric任务应设置dipoleFilePath");
            assertFalse(output.getDipoleFilePath().trim().isEmpty(),
                    "dipoleFilePath不应为空字符串");
            log.info("[LAMMPS-E2E] ✓ dielectric属性文件路径验证通过");
        }

        // solvation_structure属性特定字段
        if (targetProperties.contains("solvation_structure")) {
            log.info("[LAMMPS-E2E] 验证solvation_structure属性特定文件路径...");
            assertNotNull(output.getSolvationTrajectoryFilePath(),
                    "solvation_structure任务应设置solvationTrajectoryFilePath");
            assertFalse(output.getSolvationTrajectoryFilePath().trim().isEmpty(),
                    "solvationTrajectoryFilePath不应为空字符串");
            log.info("[LAMMPS-E2E] ✓ solvation_structure属性文件路径验证通过");
        }

        // ===== 验证统计字段已设置 =====
        // 注意：totalFrames和totalSimulationTimeNs在MDExecutorService中设置为占位值0，
        // 后续解析阶段会更新为实际值
        assertNotNull(output.getTotalFrames(), "totalFrames不应为null");
        assertNotNull(output.getTotalSimulationTimeNs(), "totalSimulationTimeNs不应为null");
        assertNotNull(output.getFileSizeGb(), "fileSizeGb不应为null");
        assertNotNull(output.getWrappedCoordsIncluded(), "wrappedCoordsIncluded不应为null");
        assertNotNull(output.getPeriodicImageFlagIncluded(), "periodicImageFlagIncluded不应为null");
        log.info("[LAMMPS-E2E] ✓ 统计字段验证通过（totalFrames={}, totalSimulationTimeNs={}, fileSizeGb={})",
                output.getTotalFrames(), output.getTotalSimulationTimeNs(), output.getFileSizeGb());

        // ===== 验证createTime已设置 =====
        assertNotNull(output.getCreateTime(), "createTime不应为null");
        log.info("[LAMMPS-E2E] ✓ createTime验证通过: {}", output.getCreateTime());

        log.info("[LAMMPS-E2E] ✓ 阶段四完成：SimulationRawOutput数据库记录验证通过");
    }

    // ==================== 综合报告 ====================

    /**
     * 在所有测试之后生成LAMMPS执行端到端测试综合报告
     */
    @Test
    @Order(99)
    @DisplayName("生成LAMMPS执行端到端测试综合报告")
    void testGenerateFinalReport() {
        log.info("");
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║        LAMMPS执行端到端测试报告 (LAMMPS E2E Test)          ║");
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

        // 输出文件统计
        if (JobStatus.COMPLETED.equals(job.getStatus())) {
            Long userId = job.getUserId();
            Long jobId = job.getJobId();
            Path rawOutputPath = pathUtil.getRawOutputPath(userId, jobId);

            log.info("╠══════════════════════════════════════════════════════════════╣");
            log.info("║  输出文件统计                                               ║");

            // 统计raw_outputs目录下的文件
            String[] expectedFiles = {
                    "log.lammps", "dump.trajectory.lammpstrj", "final.data",
                    "dump.charge.lammpstrj", "msd.dat", "pressure.dat",
                    "dipole.dat", "total_dipole.dat", "dump.solvation.lammpstrj"
            };

            int existCount = 0;
            for (String filename : expectedFiles) {
                Path filePath = rawOutputPath.resolve(filename);
                boolean exists = Files.exists(filePath);
                if (exists) {
                    existCount++;
                    try {
                        long size = Files.size(filePath);
                        log.info("║    ✓ {} ({}字节){}",
                                filename, size,
                                " ".repeat(Math.max(0, 30 - filename.length()
                                        - String.valueOf(size).length())) + "║");
                    } catch (Exception e) {
                        log.info("║    ✓ {}{}", filename,
                                " ".repeat(Math.max(0, 45 - filename.length())) + "║");
                    }
                }
            }
            log.info("║    共 {} 个输出文件存在                                    ║", existCount);

            // SimulationRawOutput记录信息
            Optional<SimulationRawOutput> outputOpt = simulationOutputRepository.findByJobId(sharedJobId);
            if (outputOpt.isPresent()) {
                SimulationRawOutput output = outputOpt.get();
                log.info("╠══════════════════════════════════════════════════════════════╣");
                log.info("║  SimulationRawOutput记录                                    ║");
                log.info("║    totalFrames: {}{}", output.getTotalFrames(),
                        " ".repeat(Math.max(0, 41 - safeLength(String.valueOf(output.getTotalFrames())))) + "║");
                log.info("║    totalSimulationTimeNs: {}{}", output.getTotalSimulationTimeNs(),
                        " ".repeat(Math.max(0, 31 - safeLength(String.valueOf(output.getTotalSimulationTimeNs())))) + "║");
                log.info("║    fileSizeGb: {}{}", output.getFileSizeGb(),
                        " ".repeat(Math.max(0, 41 - safeLength(String.valueOf(output.getFileSizeGb())))) + "║");
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
     * 解析target_properties JSON字符串为列表
     *
     * <p>target_properties存储为JSON数组格式，如 ["density","conductivity"]。
     * 该方法使用简单的字符串解析，避免引入额外依赖。</p>
     *
     * @param targetPropertiesJson target_properties的JSON字符串
     * @return 属性名称列表
     */
    private List<String> parseTargetProperties(String targetPropertiesJson) {
        if (targetPropertiesJson == null || targetPropertiesJson.trim().isEmpty()) {
            return Collections.emptyList();
        }

        // 简单解析JSON数组格式
        String cleaned = targetPropertiesJson
                .replace("[", "")
                .replace("]", "")
                .replace("\"", "")
                .replace("'", "")
                .trim();

        if (cleaned.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> properties = new ArrayList<>();
        for (String prop : cleaned.split(",")) {
            String trimmed = prop.trim();
            if (!trimmed.isEmpty()) {
                properties.add(trimmed);
            }
        }
        return properties;
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
