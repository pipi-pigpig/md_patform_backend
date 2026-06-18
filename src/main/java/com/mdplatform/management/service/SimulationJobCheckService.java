package com.mdplatform.management.service;

import java.util.List;

public interface SimulationJobCheckService {

    boolean hasAssociatedJobs(Long systemId);

    boolean hasRunningJobs(Long systemId);

    List<String> getAssociatedJobStatuses(Long systemId);
}
