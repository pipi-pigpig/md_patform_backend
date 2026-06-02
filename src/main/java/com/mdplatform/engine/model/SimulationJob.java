package com.mdplatform.engine.model;

import javax.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "simulation_jobs_table")
public class SimulationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "job_name", nullable = false)
    private String jobName;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "system_id", nullable = false)
    private Long systemId;

    @Column(name = "software_name", nullable = false)
    private String softwareName;

    @Column(name = "software_version", nullable = false)
    private String softwareVersion;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "target_properties", columnDefinition = "JSON", nullable = false)
    private String targetProperties;

    @Column(name = "hardware_used", nullable = false)
    private String hardwareUsed = "CPU";

    @Column(name = "cpu_cores", nullable = false)
    private String cpuCores = "8";

    @Column(name = "gpu_info")
    private String gpuInfo;

    @Column(name = "job_root_path", nullable = false)
    private String jobRootPath;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "execution_time_s")
    private Long executionTimeS;

    @Column(name = "result_summary", columnDefinition = "JSON")
    private String resultSummary;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "random_seed", nullable = false)
    private Integer randomSeed;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
        if (updateTime == null) {
            updateTime = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}