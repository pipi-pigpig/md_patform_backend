package com.mdplatform.engine.service;

import com.mdplatform.common.util.PathUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 原子文件写入服务
 *
 * <p>该服务提供原子性的文件写入、复制和移动操作，确保在系统崩溃或异常情况下
 * 不会产生损坏的文件。所有写入操作采用"临时文件→重命名"的原子操作模式。</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>原子写入字节数组、字符串和输入流到目标文件</li>
 *   <li>原子复制文件（源文件→临时文件→目标文件）</li>
 *   <li>原子移动文件（支持跨文件系统移动）</li>
 *   <li>带备份的原子写入（写入失败时自动恢复）</li>
 *   <li>Windows平台ATOMIC_MOVE回退处理</li>
 * </ul>
 *
 * <p>Windows平台兼容性：</p>
 * <p>在Windows系统上，NTFS文件系统对原子移动操作的支持有限。
 * 当ATOMIC_MOVE操作不被支持时，自动回退到REPLACE_EXISTING模式，
 * 并记录警告日志。虽然回退模式不是严格原子的，但在大多数场景下
 * 仍能保证文件完整性。</p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 * @since 2024-01-01
 */
@Service
@Slf4j
public class AtomicFileService {

    /** 写入过程中的临时文件后缀 */
    private static final String WRITING_SUFFIX = ".writing";

    /** 备份文件后缀 */
    private static final String BACKUP_SUFFIX = ".bak";

    /** 路径工具类，用于生成符合规范的文件路径 */
    private final PathUtil pathUtil;

    /**
     * 构造函数，通过依赖注入获取路径工具类
     *
     * @param pathUtil 路径工具类实例
     */
    public AtomicFileService(PathUtil pathUtil) {
        this.pathUtil = pathUtil;
    }

    /**
     * 原子写入字节数组到目标文件
     *
     * <p>写入流程：</p>
     * <ol>
     *   <li>创建父目录（如不存在）</li>
     *   <li>将内容写入临时文件（.writing后缀）</li>
     *   <li>原子移动临时文件到目标路径</li>
     *   <li>清理临时文件（如发生异常）</li>
     * </ol>
     *
     * <p>Windows平台兼容：当ATOMIC_MOVE不支持时，自动回退到REPLACE_EXISTING。</p>
     *
     * @param targetPath 目标文件路径
     * @param content 要写入的字节数组
     * @throws IOException 当写入失败时抛出
     */
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
            
