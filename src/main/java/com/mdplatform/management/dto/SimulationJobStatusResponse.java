package com.mdplatform.management.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SimulationJobStatusResponse {

    private Long id;
    private String status;
    private Integer progress;
    private String currentStep;
    private LocalDateTime startTime;
    private LocalDateTime estimatedEndTime;
}
