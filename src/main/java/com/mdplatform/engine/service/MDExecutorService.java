package com.mdplatform.engine.service;

import com.mdplatform.common.util.PathUtil;
import com.mdplatform.engine.model.JobStatus;
import com.mdplatform.engine.model.SimulationJob;
import com.mdplatform.engine.model.SimulationRawOutput;
import com.mdplatform.engine.repository.SimulationOutputRepository;
import com.mdplatform.engine.repository.SimulationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.*;
import java.util.concurrent.*;

/**
 * MD执行器服务类
 *
 * <p>提供分子动力学模拟任务的执行功能，包括LAMMPS三阶段模拟编排、
 * 属性相关的输出文件收集、输出记录更新等。</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>通过Docker容器执行LAMMPS三阶段模拟（能量最小化→平衡→生产）</li>
 *   <li>根据目标属性收集对应的输出文件</li>
 *   <li>更新SimulationRawOutput记录，存储文件路径</li>
 *   <li>支持降级执行模式和超时控制</li>
 *   <li>系统状态检查和输入模板生成</li>
 * </ul>
 *
 * @author 电解液MD平台
 * @version 2.0.0
 */
@Service
@Slf4j
public class MDExecutorService {

    /** Docker服务，用于在容器中执行模拟命令 */
    private final DockerService dockerService;

    /** 路径工具类，用于生成符合规范的文件路径，禁止硬编码路径 */
    private final PathUtil pathUtil;

    /** 模拟原始输出仓库，用于查询和持久化SimulationRawOutput记录 */
    private final SimulationOutputRepository simulationOutputRepository;

    /** 模拟任务仓库，用于查询和更新SimulationJob记录 */
    private final SimulationRepository simulationRepository;

    /** LAMMPS模板服务，用于解析target_properties */
    private final LammpsTemplateService lammpsTemplateService;

    /** 模拟超时时间（秒），通过配置注入 */
    @Value("${app.docker.timeout-seconds:3600}")
    private int timeoutSeconds;

    /** 容器内数据根目录路径，通过配置注入避免硬编码 */
    @Value("${app.docker.md-data-path:/workspace/data}")
    private String mdDataPath;

    /** Spring管理的线程池，避免使用Executors.newCachedThreadPool创建无界线程池 */
    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    /**
     * 构造函数，通过依赖注入获取所需服务
     *
     * @param dockerService Docker服务实例
     * @param pathUtil 路径工具类实例
     * @param simulationOutputRepository 模拟原始输出仓库实例
     * @param simulationRepository 模拟任务仓库实例
     * @param lammpsTemplateService LAMMPS模板服务实例
     */
    public MDExecutorService(DockerService dockerService,
                             PathUtil pathUtil,
                             SimulationOutputRepository simulationOutputRepository,
                             SimulationRepository simulationRepository,
                             LammpsTemplateService lammpsTemplateService) {
        this.dockerService = dockerService;
        this.pathUtil = pathUtil;
        this.simulationOutputRepository = simulationOutputRepository;
        this.simulationRepository = simulationRepository;
        this.lammpsTemplateService = lammpsTemplateService;
    }

