package com.mdplatform.management.dto;

import com.mdplatform.management.model.ElectrolyteSystem;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ElectrolyteSystemListResponse {

    private Long id;
    private String name;
    private Double temperature;
    private Double pressure;
    private Boolean isPublicTemplate;
    private LocalDateTime createdAt;

    public static ElectrolyteSystemListResponse fromEntity(ElectrolyteSystem entity) {
        ElectrolyteSystemListResponse dto = new ElectrolyteSystemListResponse();
        dto.setId(entity.getSystemId());
        dto.setName(entity.getSystemName());
        dto.setTemperature(entity.getTemperature());
        dto.setPressure(entity.getPressure());
        dto.setIsPublicTemplate(entity.getIsPublicTemplate());
        dto.setCreatedAt(entity.getCreateTime());
        return dto;
    }
}
