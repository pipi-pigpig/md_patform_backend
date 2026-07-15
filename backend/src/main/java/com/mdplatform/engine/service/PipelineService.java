package com.mdplatform.engine.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdplatform.common.util.PathUtil;
import com.mdplatform.engine.dto.FormulaRequest;
import com.mdplatform.engine.dto.FullModelingResult;
import com.mdplatform.engine.dto.PipelineProgressDto;
import com.mdplatform.engine.dto.PipelineStepConstants;
import com.mdplatform.engine.dto.PipelineSubmitRequest;
import com.mdplatform.engine.model.JobStatus;
import com.mdplatform.engine.model.ElectrolyteSystem;
import com.mdplatform.engine.model.SimulationInput;
import com.mdplatform.engine.model.SimulationJob;
import com.mdplatform.engine.repository.SimulationInputRepository;
import com.mdplatform.engine.repository.SimulationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * 全流程编排服务
 *
 * <p>该服务是电解液MD计算平台的核心编排层，负责将配方提交到最终后处理结果的
 * 完整流水线串联在一起。它协调MoltemplateService、MDExecutorService、
 * PostProcessingService等子服务，统一管理任务状态转换和进度追踪。</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>提交全流程任务（创建任务记录、输入参数、目录结构、配方序列化）</li>
 *   <li>异步执行完整流水线（建模→模拟→后处理）</li>
 *   <li>查询全流程进度信息</li>
 *   <li>步骤级进度追踪与状态管理</li>
 * </ul>
 *
 * <p>全流程步骤（共11步）：</p>
 * <pre>
 * 步骤0  - 配方序列化
 * 步骤1  - 分子数量计算
 * 步骤2  - 盒子尺寸计算
 * 步骤3  - Packmol分子堆积
 * 步骤4  - system.lt文件生成
 * 步骤5  - 分子模板调取
 * 步骤6  - Moltemplate命令执行
 * 步骤7  - 文件整理输出
 * 步骤8  - Jinja2脚本生成
 * 步骤9  - LAMMPS三阶段模拟
 * 步骤10 - 后处理分析
 * </pre>
 *
 * <p>状态转换流程：</p>
 * <pre>
 * PENDING → MODELING → RUNNING → POST_PROCESSING → COMPLETED
 *                                              ↘ FAILED
 * </pre>
 *
 * <p>依赖方向：PipelineService → MoltemplateService / MDExecutorService / PostProcessingService / SimulationService
 * 禁止反向依赖，避免循环依赖问题。</p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Service
@Slf4j
public class PipelineService {

    /** Moltemplate建模服务，负责执行完整建模流程（步骤0-9） */
    private final MoltemplateService moltemplateService;

    /** MD执行器服务，负责LAMMPS三阶段模拟执行 */
    private final MDExecutorService mdExecutorService;

    /** 后处理服务，负责执行后处理计算（步骤10） */
    private final PostProcessingService postProcessingService;

    /** 模拟任务服务，提供任务CRUD和状态管理 */
    private final SimulationService simulationService;

    /** 模拟任务仓库，用于直接查询和更新任务记录 */
    private final SimulationRepository simulationRepository;

    /** Docker服务，用于检测GPU可用性等容器操作 */
    private final DockerService dockerService;

    /** 模拟输入参数仓库，用于持久化SimulationInput记录 */
    private final SimulationInputRepository simulationInputRepository;

    /** 路径工具类，用于生成符合规范的文件路径，禁止硬编码路径 */
    private final PathUtil pathUtil;

    /** JSON序列化/反序列化工具 */
    private final ObjectMapper objectMapper;

    /** 电解液系统服务，用于创建和管理电解液配方系统记录 */
    private final SystemService systemService;

    /** Spring管理的线程池，用于异步任务执行 */
    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    /** 模拟执行超时时间（秒），通过配置注入，默认7200秒（2小时） */
    @Value("${md-platform.pipeline.timeout-seconds:7200}")
    private int pipelineTimeoutSeconds;

    /** 默认软件名称 */
    private static final String DEFAULT_SOFTWARE_NAME = "LAMMPS";

    /** 默认软件版本 */
    private static final String DEFAULT_SOFTWARE_VERSION = "2023";

    /** 默认系综类型 */
    private static final String DEFAULT_ENSEMBLE_TYPE = "NPT";

    /** 默认恒温器类型 */
    private static final String DEFAULT_THERMOSTAT_TYPE = "Nose-Hoover";

    /** 默认恒压器类型 */
    private static final String DEFAULT_BAROSTAT_TYPE = "Parrinello-Rahman";

