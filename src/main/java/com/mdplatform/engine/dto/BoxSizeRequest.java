package com.mdplatform.engine.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;

/**
 * 盒子尺寸请求DTO - 用于接收前端传入的模拟盒子尺寸参数
 *
 * <p>功能：
 *     1. 封装模拟盒子的三维尺寸信息
 *     2. 支持自动计算和手动指定两种模式
 *     3. 提供尺寸参数的校验规则
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Data
public class BoxSizeRequest {

    /** X方向盒子尺寸（埃） */
    @NotNull(message = "X尺寸不能为空")
    @DecimalMin(value = "0.0", message = "X尺寸不能为负数")
    private Double x;

    /** Y方向盒子尺寸（埃） */
    @NotNull(message = "Y尺寸不能为空")
    @DecimalMin(value = "0.0", message = "Y尺寸不能为负数")
    private Double y;

    /** Z方向盒子尺寸（埃） */
    @NotNull(message = "Z尺寸不能为空")
    @DecimalMin(value = "0.0", message = "Z尺寸不能为负数")
    private Double z;

    /** 是否自动计算盒子尺寸，默认为true */
    private Boolean auto = true;
}
