package com.mdplatform.engine.dto;

import com.mdplatform.engine.model.ConductivityResult;

/**
 * 电导率计算结果专属数据DTO
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
public class ConductivityResultDto {

    /** 电导率张量（JSON格式，单位：S/m） */
    private String conductivityTensor;

    /** 各离子电导率贡献占比（JSON格式） */
    private String ionContribution;

    /** 电场强度（单位：V/Å） */
    private Double electricFieldStrength;

    /** 电阻率（单位：Ω·cm） */
    private Double resistivity;

    /**
     * 从ConductivityResult实体构建DTO
     *
     * @param entity 电导率结果实体
     * @return ConductivityResultDto对象
     */
    public static ConductivityResultDto fromEntity(ConductivityResult entity) {
        if (entity == null) return null;
        ConductivityResultDto dto = new ConductivityResultDto();
        dto.conductivityTensor = entity.getConductivityTensor();
        dto.ionContribution = entity.getIonContribution();
        dto.electricFieldStrength = entity.getElectricFieldStrength();
        dto.resistivity = entity.getResistivity();
        return dto;
    }

    public String getConductivityTensor() { return conductivityTensor; }
    public void setConductivityTensor(String conductivityTensor) { this.conductivityTensor = conductivityTensor; }

    public String getIonContribution() { return ionContribution; }
    public void setIonContribution(String ionContribution) { this.ionContribution = ionContribution; }

    public Double getElectricFieldStrength() { return electricFieldStrength; }
    public void setElectricFieldStrength(Double electricFieldStrength) { this.electricFieldStrength = electricFieldStrength; }

    public Double getResistivity() { return resistivity; }
    public void setResistivity(Double resistivity) { this.resistivity = resistivity; }
}
