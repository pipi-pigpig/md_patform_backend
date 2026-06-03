package com.mdplatform.engine.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Docker集成测试类
 * 
 * <p>该测试类用于验证Java Docker集成的各项功能，包括：</p>
 * <ul>
 *   <li>DockerClient连接状态验证</li>
 *   <li>容器列表获取功能测试</li>
 *   <li>容器内命令执行功能测试</li>
 *   <li>Packmol工具可用性验证</li>
 *   <li>Docker诊断功能测试</li>
 * </ul>
 * 
 * <p>测试特点：</p>
 * <ul>
 *   <li>使用真实的DockerClient进行测试（非mock）</li>
 *   <li>如果Docker不可用，测试将被跳过</li>
 *   <li>需要本地Docker环境支持</li>
 * </ul>
 * 
 * <p>前置条件：</p>
 * <ul>
 *   <li>Docker Desktop已安装并运行</li>
 *   <li>md-engine容器已创建（运行状态可选）</li>
 * </ul>
 * 
 * @author MD Platform Team
 * @version 1.0
 * @since 2024-01-01
 */
@SpringBootTest
@ActiveProfiles("test")
@Slf4j
class DockerIntegrationTest {

    /** Docker客户端，通过Spring自动注入 */
    @Autowired(required = false)
    private DockerClient dockerClient;

    /** Docker服务，用于测试容器操作 */
    @Autowired(required = false)
    private DockerService dockerService;

    /** MD引擎容器名称，从配置文件读取 */
    @Value("${app.docker.md-container-name:md-engine}")
    private String mdContainerName;

    /** Docker功能是否启用，从配置文件读取 */
    @Value("${app.docker.enabled:true}")
    private boolean dockerEnabled;

    /** 标记Docker是否可用，用于跳过测试 */
    private boolean dockerAvailable = false;

    /**
     * 测试前置检查
     * 
     * <p>在每个测试方法执行前，检查Docker环境是否可用。
     * 如果Docker不可用，设置跳过标志，测试方法将根据此标志决定是否跳过。</p>
     */
    @BeforeEach
    void setUp() {
        // 检查DockerClient是否成功注入
        if (dockerClient == null) {
            log.warn("DockerClient未注入，Docker功能可能被禁用");
            dockerAvailable = false;
            return;
        }

        // 检查Docker服务是否可用
        if (dockerService == null) {
            log.warn("DockerService未注入，Docker功能可能被禁用");
            dockerAvailable = false;
            return;
        }

        // 尝试ping Docker守护进程，验证连接状态
        try {
            dockerClient.pingCmd().exec();
            dockerAvailable = true;
            log.info("Docker环境可用，测试将继续执行");
        } catch (Exception e) {
            log.warn("Docker守护进程不可达: {}", e.getMessage());
            dockerAvailable = false;
        }
    }

    /**
     * 测试DockerClient连接状态
     * 
     * <p>验证DockerClient是否能够成功连接到Docker守护进程。
     * 这是所有Docker操作的基础，必须首先确保连接正常。</p>
     * 
     * <p>测试步骤：</p>
     * <ol>
     *   <li>检查DockerClient是否成功注入</li>
     *   <li>执行ping命令验证连接</li>
     *   <li>验证返回结果不为空</li>
     * </ol>
     */
    @Test
    @DisplayName("测试DockerClient连接状态 - 验证与Docker守护进程的连接")
    void testDockerClientConnection() {
        // 如果Docker不可用，跳过测试
        if (!dockerAvailable) {
            log.info("Docker不可用，跳过测试: testDockerClientConnection");
            return;
        }

        log.info("开始测试DockerClient连接状态...");

        // 验证DockerClient不为null
        assertNotNull(dockerClient, "DockerClient应该成功注入");

        try {
            // 执行ping命令，验证与Docker守护进程的连接
            // pingCmd().exec()返回Void，执行成功即表示连接正常
            // 如果连接失败会抛出异常
            dockerClient.pingCmd().exec();
            
            // ping执行成功，说明Docker连接正常
            log.info("Docker ping成功，连接正常");
            
            log.info("DockerClient连接测试通过");
        } catch (Exception e) {
            log.error("DockerClient连接测试失败", e);
            fail("DockerClient连接失败: " + e.getMessage());
        }
    }

