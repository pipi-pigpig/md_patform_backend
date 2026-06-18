package com.mdplatform.management.service;

import com.mdplatform.management.model.SimulationJob;
import com.mdplatform.management.repository.SimulationJobRepository;
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
public class SimulationJobCheckServiceImpl implements SimulationJobCheckService {

    private static final List<String> RUNNING_STATUSES = List.of("PENDING", "RUNNING");

    private final SimulationJobRepository jobRepository;

    @Override
    public boolean hasAssociatedJobs(Long systemId) {
        return jobRepository.existsBySystemId(systemId);
    }

    @Override
    public boolean hasRunningJobs(Long systemId) {
        return jobRepository.existsBySystemIdAndStatusIn(systemId, RUNNING_STATUSES);
    }

    @Override
    public List<String> getAssociatedJobStatuses(Long systemId) {
        List<SimulationJob> jobs = jobRepository.findBySystemId(systemId);
        return jobs.stream().map(SimulationJob::getStatus).collect(Collectors.toList());
    }
}
