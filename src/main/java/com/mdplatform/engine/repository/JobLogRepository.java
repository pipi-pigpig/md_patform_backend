package com.mdplatform.engine.repository;

import com.mdplatform.engine.model.JobExecutionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobLogRepository extends JpaRepository<JobExecutionLog, Long> {

    List<JobExecutionLog> findByJobIdOrderByLogTimeDesc(Long jobId);

    List<JobExecutionLog> findByJobIdAndLogLevel(Long jobId, String logLevel);
}