            atomicMoveWithFallback(tempPath, targetPath);
            log.info("原子写入完成: {}", targetPath);
            
        } catch (IOException e) {
            log.error("原子写入失败: targetPath={}, error={}", targetPath, e.getMessage(), e);
            throw e;
        } finally {
            deleteTempFileIfExists(tempPath);
        }
    }

    /**
     * 原子写入字符串到目标文件
     *
     * <p>将字符串按UTF-8编码转换为字节数组后，委托给{@link #writeAtomic(Path, byte[])}执行。</p>
     *
     * @param targetPath 目标文件路径
     * @param content 要写入的字符串内容
     * @throws IllegalArgumentException 当content为null时抛出
     * @throws IOException 当写入失败时抛出
     */
    public void writeAtomic(Path targetPath, String content) throws IOException {
        log.debug("开始原子写入字符串: targetPath={}", targetPath);
        
        if (content == null) {
            throw new IllegalArgumentException("content不能为null");
        }
        
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        writeAtomic(targetPath, bytes);
    }

    /**
     * 原子写入输入流到目标文件
     *
     * <p>写入流程：</p>
     * <ol>
     *   <li>创建父目录（如不存在）</li>
     *   <li>将输入流内容拷贝到临时文件</li>
     *   <li>原子移动临时文件到目标路径</li>
     *   <li>清理临时文件（如发生异常）</li>
     * </ol>
     *
     * @param targetPath 目标文件路径
     * @param inputStream 输入流
     * @throws IllegalArgumentException 当inputStream为null时抛出
     * @throws IOException 当写入失败时抛出
     */
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
            
            atomicMoveWithFallback(tempPath, targetPath);
            log.info("原子写入完成: {}", targetPath);
            
        } catch (IOException e) {
            log.error("原子写入失败: targetPath={}, error={}", targetPath, e.getMessage(), e);
            throw e;
        } finally {
            deleteTempFileIfExists(tempPath);
        }
    }

    /**
     * 原子复制文件
     *
     * <p>复制流程：</p>
     * <ol>
     *   <li>校验源文件存在性</li>
     *   <li>创建父目录（如不存在）</li>
     *   <li>复制源文件到临时文件</li>
     *   <li>原子移动临时文件到目标路径</li>
     * </ol>
     *
     * @param sourcePath 源文件路径
     * @param targetPath 目标文件路径
     * @throws IllegalArgumentException 当参数为null时抛出
     * @throws IOException 当源文件不存在或复制失败时抛出
     */
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
            
            atomicMoveWithFallback(tempPath, targetPath);
            log.info("原子复制完成: {} -> {}", sourcePath, targetPath);
            
        } catch (IOException e) {
            log.error("原子复制失败: sourcePath={}, targetPath={}, error={}", 
                    sourcePath, targetPath, e.getMessage(), e);
            throw e;
        } finally {
            deleteTempFileIfExists(tempPath);
        }
    }

    /**
     * 原子移动文件
     *
     * <p>移动流程：</p>
     * <ol>
     *   <li>校验源文件存在性</li>
     *   <li>创建父目录（如不存在）</li>
     *   <li>尝试原子移动（ATOMIC_MOVE + REPLACE_EXISTING）</li>
     *   <li>如原子移动失败，回退到复制+删除方式</li>
     * </ol>
     *
     * @param sourcePath 源文件路径
     * @param targetPath 目标文件路径
     * @throws IllegalArgumentException 当参数为null时抛出
     * @throws IOException 当源文件不存在或移动失败时抛出
     */
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
            
            atomicMoveWithFallback(sourcePath, targetPath);
            log.info("原子移动完成: {} -> {}", sourcePath, targetPath);
            
        } catch (IOException e) {
            log.debug("原子移动不支持，使用临时文件方式: sourcePath={}, targetPath={}", sourcePath, targetPath);
            
            Path tempPath = Paths.get(targetPath.toString() + WRITING_SUFFIX);
            
            try {
                Files.copy(sourcePath, tempPath, StandardCopyOption.REPLACE_EXISTING);
                log.debug("临时文件复制成功: {}", tempPath);
                
                atomicMoveWithFallback(tempPath, targetPath);
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

    /**
     * 带备份的原子写入
     *
     * <p>写入流程：</p>
     * <ol>
     *   <li>如目标文件已存在，创建备份副本</li>
     *   <li>执行原子写入</li>
     *   <li>写入成功后删除备份</li>
     *   <li>写入失败时从备份恢复</li>
     * </ol>
     *
     * @param targetPath 目标文件路径
     * @param content 要写入的字节数组
     * @throws IOException 当写入失败且备份恢复也失败时抛出
     */
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

    /**
     * 带Windows回退的原子移动操作
     *
     * <p>优先尝试ATOMIC_MOVE + REPLACE_EXISTING组合进行原子移动。
     * 当操作系统或文件系统不支持原子移动时（如Windows的NTFS），
     * 自动回退到仅使用REPLACE_EXISTING模式。</p>
     *
     * <p>回退场景：</p>
     * <ul>
     *   <li>AtomicMoveNotSupportedException - 文件系统不支持原子移动</li>
     *   <li>UnsupportedOperationException - 操作系统不支持原子移动</li>
     * </ul>
     *
     * @param sourcePath 源文件路径
     * @param targetPath 目标文件路径
     * @throws IOException 当移动操作失败时抛出
     */
    private void atomicMoveWithFallback(Path sourcePath, Path targetPath) throws IOException {
        try {
            // 优先尝试原子移动，确保文件操作的原子性
            Files.move(sourcePath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            // Windows等系统可能不支持ATOMIC_MOVE，回退到REPLACE_EXISTING模式
            log.warn("[原子文件服务] 文件系统不支持原子移动操作，回退到REPLACE_EXISTING模式: " +
                    "sourcePath={}, targetPath={}, 原因: {}", sourcePath, targetPath, e.getMessage());
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (UnsupportedOperationException e) {
            // 某些JDK实现可能不支持ATOMIC_MOVE，回退到REPLACE_EXISTING模式
            log.warn("[原子文件服务] 操作系统不支持原子移动操作，回退到REPLACE_EXISTING模式: " +
                    "sourcePath={}, targetPath={}, 原因: {}", sourcePath, targetPath, e.getMessage());
            Files.move(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 删除临时文件（如存在）
     *
     * <p>安全删除临时文件，删除失败时仅记录警告日志，不抛出异常。
     * 通常在finally块中调用，确保临时文件被清理。</p>
     *
     * @param tempPath 临时文件路径
     */
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
