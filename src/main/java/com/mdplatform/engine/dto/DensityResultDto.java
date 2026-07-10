package com.mdplatform.engine.dto;

import com.mdplatform.engine.model.DensityResult;

/**
 * 密度计算结果专属数据DTO
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
public class DensityResultDto {

    /** 密度张量（JSON格式，单位：kg/m³） */
    private String densityTensor;

    /** 组分密度（JSON格式） */
    private String componentDensity;

    /**
     * 从DensityResult实体构建DTO
     *
     * @param entity 密度结果实体
     * @return DensityResultDto对象
     */
    public static DensityResultDto fromEntity(DensityResult entity) {
        if (entity == null) return null;
        DensityResultDto dto = new DensityResultDto();
        dto.densityTensor = entity.getDensityTensor();
        dto.componentDensity = entity.getComponentDensity();
        return dto;
    }

    public String getDensityTensor() { return densityTensor; }
    public void setDensityTensor(String densityTensor) { this.densityTensor = densityTensor; }

    public String getComponentDensity() { return componentDensity; }
    public void setComponentDensity(String componentDensity) { this.componentDensity = componentDensity; }
}
