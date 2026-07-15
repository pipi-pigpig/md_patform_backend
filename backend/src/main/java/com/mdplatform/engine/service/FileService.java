package com.mdplatform.engine.service;

import com.mdplatform.common.util.PathUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 文件服务类
 *
 * <p>提供文件存储、读取、复制、移动和删除等操作的业务逻辑，
 * 所有文件路径通过PathUtil工具类生成，确保路径规范和用户/任务隔离。</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>创建任务工作空间目录结构</li>
 *   <li>存储输入文件、输出文件、后处理结果、临时文件、报告和可视化文件</li>
 *   <li>读取文件内容（字节数组和字符串）</li>
 *   <li>文件复制、移动和删除</li>
 *   <li>获取文件信息和目录文件列表</li>
 *   <li>写入文件内容（支持备份写入）</li>
 * </ul>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Service
@Slf4j
public class FileService {

    private final PathUtil pathUtil;
    private final AtomicFileService atomicFileService;

    /**
     * 构造函数
     *
     * @param pathUtil 路径工具类
     * @param atomicFileService 原子文件写入服务
     */
    public FileService(PathUtil pathUtil, AtomicFileService atomicFileService) {
        this.pathUtil = pathUtil;
        this.atomicFileService = atomicFileService;
    }

    /**
     * 创建任务工作空间目录结构
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     */
    public void createJobWorkspace(Long userId, Long jobId) {
        log.info("创建任务工作空间: userId={}, jobId={}", userId, jobId);
        pathUtil.createJobDirectories(userId, jobId);
        log.info("任务工作空间创建完成: userId={}, jobId={}", userId, jobId);
    }

    /**
     * 存储上传的输入文件到任务输入目录
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param file 上传的文件
     * @return 文件的相对路径
     * @throws IOException 当文件写入失败时抛出
     */
    public String storeInputFile(Long userId, Long jobId, MultipartFile file) throws IOException {
        log.info("存储输入文件: userId={}, jobId={}, filename={}", userId, jobId, file.getOriginalFilename());
        
        Path inputPath = pathUtil.getInputPath(userId, jobId);
        pathUtil.ensureDirectoryExists(inputPath);
        
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            originalFilename = "upload_" + System.currentTimeMillis();
        }
        
        String safeFilename = sanitizeFilename(originalFilename);
        Path targetPath = inputPath.resolve(safeFilename);
        
        try (InputStream inputStream = file.getInputStream()) {
            atomicFileService.writeAtomic(targetPath, inputStream);
        }
        
