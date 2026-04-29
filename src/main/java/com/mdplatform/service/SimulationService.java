package com.mdplatform.service;

import com.mdplatform.model.ElectrolyteSystem;
import com.mdplatform.model.SimulationJob;
import com.mdplatform.repository.SimulationRepository;
import com.mdplatform.repository.SystemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class SimulationService {

    private final SimulationRepository simulationRepository;
    private final SystemRepository systemRepository;
    private final FileService fileService;
    private final MDExecutorService mdExecutorService;

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${app.results-dir:./results}")
    private String resultsDir;

    /**
     * 创建模拟任务
     */
    @Transactional
    public SimulationJob createSimulation(SimulationJob job, MultipartFile inputFile, Long systemId) throws Exception {
        try {
            // 设置 systemId
            job.setSystemId(systemId);

            // 保存输入文件
            String jobId = String.valueOf(System.currentTimeMillis());
            Path jobDir = Paths.get(uploadDir, jobId);
            Files.createDirectories(jobDir);

            String filename = inputFile.getOriginalFilename();
            Path targetPath = jobDir.resolve(filename);
            Files.copy(inputFile.getInputStream(), targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            job.setInputFilePath(targetPath.toString());
            job.setStatus(SimulationJob.JobStatus.PENDING);

            // 保存到数据库
            SimulationJob savedJob = simulationRepository.save(job);

            // 重命名目录为实际jobId
            Path newJobDir = Paths.get(uploadDir, savedJob.getId().toString());
            Files.move(jobDir, newJobDir);

            // 更新文件路径
            Path newFilePath = newJobDir.resolve(filename);
            savedJob.setInputFilePath(newFilePath.toString());
            simulationRepository.save(savedJob);

            log.info("Created simulation job: {} with id: {}", savedJob.getJobName(), savedJob.getId());

            // 异步执行模拟
            executeSimulationAsync(savedJob.getId());

            return savedJob;

        } catch (IOException e) {
            log.error("Failed to save input file", e);
            throw new RuntimeException("Failed to save input file: " + e.getMessage(), e);
        }
    }

    /**
     * 异步执行模拟
     */
    @Async
    @Transactional
    public CompletableFuture<Void> executeSimulationAsync(Long jobId) {
        return CompletableFuture.runAsync(() -> {
            try {
                SimulationJob job = simulationRepository.findById(jobId)
                        .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

                // 更新状态为运行中
                job.setStatus(SimulationJob.JobStatus.RUNNING);
                job.setStartTime(LocalDateTime.now());
                simulationRepository.save(job);

                log.info("Starting {} simulation for job: {}", job.getSoftware(), jobId);

                // 执行模拟
                String result = mdExecutorService.executeSimulation(job).join();

                // 更新完成状态
                job.setStatus(SimulationJob.JobStatus.COMPLETED);
                job.setEndTime(LocalDateTime.now());
                job.setExecutionTime(Duration.between(job.getStartTime(), job.getEndTime()).getSeconds());
                job.setResultSummary(result);
                simulationRepository.save(job);

                log.info("Simulation job {} completed successfully", jobId);

            } catch (Exception e) {
                log.error("Simulation execution failed for job {}", jobId, e);
                simulationRepository.findById(jobId).ifPresent(job -> {
                    job.setStatus(SimulationJob.JobStatus.FAILED);
                    job.setEndTime(LocalDateTime.now());
                    job.setResultSummary("{\"error\":\"" + e.getMessage() + "\"}");
                    simulationRepository.save(job);
                });
            }
        });
    }

    /**
     * 获取所有模拟任务
     */
    public List<SimulationJob> getAllSimulations() {
        return simulationRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * 根据ID获取模拟任务
     */
    public Optional<SimulationJob> getSimulationById(Long id) {
        return simulationRepository.findById(id);
    }

    /**
     * 根据状态获取模拟任务
     */
    public List<SimulationJob> getSimulationsByStatus(SimulationJob.JobStatus status) {
        return simulationRepository.findByStatus(status);
    }

    /**
     * 根据软件获取模拟任务
     */
    public List<SimulationJob> getSimulationsBySoftware(SimulationJob.Software software) {
        return simulationRepository.findBySoftware(software);
    }

    /**
     * 更新模拟任务状态
     */
    @Transactional
    public Optional<SimulationJob> updateSimulationStatus(Long id, SimulationJob.JobStatus status) {
        return simulationRepository.findById(id).map(job -> {
            job.setStatus(status);
            if (status == SimulationJob.JobStatus.RUNNING && job.getStartTime() == null) {
                job.setStartTime(LocalDateTime.now());
            } else if ((status == SimulationJob.JobStatus.COMPLETED ||
                    status == SimulationJob.JobStatus.FAILED ||
                    status == SimulationJob.JobStatus.CANCELLED) &&
                    job.getEndTime() == null) {
                job.setEndTime(LocalDateTime.now());
                if (job.getStartTime() != null) {
                    job.setExecutionTime(Duration.between(job.getStartTime(), job.getEndTime()).getSeconds());
                }
            }
            return simulationRepository.save(job);
        });
    }

    /**
     * 取消模拟任务
     */
    @Transactional
    public void cancelSimulation(Long id) {
        updateSimulationStatus(id, SimulationJob.JobStatus.CANCELLED);
    }

    /**
     * 删除模拟任务
     */
    @Transactional
    public boolean deleteSimulation(Long id) {
        if (simulationRepository.existsById(id)) {
            // 删除相关文件
            simulationRepository.findById(id).ifPresent(job -> {
                try {
                    // 删除输入文件
                    if (job.getInputFilePath() != null) {
                        Files.deleteIfExists(Paths.get(job.getInputFilePath()));
                    }
                    // 删除结果文件
                    Path resultDir = Paths.get(resultsDir, id.toString());
                    if (Files.exists(resultDir)) {
                        Files.walk(resultDir)
                                .sorted((p1, p2) -> -p1.compareTo(p2))
                                .forEach(path -> {
                                    try {
                                        Files.deleteIfExists(path);
                                    } catch (IOException e) {
                                        log.warn("Failed to delete file: {}", path, e);
                                    }
                                });
                    }
                } catch (Exception e) {
                    log.warn("Failed to delete files for job {}", id, e);
                }
            });

            simulationRepository.deleteById(id);
            log.info("Deleted simulation job with id: {}", id);
            return true;
        }
        return false;
    }

    /**
     * 获取系统统计信息
     */
    public String getSystemStatistics() {
        StringBuilder stats = new StringBuilder();
        stats.append("Total Jobs: ").append(simulationRepository.count()).append("\n");

        for (SimulationJob.JobStatus status : SimulationJob.JobStatus.values()) {
            long count = simulationRepository.countByStatus(status);
            stats.append(status).append(": ").append(count).append("\n");
        }

        stats.append("\n").append(mdExecutorService.checkSystemStatus());

        return stats.toString();
    }

    /**
     * 获取输入文件模板
     */
    public String getInputTemplate(SimulationJob.Software software) {
        return mdExecutorService.generateInputTemplate(software);
    }
}