package com.mdplatform.service;

import com.mdplatform.model.SimulationJob;
import com.mdplatform.repository.SimulationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class SimulationService {

    private final SimulationRepository simulationRepository;

    public List<SimulationJob> getAllSimulations() {
        return simulationRepository.findAllByOrderByCreateTimeDesc();
    }

    public Optional<SimulationJob> getSimulationById(Long id) {
        return simulationRepository.findById(id);
    }

    @Transactional
    public SimulationJob createSimulation(SimulationJob job) {
        job.setCreateTime(LocalDateTime.now());
        job.setUpdateTime(LocalDateTime.now());
        SimulationJob savedJob = simulationRepository.save(job);
        log.info("Created simulation job: {} with id: {}", savedJob.getJobName(), savedJob.getJobId());
        return savedJob;
    }

    @Transactional
    public Optional<SimulationJob> updateSimulation(Long id, SimulationJob job) {
        return simulationRepository.findById(id).map(existingJob -> {
            existingJob.setJobName(job.getJobName());
            existingJob.setUserId(job.getUserId());
            existingJob.setSystemId(job.getSystemId());
            existingJob.setSoftwareName(job.getSoftwareName());
            existingJob.setSoftwareVersion(job.getSoftwareVersion());
            existingJob.setTargetProperties(job.getTargetProperties());
            existingJob.setHardwareUsed(job.getHardwareUsed());
            existingJob.setCpuCores(job.getCpuCores());
            existingJob.setGpuInfo(job.getGpuInfo());
            existingJob.setJobRootPath(job.getJobRootPath());
            existingJob.setRandomSeed(job.getRandomSeed());
            existingJob.setUpdateTime(LocalDateTime.now());
            return simulationRepository.save(existingJob);
        });
    }

    @Transactional
    public boolean deleteSimulation(Long id) {
        if (simulationRepository.existsById(id)) {
            simulationRepository.deleteById(id);
            log.info("Deleted simulation job with id: {}", id);
            return true;
        }
        return false;
    }

    public List<SimulationJob> getSimulationsByStatus(String status) {
        return simulationRepository.findByStatus(status);
    }

    public List<SimulationJob> getSimulationsBySoftwareName(String softwareName) {
        return simulationRepository.findBySoftwareName(softwareName);
    }

    public List<SimulationJob> getSimulationsByUserId(Long userId) {
        return simulationRepository.findByUserId(userId);
    }

    public List<SimulationJob> getSimulationsBySystemId(Long systemId) {
        return simulationRepository.findBySystemId(systemId);
    }

    @Transactional
    public Optional<SimulationJob> updateSimulationStatus(Long id, String status) {
        return simulationRepository.findById(id).map(job -> {
            job.setStatus(status);
            job.setUpdateTime(LocalDateTime.now());
            if ("RUNNING".equals(status) && job.getStartTime() == null) {
                job.setStartTime(LocalDateTime.now());
            } else if (("COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status)) && job.getEndTime() == null) {
                job.setEndTime(LocalDateTime.now());
                if (job.getStartTime() != null) {
                    long executionTime = java.time.Duration.between(job.getStartTime(), job.getEndTime()).getSeconds();
                    job.setExecutionTimeS(executionTime);
                }
            }
            return simulationRepository.save(job);
        });
    }

    public String getSystemStatistics() {
        StringBuilder stats = new StringBuilder();
        long totalJobs = simulationRepository.count();
        stats.append("Total Jobs: ").append(totalJobs).append("\n");

        long pendingCount = simulationRepository.countByStatus("PENDING");
        long runningCount = simulationRepository.countByStatus("RUNNING");
        long completedCount = simulationRepository.countByStatus("COMPLETED");
        long failedCount = simulationRepository.countByStatus("FAILED");
        long cancelledCount = simulationRepository.countByStatus("CANCELLED");

        stats.append("PENDING: ").append(pendingCount).append("\n");
        stats.append("RUNNING: ").append(runningCount).append("\n");
        stats.append("COMPLETED: ").append(completedCount).append("\n");
        stats.append("FAILED: ").append(failedCount).append("\n");
        stats.append("CANCELLED: ").append(cancelledCount).append("\n");

        return stats.toString();
    }
}
