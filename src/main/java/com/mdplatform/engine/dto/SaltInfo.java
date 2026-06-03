package com.mdplatform.engine.dto;

import lombok.Data;

import javax.validation.constraints.*;

@Data
public class SaltInfo {

    @NotBlank(message = "阳离子名称不能为空")
    private String cation;

    @NotBlank(message = "阴离子名称不能为空")
    private String anion;

    @NotNull(message = "浓度不能为空")
    @Positive(message = "浓度必须为正数")
    private Double concentration;
}