package com.mdplatform.engine.dto;

import com.mdplatform.engine.model.DielectricResult;

/**
 * 介电常数计算结果专属数据DTO
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
public class DielectricResultDto {

    /** 介电常数张量（JSON格式） */
    private String dielectricConstantTensor;

    /** 静态介电常数 */
    private Double staticDielectricConstant;

    /** 介电谱数据（JSON格式） */
    private String dielectricSpectrumData;

    /** 偶极矩数据（JSON格式，单位：Debye） */
    private String dipoleMomentData;

    /** 组分介电贡献（JSON格式） */
    private String componentContribution;

    /** 体系尺寸（JSON格式，单位：Å） */
    private String systemSize;

    /**
     * 从DielectricResult实体构建DTO
     *
     * @param entity 介电常数结果实体
     * @return DielectricResultDto对象
     */
    public static DielectricResultDto fromEntity(DielectricResult entity) {
        if (entity == null) return null;
        DielectricResultDto dto = new DielectricResultDto();
        dto.dielectricConstantTensor = entity.getDielectricConstantTensor();
        dto.staticDielectricConstant = entity.getStaticDielectricConstant();
        dto.dielectricSpectrumData = entity.getDielectricSpectrumData();
        dto.dipoleMomentData = entity.getDipoleMomentData();
        dto.componentContribution = entity.getComponentContribution();
        dto.systemSize = entity.getSystemSize();
        return dto;
    }

    public String getDielectricConstantTensor() { return dielectricConstantTensor; }
    public void setDielectricConstantTensor(String dielectricConstantTensor) { this.dielectricConstantTensor = dielectricConstantTensor; }

    public Double getStaticDielectricConstant() { return staticDielectricConstant; }
    public void setStaticDielectricConstant(Double staticDielectricConstant) { this.staticDielectricConstant = staticDielectricConstant; }

    public String getDielectricSpectrumData() { return dielectricSpectrumData; }
    public void setDielectricSpectrumData(String dielectricSpectrumData) { this.dielectricSpectrumData = dielectricSpectrumData; }

    public String getDipoleMomentData() { return dipoleMomentData; }
    public void setDipoleMomentData(String dipoleMomentData) { this.dipoleMomentData = dipoleMomentData; }

    public String getComponentContribution() { return componentContribution; }
    public void setComponentContribution(String componentContribution) { this.componentContribution = componentContribution; }

    public String getSystemSize() { return systemSize; }
    public void setSystemSize(String systemSize) { this.systemSize = systemSize; }
}
