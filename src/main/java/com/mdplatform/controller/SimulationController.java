package com.mdplatform.controller;

import com.mdplatform.model.SimulationJob;
import com.mdplatform.service.DockerService;
import com.mdplatform.service.SimulationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/simulations")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class SimulationController {

    private final SimulationService simulationService;
    private final DockerService dockerService;

    @GetMapping
    public ResponseEntity<List<SimulationJob>> getAllSimulations() {
        List<SimulationJob> simulations = simulationService.getAllSimulations();
        return ResponseEntity.ok(simulations);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SimulationJob> getSimulation(@PathVariable Long id) {
        return simulationService.getSimulationById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 在 createSimulation 方法中，修改参数接收
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SimulationJob> createSimulation(
            @RequestParam("file") MultipartFile file,
            @RequestParam("jobName") String jobName,
            @RequestParam("software") String software,
            @RequestParam(value = "systemId", required = false) Long systemId,  // 接收 systemId
            @RequestParam(value = "hardware", defaultValue = "CPU") String hardware,
            @RequestParam(value = "parameters", defaultValue = "{}") String parameters) {

        try {
            log.info("Creating simulation: {}, software: {}, systemId: {}, hardware: {}",
                    jobName, software, systemId, hardware);

            SimulationJob job = new SimulationJob();
            job.setJobName(jobName);
            job.setSoftware(SimulationJob.Software.valueOf(software.toUpperCase()));
            job.setHardwareUsed(SimulationJob.HardwareType.valueOf(hardware.toUpperCase()));
            job.setParameters(parameters);
            job.setSystemId(systemId);  // 设置 systemId

            // 修改 createSimulation 方法调用，传递 systemId
            SimulationJob created = simulationService.createSimulation(job, file, systemId);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);

        } catch (IllegalArgumentException e) {
            log.error("Invalid parameter value", e);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Failed to create simulation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSimulation(@PathVariable Long id) {
        boolean deleted = simulationService.deleteSimulation(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<SimulationJob>> getSimulationsByStatus(
            @PathVariable String status) {
        try {
            SimulationJob.JobStatus jobStatus = SimulationJob.JobStatus.valueOf(status.toUpperCase());
            List<SimulationJob> simulations = simulationService.getSimulationsByStatus(jobStatus);
            return ResponseEntity.ok(simulations);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/software/{software}")
    public ResponseEntity<List<SimulationJob>> getSimulationsBySoftware(
            @PathVariable String software) {
        try {
            SimulationJob.Software soft = SimulationJob.Software.valueOf(software.toUpperCase());
            List<SimulationJob> simulations = simulationService.getSimulationsBySoftware(soft);
            return ResponseEntity.ok(simulations);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelSimulation(@PathVariable Long id) {
        simulationService.cancelSimulation(id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<SimulationJob> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {

        String statusStr = request.get("status");
        try {
            SimulationJob.JobStatus status = SimulationJob.JobStatus.valueOf(statusStr.toUpperCase());
            return simulationService.updateSimulationStatus(id, status)
                    .map(ResponseEntity::ok)
                    .orElse(ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<String> getStatistics() {
        String stats = simulationService.getSystemStatistics();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/template/{software}")
    public ResponseEntity<String> getInputTemplate(@PathVariable String software) {
        try {
            SimulationJob.Software soft = SimulationJob.Software.valueOf(software.toUpperCase());
            String template = simulationService.getInputTemplate(soft);
            return ResponseEntity.ok(template);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid software type");
        }
    }

    @GetMapping("/{id}/download/{filename}")
    public ResponseEntity<Resource> downloadResultFile(
            @PathVariable Long id,
            @PathVariable String filename) {

        try {
            // 构建文件路径
            Path filePath = Paths.get("./results", id.toString(), filename).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + filename + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            log.error("File path error", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}/files")
    public ResponseEntity<Map<String, Object>> getJobFiles(@PathVariable Long id) {
        try {
            // 这里可以返回作业相关的文件列表
            // 简化版本，返回空结构
            Map<String, Object> files = new HashMap<>();
            files.put("input_files", Arrays.asList());
            files.put("result_files", Arrays.asList());
            files.put("job_id", id);
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{id}/trigger")
    public ResponseEntity<String> triggerSimulation(@PathVariable Long id) {
        try {
            simulationService.executeSimulationAsync(id);
            return ResponseEntity.ok("Simulation triggered for job " + id);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to trigger simulation: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> getJobStatus(@PathVariable Long id) {
        return simulationService.getSimulationById(id)
                .map(job -> {
                    Map<String, Object> status = new HashMap<>();
                    status.put("id", job.getId());
                    status.put("status", job.getStatus());
                    status.put("startTime", job.getStartTime());
                    status.put("endTime", job.getEndTime());
                    status.put("executionTime", job.getExecutionTime());
                    return ResponseEntity.ok(status);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // 在 SimulationController.java 中添加
    @GetMapping("/docker-status")
    public ResponseEntity<Map<String, Object>> checkDockerStatus() {
        Map<String, Object> status = new HashMap<>();

        try {
            boolean dockerAvailable = dockerService.isDockerAvailable();
            boolean containerRunning = dockerService.isMDContainerRunning();
            String containerStatus = dockerService.getContainerStatus();

            status.put("docker_available", dockerAvailable);
            status.put("container_running", containerRunning);
            status.put("container_status", containerStatus);
            status.put("container_name", "md-engine");

            // 尝试执行简单命令
            if (containerRunning) {
                List<String> testCmd = Arrays.asList("echo", "Docker test successful");
                String testResult = dockerService.executeCommandInContainer(
                        "md-engine", testCmd, "/workspace");
                status.put("test_command", testResult);
            }

            return ResponseEntity.ok(status);

        } catch (Exception e) {
            status.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(status);
        }
    }

    @GetMapping("/{id}/logs")
    public ResponseEntity<Map<String, Object>> getJobLogs(@PathVariable Long id) {
        Map<String, Object> logResponse = new HashMap<>();
        logResponse.put("logs", Arrays.asList(
                createLogEntry("INFO", "Initializing simulation environment..."),
                createLogEntry("INFO", "Reading data file system.data..."),
                createLogEntry("INFO", "Setting up force field parameters..."),
                createLogEntry("INFO", "Starting energy minimization..."),
                createLogEntry("INFO", "Minimization completed in 124 steps"),
                createLogEntry("INFO", "Starting NVT equilibration..."),
                createLogEntry("WARN", "Temperature fluctuates more than 5K in first 100 steps"),
                createLogEntry("INFO", "NVT equilibration completed (200ps)"),
                createLogEntry("INFO", "Starting NPT equilibration..."),
                createLogEntry("INFO", "Density stabilized at 1.21 g/cm3"),
                createLogEntry("INFO", "NPT equilibration completed (500ps)"),
                createLogEntry("INFO", "Starting production run..."),
                createLogEntry("INFO", "Production run 25% completed"),
                createLogEntry("INFO", "Production run 50% completed"),
                createLogEntry("INFO", "Production run 75% completed"),
                createLogEntry("INFO", "Production run 100% completed"),
                createLogEntry("INFO", "Writing final trajectory and restart files..."),
                createLogEntry("INFO", "Simulation job finished successfully.")
        ));
        return ResponseEntity.ok(logResponse);
    }

    private Map<String, String> createLogEntry(String level, String message) {
        Map<String, String> entry = new HashMap<>();
        entry.put("timestamp", java.time.LocalDateTime.now().toString());
        entry.put("level", level);
        entry.put("message", message);
        return entry;
    }
}