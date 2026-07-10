package com.mdplatform.engine.repository;

import com.mdplatform.engine.model.JobExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 任务执行日志数据访问层，提供执行日志的查询操作
 *
 * <p>功能：
 *     1. 根据任务ID查询执行日志
 *     2. 根据任务ID和日志级别查询执行日志
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Repository
public interface JobLogRepository extends JpaRepository<JobExecutionLog, Long> {

    /**
     * 根据任务ID查询执行日志，按日志时间降序排列
     *
     * @param jobId 模拟任务ID
     * @return 按日志时间降序排列的执行日志列表
     */
    List<JobExecutionLog> findByJobIdOrderByLogTimeDesc(Long jobId);

    /**
     * 根据任务ID和日志级别查询执行日志
     *
     * @param jobId    模拟任务ID
     * @param logLevel 日志级别（如INFO、WARN、ERROR等）
     * @return 匹配条件的执行日志列表
     */
    List<JobExecutionLog> findByJobIdAndLogLevel(Long jobId, String logLevel);
}