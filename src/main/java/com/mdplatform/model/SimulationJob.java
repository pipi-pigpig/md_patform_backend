package com.mdplatform.model;

import javax.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "simulation_jobs")
public class SimulationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 添加 userId 字段（如果你需要用户关联）
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "system_id")
    private Long systemId;

    @Column(name = "job_name", nullable = false)
    private String jobName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "computing_unit", length = 200)
    private String computingUnit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Software software;

    @Enumerated(EnumType.STRING)
    private JobStatus status = JobStatus.PENDING;

    @Column(name = "input_file_path", length = 500)
    private String inputFilePath;

    @Column(name = "output_file_path", length = 500)
    private String outputFilePath;

    @Column(columnDefinition = "JSON")
    private String parameters;

    @Enumerated(EnumType.STRING)
    @Column(name = "hardware_used")
    private HardwareType hardwareUsed = HardwareType.CPU;

    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long executionTime;

    @Column(name = "result_summary", columnDefinition = "JSON")
    private String resultSummary;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // 如果需要，可以添加关联关系
    // @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "system_id", insertable = false, updatable = false)
    // private ElectrolyteSystem system;

    public enum Software {
        LAMMPS, GROMACS
    }

    public enum JobStatus {
        PENDING, RUNNING, COMPLETED, FAILED, CANCELLED
    }

    public enum HardwareType {
        CPU, GPU, BOTH
    }
}