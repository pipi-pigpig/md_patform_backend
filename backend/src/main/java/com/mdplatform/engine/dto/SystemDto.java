package com.mdplatform.engine.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 电解液系统数据传输对象 - 用于向前端返回电解液系统的详细信息
 *
 * <p>功能：
 *     1. 封装电解液系统的核心信息，包括名称、温度、压力等
 *     2. 提供从实体对象转换的工厂方法
 *     3. 使用@JsonRawValue注解直接输出原始JSON字符串，避免二次转义
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Data
public class SystemDto {

    /** 系统ID */
    private Long id;

    /** 系统名称 */
    private String name;

    /** 所属用户ID */
    private Long userId;

    /** 任务描述 */
    private String taskDescription;

    /** 温度（K） */
    private Double temperature;

    /** 压力（atm） */
    private Double pressure;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 是否公开模板 */
    private Boolean isPublicTemplate;

    /** 总原子数 */
    private Integer totalAtomCount;

    /** 边界条件 */
    private String boundaryConditions;

    /** 溶剂信息（原始JSON字符串） */
    @JsonRawValue
    private String solventInfo;

    /** 盐信息（原始JSON字符串） */
    @JsonRawValue
    private String saltInfo;

    /** 添加剂信息（原始JSON字符串） */
    @JsonRawValue
    private String additiveInfo;

    /** 盒子尺寸信息（原始JSON字符串） */
    @JsonRawValue
    private String boxSize;

    /**
     * 从实体对象转换为DTO
     *
     * @param entity 电解液系统实体对象
     * @return 电解液系统DTO对象
     */
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
