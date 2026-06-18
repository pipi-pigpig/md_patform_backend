package com.mdplatform.management.service;

import com.mdplatform.management.dto.*;
import com.mdplatform.management.model.ElectrolyteSystem;
import com.mdplatform.management.model.SimulationJob;
import com.mdplatform.management.repository.ElectrolyteSystemRepository;
import com.mdplatform.management.repository.SimulationJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class SimulationJobService {

    private static final int MAX_NAME_LENGTH = 200;
    private static final Set<String> CANCELABLE_STATUSES = Set.of("PENDING", "RUNNING");
    private static final Set<String> DELETABLE_STATUSES = Set.of("COMPLETED", "FAILED", "CANCELLED");

    private final SimulationJobRepository jobRepository;
    private final ElectrolyteSystemRepository systemRepository;
    private final JobLogQueryService jobLogQueryService;

    // F-J001 任务创建
    public SimulationJobResponse createJob(Long userId, SimulationJobCreateRequest request) {
        validateCreateRequest(request, userId);

        SimulationJob job = new SimulationJob();
        job.setJobName(request.getJobName());
        job.setUserId(userId);
        job.setSystemId(request.getSystemId());
        job.setSoftwareName(request.getSoftwareName() != null ? request.getSoftwareName() : "LAMMPS");
        job.setSoftwareVersion(request.getSoftwareVersion() != null ? request.getSoftwareVersion() : "29Oct2024");
        job.setStatus("PENDING");
        job.setTargetProperties(request.getTargetPropertiesJson());
        job.setHardwareUsed(request.getHardwareUsed() != null ? request.getHardwareUsed() : "CPU");
        job.setCpuCores(request.getCpuCores() != null ? request.getCpuCores() : "8");
        job.setGpuInfo(request.getGpuInfo());
        job.setHardwareEnvironment(buildHardwareEnvironment(request));
        job.setJobRootPath(generateJobRootPath());
        job.setRandomSeed(request.getRandomSeed() != null ? request.getRandomSeed() : new Random().nextInt(100000));
        job.setTaskDescription(request.getTaskDescription());
        job.setCreateTime(LocalDateTime.now());
        job.setUpdateTime(LocalDateTime.now());

        SimulationJob saved = jobRepository.save(job);
        log.info("Created simulation job: {} for user: {}", saved.getJobId(), userId);
        return SimulationJobResponse.fromEntity(saved);
    }

    // F-J002 状态查询
    public SimulationJobStatusResponse getJobStatus(Long userId, Long jobId) {
        SimulationJob job = findAndCheckOwnership(userId, jobId);

        SimulationJobStatusResponse response = new SimulationJobStatusResponse();
        response.setId(job.getJobId());
        response.setStatus(job.getStatus());
        response.setProgress(estimateProgress(job));
        response.setCurrentStep(estimateCurrentStep(job));
        response.setStartTime(job.getStartTime());
        response.setEstimatedEndTime(estimateEndTime(job));
        return response;
    }

    // F-J003 日志查看
    public SimulationJobLogResponse getJobLogs(Long userId, Long jobId, int lines, int offset) {
        findAndCheckOwnership(userId, jobId);
        return jobLogQueryService.getJobLogs(jobId, lines, offset);
    }

    // F-J004 任务取消
    public void cancelJob(Long userId, Long jobId) {
        SimulationJob job = findAndCheckOwnership(userId, jobId);

        if (!CANCELABLE_STATUSES.contains(job.getStatus())) {
            throw new IllegalArgumentException("当前任务状态不允许取消");
        }

        job.setStatus("CANCELLED");
        job.setUpdateTime(LocalDateTime.now());
        jobRepository.save(job);
        log.info("Cancelled job: {} for user: {}", jobId, userId);
    }

    // F-J005 任务删除
    public void deleteJob(Long userId, Long jobId) {
        SimulationJob job = findAndCheckOwnership(userId, jobId);

        if (!DELETABLE_STATUSES.contains(job.getStatus())) {
            throw new IllegalArgumentException("当前任务状态不允许删除");
        }

        jobRepository.delete(job);
        log.info("Deleted job: {} for user: {}", jobId, userId);
    }

    // F-J006 任务重试
    public void retryJob(Long userId, Long jobId) {
        SimulationJob job = findAndCheckOwnership(userId, jobId);

        if (!"FAILED".equals(job.getStatus())) {
            throw new IllegalArgumentException("只有FAILED状态的任务可以重试");
        }

        job.setStatus("PENDING");
        job.setErrorMessage(null);
        job.setStartTime(null);
        job.setEndTime(null);
        job.setExecutionTimeS(null);
        job.setResultSummary(null);
        job.setUpdateTime(LocalDateTime.now());
        jobRepository.save(job);
        log.info("Retried job: {} for user: {}", jobId, userId);
    }

    // F-J007 列表查询
    public PageResponse<SimulationJobListResponse> listJobs(Long userId, String status, String keyword, int page, int pageSize) {
        int safePage = Math.max(page - 1, 0);
        int safeSize = Math.max(pageSize, 1);
        PageRequest pageRequest = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createTime"));

        Page<SimulationJob> result;
        boolean hasStatus = status != null && !status.isBlank();
        boolean hasKeyword = keyword != null && !keyword.isBlank();

        if (hasStatus && hasKeyword) {
            result = jobRepository.searchByUserIdAndStatusAndKeyword(userId, status, keyword.trim(), pageRequest);
        } else if (hasStatus) {
            result = jobRepository.findByUserIdAndStatusOrderByCreateTimeDesc(userId, status, pageRequest);
        } else if (hasKeyword) {
            result = jobRepository.searchByUserIdAndKeyword(userId, keyword.trim(), pageRequest);
        } else {
            result = jobRepository.findByUserIdOrderByCreateTimeDesc(userId, pageRequest);
        }

        List<SimulationJobListResponse> list = result.getContent().stream()
                .map(job -> {
                    String systemName = systemRepository.findById(job.getSystemId())
                            .map(ElectrolyteSystem::getSystemName)
                            .orElse(null);
                    return SimulationJobListResponse.fromEntityWithSystemName(job, systemName);
                })
                .collect(Collectors.toList());

        return PageResponse.of(result.getTotalElements(), page, safeSize, list);
    }

    // F-J008 详情查询
    public SimulationJobResponse getJobDetail(Long userId, Long jobId) {
        SimulationJob job = findAndCheckOwnership(userId, jobId);

        String systemName = systemRepository.findById(job.getSystemId())
                .map(ElectrolyteSystem::getSystemName)
                .orElse(null);

        return SimulationJobResponse.fromEntityWithSystemName(job, systemName);
    }

    // 统计信息
    public SimulationJobStatsResponse getJobStats(Long userId) {
        SimulationJobStatsResponse stats = new SimulationJobStatsResponse();
        stats.setTotal(jobRepository.countByUserId(userId));
        stats.setPending(jobRepository.countByUserIdAndStatus(userId, "PENDING"));
        stats.setRunning(jobRepository.countByUserIdAndStatus(userId, "RUNNING"));
        stats.setCompleted(jobRepository.countByUserIdAndStatus(userId, "COMPLETED"));
        stats.setFailed(jobRepository.countByUserIdAndStatus(userId, "FAILED"));
        stats.setCancelled(jobRepository.countByUserIdAndStatus(userId, "CANCELLED"));
        return stats;
    }

    private void validateCreateRequest(SimulationJobCreateRequest request, Long userId) {
        if (request.getJobName() == null || request.getJobName().isBlank()) {
            throw new IllegalArgumentException("任务名称不能为空");
        }
        if (request.getJobName().length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("任务名称不能超过" + MAX_NAME_LENGTH + "字符");
        }

        ElectrolyteSystem system = systemRepository.findById(request.getSystemId())
                .orElseThrow(() -> new IllegalArgumentException("配方不存在"));
        if (!system.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权限操作该配方");
        }

        if (request.getTargetPropertiesJson() == null || request.getTargetPropertiesJson().isBlank()) {
            throw new IllegalArgumentException("目标属性不能为空");
        }
    }

    private SimulationJob findAndCheckOwnership(Long userId, Long jobId) {
        SimulationJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在"));
        if (!job.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权限操作该任务");
        }
        return job;
    }

    private String buildHardwareEnvironment(SimulationJobCreateRequest request) {
        String hw = request.getHardwareUsed() != null ? request.getHardwareUsed() : "CPU";
        String cores = request.getCpuCores() != null ? request.getCpuCores() : "8";
        if ("GPU".equalsIgnoreCase(hw) && request.getGpuInfo() != null) {
            return hw + " " + cores + "核 " + request.getGpuInfo();
        }
        return hw + " " + cores + "核";
    }

    private String generateJobRootPath() {
        return "/data/jobs/" + UUID.randomUUID().toString().substring(0, 8);
    }

    private Integer estimateProgress(SimulationJob job) {
        switch (job.getStatus()) {
            case "PENDING": return 0;
            case "RUNNING": return 50;
            case "COMPLETED": return 100;
            default: return 0;
        }
    }

    private String estimateCurrentStep(SimulationJob job) {
        switch (job.getStatus()) {
            case "PENDING": return "排队中";
            case "RUNNING": return "计算中";
            case "COMPLETED": return "已完成";
            case "FAILED": return "执行失败";
            case "CANCELLED": return "已取消";
            default: return null;
        }
    }

    private LocalDateTime estimateEndTime(SimulationJob job) {
        if (job.getStartTime() != null && "RUNNING".equals(job.getStatus())) {
            return job.getStartTime().plusHours(2);
        }
        return null;
    }
}
