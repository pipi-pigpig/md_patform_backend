package com.mdplatform.management.service;

import com.mdplatform.management.model.SysOperationLog;
import com.mdplatform.management.repository.OperationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 操作日志服务类
 *
 * <p>提供系统操作日志（SysOperationLog）的增删查业务逻辑，
 * 支持按用户ID查询操作日志记录。</p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OperationLogService {

    private final OperationLogRepository operationLogRepository;

    /**
     * 根据用户ID查询操作日志，按操作时间降序排列
     *
     * @param userId 用户ID
     * @return 该用户的操作日志列表
     */
    public List<SysOperationLog> getLogsByUserId(Long userId) {
        log.info("[操作日志] 查询用户操作日志: userId={}", userId);
        List<SysOperationLog> logs = operationLogRepository.findByUserIdOrderByOperationTimeDesc(userId);
        log.info("[操作日志] 查询完成: userId={}, 日志数量={}", userId, logs.size());
        return logs;
    }

    /**
     * 创建新的操作日志记录
     *
     * @param operationLog 待创建的操作日志对象
     * @return 保存后的操作日志对象
     */
    @Transactional
    public SysOperationLog createLog(SysOperationLog operationLog) {
        log.info("[操作日志] 创建操作日志: userId={}, operationType={}", operationLog.getUserId(), operationLog.getOperationType());
        SysOperationLog savedLog = operationLogRepository.save(operationLog);
        log.info("[操作日志] 操作日志创建成功: logId={}", savedLog.getLogId());
        return savedLog;
    }

    /**
     * 删除操作日志
     *
     * @param id 日志ID
     * @return true表示删除成功，false表示日志不存在
     */
    @Transactional
    public boolean deleteLog(Long id) {
        log.info("[操作日志] 删除操作日志: id={}", id);
        if (operationLogRepository.existsById(id)) {
            operationLogRepository.deleteById(id);
            log.info("[操作日志] 操作日志删除成功: id={}", id);
            return true;
        }
        log.warn("[操作日志] 操作日志不存在，删除失败: id={}", id);
        return false;
    }
}
