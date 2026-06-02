package com.mdplatform.engine.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;

@Data
public class BoxSizeRequest {

    @DecimalMin(value = "0.0", message = "X尺寸不能为负数")
    private Double x;

    @DecimalMin(value = "0.0", message = "Y尺寸不能为负数")
    private Double y;

    @DecimalMin(value = "0.0", message = "Z尺寸不能为负数")
    private Double z;

    private Boolean auto = true;
}