    /** 默认温度(K) */
    private static final double DEFAULT_TEMPERATURE = 300.0;

    /** 默认压强(bar) */
    private static final double DEFAULT_PRESSURE = 1.0;

    /** 默认时间步长(fs) */
    private static final double DEFAULT_TIME_STEP_FS = 1.0;

    /** 默认截断距离(Å) - 使用8.0避免密集Packmol堆积系统中邻居列表溢出导致LAMMPS挂死 */
    private static final double DEFAULT_CUTOFF_DISTANCE_ANG = 8.0;

    /** 默认力场拓扑来源 */
    private static final String DEFAULT_FORCE_FIELD_TOPOLOGY_SOURCE = "OPLS-AA";

    /** 默认输出频率（步） */
    private static final int DEFAULT_OUTPUT_FREQUENCY_STEP = 1000;

    /** 默认初始构型来源 */
    private static final String DEFAULT_INITIAL_CONFIG_SOURCE = "packmol";

    /** 默认初始速度分布方式 */
    private static final String DEFAULT_INITIAL_VELOCITY_DISTRIBUTION = "Maxwell-Boltzmann";

    /**
     * 构造函数，通过依赖注入获取所需服务
     *
     * @param moltemplateService        Moltemplate建模服务实例
     * @param mdExecutorService         MD执行器服务实例
     * @param postProcessingService     后处理服务实例
     * @param simulationService         模拟任务服务实例
     * @param simulationRepository      模拟任务仓库实例
     * @param dockerService             Docker服务实例，用于GPU可用性检测
     * @param simulationInputRepository 模拟输入参数仓库实例
     * @param pathUtil                  路径工具类实例
     * @param objectMapper              JSON序列化工具实例
     */
    public PipelineService(MoltemplateService moltemplateService,
                           MDExecutorService mdExecutorService,
                           PostProcessingService postProcessingService,
                           SimulationService simulationService,
                           SimulationRepository simulationRepository,
                           DockerService dockerService,
                           SimulationInputRepository simulationInputRepository,
                           PathUtil pathUtil,
                           ObjectMapper objectMapper,
                           SystemService systemService) {
        this.moltemplateService = moltemplateService;
        this.mdExecutorService = mdExecutorService;
        this.postProcessingService = postProcessingService;
        this.simulationService = simulationService;
        this.simulationRepository = simulationRepository;
        this.dockerService = dockerService;
        this.simulationInputRepository = simulationInputRepository;
        this.pathUtil = pathUtil;
        this.objectMapper = objectMapper;
        this.systemService = systemService;
    }

