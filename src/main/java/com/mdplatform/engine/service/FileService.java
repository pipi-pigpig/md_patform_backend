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

@Service
@Slf4j
public class FileService {

    private final PathUtil pathUtil;
    private final AtomicFileService atomicFileService;

    public FileService(PathUtil pathUtil, AtomicFileService atomicFileService) {
        this.pathUtil = pathUtil;
        this.atomicFileService = atomicFileService;
    }

    public void createJobWorkspace(Long userId, Long jobId) {
        log.info("创建任务工作空间: userId={}, jobId={}", userId, jobId);
        pathUtil.createJobDirectories(userId, jobId);
        log.info("任务工作空间创建完成: userId={}, jobId={}", userId, jobId);
    }

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

    public byte[] readFile(Long userId, Long jobId, String relativePath) throws IOException {
        log.debug("读取文件: userId={}, jobId={}, relativePath={}", userId, jobId, relativePath);
        
        Path absolutePath = pathUtil.resolveAbsolutePath(userId, jobId, relativePath);
        
        if (!pathUtil.validatePath(absolutePath)) {
            throw new IOException("文件不存在或不可读: " + relativePath);
        }
        
        return Files.readAllBytes(absolutePath);
    }

    public String readFileAsString(Long userId, Long jobId, String relativePath) throws IOException {
        log.debug("读取文件为字符串: userId={}, jobId={}, relativePath={}", userId, jobId, relativePath);
        
        Path absolutePath = pathUtil.resolveAbsolutePath(userId, jobId, relativePath);
        
        if (!pathUtil.validatePath(absolutePath)) {
            throw new IOException("文件不存在或不可读: " + relativePath);
        }
        
        return Files.readString(absolutePath, StandardCharsets.UTF_8);
    }

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

    public String[] listInputFiles(Long userId, Long jobId) throws IOException {
        log.debug("列出输入文件: userId={}, jobId={}", userId, jobId);
        return listFiles(userId, jobId, "input");
    }

    public String[] listOutputFiles(Long userId, Long jobId) throws IOException {
        log.debug("列出输出文件: userId={}, jobId={}", userId, jobId);
        return listFiles(userId, jobId, "output");
    }

    public String[] listPostProcessingFiles(Long userId, Long jobId) throws IOException {
        log.debug("列出后处理文件: userId={}, jobId={}", userId, jobId);
        return listFiles(userId, jobId, "post_processing");
    }

    public String[] listReportFiles(Long userId, Long jobId) throws IOException {
        log.debug("列出报告文件: userId={}, jobId={}", userId, jobId);
        return listFiles(userId, jobId, "report");
    }

    public String[] listVisualizationFiles(Long userId, Long jobId) throws IOException {
        log.debug("列出可视化文件: userId={}, jobId={}", userId, jobId);
        return listFiles(userId, jobId, "visualization");
    }

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

    public void writeContentWithBackup(Long userId, Long jobId, String relativePath, byte[] content) throws IOException {
        log.debug("带备份写入文件内容: userId={}, jobId={}, relativePath={}", userId, jobId, relativePath);
        
        Path absolutePath = pathUtil.resolveAbsolutePath(userId, jobId, relativePath);
        
        if (!pathUtil.validatePath(absolutePath) && !absolutePath.getParent().toFile().exists()) {
            pathUtil.ensureDirectoryExists(absolutePath.getParent());
        }
        
        atomicFileService.writeWithBackup(absolutePath, content);
        
        log.info("带备份文件内容写入成功: userId={}, jobId={}, relativePath={}", userId, jobId, relativePath);
    }

    public Path getAbsolutePath(Long userId, Long jobId, String relativePath) {
        return pathUtil.resolveAbsolutePath(userId, jobId, relativePath);
    }

    public Path getInputDirectoryPath(Long userId, Long jobId) {
        return pathUtil.getInputPath(userId, jobId);
    }

    public Path getOutputDirectoryPath(Long userId, Long jobId) {
        return pathUtil.getOutputPath(userId, jobId);
    }

    public Path getTempDirectoryPath(Long userId, Long jobId) {
        return pathUtil.getTempPath(userId, jobId);
    }

    public Path getPostProcessingDirectoryPath(Long userId, Long jobId) {
        return pathUtil.getPostProcessingPath(userId, jobId);
    }

    public Path getReportDirectoryPath(Long userId, Long jobId) {
        return pathUtil.getReportPath(userId, jobId);
    }

    public Path getVisualizationDirectoryPath(Long userId, Long jobId) {
        return pathUtil.getVisualizationPath(userId, jobId);
    }

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