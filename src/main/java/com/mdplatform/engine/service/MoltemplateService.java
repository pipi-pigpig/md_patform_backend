package com.mdplatform.engine.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdplatform.common.util.PathUtil;
import com.mdplatform.engine.util.JsonOutputParser;
import com.mdplatform.engine.dto.*;
import com.mdplatform.engine.model.JobStatus;
import com.mdplatform.engine.model.MoleculeTemplate;
import com.mdplatform.engine.model.SimulationJob;
import com.mdplatform.engine.repository.MoleculeTemplateRepository;
import com.mdplatform.engine.repository.SimulationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Moltemplate建模服务
 *
 * <p>该服务负责执行完整的Moltemplate建模流程，包括配方序列化、
 * Python建模脚本执行、Packmol堆积、Moltemplate系统文件生成、
 * Moltemplate命令执行和文件整理输出。</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>配方配置序列化到JSON文件</li>
 *   <li>执行Python建模脚本</li>
 *   <li>执行Packmol分子堆积</li>
 *   <li>生成Moltemplate系统文件（system.lt）</li>
 *   <li>执行Moltemplate命令生成LAMMPS输入文件</li>
 *   <li>整理文件输出到inputs目录</li>
 *   <li>完整建模流程编排（步骤1-8）</li>
 *   <li>任务状态更新到数据库</li>
 * </ul>
 *
 * <p>完整建模流程（步骤1-8）：</p>
 * <ol>
 *   <li>步骤1：分子数量计算 - 根据配方计算各分子类型的数量</li>
 *   <li>步骤2：盒子尺寸计算 - 根据分子数量和密度计算模拟盒子尺寸</li>
 *   <li>步骤3：Packmol堆积 - 使用Packmol生成初始构型文件</li>
 *   <li>步骤4：Moltemplate系统文件生成 - 生成system.lt系统描述文件</li>
 *   <li>步骤5：调取分子模板 - 加载分子模板文件</li>
 *   <li>步骤6：执行Moltemplate命令 - 将system.lt转换为LAMMPS输入文件</li>
 *   <li>步骤7：文件整理输出 - 将生成的文件整理到inputs/目录</li>
 *   <li>步骤7.5：Jinja2脚本生成 - 使用Jinja2模板生成LAMMPS输入脚本</li>
 *   <li>步骤8：LAMMPS模拟执行 - 执行三阶段LAMMPS模拟（能量最小化→平衡→生产）</li>
 * </ol>
 *
 * <p>文件路径规范：</p>
 * <pre>
 * user_{userId}/jobs/job_{jobId}/
 * ├── inputs/
 * │   ├── formula_config.json     # 配方配置文件
 * │   ├── packmol.inp             # Packmol输入脚本
 * │   ├── packed_system.pdb       # Packmol初始构型文件
 * │   ├── system.lt               # Moltemplate系统描述文件
 * │   ├── system.data             # LAMMPS结构文件
 * │   ├── system.in.init          # 初始化设置文件
 * │   └── system.in.settings      # 力场设置文件
 * └── raw_outputs/
 *     └── log.lammps              # LAMMPS日志文件
 * </pre>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 * @since 2024-01-01
 */
@Service
@Slf4j
public class MoltemplateService {

    /** 路径工具类，用于生成符合规范的文件路径 */
    private final PathUtil pathUtil;
    
    /** 原子文件写入服务，确保文件写入的原子性 */
    private final AtomicFileService atomicFileService;
    
    /** Docker服务，用于在md-engine容器中执行Python建模脚本 */
    private final DockerService dockerService;
    
    /** Packmol分子堆积服务 */
    private final PackmolService packmolService;
    
    /** Moltemplate系统文件构建服务 */
    private final MoltemplateSystemService moltemplateSystemService;
    
    /** Moltemplate执行与文件整理服务（步骤6和步骤7） */
    private final MoltemplateExecutionService moltemplateExecutionService;

    /** LAMMPS模板渲染服务（步骤7.5），使用Jinja2模板引擎生成LAMMPS输入脚本 */
    private final LammpsTemplateService lammpsTemplateService;

    /** MD执行器服务（步骤8），用于执行LAMMPS三阶段模拟 */
    private final MDExecutorService mdExecutorService;

    /** 分子模板仓库，用于查询分子模板信息 */
    private final MoleculeTemplateRepository moleculeTemplateRepository;
    
    /** 模拟任务仓库，用于更新任务状态到数据库 */
    private final SimulationRepository simulationRepository;
    
    /** JSON解析器，用于序列化配方配置 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Python建模脚本路径，通过配置注入避免硬编码 */
    @Value("${md-platform.scripts.moltemplate-path:scripts/modeling/run_modeling.py}")
    private String moltemplateScriptPath;

    /** Python脚本执行工作目录，通过配置注入 */
    @Value("${md-platform.scripts.working-dir:scripts/modeling}")
    private String scriptsWorkingDir;

    /** 模拟执行超时时间（秒），通过配置注入，默认3600秒（1小时） */
    @Value("${app.docker.timeout-seconds:3600}")
    private int timeoutSeconds;

    /** md-engine容器名称，通过配置注入 */
    @Value("${app.docker.md-container-name:md-engine}")
    private String mdContainerName;

    /** Python建模主脚本在Docker容器中的路径（统一入口脚本） */
    private static final String DOCKER_SCRIPT_PATH = "/workspace/scripts/modeling/run_modeling.py";

