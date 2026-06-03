package com.mdplatform.engine.dto;

import lombok.Data;

import javax.validation.constraints.*;

@Data
public class SolventInfo {

    @NotBlank(message = "溶剂名称不能为空")
    private String name;

    @NotNull(message = "摩尔分数不能为空")
    @DecimalMin(value = "0.0", message = "摩尔分数不能小于0")
    @DecimalMax(value = "1.0", message = "摩尔分数不能大于1")
    private Double moleFraction;
}