package com.mdplatform.management.mock;

import com.mdplatform.management.dto.SimulationJobLogResponse;
import com.mdplatform.management.service.JobLogQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("mock")
@Slf4j
public class MockJobLogQueryService implements JobLogQueryService {

    @Override
    public SimulationJobLogResponse getJobLogs(Long jobId, int lines, int offset) {
        log.debug("Mock: getting logs for jobId={}, lines={}, offset={}", jobId, lines, offset);
        SimulationJobLogResponse response = new SimulationJobLogResponse();
        response.setJobId(jobId);
        response.setLogEntries(java.util.Collections.emptyList());
        response.setTotalLines(0);
        response.setHasMore(false);
        return response;
    }

    @Override
    public int getTotalLogCount(Long jobId) {
        log.debug("Mock: getting total log count for jobId={}", jobId);
        return 0;
    }
}
