package com.mdplatform.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * Packmol分子堆积结果DTO - 用于返回Packmol执行的结果
 *
 * <p>功能：
 *     1. 封装Packmol分子堆积的执行结果，包括成功状态、文件路径、原子数等
 *     2. 记录执行日志和错误信息，便于问题诊断
 *     3. 提供执行耗时信息，用于性能监控
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackmolResult {

    /** 堆积是否成功 */
    @NotNull(message = "成功状态不能为空")
    private Boolean success;

    /** 生成的PDB文件路径（相对路径） */
    private String pdbFilePath;

    /** Packmol输入脚本路径（相对路径） */
    private String inputScriptPath;

    /** 原子总数 */
    @NotNull(message = "原子数不能为空")
    @Min(value = 0, message = "原子数不能为负数")
    private Integer atomCount;

    /** 执行日志 */
    private String executionLog;

    /** 错误信息 */
    private String errorMessage;

    /** 执行耗时（秒） */
    private Double elapsedTimeSeconds;
}
