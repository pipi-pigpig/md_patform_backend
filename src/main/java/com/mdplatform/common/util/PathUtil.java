package com.mdplatform.common.util;

import com.mdplatform.common.config.StorageConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * 文件路径工具类
 * 
 * <p>该类是电解液MD计算平台的核心路径管理组件，负责统一生成和管理所有文件系统路径。
 * 遵循"路径唯一确定"和"零硬编码"原则，所有文件路径通过用户ID+任务ID+文件类型唯一确定。</p>
 * 
 * <p>核心功能：</p>
 * <ul>
 *   <li>生成任务相关的各类目录路径（输入、输出、临时、可视化、报告等）</li>
 *   <li>生成符合命名规范的文件名（LAMMPS脚本、轨迹文件、结果文件、临时文件）</li>
 *   <li>管理全局临时目录（packmol、moltemplate、mdsuite等工具的临时文件）</li>
 *   <li>提供路径校验和目录创建功能</li>
 *   <li>实现绝对路径与相对路径的相互转换</li>
 * </ul>
 * 
 * <p>目录结构规范：</p>
 * <pre>
 * md_platform_data/
 * ├── user_{userId}/
 * │   └── jobs/
 * │       └── job_{jobId}/
 * │           ├── inputs/              # 输入文件目录
 * │           ├── raw_outputs/         # 原始输出目录
 * │           ├── post_processing/     # 后处理结果目录
 * │           ├── temp/                # 任务临时文件目录
 * │           ├── visualization/       # 可视化文件目录
 * │           └── report/              # 报告文件目录
 * └── temp/                            # 全局临时文件目录
 *     ├── packmol_temp/                # Packmol临时文件
 *     ├── moltemplate_temp/            # Moltemplate临时文件
 *     └── mdsuite_temp/                # MDSuite临时文件
 * </pre>
 * 
 * @author MD Platform Team
 * @version 1.0
 * @since 2024-01-01
 */
@Component
public class PathUtil {

    /** 日志记录器 */
    private static final Logger logger = LoggerFactory.getLogger(PathUtil.class);

    /** 存储配置，包含根路径等配置信息 */
    private final StorageConfig storageConfig;

    /**
     * 构造函数，通过依赖注入获取存储配置
     * 
     * @param storageConfig 存储配置对象，包含文件存储根路径等配置
     */
    public PathUtil(StorageConfig storageConfig) {
        this.storageConfig = storageConfig;
    }

    /**
     * 校验用户ID和任务ID的有效性
     * 
     * <p>校验规则：</p>
     * <ul>
     *   <li>userId和jobId均不能为null</li>
     *   <li>userId和jobId必须大于0</li>
     * </ul>
     * 
     * @param userId 用户ID
     * @param jobId 任务ID
     * @throws NullPointerException 当userId或jobId为null时抛出
     * @throws IllegalArgumentException 当userId或jobId小于等于0时抛出
     */
    private void validateUserIdAndJobId(Long userId, Long jobId) {
        Objects.requireNonNull(userId, "userId不能为null");
        Objects.requireNonNull(jobId, "jobId不能为null");
        if (userId <= 0) {
            throw new IllegalArgumentException("userId必须大于0");
        }
        if (jobId <= 0) {
            throw new IllegalArgumentException("jobId必须大于0");
        }
    }

    /**
     * 获取文件存储根路径
     * 
     * <p>从配置文件读取根路径，并转换为绝对路径进行规范化处理。</p>
     * 
     * @return 规范化后的绝对根路径
     */
    private Path getRootPath() {
        return Paths.get(storageConfig.getRootPath()).toAbsolutePath().normalize();
    }

    /**
     * 获取任务根目录路径
     * 
     * <p>路径格式：{root_path}/user_{userId}/jobs/job_{jobId}/</p>
     * 
     * <p>每个任务拥有独立的目录树，确保不同任务文件互不干扰。</p>
     * 
     * @param userId 用户ID
     * @param jobId 任务ID
     * @return 任务根目录的绝对路径
     * @throws NullPointerException 当userId或jobId为null时抛出
     * @throws IllegalArgumentException 当userId或jobId无效时抛出
     */
    public Path getJobRootPath(Long userId, Long jobId) {
        validateUserIdAndJobId(userId, jobId);
        // 构建任务根路径：根目录/user_{userId}/jobs/job_{jobId}/
        Path path = getRootPath()
                .resolve("user_" + userId)
                .resolve("jobs")
                .resolve("job_" + jobId);
        logger.debug("生成任务根路径: userId={}, jobId={}, path={}", userId, jobId, path);
        return path;
    }

