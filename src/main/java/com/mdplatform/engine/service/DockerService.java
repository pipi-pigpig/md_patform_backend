package com.mdplatform.engine.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.async.ResultCallback;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Docker服务类
 * 
 * 提供Docker容器的管理和命令执行功能，用于运行分子动力学模拟软件（LAMMPS、GROMACS等）
 * 支持容器状态监控、命令执行、文件传输等核心功能
 */
@Service
@Slf4j
public class DockerService {

    /**
     * 错误类型常量 - Docker连接失败
     * 当无法与Docker守护进程建立连接时使用此错误类型
     */
    public static final String ERROR_TYPE_CONNECTION = "CONNECTION_ERROR";
    
    /**
     * 错误类型常量 - 容器未运行
     * 当目标容器不存在或未处于运行状态时使用此错误类型
     */
    public static final String ERROR_TYPE_CONTAINER = "CONTAINER_ERROR";
    
    /**
     * 错误类型常量 - 命令执行失败
     * 当容器内命令执行返回非零退出码或发生异常时使用此错误类型
     */
    public static final String ERROR_TYPE_EXECUTION = "EXECUTION_ERROR";

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

    /**
     * 构造函数
     * 
     * @param dockerClient Docker客户端实例，可选注入
     *                      如果未注入，服务将以模拟模式运行
     */
    public DockerService(@Autowired(required = false) DockerClient dockerClient) {
        this.dockerClient = dockerClient;
        if (this.dockerClient == null) {
            log.warn("[Docker服务] DockerClient未注入，部分功能将受限，服务将以模拟模式运行");
        } else {
            log.info("[Docker服务] DockerClient初始化成功");
        }
    }

    /**
     * 检查Docker服务是否可用
     * 
     * @return true表示Docker服务可用，false表示不可用
     */
    public boolean isDockerAvailable() {
        boolean available = dockerClient != null;
        log.debug("[Docker服务] Docker可用性检查: {}", available ? "可用" : "不可用");
        return available;
    }

