package com.mdplatform.engine.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class SimulationDto {
    private Long id;
    private String jobName;
    private String description;
    private String software;
    private String status;
    private String hardwareUsed;
    private String cpuCores;
    private String gpuInfo;
    private Long systemId;
    private Long userId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long executionTime;
    private Integer progress;
    private String computingUnit;
    private Map<String, Object> parameters;
    private Map<String, Object> resultSummary;
    private String jobRootPath;
    private String errorMessage;
    private Integer randomSeed;

    public static SimulationDto fromEntity(com.mdplatform.engine.model.SimulationJob entity) {
        SimulationDto dto = new SimulationDto();
        dto.setId(entity.getJobId());
        dto.setJobName(entity.getJobName());
        dto.setSoftware(entity.getSoftwareName());
        dto.setStatus(entity.getStatus());
        dto.setHardwareUsed(entity.getHardwareUsed());
        dto.setCpuCores(entity.getCpuCores());
        dto.setGpuInfo(entity.getGpuInfo());
        dto.setSystemId(entity.getSystemId());
        dto.setUserId(entity.getUserId());
        dto.setCreatedAt(entity.getCreateTime());
        dto.setUpdatedAt(entity.getUpdateTime());
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setExecutionTime(entity.getExecutionTimeS());
        dto.setJobRootPath(entity.getJobRootPath());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setRandomSeed(entity.getRandomSeed());

        if (entity.getTargetProperties() != null && !entity.getTargetProperties().isEmpty()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> props = mapper.readValue(entity.getTargetProperties(), Map.class);
                dto.setResultSummary(props);
                dto.setDescription(props.containsKey("description") ? props.get("description").toString() : entity.getJobName());
                dto.setParameters(props);
            } catch (Exception e) {
                dto.setDescription(entity.getJobName());
            }
        } else {
            dto.setDescription(entity.getJobName());
        }

        if (entity.getResultSummary() != null && !entity.getResultSummary().isEmpty()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                dto.setResultSummary(mapper.readValue(entity.getResultSummary(), Map.class));
            } catch (Exception e) {

            }
        }

        return dto;
    }

    public static SimulationDto fromEntityWithDescription(com.mdplatform.engine.model.SimulationJob entity, String taskDescription) {
        SimulationDto dto = fromEntity(entity);
        dto.setDescription(taskDescription != null ? taskDescription : entity.getJobName());
        return dto;
    }
}