    /**
     * 获取输入文件目录路径
     * 
     * <p>路径格式：{root_path}/user_{userId}/jobs/job_{jobId}/inputs/</p>
     * 
     * <p>用于存储任务的输入文件，如分子结构文件、力场文件等。</p>
     * 
     * @param userId 用户ID
     * @param jobId 任务ID
     * @return 输入文件目录的绝对路径
     */
    public Path getInputPath(Long userId, Long jobId) {
        validateUserIdAndJobId(userId, jobId);
        Path path = getJobRootPath(userId, jobId).resolve("inputs");
        logger.debug("生成输入目录路径: userId={}, jobId={}, path={}", userId, jobId, path);
        return path;
    }

    /**
     * 获取输出文件目录路径
     * 
     * <p>路径格式：{root_path}/user_{userId}/jobs/job_{jobId}/raw_outputs/</p>
     * 
     * <p>用于存储计算引擎产生的原始输出文件。</p>
     * 
     * @param userId 用户ID
     * @param jobId 任务ID
     * @return 输出文件目录的绝对路径
     */
    public Path getOutputPath(Long userId, Long jobId) {
        return getRawOutputPath(userId, jobId);
    }

    /**
     * 获取原始输出文件目录路径
     * 
     * <p>路径格式：{root_path}/user_{userId}/jobs/job_{jobId}/raw_outputs/</p>
     * 
     * <p>用于存储计算引擎产生的原始输出文件，如LAMMPS输出文件等。</p>
     * 
     * @param userId 用户ID
     * @param jobId 任务ID
     * @return 原始输出文件目录的绝对路径
     */
    public Path getRawOutputPath(Long userId, Long jobId) {
        validateUserIdAndJobId(userId, jobId);
        Path path = getJobRootPath(userId, jobId).resolve("raw_outputs");
        logger.debug("生成原始输出目录路径: userId={}, jobId={}, path={}", userId, jobId, path);
        return path;
    }

    /**
     * 获取后处理结果目录路径
     * 
     * <p>路径格式：{root_path}/user_{userId}/jobs/job_{jobId}/post_processing/</p>
     * 
     * <p>用于存储后处理分析产生的结果文件，如密度分析、扩散系数等结果JSON文件。</p>
     * 
     * @param userId 用户ID
     * @param jobId 任务ID
     * @return 后处理结果目录的绝对路径
     */
    public Path getPostProcessingPath(Long userId, Long jobId) {
        validateUserIdAndJobId(userId, jobId);
        Path path = getJobRootPath(userId, jobId).resolve("post_processing");
        logger.debug("生成后处理目录路径: userId={}, jobId={}, path={}", userId, jobId, path);
        return path;
    }

    /**
     * 获取任务特定临时文件目录路径
     * 
     * <p>路径格式：{root_path}/user_{userId}/jobs/job_{jobId}/temp/</p>
     * 
     * <p>用于存储任务执行过程中产生的临时文件，任务完成后应清理。</p>
     * 
     * <p>注意：此方法用于任务级别的临时文件。如需获取全局工具临时目录（如packmol、moltemplate），
     * 请使用 {@link #getGlobalTempPath(String)} 方法。</p>
     * 
     * @param userId 用户ID
     * @param jobId 任务ID
     * @return 任务临时文件目录的绝对路径
     */
    public Path getTempPath(Long userId, Long jobId) {
        validateUserIdAndJobId(userId, jobId);
        Path path = getJobRootPath(userId, jobId).resolve("temp");
        logger.debug("生成任务临时文件目录路径: userId={}, jobId={}, path={}", userId, jobId, path);
        return path;
    }

