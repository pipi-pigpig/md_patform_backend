package com.mdplatform.management.model;

import javax.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "sys_operation_log_table")
public class SysOperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "operation_type", length = 50)
    private String operationType;

    @Column(name = "operation_content", columnDefinition = "TEXT")
    private String operationContent;

    @Column(name = "operation_ip", length = 50)
    private String operationIp;

    @Column(name = "operation_status")
    private Integer operationStatus;

    @Column(name = "operation_time")
    private LocalDateTime operationTime;
}