package com.mdplatform.management.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.mdplatform.management.model.ElectrolyteSystem;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ElectrolyteSystemResponse {

    private Long id;
    private String name;
    private Long userId;
    private String taskDescription;
    private Double temperature;
    private Double pressure;
    private String boundaryConditions;
    private Integer totalAtomCount;
    private Boolean isPublicTemplate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @JsonRawValue
    private String solventInfo;

    @JsonRawValue
    private String saltInfo;

    @JsonRawValue
    private String additiveInfo;

    @JsonRawValue
    private String boxSize;

    @JsonRawValue
    private String moleculeStatistics;

    public static ElectrolyteSystemResponse fromEntity(ElectrolyteSystem entity) {
        ElectrolyteSystemResponse dto = new ElectrolyteSystemResponse();
        dto.setId(entity.getSystemId());
        dto.setName(entity.getSystemName());
        dto.setUserId(entity.getUserId());
        dto.setTaskDescription(entity.getTaskDescription());
        dto.setTemperature(entity.getTemperature());
        dto.setPressure(entity.getPressure());
        dto.setBoundaryConditions(entity.getBoundaryConditions());
        dto.setTotalAtomCount(entity.getTotalAtomCount());
        dto.setIsPublicTemplate(entity.getIsPublicTemplate());
        dto.setCreatedAt(entity.getCreateTime());
        dto.setUpdatedAt(entity.getUpdateTime());
        dto.setSolventInfo(entity.getSolventInfo());
        dto.setSaltInfo(entity.getSaltInfo());
        dto.setAdditiveInfo(entity.getAdditiveInfo());
        dto.setBoxSize(entity.getBoxSize());
        dto.setMoleculeStatistics(entity.getMoleculeStatistics());
        return dto;
    }
}
