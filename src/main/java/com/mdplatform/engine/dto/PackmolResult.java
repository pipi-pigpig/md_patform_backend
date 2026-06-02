package com.mdplatform.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackmolResult {

    @NotNull(message = "成功状态不能为空")
    private Boolean success;

    private String pdbFilePath;

    private String inputScriptPath;

    @Min(value = 0, message = "原子数不能为负数")
    private Integer atomCount;

    private String executionLog;

    private String errorMessage;

    private Double elapsedTimeSeconds;
}