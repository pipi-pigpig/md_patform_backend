package com.mdplatform.engine.dto;

import lombok.Data;

import javax.validation.constraints.*;

/**
 * 锂盐信息DTO - 用于接收和传输锂盐的配方参数
 *
 * <p>功能：
 *     1. 封装锂盐的阳离子、阴离子和浓度信息
 *     2. 提供参数校验规则，确保盐信息完整有效
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Data
public class SaltInfo {

    /** 阳离子名称（如Li） */
    @NotBlank(message = "阳离子名称不能为空")
    private String cation;

    /** 阴离子名称（如PF6、TFSI） */
    @NotBlank(message = "阴离子名称不能为空")
    private String anion;

    /** 盐浓度（mol/L） */
    @NotNull(message = "浓度不能为空")
    @Positive(message = "浓度必须为正数")
    private Double concentration;
}
