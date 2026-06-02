package com.mdplatform.engine.service;

import com.mdplatform.common.util.PathUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

@Service
@Slf4j
public class AtomicFileService {

    private static final String WRITING_SUFFIX = ".writing";
    private static final String BACKUP_SUFFIX = ".bak";

    private final PathUtil pathUtil;

    public AtomicFileService(PathUtil pathUtil) {
        this.pathUtil = pathUtil;
    }

    public void writeAtomic(Path targetPath, byte[] content) throws IOException {
        log.debug("开始原子写入字节数组: targetPath={}, size={}bytes", targetPath, content.length);
        
        Path tempPath = Paths.get(targetPath.toString() + WRITING_SUFFIX);
        
        try {
            Path parentDir = targetPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                log.debug("创建父目录: {}", parentDir);
            }
            
            Files.write(tempPath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.debug("临时文件写入成功: {}", tempPath);
            
            Files.move(tempPath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            log.info("原子写入完成: {}", targetPath);
            
        } catch (IOException e) {
            log.error("原子写入失败: targetPath={}, error={}", targetPath, e.getMessage(), e);
            throw e;
        } finally {
            deleteTempFileIfExists(tempPath);
        }
    }

    public void writeAtomic(Path targetPath, String content) throws IOException {
        log.debug("开始原子写入字符串: targetPath={}", targetPath);
        
        if (content == null) {
            throw new IllegalArgumentException("content不能为null");
        }
        
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        writeAtomic(targetPath, bytes);
    }

    public void writeAtomic(Path targetPath, InputStream inputStream) throws IOException {
        log.debug("开始原子写入流: targetPath={}", targetPath);
        
        if (inputStream == null) {
            throw new IllegalArgumentException("inputStream不能为null");
        }
        
        Path tempPath = Paths.get(targetPath.toString() + WRITING_SUFFIX);
        
        try {
            Path parentDir = targetPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                log.debug("创建父目录: {}", parentDir);
            }
            
            long bytesCopied = Files.copy(inputStream, tempPath, StandardCopyOption.REPLACE_EXISTING);
            log.debug("临时文件写入成功: {}, bytes={}", tempPath, bytesCopied);
            
            Files.move(tempPath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            log.info("原子写入完成: {}", targetPath);
            
        } catch (IOException e) {
            log.error("原子写入失败: targetPath={}, error={}", targetPath, e.getMessage(), e);
            throw e;
        } finally {
            deleteTempFileIfExists(tempPath);
        }
    }

    public void copyAtomic(Path sourcePath, Path targetPath) throws IOException {
        log.debug("开始原子复制: sourcePath={}, targetPath={}", sourcePath, targetPath);
        
        if (sourcePath == null || targetPath == null) {
            throw new IllegalArgumentException("sourcePath和targetPath不能为null");
        }
        
        if (!Files.exists(sourcePath)) {
            throw new IOException("源文件不存在: " + sourcePath);
        }
        
        Path tempPath = Paths.get(targetPath.toString() + WRITING_SUFFIX);
        
        try {
            Path parentDir = targetPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                log.debug("创建父目录: {}", parentDir);
            }
            
            Files.copy(sourcePath, tempPath, StandardCopyOption.REPLACE_EXISTING);
            log.debug("临时文件复制成功: {}", tempPath);
            
            Files.move(tempPath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            log.info("原子复制完成: {} -> {}", sourcePath, targetPath);
            
        } catch (IOException e) {
            log.error("原子复制失败: sourcePath={}, targetPath={}, error={}", 
                    sourcePath, targetPath, e.getMessage(), e);
            throw e;
        } finally {
            deleteTempFileIfExists(tempPath);
        }
    }

    public void moveAtomic(Path sourcePath, Path targetPath) throws IOException {
        log.debug("开始原子移动: sourcePath={}, targetPath={}", sourcePath, targetPath);
        
        if (sourcePath == null || targetPath == null) {
            throw new IllegalArgumentException("sourcePath和targetPath不能为null");
        }
        
        if (!Files.exists(sourcePath)) {
            throw new IOException("源文件不存在: " + sourcePath);
        }
        
        try {
            Path parentDir = targetPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                log.debug("创建父目录: {}", parentDir);
            }
            
            Files.move(sourcePath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            log.info("原子移动完成: {} -> {}", sourcePath, targetPath);
            
        } catch (IOException e) {
            log.debug("原子移动不支持，使用临时文件方式: sourcePath={}, targetPath={}", sourcePath, targetPath);
            
            Path tempPath = Paths.get(targetPath.toString() + WRITING_SUFFIX);
            
            try {
                Files.copy(sourcePath, tempPath, StandardCopyOption.REPLACE_EXISTING);
                log.debug("临时文件复制成功: {}", tempPath);
                
                Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                log.info("临时文件方式移动完成: {} -> {}", sourcePath, targetPath);
                
                Files.deleteIfExists(sourcePath);
                log.debug("源文件已删除: {}", sourcePath);
                
            } catch (IOException ex) {
                log.error("原子移动失败: sourcePath={}, targetPath={}, error={}", 
                        sourcePath, targetPath, ex.getMessage(), ex);
                throw ex;
            } finally {
                deleteTempFileIfExists(tempPath);
            }
        }
    }

    public void writeWithBackup(Path targetPath, byte[] content) throws IOException {
        log.debug("开始带备份的原子写入: targetPath={}, size={}bytes", targetPath, content.length);
        
        Path backupPath = Paths.get(targetPath.toString() + BACKUP_SUFFIX);
        boolean hasBackup = false;
        
        try {
            if (Files.exists(targetPath)) {
                Files.copy(targetPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                hasBackup = true;
                log.debug("备份文件创建成功: {}", backupPath);
            }
            
            writeAtomic(targetPath, content);
            log.info("带备份的原子写入完成: {}", targetPath);
            
            if (hasBackup) {
                Files.deleteIfExists(backupPath);
                log.debug("备份文件已删除: {}", backupPath);
            }
            
        } catch (IOException e) {
            log.error("带备份的原子写入失败: targetPath={}, error={}", targetPath, e.getMessage(), e);
            
            if (hasBackup && Files.exists(backupPath)) {
                try {
                    Files.copy(backupPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    log.info("从备份恢复成功: {}", targetPath);
                } catch (IOException restoreEx) {
                    log.error("从备份恢复失败: targetPath={}, backupPath={}, error={}", 
                            targetPath, backupPath, restoreEx.getMessage(), restoreEx);
                }
            }
            
            throw e;
        } finally {
            if (hasBackup) {
                deleteTempFileIfExists(backupPath);
            }
        }
    }

    private void deleteTempFileIfExists(Path tempPath) {
        if (tempPath != null && Files.exists(tempPath)) {
            try {
                Files.delete(tempPath);
                log.debug("临时文件已删除: {}", tempPath);
            } catch (IOException e) {
                log.warn("删除临时文件失败: {}, error={}", tempPath, e.getMessage());
            }
        }
    }
}