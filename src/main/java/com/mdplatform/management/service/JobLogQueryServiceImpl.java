package com.mdplatform.management.service;

import com.mdplatform.engine.model.JobExecutionLog;
import com.mdplatform.engine.repository.JobLogRepository;
import com.mdplatform.management.dto.SimulationJobLogResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Profile("!mock")
@Slf4j
@RequiredArgsConstructor
public class JobLogQueryServiceImpl implements JobLogQueryService {

    private final JobLogRepository jobLogRepository;

    @Override
    public SimulationJobLogResponse getJobLogs(Long jobId, int lines, int offset) {
        List<JobExecutionLog> allLogs = jobLogRepository.findByJobIdOrderByLogTimeDesc(jobId);
        int totalLines = allLogs.size();

        int fromIndex = Math.min(offset, totalLines);
        int toIndex = Math.min(offset + lines, totalLines);
        List<JobExecutionLog> page = allLogs.subList(fromIndex, toIndex);

        List<SimulationJobLogResponse.LogEntry> entries = page.stream()
                .map(l -> new SimulationJobLogResponse.LogEntry(l.getLogLevel(), l.getLogContent(), l.getLogTime()))
                .collect(Collectors.toList());

        SimulationJobLogResponse response = new SimulationJobLogResponse();
        response.setJobId(jobId);
        response.setLogEntries(entries);
        response.setTotalLines(totalLines);
        response.setHasMore(toIndex < totalLines);
        return response;
    }

    @Override
    public int getTotalLogCount(Long jobId) {
        return (int) jobLogRepository.findByJobIdOrderByLogTimeDesc(jobId).size();
    }
}
