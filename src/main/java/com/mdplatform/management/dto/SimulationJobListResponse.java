package com.mdplatform.management.dto;

import com.mdplatform.management.model.SimulationJob;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SimulationJobListResponse {

    private Long id;
    private String jobName;
    private String status;
    private Long systemId;
    private String systemName;
    private String softwareName;
    private String hardwareUsed;
    private String targetProperties;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long executionTimeS;
    private LocalDateTime createdAt;

    public static SimulationJobListResponse fromEntity(SimulationJob entity) {
        SimulationJobListResponse dto = new SimulationJobListResponse();
        dto.setId(entity.getJobId());
        dto.setJobName(entity.getJobName());
        dto.setStatus(entity.getStatus());
        dto.setSystemId(entity.getSystemId());
        dto.setSoftwareName(entity.getSoftwareName());
        dto.setHardwareUsed(entity.getHardwareUsed());
        dto.setTargetProperties(entity.getTargetProperties());
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setExecutionTimeS(entity.getExecutionTimeS());
        dto.setCreatedAt(entity.getCreateTime());
        return dto;
    }

    public static SimulationJobListResponse fromEntityWithSystemName(SimulationJob entity, String systemName) {
        SimulationJobListResponse dto = fromEntity(entity);
        dto.setSystemName(systemName);
        return dto;
    }
}
