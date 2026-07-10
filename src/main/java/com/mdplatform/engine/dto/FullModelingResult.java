package com.mdplatform.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.Map;

/**
 * 完整建模流程结果DTO
 *
 * <p>该类用于封装完整建模流程的执行结果，包含所有8个步骤的详细结果信息，
 * 包括分子数量计算、盒子尺寸计算、Packmol堆积、Moltemplate系统文件生成、
 * Moltemplate命令执行、文件整理输出、Jinja2脚本生成和LAMMPS模拟执行。</p>
 *
 * <p>典型使用场景：</p>
 * <ul>
 *   <li>从MoltemplateService.executeFullModeling()返回完整建模结果</li>
 *   <li>作为API响应数据传输对象</li>
 *   <li>记录建模流程的完整执行状态</li>
 * </ul>
 *
 * <p>建模流程步骤（完整8步）：</p>
 * <ol>
 *   <li>步骤1：分子数量计算 - 根据配方计算各分子类型的数量</li>
 *   <li>步骤2：盒子尺寸计算 - 根据分子数量和密度计算模拟盒子尺寸</li>
 *   <li>步骤3：Packmol堆积 - 使用Packmol生成初始构型文件</li>
 *   <li>步骤4：Moltemplate系统文件生成 - 生成system.lt系统描述文件</li>
 *   <li>步骤5：调取分子模板 - 加载分子模板文件（在步骤4中完成）</li>
 *   <li>步骤6：执行Moltemplate命令 - 将system.lt转换为LAMMPS输入文件</li>
 *   <li>步骤7：文件整理输出 - 将生成的文件整理到inputs/目录</li>
 *   <li>步骤7.5：Jinja2脚本生成 - 使用Jinja2模板生成LAMMPS输入脚本</li>
 *   <li>步骤8：LAMMPS模拟执行 - 执行三阶段LAMMPS模拟（能量最小化→平衡→生产）</li>
 * </ol>
 *
 * @author MDPlatform Team
 * @version 1.0
 * @since 2026-06-04
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FullModelingResult {

    /**
     * 整体成功标志
     *
     * <p>表示完整建模流程是否成功完成。</p>
     * <ul>
     *   <li>true: 所有步骤均成功完成</li>
     *   <li>false: 至少一个步骤失败，请查看errorMessage和各步骤结果获取详情</li>
     * </ul>
     *
     * <p>注意：只有当所有步骤都成功时，此标志才为true。</p>
     */
    @NotNull(message = "成功状态不能为空")
    private Boolean success;

    /**
     * 分子数量计算结果
     *
     * <p>包含各分子类型的数量计算结果，包括：</p>
     * <ul>
     *   <li>分子名称</li>
     *   <li>分子ID</li>
     *   <li>分子数量</li>
     *   <li>分子电荷</li>
     * </ul>
     *
     * <p>该结果由配方计算服务生成，用于后续的Packmol堆积和Moltemplate系统构建。</p>
     */
    private List<MoleculeCountResult> moleculeCountResult;

    /**
     * 盒子尺寸计算结果
     *
     * <p>包含模拟盒子的三维尺寸信息：</p>
     * <ul>
     *   <li>X方向尺寸（埃）</li>
     *   <li>Y方向尺寸（埃）</li>
     *   <li>Z方向尺寸（埃）</li>
     *   <li>盒子体积（立方埃）</li>
     * </ul>
     *
     * <p>盒子尺寸根据分子数量和目标密度计算得到。</p>
     */
    private BoxSizeResult boxSizeResult;

    /**
     * Packmol堆积结果
     *
     * <p>包含Packmol分子堆积的执行结果：</p>
     * <ul>
     *   <li>成功标志</li>
     *   <li>PDB文件路径（packed_system.pdb）</li>
     *   <li>原子总数</li>
     *   <li>执行日志</li>
     *   <li>执行耗时</li>
     * </ul>
     *
     * <p>PDB文件包含系统的初始三维构型，供后续Moltemplate使用。</p>
     */
    private PackmolResult packmolResult;

    /**
     * Moltemplate系统文件生成结果（步骤5）
     *
     * <p>包含Moltemplate系统文件构建的执行结果：</p>
     * <ul>
     *   <li>成功标志</li>
     *   <li>system.lt文件路径</li>
     *   <li>分子模板列表</li>
     *   <li>分子实例映射</li>
     *   <li>总原子数</li>
     *   <li>盒子尺寸信息</li>
     *   <li>力场类型</li>
     *   <li>执行日志</li>
     *   <li>执行耗时</li>
     * </ul>
     *
     * <p>system.lt文件包含完整的系统拓扑结构和力场参数定义。</p>
     */
    private MoltemplateSystemResult systemResult;

    /**
     * Moltemplate命令执行结果（步骤6）
     *
     * <p>包含Moltemplate命令执行的执行结果：</p>
     * <ul>
     *   <li>成功标志</li>
     *   <li>执行的命令</li>
     *   <li>输出文件列表（system.data、system.in.init、system.in.settings）</li>
     *   <li>执行耗时</li>
     *   <li>执行日志</li>
     *   <li>错误信息</li>
     * </ul>
     *
     * <p>该步骤将system.lt文件转换为LAMMPS可识别的输入文件。</p>
     */
    private MoltemplateExecutionResult moltemplateExecutionResult;

    /**
     * 文件整理输出结果（步骤7）
     *
     * <p>包含文件整理操作的执行结果：</p>
     * <ul>
     *   <li>成功标志</li>
     *   <li>已移动文件列表</li>
     *   <li>缺失文件列表</li>
     *   <li>目标目录路径</li>
     *   <li>源目录路径</li>
     *   <li>执行日志</li>
     *   <li>错误信息</li>
     * </ul>
     *
     * <p>该步骤将生成的LAMMPS输入文件整理到inputs/目录，符合文件输入输出规则。</p>
     */
    private FileOrganizeResult fileOrganizeResult;

    /**
     * LAMMPS脚本生成结果（步骤7.5）
     *
     * <p>包含Jinja2模板渲染生成LAMMPS输入脚本的执行结果：</p>
     * <ul>
     *   <li>success: Boolean - 渲染是否成功</li>
     *   <li>rendered_files: List&lt;String&gt; - 成功渲染的文件名列表（如in.minimization、in.equilibrium、in.production）</li>
     *   <li>errors: List&lt;String&gt; - 渲染过程中的错误信息列表</li>
     *   <li>validation_passed: Boolean - 生成的脚本文件验证是否通过</li>
     * </ul>
     *
     * <p>该步骤使用Jinja2模板引擎根据任务配置动态生成LAMMPS输入脚本，
     * 支持根据target_properties动态添加计算命令。</p>
     */
    private Map<String, Object> lammpsScriptResult;

    /**
     * LAMMPS执行结果（步骤8）
     *
     * <p>包含LAMMPS三阶段模拟执行的执行结果：</p>
     * <ul>
     *   <li>success: Boolean - 模拟执行是否成功</li>
     *   <li>result_summary: String - 模拟结果摘要（JSON格式）</li>
     *   <li>target_properties: List&lt;String&gt; - 目标性质列表（如density、conductivity等）</li>
     * </ul>
     *
     * <p>该步骤执行LAMMPS三阶段模拟：能量最小化→平衡模拟→生产模拟，
     * 并根据目标性质收集对应的输出文件。</p>
     */
    private Map<String, Object> lammpsExecutionResult;

    /**
     * 总原子数
     *
     * <p>系统中所有原子的总数。</p>
     * <p>该数值应与Packmol和Moltemplate结果中的原子数一致。</p>
     */
    @PositiveOrZero(message = "总原子数不能为负数")
    private Integer totalAtoms;

    /**
     * 总电荷
     *
     * <p>系统中所有分子的总电荷。</p>
     * <p>理想情况下应为0（电中性系统）。</p>
     */
    private Double totalCharge;

    /**
     * 是否电中性
     *
     * <p>表示系统是否达到电中性状态。</p>
     * <ul>
     *   <li>true: 总电荷为0，系统电中性</li>
     *   <li>false: 总电荷不为0，系统带电</li>
     * </ul>
     *
     * <p>电中性是分子动力学模拟的基本要求。</p>
     */
    private Boolean isElectricallyNeutral;

    /**
     * 错误信息
     *
     * <p>当建模流程失败时，记录详细的错误信息。</p>
     * <p>可能包含：</p>
     * <ul>
     *   <li>失败的步骤名称</li>
     *   <li>具体的错误描述</li>
     *   <li>错误发生的位置</li>
     *   <li>建议的修复方案</li>
     * </ul>
     *
     * <p>注意：即使整体成功，某些步骤也可能有警告信息。</p>
     */
    private String errorMessage;

    /**
     * 失败的步骤名称
     *
     * <p>记录导致建模流程失败的步骤名称。</p>
     * <p>可能的值：</p>
     * <ul>
     *   <li>"分子数量计算"</li>
     *   <li>"盒子尺寸计算"</li>
     *   <li>"Packmol堆积"</li>
     *   <li>"Moltemplate系统文件生成"</li>
     *   <li>"Moltemplate命令执行"</li>
     *   <li>"文件整理输出"</li>
     *   <li>"step7.5_jinja2_script_generation"</li>
     *   <li>"step8_lammps_execution"</li>
     *   <li>"未知步骤"</li>
     * </ul>
     *
     * <p>当success=false时，此字段标识具体失败的步骤。</p>
     */
    private String failedStep;

    /**
     * 执行日志
     *
     * <p>完整建模流程的执行日志，包含所有步骤的日志输出。</p>
     * <p>用于问题诊断和调试。</p>
     */
    private String executionLog;

    /**
     * 总执行耗时（秒）
     *
     * <p>完整建模流程的总执行时间，单位为秒。</p>
     * <p>等于所有步骤执行耗时的总和。</p>
     * <p>用于性能监控和优化参考。</p>
     */
    @PositiveOrZero(message = "执行耗时不能为负数")
    private Double totalElapsedTimeSeconds;

    /**
     * 配方文件路径
     *
     * <p>配方配置文件的相对路径（相对于任务根目录）。</p>
     * <p>示例：inputs/formula_config.json</p>
     * <p>该文件包含完整的配方信息，供各步骤使用。</p>
     */
    private String formulaFilePath;

    /**
     * 用户ID
     *
     * <p>执行建模流程的用户ID。</p>
     * <p>用于文件路径生成和权限验证。</p>
     */
    private Long userId;

    /**
     * 任务ID
     *
     * <p>执行建模流程的任务ID。</p>
     * <p>用于文件路径生成和任务跟踪。</p>
     */
    private Long jobId;
}