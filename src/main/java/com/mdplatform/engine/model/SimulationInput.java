package com.mdplatform.engine.model;

import javax.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "simulation_input_table")
public class SimulationInput {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "input_id")
    private Long inputId;

    @Column(name = "job_id", nullable = false, unique = true)
    private Long jobId;

    @Column(name = "ensemble_type", nullable = false)
    private String ensembleType;

    @Column(name = "thermostat_type", nullable = false)
    private String thermostatType;

    @Column(name = "barostat_type")
    private String barostatType;

    @Column(name = "time_step_fs", nullable = false)
    private Double timeStepFs = 1.0;

    @Column(name = "integration_algorithm", nullable = false)
    private String integrationAlgorithm = "Velocity-Verlet";

    @Column(name = "cutoff_distance_ang", nullable = false)
    private Double cutoffDistanceAng = 10.0;

    @Column(name = "long_range_electrostatics", nullable = false)
    private String longRangeElectrostatics = "PPPM, accuracy 1.0e-4";

    @Column(name = "output_frequency_step", nullable = false)
    private Integer outputFrequencyStep;

    @Column(name = "minimization_params", columnDefinition = "JSON", nullable = false)
    private String minimizationParams;

    @Column(name = "equilibrium_params", columnDefinition = "JSON", nullable = false)
    private String equilibriumParams;

    @Column(name = "production_params", columnDefinition = "JSON", nullable = false)
    private String productionParams;

    @Column(name = "initial_config_source", nullable = false)
    private String initialConfigSource;

    @Column(name = "initial_velocity_distribution", nullable = false)
    private String initialVelocityDistribution;

    @Column(name = "input_file_list", columnDefinition = "JSON")
    private String inputFileList;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
    }
}