    /**
     * 检查MD容器是否正在运行
     * 
     * 验证指定的分子动力学模拟容器是否存在且处于运行状态
     * 如果Docker服务不可用，将返回模拟状态（true）以便开发测试
     * 
     * @return true表示容器正在运行，false表示容器未运行或不存在
     */
    public boolean isMDContainerRunning() {
        if (!isDockerAvailable()) {
            log.warn("[Docker服务] Docker不可用，使用模拟状态");
            return true;
        }
        if (dockerClient == null) {
            return false;
        }

        try {
            log.debug("[Docker服务] 正在检查容器 '{}' 的运行状态", mdContainerName);
            boolean isRunning = dockerClient.listContainersCmd().exec().stream()
                    .anyMatch(container ->
                            Arrays.asList(container.getNames()).contains("/" + mdContainerName) &&
                                    "running".equals(container.getState()));
            
            log.info("[Docker服务] 容器 '{}' 运行状态: {}", mdContainerName, isRunning ? "运行中" : "未运行");
            return isRunning;
        } catch (Exception e) {
            log.error("[Docker服务] 检查容器状态失败，错误类型: {}, 错误信息: {}", 
                    ERROR_TYPE_CONNECTION, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 在容器中执行命令
     * 
     * 在指定的Docker容器中执行命令，并返回命令输出结果
     * 该方法会记录详细的执行日志，包括命令内容、执行耗时、退出码和输出内容
     * 
     * @param containerId 容器ID或容器名称
     * @param commands    要执行的命令列表，每个元素代表命令的一个参数
     * @param workDir     工作目录（当前未使用，保留供未来扩展）
     * @return 命令执行结果，包含标准输出和标准错误的内容
     */
    public String executeCommandInContainer(String containerId, List<String> commands, String workDir) {
        long startTime = System.currentTimeMillis();
        String fullCommand = String.join(" ", commands);
        
        log.info("[命令执行] 开始在容器中执行命令");
        log.info("[命令执行] 容器名称: {}", containerId);
        log.info("[命令执行] 完整命令: {}", fullCommand);
        log.info("[命令执行] 工作目录: {}", workDir);
        
        if (dockerClient == null) {
            log.warn("[命令执行] Docker客户端不可用，使用模拟执行模式");
            log.info("[命令执行] 模拟执行完成，返回模拟结果");
            return "Mock execution - Docker not available";
        }

        try {
            log.debug("[命令执行] 正在创建执行实例...");
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd(commands.toArray(new String[0]))
                    .exec();

            String execId = execCreateCmdResponse.getId();
            log.debug("[命令执行] 执行实例ID: {}", execId);

            StringBuilder output = new StringBuilder();
            final int[] lineCounter = {0};
            
            log.info("[命令执行] 开始执行命令，等待输出...");
            dockerClient.execStartCmd(execId)
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            try {
                                String text = new String(frame.getPayload());
                                output.append(text);
                                
                                String[] lines = text.split("\n");
                                for (String line : lines) {
                                    if (!line.trim().isEmpty()) {
                                        lineCounter[0]++;
                                        log.info("[命令输出-第{}行] {}", lineCounter[0], line.trim());
                                    }
                                }
                            } catch (Exception e) {
                                log.error("[命令执行] 读取输出帧失败，错误类型: {}, 错误信息: {}", 
                                        ERROR_TYPE_EXECUTION, e.getMessage(), e);
                            }
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            log.error("[命令执行] 命令执行发生错误，错误类型: {}, 错误信息: {}", 
                                    ERROR_TYPE_EXECUTION, throwable.getMessage(), throwable);
                        }

                        @Override
                        public void onComplete() {
                            long elapsed = System.currentTimeMillis() - startTime;
                            log.info("[命令执行] 命令执行完成，总输出行数: {}, 耗时: {}ms", 
                                    lineCounter[0], elapsed);
                        }
                    }).awaitCompletion();

            com.github.dockerjava.api.command.InspectExecResponse inspectExecResponse =
                    dockerClient.inspectExecCmd(execId).exec();

            int exitCode = inspectExecResponse.getExitCode();
            long elapsed = System.currentTimeMillis() - startTime;
            
            log.info("[命令执行] 命令执行结束");
            log.info("[命令执行] 容器名称: {}", containerId);
            log.info("[命令执行] 执行命令: {}", fullCommand);
            log.info("[命令执行] 退出码: {}", exitCode);
            log.info("[命令执行] 执行耗时: {}ms", elapsed);
            log.info("[命令执行] 输出行数: {}", lineCounter[0]);
            
            if (exitCode != 0) {
                log.error("[命令执行] 命令执行失败，错误类型: {}, 退出码: {}", ERROR_TYPE_EXECUTION, exitCode);
                log.error("[命令执行] 错误输出:\n{}", output.toString());
                return "Command failed with exit code: " + exitCode + "\nOutput:\n" + output;
            }

            log.info("[命令执行] 命令执行成功");
            return output.toString();

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("[命令执行] 命令执行异常");
            log.error("[命令执行] 容器名称: {}", containerId);
            log.error("[命令执行] 执行命令: {}", fullCommand);
            log.error("[命令执行] 执行耗时: {}ms (异常中断)", elapsed);
            log.error("[命令执行] 错误类型: {}, 错误信息: {}", ERROR_TYPE_EXECUTION, e.getMessage(), e);
            return "Failed to execute command: " + e.getMessage();
        }
    }