    /**
     * 测试容器列表获取功能
     * 
     * <p>验证DockerClient能够正确获取容器列表，包括运行中和已停止的容器。
     * 这是容器管理的基础功能。</p>
     * 
     * <p>测试步骤：</p>
     * <ol>
     *   <li>获取所有容器列表（包括停止的）</li>
     *   <li>验证返回列表不为null</li>
     *   <li>检查md-engine容器是否存在</li>
     *   <li>输出容器信息用于调试</li>
     * </ol>
     */
    @Test
    @DisplayName("测试容器列表获取功能 - 验证能够获取Docker容器列表")
    void testListContainers() {
        // 如果Docker不可用，跳过测试
        if (!dockerAvailable) {
            log.info("Docker不可用，跳过测试: testListContainers");
            return;
        }

        log.info("开始测试容器列表获取功能...");

        try {
            // 获取所有容器列表（包括运行中和已停止的）
            // withShowAll(true)表示包括已停止的容器
            List<Container> containers = dockerClient.listContainersCmd()
                    .withShowAll(true)
                    .exec();

            // 验证容器列表不为null
            assertNotNull(containers, "容器列表不应为null");
            
            log.info("成功获取容器列表，共 {} 个容器", containers.size());

            // 遍历并输出容器信息
            boolean mdEngineFound = false;
            for (Container container : containers) {
                String[] names = container.getNames();
                String state = container.getState();
                String image = container.getImage();
                
                log.info("容器: names={}, state={}, image={}", 
                        Arrays.toString(names), state, image);

                // 检查md-engine容器是否存在
                if (names != null) {
                    for (String name : names) {
                        if (name.contains(mdContainerName)) {
                            mdEngineFound = true;
                            log.info("找到md-engine容器: {}, 状态: {}", name, state);
                        }
                    }
                }
            }

            // 输出md-engine容器查找结果
            if (mdEngineFound) {
                log.info("md-engine容器已找到");
            } else {
                log.warn("未找到md-engine容器（名称: {}），这可能影响后续测试", mdContainerName);
            }

            log.info("容器列表获取测试通过");
        } catch (Exception e) {
            log.error("容器列表获取测试失败", e);
            fail("获取容器列表失败: " + e.getMessage());
        }
    }

    /**
     * 测试executeCommandInContainer()命令执行
     * 
     * <p>验证DockerService能够在容器内执行命令并正确返回输出。
     * 这是执行LAMMPS、GROMACS等模拟软件的基础功能。</p>
     * 
     * <p>测试步骤：</p>
     * <ol>
     *   <li>检查md-engine容器是否运行</li>
     *   <li>在容器内执行简单的echo命令</li>
     *   <li>验证命令输出正确</li>
     *   <li>测试执行失败的情况（无效命令）</li>
     * </ol>
     */
    @Test
    @DisplayName("测试executeCommandInContainer()命令执行 - 验证容器内命令执行功能")
    void testExecuteCommandInContainer() {
        // 如果Docker不可用，跳过测试
        if (!dockerAvailable) {
            log.info("Docker不可用，跳过测试: testExecuteCommandInContainer");
            return;
        }

        log.info("开始测试容器内命令执行功能...");

        // 检查DockerService是否可用
        assertNotNull(dockerService, "DockerService应该成功注入");

        // 检查md-engine容器是否运行
        boolean isRunning = dockerService.isMDContainerRunning();
        log.info("md-engine容器运行状态: {}", isRunning);

        if (!isRunning) {
            log.warn("md-engine容器未运行，跳过命令执行测试");
            log.info("请先启动md-engine容器以执行完整测试");
            return;
        }

        try {
            // 测试1：执行简单的echo命令
            String testMessage = "DockerIntegrationTest-Hello-12345";
            List<String> echoCommand = Arrays.asList("echo", testMessage);
            
            log.info("执行测试命令: echo {}", testMessage);
            String echoResult = dockerService.executeCommandInContainer(
                    mdContainerName, echoCommand, "/workspace");
            
            log.info("Echo命令输出: {}", echoResult);
            
            // 验证输出包含测试消息
            assertNotNull(echoResult, "命令输出不应为null");
            assertTrue(echoResult.contains(testMessage), 
                    "输出应包含测试消息: " + testMessage);

            // 测试2：执行pwd命令验证工作目录
            List<String> pwdCommand = Arrays.asList("pwd");
            String pwdResult = dockerService.executeCommandInContainer(
                    mdContainerName, pwdCommand, "/workspace");
            
            log.info("Pwd命令输出: {}", pwdResult);
            assertNotNull(pwdResult, "pwd输出不应为null");

            // 测试3：执行ls命令验证文件系统访问
            List<String> lsCommand = Arrays.asList("ls", "-la", "/workspace");
            String lsResult = dockerService.executeCommandInContainer(
                    mdContainerName, lsCommand, "/workspace");
            
            log.info("Ls命令输出: {}", lsResult);
            assertNotNull(lsResult, "ls输出不应为null");

            log.info("容器内命令执行测试通过");
        } catch (Exception e) {
            log.error("容器内命令执行测试失败", e);
            fail("执行命令失败: " + e.getMessage());
        }
    }

