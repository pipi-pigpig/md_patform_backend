package com.mdplatform.engine.model;

import javax.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "simulation_raw_output_table")
public class SimulationRawOutput {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "output_id")
    private Long outputId;

    @Column(name = "job_id", nullable = false, unique = true)
    private Long jobId;

    @Column(name = "log_file_path", nullable = false)
    private String logFilePath;

    @Column(name = "trajectory_file_path", nullable = false)
    private String trajectoryFilePath;

    @Column(name = "stress_tensor_file_path")
    private String stressTensorFilePath;

    @Column(name = "dipole_moment_file_path")
    private String dipoleMomentFilePath;

    @Column(name = "total_frames", nullable = false)
    private Long totalFrames;

    @Column(name = "total_simulation_time_ns", nullable = false)
    private Double totalSimulationTimeNs;

    @Column(name = "wrapped_coords_included", nullable = false)
    private Boolean wrappedCoordsIncluded = true;

    @Column(name = "periodic_image_flag_included", nullable = false)
    private Boolean periodicImageFlagIncluded = true;

    @Column(name = "file_size_gb", nullable = false)
    private BigDecimal fileSizeGb;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
    }
}