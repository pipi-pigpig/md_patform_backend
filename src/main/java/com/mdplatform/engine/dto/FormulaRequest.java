package com.mdplatform.engine.dto;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.*;
import java.util.List;

@Data
public class FormulaRequest {

    @NotEmpty(message = "溶剂信息不能为空")
    @Valid
    private List<SolventInfo> solventInfo;

    @NotNull(message = "锂盐信息不能为空")
    @Valid
    private SaltInfo saltInfo;

    @Valid
    private List<SolventInfo> additiveInfo;

    @NotNull(message = "盒子尺寸不能为空")
    @Valid
    private BoxSizeRequest boxSize;

    @NotNull(message = "温度不能为空")
    @Positive(message = "温度必须为正数")
    private Double temperature;
}