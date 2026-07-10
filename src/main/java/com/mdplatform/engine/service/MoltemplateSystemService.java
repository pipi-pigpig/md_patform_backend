package com.mdplatform.engine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdplatform.common.util.PathUtil;
import com.mdplatform.engine.dto.MoltemplateSystemResult;
import com.mdplatform.engine.util.JsonOutputParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Moltemplate系统文件构建服务
 * 
 * <p>该服务负责执行Moltemplate系统文件构建，用于根据配方信息和堆积后的PDB文件
 * 生成完整的Moltemplate系统描述文件（system.lt）。</p>
 * 
 * <p>核心功能：</p>
 * <ul>
 *   <li>调用Python脚本生成Moltemplate系统文件</li>
 *   <li>管理分子模板文件路径</li>
 *   <li>解析执行结果，构建返回对象</li>
 *   <li>处理执行失败情况，返回错误信息</li>
 * </ul>
 * 
 * <p>执行方式：</p>
 * <p>本服务通过DockerService在md-engine容器中执行Python脚本，
 * 因为Moltemplate仅在md-engine容器中安装，本地Windows环境不可用。</p>
 * 
 * <p>文件路径规范：</p>
 * <pre>
 * user_{userId}/jobs/job_{jobId}/
 * ├── inputs/
 * │   ├── system.lt               # Moltemplate系统描述文件（输出）
 * │   ├── system.data             # LAMMPS结构文件（Moltemplate生成）
 * │   ├── system.in.init          # 初始化设置文件
 * │   ├── system.in.settings      # 力场设置文件
 * │   ├── formula_config.json     # 配方配置文件
 * │   └── packed_system.pdb       # Packmol初始构型文件（输入）
 * </pre>
 * 
 * <p>路径映射：</p>
 * <pre>
 * 本地路径: data/md_platform_data/user_{userId}/jobs/job_{jobId}/inputs/
 * Docker路径: /workspace/data/user_{userId}/jobs/job_{jobId}/inputs/
 * </pre>
 * 
 * @author 电解液MD平台
 * @version 1.0.0
 * @since 2024-01-01
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MoltemplateSystemService {

    /** 路径工具类，用于生成符合规范的文件路径 */
    private final PathUtil pathUtil;
    
    /** Docker服务，用于在md-engine容器中执行命令 */
    private final DockerService dockerService;
    
    /** JSON解析器，用于解析Python脚本输出的JSON结果 */
    private final ObjectMapper objectMapper;

    /** md-engine容器名称 */
    @Value("${app.docker.md-container-name:md-engine}")
    private String mdContainerName;

    /**
     * 生成Moltemplate系统文件
     * 
     * <p>该方法执行完整的Moltemplate系统文件构建流程：</p>
     * <ol>
     *   <li>检查Docker容器是否运行</li>
     *   <li>创建任务所需的目录结构</li>
     *   <li>构建Python命令参数</li>
     *   <li>通过DockerService在md-engine容器中执行Python脚本</li>
     *   <li>解析执行结果，构建MoltemplateSystemResult对象</li>
     *   <li>验证system.lt文件是否正确生成</li>
     * </ol>
     * 
     * <p>注意：Moltemplate仅在md-engine Docker容器中安装，
     * 本方法通过DockerService.executeCommandInContainer()在容器中执行命令。</p>
     * 
     * @param userId 用户ID，用于确定文件存储路径
     * @param jobId 任务ID，用于确定文件存储路径
     * @param formulaFilePath 配方文件路径，包含分子组成和力场配置信息
     * @param packedPdbFilePath Packmol堆积后的PDB文件路径
     * @return MoltemplateSystemResult 执行结果，包含成功标志、system.lt文件路径、分子信息等
     */
    public MoltemplateSystemResult generateSystemFile(Long userId, Long jobId, 
            String formulaFilePath, String packedPdbFilePath) {
        log.info("[Moltemplate系统] 开始生成系统文件: userId={}, jobId={}", userId, jobId);
        log.info("[Moltemplate系统] 配方文件路径: {}", formulaFilePath);
        log.info("[Moltemplate系统] PDB文件路径: {}", packedPdbFilePath);

        // 记录开始时间，用于计算执行耗时
        Instant startTime = Instant.now();
        
        // 构建执行日志，用于记录执行过程和错误信息
        StringBuilder executionLog = new StringBuilder();

        try {
            // 步骤1：检查Docker容器是否运行
            if (!dockerService.isMDContainerRunning()) {
                log.error("[Moltemplate系统] md-engine容器未运行，无法执行Moltemplate");
                return buildErrorResult("md-engine容器未运行", "", startTime);
            }
            log.info("[Moltemplate系统] Docker容器运行状态检查通过");
            
            // 步骤2：创建任务所需的完整目录结构
            pathUtil.createJobDirectories(userId, jobId);
            log.info("[Moltemplate系统] 任务目录结构创建完成");
            
            // 步骤3：获取各类目录路径（本地路径）
            Path inputPath = pathUtil.getInputPath(userId, jobId);
            Path globalTempPath = pathUtil.getGlobalTempPath("moltemplate");
            
            // system.lt输出文件存放在inputs目录
            Path systemLtPath = inputPath.resolve("system.lt");
            
            // 步骤4：构建Docker容器内的路径
            String dockerInputPath = pathUtil.convertToDockerPath(inputPath);
            String dockerFormulaFilePath = pathUtil.convertToDockerPath(Path.of(formulaFilePath));
            String dockerPackedPdbFilePath = pathUtil.convertToDockerPath(Path.of(packedPdbFilePath));
            String dockerSystemLtPath = pathUtil.convertToDockerPath(systemLtPath);
            String dockerGlobalTempPath = pathUtil.convertToDockerPath(globalTempPath);
            
            // 计算分子模板库根目录的Docker路径（用于--template-library-path参数）
            // 通过获取某个分子模板路径的父目录来得到molecule_templates根目录
            Path moleculeTemplatesRoot = pathUtil.getMoleculeTemplatePath("EC").getParent();
            String dockerTemplateLibraryPath = pathUtil.convertToDockerPath(moleculeTemplatesRoot);
            
            // 计算力场库路径的Docker路径（用于--forcefield-library-path参数，可选）
            // 默认使用opls-aa力场，后续可从配方文件中动态获取
            Path forcefieldPath = pathUtil.getForcefieldPath("opls-aa");
            String dockerForcefieldLibraryPath = pathUtil.convertToDockerPath(forcefieldPath);

            log.info("[Moltemplate系统] Docker输入目录: {}", dockerInputPath);
            log.info("[Moltemplate系统] Docker配方文件: {}", dockerFormulaFilePath);
            log.info("[Moltemplate系统] Docker PDB文件: {}", dockerPackedPdbFilePath);
            log.info("[Moltemplate系统] Docker输出文件: {}", dockerSystemLtPath);
            log.info("[Moltemplate系统] Docker模板库路径: {}", dockerTemplateLibraryPath);
            log.info("[Moltemplate系统] Docker力场库路径: {}", dockerForcefieldLibraryPath);

            // 步骤5：构建在Docker容器中执行的命令
            List<String> command = buildDockerCommand(userId, jobId, 
                    dockerFormulaFilePath, dockerPackedPdbFilePath, dockerSystemLtPath,
                    dockerTemplateLibraryPath, "oplsaa", dockerForcefieldLibraryPath);
            log.info("[Moltemplate系统] Docker执行命令: {}", String.join(" ", command));

            // 记录执行日志
            executionLog.append("Moltemplate系统构建进程启动（Docker容器内）\n");
            executionLog.append("容器: ").append(mdContainerName).append("\n");
            executionLog.append("命令: ").append(String.join(" ", command)).append("\n");

            // 步骤6：通过DockerService在md-engine容器中执行命令
            String output = dockerService.executeCommandInContainer(mdContainerName, command, "/workspace");
            executionLog.append(output).append("\n");
            log.info("[Moltemplate系统] Python脚本执行完成");

            // 步骤7：检查输出是否包含错误信息
            // 注意：不能简单检查"error"字符串，因为Python日志和JSON结果中可能包含"error"字段
            // 优先检查JSON标记中的success字段，其次检查明显的Docker命令执行失败标记
            boolean hasError = false;
            String errorMsg = null;

            String jsonResult = com.mdplatform.engine.util.JsonOutputParser.extractJson(output);
            if (jsonResult != null) {
                try {
                    com.fasterxml.jackson.databind.JsonNode jsonNode =
                            new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonResult);
                    if (jsonNode.has("success") && !jsonNode.get("success").asBoolean(true)) {
                        hasError = true;
                        errorMsg = jsonNode.has("error") ? jsonNode.get("error").asText() : "Moltemplate执行失败";
                    }
                } catch (Exception e) {
                    // JSON解析失败，回退到检查明显的Docker命令执行失败标记
                    if (output.contains("Command failed") || output.contains("Failed to execute")) {
                        hasError = true;
                        errorMsg = "Moltemplate系统命令执行失败";
                    }
                }
            } else {
                // 没有JSON标记，回退到检查明显的Docker命令执行失败标记
                if (output.contains("Command failed") || output.contains("Failed to execute")) {
                    hasError = true;
                    errorMsg = "Moltemplate系统命令执行失败";
                }
            }

            if (hasError) {
                log.error("[Moltemplate系统] Moltemplate执行失败: {}", errorMsg);
                return buildErrorResult(errorMsg, executionLog.toString(), startTime);
            }

            // 步骤8：验证system.lt文件是否成功生成（检查本地路径）
            if (!Files.exists(systemLtPath)) {
                log.error("[Moltemplate系统] system.lt文件未生成: {}", systemLtPath);
                return buildErrorResult("system.lt文件未生成", executionLog.toString(), startTime);
            }
            log.info("[Moltemplate系统] system.lt文件生成成功: {}", systemLtPath);

            // 步骤9：解析Python脚本输出的JSON结果（如果存在）
            MoltemplateSystemResult result = parseExecutionResult(userId, jobId, 
                    systemLtPath, output, executionLog.toString(), startTime);
            
            log.info("[Moltemplate系统] 系统文件生成完成: userId={}, jobId={}, 耗时={}秒", 
                    userId, jobId, result.getElapsedTimeSeconds());

            return result;

        } catch (Exception e) {
            // 处理所有异常
            log.error("[Moltemplate系统] 执行异常: userId={}, jobId={}, error={}", 
                    userId, jobId, e.getMessage(), e);
            return buildErrorResult("执行异常: " + e.getMessage(), 
                    executionLog.toString(), startTime);}
        }

    /**
     * 构建在Docker容器中执行的Python命令
     * 
     * <p>命令格式：</p>
     * <pre>
     * bash -c "cd /workspace/scripts && python3 -m modeling.run_modeling
     *   --mode moltemplate-system
     *   --user-id {userId}
     *   --job-id {jobId}
     *   --formula-file {formulaFilePath}
     *   --packed-pdb-file {packedPdbFilePath}
     *   --output-file {systemLtPath}
     *   --template-library-path {templateLibraryPath}
     *   --forcefield-type {forcefieldType}
     *   --forcefield-library-path {forcefieldLibraryPath}"
     * </pre>
     * 
     * <p>参数对齐说明（与Python端run_modeling.py的argparse定义一致）：</p>
     * <ul>
     *   <li>--packed-pdb-file: Python端moltemplate-system模式必需参数，对应Packmol堆积后的PDB文件</li>
     *   <li>--output-file: Python端定义的输出文件路径参数（非--output）</li>
     *   <li>--template-library-path: Python端moltemplate-system模式必需参数，分子模板库根目录</li>
     *   <li>--forcefield-type: 力场类型，默认oplsaa</li>
     *   <li>--forcefield-library-path: 力场库路径，可选</li>
     * </ul>
     * 
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param formulaFilePath 配方文件路径（Docker路径）
     * @param packedPdbFilePath PDB文件路径（Docker路径）
     * @param systemLtPath system.lt输出文件路径（Docker路径）
     * @param templateLibraryPath 分子模板库根目录路径（Docker路径）
     * @param forcefieldType 力场类型，如"oplsaa"
     * @param forcefieldLibraryPath 力场库路径（Docker路径），可为null
     * @return 命令参数列表
     */
    private List<String> buildDockerCommand(Long userId, Long jobId, 
            String formulaFilePath, String packedPdbFilePath, String systemLtPath,
            String templateLibraryPath, String forcefieldType, String forcefieldLibraryPath) {
        // 使用bash -c方式执行，先cd到/workspace/scripts目录，
        // 确保python3 -m modeling.run_modeling能正确找到模块
        StringBuilder cmdBuilder = new StringBuilder();
        cmdBuilder.append("cd /workspace/scripts && python3 -m modeling.run_modeling");
        // 通过--mode参数指定执行模式为moltemplate-system
        cmdBuilder.append(" --mode moltemplate-system");
        cmdBuilder.append(" --user-id ").append(userId);
        cmdBuilder.append(" --job-id ").append(jobId);
        // 添加--job-dir参数（Python端moltemplate-system模式强制要求）
        cmdBuilder.append(" --job-dir /workspace/data/user_").append(userId)
                  .append("/jobs/job_").append(jobId);
        cmdBuilder.append(" --formula-file ").append(formulaFilePath);
        // 修复Bug C/E3: --pdb-file改为--packed-pdb-file，与Python端argparse定义对齐
        cmdBuilder.append(" --packed-pdb-file ").append(packedPdbFilePath);
        // 修复Bug B: --output改为--output-file，与Python端argparse定义对齐
        cmdBuilder.append(" --output-file ").append(systemLtPath);
        // 添加--result-json参数，将JSON结果保存到单独的文件（与system.lt路径分离）
        // 避免Python端write_result_json将JSON结果覆盖--output-file指定的system.lt文件
        cmdBuilder.append(" --result-json /workspace/data/user_").append(userId)
                  .append("/jobs/job_").append(jobId).append("/inputs/moltemplate_result.json");
        // 修复Bug C: 添加--template-library-path必需参数，Python端moltemplate-system模式强制要求
        cmdBuilder.append(" --template-library-path ").append(templateLibraryPath);
        // 添加--forcefield-type参数，Python端默认oplsaa，此处显式传递以保持一致性
        cmdBuilder.append(" --forcefield-type ").append(forcefieldType != null ? forcefieldType : "oplsaa");
        // 添加--forcefield-library-path参数（可选），仅当路径非空时传递
        if (forcefieldLibraryPath != null && !forcefieldLibraryPath.isEmpty()) {
            cmdBuilder.append(" --forcefield-library-path ").append(forcefieldLibraryPath);
        }
        List<String> command = Arrays.asList("bash", "-c", cmdBuilder.toString());
        return command;
    }

    /**
     * 解析Python脚本执行结果，构建MoltemplateSystemResult对象
     * 
     * <p>解析流程：</p>
     * <ol>
     *   <li>尝试从输出中提取JSON结果</li>
     *   <li>如果JSON解析成功，从中提取分子信息、原子数等</li>
     *   <li>如果JSON解析失败，使用默认值构建结果</li>
     *   <li>计算执行耗时</li>
     * </ol>
     * 
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param systemLtPath system.lt文件路径
     * @param output Python脚本输出
     * @param executionLog 执行日志
     * @param startTime 开始时间
     * @return MoltemplateSystemResult对象
     */
    private MoltemplateSystemResult parseExecutionResult(Long userId, Long jobId,
            Path systemLtPath, String output, String executionLog, Instant startTime) {
        
        // 计算执行耗时
        Duration elapsed = Duration.between(startTime, Instant.now());
        
        // 获取system.lt文件的相对路径
        String relativeSystemLtPath = pathUtil.getRelativePath(userId, jobId, systemLtPath);
        
        // 尝试从输出中解析JSON结果
        Map<String, Integer> moleculeInstances = new HashMap<>();
        List<String> moleculeTemplates = new ArrayList<>();
        Integer totalAtoms = 0;
        String forcefield = "oplsaa"; // 默认力场
        MoltemplateSystemResult.BoxSizeInfo boxSize = null;
        
        try {
            // 使用统一的JSON输出解析工具提取JSON部分
            String jsonContent = JsonOutputParser.extractJson(output);
            if (jsonContent != null && !jsonContent.isEmpty()) {
                JsonNode jsonNode = objectMapper.readTree(jsonContent);
                
                // 解析分子实例映射
                if (jsonNode.has("molecule_instances")) {
                    JsonNode instancesNode = jsonNode.get("molecule_instances");
                    instancesNode.fields().forEachRemaining(entry -> {
                        moleculeInstances.put(entry.getKey(), entry.getValue().asInt());
                    });
                }
                
                // 解析分子模板列表
                if (jsonNode.has("molecule_templates")) {
                    JsonNode templatesNode = jsonNode.get("molecule_templates");
                    for (JsonNode template : templatesNode) {
                        moleculeTemplates.add(template.asText());
                    }
                }
                
                // 解析总原子数
                if (jsonNode.has("total_atoms")) {
                    totalAtoms = jsonNode.get("total_atoms").asInt();
                }
                
                // 解析力场类型
                if (jsonNode.has("forcefield")) {
                    forcefield = jsonNode.get("forcefield").asText();
                }
                
                // 解析盒子尺寸
                if (jsonNode.has("box_size")) {
                    JsonNode boxNode = jsonNode.get("box_size");
                    boxSize = MoltemplateSystemResult.BoxSizeInfo.builder()
                            .x(boxNode.has("x") ? boxNode.get("x").asDouble() : null)
                            .y(boxNode.has("y") ? boxNode.get("y").asDouble() : null)
                            .z(boxNode.has("z") ? boxNode.get("z").asDouble() : null)
                            .build();
                }
                
                log.info("[Moltemplate系统] JSON结果解析成功: 分子数={}, 原子数={}", 
                        moleculeInstances.size(), totalAtoms);
            }
        } catch (Exception e) {
            log.warn("[Moltemplate系统] JSON解析失败，使用默认值: {}", e.getMessage());
        }
        
        // 构建成功结果对象
        return MoltemplateSystemResult.builder()
                .success(true)
                .systemFilePath(relativeSystemLtPath)
                .moleculeTemplates(moleculeTemplates)
                .moleculeInstances(moleculeInstances)
                .totalAtoms(totalAtoms)
                .boxSize(boxSize)
                .forcefield(forcefield)
                .executionLog(executionLog)
                .elapsedTimeSeconds(elapsed.toMillis() / 1000.0)
                .build();
    }

    /**
     * 构建错误结果对象
     * 
     * <p>当Moltemplate执行失败时，构建包含错误信息的返回结果。</p>
     * 
     * @param errorMessage 错误信息描述
     * @param executionLog 执行日志
     * @param startTime 开始时间，用于计算耗时
     * @return 包含错误信息的MoltemplateSystemResult对象
     */
    private MoltemplateSystemResult buildErrorResult(String errorMessage, 
            String executionLog, Instant startTime) {
        Duration elapsed = Duration.between(startTime, Instant.now());
        return MoltemplateSystemResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .executionLog(executionLog)
                .elapsedTimeSeconds(elapsed.toMillis() / 1000.0)
                .totalAtoms(0)
                .build();
    }

    /**
     * 获取分子模板文件路径
     * 
     * <p>根据分子名称获取对应的Moltemplate模板文件路径。</p>
     * 
     * <p>路径格式：{root_path}/system_templates/molecule_templates/{moleculeName}/{moleculeName}.lt</p>
     * 
     * @param moleculeName 分子名称，如"EC"、"DMC"、"Li"、"PF6"等
     * @return 分子模板文件（.lt文件）的绝对路径
     */
    public Path getMoleculeLtFilePath(String moleculeName) {
        Path templateDir = pathUtil.getMoleculeTemplatePath(moleculeName);
        return templateDir.resolve(moleculeName + ".lt");
    }

    /**
     * 获取分子PDB文件路径
     * 
     * <p>根据分子名称获取对应的PDB结构文件路径。</p>
     * 
     * <p>路径格式：{root_path}/system_templates/molecule_templates/{moleculeName}/{moleculeName}.pdb</p>
     * 
     * @param moleculeName 分子名称
     * @return 分子PDB文件的绝对路径
     */
    public Path getMoleculePdbFilePath(String moleculeName) {
        Path templateDir = pathUtil.getMoleculeTemplatePath(moleculeName);
        return templateDir.resolve(moleculeName + ".pdb");
    }

    /**
     * 验证分子模板文件是否存在
     * 
     * <p>检查指定分子的模板文件是否存在于系统模板库中。</p>
     * 
     * @param moleculeName 分子名称
     * @return true表示模板文件存在，false表示不存在
     */
    public boolean moleculeTemplateExists(String moleculeName) {
        Path ltFile = getMoleculeLtFilePath(moleculeName);
        Path pdbFile = getMoleculePdbFilePath(moleculeName);
        
        boolean exists = Files.exists(ltFile) && Files.exists(pdbFile);
        log.debug("[Moltemplate系统] 分子模板存在性检查: moleculeName={}, exists={}", 
                moleculeName, exists);
        
        return exists;
    }

    /**
     * 获取力场文件路径
     * 
     * <p>根据力场名称获取对应的力场参数文件路径。</p>
     * 
     * <p>路径格式：{root_path}/system_templates/force_fields/{forcefieldName}/{forcefieldName}.lt</p>
     * 
     * @param forcefieldName 力场名称，如"opls-aa"、"gaff"等
     * @return 力场文件的绝对路径
     */
    public Path getForcefieldFilePath(String forcefieldName) {
        Path forcefieldDir = pathUtil.getForcefieldPath(forcefieldName);
        return forcefieldDir.resolve(forcefieldName + ".lt");
    }
}