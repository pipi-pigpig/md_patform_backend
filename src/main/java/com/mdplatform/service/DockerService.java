package com.mdplatform.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
public class DockerService {

    private final DockerClient dockerClient;

    @Value("${app.docker.md-container-name:md-engine}")
    private String mdContainerName;

    @Value("${app.upload-dir:./uploads}")
    private String uploadDir;

    @Value("${app.results-dir:./results}")
    private String resultsDir;

    @Value("${app.docker.md-input-path:/workspace/inputs}")
    private String mdInputPath;

    @Value("${app.docker.md-output-path:/workspace/results}")
    private String mdOutputPath;

    public DockerService(@Autowired(required = false) DockerClient dockerClient) {
        this.dockerClient = dockerClient;
        if (this.dockerClient == null) {
            log.warn("DockerClient is not available. Some features will be limited.");
        }
    }

    public boolean isDockerAvailable() {
        return dockerClient != null;
    }

    /**
     * 检查MD引擎容器是否运行
     */
    public boolean isMDContainerRunning() {
        if (!isDockerAvailable()) {
            log.warn("Docker is not available. Using mock status.");
            return true; // 假设容器运行在mock模式
        }
        if (dockerClient == null) {
            return false;
        }

        try {
            return dockerClient.listContainersCmd().exec().stream()
                    .anyMatch(container ->
                            Arrays.asList(container.getNames()).contains("/" + mdContainerName) &&
                                    "running".equals(container.getState()));
        } catch (Exception e) {
            log.error("Failed to check container status", e);
            return false;
        }
    }

    /**
     * 在容器内执行命令
     */
    public String executeCommandInContainer(String containerId, List<String> commands, String workDir) {
        if (dockerClient == null) {
            log.warn("Docker client not available, using mock execution");
            return "Mock execution - Docker not available";
        }

        try {
            // 创建执行命令
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd(commands.toArray(new String[0]))
                    .exec();

            // 执行命令并捕获输出
            StringBuilder output = new StringBuilder();
            dockerClient.execStartCmd(execCreateCmdResponse.getId())
                    .exec(new com.github.dockerjava.api.async.ResultCallback.Adapter<com.github.dockerjava.api.model.Frame>() {
                        @Override
                        public void onNext(com.github.dockerjava.api.model.Frame frame) {
                            try {
                                String text = new String(frame.getPayload());
                                output.append(text);
                                log.info("Container output: {}", text);
                            } catch (Exception e) {
                                log.error("Error reading frame", e);
                            }
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            log.error("Error executing command", throwable);
                        }

                        @Override
                        public void onComplete() {
                            log.info("Command execution completed");
                        }
                    }).awaitCompletion();

            // 检查执行状态
            com.github.dockerjava.api.command.InspectExecResponse inspectExecResponse =
                    dockerClient.inspectExecCmd(execCreateCmdResponse.getId()).exec();

            int exitCode = inspectExecResponse.getExitCode();
            if (exitCode != 0) {
                log.error("Command failed with exit code: {}", exitCode);
                return "Command failed with exit code: " + exitCode + "\nOutput:\n" + output;
            }

            return output.toString();

        } catch (Exception e) {
            log.error("Failed to execute command in container", e);
            return "Failed to execute command: " + e.getMessage();
        }
    }

    /**
     * 运行LAMMPS模拟
     */
    public String runLAMMPS(String inputFilename, String jobId) {
        try {
            // 准备容器内的路径
            String containerInputPath = mdInputPath + "/" + jobId + "/" + inputFilename;
            String containerOutputPath = mdOutputPath + "/" + jobId;

            // 创建容器内输出目录
            List<String> mkdirCmd = Arrays.asList("mkdir", "-p", containerOutputPath);
            executeCommandInContainer(mdContainerName, mkdirCmd, "/workspace");

            // 运行LAMMPS命令
            List<String> lammpsCmd = Arrays.asList(
                    "mpirun", "-np", "4", "lmp",
                    "-in", containerInputPath,
                    "-log", containerOutputPath + "/log.lammps",
                    "-screen", "none"
            );

            log.info("Executing LAMMPS command: {}", String.join(" ", lammpsCmd));
            return executeCommandInContainer(mdContainerName, lammpsCmd, "/workspace");

        } catch (Exception e) {
            log.error("Failed to run LAMMPS", e);
            return "LAMMPS execution failed: " + e.getMessage();
        }
    }

