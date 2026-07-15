package com.mdplatform.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;
import java.util.List;

/**
 * Moltemplate命令执行结果DTO
 *
 * <p>该类用于封装Moltemplate命令执行的执行结果，包含执行状态、输出文件列表、
 * 执行时长、错误信息等关键信息。</p>
 *
 * <p>典型使用场景：</p>
 * <ul>
 *   <li>从MoltemplateExecutionService返回执行结果</li>
 *   <li>作为API响应数据传输对象</li>
 *   <li>记录Moltemplate命令执行的元数据信息</li>
 * </ul>
 *
 * @author MDPlatform Team
 * @version 1.0
 * @since 2026-06-05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoltemplateExecutionResult {

    /**
     * 执行成功标志
     *
     * <p>表示Moltemplate命令执行是否成功完成。</p>
     * <ul>
     *   <li>true: 执行成功，LAMMPS输入文件已生成</li>
     *   <li>false: 执行失败，请查看errorMessage字段获取错误详情</li>
     * </ul>
     */
    @NotNull(message = "成功状态不能为空")
    private Boolean success;

    /**
     * 执行的Moltemplate命令
     *
     * <p>实际执行的Moltemplate命令字符串。</p>
     * <p>示例：moltemplate.sh -atomstyle full system.lt</p>
     * <p>用于日志记录和问题诊断。</p>
     */
    private String command;

    /**
     * 输出文件列表（相对路径）
     *
     * <p>Moltemplate命令执行成功后生成的所有输出文件的相对路径列表。</p>
     * <p>典型输出文件包括：</p>
     * <ul>
     *   <li>system.data - LAMMPS结构文件</li>
     *   <li>system.in.init - 初始化设置文件</li>
     *   <li>system.in.settings - 力场设置文件</li>
     *   <li>system.in - 主输入文件（可选）</li>
     * </ul>
     * <p>所有路径相对于任务根目录。</p>
     * <p>示例：["inputs/system.data", "inputs/system.in.init", "inputs/system.in.settings"]</p>
     */
    @NotEmpty(message = "输出文件列表不能为空", groups = {SuccessValidation.class})
    private List<String> outputFiles;

    /**
     * 执行耗时（秒）
     *
     * <p>Moltemplate命令执行的耗时，单位为秒。</p>
     * <p>用于性能监控和优化参考。</p>
     * <p>示例：1.5表示执行耗时1.5秒</p>
     */
    @PositiveOrZero(message = "执行耗时不能为负数")
    private Double elapsedTimeSeconds;

    /**
     * 执行日志
     *
     * <p>Moltemplate命令执行的完整日志输出。</p>
     * <p>包含标准输出和标准错误信息，用于问题诊断和调试。</p>
     * <p>可能包含：</p>
     * <ul>
     *   <li>Moltemplate处理进度信息</li>
     *   <li>力场参数加载信息</li>
     *   <li>原子类型统计信息</li>
     *   <li>错误和警告信息</li>
     * </ul>
     */
    private String executionLog;

    /**
     * 错误信息
     *
     * <p>当执行失败时，记录详细的错误信息。</p>
     * <p>可能包含：</p>
     * <ul>
     *   <li>Moltemplate命令执行错误</li>
     *   <li>文件读写错误</li>
     *   <li>参数验证错误</li>
     *   <li>系统资源错误</li>
     * </ul>
     */
    private String errorMessage;

    /**
     * 工作目录路径
     *
     * <p>Moltemplate命令执行的工作目录路径。</p>
     * <p>该目录包含system.lt文件和所有生成的输出文件。</p>
     * <p>示例：user_1/jobs/job_123/inputs/</p>
     */
    private String workingDirectory;

    /**
     * system.lt文件路径（相对路径）
     *
     * <p>输入的Moltemplate系统描述文件的相对路径。</p>
     * <p>示例：inputs/system.lt</p>
     * <p>该文件是Moltemplate命令的主要输入文件。</p>
     */
    private String systemLtFilePath;

    /**
     * 成功验证分组接口
     *
     * <p>用于仅在执行成功时进行字段验证的分组标识。</p>
     * <p>当success=true时，某些字段（如outputFiles）
     * 必须非空且有效。</p>
     */
    public interface SuccessValidation {
    }
}