    /**
     * 构造函数，通过依赖注入获取所需服务
     *
     * @param pathUtil 路径工具类实例
     * @param atomicFileService 原子文件写入服务实例
     * @param dockerService Docker服务实例，用于在容器中执行Python脚本
     * @param packmolService Packmol分子堆积服务实例
     * @param moltemplateSystemService Moltemplate系统文件构建服务实例
     * @param moltemplateExecutionService Moltemplate执行与文件整理服务实例
     * @param lammpsTemplateService LAMMPS模板渲染服务实例
     * @param mdExecutorService MD执行器服务实例
     * @param moleculeTemplateRepository 分子模板仓库实例
     * @param simulationRepository 模拟任务仓库实例
     */
    public MoltemplateService(PathUtil pathUtil, AtomicFileService atomicFileService,
                              DockerService dockerService,
                              PackmolService packmolService, MoltemplateSystemService moltemplateSystemService,
                              MoltemplateExecutionService moltemplateExecutionService,
                              LammpsTemplateService lammpsTemplateService,
                              MDExecutorService mdExecutorService,
                              MoleculeTemplateRepository moleculeTemplateRepository,
                              SimulationRepository simulationRepository) {
        this.pathUtil = pathUtil;
        this.atomicFileService = atomicFileService;
        this.dockerService = dockerService;
        this.packmolService = packmolService;
        this.moltemplateSystemService = moltemplateSystemService;
        this.moltemplateExecutionService = moltemplateExecutionService;
        this.lammpsTemplateService = lammpsTemplateService;
        this.mdExecutorService = mdExecutorService;
        this.moleculeTemplateRepository = moleculeTemplateRepository;
        this.simulationRepository = simulationRepository;
    }

    /**
     * 将配方请求序列化为JSON文件
     *
     * <p>将配方配置对象序列化为JSON格式，并使用原子写入方式保存到任务的输入目录中。</p>
     *
     * @param userId 用户ID，用于确定文件存储路径
     * @param jobId 任务ID，用于确定文件存储路径
     * @param request 配方请求对象
     * @return JSON文件路径
     * @throws IOException 当文件写入失败时抛出
     */
    public Path serializeFormulaToJSON(Long userId, Long jobId, FormulaRequest request) throws IOException {
        log.info("序列化配方到JSON文件: userId={}, jobId={}", userId, jobId);

        pathUtil.createJobDirectories(userId, jobId);
        Path inputPath = pathUtil.getInputPath(userId, jobId);

        String filename = "formula_config.json";
        Path jsonFilePath = inputPath.resolve(filename);

        String jsonContent = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);
        atomicFileService.writeAtomic(jsonFilePath, jsonContent);

