package com.mdplatform.management.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SimulationJobCreateRequest {

    private String jobName;
    private Long systemId;

    @JsonProperty("targetProperties")
    private Object targetPropertiesObj;

    private String targetPropertiesJson;

    private String hardwareUsed = "CPU";
    private String cpuCores = "8";
    private String gpuInfo;
    private String softwareName = "LAMMPS";
    private String softwareVersion = "29Oct2024";
    private Integer randomSeed;
    private String taskDescription;

    public String getTargetPropertiesJson() {
        if (targetPropertiesJson != null) {
            return targetPropertiesJson;
        }
        if (targetPropertiesObj != null) {
            if (targetPropertiesObj instanceof String) {
                return (String) targetPropertiesObj;
            }
            if (targetPropertiesObj instanceof List || targetPropertiesObj instanceof Map) {
                try {
                    return new com.fasterxml.jackson.databind.ObjectMapper()
                            .writeValueAsString(targetPropertiesObj);
                } catch (Exception e) {
                    return targetPropertiesObj.toString();
                }
            }
            return targetPropertiesObj.toString();
        }
        return null;
    }
}
