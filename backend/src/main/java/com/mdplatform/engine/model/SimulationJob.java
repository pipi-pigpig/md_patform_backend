package com.mdplatform.engine.model;

import javax.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 模拟任务实体类，对应simulation_jobs_table表，存储模拟任务的基本信息和状态
 *
 * <p>功能：
 *     1. 记录模拟任务的基本配置信息
 *     2. 跟踪任务执行状态和生命周期
 *     3. 存储硬件环境和计算资源配置
 *     4. 记录任务执行结果和错误信息
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Data
@Entity
@Table(name = "simulation_jobs_table")
public class SimulationJob {

    /** 任务ID，主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_id")
    private Long jobId;

    /**
     * 任务名称
     */
    @Column(name = "job_name", nullable = false)
    private String jobName;

    /**
     * 任务描述（详细说明模拟任务的目标和背景）
     */
    @Column(name = "task_description", length = 500)
    private String taskDescription;

    /** 用户ID，关联sys_user_table表 */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 电解液系统ID，关联electrolyte_systems_table表 */
    @Column(name = "system_id", nullable = false)
    private Long systemId;

    /** 模拟软件名称（如LAMMPS） */
    @Column(name = "software_name", nullable = false)
    private String softwareName;

    /** 模拟软件版本号 */
    @Column(name = "software_version", nullable = false)
    private String softwareVersion;

    /** 任务状态（PENDING-待处理、MODELING-建模中、RUNNING-运行中、POST_PROCESSING-后处理中、COMPLETED-已完成、FAILED-失败、CANCELLED-已取消） */
    @Column(nullable = false)
    private String status = JobStatus.PENDING;

    /** 目标计算属性（JSON格式，如密度、电导率、粘度等） */
    @Column(name = "target_properties", columnDefinition = "JSON", nullable = false)
    private String targetProperties;

    /** 使用的硬件类型（CPU或GPU） */
    @Column(name = "hardware_used", nullable = false)
    private String hardwareUsed = "CPU";

    /** CPU核心数 */
    @Column(name = "cpu_cores", nullable = false)
    private String cpuCores = "8";

    /**
     * GPU信息（型号、显存等）
     */
    @Column(name = "gpu_info")
    private String gpuInfo;

    /**
     * 完整计算硬件环境（如"CPU 8核"、"GPU RTX3090"等）
     */
    @Column(name = "hardware_environment", length = 500, nullable = false)
    private String hardwareEnvironment = "CPU 8核";

    /** 任务根目录路径（相对路径，通过PathUtil生成） */
    @Column(name = "job_root_path", nullable = false)
    private String jobRootPath;

    /** 任务开始执行时间 */
    @Column(name = "start_time")
    private LocalDateTime startTime;

    /** 任务结束时间 */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /** 任务执行耗时（单位：秒） */
    @Column(name = "execution_time_s")
    private Long executionTimeS;

    /** 计算结果摘要（JSON格式，包含关键计算结果的概要信息） */
    @Column(name = "result_summary", columnDefinition = "JSON")
    private String resultSummary;

    /** 错误信息（任务失败时记录详细错误描述） */
    @Column(name = "error_message")
    private String errorMessage;

    /** 随机数种子，用于确保模拟的可重复性 */
    @Column(name = "random_seed", nullable = false)
    private Integer randomSeed;

    /** 创建时间，实体持久化时自动设置 */
    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    /** 更新时间，实体更新时自动设置 */
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