package com.mdplatform.engine.dto;

import com.mdplatform.engine.model.ViscosityResult;

/**
 * 粘度计算结果专属数据DTO
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
public class ViscosityResultDto {

    /** 剪切粘度值（单位：Pa·s） */
    private Double viscosityValue;

    /** 剪切速率（单位：s⁻¹） */
    private Double shearRate;

    /** 应力响应（单位：Pa） */
    private Double stressResponse;

    /** 运动粘度（单位：mm²/s） */
    private Double kinematicViscosity;

    /**
     * 从ViscosityResult实体构建DTO
     *
     * @param entity 粘度结果实体
     * @return ViscosityResultDto对象
     */
    public static ViscosityResultDto fromEntity(ViscosityResult entity) {
        if (entity == null) return null;
        ViscosityResultDto dto = new ViscosityResultDto();
        dto.viscosityValue = entity.getViscosityValue();
        dto.shearRate = entity.getShearRate();
        dto.stressResponse = entity.getStressResponse();
        dto.kinematicViscosity = entity.getKinematicViscosity();
        return dto;
    }

    public Double getViscosityValue() { return viscosityValue; }
    public void setViscosityValue(Double viscosityValue) { this.viscosityValue = viscosityValue; }

    public Double getShearRate() { return shearRate; }
    public void setShearRate(Double shearRate) { this.shearRate = shearRate; }

    public Double getStressResponse() { return stressResponse; }
    public void setStressResponse(Double stressResponse) { this.stressResponse = stressResponse; }

    public Double getKinematicViscosity() { return kinematicViscosity; }
    public void setKinematicViscosity(Double kinematicViscosity) { this.kinematicViscosity = kinematicViscosity; }
}
