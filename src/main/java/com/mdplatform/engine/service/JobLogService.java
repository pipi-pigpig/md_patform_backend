package com.mdplatform.engine.service;

import com.mdplatform.engine.model.JobExecutionLog;
import com.mdplatform.engine.repository.JobLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class JobLogService {

    private final JobLogRepository jobLogRepository;

    public List<JobExecutionLog> getLogsByJobId(Long jobId) {
        return jobLogRepository.findByJobIdOrderByLogTimeDesc(jobId);
    }

    @Transactional
    public JobExecutionLog createLog(JobExecutionLog log) {
        return jobLogRepository.save(log);
    }

    @Transactional
    public boolean deleteLog(Long id) {
        if (jobLogRepository.existsById(id)) {
            jobLogRepository.deleteById(id);
            return true;
        }
        return false;
    }
}