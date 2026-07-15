package com.mdplatform.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 文件整理结果DTO
 *
 * <p>该类用于封装文件整理操作的执行结果，包含成功状态、文件列表、
 * 缺失文件列表、错误信息等关键信息。</p>
 *
 * <p>典型使用场景：</p>
 * <ul>
 *   <li>从MoltemplateExecutionService返回文件整理结果</li>
 *   <li>作为API响应数据传输对象</li>
 *   <li>记录文件移动和验证的元数据信息</li>
 * </ul>
 *
 * <p>文件整理操作将生成的LAMMPS输入文件从工作目录移动到inputs/目录，
 * 确保文件结构符合文件输入输出规则。</p>
 *
 * @author MDPlatform Team
 * @version 1.0
 * @since 2026-06-05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileOrganizeResult {

    /**
     * 整理成功标志
     *
     * <p>表示文件整理操作是否成功完成。</p>
     * <ul>
     *   <li>true: 整理成功，所有必需文件已移动到目标目录</li>
     *   <li>false: 整理失败，请查看errorMessage字段获取错误详情</li>
     * </ul>
     */
    @NotNull(message = "成功状态不能为空")
    private Boolean success;

    /**
     * 已移动文件列表（相对路径）
     *
     * <p>成功移动到目标目录的所有文件的相对路径列表。</p>
     * <p>典型文件包括：</p>
     * <ul>
     *   <li>inputs/system.lt - Moltemplate系统描述文件</li>
     *   <li>inputs/system.data - LAMMPS结构文件</li>
     *   <li>inputs/system.in.init - 初始化设置文件</li>
     *   <li>inputs/system.in.settings - 力场设置文件</li>
     *   <li>inputs/packmol.inp - Packmol输入脚本</li>
     *   <li>inputs/packed_system.pdb - Packmol初始构型文件</li>
     * </ul>
     * <p>所有路径相对于任务根目录。</p>
     * <p>示例：["inputs/system.data", "inputs/system.in.init", "inputs/system.in.settings"]</p>
     */
    @NotEmpty(message = "已移动文件列表不能为空", groups = {SuccessValidation.class})
    private List<String> movedFiles;

    /**
     * 缺失文件列表
     *
     * <p>在整理过程中发现缺失或无法访问的文件列表。</p>
     * <p>可能包含：</p>
     * <ul>
     *   <li>应该存在但未找到的文件</li>
     *   <li>文件权限不足无法访问的文件</li>
     *   <li>文件大小为0的损坏文件</li>
     * </ul>
     * <p>如果success=true，此列表应为空。</p>
     * <p>示例：["system.in.init", "packmol.inp"]</p>
     */
    private List<String> missingFiles;

    /**
     * 目标目录路径（相对路径）
     *
     * <p>文件移动的目标目录路径。</p>
     * <p>通常为inputs/目录，用于存储所有LAMMPS输入文件。</p>
     * <p>示例：user_1/jobs/job_123/inputs/</p>
     */
    private String targetDirectory;

    /**
     * 源目录路径（相对路径）
     *
     * <p>文件移动的源目录路径。</p>
     * <p>通常为Moltemplate执行的工作目录。</p>
     * <p>示例：user_1/jobs/job_123/temp/moltemplate_temp/</p>
     */
    private String sourceDirectory;

    /**
     * 错误信息
     *
     * <p>当整理失败时，记录详细的错误信息。</p>
     * <p>可能包含：</p>
     * <ul>
     *   <li>文件移动错误</li>
     *   <li>文件权限错误</li>
     *   <li>文件验证错误</li>
     *   <li>目录创建错误</li>
     * </ul>
     */
    private String errorMessage;

    /**
     * 执行日志
     *
     * <p>文件整理操作的完整日志输出。</p>
     * <p>包含每个文件的移动状态和验证结果，用于问题诊断和调试。</p>
     * <p>可能包含：</p>
     * <ul>
     *   <li>文件移动进度信息</li>
     *   <li>文件验证结果</li>
     *   <li>文件大小检查结果</li>
     *   <li>警告和错误信息</li>
     * </ul>
     */
    private String executionLog;

    /**
     * 总文件数
     *
     * <p>需要整理的文件总数。</p>
     * <p>用于统计和进度显示。</p>
     */
    private Integer totalFilesCount;

    /**
     * 成功移动文件数
     *
     * <p>成功移动到目标目录的文件数量。</p>
     * <p>如果success=true，此数量应等于totalFilesCount。</p>
     */
    private Integer movedFilesCount;

    /**
     * 成功验证分组接口
     *
     * <p>用于仅在整理成功时进行字段验证的分组标识。</p>
     * <p>当success=true时，某些字段（如movedFiles）
     * 必须非空且有效。</p>
     */
    public interface SuccessValidation {
    }
}