package com.mdplatform.service;

import com.mdplatform.dto.SimulationStatsDto;
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

    /**
     * 获取用户的任务及其描述（优化版：使用 JOIN 避免N+1查询）
     * 返回 Object[] 数组，每个元素包含 [SimulationJob, taskDescription]
     */
    public List<Object[]> getSimulationsByUserIdWithDescription(Long userId) {
        return simulationRepository.findByUserIdWithTaskDescription(userId);
    }

    public List<SimulationJob> getSimulationsBySystemId(Long systemId) {
        return simulationRepository.findBySystemId(systemId);
    }

    // ===== 用户相关查询 =====

    /**
     * 按用户和状态查询
     */
    public List<SimulationJob> getSimulationsByUserIdAndStatus(Long userId, String status) {
        return simulationRepository.findByUserIdAndStatus(userId, status);
    }

    /**
     * 按用户和软件查询
     */
    public List<SimulationJob> getSimulationsByUserIdAndSoftware(Long userId, String softwareName) {
        return simulationRepository.findByUserIdAndSoftwareName(userId, softwareName);
    }

    /**
     * 按用户和系统查询
     */
    public List<SimulationJob> getSimulationsByUserIdAndSystemId(Long userId, Long systemId) {
        return simulationRepository.findByUserIdAndSystemId(userId, systemId);
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

    /**
     * 全局统计（管理员用）
     */
    public SimulationStatsDto getSystemStatistics() {
        long totalJobs = simulationRepository.count();
        long pendingCount = simulationRepository.countByStatus("PENDING");
        long runningCount = simulationRepository.countByStatus("RUNNING");
        long completedCount = simulationRepository.countByStatus("COMPLETED");
        long failedCount = simulationRepository.countByStatus("FAILED");
        long cancelledCount = simulationRepository.countByStatus("CANCELLED");

        return new SimulationStatsDto(totalJobs, pendingCount, runningCount, completedCount, failedCount, cancelledCount);
    }

    /**
     * 按用户统计（优化版：使用单次查询）
     */
    public SimulationStatsDto getStatsByUserId(Long userId) {
        List<Object[]> statusCounts = simulationRepository.getStatusCountsByUserId(userId);

        long total = 0;
        long pending = 0;
        long running = 0;
        long completed = 0;
        long failed = 0;
        long cancelled = 0;

        for (Object[] row : statusCounts) {
            String status = (String) row[0];
            long count = ((Number) row[1]).longValue();
            total += count;

            switch (status) {
                case "PENDING": pending = count; break;
                case "RUNNING": running = count; break;
                case "COMPLETED": completed = count; break;
                case "FAILED": failed = count; break;
                case "CANCELLED": cancelled = count; break;
            }
        }

        return new SimulationStatsDto(total, pending, running, completed, failed, cancelled);
    }
}
