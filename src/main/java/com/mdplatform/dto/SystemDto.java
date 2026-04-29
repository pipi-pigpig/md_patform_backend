package com.mdplatform.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SystemDto {
    private Long id;
    private String name;
    private String saltFormula;
    private String solventType;
    private Double concentration;
    private Double temperature;
    private Double pressure;
    private Integer ecRatio;
    private Integer dmcRatio;
    private Long userId;
    private String taskDescription;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isPublicTemplate;
    private Integer totalAtomCount;

    public static SystemDto fromEntity(com.mdplatform.model.ElectrolyteSystem entity) {
        SystemDto dto = new SystemDto();
        dto.setId(entity.getSystemId());
        dto.setName(entity.getSystemName());
        dto.setTemperature(entity.getTemperature());
        dto.setPressure(entity.getPressure());
        dto.setUserId(entity.getUserId());
        dto.setTaskDescription(entity.getTaskDescription());
        dto.setCreatedAt(entity.getCreateTime());
        dto.setUpdatedAt(entity.getUpdateTime());
        dto.setIsPublicTemplate(entity.getIsPublicTemplate());
        dto.setTotalAtomCount(entity.getTotalAtomCount());

        if (entity.getSaltInfo() != null && !entity.getSaltInfo().isEmpty()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                java.util.List<java.util.Map<String, Object>> saltList = mapper.readValue(entity.getSaltInfo(), new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>() {});

                if (!saltList.isEmpty()) {
                    java.util.Map<String, Object> firstSalt = saltList.get(0);
                    if (firstSalt.containsKey("name")) dto.setSaltFormula(firstSalt.get("name").toString());
                    if (firstSalt.containsKey("concentration_mol_L")) dto.setConcentration(Double.valueOf(firstSalt.get("concentration_mol_L").toString()));
                }
            } catch (Exception e) {
            }
        }

        if (entity.getSolventInfo() != null && !entity.getSolventInfo().isEmpty()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                java.util.List<java.util.Map<String, Object>> solventList = mapper.readValue(entity.getSolventInfo(), new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, Object>>>() {});

                if (!solventList.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < solventList.size(); i++) {
                        java.util.Map<String, Object> s = solventList.get(i);
                        if (s.containsKey("name")) {
                            if (sb.length() > 0) sb.append("/");
                            sb.append(s.get("name").toString());
                        }
                    }
                    dto.setSolventType(sb.toString());

                    if (solventList.size() == 2) {
                        java.util.Map<String, Object> first = solventList.get(0);
                        java.util.Map<String, Object> second = solventList.get(1);
                        if (first.containsKey("mole_ratio")) dto.setEcRatio(Integer.valueOf(first.get("mole_ratio").toString()));
                        if (second.containsKey("mole_ratio")) dto.setDmcRatio(Integer.valueOf(second.get("mole_ratio").toString()));
                    }
                }
            } catch (Exception e) {
            }
        }

        return dto;
    }
}
