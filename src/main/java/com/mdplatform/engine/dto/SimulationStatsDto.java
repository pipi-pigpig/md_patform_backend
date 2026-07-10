package com.mdplatform.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模拟任务统计信息DTO - 用于返回各状态任务的数量统计
 *
 * <p>功能：
 *     1. 封装模拟任务各状态的计数信息
 *     2. 用于仪表盘和任务概览页面的数据展示
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimulationStatsDto {

    /** 任务总数 */
    private long total;

    /** 待执行的任务数 */
    private long pending;

    /** 建模中的任务数 */
    private long modeling;

    /** 正在执行的任务数 */
    private long running;

    /** 已完成的任务数 */
    private long completed;

    /** 执行失败的任务数 */
    private long failed;

    /** 已取消的任务数 */
    private long cancelled;
}