        log.info("配方JSON文件已保存: {}", jsonFilePath);
        return jsonFilePath;
    }

    /**
     * 执行Python建模脚本
     *
     * <p>在本地环境中启动Python建模脚本进程，读取配方配置文件并执行建模计算。</p>
     *
     * @param userId 用户ID，用于确定文件存储路径
     * @param jobId 任务ID，用于确定文件存储路径
     * @return Python进程对象
     * @throws IOException 当配方文件不存在或进程启动失败时抛出
     */
    public Process executePythonModeling(Long userId, Long jobId) throws IOException {
        log.info("执行Python建模脚本: userId={}, jobId={}", userId, jobId);

        Path inputPath = pathUtil.getInputPath(userId, jobId);
        Path outputPath = pathUtil.getOutputPath(userId, jobId);

        Path formulaFile = inputPath.resolve("formula_config.json");
        if (!Files.exists(formulaFile)) {
            throw new IOException("配方配置文件不存在: " + formulaFile);
        }

        // 使用配置注入的脚本路径，替代硬编码的"python/moltemplate_modeling.py"
        ProcessBuilder processBuilder = new ProcessBuilder(
                "python",
                moltemplateScriptPath,
                "--user-id", userId.toString(),
                "--job-id", jobId.toString(),
                "--input", formulaFile.toString(),
                "--output", outputPath.toString()
        );

        processBuilder.redirectErrorStream(true);
        // 使用配置注入的工作目录，替代硬编码的new File(".")
        processBuilder.directory(new File(scriptsWorkingDir));

        log.info("启动Python进程: {}", String.join(" ", processBuilder.command()));
        Process process = processBuilder.start();

        return process;
    }

    /**
     * 根据分子名称查询分子模板
     *
     * @param name 分子名称
     * @return 包含分子模板的Optional对象，若不存在则为空
     */
    public Optional<MoleculeTemplate> getMoleculeTemplateByName(String name) {
        log.debug("查询分子模板: name={}", name);
        return moleculeTemplateRepository.findByMoleculeName(name);
    }

    /**
     * 读取配方JSON文件内容
     *
     * @param userId 用户ID，用于确定文件存储路径
     * @param jobId 任务ID，用于确定文件存储路径
     * @return 配方JSON文件的字符串内容
     * @throws IOException 当配方文件不存在或读取失败时抛出
     */
    public String readFormulaJSON(Long userId, Long jobId) throws IOException {
        log.info("读取配方JSON文件: userId={}, jobId={}", userId, jobId);

        Path inputPath = pathUtil.getInputPath(userId, jobId);
        Path formulaFile = inputPath.resolve("formula_config.json");

        if (!Files.exists(formulaFile)) {
            throw new IOException("配方配置文件不存在: " + formulaFile);
        }

        return Files.readString(formulaFile);
    }

    /**
     * 检查配方JSON文件是否存在
     *
     * @param userId 用户ID，用于确定文件存储路径
     * @param jobId 任务ID，用于确定文件存储路径
     * @return true表示配方文件存在，false表示不存在
     */
    public boolean formulaFileExists(Long userId, Long jobId) {
        Path inputPath = pathUtil.getInputPath(userId, jobId);
        Path formulaFile = inputPath.resolve("formula_config.json");
        return Files.exists(formulaFile);
    }

    /**
     * 执行完整建模工作流（序列化配方 + Python建模 + Packmol堆积）
     *
     * <p>该方法依次执行配方序列化、Python建模脚本和Packmol堆积，
     * 返回包含各步骤结果的综合工作流结果。</p>
     *
     * @param userId 用户ID，用于确定文件存储路径
     * @param jobId 任务ID，用于确定文件存储路径
     * @param request 配方请求对象
     * @return 建模工作流结果，包含配方路径、Packmol结果和成功标志
     * @throws IOException 当文件操作失败时抛出
     */
    public ModelingWorkflowResult executeCompleteModelingWorkflow(Long userId, Long jobId,
            FormulaRequest request) throws IOException {
        log.info("开始完整建模流程: userId={}, jobId={}", userId, jobId);

        Path formulaFile = serializeFormulaToJSON(userId, jobId, request);
        log.info("步骤1: 配方JSON文件已保存: {}", formulaFile);

        Process modelingProcess = executePythonModeling(userId, jobId);
        log.info("步骤2: Python建模脚本已启动");

        PackmolResult packmolResult = packmolService.executePackmol(userId, jobId, 
                formulaFile.toString());
        log.info("步骤3: Packmol堆积完成, 成功={}, 原子数={}", 
                packmolResult.getSuccess(), packmolResult.getAtomCount());

        ModelingWorkflowResult result = new ModelingWorkflowResult();
        result.setFormulaFilePath(pathUtil.getRelativePath(userId, jobId, formulaFile));
        result.setPackmolResult(packmolResult);
        result.setSuccess(packmolResult.getSuccess());

        if (packmolResult.getSuccess()) {
            log.info("建模流程完成: userId={}, jobId={}, PDB路径={}", 
                    userId, jobId, packmolResult.getPdbFilePath());
        } else {
            log.error("建模流程失败: userId={}, jobId={}, 错误={}", 
                    userId, jobId, packmolResult.getErrorMessage());
        }

        return result;
    }

    /**
     * 执行Packmol堆积步骤
     *
     * <p>读取已存在的配方配置文件，执行Packmol分子堆积计算。</p>
     *
     * @param userId 用户ID，用于确定文件存储路径
     * @param jobId 任务ID，用于确定文件存储路径
     * @return Packmol堆积结果，包含成功标志、PDB路径和原子数量
     * @throws IOException 当配方文件不存在或Packmol执行失败时抛出
     */
    public PackmolResult executePackmolStep(Long userId, Long jobId) throws IOException {
        log.info("执行Packmol堆积步骤: userId={}, jobId={}", userId, jobId);

        Path inputPath = pathUtil.getInputPath(userId, jobId);
        Path formulaFile = inputPath.resolve("formula_config.json");

        if (!Files.exists(formulaFile)) {
            throw new IOException("配方配置文件不存在: " + formulaFile);
        }

        return packmolService.executePackmol(userId, jobId, formulaFile.toString());
    }

    /**
     * 执行完整建模流程（步骤1-8）
     *
     * <p>该方法执行完整的Moltemplate建模流程，包含以下8个步骤：</p>
     * <ol>
     *   <li>步骤1：分子数量计算 - 根据配方计算各分子类型的数量</li>
     *   <li>步骤2：盒子尺寸计算 - 根据分子数量和密度计算模拟盒子尺寸</li>
     *   <li>步骤3：Packmol堆积 - 使用Packmol生成初始构型文件</li>
     *   <li>步骤4：Moltemplate系统文件生成 - 生成system.lt系统描述文件</li>
     *   <li>步骤5：调取分子模板 - 加载分子模板文件（在步骤4中完成）</li>
     *   <li>步骤6：执行Moltemplate命令 - 将system.lt转换为LAMMPS输入文件</li>
     *   <li>步骤7：文件整理输出 - 将生成的文件整理到inputs/目录</li>
     *   <li>步骤7.5：Jinja2脚本生成 - 使用Jinja2模板生成LAMMPS输入脚本</li>
     *   <li>步骤8：LAMMPS模拟执行 - 执行三阶段LAMMPS模拟（能量最小化→平衡→生产）</li>
     * </ol>
     *
     * <p>步骤依赖检查：</p>
     * <ul>
     *   <li>步骤3依赖步骤1和步骤2的结果（分子数量和盒子尺寸）</li>
     *   <li>步骤4依赖步骤3的结果（Packmol生成的PDB文件）</li>
     *   <li>步骤6依赖步骤4的结果（system.lt文件）</li>
     *   <li>步骤7依赖步骤6的结果（LAMMPS输入文件）</li>
     *   <li>步骤7.5依赖步骤7的结果（整理后的输入文件）</li>
     *   <li>步骤8依赖步骤7.5的结果（Jinja2生成的LAMMPS输入脚本）</li>
     * </ul>
     *
     * <p>错误处理策略：</p>
     * <ul>
     *   <li>每个步骤失败时，立即停止后续步骤</li>
     *   <li>记录每个步骤的执行状态和错误信息</li>
     *   <li>返回详细的错误信息和失败步骤名称</li>
     *   <li>更新任务状态到数据库（COMPLETED或FAILED）</li>
     * </ul>
     *
     * <p>任务状态更新：</p>
     * <ul>
     *   <li>流程开始前：任务状态保持为PENDING或RUNNING</li>
     *   <li>流程成功完成：任务状态更新为COMPLETED</li>
     *   <li>流程失败：任务状态更新为FAILED，记录错误信息</li>
     *   <li>流程异常：任务状态更新为FAILED，记录异常信息</li>
     * </ul>
     *
     * <p>注意：该方法假设分子数量和盒子尺寸已经通过其他方式计算完成，
     * 并存储在配方配置文件中。如果需要动态计算，请先调用相关计算服务。</p>
     *
     * @param userId 用户ID，用于确定文件存储路径和任务归属验证
     * @param jobId 任务ID，用于确定文件存储路径和任务状态更新
     * @param formulaFilePath 配方文件路径，包含分子组成和力场配置信息
     * @return FullModelingResult 完整建模结果，包含所有步骤的执行结果
     */
    public FullModelingResult executeFullModeling(Long userId, Long jobId, String formulaFilePath) {
        log.info("[完整建模流程] 开始执行: userId={}, jobId={}", userId, jobId);
        log.info("[完整建模流程] 配方文件路径: {}", formulaFilePath);

        // 记录开始时间，用于计算总执行耗时
        Instant startTime = Instant.now();
        
        // 构建执行日志，用于记录整个流程的执行过程
        StringBuilder executionLog = new StringBuilder();
        executionLog.append("=== 完整建模流程开始 ===\n");
        executionLog.append("用户ID: ").append(userId).append("\n");
        executionLog.append("任务ID: ").append(jobId).append("\n");
        executionLog.append("配方文件: ").append(formulaFilePath).append("\n\n");

        // 初始化结果对象
        FullModelingResult result = FullModelingResult.builder()
                .userId(userId)
                .jobId(jobId)
                .formulaFilePath(formulaFilePath)
                .build();

        try {
            // ========== 步骤1：分子数量计算 ==========
            log.info("[完整建模流程] 步骤1：分子数量计算");
            executionLog.append("步骤1：分子数量计算\n");

            // 使用PathUtil统一方法将本地路径转换为Docker容器内路径（统一了原先分散在各Service中的重复实现）
            String dockerFormulaFilePath = pathUtil.convertToDockerPath(Path.of(formulaFilePath));
            String dockerJobDir = pathUtil.convertToDockerPath(pathUtil.getJobRootPath(userId, jobId));
            log.info("[完整建模流程] 配方文件Docker路径: {}", dockerFormulaFilePath);
            log.info("[完整建模流程] 任务目录Docker路径: {}", dockerJobDir);

            // 构建Python脚本命令：使用python -m方式运行，传入--job-id和--job-dir
            String molCountCmd = "cd /workspace/scripts && python3 -m modeling.run_modeling"
                    + " --job-id " + jobId
                    + " --job-dir " + dockerJobDir
                    + " --mode molecule-count --formula-file " + dockerFormulaFilePath;
            List<String> moleculeCountCommand = Arrays.asList("bash", "-c", molCountCmd);

            log.info("[完整建模流程] 执行分子数量计算命令: {}", molCountCmd);

            // 通过DockerService在md-engine容器中执行Python脚本
            String moleculeCountOutput = dockerService.executeCommandInContainer(
                    mdContainerName, moleculeCountCommand, "/workspace");

            // 检查命令执行结果是否包含错误信息
            if (moleculeCountOutput == null || moleculeCountOutput.contains("Command failed")
                    || moleculeCountOutput.contains("Failed to execute")) {
                String errorMsg = "分子数量计算Python脚本执行失败: " + moleculeCountOutput;
                log.error("[完整建模流程] {}", errorMsg);
                executionLog.append(errorMsg).append("\n");

                result.setSuccess(false);
                result.setFailedStep("分子数量计算");
                result.setErrorMessage(errorMsg);
                result.setExecutionLog(executionLog.toString());
                result.setTotalElapsedTimeSeconds(Duration.between(startTime, Instant.now()).toMillis() / 1000.0);

                return result;
            }

            // 解析Python脚本输出的JSON结果
            List<MoleculeCountResult> moleculeCountResult;
            try {
                // 使用统一的JSON解析工具从输出中提取JSON
                String jsonOutput = JsonOutputParser.extractJson(moleculeCountOutput);
                if (jsonOutput == null) {
                    throw new IllegalArgumentException("无法从Python脚本输出中提取有效JSON");
                }
                moleculeCountResult = objectMapper.readValue(jsonOutput,
                        new TypeReference<List<MoleculeCountResult>>() {});
                log.info("[完整建模流程] 分子数量计算结果解析成功，共{}种分子", moleculeCountResult.size());
            } catch (Exception e) {
                String errorMsg = "解析分子数量计算结果失败: " + e.getMessage();
                log.error("[完整建模流程] {}", errorMsg, e);
                executionLog.append(errorMsg).append("\n");

                result.setSuccess(false);
                result.setFailedStep("分子数量计算");
                result.setErrorMessage(errorMsg);
                result.setExecutionLog(executionLog.toString());
                result.setTotalElapsedTimeSeconds(Duration.between(startTime, Instant.now()).toMillis() / 1000.0);

                return result;
            }

            executionLog.append("分子数量计算完成\n");
            for (MoleculeCountResult mc : moleculeCountResult) {
                executionLog.append("  分子: ").append(mc.getName())
                        .append(", 数量: ").append(mc.getCount())
                        .append(", 电荷: ").append(mc.getCharge()).append("\n");
            }
            executionLog.append("\n");
            result.setMoleculeCountResult(moleculeCountResult);

            // ========== 步骤2：盒子尺寸计算 ==========
            log.info("[完整建模流程] 步骤2：盒子尺寸计算");
            executionLog.append("步骤2：盒子尺寸计算\n");

            // 构建Python脚本命令：调用run_modeling.py的box-size模式
            String boxSizeCmd = "cd /workspace/scripts && python3 -m modeling.run_modeling"
                    + " --job-id " + jobId
                    + " --job-dir " + dockerJobDir
                    + " --mode box-size --formula-file " + dockerFormulaFilePath;
            List<String> boxSizeCommand = Arrays.asList("bash", "-c", boxSizeCmd);

            log.info("[完整建模流程] 执行盒子尺寸计算命令: {}", boxSizeCmd);

            // 通过DockerService在md-engine容器中执行Python脚本
            String boxSizeOutput = dockerService.executeCommandInContainer(
                    mdContainerName, boxSizeCommand, "/workspace");

            // 检查命令执行结果是否包含错误信息
            if (boxSizeOutput == null || boxSizeOutput.contains("Command failed")
                    || boxSizeOutput.contains("Failed to execute")) {
                String errorMsg = "盒子尺寸计算Python脚本执行失败: " + boxSizeOutput;
                log.error("[完整建模流程] {}", errorMsg);
                executionLog.append(errorMsg).append("\n");

                result.setSuccess(false);
                result.setFailedStep("盒子尺寸计算");
                result.setErrorMessage(errorMsg);
                result.setExecutionLog(executionLog.toString());
                result.setTotalElapsedTimeSeconds(Duration.between(startTime, Instant.now()).toMillis() / 1000.0);

                return result;
            }

            // 解析Python脚本输出的JSON结果
            BoxSizeResult boxSizeResult;
            try {
                // 使用统一的JSON解析工具从输出中提取JSON
                String jsonOutput = JsonOutputParser.extractJson(boxSizeOutput);
                if (jsonOutput == null) {
                    throw new IllegalArgumentException("无法从Python脚本输出中提取有效JSON");
                }
                boxSizeResult = objectMapper.readValue(jsonOutput, BoxSizeResult.class);
                log.info("[完整建模流程] 盒子尺寸计算结果: x={}, y={}, z={}, volume={}",
                        boxSizeResult.getX(), boxSizeResult.getY(),
                        boxSizeResult.getZ(), boxSizeResult.getVolume());
            } catch (Exception e) {
                String errorMsg = "解析盒子尺寸计算结果失败: " + e.getMessage();
                log.error("[完整建模流程] {}", errorMsg, e);
                executionLog.append(errorMsg).append("\n");

                result.setSuccess(false);
                result.setFailedStep("盒子尺寸计算");
                result.setErrorMessage(errorMsg);
                result.setExecutionLog(executionLog.toString());
                result.setTotalElapsedTimeSeconds(Duration.between(startTime, Instant.now()).toMillis() / 1000.0);

                return result;
            }

            executionLog.append("盒子尺寸计算完成\n");
            executionLog.append("  X: ").append(boxSizeResult.getX()).append(" 埃\n");
            executionLog.append("  Y: ").append(boxSizeResult.getY()).append(" 埃\n");
            executionLog.append("  Z: ").append(boxSizeResult.getZ()).append(" 埃\n");
            executionLog.append("  体积: ").append(boxSizeResult.getVolume()).append(" 立方埃\n\n");
            result.setBoxSizeResult(boxSizeResult);

            // ========== 步骤3：Packmol堆积 ==========
            log.info("[完整建模流程] 步骤3：Packmol堆积");
            executionLog.append("步骤3：Packmol堆积\n");
            
            PackmolResult packmolResult = packmolService.executePackmol(userId, jobId, formulaFilePath);
            
            if (!packmolResult.getSuccess()) {
                log.error("[完整建模流程] Packmol堆积失败: {}", packmolResult.getErrorMessage());
                executionLog.append("Packmol堆积失败: ").append(packmolResult.getErrorMessage()).append("\n");
                
                result.setSuccess(false);
                result.setPackmolResult(packmolResult);
                result.setFailedStep("Packmol堆积");
                result.setErrorMessage("Packmol堆积失败: " + packmolResult.getErrorMessage());
                result.setExecutionLog(executionLog.toString());
                result.setTotalElapsedTimeSeconds(Duration.between(startTime, Instant.now()).toMillis() / 1000.0);
                
                return result;
            }
            
            executionLog.append("Packmol堆积成功\n");
            executionLog.append("PDB文件路径: ").append(packmolResult.getPdbFilePath()).append("\n");
            executionLog.append("原子总数: ").append(packmolResult.getAtomCount()).append("\n");
            executionLog.append("执行耗时: ").append(packmolResult.getElapsedTimeSeconds()).append("秒\n\n");
            
            result.setPackmolResult(packmolResult);
            result.setTotalAtoms(packmolResult.getAtomCount());

            // ========== 步骤4：Moltemplate系统文件生成 ==========
            log.info("[完整建模流程] 步骤4：Moltemplate系统文件生成");
            executionLog.append("步骤4：Moltemplate系统文件生成\n");
            
            // 获取Packmol生成的PDB文件路径（绝对路径）
            Path pdbAbsolutePath = pathUtil.resolveAbsolutePath(userId, jobId, packmolResult.getPdbFilePath());
            
            // 调用MoltemplateSystemService生成system.lt文件
            MoltemplateSystemResult systemResult = moltemplateSystemService.generateSystemFile(
                    userId, jobId, formulaFilePath, pdbAbsolutePath.toString());
            
            if (!systemResult.getSuccess()) {
                log.error("[完整建模流程] Moltemplate系统文件生成失败: {}", systemResult.getErrorMessage());
                executionLog.append("Moltemplate系统文件生成失败: ").append(systemResult.getErrorMessage()).append("\n");
                
                result.setSuccess(false);
                result.setSystemResult(systemResult);
                result.setFailedStep("Moltemplate系统文件生成");
                result.setErrorMessage("Moltemplate系统文件生成失败: " + systemResult.getErrorMessage());
                result.setExecutionLog(executionLog.toString());
                result.setTotalElapsedTimeSeconds(Duration.between(startTime, Instant.now()).toMillis() / 1000.0);
                
                return result;
            }
            
            executionLog.append("Moltemplate系统文件生成成功\n");
            executionLog.append("system.lt文件路径: ").append(systemResult.getSystemFilePath()).append("\n");
            executionLog.append("分子模板列表: ").append(systemResult.getMoleculeTemplates()).append("\n");
            executionLog.append("分子实例映射: ").append(systemResult.getMoleculeInstances()).append("\n");
            executionLog.append("总原子数: ").append(systemResult.getTotalAtoms()).append("\n");
            executionLog.append("力场类型: ").append(systemResult.getForcefield()).append("\n");
            executionLog.append("执行耗时: ").append(systemResult.getElapsedTimeSeconds()).append("秒\n\n");
            
            result.setSystemResult(systemResult);

            // ========== 步骤6：执行Moltemplate命令 ==========
            log.info("[完整建模流程] 步骤6：执行Moltemplate命令");
            executionLog.append("步骤6：执行Moltemplate命令\n");
            
            // 获取system.lt文件的相对路径
            String systemLtRelativePath = systemResult.getSystemFilePath();
            
            // 调用MoltemplateExecutionService执行Moltemplate命令
            MoltemplateExecutionResult moltemplateExecutionResult = 
                moltemplateExecutionService.executeMoltemplate(userId, jobId, systemLtRelativePath);
            
            if (!moltemplateExecutionResult.getSuccess()) {
                log.error("[完整建模流程] Moltemplate命令执行失败: {}", 
                    moltemplateExecutionResult.getErrorMessage());
                executionLog.append("Moltemplate命令执行失败: ")
                    .append(moltemplateExecutionResult.getErrorMessage()).append("\n");
                
                result.setSuccess(false);
                result.setMoltemplateExecutionResult(moltemplateExecutionResult);
                result.setFailedStep("Moltemplate命令执行");
                result.setErrorMessage("Moltemplate命令执行失败: " + 
                    moltemplateExecutionResult.getErrorMessage());
                result.setExecutionLog(executionLog.toString());
                result.setTotalElapsedTimeSeconds(
                    Duration.between(startTime, Instant.now()).toMillis() / 1000.0);
                
                // 更新任务状态为失败
                updateJobStatus(userId, jobId, JobStatus.FAILED,
                    "Moltemplate命令执行失败: " + moltemplateExecutionResult.getErrorMessage());
                
                return result;
            }
            
            executionLog.append("Moltemplate命令执行成功\n");
            executionLog.append("执行的命令: ").append(moltemplateExecutionResult.getCommand()).append("\n");
            executionLog.append("输出文件列表: ").append(moltemplateExecutionResult.getOutputFiles()).append("\n");
            executionLog.append("执行耗时: ").append(moltemplateExecutionResult.getElapsedTimeSeconds())
                .append("秒\n\n");
            
            result.setMoltemplateExecutionResult(moltemplateExecutionResult);

            // ========== 步骤7：文件整理输出 ==========
            // 注意：步骤6的moltemplate-execution模式已在Python端通过organize_lammps_input_files()
            // 完成了文件整理（将system.data、system.in.init、system.in.settings等文件移动到inputs/目录）。
            // 因此Java端不再需要额外调用file-organize模式，否则会因源目录(temp/moltemplate_temp/)不存在而报错。
            // 参见：moltemplate_utils.py中的run_moltemplate_execution()和organize_lammps_input_files()
            log.info("[完整建模流程] 步骤7：文件整理输出（由步骤6的moltemplate-execution模式在Python端自动完成）");
            executionLog.append("步骤7：文件整理输出（由步骤6的moltemplate-execution模式在Python端自动完成）\n");

            // 验证inputs/目录中已存在step6生成的必需文件（system.data、system.in.init、system.in.settings）
            Path inputPath = pathUtil.getInputPath(userId, jobId);
            List<String> expectedFiles = Arrays.asList("system.data", "system.in.init", "system.in.settings");
            List<String> existingFiles = new ArrayList<>();
            List<String> missingFiles = new ArrayList<>();
            for (String fileName : expectedFiles) {
                Path filePath = inputPath.resolve(fileName);
                if (Files.exists(filePath)) {
                    existingFiles.add(fileName);
                } else {
                    missingFiles.add(fileName);
                }
            }
            
            if (!missingFiles.isEmpty()) {
                log.error("[完整建模流程] 步骤7文件验证失败，缺失文件: {}", missingFiles);
                executionLog.append("步骤7文件验证失败，缺失文件: ").append(missingFiles).append("\n");

                result.setSuccess(false);
                result.setFileOrganizeResult(null);
                result.setFailedStep("文件整理输出");
                result.setErrorMessage("步骤6生成的必需文件缺失: " + missingFiles);
                result.setExecutionLog(executionLog.toString());
                result.setTotalElapsedTimeSeconds(
                    Duration.between(startTime, Instant.now()).toMillis() / 1000.0);

                updateJobStatus(userId, jobId, JobStatus.FAILED,
                    "步骤6生成的必需文件缺失: " + missingFiles);

                return result;
            }

            // 构建成功的FileOrganizeResult（movedFiles为文件路径字符串列表，非FileItem对象）
            String inputDirRelativePath = pathUtil.getRelativePath(userId, jobId, inputPath);
            List<String> movedFilePaths = new ArrayList<>();
            for (String fileName : existingFiles) {
                movedFilePaths.add(inputDirRelativePath + "/" + fileName);
            }

            FileOrganizeResult fileOrganizeResult = FileOrganizeResult.builder()
                .success(true)
                .movedFiles(movedFilePaths)
                .missingFiles(missingFiles)
                .targetDirectory(inputDirRelativePath)
                .movedFilesCount(movedFilePaths.size())
                .totalFilesCount(movedFilePaths.size())
                .errorMessage(null)
                .executionLog("步骤7文件验证通过（由步骤6的moltemplate-execution模式自动完成文件整理）")
                .build();

            executionLog.append("文件整理验证成功，已确认").append(existingFiles.size())
                .append("个文件存在于inputs/目录\n");
            executionLog.append("文件列表: ").append(existingFiles).append("\n");
            // 计算已确认存在的文件总大小
            long totalSizeBytes = 0;
            for (String fileName : existingFiles) {
                try {
                    totalSizeBytes += Files.size(inputPath.resolve(fileName));
                } catch (IOException e) {
                    log.warn("无法读取文件大小: {}", fileName);
                }
            }
            executionLog.append("文件总大小: ").append(totalSizeBytes).append(" bytes\n\n");

            result.setFileOrganizeResult(fileOrganizeResult);

            // ========== 从数据库统一读取target_properties ==========
            // 步骤7.5和步骤8共用同一份target_properties，确保数据源一致
            List<String> targetProperties = resolveTargetProperties(jobId);
            executionLog.append("目标计算属性: ").append(targetProperties).append("\n\n");

            // ========== 步骤7.5：使用Jinja2模板生成LAMMPS输入脚本 ==========
            log.info("[完整建模流程] 步骤7.5：使用Jinja2模板生成LAMMPS输入脚本");
            executionLog.append("步骤7.5：使用Jinja2模板生成LAMMPS输入脚本\n");

            try {
                // 调用LammpsTemplateService生成LAMMPS输入脚本
                Map<String, Object> scriptResult = lammpsTemplateService.generateInputScripts(
                        userId, jobId, targetProperties);

                result.setLammpsScriptResult(scriptResult);

                if (!Boolean.TRUE.equals(scriptResult.get("success"))) {
                    log.error("[完整建模流程] 步骤7.5 Jinja2脚本生成失败: {}", scriptResult.get("errors"));
                    executionLog.append("Jinja2脚本生成失败: ").append(scriptResult.get("errors")).append("\n");

                    result.setSuccess(false);
                    result.setFailedStep("step7.5_jinja2_script_generation");
                    result.setErrorMessage("Jinja2脚本生成失败: " + scriptResult.get("errors"));
                    result.setExecutionLog(executionLog.toString());
                    result.setTotalElapsedTimeSeconds(
                            Duration.between(startTime, Instant.now()).toMillis() / 1000.0);

                    updateJobStatus(userId, jobId, JobStatus.FAILED,
                            "Jinja2脚本生成失败: " + scriptResult.get("errors"));

                    return result;
                }

                executionLog.append("Jinja2脚本生成成功\n");
                executionLog.append("生成脚本列表: ").append(scriptResult.get("rendered_files")).append("\n");
                executionLog.append("验证结果: ").append(scriptResult.get("validation_passed")).append("\n\n");

                log.info("[完整建模流程] 步骤7.5完成: 生成脚本 {}", scriptResult.get("rendered_files"));
            } catch (Exception e) {
                log.error("[完整建模流程] 步骤7.5 Jinja2脚本生成异常: {}", e.getMessage(), e);
                executionLog.append("Jinja2脚本生成异常: ").append(e.getMessage()).append("\n");

                result.setSuccess(false);
                result.setFailedStep("step7.5_jinja2_script_generation");
                result.setErrorMessage("Jinja2脚本生成失败: " + e.getMessage());
                result.setExecutionLog(executionLog.toString());
                result.setTotalElapsedTimeSeconds(
                        Duration.between(startTime, Instant.now()).toMillis() / 1000.0);

                updateJobStatus(userId, jobId, JobStatus.FAILED,
                        "Jinja2脚本生成失败: " + e.getMessage());

                return result;
            }

            // ========== 步骤8：LAMMPS三阶段模拟执行 ==========
            log.info("[完整建模流程] 步骤8：LAMMPS三阶段模拟执行");
            executionLog.append("步骤8：LAMMPS三阶段模拟执行\n");

            try {
                // 使用步骤7.5之前统一读取的targetProperties，避免重复查询数据库
                SimulationJob job = simulationRepository.findById(jobId).orElse(null);

                // 调用MDExecutorService执行三阶段模拟
                CompletableFuture<String> future = mdExecutorService.executeSimulation(job);
                String lammpsResult = future.get(timeoutSeconds, TimeUnit.SECONDS);

                // 构建LAMMPS执行结果
                Map<String, Object> lammpsExecutionResult = new HashMap<>();
                lammpsExecutionResult.put("success", true);
                lammpsExecutionResult.put("result_summary", lammpsResult);
                lammpsExecutionResult.put("target_properties", targetProperties);

                result.setLammpsExecutionResult(lammpsExecutionResult);

                executionLog.append("LAMMPS三阶段模拟执行成功\n");
                executionLog.append("目标性质: ").append(targetProperties).append("\n\n");

                log.info("[完整建模流程] 步骤8完成: LAMMPS模拟执行成功");
            } catch (Exception e) {
                // 提取根因异常消息：CompletableFuture.get()抛出ExecutionException包装了实际异常
                String errorMessage = (e instanceof java.util.concurrent.ExecutionException && e.getCause() != null)
                        ? e.getCause().getMessage()
                        : e.getMessage();
                log.error("[完整建模流程] 步骤8 LAMMPS模拟执行异常: {}", errorMessage, e);
                executionLog.append("LAMMPS模拟执行异常: ").append(errorMessage).append("\n");

                result.setSuccess(false);
                result.setFailedStep("step8_lammps_execution");
                result.setErrorMessage("LAMMPS模拟执行失败: " + errorMessage);
                result.setExecutionLog(executionLog.toString());
                result.setTotalElapsedTimeSeconds(
                        Duration.between(startTime, Instant.now()).toMillis() / 1000.0);

                updateJobStatus(userId, jobId, JobStatus.FAILED,
                        "LAMMPS模拟执行失败: " + e.getMessage());

                return result;
            }

            // ========== 建模流程完成 ==========
            executionLog.append("=== 完整建模流程成功完成 ===\n");
            
            // 计算总执行耗时
            Duration totalElapsed = Duration.between(startTime, Instant.now());
            
            result.setSuccess(true);
            result.setTotalElapsedTimeSeconds(totalElapsed.toMillis() / 1000.0);
            result.setExecutionLog(executionLog.toString());
            
            // 更新任务状态为完成
            updateJobStatus(userId, jobId, JobStatus.COMPLETED, "完整建模流程成功完成");
            
            log.info("[完整建模流程] 建模流程完成: userId={}, jobId={}, 总耗时={}秒", 
                    userId, jobId, result.getTotalElapsedTimeSeconds());
            
            return result;

        } catch (Exception e) {
            // 处理所有异常
            log.error("[完整建模流程] 执行异常: userId={}, jobId={}, error={}", 
                    userId, jobId, e.getMessage(), e);
            
            executionLog.append("执行异常: ").append(e.getMessage()).append("\n");
            
            result.setSuccess(false);
            result.setFailedStep("未知步骤");
            result.setErrorMessage("执行异常: " + e.getMessage());
            result.setExecutionLog(executionLog.toString());
            result.setTotalElapsedTimeSeconds(Duration.between(startTime, Instant.now()).toMillis() / 1000.0);
            
            // 更新任务状态为失败
            updateJobStatus(userId, jobId, JobStatus.FAILED, "执行异常: " + e.getMessage());
            
            return result;
        }
    }

    /**
     * 从SimulationJob数据库记录中统一解析target_properties
     *
     * <p>所有服务统一通过该方法从数据库读取target_properties，确保数据源一致。
     * 解析逻辑复用LammpsTemplateService.parseTargetProperties()，支持JSON数组和逗号分隔两种格式。</p>
     *
     * <p>当数据库中target_properties为空时，默认返回["density"]。</p>
     *
     * @param jobId 任务ID
     * @return 目标计算属性列表
     */
    private List<String> resolveTargetProperties(Long jobId) {
        SimulationJob job = simulationRepository.findById(jobId).orElse(null);
        if (job != null && job.getTargetProperties() != null) {
            return lammpsTemplateService.parseTargetProperties(job.getTargetProperties());
        }
        // 数据库中无target_properties时使用默认值
        List<String> defaultProperties = new ArrayList<>();
        defaultProperties.add("density");
        log.info("[完整建模流程] target_properties为空，使用默认值: [density], jobId={}", jobId);
        return defaultProperties;
    }

    /**
     * 更新任务状态到数据库
     *
     * <p>该方法根据任务执行结果，更新任务状态到数据库。</p>
     * <p>状态包括：</p>
     * <ul>
     *   <li>PENDING - 待处理</li>
     *   <li>RUNNING - 运行中</li>
     *   <li>COMPLETED - 已完成</li>
     *   <li>FAILED - 失败</li>
     *   <li>CANCELLED - 已取消</li>
     * </ul>
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param status 任务状态
     * @param errorMessage 错误信息（失败时记录）
     */
    private void updateJobStatus(Long userId, Long jobId, String status, String errorMessage) {
        try {
            log.info("[任务状态更新] 更新任务状态: userId={}, jobId={}, status={}", userId, jobId, status);
            
            // 查询任务记录
            SimulationJob job = simulationRepository.findById(jobId).orElse(null);
            
            if (job == null) {
                log.warn("[任务状态更新] 任务不存在: jobId={}", jobId);
                return;
            }
            
            // 验证任务归属（确保用户只能更新自己的任务）
            if (!job.getUserId().equals(userId)) {
                log.error("[任务状态更新] 任务归属验证失败: userId={}, jobId={}, 实际userId={}", 
                    userId, jobId, job.getUserId());
                return;
            }
            
            // 更新任务状态
            job.setStatus(status);
            
            // 更新错误信息（失败时记录）
            if (JobStatus.FAILED.equals(status) && errorMessage != null) {
                job.setErrorMessage(errorMessage);
            }
            
            // 更新结束时间（完成或失败时记录）
            if (JobStatus.COMPLETED.equals(status) || JobStatus.FAILED.equals(status)) {
                job.setEndTime(LocalDateTime.now());
                
                // 计算执行耗时（如果有开始时间）
                if (job.getStartTime() != null) {
                    long executionTimeSeconds = Duration.between(job.getStartTime(), job.getEndTime()).toSeconds();
                    job.setExecutionTimeS(executionTimeSeconds);
                }
            }
            
            // 保存到数据库
            simulationRepository.save(job);
            
            log.info("[任务状态更新] 任务状态更新成功: userId={}, jobId={}, status={}", userId, jobId, status);
            
        } catch (Exception e) {
            log.error("[任务状态更新] 更新任务状态失败: userId={}, jobId={}, error={}", 
                userId, jobId, e.getMessage(), e);
        }
    }

    /**
     * 执行完整建模流程（带配方请求）
     *
     * <p>该方法首先将配方请求序列化为JSON文件，然后执行完整建模流程。</p>
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param request 配方请求对象
     * @return FullModelingResult 完整建模结果
     * @throws IOException 当配方文件序列化失败时抛出
     */
    public FullModelingResult executeFullModelingWithRequest(Long userId, Long jobId,
            FormulaRequest request) throws IOException {
        log.info("[完整建模流程] 开始执行（带配方请求）: userId={}, jobId={}", userId, jobId);

        // 步骤0：序列化配方到JSON文件
        Path formulaFile = serializeFormulaToJSON(userId, jobId, request);
        log.info("[完整建模流程] 配方JSON文件已保存: {}", formulaFile);

        // 将FormulaRequest中的targetProperties同步到SimulationJob，确保后续所有服务从同一数据源读取
        syncTargetPropertiesToJob(jobId, request.getTargetProperties());

        // 执行完整建模流程
        return executeFullModeling(userId, jobId, formulaFile.toString());
    }

    /**
     * 将targetProperties同步到SimulationJob数据库记录
     *
     * <p>确保FormulaRequest中携带的目标计算属性写入数据库，
     * 后续所有服务（LammpsTemplateService、MDExecutorService、PostProcessingService）
     * 统一从SimulationJob.targetProperties读取，保证数据源一致。</p>
     *
     * <p>JSON格式规范：target_properties存储为JSON数组，如 ["density","conductivity"]</p>
     *
     * @param jobId 任务ID
     * @param targetPropertiesList 目标计算属性列表（如["density","conductivity"]），为空时默认为["density"]
     */
    private void syncTargetPropertiesToJob(Long jobId, List<String> targetPropertiesList) {
        try {
            SimulationJob job = simulationRepository.findById(jobId).orElse(null);
            if (job == null) {
                log.warn("[targetProperties同步] 任务不存在，跳过同步: jobId={}", jobId);
                return;
            }

            // 确定目标属性列表：为空时使用默认值["density"]
            List<String> effectiveProperties = targetPropertiesList;
            if (effectiveProperties == null || effectiveProperties.isEmpty()) {
                effectiveProperties = new ArrayList<>();
                effectiveProperties.add("density");
                log.info("[targetProperties同步] targetProperties为空，使用默认值: [density]");
            }

            // 序列化为JSON数组格式，如 ["density","conductivity"]
            String targetPropertiesJson = objectMapper.writeValueAsString(effectiveProperties);
            job.setTargetProperties(targetPropertiesJson);
            simulationRepository.save(job);

            log.info("[targetProperties同步] 已同步到SimulationJob: jobId={}, targetProperties={}",
                    jobId, targetPropertiesJson);
        } catch (Exception e) {
            log.error("[targetProperties同步] 同步失败: jobId={}, error={}", jobId, e.getMessage(), e);
        }
    }

    public static class ModelingWorkflowResult {
        private boolean success;
        private String formulaFilePath;
        private PackmolResult packmolResult;
        private String errorMessage;

        public boolean getSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getFormulaFilePath() {
            return formulaFilePath;
        }

        public void setFormulaFilePath(String formulaFilePath) {
            this.formulaFilePath = formulaFilePath;
        }

        public PackmolResult getPackmolResult() {
            return packmolResult;
        }

        public void setPackmolResult(PackmolResult packmolResult) {
            this.packmolResult = packmolResult;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }
}