    /**
     * 运行GROMACS模拟
     */
    public String runGROMACS(String inputFilename, String jobId) {
        try {
            // 准备容器内的路径
            String containerInputPath = mdInputPath + "/" + jobId;
            String containerOutputPath = mdOutputPath + "/" + jobId;

            // 创建容器内输出目录
            List<String> mkdirCmd = Arrays.asList("mkdir", "-p", containerOutputPath);
            executeCommandInContainer(mdContainerName, mkdirCmd, "/workspace");

            // 复制文件到工作目录
            List<String> copyCmd = Arrays.asList(
                    "cp",
                    mdInputPath + "/" + jobId + "/*",
                    containerInputPath + "/"
            );
            executeCommandInContainer(mdContainerName, copyCmd, "/workspace");

            // 进入目录并运行GROMACS
            List<String> gromacsCmd = Arrays.asList(
                    "bash", "-c",
                    "cd " + containerInputPath + " && " +
                            "gmx grompp -f em.mdp -c system.gro -p system.top -o em.tpr && " +
                            "gmx mdrun -v -deffnm em -ntmpi 4 && " +
                            "gmx grompp -f nvt.mdp -c em.gro -r em.gro -p system.top -o nvt.tpr && " +
                            "gmx mdrun -v -deffnm nvt -ntmpi 4 && " +
                            "gmx grompp -f npt.mdp -c nvt.gro -r nvt.gro -t nvt.cpt -p system.top -o npt.tpr && " +
                            "gmx mdrun -v -deffnm npt -ntmpi 4 && " +
                            "gmx grompp -f md.mdp -c npt.gro -t npt.cpt -p system.top -o md.tpr && " +
                            "gmx mdrun -v -deffnm md -ntmpi 4"
            );

            log.info("Executing GROMACS command");
            return executeCommandInContainer(mdContainerName, gromacsCmd, "/workspace");

        } catch (Exception e) {
            log.error("Failed to run GROMACS", e);
            return "GROMACS execution failed: " + e.getMessage();
        }
    }

    /**
     * 从容器复制结果文件到宿主机
     */
    public boolean copyResultsFromContainer(String jobId, String sourcePath, String targetFilename) {
        try {
            // 创建宿主机结果目录
            Path targetDir = Paths.get(resultsDir, jobId);
            Files.createDirectories(targetDir);

            // 容器内的源文件路径
            String containerSourcePath = mdOutputPath + "/" + jobId + "/" + sourcePath;

            // 使用docker cp命令复制文件
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "docker", "cp",
                    mdContainerName + ":" + containerSourcePath,
                    targetDir.resolve(targetFilename).toString()
            );

            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                log.info("Successfully copied results from container for job {}", jobId);
                return true;
            } else {
                log.error("Failed to copy results from container, exit code: {}", exitCode);
                return false;
            }

        } catch (Exception e) {
            log.error("Failed to copy results from container", e);
            return false;
        }
    }

    /**
     * 获取容器状态
     */
    public String getContainerStatus() {
        if (dockerClient == null) {
            return "DOCKER_NOT_AVAILABLE";
        }

        try {
            return dockerClient.listContainersCmd().exec().stream()
                    .filter(container -> Arrays.asList(container.getNames()).contains("/" + mdContainerName))
                    .findFirst()
                    .map(container -> container.getState())
                    .orElse("NOT_FOUND");
        } catch (Exception e) {
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * 检查GPU是否可用
     */
    public boolean isGPUAvailable() {
        try {
            List<String> nvidiaCmd = Arrays.asList("nvidia-smi", "--query-gpu=name", "--format=csv,noheader");
            String result = executeCommandInContainer(mdContainerName, nvidiaCmd, "/");
            return result != null && !result.trim().isEmpty();
        } catch (Exception e) {
            log.warn("GPU not available or NVIDIA tools not installed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取系统资源使用情况
     */
    public String getResourceUsage() {
        try {
            List<String> cpuCmd = Arrays.asList("bash", "-c", "top -bn1 | grep 'Cpu(s)' | awk '{print $2}' | cut -d'%' -f1");
            List<String> memCmd = Arrays.asList("bash", "-c", "free | grep Mem | awk '{print $3/$2 * 100.0}'");

            String cpuUsage = executeCommandInContainer(mdContainerName, cpuCmd, "/").trim();
            String memUsage = executeCommandInContainer(mdContainerName, memCmd, "/").trim();

            return String.format("CPU: %s%%, Memory: %s%%", cpuUsage, memUsage);
        } catch (Exception e) {
            return "Unable to get resource usage: " + e.getMessage();
        }
    }
}