    /**
     * 提交全流程计算任务
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>创建SimulationJob记录（状态=PENDING，targetProperties=JSON数组，jobRootPath由PathUtil生成）</li>
     *   <li>创建SimulationInput记录（参数来自PipelineSubmitRequest中的FormulaRequest + 默认值）</li>
     *   <li>通过PathUtil创建任务目录结构</li>
     *   <li>通过MoltemplateService序列化配方到JSON文件</li>
     *   <li>更新任务状态为MODELING</li>
     *   <li>通过ExecutorService提交异步执行</li>
     * </ol>
     *
     * @param userId  用户ID
     * @param request 全流程计算任务提交请求，包含配方参数、目标性质、任务名称等
     * @return 创建的任务ID
     */
    @Transactional
    public Long submitPipelineTask(Long userId, PipelineSubmitRequest request) {
        log.info("[全流程] 提交全流程任务: userId={}, targetProperties={}", userId, request.getTargetProperties());

        // 从PipelineSubmitRequest中提取FormulaRequest和targetProperties
        FormulaRequest formulaRequest = request.getFormula();
        List<String> targetProperties = request.getTargetProperties();

        // 1. 确定目标属性列表：为空时使用默认值["density"]
        List<String> effectiveProperties = targetProperties;
        if (effectiveProperties == null || effectiveProperties.isEmpty()) {
            effectiveProperties = new ArrayList<>();
            effectiveProperties.add("density");
            log.info("[全流程] targetProperties为空，使用默认值: [density]");
        }

        // 2. 先创建ElectrolyteSystem记录（必须先生成systemId，以满足外键约束）
        // 先确定任务名称，用于系统描述
        String jobName = request.getJobName();
        if (jobName == null || jobName.trim().isEmpty()) {
            jobName = buildSystemName(formulaRequest);
        }
        ElectrolyteSystem electrolyteSystem = new ElectrolyteSystem();
        // 生成系统名称：基于溶剂和盐信息自动生成
        String systemName = buildSystemName(formulaRequest);
        electrolyteSystem.setSystemName(systemName);
        electrolyteSystem.setUserId(userId);
        electrolyteSystem.setTaskDescription("全流程自动创建 - " + jobName);
        electrolyteSystem.setTemperature(formulaRequest.getTemperature() != null ? formulaRequest.getTemperature() : 298.15);
        electrolyteSystem.setPressure(1.0);
        electrolyteSystem.setBoundaryConditions("p p p");
        electrolyteSystem.setIsPublicTemplate(false);
        // 序列化溶剂/盐/盒子信息为JSON字符串
        try {
            electrolyteSystem.setSolventInfo(objectMapper.writeValueAsString(formulaRequest.getSolventInfo()));
        } catch (Exception e) {
            log.warn("[全流程] 序列化solventInfo失败", e);
            electrolyteSystem.setSolventInfo("[]");
        }
        try {
            electrolyteSystem.setSaltInfo(objectMapper.writeValueAsString(formulaRequest.getSaltInfo()));
        } catch (Exception e) {
            log.warn("[全流程] 序列化saltInfo失败", e);
            electrolyteSystem.setSaltInfo("{}");
        }
        // 如果有添加剂信息则序列化
        if (formulaRequest.getAdditiveInfo() != null && !formulaRequest.getAdditiveInfo().isEmpty()) {
            try {
                electrolyteSystem.setAdditiveInfo(objectMapper.writeValueAsString(formulaRequest.getAdditiveInfo()));
            } catch (Exception e) {
                log.warn("[全流程] 序列化additiveInfo失败", e);
            }
        }
        try {
            electrolyteSystem.setBoxSize(objectMapper.writeValueAsString(formulaRequest.getBoxSize()));
        } catch (Exception e) {
            log.warn("[全流程] 序列化boxSize失败", e);
            electrolyteSystem.setBoxSize("{}");
        }
        ElectrolyteSystem createdSystem = systemService.createSystem(electrolyteSystem);
        Long systemId = createdSystem.getSystemId();
        log.info("[全流程] ElectrolyteSystem创建成功: systemId={}, systemName={}", systemId, systemName);

        // 3. 创建SimulationJob记录
        SimulationJob job = new SimulationJob();
        job.setJobName(jobName);
        job.setUserId(userId);
        job.setSystemId(systemId); // 使用刚创建的电解液系统ID（满足外键约束）
        job.setSoftwareName(DEFAULT_SOFTWARE_NAME);
        job.setSoftwareVersion(DEFAULT_SOFTWARE_VERSION);
        job.setStatus(JobStatus.PENDING);
        // 硬件类型：优先使用请求中的hardwareUsed，否则使用默认值AUTO（运行时根据GPU检测结果确定）
        job.setHardwareUsed(request.getHardwareUsed() != null ? request.getHardwareUsed() : "AUTO");
        job.setCpuCores("8");
        job.setHardwareEnvironment("CPU 8核");
        job.setRandomSeed(new Random().nextInt(100000));

        // 设置临时jobRootPath占位值（jobRootPath为NOT NULL字段，需要在首次保存前设置）
        // 首次保存获取jobId后，会立即更新为PathUtil生成的正确路径
        job.setJobRootPath("pending");

        // 序列化targetProperties为JSON数组格式
        try {
            String targetPropertiesJson = objectMapper.writeValueAsString(effectiveProperties);
            job.setTargetProperties(targetPropertiesJson);
        } catch (Exception e) {
            log.error("[全流程] 序列化targetProperties失败", e);
            job.setTargetProperties("[\"density\"]");
        }

        // 先保存以获取jobId
        SimulationJob savedJob = simulationService.createSimulation(job);
        Long jobId = savedJob.getJobId();

        // 使用PathUtil生成jobRootPath（相对路径）
        Path jobRootPath = pathUtil.getJobRootPath(userId, jobId);
        String relativeJobRootPath = pathUtil.getRelativePath(userId, jobId, jobRootPath);
        savedJob.setJobRootPath(relativeJobRootPath);
        savedJob = simulationRepository.save(savedJob);

        log.info("[全流程] SimulationJob创建成功: jobId={}, jobRootPath={}", jobId, relativeJobRootPath);

        // 3. 创建SimulationInput记录
        createSimulationInput(jobId, formulaRequest, effectiveProperties, request.getTemperature());

        // 4. 创建任务目录结构
        pathUtil.createJobDirectories(userId, jobId);
        log.info("[全流程] 任务目录结构创建完成: userId={}, jobId={}", userId, jobId);

        // 5. 序列化配方到JSON文件（步骤0）
        try {
            Path formulaFile = moltemplateService.serializeFormulaToJSON(userId, jobId, formulaRequest);
            log.info("[全流程] 配方JSON文件已保存: {}", formulaFile);
        } catch (IOException e) {
            log.error("[全流程] 配方序列化失败: userId={}, jobId={}", userId, jobId, e);
            // 标记任务为失败
            savedJob.setStatus(JobStatus.FAILED);
            savedJob.setErrorMessage("配方序列化失败: " + e.getMessage());
            simulationRepository.save(savedJob);
            return jobId;
        }

        // 6. 初始化进度信息
        updateProgress(jobId, 0, Collections.emptyList());

        // 7. 更新任务状态为MODELING
        simulationService.updateSimulationStatus(jobId, JobStatus.MODELING);
        log.info("[全流程] 任务状态已更新为MODELING: jobId={}", jobId);

        // 8. 提交异步执行（使用Spring管理的线程池）
        final Long finalJobId = jobId;
        final Long finalUserId = userId;
        CompletableFuture.runAsync(() -> executeFullPipeline(finalUserId, finalJobId), taskExecutor)
                .exceptionally(ex -> {
                    log.error("[全流程] 异步执行异常: jobId={}, error={}", finalJobId, ex.getMessage(), ex);
                    return null;
                });

        log.info("[全流程] 全流程任务已提交: jobId={}, userId={}", jobId, userId);
        return jobId;
    }