        String relativePath = pathUtil.getRelativePath(userId, jobId, targetPath);
        log.info("输入文件存储成功: userId={}, jobId={}, relativePath={}", userId, jobId, relativePath);
        return relativePath;
    }

    /**
     * 存储上传的输出文件到任务输出目录
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param file 上传的文件
     * @param filename 目标文件名
     * @return 文件的相对路径
     * @throws IOException 当文件写入失败时抛出
     */
    public String storeOutputFile(Long userId, Long jobId, MultipartFile file, String filename) throws IOException {
        log.info("存储输出文件: userId={}, jobId={}, filename={}", userId, jobId, filename);
        
        Path outputPath = pathUtil.getOutputPath(userId, jobId);
        pathUtil.ensureDirectoryExists(outputPath);
        
        String safeFilename = sanitizeFilename(filename);
        Path targetPath = outputPath.resolve(safeFilename);
        
        try (InputStream inputStream = file.getInputStream()) {
            atomicFileService.writeAtomic(targetPath, inputStream);
        }
        
        String relativePath = pathUtil.getRelativePath(userId, jobId, targetPath);
        log.info("输出文件存储成功: userId={}, jobId={}, relativePath={}", userId, jobId, relativePath);
        return relativePath;
    }

    /**
     * 存储后处理计算结果到后处理目录
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param property 物理属性名称（如density、conductivity）
     * @param content 结果JSON内容
     * @return 文件的相对路径
     * @throws IOException 当文件写入失败时抛出
     */
    public String storePostProcessingResult(Long userId, Long jobId, String property, String content) throws IOException {
        log.info("存储后处理结果: userId={}, jobId={}, property={}", userId, jobId, property);
        
        Path postProcessingPath = pathUtil.getPostProcessingPath(userId, jobId);
        pathUtil.ensureDirectoryExists(postProcessingPath);
        
        String filename = pathUtil.getResultFilename(property);
        Path targetPath = postProcessingPath.resolve(filename);
        
        atomicFileService.writeAtomic(targetPath, content);
        
        String relativePath = pathUtil.getRelativePath(userId, jobId, targetPath);
        log.info("后处理结果存储成功: userId={}, jobId={}, relativePath={}", userId, jobId, relativePath);
        return relativePath;
    }

    /**
     * 存储临时文件到临时目录
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param name 临时文件名称
     * @param content 文件内容字节数组
     * @return 文件的相对路径
     * @throws IOException 当文件写入失败时抛出
     */
    public String storeTempFile(Long userId, Long jobId, String name, byte[] content) throws IOException {
        log.info("存储临时文件: userId={}, jobId={}, name={}", userId, jobId, name);
        
        Path tempPath = pathUtil.getTempPath(userId, jobId);
        pathUtil.ensureDirectoryExists(tempPath);
        
        String filename = pathUtil.getTempFilename(jobId, name);
        Path targetPath = tempPath.resolve(filename);
        
        atomicFileService.writeAtomic(targetPath, content);
        
        String relativePath = pathUtil.getRelativePath(userId, jobId, targetPath);
        log.info("临时文件存储成功: userId={}, jobId={}, relativePath={}", userId, jobId, relativePath);
        return relativePath;
    }

    /**
     * 存储计算报告到报告目录
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param reportName 报告文件名
     * @param content 报告内容
     * @return 文件的相对路径
     * @throws IOException 当文件写入失败时抛出
     */
    public String storeReport(Long userId, Long jobId, String reportName, String content) throws IOException {
        log.info("存储报告: userId={}, jobId={}, reportName={}", userId, jobId, reportName);
        
        Path reportPath = pathUtil.getReportPath(userId, jobId);
        pathUtil.ensureDirectoryExists(reportPath);
        
        String safeFilename = sanitizeFilename(reportName);
        Path targetPath = reportPath.resolve(safeFilename);
        
        atomicFileService.writeAtomic(targetPath, content);
        
        String relativePath = pathUtil.getRelativePath(userId, jobId, targetPath);
        log.info("报告存储成功: userId={}, jobId={}, relativePath={}", userId, jobId, relativePath);
        return relativePath;
    }

    /**
     * 存储可视化文件到可视化目录
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param vizName 可视化文件名
     * @param content 文件内容字节数组
     * @return 文件的相对路径
     * @throws IOException 当文件写入失败时抛出
     */
    public String storeVisualization(Long userId, Long jobId, String vizName, byte[] content) throws IOException {
        log.info("存储可视化文件: userId={}, jobId={}, vizName={}", userId, jobId, vizName);
        
        Path vizPath = pathUtil.getVisualizationPath(userId, jobId);
        pathUtil.ensureDirectoryExists(vizPath);
        
        String safeFilename = sanitizeFilename(vizName);
        Path targetPath = vizPath.resolve(safeFilename);
        
        atomicFileService.writeAtomic(targetPath, content);
        
        String relativePath = pathUtil.getRelativePath(userId, jobId, targetPath);
        log.info("可视化文件存储成功: userId={}, jobId={}, relativePath={}", userId, jobId, relativePath);
        return relativePath;
    }

    /**
     * 读取文件内容为字节数组
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param relativePath 文件相对路径
     * @return 文件内容字节数组
     * @throws IOException 当文件不存在或读取失败时抛出
     */
    public byte[] readFile(Long userId, Long jobId, String relativePath) throws IOException {
        log.debug("读取文件: userId={}, jobId={}, relativePath={}", userId, jobId, relativePath);
        
        Path absolutePath = pathUtil.resolveAbsolutePath(userId, jobId, relativePath);
        
        if (!pathUtil.validatePath(absolutePath)) {
            throw new IOException("文件不存在或不可读: " + relativePath);
        }
        
        return Files.readAllBytes(absolutePath);
    }

    /**
     * 读取文件内容为字符串（UTF-8编码）
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param relativePath 文件相对路径
     * @return 文件内容字符串
     * @throws IOException 当文件不存在或读取失败时抛出
     */
    public String readFileAsString(Long userId, Long jobId, String relativePath) throws IOException {
        log.debug("读取文件为字符串: userId={}, jobId={}, relativePath={}", userId, jobId, relativePath);
        
        Path absolutePath = pathUtil.resolveAbsolutePath(userId, jobId, relativePath);
        
        if (!pathUtil.validatePath(absolutePath)) {
            throw new IOException("文件不存在或不可读: " + relativePath);
        }
        
        return Files.readString(absolutePath, StandardCharsets.UTF_8);
    }

    /**
     * 检查文件是否存在
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param relativePath 文件相对路径
     * @return true表示文件存在，false表示不存在
     */
    public boolean fileExists(Long userId, Long jobId, String relativePath) {
        log.debug("检查文件是否存在: userId={}, jobId={}, relativePath={}", userId, jobId, relativePath);
        
        try {
            Path absolutePath = pathUtil.resolveAbsolutePath(userId, jobId, relativePath);
            return Files.exists(absolutePath);
        } catch (Exception e) {
            log.warn("检查文件存在性失败: userId={}, jobId={}, relativePath={}, error={}", 
                    userId, jobId, relativePath, e.getMessage());
            return false;
        }
    }

    /**
     * 删除文件
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param relativePath 文件相对路径
     * @throws IOException 当文件删除失败时抛出
     */
    public void deleteFile(Long userId, Long jobId, String relativePath) throws IOException {
        log.info("删除文件: userId={}, jobId={}, relativePath={}", userId, jobId, relativePath);
        
        Path absolutePath = pathUtil.resolveAbsolutePath(userId, jobId, relativePath);
        
        if (Files.exists(absolutePath)) {
            Files.delete(absolutePath);
            log.info("文件删除成功: userId={}, jobId={}, relativePath={}", userId, jobId, relativePath);
        } else {
            log.warn("文件不存在，无需删除: userId={}, jobId={}, relativePath={}", userId, jobId, relativePath);
        }
    }

    /**
     * 复制文件
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param sourceRelativePath 源文件相对路径
     * @param targetRelativePath 目标文件相对路径
     * @throws IOException 当源文件不存在或复制失败时抛出
     */
    public void copyFile(Long userId, Long jobId, String sourceRelativePath, String targetRelativePath) throws IOException {
        log.info("复制文件: userId={}, jobId={}, from={}, to={}", userId, jobId, sourceRelativePath, targetRelativePath);
        
        Path sourcePath = pathUtil.resolveAbsolutePath(userId, jobId, sourceRelativePath);
        Path targetPath = pathUtil.resolveAbsolutePath(userId, jobId, targetRelativePath);
        
        if (!pathUtil.validatePath(sourcePath)) {
            throw new IOException("源文件不存在或不可读: " + sourceRelativePath);
        }
        
        atomicFileService.copyAtomic(sourcePath, targetPath);
        
        log.info("文件复制成功: userId={}, jobId={}, from={}, to={}", userId, jobId, sourceRelativePath, targetRelativePath);
    }

    /**
     * 移动文件
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param sourceRelativePath 源文件相对路径
     * @param targetRelativePath 目标文件相对路径
     * @throws IOException 当源文件不存在或移动失败时抛出
     */
    public void moveFile(Long userId, Long jobId, String sourceRelativePath, String targetRelativePath) throws IOException {
        log.info("移动文件: userId={}, jobId={}, from={}, to={}", userId, jobId, sourceRelativePath, targetRelativePath);
        
        Path sourcePath = pathUtil.resolveAbsolutePath(userId, jobId, sourceRelativePath);
        Path targetPath = pathUtil.resolveAbsolutePath(userId, jobId, targetRelativePath);
        
        if (!pathUtil.validatePath(sourcePath)) {
            throw new IOException("源文件不存在或不可读: " + sourceRelativePath);
        }
        
        atomicFileService.moveAtomic(sourcePath, targetPath);
        
        log.info("文件移动成功: userId={}, jobId={}, from={}, to={}", userId, jobId, sourceRelativePath, targetRelativePath);
    }

    /**
     * 获取文件详细信息（大小、类型、权限等）
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param relativePath 文件相对路径
     * @return 包含文件信息的Map对象
     * @throws IOException 当文件不存在或读取失败时抛出
     */
    public Map<String, Object> getFileInfo(Long userId, Long jobId, String relativePath) throws IOException {
        log.debug("获取文件信息: userId={}, jobId={}, relativePath={}", userId, jobId, relativePath);
        
        Path absolutePath = pathUtil.resolveAbsolutePath(userId, jobId, relativePath);
        
        if (!pathUtil.validatePath(absolutePath)) {
            throw new IOException("文件不存在或不可读: " + relativePath);
        }
        
        Map<String, Object> fileInfo = new HashMap<>();
        fileInfo.put("path", relativePath);
        fileInfo.put("absolutePath", absolutePath.toString());
        fileInfo.put("size", Files.size(absolutePath));
        fileInfo.put("isDirectory", Files.isDirectory(absolutePath));
        fileInfo.put("isRegularFile", Files.isRegularFile(absolutePath));
        fileInfo.put("lastModified", Files.getLastModifiedTime(absolutePath).toInstant());
        fileInfo.put("readable", Files.isReadable(absolutePath));
        fileInfo.put("writable", Files.isWritable(absolutePath));
        
        return fileInfo;
    }

    /**
     * 列出指定类型目录下的所有文件
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param directoryType 目录类型（如input、output、post_processing等）
     * @return 文件相对路径数组
     * @throws IOException 当目录读取失败时抛出
     */
    public String[] listFiles(Long userId, Long jobId, String directoryType) throws IOException {
        log.debug("列出目录文件: userId={}, jobId={}, directoryType={}", userId, jobId, directoryType);
        
        Path directoryPath = getDirectoryPathByType(userId, jobId, directoryType);
        
        if (!Files.exists(directoryPath)) {
            log.debug("目录不存在: userId={}, jobId={}, directoryType={}", userId, jobId, directoryType);
            return new String[0];
        }
        
        try (Stream<Path> stream = Files.list(directoryPath)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(path -> pathUtil.getRelativePath(userId, jobId, path))
                    .toArray(String[]::new);
        }
    }

    /**
     * 列出输入目录下的所有文件
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     * @return 文件相对路径数组
     * @throws IOException 当目录读取失败时抛出
     */
    public String[] listInputFiles(Long userId, Long jobId) throws IOException {
        log.debug("列出输入文件: userId={}, jobId={}", userId, jobId);
        return listFiles(userId, jobId, "input");
    }

    /**
     * 列出输出目录下的所有文件
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     * @return 文件相对路径数组
     * @throws IOException 当目录读取失败时抛出
     */
    public String[] listOutputFiles(Long userId, Long jobId) throws IOException {
        log.debug("列出输出文件: userId={}, jobId={}", userId, jobId);
        return listFiles(userId, jobId, "output");
    }

    /**
     * 列出后处理目录下的所有文件
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     * @return 文件相对路径数组
     * @throws IOException 当目录读取失败时抛出
     */
    public String[] listPostProcessingFiles(Long userId, Long jobId) throws IOException {
        log.debug("列出后处理文件: userId={}, jobId={}", userId, jobId);
        return listFiles(userId, jobId, "post_processing");
    }

    /**
     * 列出报告目录下的所有文件
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     * @return 文件相对路径数组
     * @throws IOException 当目录读取失败时抛出
     */
    public String[] listReportFiles(Long userId, Long jobId) throws IOException {
        log.debug("列出报告文件: userId={}, jobId={}", userId, jobId);
        return listFiles(userId, jobId, "report");
    }

    /**
     * 列出可视化目录下的所有文件
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     * @return 文件相对路径数组
     * @throws IOException 当目录读取失败时抛出
     */
    public String[] listVisualizationFiles(Long userId, Long jobId) throws IOException {
        log.debug("列出可视化文件: userId={}, jobId={}", userId, jobId);
        return listFiles(userId, jobId, "visualization");
    }

    /**
     * 写入字符串内容到指定文件
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param relativePath 文件相对路径
     * @param content 文件内容字符串
     * @throws IOException 当文件写入失败时抛出
     */
    public void writeContent(Long userId, Long jobId, String relativePath, String content) throws IOException {
        log.debug("写入文件内容: userId={}, jobId={}, relativePath={}", userId, jobId, relativePath);
        
        Path absolutePath = pathUtil.resolveAbsolutePath(userId, jobId, relativePath);
        
        Path parentDir = absolutePath.getParent();
        if (parentDir != null) {
            pathUtil.ensureDirectoryExists(parentDir);
        }
        
        atomicFileService.writeAtomic(absolutePath, content);
        
        log.info("文件内容写入成功: userId={}, jobId={}, relativePath={}", userId, jobId, relativePath);
    }

    /**
     * 写入字节数组内容到指定文件
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param relativePath 文件相对路径
     * @param content 文件内容字节数组
     * @throws IOException 当文件写入失败时抛出
     */
    public void writeContent(Long userId, Long jobId, String relativePath, byte[] content) throws IOException {
        log.debug("写入文件内容(bytes): userId={}, jobId={}, relativePath={}", userId, jobId, relativePath);
        
        Path absolutePath = pathUtil.resolveAbsolutePath(userId, jobId, relativePath);
        
        Path parentDir = absolutePath.getParent();
        if (parentDir != null) {
            pathUtil.ensureDirectoryExists(parentDir);
        }
        
        atomicFileService.writeAtomic(absolutePath, content);
        
        log.info("文件内容写入成功(bytes): userId={}, jobId={}, relativePath={}", userId, jobId, relativePath);
    }

    /**
     * 带备份写入文件内容，写入失败时自动从备份恢复
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param relativePath 文件相对路径
     * @param content 文件内容字节数组
     * @throws IOException 当文件写入和备份恢复都失败时抛出
     */
    public void writeContentWithBackup(Long userId, Long jobId, String relativePath, byte[] content) throws IOException {
        log.debug("带备份写入文件内容: userId={}, jobId={}, relativePath={}", userId, jobId, relativePath);
        
        Path absolutePath = pathUtil.resolveAbsolutePath(userId, jobId, relativePath);
        
        if (!pathUtil.validatePath(absolutePath) && !absolutePath.getParent().toFile().exists()) {
            pathUtil.ensureDirectoryExists(absolutePath.getParent());
        }
        
        atomicFileService.writeWithBackup(absolutePath, content);
        
        log.info("带备份文件内容写入成功: userId={}, jobId={}, relativePath={}", userId, jobId, relativePath);
    }

    /**
     * 获取文件的绝对路径
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param relativePath 文件相对路径
     * @return 文件的绝对路径
     */
    public Path getAbsolutePath(Long userId, Long jobId, String relativePath) {
        return pathUtil.resolveAbsolutePath(userId, jobId, relativePath);
    }

    /** 获取输入目录路径 */
    public Path getInputDirectoryPath(Long userId, Long jobId) {
        return pathUtil.getInputPath(userId, jobId);
    }

    /** 获取输出目录路径 */
    public Path getOutputDirectoryPath(Long userId, Long jobId) {
        return pathUtil.getOutputPath(userId, jobId);
    }

    /** 获取临时目录路径 */
    public Path getTempDirectoryPath(Long userId, Long jobId) {
        return pathUtil.getTempPath(userId, jobId);
    }

    /** 获取后处理目录路径 */
    public Path getPostProcessingDirectoryPath(Long userId, Long jobId) {
        return pathUtil.getPostProcessingPath(userId, jobId);
    }

    /** 获取报告目录路径 */
    public Path getReportDirectoryPath(Long userId, Long jobId) {
        return pathUtil.getReportPath(userId, jobId);
    }

    /** 获取可视化目录路径 */
    public Path getVisualizationDirectoryPath(Long userId, Long jobId) {
        return pathUtil.getVisualizationPath(userId, jobId);
    }

    /** 获取任务根目录路径 */
    public Path getJobRootDirectoryPath(Long userId, Long jobId) {
        return pathUtil.getJobRootPath(userId, jobId);
    }

    private Path getDirectoryPathByType(Long userId, Long jobId, String directoryType) {
        switch (directoryType.toLowerCase()) {
            case "input":
            case "inputs":
                return pathUtil.getInputPath(userId, jobId);
            case "output":
            case "outputs":
            case "raw_output":
            case "raw_outputs":
                return pathUtil.getOutputPath(userId, jobId);
            case "temp":
            case "temporary":
                return pathUtil.getTempPath(userId, jobId);
            case "post_processing":
            case "postprocessing":
                return pathUtil.getPostProcessingPath(userId, jobId);
            case "report":
            case "reports":
                return pathUtil.getReportPath(userId, jobId);
            case "visualization":
            case "visualizations":
                return pathUtil.getVisualizationPath(userId, jobId);
            default:
                throw new IllegalArgumentException("未知的目录类型: " + directoryType);
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            return "file_" + System.currentTimeMillis();
        }
        
        String sanitized = filename.toLowerCase()
                .replaceAll("[^a-z0-9._-]", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^_|_$", "");
        
        if (sanitized.isEmpty()) {
            return "file_" + System.currentTimeMillis();
        }
        
        return sanitized;
    }
}