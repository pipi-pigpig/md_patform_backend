package com.mdplatform.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimulationStatsDto {
    private long total;
    private long pending;
    private long running;
    private long completed;
    private long failed;
    private long cancelled;
}