package com.mdplatform.engine.dto;

import lombok.Data;

import javax.validation.constraints.*;

/**
 * 溶剂信息DTO - 用于接收和传输溶剂的配方参数
 *
 * <p>功能：
 *     1. 封装溶剂的名称和摩尔分数信息
 *     2. 提供参数校验规则，确保溶剂信息完整有效
 *     3. 摩尔分数范围限制在0到1之间
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Data
public class SolventInfo {

    /** 溶剂名称（如EC、DMC、EMC等） */
    @NotBlank(message = "溶剂名称不能为空")
    private String name;

    /** 摩尔分数（0.0~1.0） */
    @NotNull(message = "摩尔分数不能为空")
    @DecimalMin(value = "0.0", message = "摩尔分数不能小于0")
    @DecimalMax(value = "1.0", message = "摩尔分数不能大于1")
    private Double moleFraction;
}
