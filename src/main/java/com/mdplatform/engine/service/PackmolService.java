package com.mdplatform.engine.service;

import com.mdplatform.common.util.PathUtil;
import com.mdplatform.engine.dto.PackmolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Packmol分子堆积服务
 * 
 * <p>该服务负责执行Packmol分子堆积计算，用于将多个分子按照指定的空间约束
 * 堆积到模拟盒子中，生成初始构型文件。</p>
 * 
 * <p>核心功能：</p>
 * <ul>
 *   <li>执行Packmol堆积计算，生成初始PDB构型文件</li>
 *   <li>生成Packmol输入脚本文件</li>
 *   <li>解析PDB文件，统计原子数量</li>
 *   <li>管理临时文件和输出文件的路径</li>
 * </ul>
 * 
 * <p>执行方式：</p>
 * <p>本服务通过DockerService在md-engine容器中执行Packmol命令，
 * 因为Packmol仅在md-engine容器中安装，本地Windows环境不可用。</p>
 * 
 * <p>文件路径规范：</p>
 * <pre>
 * user_{userId}/jobs/job_{jobId}/
 * ├── inputs/
 * │   ├── packmol.inp           # Packmol输入脚本
 * │   └── packed_system.pdb      # Packmol初始构型文件（输出）
 * └── temp/
 *     └── packmol_temp/          # 全局临时目录（定时清理）
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
public class PackmolService {

    /** 路径工具类，用于生成符合规范的文件路径 */
    private final PathUtil pathUtil;
    
    /** 原子文件写入服务，确保文件写入的原子性 */
    private final AtomicFileService atomicFileService;
    
    /** Docker服务，用于在md-engine容器中执行命令 */
    private final DockerService dockerService;

    /** Packmol执行默认超时时间（秒） */
    private static final int DEFAULT_TIMEOUT_SECONDS = 3600;
    
    /** md-engine容器名称 */
    @Value("${app.docker.md-container-name:md-engine}")
    private String mdContainerName;

