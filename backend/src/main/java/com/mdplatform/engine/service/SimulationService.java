package com.mdplatform.engine.service;

import com.mdplatform.engine.dto.SimulationStatsDto;
import com.mdplatform.engine.model.JobStatus;
import com.mdplatform.engine.model.SimulationJob;
import com.mdplatform.engine.repository.SimulationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 模拟任务服务类
 *
 * <p>提供模拟任务（SimulationJob）的增删改查及状态管理业务逻辑，
 * 支持按状态、软件名称、用户ID、系统ID等多维度查询，
 * 以及任务统计信息获取等功能。</p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SimulationService {

    private final SimulationRepository simulationRepository;

    /**
     * 获取所有模拟任务，按创建时间降序排列
     *
     * @return 模拟任务列表
     */
    public List<SimulationJob> getAllSimulations() {
        return simulationRepository.findAllByOrderByCreateTimeDesc();
    }

    /**
     * 根据任务ID查询模拟任务
     *
     * @param id 任务ID
     * @return 包含模拟任务的Optional对象，若不存在则为空
     */
    public Optional<SimulationJob> getSimulationById(Long id) {
        return simulationRepository.findById(id);
    }

    /**
     * 创建新的模拟任务
     *
     * @param job 待创建的模拟任务对象
     * @return 保存后的模拟任务对象
     */
    @Transactional
    public SimulationJob createSimulation(SimulationJob job) {
        job.setCreateTime(LocalDateTime.now());
        job.setUpdateTime(LocalDateTime.now());
        SimulationJob savedJob = simulationRepository.save(job);
        log.info("Created simulation job: {} with id: {}", savedJob.getJobName(), savedJob.getJobId());
        return savedJob;
    }

    /**
     * 更新模拟任务信息
     *
     * @param id 任务ID
     * @param job 包含更新信息的模拟任务对象
     * @return 包含更新后任务的Optional对象，若任务不存在则为空
     */
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

    /**
     * 删除模拟任务
     *
     * @param id 任务ID
     * @return true表示删除成功，false表示任务不存在
     */
    @Transactional
    public boolean deleteSimulation(Long id) {
        if (simulationRepository.existsById(id)) {
            simulationRepository.deleteById(id);
            log.info("Deleted simulation job with id: {}", id);
            return true;
        }
        return false;
    }

    /**
     * 根据状态查询模拟任务
     *
     * @param status 任务状态（如PENDING、RUNNING、COMPLETED、FAILED、CANCELLED）
     * @return 匹配状态的模拟任务列表
     */
    public List<SimulationJob> getSimulationsByStatus(String status) {
        return simulationRepository.findByStatus(status);
    }

    /**
     * 根据软件名称查询模拟任务
     *
     * @param softwareName 软件名称（如LAMMPS、GROMACS）
     * @return 匹配软件名称的模拟任务列表
     */
    public List<SimulationJob> getSimulationsBySoftwareName(String softwareName) {
        return simulationRepository.findBySoftwareName(softwareName);
    }

    /**
     * 根据用户ID查询模拟任务
     *
     * @param userId 用户ID
     * @return 该用户的模拟任务列表
     */
    public List<SimulationJob> getSimulationsByUserId(Long userId) {
        return simulationRepository.findByUserId(userId);
    }

    /**
     * 根据用户ID查询模拟任务（包含系统任务描述）
     *
     * @param userId 用户ID
     * @return 包含任务描述的模拟任务数据列表
     */
    public List<Object[]> getSimulationsByUserIdWithDescription(Long userId) {
        return simulationRepository.findByUserIdWithTaskDescription(userId);
    }

    /**
     * 根据系统ID查询模拟任务
     *
     * @param systemId 系统ID
     * @return 关联该系统的模拟任务列表
     */
    public List<SimulationJob> getSimulationsBySystemId(Long systemId) {
        return simulationRepository.findBySystemId(systemId);
    }

    /**
     * 根据用户ID和状态查询模拟任务
     *
     * @param userId 用户ID
     * @param status 任务状态
     * @return 匹配条件的模拟任务列表
     */
    public List<SimulationJob> getSimulationsByUserIdAndStatus(Long userId, String status) {
        return simulationRepository.findByUserIdAndStatus(userId, status);
    }

    /**
     * 根据用户ID和软件名称查询模拟任务
     *
     * @param userId 用户ID
     * @param softwareName 软件名称
     * @return 匹配条件的模拟任务列表
     */
    public List<SimulationJob> getSimulationsByUserIdAndSoftware(Long userId, String softwareName) {
        return simulationRepository.findByUserIdAndSoftwareName(userId, softwareName);
    }

    /**
     * 根据用户ID和系统ID查询模拟任务
     *
     * @param userId 用户ID
     * @param systemId 系统ID
     * @return 匹配条件的模拟任务列表
     */
    public List<SimulationJob> getSimulationsByUserIdAndSystemId(Long userId, Long systemId) {
        return simulationRepository.findByUserIdAndSystemId(userId, systemId);
    }

    /**
     * 更新模拟任务状态
     *
     * <p>根据状态值自动设置开始时间（MODELING/RUNNING）和结束时间（COMPLETED/FAILED/CANCELLED），
     * 并计算执行耗时。</p>
     *
     * @param id 任务ID
     * @param status 新的任务状态
     * @return 包含更新后任务的Optional对象，若任务不存在则为空
     */
    @Transactional
    public Optional<SimulationJob> updateSimulationStatus(Long id, String status) {
        return simulationRepository.findById(id).map(job -> {
            job.setStatus(status);
            job.setUpdateTime(LocalDateTime.now());
            // 建模中和运行中均设置开始时间，且从MODELING转为RUNNING时不会重置startTime
            if ((JobStatus.RUNNING.equals(status) || JobStatus.MODELING.equals(status)) && job.getStartTime() == null) {
                job.setStartTime(LocalDateTime.now());
            } else if ((JobStatus.COMPLETED.equals(status) || JobStatus.FAILED.equals(status) || JobStatus.CANCELLED.equals(status)) && job.getEndTime() == null) {
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
     * 获取全局模拟任务统计信息
     *
     * @return 包含各状态任务数量的统计DTO对象
     */
    public SimulationStatsDto getSystemStatistics() {
        long totalJobs = simulationRepository.count();
        long pendingCount = simulationRepository.countByStatus(JobStatus.PENDING);
        long modelingCount = simulationRepository.countByStatus(JobStatus.MODELING);
        long runningCount = simulationRepository.countByStatus(JobStatus.RUNNING);
        long completedCount = simulationRepository.countByStatus(JobStatus.COMPLETED);
        long failedCount = simulationRepository.countByStatus(JobStatus.FAILED);
        long cancelledCount = simulationRepository.countByStatus(JobStatus.CANCELLED);

        return new SimulationStatsDto(totalJobs, pendingCount, modelingCount, runningCount, completedCount, failedCount, cancelledCount);
    }

    /**
     * 获取指定用户的模拟任务统计信息
     *
     * @param userId 用户ID
     * @return 包含该用户各状态任务数量的统计DTO对象
     */
    public SimulationStatsDto getStatsByUserId(Long userId) {
        List<Object[]> statusCounts = simulationRepository.getStatusCountsByUserId(userId);

        long total = 0;
        long pending = 0;
        long modeling = 0;
        long running = 0;
        long completed = 0;
        long failed = 0;
        long cancelled = 0;

        for (Object[] row : statusCounts) {
            String status = (String) row[0];
            long count = ((Number) row[1]).longValue();
            total += count;

            switch (status) {
                case JobStatus.PENDING: pending = count; break;
                case JobStatus.MODELING: modeling = count; break;
                case JobStatus.RUNNING: running = count; break;
                case JobStatus.COMPLETED: completed = count; break;
                case JobStatus.FAILED: failed = count; break;
                case JobStatus.CANCELLED: cancelled = count; break;
            }
        }

        return new SimulationStatsDto(total, pending, modeling, running, completed, failed, cancelled);
    }
}