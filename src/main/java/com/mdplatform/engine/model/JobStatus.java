package com.mdplatform.engine.model;

/**
 * 任务状态常量类
 *
 * <p>定义模拟任务（SimulationJob）的所有合法状态常量，
 * 替代代码中分散的硬编码状态字符串，确保状态值的一致性和可维护性。</p>
 *
 * <p>状态转换流程：</p>
 * <pre>
 * PENDING → MODELING → RUNNING → POST_PROCESSING → POST_PROCESSING_COMPLETED → COMPLETED
 *                                              ↘ FAILED
 * 任意状态 → CANCELLED
 * </pre>
 *
 * <p>状态说明：</p>
 * <ul>
 *   <li>PENDING - 待处理，任务已创建但尚未开始执行</li>
 *   <li>MODELING - 建模中，正在执行Moltemplate建模流程（步骤0-8）</li>
 *   <li>RUNNING - 运行中，正在执行LAMMPS模拟</li>
 *   <li>POST_PROCESSING - 后处理中，正在执行后处理计算</li>
 *   <li>POST_PROCESSING_COMPLETED - 后处理完成，等待最终状态确认</li>
 *   <li>COMPLETED - 已完成，全部流程执行成功</li>
 *   <li>FAILED - 失败，流程执行过程中出现错误</li>
 *   <li>CANCELLED - 已取消，用户主动取消任务</li>
 * </ul>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
public final class JobStatus {

    /** 待处理状态，任务已创建但尚未开始执行 */
    public static final String PENDING = "PENDING";

    /** 建模中状态，正在执行Moltemplate建模流程 */
    public static final String MODELING = "MODELING";

    /** 运行中状态，正在执行LAMMPS模拟 */
    public static final String RUNNING = "RUNNING";

    /** 后处理中状态，正在执行后处理计算 */
    public static final String POST_PROCESSING = "POST_PROCESSING";

    /** 后处理完成状态，等待最终状态确认（缩短为POST_PROC_DONE以适应数据库VARCHAR(20)限制） */
    public static final String POST_PROCESSING_COMPLETED = "POST_PROC_DONE";

    /** 已完成状态，全部流程执行成功 */
    public static final String COMPLETED = "COMPLETED";

    /** 失败状态，流程执行过程中出现错误 */
    public static final String FAILED = "FAILED";

    /** 已取消状态，用户主动取消任务 */
    public static final String CANCELLED = "CANCELLED";

    /**
     * 私有构造函数，防止实例化
     */
    private JobStatus() {
        throw new UnsupportedOperationException("常量类不允许实例化");
    }

    /**
     * 验证给定的状态字符串是否为合法的任务状态
     *
     * <p>检查给定字符串是否匹配本类中定义的任一状态常量。</p>
     *
     * @param status 待验证的状态字符串，可为null
     * @return true表示状态合法，false表示状态不合法或为null
     */
    public static boolean isValidStatus(String status) {
        if (status == null) {
            return false;
        }
        return PENDING.equals(status)
                || MODELING.equals(status)
                || RUNNING.equals(status)
                || POST_PROCESSING.equals(status)
                || POST_PROCESSING_COMPLETED.equals(status)
                || COMPLETED.equals(status)
                || FAILED.equals(status)
                || CANCELLED.equals(status);
    }
}
