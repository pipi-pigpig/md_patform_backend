package com.mdplatform.engine.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 模拟任务数据传输对象 - 用于向前端返回模拟任务的详细信息
 *
 * <p>功能：
 *     1. 封装模拟任务的核心信息，包括任务状态、硬件配置、执行时间等
 *     2. 提供从实体对象转换的工厂方法
 *     3. 处理JSON格式参数和结果摘要的解析
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Data
public class SimulationDto {

    /** 任务ID */
    private Long id;

    /** 任务名称 */
    private String jobName;

    /** 任务描述 */
    private String description;

    /** 使用的模拟软件名称 */
    private String software;

    /** 任务状态 */
    private String status;

    /** 使用的硬件类型 */
    private String hardwareUsed;

    /** CPU核心数 */
    private String cpuCores;

    /** GPU信息 */
    private String gpuInfo;

    /** 关联的电解液系统ID */
    private Long systemId;

    /** 所属用户ID */
    private Long userId;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 开始执行时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 执行耗时（秒） */
    private Long executionTime;

    /** 任务进度百分比（0-100） */
    private Integer progress;

    /** 计算单元类型 */
    private String computingUnit;

    /** 模拟参数（键值对） */
    private Map<String, Object> parameters;

    /** 结果摘要（键值对） */
    private Map<String, Object> resultSummary;

    /** 任务根目录路径 */
    private String jobRootPath;

    /** 错误信息 */
    private String errorMessage;

    /** 随机数种子 */
    private Integer randomSeed;

    /** JSON序列化工具（静态共享，避免重复创建） */
    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * 从实体对象转换为DTO
     *
     * @param entity 模拟任务实体对象
     * @return 模拟任务DTO对象
     */
    public static SimulationDto fromEntity(com.mdplatform.engine.model.SimulationJob entity) {
        SimulationDto dto = new SimulationDto();
        dto.setId(entity.getJobId());
        dto.setJobName(entity.getJobName());
        dto.setSoftware(entity.getSoftwareName());
        dto.setStatus(entity.getStatus());
        dto.setHardwareUsed(entity.getHardwareUsed());
        dto.setCpuCores(entity.getCpuCores());
        dto.setGpuInfo(entity.getGpuInfo());
        dto.setSystemId(entity.getSystemId());
        dto.setUserId(entity.getUserId());
        dto.setCreatedAt(entity.getCreateTime());
        dto.setUpdatedAt(entity.getUpdateTime());
        dto.setStartTime(entity.getStartTime());
        dto.setEndTime(entity.getEndTime());
        dto.setExecutionTime(entity.getExecutionTimeS());
        dto.setJobRootPath(entity.getJobRootPath());
        dto.setErrorMessage(entity.getErrorMessage());
        dto.setRandomSeed(entity.getRandomSeed());

        if (entity.getTargetProperties() != null && !entity.getTargetProperties().isEmpty()) {
            try {
                Map<String, Object> props = OBJECT_MAPPER.readValue(entity.getTargetProperties(), Map.class);
                dto.setResultSummary(props);
                dto.setDescription(props.containsKey("description") ? props.get("description").toString() : entity.getJobName());
                dto.setParameters(props);
            } catch (Exception e) {
                dto.setDescription(entity.getJobName());
            }
        } else {
            dto.setDescription(entity.getJobName());
        }

        if (entity.getResultSummary() != null && !entity.getResultSummary().isEmpty()) {
            try {
                dto.setResultSummary(OBJECT_MAPPER.readValue(entity.getResultSummary(), Map.class));
            } catch (Exception e) {
                // 结果摘要解析失败时忽略，保留之前设置的值
            }
        }

        return dto;
    }

    /**
     * 从实体对象转换为DTO，并指定任务描述
     *
     * @param entity 模拟任务实体对象
     * @param taskDescription 任务描述
     * @return 模拟任务DTO对象
     */
    public static SimulationDto fromEntityWithDescription(com.mdplatform.engine.model.SimulationJob entity, String taskDescription) {
        SimulationDto dto = fromEntity(entity);
        dto.setDescription(taskDescription != null ? taskDescription : entity.getJobName());
        return dto;
    }
}
