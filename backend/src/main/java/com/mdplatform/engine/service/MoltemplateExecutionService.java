package com.mdplatform.engine.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdplatform.common.util.PathUtil;
import com.mdplatform.engine.util.JsonOutputParser;
import com.mdplatform.engine.dto.MoltemplateExecutionResult;
import com.mdplatform.engine.dto.FileOrganizeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Moltemplate执行与文件整理服务
 * 
 * <p>该服务负责执行Moltemplate命令（步骤6）和整理LAMMPS输入文件（步骤7），
 * 将system.lt文件转换为LAMMPS可识别的输入文件，并整理到正确的目录结构。</p>
 * 
 * <p>核心功能：</p>
 * <ul>
 *   <li>步骤6：执行Moltemplate命令，生成LAMMPS输入文件（system.data、system.in.init、system.in.settings）</li>
 *   <li>步骤7：整理文件输出，将生成的文件移动到inputs/目录，符合文件输入输出规则</li>
 *   <li>调用Python脚本在Docker容器中执行Moltemplate命令</li>
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
 * ├── inputs/                      # 输入文件目录（步骤7的目标目录）
 * │   ├── system.lt                # Moltemplate系统描述文件
 * │   ├── system.data              # LAMMPS结构文件（步骤6生成）
 * │   ├── system.in.init           # 初始化设置文件（步骤6生成）
 * │   ├── system.in.settings       # 力场设置文件（步骤6生成）
 * │   ├── packmol.inp              # Packmol输入脚本
 * │   └── packed_system.pdb        # Packmol初始构型文件
 * └── temp/                        # 临时文件目录
 *     └── moltemplate_temp/        # Moltemplate临时文件（步骤6的工作目录）
 * </pre>
 * 
 * <p>路径映射：</p>
 * <pre>
 * 本地路径: data/md_platform_data/user_{userId}/jobs/job_{jobId}/
 * Docker路径: /workspace/data/user_{userId}/jobs/job_{jobId}/
 * </pre>
 * 
 * @author 电解液MD平台
 * @version 1.0.0
 * @since 2026-06-05
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MoltemplateExecutionService {

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
     * 执行Moltemplate命令（步骤6）
     * 
     * <p>该方法执行Moltemplate命令，将system.lt文件转换为LAMMPS输入文件：</p>
     * <ol>
     *   <li>检查Docker容器是否运行</li>
     *   <li>验证system.lt文件是否存在</li>
     *   <li>构建Python命令参数（--mode moltemplate-execution）</li>
     *   <li>通过DockerService在md-engine容器中执行Python脚本</li>
     *   <li>解析执行结果，构建MoltemplateExecutionResult对象</li>
     *   <li>验证生成的文件（system.data、system.in.init、system.in.settings）</li>
     * </ol>
     * 
     * <p>注意：Moltemplate仅在md-engine Docker容器中安装，
     * 本方法通过DockerService.executeCommandInContainer()在容器中执行命令。</p>
     * 
     * @param userId 用户ID，用于确定文件存储路径
     * @param jobId 任务ID，用于确定文件存储路径
     * @param systemLtFilePath system.lt文件的相对路径（相对于任务根目录）
     * @return MoltemplateExecutionResult 执行结果，包含成功标志、输出文件列表、执行时长等
     */
    public MoltemplateExecutionResult executeMoltemplate(Long userId, Long jobId, String systemLtFilePath) {
        log.info("[Moltemplate执行] 开始执行Moltemplate命令（步骤6）: userId={}, jobId={}", userId, jobId);
        log.info("[Moltemplate执行] system.lt文件路径: {}", systemLtFilePath);

        // 记录开始时间，用于计算执行耗时
        Instant startTime = Instant.now();
        
        // 构建执行日志，用于记录执行过程和错误信息
        StringBuilder executionLog = new StringBuilder();

        try {
            // 步骤1：检查Docker容器是否运行
            if (!dockerService.isMDContainerRunning()) {
                log.error("[Moltemplate执行] md-engine容器未运行，无法执行Moltemplate");
                return buildMoltemplateErrorResult("md-engine容器未运行", "", startTime);
            }
            log.info("[Moltemplate执行] Docker容器运行状态检查通过");
            
            // 步骤2：验证system.lt文件是否存在
            Path systemLtPath = pathUtil.resolveAbsolutePath(userId, jobId, systemLtFilePath);
            if (!Files.exists(systemLtPath)) {
                log.error("[Moltemplate执行] system.lt文件不存在: {}", systemLtPath);
                return buildMoltemplateErrorResult("system.lt文件不存在: " + systemLtFilePath, 
                        "", startTime);
            }
            log.info("[Moltemplate执行] system.lt文件验证通过: {}", systemLtPath);
            
            // 步骤3：获取各类目录路径（本地路径）
            Path inputPath = pathUtil.getInputPath(userId, jobId);
            // 获取任务根目录，用于传递--job-dir参数给Python脚本
            // Python脚本内部会基于job-dir拼接inputs/子目录，因此必须传任务根目录而非inputs目录，
            // 否则会导致路径双重嵌套（如 /inputs/inputs/）
            Path jobRootPath = pathUtil.getJobRootPath(userId, jobId);
            Path tempPath = pathUtil.getTempPath(userId, jobId);
            Path moltemplateTempPath = tempPath.resolve("moltemplate_temp");

            // 检查packed_system.pdb是否存在，用于传递-pdb参数给Moltemplate
            Path pdbPath = inputPath.resolve("packed_system.pdb");
            boolean hasPdbFile = Files.exists(pdbPath);

            // 使用PathUtil统一方法将本地路径转换为Docker容器内路径（统一了原先分散在各Service中的重复实现）
            String dockerSystemLtFilePath = pathUtil.convertToDockerPath(systemLtPath);
            // --job-dir必须传任务根目录的Docker路径，而非inputs目录路径
            String dockerJobRootPath = pathUtil.convertToDockerPath(jobRootPath);
            String dockerMoltemplateTempPath = pathUtil.convertToDockerPath(moltemplateTempPath);

            log.info("[Moltemplate执行] Docker system.lt文件: {}", dockerSystemLtFilePath);
            log.info("[Moltemplate执行] Docker任务根目录(--job-dir): {}", dockerJobRootPath);
            log.info("[Moltemplate执行] Docker临时目录: {}", dockerMoltemplateTempPath);

            // 步骤5：构建在Docker容器中执行的命令
            List<String> command = buildMoltemplateExecutionCommand(userId, jobId,
                    dockerSystemLtFilePath, dockerJobRootPath, hasPdbFile);
            log.info("[Moltemplate执行] Docker执行命令: {}", String.join(" ", command));

            // 记录执行日志
            executionLog.append("Moltemplate执行进程启动（Docker容器内）\n");
            executionLog.append("容器: ").append(mdContainerName).append("\n");
            executionLog.append("命令: ").append(String.join(" ", command)).append("\n");

            // 步骤6：通过DockerService在md-engine容器中执行命令
            String output = dockerService.executeCommandInContainer(mdContainerName, command, "/workspace");
            executionLog.append(output).append("\n");
            log.info("[Moltemplate执行] Python脚本执行完成");

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
                        errorMsg = "Moltemplate命令执行失败";
                    }
                }
            } else {
                // 没有JSON标记，回退到检查明显的Docker命令执行失败标记
                if (output.contains("Command failed") || output.contains("Failed to execute")) {
                    hasError = true;
                    errorMsg = "Moltemplate命令执行失败";
                }
            }

            if (hasError) {
                log.error("[Moltemplate执行] Moltemplate执行失败: {}", errorMsg);
                return buildMoltemplateErrorResult(errorMsg,
                        executionLog.toString(), startTime);
            }

            // 步骤8：验证生成的文件是否成功生成（检查本地路径）
            List<String> outputFiles = new ArrayList<>();
            Path systemDataPath = inputPath.resolve("system.data");
            Path systemInInitPath = inputPath.resolve("system.in.init");
            Path systemInSettingsPath = inputPath.resolve("system.in.settings");
            
            if (!Files.exists(systemDataPath)) {
                log.error("[Moltemplate执行] system.data文件未生成: {}", systemDataPath);
                return buildMoltemplateErrorResult("system.data文件未生成", 
                        executionLog.toString(), startTime);
            }
            
            if (!Files.exists(systemInInitPath)) {
                log.error("[Moltemplate执行] system.in.init文件未生成: {}", systemInInitPath);
                return buildMoltemplateErrorResult("system.in.init文件未生成", 
                        executionLog.toString(), startTime);
            }
            
            if (!Files.exists(systemInSettingsPath)) {
                log.error("[Moltemplate执行] system.in.settings文件未生成: {}", systemInSettingsPath);
                return buildMoltemplateErrorResult("system.in.settings文件未生成", 
                        executionLog.toString(), startTime);
            }
            
            // 步骤8.1：清理system.in.settings中的非ASCII字符（中文注释导致LAMMPS崩溃）
            // LAMMPS (23 Jun 2022 - Update 4)在处理UTF-8多字节字符时存在兼容性问题，
            // 会触发MPI_ABORT导致模拟崩溃。通过清理非ASCII字符确保兼容性。
            sanitizeAsciiOnlyFile(systemInSettingsPath);
            
            // 添加输出文件到列表（使用相对路径）
            outputFiles.add(pathUtil.getRelativePath(userId, jobId, systemDataPath));
            outputFiles.add(pathUtil.getRelativePath(userId, jobId, systemInInitPath));
            outputFiles.add(pathUtil.getRelativePath(userId, jobId, systemInSettingsPath));
            
            log.info("[Moltemplate执行] 所有必需文件生成成功");
            log.info("[Moltemplate执行] 输出文件列表: {}", outputFiles);

            // 步骤9：解析Python脚本输出的JSON结果（如果存在）
            MoltemplateExecutionResult result = parseMoltemplateExecutionResult(userId, jobId, 
                    outputFiles, output, executionLog.toString(), startTime, systemLtFilePath);
            
            log.info("[Moltemplate执行] Moltemplate命令执行完成: userId={}, jobId={}, 耗时={}秒", 
                    userId, jobId, result.getElapsedTimeSeconds());

            return result;

        } catch (Exception e) {
            // 处理所有异常
            log.error("[Moltemplate执行] 执行异常: userId={}, jobId={}, error={}", 
                    userId, jobId, e.getMessage(), e);
            return buildMoltemplateErrorResult("执行异常: " + e.getMessage(), 
                    executionLog.toString(), startTime);
        }
    }

    /**
     * 整理LAMMPS输入文件（步骤7）
     * 
     * <p>该方法将生成的LAMMPS输入文件整理到正确的目录结构：</p>
     * <ol>
     *   <li>检查Docker容器是否运行</li>
     *   <li>验证必需文件是否存在（system.lt、system.data、system.in.init、system.in.settings）</li>
     *   <li>构建Python命令参数（--mode file-organize）</li>
     *   <li>通过DockerService在md-engine容器中执行Python脚本</li>
     *   <li>解析执行结果，构建FileOrganizeResult对象</li>
     *   <li>验证文件完整性（文件存在、大小正确）</li>
     * </ol>
     * 
     * <p>文件整理操作将以下文件从moltemplate_temp/目录移动到inputs/目录：</p>
     * <ul>
     *   <li>system.data - LAMMPS结构文件（Moltemplate生成）</li>
     *   <li>system.in.init - 初始化设置文件（Moltemplate生成）</li>
     *   <li>system.in.settings - 力场设置文件（Moltemplate生成）</li>
     * </ul>
     * 
     * <p>注意：packmol.inp和packed_system.pdb已在inputs/目录中（由Packmol步骤生成），
     * 不在moltemplate_temp目录中，Python端的file-organize模式不会复制它们，
     * 因此不在此步骤的验证范围内。</p>
     * 
     * @param userId 用户ID，用于确定文件存储路径
     * @param jobId 任务ID，用于确定文件存储路径
     * @param sourceDirectory 源目录的相对路径（通常为temp/moltemplate_temp/）
     * @return FileOrganizeResult 整理结果，包含成功标志、文件列表、缺失文件列表等
     */
    public FileOrganizeResult organizeInputFiles(Long userId, Long jobId, String sourceDirectory) {
        log.info("[文件整理] 开始整理LAMMPS输入文件（步骤7）: userId={}, jobId={}", userId, jobId);
        log.info("[文件整理] 源目录: {}", sourceDirectory);

        // 记录开始时间，用于计算执行耗时
        Instant startTime = Instant.now();
        
        // 构建执行日志，用于记录执行过程和错误信息
        StringBuilder executionLog = new StringBuilder();

        try {
            // 步骤1：检查Docker容器是否运行
            if (!dockerService.isMDContainerRunning()) {
                log.error("[文件整理] md-engine容器未运行，无法执行文件整理");
                return buildFileOrganizeErrorResult("md-engine容器未运行", "", startTime);
            }
            log.info("[文件整理] Docker容器运行状态检查通过");
            
            // 步骤2：获取各类目录路径（本地路径）
            Path inputPath = pathUtil.getInputPath(userId, jobId);
            Path tempPath = pathUtil.getTempPath(userId, jobId);
            Path moltemplateTempPath = tempPath.resolve("moltemplate_temp");
            
            // 使用PathUtil统一方法将本地路径转换为Docker容器内路径（统一了原先分散在各Service中的重复实现）
            String dockerInputPath = pathUtil.convertToDockerPath(inputPath);
            String dockerMoltemplateTempPath = pathUtil.convertToDockerPath(moltemplateTempPath);

            log.info("[文件整理] Docker输入目录: {}", dockerInputPath);
            log.info("[文件整理] Docker临时目录: {}", dockerMoltemplateTempPath);

            // 步骤4：构建在Docker容器中执行的命令
            List<String> command = buildFileOrganizeCommand(userId, jobId, 
                    dockerMoltemplateTempPath, dockerInputPath);
            log.info("[文件整理] Docker执行命令: {}", String.join(" ", command));

            // 记录执行日志
            executionLog.append("文件整理进程启动（Docker容器内）\n");
            executionLog.append("容器: ").append(mdContainerName).append("\n");
            executionLog.append("命令: ").append(String.join(" ", command)).append("\n");

            // 步骤5：通过DockerService在md-engine容器中执行命令
            String output = dockerService.executeCommandInContainer(mdContainerName, command, "/workspace");
            executionLog.append(output).append("\n");
            log.info("[文件整理] Python脚本执行完成");

            // 步骤6：检查输出是否包含错误信息
            // 注意：不能简单检查"error"字符串，因为Python日志和JSON结果中可能包含"error"字段
            // 优先检查JSON标记中的success字段，其次检查明显的错误标记
            boolean hasError = false;
            String errorMsg = null;
            
            // 尝试从JSON标记中提取success字段判断是否成功
            String jsonResult = com.mdplatform.engine.util.JsonOutputParser.extractJson(output);
            if (jsonResult != null) {
                try {
                    com.fasterxml.jackson.databind.JsonNode jsonNode = 
                            new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonResult);
                    if (jsonNode.has("success") && !jsonNode.get("success").asBoolean(true)) {
                        hasError = true;
                        errorMsg = jsonNode.has("error") ? jsonNode.get("error").asText() : "文件整理失败";
                    }
                } catch (Exception e) {
                    // JSON解析失败，回退到字符串检查
                    if (output.contains("Command failed") || output.contains("Failed to execute")) {
                        hasError = true;
                        errorMsg = "文件整理命令执行失败";
                    }
                }
            } else {
                // 没有JSON标记，回退到检查明显的错误标记
                if (output.contains("Command failed") || output.contains("Failed to execute")) {
                    hasError = true;
                    errorMsg = "文件整理命令执行失败";
                }
            }
            
            if (hasError) {
                log.error("[文件整理] 文件整理失败: {}", errorMsg);
                return buildFileOrganizeErrorResult(errorMsg, 
                        executionLog.toString(), startTime);
            }

            // 步骤7：验证必需文件是否已移动到inputs目录（检查本地路径）
            List<String> movedFiles = new ArrayList<>();
            List<String> missingFiles = new ArrayList<>();
            
            // 检查必需文件
            // 注意：只验证从moltemplate_temp目录复制到inputs/目录的文件。
            // packmol.inp和packed_system.pdb已在inputs/目录中（由Packmol步骤生成），
            // 不在moltemplate_temp目录中，Python端的file-organize模式也不会复制它们
            // （因为它们不匹配.data或.in.*模式），因此不应列入必需文件验证列表。
            String[] requiredFiles = {
                "system.data",
                "system.in.init",
                "system.in.settings"
            };
            
            for (String filename : requiredFiles) {
                Path filePath = inputPath.resolve(filename);
                if (Files.exists(filePath)) {
                    movedFiles.add(pathUtil.getRelativePath(userId, jobId, filePath));
                    log.debug("[文件整理] 文件验证成功: {}", filePath);
                } else {
                    missingFiles.add(filename);
                    log.warn("[文件整理] 文件缺失: {}", filePath);
                }
            }
            
            // 如果有缺失文件，返回失败结果
            if (!missingFiles.isEmpty()) {
                log.error("[文件整理] 缺失文件数量: {}", missingFiles.size());
                log.error("[文件整理] 缺失文件列表: {}", missingFiles);
                return buildFileOrganizeErrorResult("缺失必需文件: " + String.join(", ", missingFiles), 
                        executionLog.toString(), startTime);
            }
            
            log.info("[文件整理] 所有必需文件验证成功");
            log.info("[文件整理] 已移动文件数量: {}", movedFiles.size());

            // 步骤7.5：修复system.in.init文件
            fixSystemInInit(inputPath);

            // 步骤8：解析Python脚本输出的JSON结果（如果存在）
            FileOrganizeResult result = parseFileOrganizeResult(userId, jobId, 
                    movedFiles, missingFiles, output, executionLog.toString(), startTime);
            
            log.info("[文件整理] 文件整理完成: userId={}, jobId={}, 移动文件数={}", 
                    userId, jobId, result.getMovedFilesCount());

            return result;

        } catch (Exception e) {
            // 处理所有异常
            log.error("[文件整理] 执行异常: userId={}, jobId={}, error={}", 
                    userId, jobId, e.getMessage(), e);
            return buildFileOrganizeErrorResult("执行异常: " + e.getMessage(), 
                    executionLog.toString(), startTime);
        }
    }

    /**
     * 修复moltemplate生成的system.in.init文件
     * 
     * <p>修复内容：</p>
     * <ol>
     *   <li>添加缺失的atom_style full</li>
     *   <li>添加缺失的kspace_style pppm 1.0e-4</li>
     *   <li>去除重复的力场样式定义</li>
     * </ol>
     * 
     * @param inputDir 输入文件目录路径（inputs/目录）
     */
    private void fixSystemInInit(Path inputDir) {
        Path initFile = inputDir.resolve("system.in.init");
        
        // 检查文件是否存在
        if (!Files.exists(initFile)) {
            log.warn("[init文件修复] system.in.init文件不存在: {}", initFile);
            return;
        }
        
        log.info("[init文件修复] 开始修复system.in.init文件: {}", initFile);
        
        try {
            // 读取文件内容
            String content = Files.readString(initFile, StandardCharsets.UTF_8);
            String[] lines = content.strip().split("\n");
            List<String> fixedLines = new ArrayList<>();
            
            // 检查并去重
            boolean hasAtomStyle = false;
            boolean hasKspaceStyle = false;
            Set<String> seenSettings = new LinkedHashSet<>();  // 用于去重
            
            // 需要去重的力场样式关键字
            Set<String> dedupKeywords = new LinkedHashSet<>(Arrays.asList(
                "units", "bond_style", "angle_style", "dihedral_style",
                "improper_style", "pair_style", "special_bonds"
            ));
            
            for (String line : lines) {
                String stripped = line.strip();
                
                // 跳过空行和注释
                if (stripped.isEmpty() || stripped.startsWith("#")) {
                    fixedLines.add(line);
                    continue;
                }
                
                // 检查atom_style
                if (stripped.startsWith("atom_style")) {
                    hasAtomStyle = true;
                    if (!seenSettings.contains("atom_style")) {
                        seenSettings.add("atom_style");
                        fixedLines.add(line);
                    } else {
                        // 重复的atom_style跳过
                        log.info("[init文件修复] 去除重复的atom_style定义: {}", stripped);
                    }
                    continue;
                }
                
                // 检查kspace_style
                if (stripped.startsWith("kspace_style")) {
                    hasKspaceStyle = true;
                    if (!seenSettings.contains("kspace_style")) {
                        seenSettings.add("kspace_style");
                        fixedLines.add(line);
                    } else {
                        // 重复的kspace_style跳过
                        log.info("[init文件修复] 去除重复的kspace_style定义: {}", stripped);
                    }
                    continue;
                }
                
                // 去重其他力场样式定义
                String settingKey = stripped.split("\\s+")[0];
                if (dedupKeywords.contains(settingKey)) {
                    if (!seenSettings.contains(settingKey)) {
                        seenSettings.add(settingKey);
                        fixedLines.add(line);
                    } else {
                        // 重复的定义跳过
                        log.info("[init文件修复] 去除重复的{}定义: {}", settingKey, stripped);
                    }
                    continue;
                }
                
                fixedLines.add(line);
            }
            
            // 如果缺少atom_style full，在开头添加
            if (!hasAtomStyle) {
                fixedLines.add(0, "atom_style      full");
                log.info("[init文件修复] 添加缺失的atom_style full");
            }
            
            // 如果缺少kspace_style，在special_bonds之后添加
            if (!hasKspaceStyle) {
                // 找到special_bonds的位置，在其后添加
                int insertPos = 0;
                for (int i = 0; i < fixedLines.size(); i++) {
                    if (fixedLines.get(i).strip().startsWith("special_bonds")) {
                        insertPos = i + 1;
                        break;
                    }
                }
                if (insertPos == 0) {
                    // 没有找到special_bonds，在末尾添加
                    insertPos = fixedLines.size();
                }
                fixedLines.add(insertPos, "kspace_style    pppm 1.0e-4");
                log.info("[init文件修复] 添加缺失的kspace_style pppm 1.0e-4");
            }
            
            // 原子写入：先写临时文件，再替换
            Path tempFile = initFile.resolveSibling(initFile.getFileName() + ".tmp");
            String fixedContent = String.join("\n", fixedLines) + "\n";
            Files.writeString(tempFile, fixedContent, StandardCharsets.UTF_8);
            Files.move(tempFile, initFile, StandardCopyOption.REPLACE_EXISTING, 
                       StandardCopyOption.ATOMIC_MOVE);
            
            log.info("[init文件修复] system.in.init文件修复完成");
            
        } catch (IOException e) {
            log.error("[init文件修复] 修复system.in.init文件失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 构建Moltemplate执行的Python命令
     *
     * <p>命令格式：</p>
     * <pre>
     * bash -c "cd /workspace/scripts && python3 -m modeling.run_modeling
     *   --mode moltemplate-execution
     *   --user-id {userId}
     *   --job-id {jobId}
     *   --system-lt-file {systemLtFilePath}
     *   --job-dir {jobRootPath}
     *   [--pdb-file {jobRootPath}/inputs/packed_system.pdb]"
     * </pre>
     *
     * <p>使用bash -c方式执行，先cd到/workspace/scripts目录，
     * 确保python3 -m modeling.run_modeling能正确找到模块。</p>
     *
     * <p>注意：--pdb-file参数传递的是完整的Docker容器内路径（如
     * /workspace/data/user_1/jobs/job_1/inputs/packed_system.pdb），
     * 而非仅文件名，因为Python脚本在Docker容器中执行时工作目录为/workspace。</p>
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param systemLtFilePath system.lt文件路径（Docker路径）
     * @param jobRootPath 任务根目录路径（Docker路径），Python脚本会基于此路径拼接inputs/子目录
     * @param hasPdbFile 是否存在packed_system.pdb文件
     * @return 命令参数列表
     */
    private List<String> buildMoltemplateExecutionCommand(Long userId, Long jobId,
            String systemLtFilePath, String jobRootPath, boolean hasPdbFile) {
        // 使用bash -c方式执行，先cd到/workspace/scripts目录，
        // 确保python3 -m modeling.run_modeling能正确找到模块
        StringBuilder cmdBuilder = new StringBuilder();
        cmdBuilder.append("cd /workspace/scripts && python3 -m modeling.run_modeling");
        cmdBuilder.append(" --mode moltemplate-execution");
        cmdBuilder.append(" --user-id ").append(userId);
        cmdBuilder.append(" --job-id ").append(jobId);
        cmdBuilder.append(" --system-lt-file ").append(systemLtFilePath);
        // --job-dir传任务根目录，Python脚本内部会拼接inputs/子目录来定位输入文件
        cmdBuilder.append(" --job-dir ").append(jobRootPath);

        // 如果存在packed_system.pdb文件，添加--pdb-file参数
        // 注意：必须传递完整的Docker容器内路径（而非仅文件名），
        // 因为Python脚本在Docker容器中执行时，工作目录为/workspace而非inputs/目录，
        // 仅传文件名会导致Python脚本找不到PDB文件
        if (hasPdbFile) {
            // 从任务根Docker路径拼接inputs子目录和PDB文件名，得到完整Docker路径
            String dockerPdbPath = jobRootPath + "/inputs/packed_system.pdb";
            cmdBuilder.append(" --pdb-file ").append(dockerPdbPath);
            log.info("[Moltemplate执行] 检测到PDB坐标文件，将使用Packmol堆积坐标，Docker路径: {}", dockerPdbPath);
        } else {
            log.warn("[Moltemplate执行] 未检测到PDB坐标文件，将使用模板默认坐标");
        }

        List<String> command = Arrays.asList("bash", "-c", cmdBuilder.toString());
        return command;
    }

    /**
     * 构建文件整理的Python命令
     * 
     * <p>命令格式：</p>
     * <pre>
     * bash -c "cd /workspace/scripts && python3 -m modeling.run_modeling
     *   --mode file-organize
     *   --user-id {userId}
     *   --job-id {jobId}
     *   --source-dir {sourceDir}
     *   --target-dir {inputPath}"
     * </pre>
     * 
     * <p>使用bash -c方式执行，先cd到/workspace/scripts目录，
     * 确保python3 -m modeling.run_modeling能正确找到模块。</p>
     * 
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param sourceDir 源目录路径（Docker路径）
     * @param targetDir 目标目录路径（Docker路径）
     * @return 命令参数列表
     */
    private List<String> buildFileOrganizeCommand(Long userId, Long jobId, 
            String sourceDir, String targetDir) {
        // 使用bash -c方式执行，先cd到/workspace/scripts目录，
        // 确保python3 -m modeling.run_modeling能正确找到模块
        String cmdStr = "cd /workspace/scripts && python3 -m modeling.run_modeling"
                + " --mode file-organize"
                + " --user-id " + userId
                + " --job-id " + jobId
                + " --source-dir " + sourceDir
                + " --target-dir " + targetDir;
        List<String> command = Arrays.asList("bash", "-c", cmdStr);
        return command;
    }

    /**
     * 解析Moltemplate执行结果，构建MoltemplateExecutionResult对象
     * 
     * <p>解析流程：</p>
     * <ol>
     *   <li>尝试从输出中提取JSON结果</li>
     *   <li>如果JSON解析成功，从中提取输出文件列表、执行时长等</li>
     *   <li>如果JSON解析失败，使用默认值构建结果</li>
     *   <li>计算执行耗时</li>
     * </ol>
     * 
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param outputFiles 输出文件列表（相对路径）
     * @param output Python脚本输出
     * @param executionLog 执行日志
     * @param startTime 开始时间
     * @param systemLtFilePath system.lt文件路径（相对路径）
     * @return MoltemplateExecutionResult对象
     */
    private MoltemplateExecutionResult parseMoltemplateExecutionResult(Long userId, Long jobId,
            List<String> outputFiles, String output, String executionLog, 
            Instant startTime, String systemLtFilePath) {
        
        // 计算执行耗时
        Duration elapsed = Duration.between(startTime, Instant.now());
        
        // 获取工作目录的相对路径
        Path inputPath = pathUtil.getInputPath(userId, jobId);
        String workingDirectory = pathUtil.getRelativePath(userId, jobId, inputPath);
        
        // 尝试从输出中解析JSON结果
        String command = "moltemplate.sh -atomstyle full system.lt";
        
        try {
            // 尝试从输出中提取JSON部分（使用统一的JSON解析工具）
            String jsonContent = JsonOutputParser.extractJson(output);
            if (jsonContent != null && !jsonContent.isEmpty()) {
                JsonNode jsonNode = objectMapper.readTree(jsonContent);
                
                // 解析执行的命令
                if (jsonNode.has("command")) {
                    command = jsonNode.get("command").asText();
                }
                
                log.info("[Moltemplate执行] JSON结果解析成功");
            }
        } catch (Exception e) {
            log.warn("[Moltemplate执行] JSON解析失败，使用默认值: {}", e.getMessage());
        }
        
        // 构建成功结果对象
        return MoltemplateExecutionResult.builder()
                .success(true)
                .command(command)
                .outputFiles(outputFiles)
                .elapsedTimeSeconds(elapsed.toMillis() / 1000.0)
                .executionLog(executionLog)
                .workingDirectory(workingDirectory)
                .systemLtFilePath(systemLtFilePath)
                .build();
    }

    /**
     * 解析文件整理结果，构建FileOrganizeResult对象
     * 
     * <p>解析流程：</p>
     * <ol>
     *   <li>尝试从输出中提取JSON结果</li>
     *   <li>如果JSON解析成功，从中提取文件列表、缺失文件列表等</li>
     *   <li>如果JSON解析失败，使用默认值构建结果</li>
     *   <li>计算执行耗时</li>
     * </ol>
     * 
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param movedFiles 已移动文件列表（相对路径）
     * @param missingFiles 缺失文件列表
     * @param output Python脚本输出
     * @param executionLog 执行日志
     * @param startTime 开始时间
     * @return FileOrganizeResult对象
     */
    private FileOrganizeResult parseFileOrganizeResult(Long userId, Long jobId,
            List<String> movedFiles, List<String> missingFiles, String output, 
            String executionLog, Instant startTime) {
        
        // 计算执行耗时
        Duration elapsed = Duration.between(startTime, Instant.now());
        
        // 获取目录的相对路径
        Path inputPath = pathUtil.getInputPath(userId, jobId);
        Path tempPath = pathUtil.getTempPath(userId, jobId);
        Path moltemplateTempPath = tempPath.resolve("moltemplate_temp");
        
        String targetDirectory = pathUtil.getRelativePath(userId, jobId, inputPath);
        String sourceDirectory = pathUtil.getRelativePath(userId, jobId, moltemplateTempPath);
        
        // 尝试从输出中解析JSON结果
        // 默认必需文件数量（仅包含从moltemplate_temp复制到inputs/的文件：
        // system.data、system.in.init、system.in.settings）
        Integer totalFilesCount = 3;
        Integer movedFilesCount = movedFiles.size();
        
        try {
            // 尝试从输出中提取JSON部分（使用统一的JSON解析工具）
            String jsonContent = JsonOutputParser.extractJson(output);
            if (jsonContent != null && !jsonContent.isEmpty()) {
                JsonNode jsonNode = objectMapper.readTree(jsonContent);
                
                // 解析文件数量统计
                if (jsonNode.has("total_files_count")) {
                    totalFilesCount = jsonNode.get("total_files_count").asInt();
                }
                
                if (jsonNode.has("moved_files_count")) {
                    movedFilesCount = jsonNode.get("moved_files_count").asInt();
                }
                
                log.info("[文件整理] JSON结果解析成功: 总文件数={}, 移动文件数={}", 
                        totalFilesCount, movedFilesCount);
            }
        } catch (Exception e) {
            log.warn("[文件整理] JSON解析失败，使用默认值: {}", e.getMessage());
        }
        
        // 构建成功结果对象
        return FileOrganizeResult.builder()
                .success(true)
                .movedFiles(movedFiles)
                .missingFiles(missingFiles)
                .targetDirectory(targetDirectory)
                .sourceDirectory(sourceDirectory)
                .executionLog(executionLog)
                .totalFilesCount(totalFilesCount)
                .movedFilesCount(movedFilesCount)
                .build();
    }

    /**
     * 构建Moltemplate执行的错误结果对象
     * 
     * <p>当Moltemplate执行失败时，构建包含错误信息的返回结果。</p>
     * 
     * @param errorMessage 错误信息描述
     * @param executionLog 执行日志
     * @param startTime 开始时间，用于计算耗时
     * @return 包含错误信息的MoltemplateExecutionResult对象
     */
    private MoltemplateExecutionResult buildMoltemplateErrorResult(String errorMessage, 
            String executionLog, Instant startTime) {
        Duration elapsed = Duration.between(startTime, Instant.now());
        return MoltemplateExecutionResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .executionLog(executionLog)
                .elapsedTimeSeconds(elapsed.toMillis() / 1000.0)
                .outputFiles(new ArrayList<>())
                .build();
    }

    /**
     * 构建文件整理的错误结果对象
     * 
     * <p>当文件整理失败时，构建包含错误信息的返回结果。</p>
     * 
     * @param errorMessage 错误信息描述
     * @param executionLog 执行日志
     * @param startTime 开始时间，用于计算耗时
     * @return 包含错误信息的FileOrganizeResult对象
     */
    private FileOrganizeResult buildFileOrganizeErrorResult(String errorMessage, 
            String executionLog, Instant startTime) {
        Duration elapsed = Duration.between(startTime, Instant.now());
        return FileOrganizeResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .executionLog(executionLog)
                .movedFiles(new ArrayList<>())
                .missingFiles(new ArrayList<>())
                .totalFilesCount(0)
                .movedFilesCount(0)
                .build();
    }

    /**
     * 清理文件中的非ASCII字符，确保LAMMPS兼容性
     *
     * <p>LAMMPS (23 Jun 2022 - Update 4)在处理UTF-8多字节字符（如中文注释）时存在兼容性问题，
     * 会触发MPI_ABORT导致模拟崩溃。该方法将文件中的非ASCII字符替换为空格，
     * 保留ASCII字符集（字母、数字、标点、括号、#注释标记等）。</p>
     *
     * <p>处理逻辑：</p>
     * <ol>
     *   <li>按UTF-8编码读取文件所有行</li>
     *   <li>将每行中所有非ASCII字符（码点大于127）替换为空格</li>
     *   <li>将处理后的内容写回原文件（原子写入方式）</li>
     * </ol>
     *
     * <p>此方法仅清理以下文件：</p>
     * <ul>
     *   <li>system.in.settings - 力场设置文件（由Moltemplate生成，含中文注释）</li>
     * </ul>
     *
     * <p>其他Moltemplate输出文件（system.data、system.in.init）不含中文注释，不需要清理。</p>
     *
     * @param filePath 需要清理的文件路径
     */
    private void sanitizeAsciiOnlyFile(Path filePath) {
        if (!Files.exists(filePath)) {
            log.warn("[ASCII清理] 文件不存在，跳过清理: {}", filePath);
            return;
        }
        try {
            log.info("[ASCII清理] 开始清理文件中的非ASCII字符和无效命令: {}", filePath);

            // 读取文件所有行
            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            List<String> cleanedLines = new ArrayList<>();
            int cleanedCharsCount = 0;
            int removedLinesCount = 0;

            // 逐行处理
            for (String line : lines) {
                // 移除dihedral_coeff和improper_coeff行
                // 原因：模板已将dihedral_style和improper_style设置为none，
                // 但Moltemplate生成的system.in.settings仍包含这些系数定义，
                // LAMMPS在style为none时拒绝接受coeff命令，导致崩溃。
                String stripped = line.strip();
                if (stripped.startsWith("dihedral_coeff")) {
                    log.debug("[ASCII清理] 移除dihedral_coeff行: {}", stripped);
                    removedLinesCount++;
                    continue;
                }
                if (stripped.startsWith("improper_coeff")) {
                    log.debug("[ASCII清理] 移除improper_coeff行: {}", stripped);
                    removedLinesCount++;
                    continue;
                }

                // 替换非ASCII字符为空格
                StringBuilder cleaned = new StringBuilder();
                for (char c : line.toCharArray()) {
                    if (c <= 127) {
                        // ASCII字符（0-127），保留原样
                        cleaned.append(c);
                    } else {
                        // 非ASCII字符（如中文），替换为空格
                        cleaned.append(' ');
                        cleanedCharsCount++;
                    }
                }
                cleanedLines.add(cleaned.toString());
            }

            // 直接写入文件（Docker挂载卷上ATOMIC_MOVE可能失败，使用直接写入）
            // 将内容拼接为字节数组，使用ISO-8859-1确保只写入ASCII字节
            String content = String.join("\n", cleanedLines) + "\n";
            Files.write(filePath, content.getBytes(StandardCharsets.ISO_8859_1));

            log.info("[ASCII清理] 文件清理完成: cleanedChars={}, removedLines={}, file={}",
                    cleanedCharsCount, removedLinesCount, filePath);

        } catch (Exception e) {
            // 清理失败不阻塞主流程，仅记录警告
            log.warn("[ASCII清理] 文件清理失败，LAMMPS可能因UTF-8字符崩溃: file={}, error={}",
                    filePath, e.getMessage());
        }
    }
}