package com.mdplatform.engine.dto;

import com.mdplatform.engine.model.SolvationResult;

/**
 * 溶剂化结构计算结果专属数据DTO
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
public class SolvationResultDto {

    /** 中心离子类型（如Li⁺、Na⁺等） */
    private String centralIonType;

    /** 溶剂化壳层组成描述 */
    private String solvationShellStructure;

    /** 平均配位数 */
    private Double averageCoordinationNumber;

    /** 配位距离（单位：Å） */
    private Double coordinationDistance;

    /** RDF特征峰位置及强度 */
    private String rdfCharacteristicPeak;

    /** 氢键网络特征描述 */
    private String hydrogenBondFeature;

    /** 溶剂化壳层寿命（单位：ps） */
    private Double solvationStability;

    /** 离子-溶剂相互作用能（单位：kJ/mol） */
    private Double ionSolventInteractionEnergy;

    /**
     * 从SolvationResult实体构建DTO
     *
     * @param entity 溶剂化结构结果实体
     * @return SolvationResultDto对象
     */
    public static SolvationResultDto fromEntity(SolvationResult entity) {
        if (entity == null) return null;
        SolvationResultDto dto = new SolvationResultDto();
        dto.centralIonType = entity.getCentralIonType();
        dto.solvationShellStructure = entity.getSolvationShellStructure();
        dto.averageCoordinationNumber = entity.getAverageCoordinationNumber();
        dto.coordinationDistance = entity.getCoordinationDistance();
        dto.rdfCharacteristicPeak = entity.getRdfCharacteristicPeak();
        dto.hydrogenBondFeature = entity.getHydrogenBondFeature();
        dto.solvationStability = entity.getSolvationStability();
        dto.ionSolventInteractionEnergy = entity.getIonSolventInteractionEnergy();
        return dto;
    }

    public String getCentralIonType() { return centralIonType; }
    public void setCentralIonType(String centralIonType) { this.centralIonType = centralIonType; }

    public String getSolvationShellStructure() { return solvationShellStructure; }
    public void setSolvationShellStructure(String solvationShellStructure) { this.solvationShellStructure = solvationShellStructure; }

    public Double getAverageCoordinationNumber() { return averageCoordinationNumber; }
    public void setAverageCoordinationNumber(Double averageCoordinationNumber) { this.averageCoordinationNumber = averageCoordinationNumber; }

    public Double getCoordinationDistance() { return coordinationDistance; }
    public void setCoordinationDistance(Double coordinationDistance) { this.coordinationDistance = coordinationDistance; }

    public String getRdfCharacteristicPeak() { return rdfCharacteristicPeak; }
    public void setRdfCharacteristicPeak(String rdfCharacteristicPeak) { this.rdfCharacteristicPeak = rdfCharacteristicPeak; }

    public String getHydrogenBondFeature() { return hydrogenBondFeature; }
    public void setHydrogenBondFeature(String hydrogenBondFeature) { this.hydrogenBondFeature = hydrogenBondFeature; }

    public Double getSolvationStability() { return solvationStability; }
    public void setSolvationStability(Double solvationStability) { this.solvationStability = solvationStability; }

    public Double getIonSolventInteractionEnergy() { return ionSolventInteractionEnergy; }
    public void setIonSolventInteractionEnergy(Double ionSolventInteractionEnergy) { this.ionSolventInteractionEnergy = ionSolventInteractionEnergy; }
}
