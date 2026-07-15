package com.mdplatform.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 全流程进度信息DTO
 *
 * <p>用于返回全流程计算任务的进度信息，包含当前步骤、已完成步骤、进度百分比等。</p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineProgressDto {

    /** 任务ID */
    private Long jobId;

    /** 用户ID */
    private Long userId;

    /** 当前状态（PENDING/MODELING/RUNNING/POST_PROCESSING/COMPLETED/FAILED/CANCELLED） */
    private String status;

    /** 当前步骤编号（0-9，共11步） */
    private Integer currentStep;

    /** 当前步骤名称 */
    private String stepName;

    /** 已完成步骤编号列表 */
    private List<Integer> completedSteps;

    /** 总步骤数 */
    private Integer totalSteps;

    /** 进度百分比（0-100） */
    private Integer progressPercent;

    /** 错误信息（任务失败时记录） */
    private String errorMessage;

    /** 任务开始时间 */
    private LocalDateTime startTime;

    /** 任务创建时间 */
    private LocalDateTime createTime;
}