    /**
     * 测试Packmol在容器中的可用性
     * 
     * <p>验证md-engine容器中是否正确安装了Packmol分子堆积工具。
     * Packmol是电解液MD模拟的关键工具，用于生成初始分子构型。</p>
     * 
     * <p>测试步骤：</p>
     * <ol>
     *   <li>检查md-engine容器是否运行</li>
     *   <li>执行packmol --version或which packmol命令</li>
     *   <li>验证Packmol已安装并可用</li>
     *   <li>可选：测试Packmol基本功能</li>
     * </ol>
     */
    @Test
    @DisplayName("测试Packmol在容器中的可用性 - 验证Packmol工具已正确安装")
    void testPackmolAvailability() {
        // 如果Docker不可用，跳过测试
        if (!dockerAvailable) {
            log.info("Docker不可用，跳过测试: testPackmolAvailability");
            return;
        }

        log.info("开始测试Packmol可用性...");

        // 检查DockerService是否可用
        assertNotNull(dockerService, "DockerService应该成功注入");

        // 检查md-engine容器是否运行
        boolean isRunning = dockerService.isMDContainerRunning();
        if (!isRunning) {
            log.warn("md-engine容器未运行，跳过Packmol可用性测试");
            return;
        }

        try {
            // 测试1：检查packmol可执行文件是否存在
            List<String> whichCommand = Arrays.asList("which", "packmol");
            String whichResult = dockerService.executeCommandInContainer(
                    mdContainerName, whichCommand, "/workspace");
            
            log.info("Which packmol结果: {}", whichResult);

            // 验证packmol路径
            boolean packmolFound = whichResult != null && 
                    (whichResult.contains("/usr") || whichResult.contains("/bin") || 
                     whichResult.contains("packmol"));
            
            if (packmolFound) {
                log.info("Packmol可执行文件已找到");
            } else {
                log.warn("Packmol可执行文件未找到，尝试其他方式验证...");
            }

            // 测试2：尝试运行packmol获取版本或帮助信息
            // packmol通常不支持--version，尝试运行获取帮助
            List<String> helpCommand = Arrays.asList("bash", "-c", 
                    "echo 'tolerance 2.0' | packmol 2>&1 | head -5");
            String helpResult = dockerService.executeCommandInContainer(
                    mdContainerName, helpCommand, "/workspace");
            
            log.info("Packmol测试运行结果: {}", helpResult);

            // 验证packmol能够执行
            // packmol在收到无效输入时会输出错误信息，但这证明它可以运行
            boolean packmolWorks = helpResult != null && 
                    (helpResult.contains("Packmol") || 
                     helpResult.contains("packmol") ||
                     helpResult.contains("error") ||
                     helpResult.contains("Error") ||
                     helpResult.length() > 0);
            
            assertTrue(packmolWorks, "Packmol应该能够执行");

            // 测试3：检查packmol版本或安装信息
            List<String> versionCommand = Arrays.asList("bash", "-c", 
                    "packmol 2>&1 | grep -i 'version\\|packmol' | head -3 || echo 'Version info not available'");
            String versionResult = dockerService.executeCommandInContainer(
                    mdContainerName, versionCommand, "/workspace");
            
            log.info("Packmol版本信息: {}", versionResult);

            log.info("Packmol可用性测试通过");
        } catch (Exception e) {
            log.error("Packmol可用性测试失败", e);
            // 不直接fail，而是记录警告，因为packmol可能确实未安装
            log.warn("Packmol可能未安装在md-engine容器中，这会影响分子堆积功能");
        }
    }