    /**
     * 获取全局临时文件目录路径
     * 
     * <p>路径格式：{root_path}/temp/{tempType}_temp/</p>
     * 
     * <p>用于存储全局工具的临时文件，不与特定用户或任务关联。</p>
     * 
     * <p>支持的临时类型包括：</p>
     * <ul>
     *   <li>packmol - Packmol分子打包工具临时文件</li>
     *   <li>moltemplate - Moltemplate分子模板工具临时文件</li>
     *   <li>mdsuite - MDSuite分析工具临时文件</li>
     * </ul>
     * 
     * <p>示例：</p>
     * <pre>
     * Path packmolTemp = pathUtil.getGlobalTempPath("packmol");
     * // 返回: {root_path}/temp/packmol_temp/
     * </pre>
     * 
     * @param tempType 临时文件类型，如"packmol"、"moltemplate"、"mdsuite"
     * @return 全局临时文件目录的绝对路径
     * @throws NullPointerException 当tempType为null时抛出
     * @throws IllegalArgumentException 当tempType为空字符串时抛出
     */
    public Path getGlobalTempPath(String tempType) {
        // 校验临时类型参数
        Objects.requireNonNull(tempType, "tempType不能为null");
        if (tempType.trim().isEmpty()) {
            throw new IllegalArgumentException("tempType不能为空字符串");
        }
        
        // 规范化临时类型：转小写并移除特殊字符
        String normalizedType = tempType.toLowerCase().replaceAll("[^a-z0-9_]", "");
        
        // 构建全局临时目录路径：根目录/temp/{tempType}_temp/
        Path path = getRootPath()
                .resolve("temp")
                .resolve(normalizedType + "_temp");
        
        logger.debug("生成全局临时目录路径: tempType={}, path={}", tempType, path);
        return path;
    }

    /**
     * 获取可视化文件目录路径
     * 
     * <p>路径格式：{root_path}/user_{userId}/jobs/job_{jobId}/visualization/</p>
     * 
     * <p>用于存储可视化相关的文件，如渲染图像、动画文件等。</p>
     * 
     * @param userId 用户ID
     * @param jobId 任务ID
     * @return 可视化文件目录的绝对路径
     */
    public Path getVisualizationPath(Long userId, Long jobId) {
        validateUserIdAndJobId(userId, jobId);
        Path path = getJobRootPath(userId, jobId).resolve("visualization");
        logger.debug("生成可视化目录路径: userId={}, jobId={}, path={}", userId, jobId, path);
        return path;
    }

    /**
     * 获取报告文件目录路径
     * 
     * <p>路径格式：{root_path}/user_{userId}/jobs/job_{jobId}/report/</p>
     * 
     * <p>用于存储任务生成的分析报告文件，永久保留。</p>
     * 
     * @param userId 用户ID
     * @param jobId 任务ID
     * @return 报告文件目录的绝对路径
     */
    public Path getReportPath(Long userId, Long jobId) {
        validateUserIdAndJobId(userId, jobId);
        Path path = getJobRootPath(userId, jobId).resolve("report");
        logger.debug("生成报告目录路径: userId={}, jobId={}, path={}", userId, jobId, path);
        return path;
    }

    /**
     * 生成LAMMPS脚本文件名
     * 
     * <p>命名规范：in.{stage}</p>
     * 
     * <p>示例：</p>
     * <ul>
     *   <li>stage="minimization" -> "in.minimization"</li>
     *   <li>stage="NPT" -> "in.npt"</li>
     *   <li>stage="production" -> "in.production"</li>
     * </ul>
     * 
     * @param stage 脚本阶段标识，如"minimization"、"equilibration"、"production"等
     * @return 符合命名规范的LAMMPS脚本文件名
     * @throws NullPointerException 当stage为null时抛出
     * @throws IllegalArgumentException 当stage为空字符串时抛出
     */
    public String getLammpsScriptFilename(String stage) {
        Objects.requireNonNull(stage, "stage不能为null");
        if (stage.trim().isEmpty()) {
            throw new IllegalArgumentException("stage不能为空字符串");
        }
        // 规范化阶段名称：转小写并移除非字母数字下划线的字符
        String filename = "in." + stage.toLowerCase().replaceAll("[^a-z0-9_]", "");
        logger.debug("生成LAMMPS脚本文件名: stage={}, filename={}", stage, filename);
        return filename;
    }

    /**
     * 生成轨迹文件名
     * 
     * <p>命名规范：dump.{type}.lammpstrj</p>
     * 
     * <p>示例：</p>
     * <ul>
     *   <li>type="trajectory" -> "dump.trajectory.lammpstrj"</li>
     *   <li>type="velocity" -> "dump.velocity.lammpstrj"</li>
     * </ul>
     * 
     * @param type 轨迹类型，如"trajectory"、"velocity"等
     * @return 符合命名规范的轨迹文件名
     * @throws NullPointerException 当type为null时抛出
     * @throws IllegalArgumentException 当type为空字符串时抛出
     */
    public String getTrajectoryFilename(String type) {
        Objects.requireNonNull(type, "type不能为null");
        if (type.trim().isEmpty()) {
            throw new IllegalArgumentException("type不能为空字符串");
        }
        // 规范化类型名称：转小写并移除非字母数字下划线的字符
        String filename = "dump." + type.toLowerCase().replaceAll("[^a-z0-9_]", "") + ".lammpstrj";
        logger.debug("生成轨迹文件名: type={}, filename={}", type, filename);
        return filename;
    }

