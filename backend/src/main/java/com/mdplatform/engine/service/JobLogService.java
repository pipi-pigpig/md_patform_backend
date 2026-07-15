package com.mdplatform.engine.service;

import com.mdplatform.engine.model.JobExecutionLog;
import com.mdplatform.engine.repository.JobLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 任务执行日志服务类
 *
 * <p>提供任务执行日志（JobExecutionLog）的增删查业务逻辑，
 * 支持按任务ID查询日志记录。</p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JobLogService {

    private final JobLogRepository jobLogRepository;

    /**
     * 根据任务ID查询执行日志，按日志时间降序排列
     *
     * @param jobId 任务ID
     * @return 该任务的执行日志列表
     */
    public List<JobExecutionLog> getLogsByJobId(Long jobId) {
        log.info("[任务日志] 查询任务执行日志: jobId={}", jobId);
        List<JobExecutionLog> logs = jobLogRepository.findByJobIdOrderByLogTimeDesc(jobId);
        log.info("[任务日志] 查询完成: jobId={}, 日志数量={}", jobId, logs.size());
        return logs;
    }

    /**
     * 创建新的任务执行日志记录
     *
     * @param jobLog 待创建的日志对象
     * @return 保存后的日志对象
     */
    @Transactional
    public JobExecutionLog createLog(JobExecutionLog jobLog) {
        log.info("[任务日志] 创建执行日志: jobId={}, level={}", jobLog.getJobId(), jobLog.getLogLevel());
        JobExecutionLog savedLog = jobLogRepository.save(jobLog);
        log.info("[任务日志] 日志创建成功: id={}", savedLog.getLogId());
        return savedLog;
    }

    /**
     * 删除任务执行日志
     *
     * @param id 日志ID
     * @return true表示删除成功，false表示日志不存在
     */
    @Transactional
    public boolean deleteLog(Long id) {
        log.info("[任务日志] 删除执行日志: id={}", id);
        if (jobLogRepository.existsById(id)) {
            jobLogRepository.deleteById(id);
            log.info("[任务日志] 日志删除成功: id={}", id);
            return true;
        }
        log.warn("[任务日志] 日志不存在，删除失败: id={}", id);
        return false;
    }
}
