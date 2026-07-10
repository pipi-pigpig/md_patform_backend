package com.mdplatform.engine.model;

import javax.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 任务执行日志实体类，对应job_execution_log_table表，记录任务执行的详细日志
 *
 * <p>功能：
 *     1. 记录任务执行过程中的关键事件和状态变更
 *     2. 支持不同级别的日志记录（INFO、WARN、ERROR等）
 *     3. 提供任务执行过程的完整审计追踪
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Data
@Entity
@Table(name = "job_execution_log_table")
public class JobExecutionLog {

    /** 日志ID，主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    /** 任务ID，关联simulation_jobs_table表 */
    @Column(name = "job_id", nullable = false)
    private Long jobId;

    /** 日志级别（INFO-信息、WARN-警告、ERROR-错误、DEBUG-调试） */
    @Column(name = "log_level", nullable = false)
    private String logLevel;

    /** 日志内容（详细的日志消息文本） */
    @Column(name = "log_content", nullable = false)
    private String logContent;

    /** 日志记录时间 */
    @Column(name = "log_time", nullable = false)
    private LocalDateTime logTime;
}