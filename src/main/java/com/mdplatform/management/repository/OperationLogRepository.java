package com.mdplatform.management.repository;

import com.mdplatform.management.model.SysOperationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 操作日志数据访问层，提供操作日志的查询操作
 *
 * <p>功能：
 *     1. 根据用户ID查询操作日志
 *     2. 根据操作类型查询操作日志
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Repository
public interface OperationLogRepository extends JpaRepository<SysOperationLog, Long> {

    /**
     * 根据用户ID查询操作日志，按操作时间降序排列
     *
     * @param userId 用户ID
     * @return 按操作时间降序排列的操作日志列表
     */
    List<SysOperationLog> findByUserIdOrderByOperationTimeDesc(Long userId);

    /**
     * 根据操作类型查询操作日志
     *
     * @param operationType 操作类型（如LOGIN、CREATE、UPDATE、DELETE等）
     * @return 指定操作类型的日志列表
     */
    List<SysOperationLog> findByOperationType(String operationType);
}