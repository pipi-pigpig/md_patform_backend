package com.mdplatform.engine.model;

import javax.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟原始输出实体类，对应simulation_raw_output_table表，存储模拟的原始输出文件路径和统计信息
 *
 * <p>功能：
 *     1. 记录模拟输出的各类文件路径（日志、轨迹、应力张量等）
 *     2. 存储模拟输出的统计信息（总帧数、总模拟时间等）
 *     3. 记录输出文件的格式特征（是否包含回绕坐标等）
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Data
@Entity
@Table(name = "simulation_raw_output_table")
public class SimulationRawOutput {

    /** 输出记录ID，主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "output_id")
    private Long outputId;

    /** 任务ID，关联simulation_jobs_table表，唯一约束 */
    @Column(name = "job_id", nullable = false, unique = true)
    private Long jobId;

    /** LAMMPS日志文件路径（相对路径） */
    @Column(name = "log_file_path", nullable = false)
    private String logFilePath;

    /** 轨迹文件路径（相对路径，存储原子运动轨迹） */
    @Column(name = "trajectory_file_path", nullable = false)
    private String trajectoryFilePath;

    /** 应力张量文件路径（相对路径） */
    @Column(name = "stress_tensor_file_path")
    private String stressTensorFilePath;

    /** 偶极矩文件路径（相对路径） */
    @Column(name = "dipole_moment_file_path")
    private String dipoleMomentFilePath;

    /** 带电荷轨迹文件路径（相对路径，dump.charge.lammpstrj），仅conductivity/dielectric计算时存在 */
    @Column(name = "charge_trajectory_file_path")
    private String chargeTrajectoryFilePath;

    /** 溶剂化结构轨迹文件路径（相对路径，dump.solvation.lammpstrj），仅solvation_structure计算时存在 */
    @Column(name = "solvation_trajectory_file_path")
    private String solvationTrajectoryFilePath;

    /** 压力张量文件路径（相对路径，pressure.dat），仅viscosity计算时存在 */
    @Column(name = "pressure_file_path")
    private String pressureFilePath;

    /** 偶极矩文件路径（相对路径，dipole.dat），仅dielectric计算时存在 */
    @Column(name = "dipole_file_path")
    private String dipoleFilePath;

    /** 均方位移文件路径（相对路径，msd.dat），仅conductivity计算时存在 */
    @Column(name = "msd_file_path")
    private String msdFilePath;

    /** 最终构型文件路径（相对路径，final.data） */
    @Column(name = "final_data_file_path")
    private String finalDataFilePath;

    /** 总帧数，轨迹文件中的总快照数 */
    @Column(name = "total_frames", nullable = false)
    private Long totalFrames;

    /** 总模拟时间（单位：ns） */
    @Column(name = "total_simulation_time_ns", nullable = false)
    private Double totalSimulationTimeNs;

    /** 是否包含回绕坐标（将周期性边界条件下的原子坐标回绕到模拟盒子内） */
    @Column(name = "wrapped_coords_included", nullable = false)
    private Boolean wrappedCoordsIncluded = true;

    /** 是否包含周期性镜像标记 */
    @Column(name = "periodic_image_flag_included", nullable = false)
    private Boolean periodicImageFlagIncluded = true;

    /**
     * 输出文件总大小（单位：GB）
     */
    @Column(name = "file_size_gb", nullable = false)
    private BigDecimal fileSizeGb;

    /**
     * 盒子倾斜因子（JSON格式，包含xy/xz/yz三个倾斜分量）
     */
    @Column(name = "box_tilt_factor", columnDefinition = "JSON")
    private String boxTiltFactor;

    /** 创建时间，实体持久化时自动设置 */
    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
    }
}