    /**
     * 异步执行模拟任务（三阶段LAMMPS模拟编排）
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>检查MD容器运行状态，不可用时使用降级模式</li>
     *   <li>使用PathUtil生成所有路径（本地路径和容器路径）</li>
     *   <li>依次执行三阶段LAMMPS模拟：能量最小化→平衡→生产</li>
     *   <li>每阶段完成后验证关键输出文件是否存在</li>
     *   <li>任何阶段失败则停止并标记任务为FAILED</li>
     *   <li>全部阶段完成后，根据target_properties收集输出文件</li>
     *   <li>更新SimulationRawOutput记录</li>
     * </ol>
     *
     * @param job 模拟任务对象
     * @return 异步执行结果，包含模拟输出的JSON字符串
     */
    public CompletableFuture<String> executeSimulation(SimulationJob job) {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                // 检查MD容器是否运行，不可用时直接标记任务失败并抛出异常
                if (!dockerService.isMDContainerRunning()) {
                    String errorMsg = "Docker服务不可用，请检查md-engine容器状态。无法执行LAMMPS模拟。";
                    log.error("[MD执行器] MD容器未运行，无法执行模拟，任务将标记为失败，任务ID: {}", job.getJobId());
                    markJobAsFailed(job, errorMsg);
                    throw new RuntimeException(errorMsg);
                }

                Long userId = job.getUserId();
                Long jobId = job.getJobId();
                String jobIdStr = jobId.toString();

                log.info("[MD执行器] 开始执行三阶段LAMMPS模拟，任务ID: {}, 用户ID: {}", jobId, userId);

                // 使用PathUtil生成所有本地路径，禁止硬编码
                Path localInputPath = pathUtil.getInputPath(userId, jobId);
                Path localRawOutputPath = pathUtil.getRawOutputPath(userId, jobId);

                // 确保输出目录存在
                pathUtil.ensureDirectoryExists(localRawOutputPath);

                // 构建容器内路径，使用PathUtil的convertToDockerPath方法将本地路径转换为Docker容器路径，禁止硬编码
                String containerInputPath = pathUtil.convertToDockerPath(localInputPath);
                String containerOutputPath = pathUtil.convertToDockerPath(localRawOutputPath);

                log.info("[MD执行器] 容器输入目录: {}", containerInputPath);
                log.info("[MD执行器] 容器输出目录: {}", containerOutputPath);

                // 解析目标属性列表
                List<String> targetProperties = lammpsTemplateService.parseTargetProperties(job.getTargetProperties());
                log.info("[MD执行器] 目标属性列表: {}", targetProperties);

                // ========== 阶段1：能量最小化 ==========
                // 使用log.minimization作为日志文件名，避免各阶段日志互相覆盖
                log.info("[MD执行器] 阶段1：执行能量最小化，任务ID: {}", jobId);
                String minimizationResult = dockerService.runLAMMPS(
                        "in.minimization", jobIdStr, containerInputPath, containerOutputPath, "log.minimization");
                if (isLammpsFailed(minimizationResult)) {
                    String errorMsg = "能量最小化阶段失败: " + minimizationResult;
                    log.error("[MD执行器] {}，任务ID: {}", errorMsg, jobId);
                    markJobAsFailed(job, errorMsg);
                    // 抛出异常而非返回失败JSON，确保CompletableFuture异常完成，
                    // 让MoltemplateService的catch块能正确捕获并设置result.setSuccess(false)
                    throw new RuntimeException(errorMsg);
                }
                // 验证能量最小化输出文件
                if (!verifyOutputFileExists(localInputPath, "minimized.data")) {
                    String errorMsg = "能量最小化输出文件不存在: minimized.data";
                    log.error("[MD执行器] {}，任务ID: {}", errorMsg, jobId);
                    markJobAsFailed(job, errorMsg);
                    throw new RuntimeException(errorMsg);
                }
                log.info("[MD执行器] 阶段1完成：能量最小化成功，任务ID: {}", jobId);

                // ========== 阶段2：平衡模拟 ==========
                // 使用log.equilibrium作为日志文件名，避免各阶段日志互相覆盖
                log.info("[MD执行器] 阶段2：执行平衡模拟，任务ID: {}", jobId);
                String equilibriumResult = dockerService.runLAMMPS(
                        "in.equilibrium", jobIdStr, containerInputPath, containerOutputPath, "log.equilibrium");
                if (isLammpsFailed(equilibriumResult)) {
                    String errorMsg = "平衡模拟阶段失败: " + equilibriumResult;
                    log.error("[MD执行器] {}，任务ID: {}", errorMsg, jobId);
                    markJobAsFailed(job, errorMsg);
                    throw new RuntimeException(errorMsg);
                }
                // 验证平衡模拟输出文件
                if (!verifyOutputFileExists(localInputPath, "equilibrated.data")) {
                    String errorMsg = "平衡模拟输出文件不存在: equilibrated.data";
                    log.error("[MD执行器] {}，任务ID: {}", errorMsg, jobId);
                    markJobAsFailed(job, errorMsg);
                    throw new RuntimeException(errorMsg);
                }
                log.info("[MD执行器] 阶段2完成：平衡模拟成功，任务ID: {}", jobId);

                // ========== 阶段3：生产模拟 ==========
                // 使用log.production作为日志文件名，避免各阶段日志互相覆盖
                log.info("[MD执行器] 阶段3：执行生产模拟，任务ID: {}", jobId);
                String productionResult = dockerService.runLAMMPS(
                        "in.production", jobIdStr, containerInputPath, containerOutputPath, "log.production");
                if (isLammpsFailed(productionResult)) {
                    String errorMsg = "生产模拟阶段失败: " + productionResult;
                    log.error("[MD执行器] {}，任务ID: {}", errorMsg, jobId);
                    markJobAsFailed(job, errorMsg);
                    throw new RuntimeException(errorMsg);
                }
                log.info("[MD执行器] 阶段3完成：生产模拟成功，任务ID: {}", jobId);

                // ========== 合并三阶段日志文件 ==========
                // 将log.minimization、log.equilibrium、log.production合并为log.lammps
                // 合并后的log.lammps包含完整的模拟过程日志，便于后续后处理分析
                log.info("[MD执行器] 开始合并三阶段日志文件，任务ID: {}", jobId);
                mergeLammpsLogFiles(containerOutputPath, jobIdStr);
                log.info("[MD执行器] 三阶段日志文件合并完成，任务ID: {}", jobId);

                // ========== 收集输出文件 ==========
                log.info("[MD执行器] 开始收集输出文件，任务ID: {}", jobId);
                Map<String, String> collectedFiles = collectOutputFiles(userId, jobId, targetProperties);
                log.info("[MD执行器] 输出文件收集完成，共收集{}个文件，任务ID: {}", collectedFiles.size(), jobId);

                // ========== 更新原始输出记录 ==========
                log.info("[MD执行器] 更新原始输出记录，任务ID: {}", jobId);
                updateRawOutputRecord(userId, jobId, targetProperties, collectedFiles);

                // 构建结果摘要
                String resultSummary = buildResultSummary(job, collectedFiles, targetProperties);

                log.info("[MD执行器] 三阶段LAMMPS模拟全部完成，任务ID: {}", jobId);
                return resultSummary;

            } catch (Exception e) {
                log.error("[MD执行器] 模拟执行异常，任务ID: {}", job.getJobId(), e);
                markJobAsFailed(job, "模拟执行异常: " + e.getMessage());
                throw new RuntimeException("模拟执行失败: " + e.getMessage(), e);
            }
        }, taskExecutor);

        // 超时控制
        ScheduledExecutorService delayer = Executors.newScheduledThreadPool(1);
        delayer.schedule(() -> {
            if (!future.isDone()) {
                future.completeExceptionally(new TimeoutException("模拟执行超时，超时时间: " + timeoutSeconds + "秒"));
            }
        }, timeoutSeconds, TimeUnit.SECONDS);

        future.whenComplete((res, ex) -> delayer.shutdown());

        return future;
    }

    /**
     * 合并三阶段LAMMPS日志文件为统一的log.lammps
     *
     * <p>在容器内执行cat命令，将能量最小化、平衡、生产三个阶段的日志文件
     * 合并为一个完整的log.lammps文件，便于后续后处理分析。</p>
     *
     * <p>合并顺序：log.minimization → log.equilibrium → log.production</p>
     *
     * <p>安全措施：在执行合并命令前，先验证路径参数的安全性，
     * 防止命令注入攻击。</p>
     *
     * @param containerOutputDir 容器内输出目录路径
     * @param jobIdStr 任务ID字符串，用于日志标识
     */
    private void mergeLammpsLogFiles(String containerOutputDir, String jobIdStr) {
        try {
            // 验证路径安全性，防止命令注入攻击
            // 虽然当前路径由PathUtil生成（仅包含数字和下划线），风险较低，
            // 但添加验证可以防止未来代码变更引入特殊字符
            if (!dockerService.isPathSafe(containerOutputDir)) {
                log.error("[MD执行器] 路径验证失败，可能存在命令注入风险，任务ID: {}, 路径: '{}'",
                        jobIdStr, containerOutputDir);
                // 路径验证失败时，不执行合并命令，避免潜在的安全风险
                return;
            }

            // 在容器内使用cat命令合并三个阶段的日志文件
            // 合并顺序：能量最小化 → 平衡 → 生产，保持时间顺序
            // 为每个路径添加双引号，防止路径包含空格或特殊字符导致命令解析失败，同时避免命令注入风险
            List<String> mergeCmd = Arrays.asList(
                    "bash", "-c",
                    "cat \"" + containerOutputDir + "/log.minimization\" " +
                            "\"" + containerOutputDir + "/log.equilibrium\" " +
                            "\"" + containerOutputDir + "/log.production\"" +
                            " > \"" + containerOutputDir + "/log.lammps\""
            );
            log.info("[MD执行器] 执行日志合并命令，任务ID: {}", jobIdStr);
            String mergeResult = dockerService.executeCommandInContainer(
                    dockerService.getContainerName(), mergeCmd, "/workspace");

            // 使用isLammpsFailed进行精确的失败检测，避免正常输出中的"failed"字符串被误判
            if (isLammpsFailed(mergeResult)) {
                log.warn("[MD执行器] 日志合并命令可能失败，任务ID: {}, 结果: {}", jobIdStr, mergeResult);
            } else {
                log.info("[MD执行器] 日志合并命令执行成功，任务ID: {}", jobIdStr);
            }
        } catch (Exception e) {
            // 日志合并不影响主流程，仅记录警告
            log.warn("[MD执行器] 合并日志文件时发生异常，任务ID: {}, 错误: {}", jobIdStr, e.getMessage());
        }
    }

    /**
     * 判断LAMMPS执行结果是否表示失败
     *
     * <p>采用精确的失败检测逻辑，避免LAMMPS正常输出中的"failed"字符串
     * （如GPU初始化失败但回退到CPU的提示）被误判为执行失败。</p>
     *
     * <p>检测逻辑：</p>
     * <ol>
     *   <li>结果为null时判定为失败（Docker命令未返回任何输出）</li>
     *   <li>检查Docker命令执行失败标记："Command failed with exit code" 或 "Failed to execute command"</li>
     *   <li>检查LAMMPS致命错误标记：以"ERROR:"或"ERROR on proc"开头的行，
     *       这是LAMMPS的标准错误格式，按行检查避免误判包含"ERROR"的正常输出</li>
     * </ol>
     *
     * @param result LAMMPS执行结果
     * @return true表示执行失败，false表示可能成功
     */
    private boolean isLammpsFailed(String result) {
        if (result == null) {
            return true;
        }
        // 检查Docker命令执行失败标记
        if (result.contains("Command failed with exit code") || result.contains("Failed to execute command")) {
            return true;
        }
        // 检查LAMMPS致命错误标记（LAMMPS标准错误格式以"ERROR:"开头）
        // 使用按行检查避免误判包含"ERROR"的正常输出
        for (String line : result.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("ERROR:") || trimmed.startsWith("ERROR on proc")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 验证输出文件是否存在
     *
     * <p>LAMMPS在容器中运行时，输出文件默认写入工作目录（即inputs目录），
     * 因此在本地文件系统中检查inputs目录下是否存在指定文件。</p>
     *
     * @param inputPath 输入目录路径（LAMMPS工作目录）
     * @param filename 文件名
     * @return true表示文件存在，false表示文件不存在
     */
    private boolean verifyOutputFileExists(Path inputPath, String filename) {
        Path filePath = inputPath.resolve(filename);
        boolean exists = Files.exists(filePath);
        if (!exists) {
            log.warn("[MD执行器] 输出文件不存在: {}", filePath);
        }
        return exists;
    }

    /**
     * 将任务标记为失败状态
     *
     * @param job 模拟任务对象
     * @param errorMessage 错误信息
     */
    private void markJobAsFailed(SimulationJob job, String errorMessage) {
        try {
            // 在异步线程中重新获取JPA托管实体，避免脱管实体保存失败
            // 因为传入的job对象可能在CompletableFuture线程中已脱离JPA持久化上下文，
            // 直接save会导致LazyInitializationException或OptimisticLockingFailureException
            SimulationJob managedJob = simulationRepository.findById(job.getJobId()).orElse(null);
            if (managedJob != null) {
                managedJob.setStatus(JobStatus.FAILED);
                managedJob.setErrorMessage(errorMessage);
                managedJob.setEndTime(java.time.LocalDateTime.now());
                simulationRepository.save(managedJob);
                log.info("[MD执行器] 任务已标记为FAILED，任务ID: {}, 错误: {}", job.getJobId(), errorMessage);
            } else {
                log.error("[MD执行器] 任务不存在，无法标记失败: jobId={}", job.getJobId());
            }
        } catch (Exception e) {
            log.error("[MD执行器] 标记任务失败状态时异常，任务ID: {}", job.getJobId(), e);
        }
    }

    /**
     * 根据目标属性收集输出文件
     *
     * <p>LAMMPS在容器中运行时，输出文件默认写入工作目录（即inputs目录）。
     * 该方法将文件从inputs目录移动到raw_outputs目录，并根据目标属性
     * 确定需要收集的特定文件。</p>
     *
     * <p>属性与输出文件的映射关系：</p>
     * <ul>
     *   <li>density: 无额外文件（密度数据在log.lammps热力学输出中）</li>
     *   <li>conductivity: dump.charge.lammpstrj, msd.dat</li>
     *   <li>viscosity: pressure.dat</li>
     *   <li>dielectric: dipole.dat, total_dipole.dat</li>
     *   <li>solvation_structure: dump.solvation.lammpstrj</li>
     * </ul>
     *
     * <p>始终收集的公共文件：log.lammps, dump.trajectory.lammpstrj, final.data</p>
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param targetProperties 目标属性列表
     * @return 文件类型到相对路径的映射
     */
    public Map<String, String> collectOutputFiles(Long userId, Long jobId, List<String> targetProperties) {
        log.info("[MD执行器] 开始收集输出文件: userId={}, jobId={}, targetProperties={}",
                userId, jobId, targetProperties);

        Map<String, String> collectedFiles = new LinkedHashMap<>();

        // 使用PathUtil生成路径，禁止硬编码
        Path localInputPath = pathUtil.getInputPath(userId, jobId);
        Path localRawOutputPath = pathUtil.getRawOutputPath(userId, jobId);

        // 确保输出目录存在
        pathUtil.ensureDirectoryExists(localRawOutputPath);

        // 定义属性到输出文件的映射关系
        Map<String, List<String>> propertyFileMapping = buildPropertyFileMapping();

        // 收集公共文件（所有任务都需要）
        // 包含文件输入输出规则要求的所有标准输出文件
        List<String> commonFiles = Arrays.asList(
                "log.lammps",                    // LAMMPS日志文件
                "dump.trajectory.lammpstrj",     // 原子轨迹文件
                "thermo.out",                    // 热力学数据输出文件
                "minimized.data",                // 能量最小化后构型
                "equilibrated.data",             // 平衡后构型
                "final.data"                     // 最终构型
        );

        // 合并需要收集的文件列表：公共文件 + 属性特定文件
        Set<String> filesToCollect = new LinkedHashSet<>(commonFiles);
        for (String property : targetProperties) {
            List<String> propertyFiles = propertyFileMapping.getOrDefault(property, Collections.emptyList());
            filesToCollect.addAll(propertyFiles);
        }

        // 将文件从inputs目录移动到raw_outputs目录
        for (String filename : filesToCollect) {
            // log.lammps由mergeLammpsLogFiles在raw_outputs目录中生成，
            // 其他文件（dump.trajectory.lammpstrj、final.data等）由LAMMPS在inputs工作目录中生成
            Path sourcePath;
            if ("log.lammps".equals(filename)) {
                sourcePath = localRawOutputPath.resolve(filename);
            } else {
                sourcePath = localInputPath.resolve(filename);
            }
            Path targetPath = localRawOutputPath.resolve(filename);

            if (Files.exists(sourcePath)) {
                try {
                    Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    // 存储相对路径（相对于任务根目录）
                    String relativePath = pathUtil.getRelativePath(userId, jobId, targetPath);
                    collectedFiles.put(filename, relativePath);
                    log.info("[MD执行器] 文件收集成功: {} -> {}", filename, relativePath);
                } catch (IOException e) {
                    log.warn("[MD执行器] 文件移动失败: {}，错误: {}", filename, e.getMessage());
                }
            } else {
                log.warn("[MD执行器] 输出文件不存在，跳过收集: {}", filename);
            }
        }

        log.info("[MD执行器] 输出文件收集完成: 共收集{}个文件", collectedFiles.size());
        return collectedFiles;
    }

    /**
     * 构建属性与输出文件的映射关系
     *
     * <p>映射关系定义：</p>
     * <ul>
     *   <li>density: 无额外文件（密度数据在log.lammps热力学输出中）</li>
     *   <li>conductivity: dump.charge.lammpstrj（带电荷轨迹）, msd.dat（均方位移）</li>
     *   <li>viscosity: pressure.dat（压力张量）</li>
     *   <li>dielectric: dipole.dat（偶极矩）, total_dipole.dat（总偶极矩）</li>
     *   <li>solvation_structure: dump.solvation.lammpstrj（溶剂化结构轨迹）</li>
     * </ul>
     *
     * @return 属性名称到文件名列表的映射
     */
    private Map<String, List<String>> buildPropertyFileMapping() {
        Map<String, List<String>> mapping = new LinkedHashMap<>();
        // 密度：无需额外文件，数据在log.lammps和thermo.out热力学输出中
        mapping.put("density", Collections.emptyList());
        // 电导率：需要带电荷轨迹文件（MSD通过后处理脚本从轨迹计算，不需要单独的msd.dat）
        mapping.put("conductivity", Arrays.asList("dump.charge.lammpstrj"));
        // 粘度：需要压力张量数据
        mapping.put("viscosity", Arrays.asList("pressure.dat"));
        // 介电常数：需要偶极矩数据和总偶极矩数据
        mapping.put("dielectric", Arrays.asList("dipole.dat", "total_dipole.dat"));
        // 溶剂化结构：需要溶剂化结构轨迹文件
        mapping.put("solvation_structure", Arrays.asList("dump.solvation.lammpstrj"));
        return mapping;
    }

    /**
     * 更新或创建SimulationRawOutput记录
     *
     * <p>根据收集到的文件路径更新SimulationRawOutput实体，存储相对路径。
     * 如果记录已存在则更新，否则创建新记录。</p>
     *
     * <p>totalFrames、totalSimulationTimeNs、fileSizeGb设置为占位值，
     * 将在后续解析阶段更新为实际值。</p>
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param targetProperties 目标属性列表
     * @param collectedFiles 文件类型到相对路径的映射
     */
    public void updateRawOutputRecord(Long userId, Long jobId, List<String> targetProperties,
                                      Map<String, String> collectedFiles) {
        log.info("[MD执行器] 开始更新原始输出记录: userId={}, jobId={}", userId, jobId);

        try {
            // 查找已有记录或创建新记录
            SimulationRawOutput output = simulationOutputRepository.findByJobId(jobId)
                    .orElseGet(() -> {
                        SimulationRawOutput newOutput = new SimulationRawOutput();
                        newOutput.setJobId(jobId);
                        return newOutput;
                    });

            // 设置公共文件路径
            output.setLogFilePath(collectedFiles.getOrDefault("log.lammps", ""));
            output.setTrajectoryFilePath(collectedFiles.getOrDefault("dump.trajectory.lammpstrj", ""));
            output.setFinalDataFilePath(collectedFiles.getOrDefault("final.data", ""));

            // 根据目标属性设置特定文件路径
            for (String property : targetProperties) {
                setPropertySpecificFilePaths(output, property, collectedFiles);
            }

            // 设置占位值，将在后续解析阶段更新
            output.setTotalFrames(0L);
            output.setTotalSimulationTimeNs(0.0);
            output.setFileSizeGb(BigDecimal.ZERO);
            output.setWrappedCoordsIncluded(true);
            output.setPeriodicImageFlagIncluded(true);

            simulationOutputRepository.save(output);
            log.info("[MD执行器] 原始输出记录更新成功: jobId={}", jobId);

        } catch (Exception e) {
            log.error("[MD执行器] 更新原始输出记录失败: jobId={}", jobId, e);
        }
    }

    /**
     * 根据属性类型设置特定的输出文件路径
     *
     * @param output SimulationRawOutput实体
     * @param property 属性名称
     * @param collectedFiles 文件类型到相对路径的映射
     */
    private void setPropertySpecificFilePaths(SimulationRawOutput output, String property,
                                              Map<String, String> collectedFiles) {
        switch (property) {
            case "conductivity":
                // 电导率：设置带电荷轨迹文件和均方位移文件路径
                output.setChargeTrajectoryFilePath(collectedFiles.getOrDefault("dump.charge.lammpstrj", ""));
                output.setMsdFilePath(collectedFiles.getOrDefault("msd.dat", ""));
                break;
            case "viscosity":
                // 粘度：设置压力张量文件路径
                output.setPressureFilePath(collectedFiles.getOrDefault("pressure.dat", ""));
                break;
            case "dielectric":
                // 介电常数：设置偶极矩文件路径
                output.setDipoleFilePath(collectedFiles.getOrDefault("dipole.dat", ""));
                // total_dipole.dat也存储在dipoleFilePath字段（如果存在）
                if (collectedFiles.containsKey("total_dipole.dat")) {
                    output.setDipoleMomentFilePath(collectedFiles.get("total_dipole.dat"));
                }
                break;
            case "solvation_structure":
                // 溶剂化结构：设置溶剂化轨迹文件路径
                output.setSolvationTrajectoryFilePath(collectedFiles.getOrDefault("dump.solvation.lammpstrj", ""));
                break;
            case "density":
                // 密度：无需额外文件，数据在log.lammps热力学输出中
                break;
            default:
                log.warn("[MD执行器] 未知的目标属性: {}，跳过文件路径设置", property);
                break;
        }
    }

    /**
     * 构建模拟结果摘要JSON字符串
     *
     * @param job 模拟任务对象
     * @param collectedFiles 收集到的文件映射
     * @param targetProperties 目标属性列表
     * @return JSON格式的结果摘要字符串
     */
    private String buildResultSummary(SimulationJob job, Map<String, String> collectedFiles,
                                       List<String> targetProperties) {
        StringBuilder result = new StringBuilder();
        result.append("{\n");
        result.append("  \"status\": \"").append(JobStatus.COMPLETED).append("\",\n");
        result.append("  \"software\": \"").append(job.getSoftwareName()).append("\",\n");
        result.append("  \"jobId\": \"").append(job.getJobId()).append("\",\n");
        result.append("  \"targetProperties\": ").append(targetProperties).append(",\n");
        result.append("  \"collectedFiles\": ").append(collectedFiles.keySet()).append(",\n");
        result.append("  \"stages\": [\"minimization\", \"equilibrium\", \"production\"],\n");
        result.append("  \"timestamp\": \"").append(java.time.LocalDateTime.now()).append("\"\n");
        result.append("}");
        return result.toString();
    }

    /**
     * 解析模拟输出结果，提取关键物理量并生成JSON格式摘要
     *
     * <p>从LAMMPS输出中提取总能量等关键物理量。
     * 该方法为简化版本，详细的结果解析由后处理服务完成。</p>
     *
     * @param output 模拟软件的原始输出
     * @param job 模拟任务对象
     * @return JSON格式的结果摘要字符串
     */
    private String parseOutputResult(String output, SimulationJob job) {
        try {
            StringBuilder result = new StringBuilder();
            result.append("{\n");
            result.append("  \"status\": \"").append(JobStatus.COMPLETED).append("\",\n");
            result.append("  \"software\": \"").append(job.getSoftwareName()).append("\",\n");

            if ("LAMMPS".equals(job.getSoftwareName())) {
                // 从LAMMPS输出中提取总能量
                String[] lines = output.split("\n");
                for (int i = lines.length - 1; i >= 0; i--) {
                    if (lines[i].contains("Total energy")) {
                        String energy = lines[i].replaceAll("[^0-9.-]", " ").trim().split("\\s+")[0];
                        result.append("  \"total_energy\": ").append(energy).append(",\n");
                        break;
                    }
                }
            }

            result.append("  \"timestamp\": \"").append(java.time.LocalDateTime.now()).append("\"\n");
            result.append("}");

            return result.toString();

        } catch (Exception e) {
            log.warn("[MD执行器] 解析输出结果失败，返回原始输出", e);
            return String.format("{\"status\":\"%s\",\"raw_output\":\"%s\"}",
                    JobStatus.COMPLETED, output.replace("\"", "\\\"").replace("\n", "\\n"));
        }
    }

    /**
     * 检查MD系统状态，包括容器运行状态、GPU可用性和资源使用情况
     *
     * @return 系统状态信息字符串
     */
    public String checkSystemStatus() {
        try {
            StringBuilder status = new StringBuilder();

            boolean containerRunning = dockerService.isMDContainerRunning();
            status.append("MD Container: ").append(containerRunning ? "RUNNING" : "STOPPED").append("\n");

            if (containerRunning) {
                boolean gpuAvailable = dockerService.isGPUAvailable();
                status.append("GPU Available: ").append(gpuAvailable ? "YES" : "NO").append("\n");

                String resourceUsage = dockerService.getResourceUsage();
                status.append("Resource Usage: ").append(resourceUsage).append("\n");
            }

            return status.toString();

        } catch (Exception e) {
            return "System check failed: " + e.getMessage();
        }
    }

    /**
     * 生成模拟软件的输入模板文件
     *
     * <p>根据软件名称生成对应的输入模板，优先从模板文件加载，
     * 若模板文件不存在则使用内置默认模板。</p>
     *
     * @param software 软件名称（如LAMMPS、GROMACS）
     * @return 输入模板内容字符串
     */
    public String generateInputTemplate(String software) {
        try {
            Path templatePath = Paths.get("templates", software.toLowerCase() + "_template.in");

            if (Files.exists(templatePath)) {
                return new String(Files.readAllBytes(templatePath), java.nio.charset.StandardCharsets.UTF_8);
            }

            if ("LAMMPS".equals(software)) {
                return getLAMMPSTemplate();
            } else {
                return getGROMACSTemplate();
            }

        } catch (Exception e) {
            log.warn("[MD执行器] 加载模板失败: {}", software, e);
            return "Template not available";
        }
    }

    /**
     * 获取LAMMPS默认输入模板
     *
     * @return LAMMPS输入模板内容字符串
     */
    private String getLAMMPSTemplate() {
        return "# LAMMPS input template for electrolyte simulation\n" +
                "units       real\n" +
                "atom_style  full\n" +
                "boundary    p p p\n" +
                "\n" +
                "# Read data file\n" +
                "read_data   system.data\n" +
                "\n" +
                "# Force field\n" +
                "pair_style  lj/cut/coul/long 12.0\n" +
                "pair_modify mix arithmetic\n" +
                "kspace_style pppm 1.0e-4\n" +
                "\n" +
                "# Thermostat and barostat\n" +
                "fix         1 all nvt temp 300.0 300.0 100.0\n" +
                "fix         2 all npt temp 300.0 300.0 100.0 iso 1.0 1.0 1000.0\n" +
                "\n" +
                "# Output\n" +
                "thermo      1000\n" +
                "thermo_style custom step temp press vol density etotal\n" +
                "\n" +
                "# Run\n" +
                "run         10000\n";
    }

    /**
     * 获取GROMACS默认输入模板
     *
     * @return GROMACS输入模板内容字符串
     */
    private String getGROMACSTemplate() {
        return "; GROMACS mdp template for electrolyte simulation\n" +
                "integrator               = md\n" +
                "dt                       = 0.002\n" +
                "nsteps                   = 50000\n" +
                "\n" +
                "; Temperature coupling\n" +
                "tcoupl                   = v-rescale\n" +
                "tc-grps                  = system\n" +
                "tau_t                    = 0.1\n" +
                "ref_t                    = 300.0\n" +
                "\n" +
                "; Pressure coupling\n" +
                "pcoupl                   = Parrinello-Rahman\n" +
                "pcoupltype               = isotropic\n" +
                "tau_p                    = 2.0\n" +
                "ref_p                    = 1.0\n" +
                "compressibility          = 4.5e-5\n" +
                "\n" +
                "; Electrostatics\n" +
                "coulombtype              = PME\n" +
                "rcoulomb                 = 1.0\n" +
                "vdwtype                  = Cut-off\n" +
                "rvdw                     = 1.0\n" +
                "pbc                      = xyz\n" +
                "\n" +
                "; Output\n" +
                "nstxout                  = 1000\n" +
                "nstvout                  = 1000\n" +
                "nstfout                  = 0\n" +
                "nstlog                   = 1000\n" +
                "nstenergy                = 1000\n" +
                "nstxout-compressed       = 1000\n";
    }
}
