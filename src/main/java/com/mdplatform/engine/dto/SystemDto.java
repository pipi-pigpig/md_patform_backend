package com.mdplatform.engine.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SystemDto {
    private Long id;
    private String name;
    private Long userId;
    private String taskDescription;
    private Double temperature;
    private Double pressure;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isPublicTemplate;
    private Integer totalAtomCount;
    private String boundaryConditions;

    @JsonRawValue
    private String solventInfo;

    @JsonRawValue
    private String saltInfo;

    @JsonRawValue
    private String additiveInfo;

    @JsonRawValue
    private String boxSize;

    public static SystemDto fromEntity(com.mdplatform.engine.model.ElectrolyteSystem entity) {
        SystemDto dto = new SystemDto();
        dto.setId(entity.getSystemId());
        dto.setName(entity.getSystemName());
        dto.setUserId(entity.getUserId());
        dto.setTaskDescription(entity.getTaskDescription());
        dto.setTemperature(entity.getTemperature());
        dto.setPressure(entity.getPressure());
        dto.setCreatedAt(entity.getCreateTime());
        dto.setUpdatedAt(entity.getUpdateTime());
        dto.setIsPublicTemplate(entity.getIsPublicTemplate());
        dto.setTotalAtomCount(entity.getTotalAtomCount());
        dto.setBoundaryConditions(entity.getBoundaryConditions());

        dto.setSolventInfo(entity.getSolventInfo());
        dto.setSaltInfo(entity.getSaltInfo());
        dto.setAdditiveInfo(entity.getAdditiveInfo());
        dto.setBoxSize(entity.getBoxSize());

        return dto;
    }
}