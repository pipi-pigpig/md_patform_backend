package com.mdplatform.engine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SystemCreateRequest {
    private String name;
    private Long userId;
    private String taskDescription;
    private Double temperature;
    private Double pressure;
    private String boundaryConditions;
    private Boolean isPublicTemplate;
    private Integer totalAtomCount;

    @JsonProperty("solventInfo")
    private Object solventInfoObj;

    @JsonProperty("saltInfo")
    private Object saltInfoObj;

    @JsonProperty("additiveInfo")
    private Object additiveInfoObj;

    @JsonProperty("boxSize")
    private Object boxSizeObj;

    private static final ObjectMapper mapper = new ObjectMapper();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getTaskDescription() { return taskDescription; }
    public void setTaskDescription(String taskDescription) { this.taskDescription = taskDescription; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Double getPressure() { return pressure; }
    public void setPressure(Double pressure) { this.pressure = pressure; }

    public String getBoundaryConditions() { return boundaryConditions; }
    public void setBoundaryConditions(String boundaryConditions) { this.boundaryConditions = boundaryConditions; }

    public Boolean getIsPublicTemplate() { return isPublicTemplate; }
    public void setIsPublicTemplate(Boolean isPublicTemplate) { this.isPublicTemplate = isPublicTemplate; }

    public Integer getTotalAtomCount() { return totalAtomCount; }
    public void setTotalAtomCount(Integer totalAtomCount) { this.totalAtomCount = totalAtomCount; }

    public String getSolventInfo() {
        return toJsonString(solventInfoObj);
    }

    public String getSaltInfo() {
        return toJsonString(saltInfoObj);
    }

    public String getAdditiveInfo() {
        return toJsonString(additiveInfoObj);
    }

    public String getBoxSize() {
        return toJsonString(boxSizeObj);
    }

    private String toJsonString(Object obj) {
        if (obj == null) return null;
        if (obj instanceof String) return (String) obj;
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }

    public com.mdplatform.engine.model.ElectrolyteSystem toEntity() {
        com.mdplatform.engine.model.ElectrolyteSystem entity = new com.mdplatform.engine.model.ElectrolyteSystem();
        entity.setSystemName(name);
        entity.setUserId(userId != null ? userId : 1L);
        entity.setTaskDescription(taskDescription);
        entity.setTemperature(temperature != null ? temperature : 298.15);
        entity.setPressure(pressure != null ? pressure : 1.0);
        entity.setBoundaryConditions(boundaryConditions != null ? boundaryConditions : "p p p");
        entity.setIsPublicTemplate(isPublicTemplate != null ? isPublicTemplate : false);
        entity.setTotalAtomCount(totalAtomCount);
        entity.setSolventInfo(getSolventInfo());
        entity.setSaltInfo(getSaltInfo());
        entity.setAdditiveInfo(getAdditiveInfo());
        entity.setBoxSize(getBoxSize());
        return entity;
    }
}