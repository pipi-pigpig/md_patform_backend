package com.mdplatform.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.Map;

/**
 * Moltemplate系统构建结果DTO
 *
 * <p>该类用于封装Moltemplate构建系统文件的执行结果，包含系统文件路径、
 * 分子模板信息、盒子尺寸、力场类型等关键信息。</p>
 *
 * <p>典型使用场景：</p>
 * <ul>
 *   <li>从MoltemplateService返回构建结果</li>
 *   <li>作为API响应数据传输对象</li>
 *   <li>记录系统构建的元数据信息</li>
 * </ul>
 *
 * @author MDPlatform Team
 * @version 1.0
 * @since 2026-06-04
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoltemplateSystemResult {

    /**
     * 构建成功标志
     *
     * <p>表示Moltemplate系统文件构建是否成功完成。</p>
     * <ul>
     *   <li>true: 构建成功，system.lt文件已生成</li>
     *   <li>false: 构建失败，请查看errorMessage字段获取错误详情</li>
     * </ul>
     */
    @NotNull(message = "成功状态不能为空")
    private Boolean success;

    /**
     * system.lt文件路径（相对路径）
     *
     * <p>生成的Moltemplate系统描述文件的相对路径，相对于任务根目录。</p>
     * <p>示例：inputs/system.lt</p>
     * <p>该文件包含完整的系统拓扑结构和力场参数定义。</p>
     */
    private String systemFilePath;

    /**
     * 分子模板文件列表
     *
     * <p>系统构建过程中使用的所有分子模板文件名称列表。</p>
     * <p>示例：["EC.lt", "DMC.lt", "Li.lt", "PF6.lt"]</p>
     * <p>这些模板文件定义了各个分子的结构、力场参数和原子类型。</p>
     */
    @NotEmpty(message = "分子模板列表不能为空", groups = {SuccessValidation.class})
    private List<String> moleculeTemplates;

    /**
     * 分子实例数量映射
     *
     * <p>系统中各分子类型的实例数量映射。</p>
     * <p>键：分子名称（如"EC", "DMC", "Li", "PF6"）</p>
     * <p>值：该分子的实例数量</p>
     * <p>示例：{"EC": 200, "DMC": 200, "Li": 50, "PF6": 50}</p>
     * <p>该映射反映了最终系统的分子组成。</p>
     */
    @NotEmpty(message = "分子实例映射不能为空", groups = {SuccessValidation.class})
    private Map<String, Integer> moleculeInstances;

    /**
     * 总原子数
     *
     * <p>系统中所有原子的总数，包括所有分子实例的所有原子。</p>
     * <p>该数值由分子模板的原子数乘以实例数量累加得到。</p>
     * <p>示例：对于包含200个EC分子（每个10个原子）的系统，总原子数为2000。</p>
     */
    @Positive(message = "总原子数必须为正数", groups = {SuccessValidation.class})
    private Integer totalAtoms;

    /**
     * 盒子尺寸信息
     *
     * <p>模拟盒子的三维尺寸信息，包含x、y、z三个方向的长度。</p>
     * <p>盒子尺寸决定了模拟系统的空间范围。</p>
     */
    private BoxSizeInfo boxSize;

    /**
     * 力场类型
     *
     * <p>系统使用的力场类型标识。</p>
     * <p>常见力场类型：</p>
     * <ul>
     *   <li>oplsaa - OPLS-AA全原子力场</li>
     *   <li>gaff - GAFF通用力场</li>
     *   <li>compass - COMPASS力场</li>
     * </ul>
     */
    private String forcefield;

    /**
     * 错误信息
     *
     * <p>当构建失败时，记录详细的错误信息。</p>
     * <p>可能包含：</p>
     * <ul>
     *   <li>Moltemplate执行错误</li>
     *   <li>文件读写错误</li>
     *   <li>参数验证错误</li>
     *   <li>模板解析错误</li>
     * </ul>
     */
    private String errorMessage;

    /**
     * 执行日志
     *
     * <p>Moltemplate命令执行的完整日志输出。</p>
     * <p>包含标准输出和标准错误信息，用于问题诊断和调试。</p>
     */
    private String executionLog;

    /**
     * 执行耗时（秒）
     *
     * <p>Moltemplate构建过程的执行时间，单位为秒。</p>
     * <p>用于性能监控和优化参考。</p>
     */
    @PositiveOrZero(message = "执行耗时不能为负数")
    private Double elapsedTimeSeconds;

    /**
     * 盒子尺寸信息内部类
     *
     * <p>封装模拟盒子的三维尺寸信息。</p>
     * <p>所有尺寸单位为埃（Å）。</p>
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BoxSizeInfo {

        /**
         * X方向盒子长度（埃）
         *
         * <p>模拟盒子在X轴方向的长度。</p>
         * <p>必须为正数。</p>
         */
        @Positive(message = "X方向长度必须为正数")
        private Double x;

        /**
         * Y方向盒子长度（埃）
         *
         * <p>模拟盒子在Y轴方向的长度。</p>
         * <p>必须为正数。</p>
         */
        @Positive(message = "Y方向长度必须为正数")
        private Double y;

        /**
         * Z方向盒子长度（埃）
         *
         * <p>模拟盒子在Z轴方向的长度。</p>
         * <p>必须为正数。</p>
         */
        @Positive(message = "Z方向长度必须为正数")
        private Double z;

        /**
         * 计算盒子体积
         *
         * @return 盒子体积（立方埃）
         */
        public Double getVolume() {
            if (x == null || y == null || z == null) {
                return null;
            }
            return x * y * z;
        }
    }

    /**
     * 成功验证分组接口
     *
     * <p>用于仅在构建成功时进行字段验证的分组标识。</p>
     * <p>当success=true时，某些字段（如moleculeTemplates、moleculeInstances）
     * 必须非空且有效。</p>
     */
    public interface SuccessValidation {
    }
}