    /**
     * 执行完整流水线（私有方法，异步运行）
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>步骤0-9：调用MoltemplateService.executeFullModeling()完成建模和LAMMPS模拟</li>
     *   <li>步骤10：调用PostProcessingService.executePostProcessing()执行后处理分析</li>
     * </ol>
     *
     * <p>注意：MoltemplateService.executeFullModeling()内部已包含LAMMPS三阶段模拟执行
     * （步骤9），因此PipelineService无需单独调用MDExecutorService.executeSimulation()，
     * 避免LAMMPS重复执行。如需将LAMMPS执行从executeFullModeling()中解耦，
     * 应先重构MoltemplateService，再在PipelineService中单独调用MDExecutorService。</p>
     *
     * <p>状态转换：</p>
     * <ul>
     *   <li>MODELING → 执行建模流程（步骤0-8）</li>
     *   <li>RUNNING → LAMMPS模拟执行（步骤9，由executeFullModeling内部处理）</li>
     *   <li>POST_PROCESSING → 后处理分析（步骤10）</li>
     *   <li>COMPLETED → 全部完成</li>
     *   <li>FAILED → 任何步骤失败</li>
     * </ul>
     *
     * @param userId 用户ID
     * @param jobId  任务ID
     */
    private void executeFullPipeline(Long userId, Long jobId) {
        log.info("[全流程] 开始执行完整流水线: userId={}, jobId={}", userId, jobId);

        try {
            // ========== 步骤0：配方序列化（已在submitPipelineTask中完成） ==========
            updateProgress(jobId, 0, Collections.singletonList(0));
            log.info("[全流程] 步骤0完成：配方序列化");

            // ========== 步骤1-9：调用MoltemplateService执行完整建模流程 ==========
            // executeFullModeling()内部包含：分子数量计算、盒子尺寸计算、Packmol堆积、
            // system.lt文件生成、分子模板调取、Moltemplate命令执行、文件整理输出、
            // Jinja2脚本生成、LAMMPS三阶段模拟
            log.info("[全流程] 开始执行建模流程（步骤1-9）: userId={}, jobId={}", userId, jobId);

            // 构建配方文件路径（已在submitPipelineTask中序列化）
            Path inputPath = pathUtil.getInputPath(userId, jobId);
            Path formulaFile = inputPath.resolve("formula_config.json");
            String formulaFilePath = formulaFile.toString();

            FullModelingResult modelingResult = moltemplateService.executeFullModeling(
                    userId, jobId, formulaFilePath);

            // 检查建模结果
            if (modelingResult == null || !Boolean.TRUE.equals(modelingResult.getSuccess())) {
                String errorMsg = modelingResult != null ? modelingResult.getErrorMessage() : "建模流程返回空结果";
                String failedStep = modelingResult != null ? modelingResult.getFailedStep() : "未知步骤";
                log.error("[全流程] 建模流程失败: jobId={}, 失败步骤={}, 错误={}", jobId, failedStep, errorMsg);

                // 更新进度，并显式设置 FAILED 状态
                // (H2环境下 executeFullModeling 内部的 @Modifying 状态更新可能不生效)
                updateProgress(jobId, PipelineStepConstants.TOTAL_STEPS - 1,
                        getCompletedStepsList(modelingResult));
                updateJobAsFailed(jobId, "建模流程失败[" + failedStep + "]: " + errorMsg);
                return;
            }

            log.info("[全流程] 建模流程完成: jobId={}, 总原子数={}, 总耗时={}秒",
                    jobId, modelingResult.getTotalAtoms(), modelingResult.getTotalElapsedTimeSeconds());

            // 更新进度：步骤0-9已完成
            List<Integer> completedSteps = new ArrayList<>();
            for (int i = 0; i <= 9; i++) {
                completedSteps.add(i);
            }
            updateProgress(jobId, 9, completedSteps);

            // 根据实际GPU检测结果更新硬件使用记录
            // LAMMPS执行完成后，根据运行时GPU检测结果将hardwareUsed从AUTO更新为实际的GPU或CPU
            String actualHardware = dockerService.isGPUAvailable() ? "GPU" : "CPU";
            SimulationJob jobForHardware = simulationRepository.findById(jobId).orElse(null);
            if (jobForHardware != null) {
                jobForHardware.setHardwareUsed(actualHardware);
                simulationRepository.save(jobForHardware);
                log.info("[全流程] 硬件使用记录已更新: jobId={}, hardwareUsed={}", jobId, actualHardware);
            }

            // ========== 步骤10：后处理分析 ==========
            log.info("[全流程] 开始执行后处理分析（步骤10）: jobId={}", jobId);

            // 更新任务状态为POST_PROCESSING
            // 注意：executeFullModeling()可能已将状态设为COMPLETED，此处覆盖为POST_PROCESSING
            simulationService.updateSimulationStatus(jobId, JobStatus.POST_PROCESSING);
            log.info("[全流程] 任务状态已更新为POST_PROCESSING: jobId={}", jobId);

            // 调用PostProcessingService执行后处理（autoTriggered=true，跳过COMPLETED状态检查）
            CompletableFuture<List<Long>> postProcessingFuture =
                    postProcessingService.executePostProcessing(jobId, true);

            // 等待后处理完成
            List<Long> resultIds = postProcessingFuture.get(pipelineTimeoutSeconds, java.util.concurrent.TimeUnit.SECONDS);

            if (resultIds == null || resultIds.isEmpty()) {
                log.warn("[全流程] 后处理未生成有效结果: jobId={}, resultIds={}", jobId, resultIds);
            } else {
                log.info("[全流程] 后处理完成: jobId={}, 结果数量={}", jobId, resultIds.size());
            }

            // ========== 全流程完成 ==========
            // 更新进度：所有步骤已完成
            List<Integer> allCompletedSteps = new ArrayList<>();
            for (int i = 0; i < PipelineStepConstants.TOTAL_STEPS; i++) {
                allCompletedSteps.add(i);
            }
            updateProgress(jobId, PipelineStepConstants.TOTAL_STEPS - 1, allCompletedSteps);

            // 更新任务状态为COMPLETED（使用findById+save确保H2兼容性）
            LocalDateTime endTime = LocalDateTime.now();
            SimulationJob completedJob = simulationRepository.findById(jobId).orElse(null);
            if (completedJob != null) {
                completedJob.setStatus(JobStatus.COMPLETED);
                completedJob.setEndTime(endTime);
                if (completedJob.getStartTime() != null) {
                    completedJob.setExecutionTimeS(
                            java.time.Duration.between(completedJob.getStartTime(), endTime).getSeconds());
                }
                simulationRepository.save(completedJob);
            }
            log.info("[全流程] 完整流水线执行成功: userId={}, jobId={}", userId, jobId);

        } catch (java.util.concurrent.TimeoutException e) {
            log.error("[全流程] 流水线执行超时: jobId={}, 超时时间={}秒", jobId, pipelineTimeoutSeconds);
            updateJobAsFailed(jobId, "流水线执行超时，超时时间: " + pipelineTimeoutSeconds + "秒");
        } catch (java.util.concurrent.ExecutionException e) {
            log.error("[全流程] 流水线执行异常: jobId={}, error={}", jobId, e.getMessage(), e);
            updateJobAsFailed(jobId, "流水线执行异常: " + e.getCause().getMessage());
        } catch (InterruptedException e) {
            log.error("[全流程] 流水线执行被中断: jobId={}", jobId, e);
            Thread.currentThread().interrupt();
            updateJobAsFailed(jobId, "流水线执行被中断: " + e.getMessage());
        } catch (Exception e) {
            log.error("[全流程] 流水线执行未知异常: jobId={}, error={}", jobId, e.getMessage(), e);
            updateJobAsFailed(jobId, "流水线执行未知异常: " + e.getMessage());
        }
    }

