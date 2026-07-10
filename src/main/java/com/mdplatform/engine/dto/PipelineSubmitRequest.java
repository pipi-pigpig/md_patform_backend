package com.mdplatform.engine.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 全流程计算任务提交请求DTO
 *
 * <p>功能：
 *     1. 封装全流程计算任务提交所需的配方参数、目标性质、温度等信息
 *     2. 提供级联校验，确保嵌套的配方参数对象也有效
 *     3. 支持可选的任务名称和硬件类型配置
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Data
public class PipelineSubmitRequest {

    /** 配方参数，包含溶剂、锂盐、添加剂和盒子尺寸等信息 */
    @Valid
    @NotNull(message = "配方参数不能为空")
    private FormulaRequest formula;

    /** 目标计算性质列表（如["density","conductivity"]），为空时默认为["density"] */
    @NotNull(message = "目标计算性质不能为空")
    private List<String> targetProperties;

    /** 任务名称（可选，默认自动生成） */
    private String jobName;

    /** 温度（K，可选，默认300.0） */
    private Double temperature;

    /** 硬件类型（CPU/GPU，可选，默认CPU） */
    private String hardwareUsed;
}