    /**
     * 生成后处理结果文件名
     * 
     * <p>命名规范：{property}_result.json</p>
     * 
     * <p>示例：</p>
     * <ul>
     *   <li>property="density" -> "density_result.json"</li>
     *   <li>property="diffusion_coefficient" -> "diffusion_coefficient_result.json"</li>
     * </ul>
     * 
     * @param property 属性名称，如"density"、"diffusion_coefficient"等
     * @return 符合命名规范的结果文件名
     * @throws NullPointerException 当property为null时抛出
     * @throws IllegalArgumentException 当property为空字符串时抛出
     */
    public String getResultFilename(String property) {
        Objects.requireNonNull(property, "property不能为null");
        if (property.trim().isEmpty()) {
            throw new IllegalArgumentException("property不能为空字符串");
        }
        // 规范化属性名称：转小写并移除非字母数字下划线的字符
        String filename = property.toLowerCase().replaceAll("[^a-z0-9_]", "") + "_result.json";
        logger.debug("生成结果文件名: property={}, filename={}", property, filename);
        return filename;
    }

    /**
     * 生成临时文件名
     * 
     * <p>命名规范：job_{jobId}_{name}.tmp</p>
     * 
     * <p>示例：</p>
     * <ul>
     *   <li>jobId=123, name="working" -> "job_123_working.tmp"</li>
     *   <li>jobId=456, name="cache_data" -> "job_456_cache_data.tmp"</li>
     * </ul>
     * 
     * <p>注意：临时文件应在任务完成后立即删除，保留时间不超过24小时。</p>
     * 
     * @param jobId 任务ID
     * @param name 临时文件名称标识
     * @return 符合命名规范的临时文件名
     * @throws NullPointerException 当jobId或name为null时抛出
     * @throws IllegalArgumentException 当参数无效时抛出
     */
    public String getTempFilename(Long jobId, String name) {
        Objects.requireNonNull(jobId, "jobId不能为null");
        Objects.requireNonNull(name, "name不能为null");
        if (jobId <= 0) {
            throw new IllegalArgumentException("jobId必须大于0");
        }
        if (name.trim().isEmpty()) {
            throw new IllegalArgumentException("name不能为空字符串");
        }
        // 规范化名称：转小写并移除非字母数字下划线的字符
        String safeName = name.toLowerCase().replaceAll("[^a-z0-9_]", "");
        String filename = "job_" + jobId + "_" + safeName + ".tmp";
        logger.debug("生成临时文件名: jobId={}, name={}, filename={}", jobId, name, filename);
        return filename;
    }

    /**
     * 创建任务所需的完整目录结构
     * 
     * <p>创建以下目录：</p>
     * <ul>
     *   <li>任务根目录</li>
     *   <li>输入文件目录 (inputs)</li>
     *   <li>原始输出目录 (raw_outputs)</li>
     *   <li>后处理目录 (post_processing)</li>
     *   <li>临时文件目录 (temp)</li>
     *   <li>可视化目录 (visualization)</li>
     *   <li>报告目录 (report)</li>
     * </ul>
     * 
     * <p>此方法在任务创建时自动调用，确保目录结构完整。</p>
     * 
     * @param userId 用户ID
     * @param jobId 任务ID
     * @throws NullPointerException 当userId或jobId为null时抛出
     * @throws IllegalArgumentException 当userId或jobId无效时抛出
     * @throws RuntimeException 当目录创建失败时抛出
     */
    public void createJobDirectories(Long userId, Long jobId) {
        validateUserIdAndJobId(userId, jobId);
        logger.info("创建任务目录结构: userId={}, jobId={}", userId, jobId);
        
        // 创建任务根目录及所有子目录
        ensureDirectoryExists(getJobRootPath(userId, jobId));
        ensureDirectoryExists(getInputPath(userId, jobId));
        ensureDirectoryExists(getOutputPath(userId, jobId));
        ensureDirectoryExists(getPostProcessingPath(userId, jobId));
        ensureDirectoryExists(getTempPath(userId, jobId));
        ensureDirectoryExists(getVisualizationPath(userId, jobId));
        ensureDirectoryExists(getReportPath(userId, jobId));
        
        logger.info("任务目录结构创建完成: userId={}, jobId={}", userId, jobId);
    }