    /**
     * 获取全流程进度信息
     *
     * <p>从数据库读取SimulationJob记录，解析resultSummary中的步骤进度JSON，
     * 构建PipelineProgressDto返回给前端。</p>
     *
     * @param jobId 任务ID
     * @return 全流程进度信息DTO
     */
    public PipelineProgressDto getPipelineProgress(Long jobId) {
        log.debug("[全流程] 查询进度: jobId={}", jobId);

        SimulationJob job = simulationRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("[全流程] 任务不存在: jobId={}", jobId);
            return PipelineProgressDto.builder()
                    .jobId(jobId)
                    .status("NOT_FOUND")
                    .build();
        }

        // 解析resultSummary中的步骤进度JSON
        int currentStep = 0;
        String stepName = PipelineStepConstants.getStepName(0);
        List<Integer> completedSteps = Collections.emptyList();
        int totalSteps = PipelineStepConstants.TOTAL_STEPS;

        if (job.getResultSummary() != null && !job.getResultSummary().isEmpty()) {
            try {
                Map<String, Object> progressMap = objectMapper.readValue(
                        job.getResultSummary(),
                        new TypeReference<Map<String, Object>>() {});

                if (progressMap.containsKey("currentStep")) {
                    currentStep = ((Number) progressMap.get("currentStep")).intValue();
                }
                if (progressMap.containsKey("stepName")) {
                    stepName = (String) progressMap.get("stepName");
                } else {
                    stepName = PipelineStepConstants.getStepName(currentStep);
                }
                if (progressMap.containsKey("completedSteps")) {
                    @SuppressWarnings("unchecked")
                    List<Integer> steps = (List<Integer>) progressMap.get("completedSteps");
                    completedSteps = steps;
                }
                if (progressMap.containsKey("totalSteps")) {
                    totalSteps = ((Number) progressMap.get("totalSteps")).intValue();
                }
            } catch (Exception e) {
                log.warn("[全流程] 解析进度JSON失败: jobId={}, resultSummary={}", jobId, job.getResultSummary(), e);
                stepName = PipelineStepConstants.getStepName(0);
            }
        }

