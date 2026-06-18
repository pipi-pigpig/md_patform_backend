package com.mdplatform.management.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.mdplatform.management.model.SimulationJob;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SimulationJobResponse {

    private Long id;
    private String jobName;
    private Long userId;
    private Long systemId;
    private String systemName;
    private String softwareName;
    private String softwareVersion;
    private String status;

    @JsonRawValue
    private String targetProperties;

    private String hardwareUsed;
    private String cpuCores;
    private String gpuInfo;
    private String hardwareEnvironment;
    private String jobRootPath;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long executionTimeS;

    @JsonRawValue
    private String resultSummary;

    private String errorMessage;
    private Integer randomSeed;
    private String taskDescription;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SimulationJobResponse fromEntity(SimulationJob entity) {
        SimulationJobResponse dto = new SimulationJobResponse();
        dto.setId(entity.getJobId());
        dto.setJobName(entity.getJobName());
        dto.setUserId(entity.getUserId());
        dto.setSystemId(entity.getSystemId());
        dto.setSoftwareName(entity.getSoftwareName());
        dto.setSoftwareVersion(entity.getSoftwareVersion());
        dto.setStatus(entity.getStatus());
        dto.setTargetProperties(entity.getTargetProperties());
        dto.setHardwareUsed(entity.getHardwareUsed());
        dto.setCpuCores(entity.getCpuCores());
        dto.setGpuInfo(entity.getGpuInfo());
        dto.setHardwareEnvironment(entity.getHardwareEnvironment());
        dto.setJobRootPath(entity.getJobRootPath());
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setExecutionTimeS(entity.getExecutionTimeS());
        dto.setResultSummary(entity.getResultSummary());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setRandomSeed(entity.getRandomSeed());
        dto.setTaskDescription(entity.getTaskDescription());
        dto.setCreatedAt(entity.getCreateTime());
        dto.setUpdatedAt(entity.getUpdateTime());
        return dto;
    }

    public static SimulationJobResponse fromEntityWithSystemName(SimulationJob entity, String systemName) {
        SimulationJobResponse dto = fromEntity(entity);
        dto.setSystemName(systemName);
        return dto;
    }
}
