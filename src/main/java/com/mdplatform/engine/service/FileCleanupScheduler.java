package com.mdplatform.engine.service;

import com.mdplatform.common.config.StorageConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;

@Service
public class FileCleanupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(FileCleanupScheduler.class);

    private final StorageConfig storageConfig;

    public FileCleanupScheduler(StorageConfig storageConfig) {
        this.storageConfig = storageConfig;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupTempFiles() {
        logger.info("开始执行临时文件清理任务");
        
        Path rootPath = Paths.get(storageConfig.getRootPath()).toAbsolutePath().normalize();
        int retentionHours = storageConfig.getTempRetentionHours();
        
        if (!Files.exists(rootPath)) {
            logger.info("根目录不存在，跳过清理: {}", rootPath);
            return;
        }
        
        int deletedCount = 0;
        long freedBytes = 0;
        
        try (Stream<Path> userDirs = Files.list(rootPath)) {
            for (Path userDir : userDirs.toList()) {
                if (!Files.isDirectory(userDir) || !userDir.getFileName().toString().startsWith("user_")) {
                    continue;
                }
                
                Path jobsDir = userDir.resolve("jobs");
                if (!Files.exists(jobsDir)) {
                    continue;
                }
                
                try (Stream<Path> jobDirs = Files.list(jobsDir)) {
                    for (Path jobDir : jobDirs.toList()) {
                        if (!Files.isDirectory(jobDir) || !jobDir.getFileName().toString().startsWith("job_")) {
                            continue;
                        }
                        
                        Path tempDir = jobDir.resolve("temp");
                        if (!Files.exists(tempDir)) {
                            continue;
                        }
                        
                        CleanupResult result = cleanupTempDirectory(tempDir, retentionHours);
                        deletedCount += result.deletedCount;
                        freedBytes += result.freedBytes;
                    }
                } catch (IOException e) {
                    logger.error("扫描任务目录失败: {}", jobsDir, e);
                }
            }
        } catch (IOException e) {
            logger.error("扫描用户目录失败: {}", rootPath, e);
            return;
        }
        
        logger.info("临时文件清理完成: 删除文件数={}, 释放空间={}字节 ({}KB)", 
                deletedCount, freedBytes, freedBytes / 1024);
    }

    private CleanupResult cleanupTempDirectory(Path tempDir, int retentionHours) {
        int deletedCount = 0;
        long freedBytes = 0;
        
        Instant cutoffTime = Instant.now().minus(retentionHours, ChronoUnit.HOURS);
        
        try (Stream<Path> files = Files.list(tempDir)) {
            for (Path file : files.toList()) {
                if (!Files.isRegularFile(file) || !file.getFileName().toString().endsWith(".tmp")) {
                    continue;
                }
                
                try {
                    BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                    FileTime lastModifiedTime = attrs.lastModifiedTime();
                    
                    if (lastModifiedTime.toInstant().isBefore(cutoffTime)) {
                        long fileSize = attrs.size();
                        Files.delete(file);
                        deletedCount++;
                        freedBytes += fileSize;
                        logger.debug("删除过期临时文件: {}, 大小: {}字节", file, fileSize);
                    }
                } catch (IOException e) {
                    logger.error("删除临时文件失败: {}", file, e);
                }
            }
        } catch (IOException e) {
            logger.error("扫描临时目录失败: {}", tempDir, e);
        }
        
        return new CleanupResult(deletedCount, freedBytes);
    }

    private static class CleanupResult {
        final int deletedCount;
        final long freedBytes;
        
        CleanupResult(int deletedCount, long freedBytes) {
            this.deletedCount = deletedCount;
            this.freedBytes = freedBytes;
        }
    }
}