package com.mdplatform.management.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

@Data
public class ElectrolyteSystemCreateRequest {

    private String name;
    private String taskDescription;
    private Double temperature;
    private Double pressure;
    private String boundaryConditions;

    @JsonProperty("solventInfo")
    private Object solventInfoObj;

    @JsonProperty("saltInfo")
    private Object saltInfoObj;

    @JsonProperty("additiveInfo")
    private Object additiveInfoObj;

    @JsonProperty("boxSize")
    private Object boxSizeObj;

    private static final ObjectMapper mapper = new ObjectMapper();

    public String getSolventInfoJson() {
        return toJsonString(solventInfoObj);
    }

    public String getSaltInfoJson() {
        return toJsonString(saltInfoObj);
    }

    public String getAdditiveInfoJson() {
        return toJsonString(additiveInfoObj);
    }

    public String getBoxSizeJson() {
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
}
