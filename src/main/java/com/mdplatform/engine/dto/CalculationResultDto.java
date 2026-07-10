package com.mdplatform.engine.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mdplatform.engine.model.CalculationResult;

import java.time.LocalDateTime;

/**
 * 计算结果统一响应DTO，包含公共字段和对应子表的专属数据
 *
 * <p>功能：封装计算结果的API响应数据，根据propertyName自动包含对应子表的专属数据。
 * 子表数据通过detail字段以Object类型返回，前端根据propertyName判断具体类型。</p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CalculationResultDto {

    /** 计算结果ID */
    private Long resultId;

    /** 任务ID */
    private Long jobId;

    /** 属性名称（density/viscosity/conductivity/dielectric/solvation） */
    private String propertyName;

    /** 属性值（主计算结果数值） */
    private Double propertyValue;

    /** 属性单位 */
    private String propertyUnit;

    /** 计算方法 */
    private String calculationMethod;

    /** 计算温度（单位：K） */
    private Double temperatureK;

    /** 计算压强（单位：bar） */
    private Double pressureBar;

    /** 采样时间（单位：ps） */
    private Double samplingTimePs;

    /** 收敛状态 */
    private String convergenceStatus;

    /** 性质统计摘要（JSON格式） */
    private String propertyDetail;

    /** 原始数据文件路径 */
    private String rawDataPath;

    /** 图表数据文件路径 */
    private String chartDataPath;

    /** 性质专属数据（根据propertyName为DensityResultDto/ViscosityResultDto等） */
    private Object propertyDetailData;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 从CalculationResult实体构建CalculationResultDto（不含子表数据）
     *
     * @param result 计算结果实体
     * @return CalculationResultDto对象
     */
    public static CalculationResultDto fromEntity(CalculationResult result) {
        if (result == null) return null;
        CalculationResultDto dto = new CalculationResultDto();
        dto.resultId = result.getResultId();
        dto.jobId = result.getJobId();
        dto.propertyName = result.getPropertyName();
        dto.propertyValue = result.getPropertyValue();
        dto.propertyUnit = result.getPropertyUnit();
        dto.calculationMethod = result.getCalculationMethod();
        dto.temperatureK = result.getTemperatureK();
        dto.pressureBar = result.getPressureBar();
        dto.samplingTimePs = result.getSamplingTimePs();
        dto.convergenceStatus = result.getConvergenceStatus();
        dto.propertyDetail = result.getPropertyDetail();
        dto.rawDataPath = result.getRawDataPath();
        dto.chartDataPath = result.getChartDataPath();
        dto.createTime = result.getCreateTime();
        return dto;
    }

    // Getters and Setters
    public Long getResultId() { return resultId; }
    public void setResultId(Long resultId) { this.resultId = resultId; }

    public Long getJobId() { return jobId; }
    public void setJobId(Long jobId) { this.jobId = jobId; }

    public String getPropertyName() { return propertyName; }
    public void setPropertyName(String propertyName) { this.propertyName = propertyName; }

    public Double getPropertyValue() { return propertyValue; }
    public void setPropertyValue(Double propertyValue) { this.propertyValue = propertyValue; }

    public String getPropertyUnit() { return propertyUnit; }
    public void setPropertyUnit(String propertyUnit) { this.propertyUnit = propertyUnit; }

    public String getCalculationMethod() { return calculationMethod; }
    public void setCalculationMethod(String calculationMethod) { this.calculationMethod = calculationMethod; }

    public Double getTemperatureK() { return temperatureK; }
    public void setTemperatureK(Double temperatureK) { this.temperatureK = temperatureK; }

    public Double getPressureBar() { return pressureBar; }
    public void setPressureBar(Double pressureBar) { this.pressureBar = pressureBar; }

    public Double getSamplingTimePs() { return samplingTimePs; }
    public void setSamplingTimePs(Double samplingTimePs) { this.samplingTimePs = samplingTimePs; }

    public String getConvergenceStatus() { return convergenceStatus; }
    public void setConvergenceStatus(String convergenceStatus) { this.convergenceStatus = convergenceStatus; }

    public String getPropertyDetail() { return propertyDetail; }
    public void setPropertyDetail(String propertyDetail) { this.propertyDetail = propertyDetail; }

    public String getRawDataPath() { return rawDataPath; }
    public void setRawDataPath(String rawDataPath) { this.rawDataPath = rawDataPath; }

    public String getChartDataPath() { return chartDataPath; }
    public void setChartDataPath(String chartDataPath) { this.chartDataPath = chartDataPath; }

    public Object getPropertyDetailData() { return propertyDetailData; }
    public void setPropertyDetailData(Object propertyDetailData) { this.propertyDetailData = propertyDetailData; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
