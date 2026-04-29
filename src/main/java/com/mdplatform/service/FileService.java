package com.mdplatform.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@Slf4j
public class FileService {

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${app.results-dir:./results}")
    private String resultsDir;

    /**
     * 存储输入文件
     */
    public String storeInputFile(MultipartFile file, Long jobId) throws IOException {
        return storeFile(file, uploadDir, jobId != null ? jobId.toString() : null);
    }

    /**
     * 存储结果文件
     */
    public String storeResultFile(MultipartFile file, Long jobId) throws IOException {
        return storeFile(file, resultsDir, jobId != null ? jobId.toString() : null);
    }

    private String storeFile(MultipartFile file, String baseDir, String subDir) throws IOException {
        // 创建基础目录
        Path basePath = Paths.get(baseDir).toAbsolutePath().normalize();
        Files.createDirectories(basePath);

        // 创建子目录（如果提供）
        Path targetDir = basePath;
        if (subDir != null && !subDir.isEmpty()) {
            targetDir = basePath.resolve(subDir);
            Files.createDirectories(targetDir);
        }

        // 生成文件名
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String filename = UUID.randomUUID().toString() + extension;
        Path targetLocation = targetDir.resolve(filename);

        // 保存文件
        Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

        log.info("File saved: {}", targetLocation);
        return targetLocation.toString();
    }

    /**
     * 读取文件内容
     */
    public byte[] readFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (Files.exists(path)) {
            return Files.readAllBytes(path);
        }
        throw new IOException("File not found: " + filePath);
    }

    /**
     * 读取文件为字符串
     */
    public String readFileAsString(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (Files.exists(path)) {
                return new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8);
            }
        throw new IOException("File not found: " + filePath);
    }

    /**
     * 获取作业的输入文件列表
     */
    public String[] getJobInputFiles(Long jobId) throws IOException {
        Path jobDir = Paths.get(uploadDir, jobId.toString());
        if (Files.exists(jobDir)) {
            return Files.list(jobDir)
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .toArray(String[]::new);
        }
        return new String[0];
    }

    /**
     * 获取作业的结果文件列表
     */
    public String[] getJobResultFiles(Long jobId) throws IOException {
        Path jobDir = Paths.get(resultsDir, jobId.toString());
        if (Files.exists(jobDir)) {
            return Files.list(jobDir)
                    .filter(Files::isRegularFile)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .toArray(String[]::new);
        }
        return new String[0];
    }

    /**
     * 检查文件是否存在
     */
    public boolean fileExists(String filePath) {
        Path path = Paths.get(filePath);
        return Files.exists(path);
    }

    /**
     * 删除文件
     */
    public void deleteFile(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        Files.deleteIfExists(path);
    }

    /**
     * 创建输入文件目录
     */
    public Path createInputDirectory(Long jobId) throws IOException {
        Path dirPath = Paths.get(uploadDir, jobId.toString());
        Files.createDirectories(dirPath);
        return dirPath;
    }

    /**
     * 创建结果文件目录
     */
    public Path createResultDirectory(Long jobId) throws IOException {
        Path dirPath = Paths.get(resultsDir, jobId.toString());
        Files.createDirectories(dirPath);
        return dirPath;
    }
}