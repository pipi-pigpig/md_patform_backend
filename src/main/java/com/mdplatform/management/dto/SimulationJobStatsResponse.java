package com.mdplatform.management.dto;

import lombok.Data;

@Data
public class SimulationJobStatsResponse {

    private long total;
    private long pending;
    private long running;
    private long completed;
    private long failed;
    private long cancelled;
}