    /**
     * 根据相对路径解析绝对路径
     * 
     * <p>将相对于任务根目录的相对路径转换为绝对路径。</p>
     * 
     * <p>安全特性：校验解析后的路径是否仍在任务根目录范围内，
     * 防止路径遍历攻击。</p>
     * 
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param relativePath 相对于任务根目录的相对路径
     * @return 解析后的绝对路径
     * @throws NullPointerException 当参数为null时抛出
     * @throws IllegalArgumentException 当relativePath为空字符串时抛出
     * @throws SecurityException 当相对路径超出任务根目录范围时抛出
     */
    public Path resolveAbsolutePath(Long userId, Long jobId, String relativePath) {
        validateUserIdAndJobId(userId, jobId);
        Objects.requireNonNull(relativePath, "relativePath不能为null");
        if (relativePath.trim().isEmpty()) {
            throw new IllegalArgumentException("relativePath不能为空字符串");
        }
        
        Path jobRoot = getJobRootPath(userId, jobId);
        // 解析相对路径为绝对路径
        Path absolutePath = jobRoot.resolve(relativePath).normalize();
        
        // 安全检查：确保解析后的路径仍在任务根目录范围内
        if (!absolutePath.startsWith(jobRoot)) {
            throw new SecurityException("相对路径不能超出任务根目录范围: " + relativePath);
        }
        
        logger.debug("解析绝对路径: userId={}, jobId={}, relativePath={}, absolutePath={}", 
                userId, jobId, relativePath, absolutePath);
        return absolutePath;
    }

    /**
     * 根据绝对路径获取相对路径
     * 
     * <p>将绝对路径转换为相对于任务根目录的相对路径。</p>
     * 
     * <p>数据库中只存储相对路径，绝对路径运行时动态生成。</p>
     * 
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param absolutePath 绝对路径
     * @return 相对于任务根目录的相对路径
     * @throws NullPointerException 当参数为null时抛出
     * @throws IllegalArgumentException 当绝对路径不在任务根目录范围内时抛出
     */
    public String getRelativePath(Long userId, Long jobId, Path absolutePath) {
        validateUserIdAndJobId(userId, jobId);
        Objects.requireNonNull(absolutePath, "absolutePath不能为null");
        
        Path jobRoot = getJobRootPath(userId, jobId);
        Path normalizedAbsolute = absolutePath.toAbsolutePath().normalize();
        
        // 校验绝对路径是否在任务根目录范围内
        if (!normalizedAbsolute.startsWith(jobRoot)) {
            throw new IllegalArgumentException("绝对路径不在任务根目录范围内: " + absolutePath);
        }
        
        // 计算相对路径
        String relativePath = jobRoot.relativize(normalizedAbsolute).toString();
        logger.debug("获取相对路径: userId={}, jobId={}, absolutePath={}, relativePath={}", 
                userId, jobId, absolutePath, relativePath);
        return relativePath;
    }

    /**
     * 校验路径是否存在且可读
     * 
     * <p>校验规则：</p>
     * <ul>
     *   <li>路径必须存在</li>
     *   <li>路径必须可读</li>
     * </ul>
     * 
     * @param path 待校验的路径
     * @return 校验通过返回true，否则返回false
     * @throws NullPointerException 当path为null时抛出
     */
    public boolean validatePath(Path path) {
        Objects.requireNonNull(path, "path不能为null");
        
        // 检查路径是否存在
        if (!Files.exists(path)) {
            logger.warn("路径不存在: {}", path);
            return false;
        }
        
        // 检查路径是否可读
        if (!Files.isReadable(path)) {
            logger.warn("路径不可读: {}", path);
            return false;
        }
        
        logger.debug("路径校验通过: {}", path);
        return true;
    }

    /**
     * 确保目录存在，如不存在则创建
     * 
     * <p>创建目录时会自动创建所有不存在的父目录。</p>
     * 
     * <p>此方法用于文件操作前的目录准备，确保路径存在性和权限正确。</p>
     * 
     * @param path 目录路径
     * @throws NullPointerException 当path为null时抛出
     * @throws IOException 当路径存在但不是目录时抛出
     * @throws RuntimeException 当目录创建失败时抛出
     */
    public void ensureDirectoryExists(Path path) {
        Objects.requireNonNull(path, "path不能为null");
        
        try {
            if (!Files.exists(path)) {
                // 创建目录及其所有父目录
                Files.createDirectories(path);
                logger.info("创建目录: {}", path);
            } else if (!Files.isDirectory(path)) {
                // 路径存在但不是目录
                throw new IOException("路径存在但不是目录: " + path);
            }
        } catch (IOException e) {
            logger.error("创建目录失败: {}", path, e);
            throw new RuntimeException("创建目录失败: " + path, e);
        }
    }
}