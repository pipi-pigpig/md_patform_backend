package com.mdplatform.management.model;

import javax.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系统操作日志实体类，对应sys_operation_log_table表，记录用户操作审计日志
 *
 * <p>功能：
 *     1. 记录用户的关键操作行为（登录、创建、删除等）
 *     2. 存储操作来源IP和操作结果状态
 *     3. 提供完整的操作审计追踪能力
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Data
@Entity
@Table(name = "sys_operation_log_table")
public class SysOperationLog {

    /** 日志ID，主键自增 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    /** 操作用户ID，关联sys_user_table表 */
    @Column(name = "user_id")
    private Long userId;

    /** 操作类型（如LOGIN-登录、CREATE-创建、UPDATE-更新、DELETE-删除等） */
    @Column(name = "operation_type", length = 50)
    private String operationType;

    /** 操作内容详情（描述具体执行的操作） */
    @Column(name = "operation_content", columnDefinition = "TEXT")
    private String operationContent;

    /** 操作IP地址（发起操作的客户端IP） */
    @Column(name = "operation_ip", length = 50)
    private String operationIp;

    /** 操作状态（0-失败，1-成功） */
    @Column(name = "operation_status")
    private Integer operationStatus;

    /** 操作时间 */
    @Column(name = "operation_time")
    private LocalDateTime operationTime;
}