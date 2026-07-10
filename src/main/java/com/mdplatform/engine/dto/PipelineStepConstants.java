package com.mdplatform.engine.dto;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 全流程步骤常量定义
 *
 * <p>定义全流程计算管线的步骤编号和名称映射，共11步（步骤0-9，其中步骤7.5编号为7）。</p>
 *
 * <p>步骤编号与名称对应关系：</p>
 * <pre>
 * 0  - 配方序列化
 * 1  - 分子数量计算
 * 2  - 盒子尺寸计算
 * 3  - Packmol分子堆积
 * 4  - system.lt文件生成
 * 5  - 分子模板调取
 * 6  - Moltemplate命令执行
 * 7  - 文件整理输出
 * 8  - Jinja2脚本生成（原步骤7.5）
 * 9  - LAMMPS三阶段模拟
 * 10 - 后处理分析
 * </pre>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
public final class PipelineStepConstants {

    /** 总步骤数 */
    public static final int TOTAL_STEPS = 11;

    /** 步骤编号与名称的映射（不可变） */
    public static final Map<Integer, String> STEP_NAMES;

    static {
        Map<Integer, String> map = new HashMap<>();
        map.put(0, "配方序列化");
        map.put(1, "分子数量计算");
        map.put(2, "盒子尺寸计算");
        map.put(3, "Packmol分子堆积");
        map.put(4, "system.lt文件生成");
        map.put(5, "分子模板调取");
        map.put(6, "Moltemplate命令执行");
        map.put(7, "文件整理输出");
        map.put(8, "Jinja2脚本生成");
        map.put(9, "LAMMPS三阶段模拟");
        map.put(10, "后处理分析");
        STEP_NAMES = Collections.unmodifiableMap(map);
    }

    private PipelineStepConstants() {
        // 防止实例化
    }

    /**
     * 根据步骤编号获取步骤名称
     *
     * @param stepNumber 步骤编号
     * @return 步骤名称，如果编号无效则返回"未知步骤"
     */
    public static String getStepName(int stepNumber) {
        return STEP_NAMES.getOrDefault(stepNumber, "未知步骤");
    }

    /**
     * 计算进度百分比
     *
     * @param completedSteps 已完成的步骤数
     * @return 进度百分比（0-100）
     */
    public static int calculateProgressPercent(int completedSteps) {
        if (completedSteps <= 0) return 0;
        if (completedSteps >= TOTAL_STEPS) return 100;
        return (int) ((completedSteps * 100.0) / TOTAL_STEPS);
    }
}