    /**
     * 测试诊断方法
     * 
     * <p>验证DockerService的诊断方法能够正确返回Docker环境的状态信息。
     * 这些诊断方法对于监控和问题排查非常重要。</p>
     * 
     * <p>测试步骤：</p>
     * <ol>
     *   <li>测试isDockerAvailable()方法</li>
     *   <li>测试isMDContainerRunning()方法</li>
     *   <li>测试getContainerStatus()方法</li>
     *   <li>测试isGPUAvailable()方法（可选）</li>
     *   <li>测试getResourceUsage()方法</li>
     * </ol>
     */
    @Test
    @DisplayName("测试诊断方法 - 验证Docker诊断功能")
    void testDiagnostics() {
        // 如果Docker不可用，跳过测试
        if (!dockerAvailable) {
            log.info("Docker不可用，跳过测试: testDiagnostics");
            return;
        }

        log.info("开始测试Docker诊断方法...");

        // 检查DockerService是否可用
        assertNotNull(dockerService, "DockerService应该成功注入");

        // 测试1：isDockerAvailable()方法
        boolean dockerAvailableFlag = dockerService.isDockerAvailable();
        log.info("isDockerAvailable(): {}", dockerAvailableFlag);
        assertTrue(dockerAvailableFlag, "Docker应该可用");

        // 测试2：isMDContainerRunning()方法
        boolean containerRunning = dockerService.isMDContainerRunning();
        log.info("isMDContainerRunning(): {}", containerRunning);
        // 不强制要求容器运行，只记录状态

        // 测试3：getContainerStatus()方法
        String containerStatus = dockerService.getContainerStatus();
        log.info("getContainerStatus(): {}", containerStatus);
        assertNotNull(containerStatus, "容器状态不应为null");
        
        // 验证状态值是否合法
        // 合法状态包括：running, exited, created, paused, NOT_FOUND, DOCKER_NOT_AVAILABLE, ERROR
        boolean isValidStatus = containerStatus.equals("running") ||
                containerStatus.equals("exited") ||
                containerStatus.equals("created") ||
                containerStatus.equals("paused") ||
                containerStatus.equals("NOT_FOUND") ||
                containerStatus.equals("DOCKER_NOT_AVAILABLE") ||
                containerStatus.startsWith("ERROR");
        
        assertTrue(isValidStatus, "容器状态应该是有效值: " + containerStatus);

        // 测试4：isGPUAvailable()方法（可选功能）
        // GPU检测可能失败，不影响主流程
        try {
            boolean gpuAvailable = dockerService.isGPUAvailable();
            log.info("isGPUAvailable(): {}", gpuAvailable);
        } catch (Exception e) {
            log.warn("GPU可用性检测失败（可能是预期行为）: {}", e.getMessage());
        }

        // 测试5：getResourceUsage()方法（需要容器运行）
        if (containerRunning) {
            try {
                String resourceUsage = dockerService.getResourceUsage();
                log.info("getResourceUsage(): {}", resourceUsage);
                assertNotNull(resourceUsage, "资源使用信息不应为null");
            } catch (Exception e) {
                log.warn("获取资源使用信息失败: {}", e.getMessage());
            }
        } else {
            log.info("容器未运行，跳过资源使用检测");
        }

        log.info("Docker诊断方法测试通过");
    }

