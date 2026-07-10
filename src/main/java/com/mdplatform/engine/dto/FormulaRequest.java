package com.mdplatform.engine.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.List;

/**
 * 配方计算请求DTO - 用于接收前端传入的配方计算参数
 *
 * <p>功能：
 *     1. 封装配方计算所需的溶剂、锂盐、添加剂和盒子尺寸等参数
 *     2. 提供级联校验，确保嵌套对象的参数也有效
 *     3. 用于分子数量计算和盒子尺寸估算
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Data
public class FormulaRequest {

    /** 溶剂信息列表 */
    @NotEmpty(message = "溶剂信息不能为空")
    @Valid
    private List<SolventInfo> solventInfo;

    /** 锂盐信息 */
    @NotNull(message = "锂盐信息不能为空")
    @Valid
    private SaltInfo saltInfo;

    /** 添加剂信息列表（可选） */
    @Valid
    private List<SolventInfo> additiveInfo;

    /** 盒子尺寸参数 */
    @NotNull(message = "盒子尺寸不能为空")
    @Valid
    private BoxSizeRequest boxSize;

    /** 温度（K） */
    @NotNull(message = "温度不能为空")
    @Positive(message = "温度必须为正数")
    private Double temperature;

    /** 目标计算属性列表（如["density","conductivity"]），为空时默认为["density"] */
    private List<String> targetProperties;
}
