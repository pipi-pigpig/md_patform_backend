package com.mdplatform.engine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * 电解液系统创建请求DTO - 用于接收前端创建电解液系统的请求参数
 *
 * <p>功能：
 *     1. 封装电解液系统创建所需的参数
 *     2. 提供转换为实体对象的方法
 *     3. 处理溶剂、盐、添加剂等复杂JSON字段的序列化
 * </p>
 *
 * <p>注意：userId应由Controller从认证上下文中设置，不允许使用默认值</p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
public class SystemCreateRequest {

    /** 系统名称 */
    @NotBlank(message = "系统名称不能为空")
    @Size(max = 200, message = "系统名称不能超过200字符")
    private String name;

    /** 所属用户ID，由Controller从认证上下文中设置，不允许默认值 */
    private Long userId;

    /** 任务描述 */
    private String taskDescription;

    /** 温度（K） */
    @NotNull(message = "温度不能为空")
    @DecimalMin(value = "0.1", message = "温度必须大于0.1K")
    private Double temperature;

    /** 压力（atm） */
    @NotNull(message = "压力不能为空")
    @DecimalMin(value = "0.0", inclusive = false, message = "压力必须大于0")
    private Double pressure;

    /** 边界条件 */
    private String boundaryConditions;

    /** 是否公开模板 */
    private Boolean isPublicTemplate;

    /** 总原子数 */
    private Integer totalAtomCount;

    /** 溶剂信息（JSON对象） */
    @JsonProperty("solventInfo")
    private Object solventInfoObj;

    /** 盐信息（JSON对象） */
    @JsonProperty("saltInfo")
    private Object saltInfoObj;

    /** 添加剂信息（JSON对象） */
    @JsonProperty("additiveInfo")
    private Object additiveInfoObj;

    /** 盒子尺寸信息（JSON对象） */
    @JsonProperty("boxSize")
    private Object boxSizeObj;

    /** JSON序列化工具 */
    private static final ObjectMapper mapper = new ObjectMapper();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getTaskDescription() { return taskDescription; }
    public void setTaskDescription(String taskDescription) { this.taskDescription = taskDescription; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Double getPressure() { return pressure; }
    public void setPressure(Double pressure) { this.pressure = pressure; }

    public String getBoundaryConditions() { return boundaryConditions; }
    public void setBoundaryConditions(String boundaryConditions) { this.boundaryConditions = boundaryConditions; }

    public Boolean getIsPublicTemplate() { return isPublicTemplate; }
    public void setIsPublicTemplate(Boolean isPublicTemplate) { this.isPublicTemplate = isPublicTemplate; }

    public Integer getTotalAtomCount() { return totalAtomCount; }
    public void setTotalAtomCount(Integer totalAtomCount) { this.totalAtomCount = totalAtomCount; }

    public String getSolventInfo() {
        return toJsonString(solventInfoObj);
    }

    public String getSaltInfo() {
        return toJsonString(saltInfoObj);
    }

    public String getAdditiveInfo() {
        return toJsonString(additiveInfoObj);
    }

    public String getBoxSize() {
        return toJsonString(boxSizeObj);
    }

    /**
     * 将对象转换为JSON字符串
     *
     * @param obj 待转换的对象
     * @return JSON字符串，转换失败返回null
     */
    private String toJsonString(Object obj) {
        if (obj == null) return null;
        if (obj instanceof String) return (String) obj;
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 将请求DTO转换为电解液系统实体对象
     *
     * <p>userId必须由Controller从认证上下文中设置，不允许使用默认值</p>
     *
     * @return 电解液系统实体对象
     */
    public com.mdplatform.engine.model.ElectrolyteSystem toEntity() {
        com.mdplatform.engine.model.ElectrolyteSystem entity = new com.mdplatform.engine.model.ElectrolyteSystem();
        entity.setSystemName(name);
        // userId由Controller从认证上下文中设置，不使用默认值
        entity.setUserId(userId);
        entity.setTaskDescription(taskDescription);
        entity.setTemperature(temperature != null ? temperature : 298.15);
        entity.setPressure(pressure != null ? pressure : 1.0);
        entity.setBoundaryConditions(boundaryConditions != null ? boundaryConditions : "p p p");
        entity.setIsPublicTemplate(isPublicTemplate != null ? isPublicTemplate : false);
        entity.setTotalAtomCount(totalAtomCount);
        entity.setSolventInfo(getSolventInfo());
        entity.setSaltInfo(getSaltInfo());
        entity.setAdditiveInfo(getAdditiveInfo());
        entity.setBoxSize(getBoxSize());
        return entity;
    }
}
