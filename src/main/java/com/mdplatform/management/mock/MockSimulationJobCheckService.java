package com.mdplatform.management.mock;

import com.mdplatform.management.service.SimulationJobCheckService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@Profile("mock")
@Slf4j
public class MockSimulationJobCheckService implements SimulationJobCheckService {

    @Override
    public boolean hasAssociatedJobs(Long systemId) {
        log.debug("Mock: checking associated jobs for systemId={}", systemId);
        return false;
    }

    @Override
    public boolean hasRunningJobs(Long systemId) {
        log.debug("Mock: checking running jobs for systemId={}", systemId);
        return false;
    }

    @Override
    public List<String> getAssociatedJobStatuses(Long systemId) {
        log.debug("Mock: getting job statuses for systemId={}", systemId);
        return Collections.emptyList();
    }
}