    /**
     * 运行LAMMPS分子动力学模拟
     * 
     * 在Docker容器中执行LAMMPS模拟任务，使用MPI并行计算
     * 
     * @param inputFilename 输入文件名
     * @param jobId         任务ID，用于组织输入输出路径
     * @return 命令执行结果
     */
    public String runLAMMPS(String inputFilename, String jobId) {
        log.info("[LAMMPS执行] 开始准备LAMMPS模拟任务，任务ID: {}", jobId);
        
        try {
            String containerInputPath = mdInputPath + "/" + jobId + "/" + inputFilename;
            String containerOutputPath = mdOutputPath + "/" + jobId;

            log.info("[LAMMPS执行] 容器输入路径: {}", containerInputPath);
            log.info("[LAMMPS执行] 容器输出路径: {}", containerOutputPath);

            List<String> mkdirCmd = Arrays.asList("mkdir", "-p", containerOutputPath);
            log.debug("[LAMMPS执行] 创建输出目录");
            executeCommandInContainer(mdContainerName, mkdirCmd, "/workspace");

            List<String> lammpsCmd = Arrays.asList(
                    "mpirun", "-np", "4", "lmp",
                    "-in", containerInputPath,
                    "-log", containerOutputPath + "/log.lammps",
                    "-screen", "none"
            );

            log.info("[LAMMPS执行] 执行LAMMPS命令: {}", String.join(" ", lammpsCmd));
            String result = executeCommandInContainer(mdContainerName, lammpsCmd, "/workspace");
            log.info("[LAMMPS执行] LAMMPS任务执行完成，任务ID: {}", jobId);
            return result;

        } catch (Exception e) {
            log.error("[LAMMPS执行] LAMMPS执行失败，任务ID: {}, 错误类型: {}, 错误信息: {}", 
                    jobId, ERROR_TYPE_EXECUTION, e.getMessage(), e);
            return "LAMMPS execution failed: " + e.getMessage();
        }
    }

    /**
     * 运行GROMACS分子动力学模拟
     * 
     * 在Docker容器中执行GROMACS模拟任务，包含能量最小化、NVT平衡、NPT平衡和生产运行
     * 
     * @param inputFilename 输入文件名（当前未使用）
     * @param jobId         任务ID，用于组织输入输出路径
     * @return 命令执行结果
     */
    public String runGROMACS(String inputFilename, String jobId) {
        log.info("[GROMACS执行] 开始准备GROMACS模拟任务，任务ID: {}", jobId);
        
        try {
            String containerInputPath = mdInputPath + "/" + jobId;
            String containerOutputPath = mdOutputPath + "/" + jobId;

            log.info("[GROMACS执行] 容器输入路径: {}", containerInputPath);
            log.info("[GROMACS执行] 容器输出路径: {}", containerOutputPath);

            List<String> mkdirCmd = Arrays.asList("mkdir", "-p", containerOutputPath);
            log.debug("[GROMACS执行] 创建输出目录");
            executeCommandInContainer(mdContainerName, mkdirCmd, "/workspace");

            List<String> copyCmd = Arrays.asList(
                    "cp",
                    mdInputPath + "/" + jobId + "/*",
                    containerInputPath + "/"
            );
            log.debug("[GROMACS执行] 复制输入文件");
            executeCommandInContainer(mdContainerName, copyCmd, "/workspace");

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

            log.info("[GROMACS执行] 执行GROMACS模拟流程（能量最小化 -> NVT -> NPT -> 生产运行）");
            String result = executeCommandInContainer(mdContainerName, gromacsCmd, "/workspace");
            log.info("[GROMACS执行] GROMACS任务执行完成，任务ID: {}", jobId);
            return result;

        } catch (Exception e) {
            log.error("[GROMACS执行] GROMACS执行失败，任务ID: {}, 错误类型: {}, 错误信息: {}", 
                    jobId, ERROR_TYPE_EXECUTION, e.getMessage(), e);
            return "GROMACS execution failed: " + e.getMessage();
        }
    }

