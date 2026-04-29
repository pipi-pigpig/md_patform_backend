package com.mdplatform.service;

import com.mdplatform.model.SimulationJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.*;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

@Service
@Slf4j
@RequiredArgsConstructor
public class MDExecutorService {

    private final DockerService dockerService;
    private final FileService fileService;

    @Value("${app.docker.timeout-seconds:3600}")
    private int timeoutSeconds;

    private final ExecutorService executorService = Executors.newCachedThreadPool();


    // 在 executeSimulation() 开头添加
    private void prepareInputFiles(SimulationJob job) throws IOException {
        String jobId = job.getJobId().toString();
        Path srcFile = Paths.get(job.getJobRootPath());
        Path destDir = Paths.get("data/inputs", jobId);
        Files.createDirectories(destDir);
        Files.copy(srcFile, destDir.resolve(srcFile.getFileName()), REPLACE_EXISTING);
    }
    /**
     * 执行MD模拟
     */
    public CompletableFuture<String> executeSimulation(SimulationJob job) {
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                // 检查Docker容器是否可用
                if (!dockerService.isMDContainerRunning()) {
                    log.warn("MD container not running, using fallback execution");
                    return executeFallbackSimulation(job);
                }

                String jobId = job.getJobId().toString();
                String inputFilename = getInputFilename(job.getJobRootPath());

                log.info("Starting {} simulation for job {} with input: {}",
                        job.getSoftwareName(), jobId, inputFilename);

                // 执行MD模拟
                String output;
                if ("LAMMPS".equals(job.getSoftwareName())) {
                    output = dockerService.runLAMMPS(inputFilename, jobId);
                } else {
                    output = dockerService.runGROMACS(inputFilename, jobId);
                }

                // 复制结果文件
                copyResultFiles(job);

                // 解析输出结果
                String resultSummary = parseOutputResult(output, job);

                log.info("Simulation job {} completed successfully", jobId);
                return resultSummary;

            } catch (Exception e) {
                log.error("Failed to execute simulation for job {}", job.getJobId(), e);
                throw new RuntimeException("Simulation execution failed: " + e.getMessage(), e);
            }
        }, executorService);

        // Java 8 fallback for orTimeout
        java.util.concurrent.ScheduledExecutorService delayer = java.util.concurrent.Executors.newScheduledThreadPool(1);
        delayer.schedule(() -> {
            if (!future.isDone()) {
                future.completeExceptionally(new java.util.concurrent.TimeoutException());
            }
        }, timeoutSeconds, TimeUnit.SECONDS);

        future.whenComplete((res, ex) -> delayer.shutdown());

        return future;
    }

    /**
     * 获取输入文件名
     */
    private String getInputFilename(String inputFilePath) {
        if (inputFilePath == null || inputFilePath.isEmpty()) {
            throw new IllegalArgumentException("Input file path is empty");
        }

        Path path = Paths.get(inputFilePath);
        return path.getFileName().toString();
    }

    /**
     * 复制结果文件
     */
    private void copyResultFiles(SimulationJob job) {

        String jobId = job.getJobId().toString();
        Path srcLog = Paths.get("data/results", jobId, "log.lammps");
        Path destLog = Paths.get("results", jobId, "simulation.log");


        try {
            // 复制日志文件
            String logFile = ("LAMMPS".equals(job.getSoftwareName())) ? "log.lammps" : "md.log";
            dockerService.copyResultsFromContainer(jobId, logFile, "simulation.log");

            // 复制轨迹文件
            if ("LAMMPS".equals(job.getSoftwareName())) {
                dockerService.copyResultsFromContainer(jobId, "traj.dump", "trajectory.dump");
            } else {
                dockerService.copyResultsFromContainer(jobId, "md.xtc", "trajectory.xtc");
                dockerService.copyResultsFromContainer(jobId, "md.trr", "trajectory.trr");
                dockerService.copyResultsFromContainer(jobId, "md.edr", "energy.edr");
            }

            log.info("Result files copied for job {}", jobId);

        } catch (Exception e) {
            log.warn("Failed to copy some result files for job {}", jobId, e);
        }
    }

    /**
     * 解析输出结果
     */
    private String parseOutputResult(String output, SimulationJob job) {
        try {
            // 这里根据实际MD软件输出格式进行解析
            // 简化版本，提取关键信息

            StringBuilder result = new StringBuilder();
            result.append("{\n");
            result.append("  \"status\": \"COMPLETED\",\n");
            result.append("  \"software\": \"").append(job.getSoftwareName()).append("\",\n");
            result.append("  \"output_lines\": ").append(output.split("\n").length).append(",\n");

            // 从输出中提取能量信息（示例）
            if ("LAMMPS".equals(job.getSoftwareName())) {
                // 查找LAMMPS的最终能量
                String[] lines = output.split("\n");
                for (int i = lines.length - 1; i >= 0; i--) {
                    if (lines[i].contains("Total energy")) {
                        String energy = lines[i].replaceAll("[^0-9.-]", " ").trim().split("\\s+")[0];
                        result.append("  \"total_energy\": ").append(energy).append(",\n");
                        break;
                    }
                }
            } else {
                // 查找GROMACS的最终能量
                String[] lines = output.split("\n");
                for (int i = lines.length - 1; i >= 0; i--) {
                    if (lines[i].contains("Potential Energy")) {
                        String energy = lines[i].replaceAll("[^0-9.-]", " ").trim().split("\\s+")[0];
                        result.append("  \"potential_energy\": ").append(energy).append(",\n");
                        break;
                    }
                }
            }

            result.append("  \"timestamp\": \"").append(java.time.LocalDateTime.now()).append("\"\n");
            result.append("}");

            return result.toString();

        } catch (Exception e) {
            log.warn("Failed to parse output, returning raw output", e);
            return String.format("{\"status\":\"COMPLETED\",\"raw_output\":\"%s\"}",
                    output.replace("\"", "\\\"").replace("\n", "\\n"));
        }
    }

    /**
     * 备用执行方案（当Docker不可用时）
     */
    private String executeFallbackSimulation(SimulationJob job) {
        log.info("Using fallback execution for job {}", job.getJobId());

        try {
            // 模拟执行延迟
            Thread.sleep(5000);

            // 生成模拟结果
            return String.format(
                    "{\"status\":\"COMPLETED\",\"warning\":\"DOCKER_UNAVAILABLE\",\"mode\":\"FALLBACK\",\"software\":\"%s\"}",
                    job.getSoftwareName()
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "{\"status\":\"FAILED\",\"error\":\"Interrupted\"}";
        }
    }

    /**
     * 检查系统状态
     */
    public String checkSystemStatus() {
        try {
            StringBuilder status = new StringBuilder();

            // 检查Docker容器
            boolean containerRunning = dockerService.isMDContainerRunning();
            status.append("MD Container: ").append(containerRunning ? "RUNNING" : "STOPPED").append("\n");

            if (containerRunning) {
                // 检查GPU
                boolean gpuAvailable = dockerService.isGPUAvailable();
                status.append("GPU Available: ").append(gpuAvailable).append("\n");

                // 获取资源使用情况
                String resourceUsage = dockerService.getResourceUsage();
                status.append("Resource Usage: ").append(resourceUsage).append("\n");
            }

            return status.toString();

        } catch (Exception e) {
            return "System check failed: " + e.getMessage();
        }
    }

    /**
     * 生成输入文件模板
     */
    public String generateInputTemplate(String software) {
        try {
            Path templatePath = Paths.get("templates", software.toLowerCase() + "_template.in");

            if (Files.exists(templatePath)) {
                return new String(Files.readAllBytes(templatePath), java.nio.charset.StandardCharsets.UTF_8);
            }

            // 返回默认模板
            if ("LAMMPS".equals(software)) {
                return getLAMMPSTemplate();
            } else {
                return getGROMACSTemplate();
            }

        } catch (Exception e) {
            log.warn("Failed to load template for {}", software, e);
            return "Template not available";
        }
    }

    private String getLAMMPSTemplate() {
        return "# LAMMPS input template for electrolyte simulation\n" +
                "units       real\n" +
                "atom_style  full\n" +
                "boundary    p p p\n" +
                "\n" +
                "# Read data file\n" +
                "read_data   system.data\n" +
                "\n" +
                "# Force field\n" +
                "pair_style  lj/cut/coul/long 12.0\n" +
                "pair_modify mix arithmetic\n" +
                "kspace_style pppm 1.0e-4\n" +
                "\n" +
                "# Thermostat and barostat\n" +
                "fix         1 all nvt temp 300.0 300.0 100.0\n" +
                "fix         2 all npt temp 300.0 300.0 100.0 iso 1.0 1.0 1000.0\n" +
                "\n" +
                "# Output\n" +
                "thermo      1000\n" +
                "thermo_style custom step temp press vol density etotal\n" +
                "\n" +
                "# Run\n" +
                "run         10000\n";
    }

    private String getGROMACSTemplate() {
        return "; GROMACS mdp template for electrolyte simulation\n" +
                "integrator               = md\n" +
                "dt                       = 0.002\n" +
                "nsteps                   = 50000\n" +
                "\n" +
                "; Temperature coupling\n" +
                "tcoupl                   = v-rescale\n" +
                "tc-grps                  = system\n" +
                "tau_t                    = 0.1\n" +
                "ref_t                    = 300.0\n" +
                "\n" +
                "; Pressure coupling\n" +
                "pcoupl                   = Parrinello-Rahman\n" +
                "pcoupltype               = isotropic\n" +
                "tau_p                    = 2.0\n" +
                "ref_p                    = 1.0\n" +
                "compressibility          = 4.5e-5\n" +
                "\n" +
                "; Electrostatics\n" +
                "coulombtype              = PME\n" +
                "rcoulomb                 = 1.0\n" +
                "vdwtype                  = Cut-off\n" +
                "rvdw                     = 1.0\n" +
                "pbc                      = xyz\n" +
                "\n" +
                "; Output\n" +
                "nstxout                  = 1000\n" +
                "nstvout                  = 1000\n" +
                "nstfout                  = 0\n" +
                "nstlog                   = 1000\n" +
                "nstenergy                = 1000\n" +
                "nstxout-compressed       = 1000\n";
    }
}