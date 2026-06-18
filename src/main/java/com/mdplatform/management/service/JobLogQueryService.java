package com.mdplatform.management.service;

import com.mdplatform.management.dto.SimulationJobLogResponse;

public interface JobLogQueryService {

    SimulationJobLogResponse getJobLogs(Long jobId, int lines, int offset);

    int getTotalLogCount(Long jobId);
}
