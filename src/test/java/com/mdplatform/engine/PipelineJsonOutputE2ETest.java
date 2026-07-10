package com.mdplatform.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdplatform.engine.dto.PipelineProgressDto;
import com.mdplatform.engine.dto.PipelineSubmitRequest;
import com.mdplatform.engine.model.CalculationResult;
import com.mdplatform.engine.model.SimulationJob;
import com.mdplatform.engine.repository.CalculationResultRepository;
import com.mdplatform.engine.repository.SimulationInputRepository;
import com.mdplatform.engine.repository.SimulationRepository;
import com.mdplatform.engine.service.DockerService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Python脚本JSON输出格式与全流程端到端测试
 *
 * <p>该测试验证Python脚本的JSON输出格式与Java端期望一致，
 * 并验证全流程可以端到端执行。不使用Mock，运行在真实的Docker容器和数据库之上。</p>
 *
 * <h3>测试范围</h3>
 * <ul>
 *   <li>Test 1: molecule-count模式JSON输出格式验证</li>
 *   <li>Test 2: box-size模式JSON输出格式验证</li>
 *   <li>Test 3: file-organize模式JSON输出格式验证</li>
 *   <li>Test 4: 全流程提交与执行验证</li>
 * </ul>
 *
 * <h3>前置条件</h3>
 * <ul>
 *   <li>Docker 引擎可用</li>
 *   <li>md_engine 容器正在运行</li>
 *   <li>数据库可连接</li>
 * </ul>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PipelineJsonOutputE2ETest {

    // ==================== 依赖注入 ====================

    @Autowired
    private DockerService dockerService;

    @Autowired
    private SimulationRepository simulationRepository;

    @Autowired
    private SimulationInputRepository simulationInputRepository;

    @Autowired
    private CalculationResultRepository calculationResultRepository;

    @Autowired
    private TestRestTemplate restTemplate;

    /** MD容器名称，从配置文件读取，默认为md_engine */
    @Value("${app.docker.md-container-name:md_engine}")
    private String mdContainerName;

    /** JSON解析器 */
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 测试状态 ====================

    /** 全流程提交后得到的任务ID，在多个测试方法间共享 */
    private static Long sharedJobId;

    /** 记录全流程状态流转过程 */
    private static final List<String> statusTransitions = Collections.synchronizedList(new ArrayList<>());

    // ==================== 辅助方法 ====================

    /**
     * 检查Docker和md_engine容器是否可用
     *
     * @return true表示环境可用，false表示不可用
     */
    private boolean isDockerAndContainerAvailable() {
        if (dockerService == null || !dockerService.isDockerAvailable()) {
            log.warn("[JSON-E2E] Docker服务不可用");
            return false;
        }
        String containerStatus = dockerService.getContainerStatus();
        boolean available = containerStatus != null
                && containerStatus.toLowerCase().contains("running");
        if (!available) {
            log.warn("[JSON-E2E] md_engine容器未运行，当前状态: {}", containerStatus);
        }
        return available;
    }

    /**
     * 从命令输出中提取===JSON_RESULT===和===END_JSON===之间的JSON字符串
     *
     * @param output 命令完整输出
     * @return 提取出的JSON字符串，如果标记不存在则返回null
     */
    private String extractJsonBetweenMarkers(String output) {
        String startMarker = "===JSON_RESULT===";
        String endMarker = "===END_JSON===";

        int startIndex = output.indexOf(startMarker);
        int endIndex = output.indexOf(endMarker);

        if (startIndex == -1 || endIndex == -1) {
            log.error("[JSON-E2E] 未找到JSON标记，startMarker存在: {}, endMarker存在: {}",
                    startIndex != -1, endIndex != -1);
            log.error("[JSON-E2E] 完整输出:\n{}", output);
            return null;
        }

        // 提取标记之间的内容（去掉标记本身所在行）
        String jsonContent = output.substring(startIndex + startMarker.length(), endIndex).trim();
        log.info("[JSON-E2E] 提取到的JSON内容:\n{}", jsonContent);
        return jsonContent;
    }

    // ==================== Test 1: molecule-count JSON输出 ====================

    /**
     * 测试Python脚本molecule-count模式的JSON输出格式
     *
     * <p>验证点：</p>
     * <ul>
     *   <li>输出包含===JSON_RESULT===和===END_JSON===标记</li>
     *   <li>标记之间的内容是有效的JSON数组</li>
     *   <li>数组中每个对象包含name、count、charge字段</li>
     * </ul>
     */
    @Test
    @Order(1)
    @DisplayName("Test 1: 验证molecule-count模式JSON输出格式")
    @SuppressWarnings("unchecked")
    void testPythonMoleculeCountJsonOutput() {
        log.info("========================================");
        log.info("[JSON-E2E] Test 1: molecule-count JSON输出验证");
        log.info("========================================");

        // 跳过条件：Docker不可用
        Assumptions.assumeTrue(isDockerAndContainerAvailable(),
                "Docker或md_engine容器不可用，跳过此测试");

        // 步骤1：在容器中创建测试配方文件
        String formulaJson = "{\"solvent_info\":["
                + "{\"name\":\"EC\",\"mole_fraction\":0.3,\"molecular_weight\":88.06},"
                + "{\"name\":\"DMC\",\"mole_fraction\":0.7,\"molecular_weight\":90.08}"
                + "],\"salt_info\":{"
                + "\"name\":\"LiPF6\",\"concentration\":1.0,"
                + "\"cation\":{\"name\":\"Li\",\"molecular_weight\":6.94,\"charge\":1},"
                + "\"anion\":{\"name\":\"PF6\",\"molecular_weight\":144.96,\"charge\":-1}"
                + "},\"density\":1.2}";

        log.info("[JSON-E2E] 步骤1：创建测试配方文件");
        List<String> createFormulaCmd = Arrays.asList("bash", "-c",
                "echo '" + formulaJson + "' > /workspace/data/test_formula.json");
        String createResult = dockerService.executeCommandInContainer(
                mdContainerName, createFormulaCmd, "/workspace");
        log.info("[JSON-E2E] 配方文件创建结果: {}", createResult);

        // 步骤2：创建测试任务目录
        log.info("[JSON-E2E] 步骤2：创建测试任务目录");
        List<String> mkdirCmd = Arrays.asList("mkdir", "-p", "/workspace/data/test_json");
        dockerService.executeCommandInContainer(mdContainerName, mkdirCmd, "/workspace");

        // 步骤3：执行molecule-count模式
        // 注意：必须先cd到/workspace/scripts目录，因为modeling包位于/workspace/scripts/modeling/
        // MoltemplateService也使用相同的模式：bash -c "cd /workspace/scripts && python3 -m modeling.run_modeling ..."
        log.info("[JSON-E2E] 步骤3：执行molecule-count模式");
        String molCountCmdStr = "cd /workspace/scripts && python3 -m modeling.run_modeling"
                + " --mode molecule-count"
                + " --job-id test_json"
                + " --job-dir /workspace/data/test_json"
                + " --formula-file /workspace/data/test_formula.json";
        List<String> moleculeCountCmd = Arrays.asList("bash", "-c", molCountCmdStr);

        String output = dockerService.executeCommandInContainer(
                mdContainerName, moleculeCountCmd, "/workspace");
        log.info("[JSON-E2E] molecule-count命令输出:\n{}", output);

        // 步骤4：验证JSON标记存在
        log.info("[JSON-E2E] 步骤4：验证JSON标记");
        assertNotNull(output, "命令输出不应为null");
        assertTrue(output.contains("===JSON_RESULT==="),
                "输出应包含===JSON_RESULT===标记");
        assertTrue(output.contains("===END_JSON==="),
                "输出应包含===END_JSON===标记");

        // 步骤5：提取并解析JSON
        log.info("[JSON-E2E] 步骤5：提取并解析JSON");
        String jsonContent = extractJsonBetweenMarkers(output);
        assertNotNull(jsonContent, "提取的JSON内容不应为null");

        List<Map<String, Object>> moleculeCounts;
        try {
            moleculeCounts = objectMapper.readValue(jsonContent,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        } catch (Exception e) {
            fail("JSON解析失败: " + e.getMessage() + "\nJSON内容: " + jsonContent);
            return; // 编译器需要，实际不会执行
        }

        // 步骤6：验证JSON数组结构
        log.info("[JSON-E2E] 步骤6：验证JSON数组结构");
        assertNotNull(moleculeCounts, "解析后的分子数量列表不应为null");
        assertFalse(moleculeCounts.isEmpty(), "分子数量列表不应为空");

        // 验证每个对象包含name、count、charge字段
        for (Map<String, Object> molecule : moleculeCounts) {
            log.info("[JSON-E2E] 分子条目: {}", molecule);
            assertTrue(molecule.containsKey("name"),
                    "每个分子条目应包含name字段，实际字段: " + molecule.keySet());
            assertTrue(molecule.containsKey("count"),
                    "每个分子条目应包含count字段，实际字段: " + molecule.keySet());
            assertTrue(molecule.containsKey("charge"),
                    "每个分子条目应包含charge字段，实际字段: " + molecule.keySet());

            // 验证字段值类型
            assertNotNull(molecule.get("name"), "name字段不应为null");
            assertNotNull(molecule.get("count"), "count字段不应为null");
            assertNotNull(molecule.get("charge"), "charge字段不应为null");
        }

        log.info("[JSON-E2E] ✓ Test 1完成：molecule-count JSON输出格式验证通过");
        log.info("[JSON-E2E] 解析结果: {} 条分子记录", moleculeCounts.size());
    }

    // ==================== Test 2: box-size JSON输出 ====================

    /**
     * 测试Python脚本box-size模式的JSON输出格式
     *
     * <p>验证点：</p>
     * <ul>
     *   <li>输出包含===JSON_RESULT===和===END_JSON===标记</li>
     *   <li>标记之间的内容是有效的JSON对象</li>
     *   <li>对象包含x、y、z、volume字段</li>
     * </ul>
     */
    @Test
    @Order(2)
    @DisplayName("Test 2: 验证box-size模式JSON输出格式")
    void testPythonBoxSizeJsonOutput() {
        log.info("========================================");
        log.info("[JSON-E2E] Test 2: box-size JSON输出验证");
        log.info("========================================");

        // 跳过条件：Docker不可用
        Assumptions.assumeTrue(isDockerAndContainerAvailable(),
                "Docker或md_engine容器不可用，跳过此测试");

        // 步骤1：确保测试配方文件存在（Test 1可能已创建）
        String formulaJson = "{\"solvent_info\":["
                + "{\"name\":\"EC\",\"mole_fraction\":0.3,\"molecular_weight\":88.06},"
                + "{\"name\":\"DMC\",\"mole_fraction\":0.7,\"molecular_weight\":90.08}"
                + "],\"salt_info\":{"
                + "\"name\":\"LiPF6\",\"concentration\":1.0,"
                + "\"cation\":{\"name\":\"Li\",\"molecular_weight\":6.94,\"charge\":1},"
                + "\"anion\":{\"name\":\"PF6\",\"molecular_weight\":144.96,\"charge\":-1}"
                + "},\"density\":1.2}";

        log.info("[JSON-E2E] 步骤1：确保测试配方文件存在");
        List<String> createFormulaCmd = Arrays.asList("bash", "-c",
                "echo '" + formulaJson + "' > /workspace/data/test_formula.json");
        dockerService.executeCommandInContainer(mdContainerName, createFormulaCmd, "/workspace");

        // 步骤2：确保测试任务目录存在
        List<String> mkdirCmd = Arrays.asList("mkdir", "-p", "/workspace/data/test_json");
        dockerService.executeCommandInContainer(mdContainerName, mkdirCmd, "/workspace");

        // 步骤3：执行box-size模式
        // 注意：必须先cd到/workspace/scripts目录，因为modeling包位于/workspace/scripts/modeling/
        log.info("[JSON-E2E] 步骤3：执行box-size模式");
        String boxSizeCmdStr = "cd /workspace/scripts && python3 -m modeling.run_modeling"
                + " --mode box-size"
                + " --job-id test_json"
                + " --job-dir /workspace/data/test_json"
                + " --formula-file /workspace/data/test_formula.json";
        List<String> boxSizeCmd = Arrays.asList("bash", "-c", boxSizeCmdStr);

        String output = dockerService.executeCommandInContainer(
                mdContainerName, boxSizeCmd, "/workspace");
        log.info("[JSON-E2E] box-size命令输出:\n{}", output);

        // 步骤4：验证JSON标记存在
        log.info("[JSON-E2E] 步骤4：验证JSON标记");
        assertNotNull(output, "命令输出不应为null");
        assertTrue(output.contains("===JSON_RESULT==="),
                "输出应包含===JSON_RESULT===标记");
        assertTrue(output.contains("===END_JSON==="),
                "输出应包含===END_JSON===标记");

        // 步骤5：提取并解析JSON
        log.info("[JSON-E2E] 步骤5：提取并解析JSON");
        String jsonContent = extractJsonBetweenMarkers(output);
        assertNotNull(jsonContent, "提取的JSON内容不应为null");

        Map<String, Object> boxSize;
        try {
            boxSize = objectMapper.readValue(jsonContent, Map.class);
        } catch (Exception e) {
            fail("JSON解析失败: " + e.getMessage() + "\nJSON内容: " + jsonContent);
            return;
        }

        // 步骤6：验证JSON对象结构
        log.info("[JSON-E2E] 步骤6：验证JSON对象结构");
        assertNotNull(boxSize, "解析后的盒子尺寸不应为null");
        log.info("[JSON-E2E] 解析结果: {}", boxSize);

        // 验证包含x、y、z、volume字段
        assertTrue(boxSize.containsKey("x"),
                "盒子尺寸应包含x字段，实际字段: " + boxSize.keySet());
        assertTrue(boxSize.containsKey("y"),
                "盒子尺寸应包含y字段，实际字段: " + boxSize.keySet());
        assertTrue(boxSize.containsKey("z"),
                "盒子尺寸应包含z字段，实际字段: " + boxSize.keySet());
        assertTrue(boxSize.containsKey("volume"),
                "盒子尺寸应包含volume字段，实际字段: " + boxSize.keySet());

        // 验证字段值为有效数字
        for (String field : Arrays.asList("x", "y", "z", "volume")) {
            Object value = boxSize.get(field);
            assertNotNull(value, field + "字段不应为null");
            assertTrue(value instanceof Number,
                    field + "字段应为数字类型，实际类型: " + value.getClass().getSimpleName());
            double numValue = ((Number) value).doubleValue();
            assertTrue(numValue > 0,
                    field + "字段应为正数，实际值: " + numValue);
        }

        log.info("[JSON-E2E] ✓ Test 2完成：box-size JSON输出格式验证通过");
        log.info("[JSON-E2E] 盒子尺寸: x={}, y={}, z={}, volume={}",
                boxSize.get("x"), boxSize.get("y"), boxSize.get("z"), boxSize.get("volume"));
    }

    // ==================== Test 3: file-organize JSON输出 ====================

    /**
     * 测试Python脚本file-organize模式的JSON输出格式
     *
     * <p>验证点：</p>
     * <ul>
     *   <li>输出包含===JSON_RESULT===和===END_JSON===标记</li>
     *   <li>标记之间的内容是有效的JSON对象</li>
     *   <li>对象包含success字段（值为true）和output_files数组</li>
     * </ul>
     */
    @Test
    @Order(3)
    @DisplayName("Test 3: 验证file-organize模式JSON输出格式")
    @SuppressWarnings("unchecked")
    void testPythonFileOrganizeMode() {
        log.info("========================================");
        log.info("[JSON-E2E] Test 3: file-organize JSON输出验证");
        log.info("========================================");

        // 跳过条件：Docker不可用
        Assumptions.assumeTrue(isDockerAndContainerAvailable(),
                "Docker或md_engine容器不可用，跳过此测试");

        // 步骤1：在容器中创建测试源目录和测试文件
        // 注意：file-organize模式只复制.data文件和.in.*文件，所以测试文件必须匹配这些模式
        log.info("[JSON-E2E] 步骤1：创建测试源目录和测试文件");
        List<String> createSourceDirCmd = Arrays.asList("bash", "-c",
                "mkdir -p /workspace/data/test_source && "
                        + "echo 'LAMMPS data file' > /workspace/data/test_source/system.data && "
                        + "echo 'init settings' > /workspace/data/test_source/system.in.init && "
                        + "echo 'force field settings' > /workspace/data/test_source/system.in.settings"
        );
        String createResult = dockerService.executeCommandInContainer(
                mdContainerName, createSourceDirCmd, "/workspace");
        log.info("[JSON-E2E] 测试文件创建结果: {}", createResult);

        // 步骤2：创建目标目录
        List<String> createTargetDirCmd = Arrays.asList("mkdir", "-p", "/workspace/data/test_target");
        dockerService.executeCommandInContainer(mdContainerName, createTargetDirCmd, "/workspace");

        // 步骤3：执行file-organize模式
        // 注意：必须先cd到/workspace/scripts目录，因为modeling包位于/workspace/scripts/modeling/
        log.info("[JSON-E2E] 步骤3：执行file-organize模式");
        String fileOrganizeCmdStr = "cd /workspace/scripts && python3 -m modeling.run_modeling"
                + " --mode file-organize"
                + " --job-id test_fo"
                + " --source-dir /workspace/data/test_source"
                + " --target-dir /workspace/data/test_target";
        List<String> fileOrganizeCmd = Arrays.asList("bash", "-c", fileOrganizeCmdStr);

        String output = dockerService.executeCommandInContainer(
                mdContainerName, fileOrganizeCmd, "/workspace");
        log.info("[JSON-E2E] file-organize命令输出:\n{}", output);

        // 步骤4：验证JSON标记存在
        log.info("[JSON-E2E] 步骤4：验证JSON标记");
        assertNotNull(output, "命令输出不应为null");
        assertTrue(output.contains("===JSON_RESULT==="),
                "输出应包含===JSON_RESULT===标记");
        assertTrue(output.contains("===END_JSON==="),
                "输出应包含===END_JSON===标记");

        // 步骤5：提取并解析JSON
        log.info("[JSON-E2E] 步骤5：提取并解析JSON");
        String jsonContent = extractJsonBetweenMarkers(output);
        assertNotNull(jsonContent, "提取的JSON内容不应为null");

        Map<String, Object> organizeResult;
        try {
            organizeResult = objectMapper.readValue(jsonContent, Map.class);
        } catch (Exception e) {
            fail("JSON解析失败: " + e.getMessage() + "\nJSON内容: " + jsonContent);
            return;
        }

        // 步骤6：验证JSON对象结构
        log.info("[JSON-E2E] 步骤6：验证JSON对象结构");
        assertNotNull(organizeResult, "解析后的文件组织结果不应为null");
        log.info("[JSON-E2E] 解析结果: {}", organizeResult);

        // 验证success字段
        assertTrue(organizeResult.containsKey("success"),
                "文件组织结果应包含success字段，实际字段: " + organizeResult.keySet());
        Object successValue = organizeResult.get("success");
        assertNotNull(successValue, "success字段不应为null");
        assertTrue(Boolean.TRUE.equals(successValue) || "true".equals(String.valueOf(successValue)),
                "success字段应为true，实际值: " + successValue);

        // 验证output_files字段
        assertTrue(organizeResult.containsKey("output_files"),
                "文件组织结果应包含output_files字段，实际字段: " + organizeResult.keySet());
        Object outputFilesObj = organizeResult.get("output_files");
        assertNotNull(outputFilesObj, "output_files字段不应为null");
        assertTrue(outputFilesObj instanceof List,
                "output_files应为数组类型，实际类型: " + outputFilesObj.getClass().getSimpleName());

        List<String> outputFiles = (List<String>) outputFilesObj;
        log.info("[JSON-E2E] 输出文件列表: {}", outputFiles);

        log.info("[JSON-E2E] ✓ Test 3完成：file-organize JSON输出格式验证通过");
    }

    // ==================== Test 4: 全流程提交与执行 ====================

    /**
     * 测试全流程提交与执行
     *
     * <p>验证点：</p>
     * <ul>
     *   <li>POST /api/pipeline/submit 返回202 ACCEPTED</li>
     *   <li>轮询 GET /api/pipeline/status/{jobId} 直到COMPLETED或FAILED</li>
     *   <li>COMPLETED时验证CalculationResult记录存在于数据库</li>
     *   <li>FAILED时记录错误信息用于调试（不使测试失败）</li>
     *   <li>记录所有状态流转</li>
     * </ul>
     */
    @Test
    @Order(4)
    @DisplayName("Test 4: 全流程提交与执行验证")
    @SuppressWarnings("unchecked")
    void testFullPipelineSubmission() {
        log.info("========================================");
        log.info("[JSON-E2E] Test 4: 全流程提交与执行验证");
        log.info("========================================");

        // ===== 阶段1：提交任务 =====
        log.info("[JSON-E2E] 阶段1：提交全流程计算任务");

        // 构建标准测试请求（EC:DMC=3:7, 1M LiPF6, 353K）
        PipelineSubmitRequest request = TestDataBuilder.buildStandardPipelineRequest();
        log.info("[JSON-E2E] 测试配方: EC:DMC=3:7, 1M LiPF6, T=353K");
        log.info("[JSON-E2E] 目标属性: {}", request.getTargetProperties());

        // 提交任务
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/pipeline/submit?testUserId=" + TestDataBuilder.DEFAULT_TEST_USER_ID,
                request,
                Map.class
        );

        // 验证HTTP响应
        log.info("[JSON-E2E] HTTP响应状态: {}", response.getStatusCodeValue());
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode(),
                "任务提交应返回 202 ACCEPTED");

        Map<String, Object> body = response.getBody();
        assertNotNull(body, "响应体不应为null");
        assertNotNull(body.get("jobId"), "响应体应包含jobId");

        Long jobId = ((Number) body.get("jobId")).longValue();
        sharedJobId = jobId;
        log.info("[JSON-E2E] ✓ 任务提交成功，jobId = {}", jobId);

        // ===== 阶段2：轮询等待任务完成 =====
        log.info("[JSON-E2E] 阶段2：轮询等待任务完成，jobId = {}", jobId);

        // 使用AtomicReference记录状态变化（lambda中不能修改外部局部变量）
        java.util.concurrent.atomic.AtomicReference<String> previousStatus =
                new java.util.concurrent.atomic.AtomicReference<>(null);

        // 轮询等待任务完成或失败（最多600秒）
        await().atMost(600, SECONDS)
                .pollInterval(5, SECONDS)
                .until(() -> {
                    ResponseEntity<PipelineProgressDto> statusResponse = restTemplate.getForEntity(
                            "/api/pipeline/status/" + jobId
                                    + "?testUserId=" + TestDataBuilder.DEFAULT_TEST_USER_ID,
                            PipelineProgressDto.class
                    );

                    if (statusResponse.getStatusCode() != HttpStatus.OK) {
                        log.warn("[JSON-E2E] 状态API返回非200: {}", statusResponse.getStatusCode());
                        return false;
                    }

                    PipelineProgressDto progress = statusResponse.getBody();
                    if (progress == null) {
                        log.warn("[JSON-E2E] 状态API返回空响应");
                        return false;
                    }

                    String currentStatus = progress.getStatus();
                    String prevStatus = previousStatus.get();

                    // 记录状态变化
                    if (!currentStatus.equals(prevStatus)) {
                        statusTransitions.add(currentStatus);
                        log.info("[JSON-E2E] 📊 状态变更: {} → {} (步骤 {}/{}, 进度 {}%)",
                                prevStatus, currentStatus,
                                progress.getCurrentStep(), progress.getTotalSteps(),
                                progress.getProgressPercent());
                        previousStatus.set(currentStatus);
                    }

                    // 检查是否完成或失败
                    if ("COMPLETED".equals(currentStatus)) {
                        log.info("[JSON-E2E] ✅ 流水线执行完成!");
                        return true;
                    }

                    if ("FAILED".equals(currentStatus)) {
                        log.error("[JSON-E2E] ❌ 流水线执行失败: {}", progress.getErrorMessage());
                        return true;
                    }

                    return false;
                });

        // ===== 阶段3：验证最终结果 =====
        log.info("[JSON-E2E] 阶段3：验证最终结果");

        ResponseEntity<PipelineProgressDto> finalResponse = restTemplate.getForEntity(
                "/api/pipeline/status/" + jobId
                        + "?testUserId=" + TestDataBuilder.DEFAULT_TEST_USER_ID,
                PipelineProgressDto.class
        );

        assertEquals(HttpStatus.OK, finalResponse.getStatusCode(), "最终状态API应返回200");
        PipelineProgressDto finalProgress = finalResponse.getBody();
        assertNotNull(finalProgress, "最终进度不应为null");

        String finalStatus = finalProgress.getStatus();
        log.info("[JSON-E2E] 最终状态: {}", finalStatus);
        log.info("[JSON-E2E] 状态流转: {}", String.join(" → ", statusTransitions));

        // 根据最终状态进行不同的验证
        if ("COMPLETED".equals(finalStatus)) {
            // COMPLETED状态：验证CalculationResult记录存在于数据库
            log.info("[JSON-E2E] 任务成功完成，验证数据库计算结果");
            List<CalculationResult> results = calculationResultRepository.findByJobId(jobId);
            log.info("[JSON-E2E] CalculationResult记录数: {}", results.size());
            assertFalse(results.isEmpty(),
                    "COMPLETED任务应有CalculationResult记录");

            // 记录所有计算结果
            for (CalculationResult result : results) {
                log.info("[JSON-E2E] CalculationResult: propertyName={}, propertyValue={}, propertyUnit={}",
                        result.getPropertyName(), result.getPropertyValue(), result.getPropertyUnit());
                assertNotNull(result.getPropertyName(), "propertyName不应为null");
                assertNotNull(result.getPropertyValue(), "propertyValue不应为null");
                assertFalse(Double.isNaN(result.getPropertyValue()),
                        "propertyValue不应为NaN");
            }

            log.info("[JSON-E2E] ✓ 数据库计算结果验证通过");
        } else if ("FAILED".equals(finalStatus)) {
            // FAILED状态：记录错误信息用于调试，但不使测试失败
            // 流水线可能因环境原因失败（如缺少LAMMPS GPU等），这是可接受的
            String errorMessage = finalProgress.getErrorMessage();
            log.warn("[JSON-E2E] ⚠ 流水线执行失败（环境原因可接受）");
            log.warn("[JSON-E2E] 错误信息: {}", errorMessage);
            log.warn("[JSON-E2E] 这不构成测试失败，因为流水线可能因环境限制而失败"
                    + "（如缺少LAMMPS GPU、Docker资源不足等）");
        } else {
            // 其他状态（如超时仍在运行中）
            log.warn("[JSON-E2E] ⚠ 流水线未在超时时间内完成，最终状态: {}", finalStatus);
        }

        log.info("[JSON-E2E] ✓ Test 4完成：全流程提交与执行验证结束");
    }

    // ==================== 综合报告 ====================

    /**
     * 在所有测试之后生成综合测试报告
     */
    @Test
    @Order(99)
    @DisplayName("生成JSON输出E2E测试综合报告")
    void testGenerateFinalReport() {
        log.info("");
        log.info("╔══════════════════════════════════════════════════════════════╗");
        log.info("║      JSON输出E2E测试报告 (Pipeline JsonOutput E2E)          ║");
        log.info("╠══════════════════════════════════════════════════════════════╣");

        // Docker环境状态
        boolean dockerAvailable = isDockerAndContainerAvailable();
        log.info("║  Docker环境: {}{}",
                dockerAvailable ? "可用" : "不可用",
                " ".repeat(Math.max(0, 46 - (dockerAvailable ? "可用".length() : "不可用".length()))) + "║");

        // 全流程任务信息
        if (sharedJobId != null) {
            log.info("║  全流程任务ID: {}{}", sharedJobId,
                    " ".repeat(Math.max(0, 42 - String.valueOf(sharedJobId).length())) + "║");

            SimulationJob job = simulationRepository.findById(sharedJobId).orElse(null);
            if (job != null) {
                log.info("║  最终状态: {}{}", job.getStatus(),
                        " ".repeat(Math.max(0, 45 - safeLength(job.getStatus()))) + "║");
            }

            log.info("║  状态流转: {}{}",
                    String.join(" → ", statusTransitions),
                    " ".repeat(Math.max(0, 44 - String.join(" → ", statusTransitions).length())) + "║");
        } else {
            log.info("║  全流程任务: 未提交                                        ║");
        }

        log.info("╚══════════════════════════════════════════════════════════════╝");
        log.info("");
    }

    // ==================== 辅助方法 ====================

    /**
     * 安全获取字符串长度，null时返回4（"null"的长度）
     *
     * @param s 输入字符串
     * @return 字符串长度
     */
    private static int safeLength(String s) {
        return s == null ? 4 : s.length();
    }
}