    /**
     * 从容器中复制结果文件
     * 
     * 将指定任务的结果文件从Docker容器复制到本地文件系统
     * 
     * @param jobId         任务ID
     * @param sourcePath    容器内的源文件路径（相对于输出目录）
     * @param targetFilename 目标文件名
     * @return true表示复制成功，false表示复制失败
     */
    public boolean copyResultsFromContainer(String jobId, String sourcePath, String targetFilename) {
        log.info("[文件复制] 开始从容器复制结果文件，任务ID: {}", jobId);
        log.info("[文件复制] 源路径: {}", sourcePath);
        log.info("[文件复制] 目标文件名: {}", targetFilename);
        
        try {
            Path targetDir = Paths.get(resultsDir, jobId);
            Files.createDirectories(targetDir);
            log.debug("[文件复制] 创建目标目录: {}", targetDir);

            String containerSourcePath = mdOutputPath + "/" + jobId + "/" + sourcePath;
            log.info("[文件复制] 容器内完整路径: {}:{}", mdContainerName, containerSourcePath);

            ProcessBuilder processBuilder = new ProcessBuilder(
                    "docker", "cp",
                    mdContainerName + ":" + containerSourcePath,
                    targetDir.resolve(targetFilename).toString()
            );

            log.debug("[文件复制] 执行docker cp命令");
            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                log.info("[文件复制] 成功从容器复制结果文件，任务ID: {}, 目标路径: {}", 
                        jobId, targetDir.resolve(targetFilename));
                return true;
            } else {
                log.error("[文件复制] 从容器复制结果文件失败，任务ID: {}, 退出码: {}", jobId, exitCode);
                return false;
            }

        } catch (Exception e) {
            log.error("[文件复制] 从容器复制结果文件异常，任务ID: {}, 错误类型: {}, 错误信息: {}", 
                    jobId, ERROR_TYPE_EXECUTION, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取容器状态
     * 
     * 查询指定MD容器的当前运行状态
     * 
     * @return 容器状态字符串，可能的值包括：
     *         - "running": 容器正在运行
     *         - "exited": 容器已停止
     *         - "NOT_FOUND": 容器不存在
     *         - "DOCKER_NOT_AVAILABLE": Docker服务不可用
     *         - "ERROR: ...": 查询时发生错误
     */
    public String getContainerStatus() {
        log.debug("[容器状态] 正在查询容器 '{}' 的状态", mdContainerName);
        
        if (dockerClient == null) {
            log.warn("[容器状态] Docker客户端不可用");
            return "DOCKER_NOT_AVAILABLE";
        }

        try {
            String status = dockerClient.listContainersCmd().exec().stream()
                    .filter(container -> Arrays.asList(container.getNames()).contains("/" + mdContainerName))
                    .findFirst()
                    .map(container -> container.getState())
                    .orElse("NOT_FOUND");
            
            log.info("[容器状态] 容器 '{}' 状态: {}", mdContainerName, status);
            return status;
        } catch (Exception e) {
            log.error("[容器状态] 查询容器状态失败，错误类型: {}, 错误信息: {}", 
                    ERROR_TYPE_CONNECTION, e.getMessage(), e);
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * 检查GPU是否可用
     * 
     * 通过执行nvidia-smi命令检查容器内是否有可用的NVIDIA GPU
     * 
     * @return true表示GPU可用，false表示GPU不可用或NVIDIA工具未安装
     */
    public boolean isGPUAvailable() {
        log.debug("[GPU检测] 正在检查GPU可用性");
        
        try {
            List<String> nvidiaCmd = Arrays.asList("nvidia-smi", "--query-gpu=name", "--format=csv,noheader");
            String result = executeCommandInContainer(mdContainerName, nvidiaCmd, "/");
            boolean available = result != null && !result.trim().isEmpty() && !result.contains("failed");
            
            log.info("[GPU检测] GPU可用性: {}", available ? "可用" : "不可用");
            if (available && result != null) {
                log.info("[GPU检测] 检测到GPU设备:\n{}", result.trim());
            }
            return available;
        } catch (Exception e) {
            log.warn("[GPU检测] GPU不可用或NVIDIA工具未安装，错误信息: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取容器资源使用情况
     * 
     * 查询容器的CPU和内存使用率
     * 
     * @return 资源使用情况字符串，格式为 "CPU: xx%, Memory: xx%"
     */
    public String getResourceUsage() {
        log.debug("[资源监控] 正在获取容器资源使用情况");
        
        try {
            List<String> cpuCmd = Arrays.asList("bash", "-c", "top -bn1 | grep 'Cpu(s)' | awk '{print $2}' | cut -d'%' -f1");
            List<String> memCmd = Arrays.asList("bash", "-c", "free | grep Mem | awk '{print $3/$2 * 100.0}'");

            String cpuUsage = executeCommandInContainer(mdContainerName, cpuCmd, "/").trim();
            String memUsage = executeCommandInContainer(mdContainerName, memCmd, "/").trim();

            String resourceInfo = String.format("CPU: %s%%, Memory: %s%%", cpuUsage, memUsage);
            log.info("[资源监控] 容器资源使用情况: {}", resourceInfo);
            return resourceInfo;
        } catch (Exception e) {
            log.error("[资源监控] 获取资源使用情况失败，错误信息: {}", e.getMessage(), e);
            return "Unable to get resource usage: " + e.getMessage();
        }
    }

    /**
     * 获取Docker服务诊断信息
     * 
     * 收集Docker服务和MD容器的全面诊断信息，用于故障排查和健康检查
     * 
     * @return 包含诊断信息的Map，包含以下字段：
     *         - dockerAvailable: Boolean - Docker服务是否可用
     *         - containerRunning: Boolean - MD容器是否正在运行
     *         - containerStatus: String - 容器状态描述
     *         - packmolAvailable: Boolean - Packmol工具是否可用（预留）
     *         - errorMessage: String - 错误信息（如果有）
     */
    public Map<String, Object> getDiagnostics() {
        log.info("[诊断信息] 开始收集Docker服务诊断信息");
        
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        StringBuilder errorBuilder = new StringBuilder();
        
        diagnostics.put("timestamp", new Date());
        
        boolean dockerAvailable = isDockerAvailable();
        diagnostics.put("dockerAvailable", dockerAvailable);
        log.debug("[诊断信息] Docker可用性: {}", dockerAvailable);
        
        if (!dockerAvailable) {
            errorBuilder.append("Docker服务不可用; ");
            diagnostics.put("containerRunning", false);
            diagnostics.put("containerStatus", "DOCKER_NOT_AVAILABLE");
            diagnostics.put("packmolAvailable", false);
            diagnostics.put("errorMessage", "Docker服务不可用，请检查Docker是否正确安装并运行");
            log.warn("[诊断信息] Docker服务不可用，诊断完成");
            return diagnostics;
        }
        
        boolean containerRunning = false;
        String containerStatus;
        try {
            containerRunning = isMDContainerRunning();
            containerStatus = getContainerStatus();
            log.debug("[诊断信息] 容器运行状态: {}, 状态描述: {}", containerRunning, containerStatus);
        } catch (Exception e) {
            containerStatus = "ERROR: " + e.getMessage();
            errorBuilder.append("无法获取容器状态: ").append(e.getMessage()).append("; ");
            log.error("[诊断信息] 获取容器状态失败: {}", e.getMessage(), e);
        }
        diagnostics.put("containerRunning", containerRunning);
        diagnostics.put("containerStatus", containerStatus);
        
        if (!containerRunning) {
            errorBuilder.append("MD容器未运行; ");
        }
        
        boolean packmolAvailable = false;
        try {
            log.debug("[诊断信息] 正在检查Packmol工具可用性");
            List<String> packmolCmd = Arrays.asList("which", "packmol");
            String result = executeCommandInContainer(mdContainerName, packmolCmd, "/");
            packmolAvailable = result != null && !result.contains("failed") && !result.trim().isEmpty();
            log.debug("[诊断信息] Packmol可用性: {}", packmolAvailable);
        } catch (Exception e) {
            log.warn("[诊断信息] Packmol检查失败: {}", e.getMessage());
        }
        diagnostics.put("packmolAvailable", packmolAvailable);
        
        if (!packmolAvailable) {
            errorBuilder.append("Packmol工具不可用; ");
        }
        
        try {
            String resourceUsage = getResourceUsage();
            diagnostics.put("resourceUsage", resourceUsage);
            log.debug("[诊断信息] 资源使用情况: {}", resourceUsage);
        } catch (Exception e) {
            log.warn("[诊断信息] 获取资源使用情况失败: {}", e.getMessage());
        }
        
        try {
            boolean gpuAvailable = isGPUAvailable();
            diagnostics.put("gpuAvailable", gpuAvailable);
            log.debug("[诊断信息] GPU可用性: {}", gpuAvailable);
        } catch (Exception e) {
            log.warn("[诊断信息] GPU检查失败: {}", e.getMessage());
            diagnostics.put("gpuAvailable", false);
        }
        
        String errorMessage = errorBuilder.length() > 0 ? errorBuilder.toString() : null;
        diagnostics.put("errorMessage", errorMessage);
        
        log.info("[诊断信息] 诊断信息收集完成");
        log.info("[诊断信息] Docker可用: {}, 容器运行: {}, Packmol可用: {}", 
                dockerAvailable, containerRunning, packmolAvailable);
        
        return diagnostics;
    }
}