        // 计算进度百分比
        int progressPercent = PipelineStepConstants.calculateProgressPercent(completedSteps.size());

        // 如果任务已完成，进度应为100%
        if (JobStatus.COMPLETED.equals(job.getStatus()) || JobStatus.POST_PROCESSING_COMPLETED.equals(job.getStatus())) {
            progressPercent = 100;
            currentStep = PipelineStepConstants.TOTAL_STEPS - 1;
            stepName = PipelineStepConstants.getStepName(PipelineStepConstants.TOTAL_STEPS - 1);
            completedSteps = new ArrayList<>();
            for (int i = 0; i < PipelineStepConstants.TOTAL_STEPS; i++) {
                completedSteps.add(i);
            }
        }

        // 如果任务失败，保持当前进度
        if (JobStatus.FAILED.equals(job.getStatus())) {
            stepName = stepName + " (失败)";
        }

        return PipelineProgressDto.builder()
                .jobId(jobId)
                .userId(job.getUserId())
                .status(job.getStatus())
                .currentStep(currentStep)
                .stepName(stepName)
                .completedSteps(completedSteps)
                .totalSteps(totalSteps)
                .progressPercent(progressPercent)
                .errorMessage(job.getErrorMessage())
                .startTime(job.getStartTime())
                .createTime(job.getCreateTime())
                .build();
    }

    /**
     * 更新步骤级进度到resultSummary
     *
     * <p>构建进度JSON并更新SimulationJob.resultSummary字段，格式如下：</p>
     * <pre>
     * {
     *   "currentStep": 5,
     *   "stepName": "Moltemplate命令执行",
     *   "completedSteps": [0, 1, 2, 3, 4],
     *   "totalSteps": 11
     * }
     * </pre>
     *
     * @param jobId          任务ID
     * @param currentStep    当前步骤编号
     * @param completedSteps 已完成步骤编号列表
     */
    private void updateProgress(Long jobId, int currentStep, List<Integer> completedSteps) {
        try {
            Map<String, Object> progressMap = new LinkedHashMap<>();
            progressMap.put("currentStep", currentStep);
            progressMap.put("stepName", PipelineStepConstants.getStepName(currentStep));
            progressMap.put("completedSteps", completedSteps);
            progressMap.put("totalSteps", PipelineStepConstants.TOTAL_STEPS);

            String progressJson = objectMapper.writeValueAsString(progressMap);

            // 使用findById+save而非@Modifying UPDATE，避免H2 JSON列双重转义问题
            SimulationJob job = simulationRepository.findById(jobId).orElse(null);
            if (job != null) {
                job.setResultSummary(progressJson);
                simulationRepository.save(job);
            }
            log.debug("[全流程] 进度更新: jobId={}, currentStep={}, stepName={}, completedSteps={}",
                    jobId, currentStep, PipelineStepConstants.getStepName(currentStep), completedSteps);
        } catch (Exception e) {
            log.error("[全流程] 更新进度失败: jobId={}, currentStep={}", jobId, currentStep, e);
        }
    }

    /**
     * 创建模拟输入参数记录
     *
     * <p>根据FormulaRequest中的参数和默认值创建SimulationInput记录。</p>
     *
     * <p>默认值配置：</p>
     * <ul>
     *   <li>temperature: 300.0 K（优先使用PipelineSubmitRequest中的温度，其次使用FormulaRequest中的温度）</li>
     *   <li>pressure: 1.0 bar</li>
     *   <li>timeStepFs: 1.0 fs</li>
     *   <li>cutoffDistanceAng: 12.0 Å</li>
     *   <li>ensembleType: NPT</li>
     *   <li>thermostatType: Nose-Hoover</li>
     *   <li>barostatType: Parrinello-Rahman</li>
     * </ul>
     *
     * @param jobId              任务ID
     * @param formulaRequest     配方请求对象
     * @param targetProperties   目标计算属性列表
     * @param overrideTemperature 覆盖温度（来自PipelineSubmitRequest，可为null）
     * @return 创建的SimulationInput对象
     */
    private SimulationInput createSimulationInput(Long jobId, FormulaRequest formulaRequest,
                                                  List<String> targetProperties, Double overrideTemperature) {
        log.info("[全流程] 创建SimulationInput: jobId={}", jobId);

        SimulationInput input = new SimulationInput();
        input.setJobId(jobId);

        // 系综与温压控制参数
        input.setEnsembleType(DEFAULT_ENSEMBLE_TYPE);
        input.setThermostatType(DEFAULT_THERMOSTAT_TYPE);
        input.setBarostatType(DEFAULT_BAROSTAT_TYPE);

        // 温度：优先使用PipelineSubmitRequest中的温度，其次使用FormulaRequest中的温度，最后使用默认值
        Double temperature = overrideTemperature;
        if (temperature == null && formulaRequest.getTemperature() != null) {
            temperature = formulaRequest.getTemperature();
        }
        if (temperature == null) {
            temperature = DEFAULT_TEMPERATURE;
        }
        input.setTemperature(temperature);

        // 压力
        input.setPressure(DEFAULT_PRESSURE);

        // 时间步长与截断距离
        input.setTimeStepFs(DEFAULT_TIME_STEP_FS);
        input.setCutoffDistanceAng(DEFAULT_CUTOFF_DISTANCE_ANG);

        // 积分算法与长程静电（使用SimulationInput中的默认值）
        input.setIntegrationAlgorithm("Velocity-Verlet");
        input.setLongRangeElectrostatics("PPPM, accuracy 1.0e-4");

        // 力场拓扑相关
        input.setForceFieldTopologySource(DEFAULT_FORCE_FIELD_TOPOLOGY_SOURCE);

        // 输出控制
        input.setOutputFrequencyStep(DEFAULT_OUTPUT_FREQUENCY_STEP);

        // 能量最小化参数
        input.setMinimizationParams("{}");
        input.setMinimizationForceThreshold(1.0e-4);
        input.setMinimizationMaxSteps(150000L);

        // 平衡模拟参数
        input.setEquilibriumParams("{}");
        input.setEquilibriumDensityStdThreshold(0.01);
        input.setEquilibriumTempFluctuationRange(5.0);
        input.setMinimumEquilibriumTime(5000.0);

        // 生产模拟参数
        input.setProductionParams("{}");
        input.setMinimumProductionTime(50.0);

        // 初始构型与速度分布
        input.setInitialConfigSource(DEFAULT_INITIAL_CONFIG_SOURCE);
        input.setInitialVelocityDistribution(DEFAULT_INITIAL_VELOCITY_DISTRIBUTION);

        // 将targetProperties序列化为JSON存储（用于后续服务读取）
        try {
            String targetPropertiesJson = objectMapper.writeValueAsString(targetProperties);
            input.setMoleculeTopologyTemplates(targetPropertiesJson);
        } catch (Exception e) {
            log.warn("[全流程] 序列化targetProperties到SimulationInput失败", e);
        }

        SimulationInput savedInput = simulationInputRepository.save(input);
        log.info("[全流程] SimulationInput创建成功: jobId={}, inputId={}", jobId, savedInput.getInputId());
        return savedInput;
    }

    /**
     * 将任务标记为失败状态
     *
     * <p>更新任务状态为FAILED，记录错误信息和结束时间。</p>
     *
     * @param jobId       任务ID
     * @param errorMessage 错误信息
     */
    private void updateJobAsFailed(Long jobId, String errorMessage) {
        try {
            // 使用findById+save而非@Modifying UPDATE，确保H2兼容性
            LocalDateTime endTime = LocalDateTime.now();
            SimulationJob job = simulationRepository.findById(jobId).orElse(null);
            if (job != null) {
                job.setStatus(JobStatus.FAILED);
                job.setEndTime(endTime);
                // 截断错误信息至255字符，适配VARCHAR(255)列限制
                String truncatedMsg = errorMessage.length() > 255
                        ? errorMessage.substring(0, 252) + "..."
                        : errorMessage;
                job.setErrorMessage(truncatedMsg);
                if (job.getStartTime() != null) {
                    long executionTimeSeconds = java.time.Duration.between(
                            job.getStartTime(), endTime).getSeconds();
                    job.setExecutionTimeS(executionTimeSeconds);
                }
                simulationRepository.save(job);
                log.info("[全流程] 任务已标记为FAILED: jobId={}, 错误: {}", jobId, errorMessage);
            }
        } catch (Exception e) {
            log.error("[全流程] 标记任务失败状态时异常: jobId={}", jobId, e);
        }
    }

    /**
     * 根据FullModelingResult推断已完成的步骤列表
     *
     * <p>根据建模结果中非空的字段推断哪些步骤已成功完成。</p>
     *
     * @param result 完整建模结果
     * @return 已完成步骤编号列表
     */
    private List<Integer> getCompletedStepsList(FullModelingResult result) {
        List<Integer> completed = new ArrayList<>();
        if (result == null) {
            return completed;
        }

        // 步骤0：配方序列化（始终完成，因为在submitPipelineTask中已执行）
        completed.add(0);

        // 步骤1：分子数量计算
        if (result.getMoleculeCountResult() != null) {
            completed.add(1);
        }

        // 步骤2：盒子尺寸计算
        if (result.getBoxSizeResult() != null) {
            completed.add(2);
        }

        // 步骤3：Packmol分子堆积
        if (result.getPackmolResult() != null) {
            completed.add(3);
        }

        // 步骤4-5：system.lt文件生成 + 分子模板调取
        if (result.getSystemResult() != null) {
            completed.add(4);
            completed.add(5);
        }

        // 步骤6：Moltemplate命令执行
        if (result.getMoltemplateExecutionResult() != null) {
            completed.add(6);
        }

        // 步骤7：文件整理输出
        if (result.getFileOrganizeResult() != null) {
            completed.add(7);
        }

        // 步骤8：Jinja2脚本生成
        if (result.getLammpsScriptResult() != null) {
            completed.add(8);
        }

        // 步骤9：LAMMPS三阶段模拟
        if (result.getLammpsExecutionResult() != null) {
            completed.add(9);
        }

        return completed;
    }

    /**
     * 从配方信息构建电解液系统名称
     *
     * <p>根据溶剂和盐信息自动生成可识别的系统名称，
     * 格式如："1M LiPF6 in EC:DMC(3:7)"。</p>
     *
     * @param formulaRequest 配方请求
     * @return 系统名称字符串
     */
    private String buildSystemName(FormulaRequest formulaRequest) {
        StringBuilder name = new StringBuilder();
        // 盐信息
        if (formulaRequest.getSaltInfo() != null) {
            name.append(formulaRequest.getSaltInfo().getConcentration())
                .append("M ")
                .append(formulaRequest.getSaltInfo().getCation())
                .append(formulaRequest.getSaltInfo().getAnion())
                .append(" in ");
        }
        // 溶剂信息
        List<?> solvents = formulaRequest.getSolventInfo();
        if (solvents != null && !solvents.isEmpty()) {
            for (int i = 0; i < solvents.size(); i++) {
                Object solvent = solvents.get(i);
                // 尝试获取name和moleFraction
                String solventName = "Unknown";
                try {
                    java.lang.reflect.Method getNameMethod = solvent.getClass().getMethod("getName");
                    Object nameVal = getNameMethod.invoke(solvent);
                    if (nameVal != null) solventName = nameVal.toString();
                } catch (Exception e) {
                    // 忽略反射异常
                }
                if (i > 0) name.append(":");
                name.append(solventName);
            }
        }
        // 温度信息
        if (formulaRequest.getTemperature() != null) {
            name.append(" (").append(formulaRequest.getTemperature().intValue()).append("K)");
        }
        return name.toString();
    }
}
