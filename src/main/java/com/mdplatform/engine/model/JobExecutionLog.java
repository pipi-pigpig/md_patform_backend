package com.mdplatform.engine.model;

import javax.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "job_execution_log_table")
public class JobExecutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "log_level", nullable = false)
    private String logLevel;

    @Column(name = "log_content", nullable = false)
    private String logContent;

    @Column(name = "log_time", nullable = false)
    private LocalDateTime logTime;
}