    /**
     * 执行Packmol分子堆积计算
     * 
     * <p>该方法执行完整的Packmol堆积流程：</p>
     * <ol>
     *   <li>创建任务所需的目录结构</li>
     *   <li>生成Packmol输入脚本</li>
     *   <li>通过DockerService在md-engine容器中执行Packmol命令</li>
     *   <li>验证PDB输出文件是否正确生成</li>
     *   <li>解析PDB文件统计原子数量</li>
     *   <li>返回执行结果</li>
     * </ol>
     * 
     * <p>注意：Packmol仅在md-engine Docker容器中安装，
     * 本方法通过DockerService.executeCommandInContainer()在容器中执行命令。</p>
     * 
     * @param userId 用户ID，用于确定文件存储路径
     * @param jobId 任务ID，用于确定文件存储路径
     * @param formulaFilePath 配方文件路径，包含分子堆积配置信息
     * @return PackmolResult 执行结果，包含成功标志、PDB文件路径、原子数量等信息
     */
    public PackmolResult executePackmol(Long userId, Long jobId, String formulaFilePath) {
        log.info("开始执行Packmol堆积: userId={}, jobId={}", userId, jobId);

        // 记录开始时间，用于计算执行耗时
        Instant startTime = Instant.now();
        
        // 构建执行日志，用于记录执行过程和错误信息
        StringBuilder executionLog = new StringBuilder();

        try {
            // 检查Docker容器是否运行
            if (!dockerService.isMDContainerRunning()) {
                log.error("md-engine容器未运行，无法执行Packmol");
                return buildErrorResult("md-engine容器未运行", "", startTime);
            }
            
            // 创建任务所需的完整目录结构
            pathUtil.createJobDirectories(userId, jobId);
            
            // 获取各类目录路径（本地路径）
            Path inputPath = pathUtil.getInputPath(userId, jobId);
            Path globalTempPath = pathUtil.getGlobalTempPath("packmol");

            // PDB输出文件存放在inputs目录（符合文档规范）
            // packed_system.pdb作为初始构型文件，供后续LAMMPS模拟使用
            Path pdbOutputPath = inputPath.resolve("packed_system.pdb");
            
            // Packmol输入脚本存放在inputs目录
            Path inputScriptPath = inputPath.resolve("packmol.inp");

            // 使用PathUtil统一方法将本地路径转换为Docker容器内路径（统一了原先分散在各Service中的重复实现）
            String dockerFormulaFilePath = pathUtil.convertToDockerPath(Path.of(formulaFilePath));
            // 任务根目录的Docker路径，用于传递--job-dir参数给Python脚本
            // Python脚本会根据job-dir自动确定输出文件路径，无需单独传递--output参数
            Path jobRootPath = pathUtil.getJobRootPath(userId, jobId);
            String dockerJobDirPath = pathUtil.convertToDockerPath(jobRootPath);

            // 计算分子模板库根目录的Docker路径（用于--template-dir参数）
            // Python脚本默认使用相对路径system_templates/molecule_templates/，
            // 在Docker CWD为/workspace/scripts时会解析为不存在的/workspace/scripts/system_templates/molecule_templates/，
            // 实际模板在/workspace/data/system_templates/molecule_templates/，因此需要显式传递
            Path moleculeTemplatesRoot = pathUtil.getMoleculeTemplatePath("EC").getParent();
            String dockerTemplateDir = pathUtil.convertToDockerPath(moleculeTemplatesRoot);
            log.info("Docker模板目录: {}", dockerTemplateDir);

            // 构建在Docker容器中执行的命令
            // 修复：移除不存在的--output参数，改用--job-dir参数（与MoltemplateExecutionService保持一致）
            List<String> command = buildDockerCommand(userId, jobId, dockerFormulaFilePath, dockerJobDirPath, dockerTemplateDir);
            log.info("Docker执行命令: {}", String.join(" ", command));

            // 记录执行日志
            executionLog.append("Packmol进程启动（Docker容器内）\n");
            executionLog.append("容器: ").append(mdContainerName).append("\n");
            executionLog.append("命令: ").append(String.join(" ", command)).append("\n");

            // 通过DockerService在md-engine容器中执行命令
            String output = dockerService.executeCommandInContainer(mdContainerName, command, "/workspace");
            executionLog.append(output).append("\n");

            // 检查输出是否包含错误信息
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
                        errorMsg = jsonNode.has("error") ? jsonNode.get("error").asText() : "Packmol执行失败";
                    }
                } catch (Exception e) {
                    // JSON解析失败，回退到检查明显的Docker命令执行失败标记
                    if (output.contains("Command failed") || output.contains("Failed to execute")) {
                        hasError = true;
                        errorMsg = "Packmol命令执行失败";
                    }
                }
            } else {
                // 没有JSON标记，回退到检查明显的Docker命令执行失败标记
                if (output.contains("Command failed") || output.contains("Failed to execute")) {
                    hasError = true;
                    errorMsg = "Packmol命令执行失败";
                }
            }

            if (hasError) {
                log.error("Packmol执行失败: {}", errorMsg);
                return buildErrorResult(errorMsg, executionLog.toString(), startTime);
            }

            // 验证PDB文件是否成功生成（检查本地路径）
            if (!Files.exists(pdbOutputPath)) {
                log.error("PDB文件未生成: {}", pdbOutputPath);
                return buildErrorResult("PDB文件未生成", executionLog.toString(), startTime);
            }

            // 解析PDB文件，统计原子数量
            int atomCount = parseAtomCount(pdbOutputPath);
            log.info("PDB文件生成成功，原子数: {}", atomCount);

            // 计算执行耗时
            Duration elapsed = Duration.between(startTime, Instant.now());

            // 构建成功结果对象
            PackmolResult result = PackmolResult.builder()
                    .success(true)
                    .pdbFilePath(pathUtil.getRelativePath(userId, jobId, pdbOutputPath))
                    .inputScriptPath(pathUtil.getRelativePath(userId, jobId, inputScriptPath))
                    .atomCount(atomCount)
                    .executionLog(executionLog.toString())
                    .elapsedTimeSeconds(elapsed.toMillis() / 1000.0)
                    .build();

            log.info("Packmol执行完成: userId={}, jobId={}, 原子数={}, 耗时={}秒", 
                    userId, jobId, atomCount, elapsed.toSeconds());

            return result;

        } catch (IOException e) {
            // 处理IO异常
            log.error("Packmol执行IO异常: userId={}, jobId={}, error={}", 
                    userId, jobId, e.getMessage(), e);
            return buildErrorResult("IO异常: " + e.getMessage(), 
                    executionLog.toString(), startTime);
        }
    }

    /**
     * 直接执行Packmol命令（不通过Python脚本）
     * 
     * <p>当Packmol输入脚本已准备好时，可直接调用此方法执行Packmol。</p>
     * 
     * @param userId 用户ID
     * @param jobId 任务ID
     * @return 执行结果
     */
    public PackmolResult executePackmolDirect(Long userId, Long jobId) {
        log.info("直接执行Packmol命令: userId={}, jobId={}", userId, jobId);

        Instant startTime = Instant.now();
        StringBuilder executionLog = new StringBuilder();

        try {
            // 检查Docker容器是否运行
            if (!dockerService.isMDContainerRunning()) {
                log.error("md-engine容器未运行，无法执行Packmol");
                return buildErrorResult("md-engine容器未运行", "", startTime);
            }

            // 获取输入脚本路径
            Path inputPath = pathUtil.getInputPath(userId, jobId);
            Path inputScriptPath = inputPath.resolve("packmol.inp");
            Path pdbOutputPath = inputPath.resolve("packed_system.pdb");

            // 确保输入脚本存在
            if (!Files.exists(inputScriptPath)) {
                log.error("Packmol输入脚本不存在: {}", inputScriptPath);
                return buildErrorResult("Packmol输入脚本不存在", "", startTime);
            }

            // 使用PathUtil统一方法将本地路径转换为Docker容器内路径（统一了原先分散在各Service中的重复实现）
            String dockerInputScriptPath = pathUtil.convertToDockerPath(inputScriptPath);
            String dockerWorkDir = pathUtil.convertToDockerPath(inputPath);

            // 构建Packmol执行命令
            List<String> command = Arrays.asList(
                "bash", "-c",
                String.format("cd %s && packmol < %s", dockerWorkDir, dockerInputScriptPath)
            );

            log.info("执行Packmol命令: {}", String.join(" ", command));
            executionLog.append("命令: ").append(String.join(" ", command)).append("\n");

            // 在Docker容器中执行
            String output = dockerService.executeCommandInContainer(mdContainerName, command, "/workspace");
            executionLog.append(output).append("\n");

            // 检查执行结果
            if (output.contains("Success!") || Files.exists(pdbOutputPath)) {
                int atomCount = parseAtomCount(pdbOutputPath);
                Duration elapsed = Duration.between(startTime, Instant.now());

                return PackmolResult.builder()
                        .success(true)
                        .pdbFilePath(pathUtil.getRelativePath(userId, jobId, pdbOutputPath))
                        .inputScriptPath(pathUtil.getRelativePath(userId, jobId, inputScriptPath))
                        .atomCount(atomCount)
                        .executionLog(executionLog.toString())
                        .elapsedTimeSeconds(elapsed.toMillis() / 1000.0)
                        .build();
            } else {
                return buildErrorResult("Packmol执行失败", executionLog.toString(), startTime);
            }

        } catch (Exception e) {
            log.error("Packmol执行异常", e);
            return buildErrorResult("执行异常: " + e.getMessage(), executionLog.toString(), startTime);
        }
    }

    /**
     * 构建在Docker容器中执行的Python命令
     *
     * <p>使用统一入口脚本 run_modeling.py，通过 --mode packmol 指定执行Packmol堆积模式。</p>
     * <p>使用 bash -c "cd /workspace/scripts && python3 -m modeling.run_modeling ..." 方式执行，
     * 与MoltemplateService保持一致，确保Python模块路径正确解析。</p>
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param formulaFilePath 配方文件路径（Docker路径）
     * @param jobDirPath 任务根目录路径（Docker路径），Python脚本据此确定输出路径
     * @param templateDirPath 分子模板库根目录路径（Docker路径），用于--template-dir参数
     * @return 命令参数列表
     */
    private List<String> buildDockerCommand(Long userId, Long jobId,
            String formulaFilePath, String jobDirPath, String templateDirPath) {
        // 使用bash -c方式执行，先cd到/workspace/scripts目录，
        // 确保python3 -m modeling.run_modeling能正确找到模块
        String cmdStr = "cd /workspace/scripts && python3 -m modeling.run_modeling"
                + " --mode packmol"
                + " --user-id " + userId
                + " --job-id " + jobId
                + " --formula-file " + formulaFilePath
                + " --job-dir " + jobDirPath
                + " --template-dir " + templateDirPath;
        List<String> command = Arrays.asList("bash", "-c", cmdStr);
        return command;
    }

    /**
     * 生成Packmol输入脚本文件
     * 
     * <p>根据分子信息和盒子尺寸生成Packmol输入脚本（.inp格式）。
     * 脚本包含堆积容差、随机种子、迭代次数等参数配置。</p>
     * 
     * <p>脚本格式示例：</p>
     * <pre>
     * tolerance 2.0
     * seed 12345
     * maxit 100
     * 
     * structure molecule1.pdb
     *   number 100
     *   inside box 0. 0. 0. 50.00 50.00 50.00
     * end structure
     * 
     * output /path/to/packed_system.pdb
     * </pre>
     * 
     * @param userId 用户ID，用于确定文件存储路径
     * @param jobId 任务ID，用于确定文件存储路径
     * @param molecules 分子信息列表，包含分子名称、PDB文件路径和数量
     * @param boxSize 模拟盒子尺寸信息（X、Y、Z三个方向）
     * @return 生成的输入脚本文件路径
     * @throws IOException 当文件写入失败时抛出
     */
    public Path generatePackmolInputScript(Long userId, Long jobId, 
            List<PackmolMoleculeInfo> molecules, BoxSizeInfo boxSize) throws IOException {
        log.info("生成Packmol输入脚本: userId={}, jobId={}", userId, jobId);

        // 确保输入目录存在
        pathUtil.ensureDirectoryExists(pathUtil.getInputPath(userId, jobId));
        
        // 输入脚本文件路径
        Path inputScriptPath = pathUtil.getInputPath(userId, jobId).resolve("packmol.inp");

        // 构建脚本内容
        StringBuilder scriptContent = new StringBuilder();
        
        // 添加全局参数配置
        scriptContent.append("tolerance 2.0\n");      // 堆积容差（埃），固定值
        scriptContent.append("seed 12345\n");         // 随机种子，确保可重复性
        scriptContent.append("maxit 100\n\n");        // 最大迭代次数

        // 添加分子结构配置
        for (PackmolMoleculeInfo mol : molecules) {
            scriptContent.append(String.format("structure %s\n", mol.getPdbFilePath()));
            scriptContent.append(String.format("  number %d\n", mol.getCount()));
            scriptContent.append(String.format("  inside box 0. 0. 0. %.2f %.2f %.2f\n",
                    boxSize.getX(), boxSize.getY(), boxSize.getZ()));
            scriptContent.append("end structure\n\n");
        }

        // 输出文件路径配置 - 存放在inputs目录
        // packed_system.pdb作为初始构型文件，供后续LAMMPS模拟使用
        Path pdbOutputPath = pathUtil.getInputPath(userId, jobId).resolve("packed_system.pdb");
        scriptContent.append(String.format("output %s\n", pdbOutputPath.toString()));

        // 使用原子写入方式保存脚本文件
        atomicFileService.writeAtomic(inputScriptPath, scriptContent.toString());
        log.info("Packmol输入脚本已生成: {}", inputScriptPath);

        return inputScriptPath;
    }

    /**
     * 解析PDB文件，统计原子数量
     * 
     * <p>遍历PDB文件，统计以"ATOM"或"HETATM"开头的行数，
     * 每一行代表一个原子记录。</p>
     * 
     * @param pdbFile PDB文件路径
     * @return 原子总数
     * @throws IOException 当文件读取失败时抛出
     */
    private int parseAtomCount(Path pdbFile) throws IOException {
        log.debug("解析PDB文件原子数: {}", pdbFile);

        int atomCount = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(Files.newInputStream(pdbFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // PDB文件中，ATOM和HETATM记录代表原子
                if (line.startsWith("ATOM") || line.startsWith("HETATM")) {
                    atomCount++;
                }
            }
        }

        return atomCount;
    }

    /**
     * 构建错误结果对象
     * 
     * <p>当Packmol执行失败时，构建包含错误信息的返回结果。</p>
     * 
     * @param errorMessage 错误信息描述
     * @param executionLog 执行日志
     * @param startTime 开始时间，用于计算耗时
     * @return 包含错误信息的PackmolResult对象
     */
    private PackmolResult buildErrorResult(String errorMessage, 
            String executionLog, Instant startTime) {
        Duration elapsed = Duration.between(startTime, Instant.now());
        return PackmolResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .executionLog(executionLog)
                .elapsedTimeSeconds(elapsed.toMillis() / 1000.0)
                .atomCount(0)
                .build();
    }

    /**
     * Packmol分子信息类
     * 
     * <p>封装参与堆积的分子信息，包括分子名称、PDB文件路径和分子数量。</p>
     */
    public static class PackmolMoleculeInfo {
        /** 分子名称 */
        private final String name;
        
        /** 分子PDB文件路径 */
        private final String pdbFilePath;
        
        /** 分子数量 */
        private final int count;

        /**
         * 构造函数
         * 
         * @param name 分子名称
         * @param pdbFilePath 分子PDB文件路径
         * @param count 分子数量
         */
        public PackmolMoleculeInfo(String name, String pdbFilePath, int count) {
            this.name = name;
            this.pdbFilePath = pdbFilePath;
            this.count = count;
        }

        /**
         * 获取分子名称
         * 
         * @return 分子名称
         */
        public String getName() {
            return name;
        }

        /**
         * 获取分子PDB文件路径
         * 
         * @return PDB文件路径
         */
        public String getPdbFilePath() {
            return pdbFilePath;
        }

        /**
         * 获取分子数量
         * 
         * @return 分子数量
         */
        public int getCount() {
            return count;
        }
    }

    /**
     * 模拟盒子尺寸信息类
     * 
     * <p>封装模拟盒子在X、Y、Z三个方向的尺寸信息。</p>
     */
    public static class BoxSizeInfo {
        /** X方向尺寸（埃） */
        private final double x;
        
        /** Y方向尺寸（埃） */
        private final double y;
        
        /** Z方向尺寸（埃） */
        private final double z;

        /**
         * 构造函数
         * 
         * @param x X方向尺寸（埃）
         * @param y Y方向尺寸（埃）
         * @param z Z方向尺寸（埃）
         */
        public BoxSizeInfo(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        /**
         * 获取X方向尺寸
         * 
         * @return X方向尺寸（埃）
         */
        public double getX() {
            return x;
        }

        /**
         * 获取Y方向尺寸
         * 
         * @return Y方向尺寸（埃）
         */
        public double getY() {
            return y;
        }

        /**
         * 获取Z方向尺寸
         * 
         * @return Z方向尺寸（埃）
         */
        public double getZ() {
            return z;
        }
    }
}