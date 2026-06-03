package com.mdplatform.engine.model;

import javax.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "calculation_result_table")
public class CalculationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "result_id")
    private Long resultId;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "property_name", nullable = false)
    private String propertyName;

    @Column(name = "property_value", nullable = false)
    private Double propertyValue;

    @Column(name = "property_unit", nullable = false)
    private String propertyUnit;

    @Column(name = "calculation_method", nullable = false)
    private String calculationMethod;

    @Column(name = "temperature_k", nullable = false)
    private Double temperatureK;

    @Column(name = "pressure_bar")
    private Double pressureBar;

    @Column(name = "sampling_time_ps", nullable = false)
    private Double samplingTimePs;

    @Column(name = "convergence_status", nullable = false)
    private String convergenceStatus;

    @Column(name = "property_detail", columnDefinition = "JSON", nullable = false)
    private String propertyDetail;

    @Column(name = "raw_data_path")
    private String rawDataPath;

    @Column(name = "chart_data_path")
    private String chartDataPath;

    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
    }
}