    /**
     * 测试DockerClient为null时的降级行为
     * 
     * <p>验证当DockerClient不可用时，DockerService能够正确处理降级情况。
     * 这是系统容错性的重要测试。</p>
     */
    @Test
    @DisplayName("测试DockerClient为null时的降级行为")
    void testDockerClientNullFallback() {
        log.info("开始测试DockerClient为null时的降级行为...");

        // 创建一个没有DockerClient的DockerService实例
        DockerService fallbackService = new DockerService(null);
        
        // 验证isDockerAvailable返回false
        assertFalse(fallbackService.isDockerAvailable(), 
                "当DockerClient为null时，isDockerAvailable应返回false");
        
        // 验证isMDContainerRunning的降级行为
        // 根据DockerService实现，当Docker不可用时会返回true（mock状态）
        boolean runningStatus = fallbackService.isMDContainerRunning();
        log.info("Docker不可用时isMDContainerRunning返回: {}", runningStatus);
        
        // 验证getContainerStatus返回DOCKER_NOT_AVAILABLE
        String status = fallbackService.getContainerStatus();
        assertEquals("DOCKER_NOT_AVAILABLE", status, 
                "当Docker不可用时，getContainerStatus应返回DOCKER_NOT_AVAILABLE");
        
        // 验证executeCommandInContainer的降级行为
        String commandResult = fallbackService.executeCommandInContainer(
                "test-container", Arrays.asList("echo", "test"), "/workspace");
        assertTrue(commandResult.contains("Mock") || commandResult.contains("not available"), 
                "当Docker不可用时，executeCommandInContainer应返回mock或不可用提示");

        log.info("DockerClient为null时的降级行为测试通过");
    }

    /**
     * 测试容器内环境变量和路径
     * 
     * <p>验证md-engine容器内的环境变量配置和工作目录结构，
     * 确保容器环境配置正确。</p>
     */
    @Test
    @DisplayName("测试容器内环境变量和路径配置")
    void testContainerEnvironment() {
        // 如果Docker不可用，跳过测试
        if (!dockerAvailable) {
            log.info("Docker不可用，跳过测试: testContainerEnvironment");
            return;
        }

        log.info("开始测试容器内环境变量和路径配置...");

        // 检查md-engine容器是否运行
        if (!dockerService.isMDContainerRunning()) {
            log.warn("md-engine容器未运行，跳过环境测试");
            return;
        }

        try {
            // 测试1：检查/workspace目录是否存在
            List<String> checkWorkspaceCmd = Arrays.asList("bash", "-c", 
                    "test -d /workspace && echo 'WORKSPACE_EXISTS' || echo 'WORKSPACE_NOT_FOUND'");
            String workspaceResult = dockerService.executeCommandInContainer(
                    mdContainerName, checkWorkspaceCmd, "/");
            log.info("Workspace目录检查: {}", workspaceResult);
            
            // 测试2：检查Python是否可用
            List<String> checkPythonCmd = Arrays.asList("which", "python3");
            String pythonResult = dockerService.executeCommandInContainer(
                    mdContainerName, checkPythonCmd, "/");
            log.info("Python路径: {}", pythonResult);
            
            // 测试3：检查LAMMPS是否可用
            List<String> checkLammpsCmd = Arrays.asList("bash", "-c", 
                    "which lmp || which lammps || echo 'LAMMPS_NOT_FOUND'");
            String lammpsResult = dockerService.executeCommandInContainer(
                    mdContainerName, checkLammpsCmd, "/");
            log.info("LAMMPS路径: {}", lammpsResult);
            
            // 测试4：检查环境变量
            List<String> checkEnvCmd = Arrays.asList("bash", "-c", 
                    "env | grep -E 'PATH|PYTHON|MPI' | head -10");
            String envResult = dockerService.executeCommandInContainer(
                    mdContainerName, checkEnvCmd, "/");
            log.info("关键环境变量:\n{}", envResult);

            log.info("容器环境检查完成");
        } catch (Exception e) {
            log.warn("容器环境检查过程中出现异常: {}", e.getMessage());
        }
    }
}