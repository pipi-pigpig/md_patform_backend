package com.mdplatform.engine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdplatform.common.util.PathUtil;
import com.mdplatform.engine.dto.*;
import com.mdplatform.engine.model.JobStatus;
import com.mdplatform.engine.model.SimulationInput;
import com.mdplatform.engine.model.SimulationJob;
import com.mdplatform.engine.repository.SimulationInputRepository;
import com.mdplatform.engine.repository.SimulationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * 后处理服务类
 *
 * <p>该服务负责执行分子动力学模拟的后处理计算，包括密度、电导率、粘度、
 * 介电常数和溶剂化结构等物性的计算与结果存储。</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>通过Docker容器执行Python后处理脚本</li>
 *   <li>解析后处理生成的JSON结果文件</li>
 *   <li>将解析结果存储到数据库主表和子表</li>
 *   <li>管理后处理任务的异步执行与状态更新</li>
 * </ul>
 *
 * <p>执行方式：</p>
 * <p>本服务通过DockerService在md-engine容器中执行Python后处理脚本，
 * 脚本路径为 /workspace/scripts/modeling/run_modeling.py。</p>
 *
 * <p>文件路径规范：</p>
 * <pre>
 * user_{userId}/jobs/job_{jobId}/
 * ├── post_processing/                    # 后处理结果目录
 * │   ├── density_result.json             # 密度计算结果
 * │   ├── conductivity_result.json        # 电导率计算结果
 * │   ├── viscosity_result.json           # 粘度计算结果
 * │   ├── dielectric_constant_result.json # 介电常数计算结果
 * │   └── solvation_structure_result.json # 溶剂化结构计算结果
 * └── visualization/charts/               # 可视化图表数据目录
 * </pre>
 *
 * <p>路径映射：</p>
 * <pre>
 * 本地路径: data/md_platform_data/user_{userId}/jobs/job_{jobId}/
 * Docker路径: /workspace/data/user_{userId}/jobs/job_{jobId}/
 * </pre>
 *
 * <p>性质名称映射（Python输出文件名 -> Java propertyName）：</p>
 * <ul>
 *   <li>density -> density</li>
 *   <li>conductivity -> conductivity</li>
 *   <li>viscosity -> viscosity</li>
 *   <li>dielectric_constant -> dielectric</li>
 *   <li>solvation_structure -> solvation</li>
 * </ul>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PostProcessingService {

    /** Docker服务，用于在md-engine容器中执行后处理命令 */
    private final DockerService dockerService;

    /** 模拟任务服务，用于查询和更新任务状态 */
    private final SimulationService simulationService;

    /** 计算结果服务，用于将解析后的结果存储到数据库 */
    private final CalculationResultService calculationResultService;

    /** 文件服务，用于文件读写操作 */
    private final FileService fileService;

    /** 路径工具类，用于生成符合规范的文件路径，禁止硬编码路径 */
    private final PathUtil pathUtil;

    /** 模拟任务仓库，用于直接查询和更新任务记录 */
    private final SimulationRepository simulationRepository;

    /** JSON序列化/反序列化工具 */
    private final ObjectMapper objectMapper;

    /** LAMMPS模板服务，用于复用target_properties解析逻辑，确保各服务解析方式一致 */
    private final LammpsTemplateService lammpsTemplateService;

    /** 模拟输入数据仓库，用于获取温度和时间步长等参数 */
    private final SimulationInputRepository simulationInputRepository;

    /** md-engine容器名称，通过配置注入避免硬编码 */
    @Value("${app.docker.md-container-name:md-engine}")
    private String mdContainerName;

    /** Docker容器中输入文件路径，通过配置注入避免硬编码 */
    @Value("${app.docker.md-input-path:/workspace/inputs}")
    private String mdInputPath;

    /** 文件存储根路径，通过配置注入避免硬编码 */
    @Value("${md-platform.file-storage.root-path:./data/md_platform_data}")
    private String rootPath;

    /** Docker容器中数据根目录路径，通过配置注入避免硬编码 */
    @Value("${app.docker.md-data-path:/workspace/data}")
    private String mdDataPath;

    /** Python后处理脚本在Docker容器中的路径 */
    private static final String DOCKER_SCRIPT_PATH = "/workspace/scripts/modeling/run_modeling.py";

    /** Spring管理的线程池，避免使用Executors.newCachedThreadPool创建无界线程池 */
    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    /**
     * Python输出文件名中的性质名称到Java propertyName的映射
     *
     * <p>映射关系：</p>
     * <ul>
     *   <li>density -> density</li>
     *   <li>conductivity -> conductivity</li>
     *   <li>viscosity -> viscosity</li>
     *   <li>dielectric_constant -> dielectric</li>
     *   <li>solvation_structure -> solvation</li>
     * </ul>
     */
    private static final Map<String, String> PROPERTY_NAME_MAPPING = new LinkedHashMap<>();

    static {
        PROPERTY_NAME_MAPPING.put("density", "density");
        PROPERTY_NAME_MAPPING.put("conductivity", "conductivity");
        PROPERTY_NAME_MAPPING.put("viscosity", "viscosity");
        PROPERTY_NAME_MAPPING.put("dielectric_constant", "dielectric");
        PROPERTY_NAME_MAPPING.put("solvation_structure", "solvation");
    }

    /**
     * 执行后处理计算（手动触发入口方法）
     *
     * <p>该方法为手动触发后处理的入口，仅允许任务状态为COMPLETED时执行。
     * 内部调用带autoTriggered参数的重载方法，autoTriggered=false。</p>
     *
     * @param jobId 任务ID
     * @return 异步执行结果，CompletableFuture包含存储的结果ID列表
     */
    public CompletableFuture<List<Long>> executePostProcessing(Long jobId) {
        return executePostProcessing(jobId, false);
    }

    /**
     * 执行后处理计算（支持自动触发模式）
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>根据任务ID查询SimulationJob，验证任务状态</li>
     *   <li>更新任务状态为POST_PROCESSING</li>
     *   <li>构建Python后处理命令</li>
     *   <li>通过DockerService在md-engine容器中执行命令</li>
     *   <li>执行成功后调用parseAndStoreResults()解析并存储结果</li>
     *   <li>执行失败时更新任务状态为FAILED并记录错误信息</li>
     * </ol>
     *
     * <p>状态检查策略：</p>
     * <ul>
     *   <li>手动触发（autoTriggered=false）：仅允许COMPLETED状态，确保模拟已完成</li>
     *   <li>自动触发（autoTriggered=true）：允许COMPLETED、RUNNING、POST_PROCESSING状态，
     *       由PipelineService保证任务处于可后处理状态（PipelineService在调用前已确认模拟完成）</li>
     * </ul>
     *
     * @param jobId          任务ID
     * @param autoTriggered  是否为自动触发模式。true=PipelineService自动调用，跳过COMPLETED状态检查；
     *                       false=手动触发，仅允许COMPLETED状态
     * @return 异步执行结果，CompletableFuture包含存储的结果ID列表
     */
    public CompletableFuture<List<Long>> executePostProcessing(Long jobId, boolean autoTriggered) {
        log.info("[后处理] 开始执行后处理计算，任务ID: {}, 自动触发: {}", jobId, autoTriggered);

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. 查询任务并验证状态
                SimulationJob job = simulationRepository.findById(jobId)
                        .orElseThrow(() -> new IllegalArgumentException("任务不存在，任务ID: " + jobId));

                String currentStatus = job.getStatus();

                if (autoTriggered) {
                    // 自动触发模式：允许COMPLETED、RUNNING、POST_PROCESSING状态
                    // PipelineService在调用前已确认模拟完成，状态可能为COMPLETED（由executeFullModeling设置）
                    // 或POST_PROCESSING（由PipelineService手动设置）
                    if (!JobStatus.COMPLETED.equals(currentStatus)
                            && !JobStatus.RUNNING.equals(currentStatus)
                            && !JobStatus.POST_PROCESSING.equals(currentStatus)) {
                        String errorMsg = String.format(
                                "自动触发模式下任务状态不允许后处理，当前状态: %s，任务ID: %s，允许状态: [COMPLETED, RUNNING, POST_PROCESSING]",
                                currentStatus, jobId);
                        log.error("[后处理] {}", errorMsg);
                        throw new IllegalStateException(errorMsg);
                    }
                    log.info("[后处理] 自动触发模式，任务状态: {}，任务ID: {}", currentStatus, jobId);
                } else {
                    // 手动触发模式：仅允许COMPLETED状态
                    if (!JobStatus.COMPLETED.equals(currentStatus)) {
                        String errorMsg = String.format("任务状态不是COMPLETED，当前状态: %s，任务ID: %s", currentStatus, jobId);
                        log.error("[后处理] {}", errorMsg);
                        throw new IllegalStateException(errorMsg);
                    }
                }

                // 2. 更新任务状态为POST_PROCESSING
                job.setStatus(JobStatus.POST_PROCESSING);
                simulationRepository.save(job);
                log.info("[后处理] 任务状态已更新为POST_PROCESSING，任务ID: {}", jobId);

                // 3. 构建Python后处理命令
                List<String> command = buildPythonCommand(job);
                log.info("[后处理] 执行命令: {}", String.join(" ", command));

                // 4. 在Docker容器中执行命令
                String output = dockerService.executeCommandInContainer(mdContainerName, command, "/workspace");
                log.info("[后处理] 后处理脚本执行完成，任务ID: {}", jobId);

                // 5. 检查执行结果
                // 注意：不能简单检查"error"字符串，因为Python日志和JSON结果中可能包含"error"字段
                // 优先检查JSON标记中的success字段，其次检查明显的Docker命令执行失败标记
                if (output != null) {
                    boolean hasError = false;
                    String errorMsg = null;

                    String jsonResult = com.mdplatform.engine.util.JsonOutputParser.extractJson(output);
                    if (jsonResult != null) {
                        try {
                            com.fasterxml.jackson.databind.JsonNode jsonNode =
                                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonResult);
                            if (jsonNode.has("success") && !jsonNode.get("success").asBoolean(true)) {
                                hasError = true;
                                errorMsg = jsonNode.has("error") ? jsonNode.get("error").asText() : "后处理脚本执行失败";
                            }
                        } catch (Exception e) {
                            // JSON解析失败，回退到检查明显的Docker命令执行失败标记
                            if (output.contains("Command failed") || output.contains("Failed to execute")) {
                                hasError = true;
                                errorMsg = "后处理命令执行失败";
                            }
                        }
                    } else {
                        // 没有JSON标记，回退到检查明显的Docker命令执行失败标记
                        if (output.contains("Command failed") || output.contains("Failed to execute")) {
                            hasError = true;
                            errorMsg = "后处理命令执行失败";
                        }
                    }

                    if (hasError) {
                        log.error("[后处理] 后处理脚本执行失败: {}", errorMsg);
                        markJobAsFailed(job, "后处理脚本执行失败: " + errorMsg);
                        return Collections.emptyList();
                    }
                }

                // 6. 解析并存储结果
                List<Long> resultIds = parseAndStoreResults(jobId);
                log.info("[后处理] 后处理完成，共存储{}条结果，任务ID: {}", resultIds.size(), jobId);

                // 7. 更新任务状态为POST_PROCESSING_COMPLETED
                job.setStatus(JobStatus.POST_PROCESSING_COMPLETED);
                simulationRepository.save(job);
                log.info("[后处理] 任务状态已更新为POST_PROCESSING_COMPLETED，任务ID: {}", jobId);

                return resultIds;

            } catch (Exception e) {
                log.error("[后处理] 后处理执行异常，任务ID: {}", jobId, e);
                // 标记任务为失败
                simulationRepository.findById(jobId).ifPresent(job -> markJobAsFailed(job, "后处理执行异常: " + e.getMessage()));
                throw new RuntimeException("后处理执行失败: " + e.getMessage(), e);
            }
        }, taskExecutor);
    }

    /**
     * 解析后处理JSON结果文件并存储到数据库
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>根据任务ID查询SimulationJob获取userId</li>
     *   <li>使用PathUtil获取后处理目录路径</li>
     *   <li>读取所有{property}_result.json文件</li>
     *   <li>解析每个JSON文件为Map</li>
     *   <li>调用storeResultToDatabase()将结果存储到数据库</li>
     * </ol>
     *
     * @param jobId 任务ID
     * @return 存储的结果ID列表
     */
    // 注意：不使用@Transactional注解，避免单个性质存储失败导致整个事务回滚
    // 每个性质的存储由CalculationResultService.createResult()的@Transactional独立管理事务
    public List<Long> parseAndStoreResults(Long jobId) {
        log.info("[后处理] 开始解析并存储结果，任务ID: {}", jobId);

        // 1. 查询任务获取userId
        SimulationJob job = simulationRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在，任务ID: " + jobId));
        Long userId = job.getUserId();

        // 2. 读取所有结果JSON文件
        Map<String, Map<String, Object>> resultMap = readResultJsonFiles(userId, jobId);
        log.info("[后处理] 读取到{}个结果文件，任务ID: {}", resultMap.size(), jobId);

        // 3. 逐个存储结果到数据库
        List<Long> resultIds = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : resultMap.entrySet()) {
            String propertyName = entry.getKey();
            Map<String, Object> resultData = entry.getValue();
            try {
                Long resultId = storeResultToDatabase(jobId, propertyName, resultData);
                resultIds.add(resultId);
                log.info("[后处理] 结果存储成功，性质: {}，结果ID: {}", propertyName, resultId);
            } catch (Exception e) {
                log.error("[后处理] 结果存储失败，性质: {}，任务ID: {}", propertyName, jobId, e);
            }
        }

        log.info("[后处理] 结果解析与存储完成，共存储{}条，任务ID: {}", resultIds.size(), jobId);
        return resultIds;
    }

    /**
     * 将单个性质的计算结果存储到数据库
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>创建CalculationResultDto，设置公共字段</li>
     *   <li>从resultData中提取子表数据，根据propertyName创建对应DTO</li>
     *   <li>设置raw_data_path和chart_data_path为相对路径</li>
     *   <li>调用calculationResultService.createResult()保存到数据库</li>
     * </ol>
     *
     * <p>子表DTO映射：</p>
     * <ul>
     *   <li>density -> DensityResultDto</li>
     *   <li>conductivity -> ConductivityResultDto</li>
     *   <li>viscosity -> ViscosityResultDto</li>
     *   <li>dielectric -> DielectricResultDto</li>
     *   <li>solvation -> SolvationResultDto</li>
     * </ul>
     *
     * @param jobId 任务ID
     * @param propertyName 性质名称（Java侧映射后的名称）
     * @param resultData 解析后的JSON数据Map
     * @return 存储后的结果ID
     */
    @Transactional
    public Long storeResultToDatabase(Long jobId, String propertyName, Map<String, Object> resultData) {
        log.info("[后处理] 存储结果到数据库，任务ID: {}，性质: {}", jobId, propertyName);

        // 查询任务获取userId（用于生成路径）
        SimulationJob job = simulationRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在，任务ID: " + jobId));
        Long userId = job.getUserId();

        // 1. 创建CalculationResultDto，设置公共字段
        CalculationResultDto dto = new CalculationResultDto();
        dto.setJobId(jobId);
        dto.setPropertyName(propertyName);

        // 从resultData中提取公共字段
        dto.setPropertyValue(extractDouble(resultData, "property_value"));
        dto.setPropertyUnit(extractString(resultData, "property_unit"));
        dto.setCalculationMethod(extractString(resultData, "calculation_method"));
        dto.setTemperatureK(extractDouble(resultData, "temperature_k"));
        dto.setPressureBar(extractDouble(resultData, "pressure_bar"));
        dto.setSamplingTimePs(extractDouble(resultData, "sampling_time_ps"));
        dto.setConvergenceStatus(extractString(resultData, "convergence_status"));
        dto.setPropertyDetail(extractString(resultData, "property_detail"));

        // 后备填充：当Python脚本未生成temperature_k/pressure_bar/sampling_time_ps时
        // 从SimulationInput中获取任务输入参数作为默认值，避免违反数据库NOT NULL约束
        if (dto.getTemperatureK() == null || dto.getPressureBar() == null || dto.getSamplingTimePs() == null) {
            SimulationInput input = simulationInputRepository.findByJobId(jobId).orElse(null);
            if (input != null) {
                // 温度：优先使用Python结果，其次使用任务输入温度
                if (dto.getTemperatureK() == null && input.getTemperature() != null) {
                    dto.setTemperatureK(input.getTemperature());
                    log.info("[后处理] temperature_k为空，使用任务输入温度: {} K，任务ID: {}", input.getTemperature(), jobId);
                }
                // 压强：优先使用Python结果，其次使用任务输入压强，最后使用默认值1.0 bar
                if (dto.getPressureBar() == null) {
                    if (input.getPressure() != null) {
                        dto.setPressureBar(input.getPressure());
                    } else {
                        dto.setPressureBar(1.0);
                    }
                    log.info("[后处理] pressure_bar为空，使用任务输入压强: {} bar，任务ID: {}", dto.getPressureBar(), jobId);
                }
                // 采样时间：默认生产模拟1000步 * 1.0 fs = 1000 fs = 1.0 ps
                if (dto.getSamplingTimePs() == null) {
                    Double timeStepFs = input.getTimeStepFs() != null ? input.getTimeStepFs() : 1.0;
                    // 默认生产模拟步数1000（与LammpsTemplateService中的nsteps默认值一致）
                    double samplingTimePs = 1000 * timeStepFs / 1000.0;
                    dto.setSamplingTimePs(samplingTimePs);
                    log.info("[后处理] sampling_time_ps为空，使用默认采样时间: {} ps，任务ID: {}", samplingTimePs, jobId);
                }
            } else {
                // SimulationInput不存在时使用硬编码默认值，确保不违反NOT NULL约束
                if (dto.getTemperatureK() == null) {
                    dto.setTemperatureK(298.15);
                }
                if (dto.getPressureBar() == null) {
                    dto.setPressureBar(1.0);
                }
                if (dto.getSamplingTimePs() == null) {
                    dto.setSamplingTimePs(1.0);
                }
                log.warn("[后处理] SimulationInput不存在，使用硬编码默认值，任务ID: {}", jobId);
            }
        }

        // 2. 设置文件路径（相对路径）
        // raw_data_path: 后处理结果JSON文件的相对路径
        String rawResultFilename = pathUtil.getResultFilename(mapToPythonPropertyName(propertyName));
        Path postProcessingPath = pathUtil.getPostProcessingPath(userId, jobId);
        Path rawResultPath = postProcessingPath.resolve(rawResultFilename);
        dto.setRawDataPath(pathUtil.getRelativePath(userId, jobId, rawResultPath));

        // chart_data_path: 可视化图表数据的相对路径
        String chartFilename = propertyName + "_curve.json";
        Path chartPath = pathUtil.getVisualizationPath(userId, jobId).resolve("charts").resolve(chartFilename);
        if (Files.exists(chartPath)) {
            dto.setChartDataPath(pathUtil.getRelativePath(userId, jobId, chartPath));
        } else {
            // 图表文件可能尚未生成，设置预期路径
            dto.setChartDataPath("visualization/charts/" + chartFilename);
        }

        // 3. 提取子表数据并创建对应DTO
        Object subTableData = resultData.get("sub_table_data");
        if (subTableData instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> subTableMap = (Map<String, Object>) subTableData;
            Object detailDto = buildSubTableDto(propertyName, subTableMap);
            dto.setPropertyDetailData(detailDto);
        }

        // 4. 调用CalculationResultService保存到数据库
        CalculationResultDto savedDto = calculationResultService.createResult(dto);
        Long resultId = savedDto.getResultId();

        log.info("[后处理] 结果存储成功，结果ID: {}，性质: {}，任务ID: {}", resultId, propertyName, jobId);
        return resultId;
    }

    /**
     * 构建Python后处理命令
     *
     * <p>命令格式：</p>
     * <pre>
     * python3 /workspace/scripts/modeling/run_modeling.py
     *     --mode post-processing
     *     --job-dir /workspace/data/user_{userId}/jobs/job_{jobId}
     *     --target-properties density,conductivity
     *     --temperature 300
     *     --time-step-fs 1.0
     * </pre>
     *
     * @param job 模拟任务对象
     * @return 命令参数列表
     */
    public List<String> buildPythonCommand(SimulationJob job) {
        log.info("[后处理] 构建Python后处理命令，任务ID: {}", job.getJobId());

        Long userId = job.getUserId();
        Long jobId = job.getJobId();

        // 构建容器内任务目录路径，使用PathUtil生成路径并通过convertToDockerPath转换为容器路径，禁止硬编码
        String containerJobDir = pathUtil.convertToDockerPath(pathUtil.getJobRootPath(userId, jobId));

        // 解析目标属性：复用LammpsTemplateService.parseTargetProperties()，确保解析逻辑一致
        List<String> targetPropertiesList = lammpsTemplateService.parseTargetProperties(job.getTargetProperties());
        // Python脚本需要逗号分隔的格式，如 "density,conductivity"
        String targetProperties = String.join(",", targetPropertiesList);

        // 使用bash -c方式执行，先cd到/workspace/scripts目录，
        // 确保python3 -m modeling.run_modeling能正确找到模块并支持相对导入
        // 这与MoltemplateExecutionService的调用方式保持一致
        StringBuilder cmdBuilder = new StringBuilder();
        cmdBuilder.append("cd /workspace/scripts && python3 -m modeling.run_modeling");
        cmdBuilder.append(" --user-id ").append(userId);
        cmdBuilder.append(" --job-id ").append(jobId);
        cmdBuilder.append(" --mode post-processing");
        cmdBuilder.append(" --job-dir ").append(containerJobDir);
        cmdBuilder.append(" --target-properties ").append(targetProperties);

        // 传递温度参数，确保后处理使用与模拟一致的温度
        // 从SimulationInput中获取温度，如果未设置则使用默认值300K
        try {
            Optional<com.mdplatform.engine.model.SimulationInput> inputOpt = simulationInputRepository.findByJobId(jobId);
            if (inputOpt.isPresent()) {
                com.mdplatform.engine.model.SimulationInput simInput = inputOpt.get();
                Double temperature = simInput.getTemperature();
                if (temperature != null && temperature > 0) {
                    cmdBuilder.append(" --temperature ").append(temperature);
                    log.info("[后处理] 传递温度参数: {}K", temperature);
                }
                
                // 传递时间步长参数
                Double timeStepFs = simInput.getTimeStepFs();
                if (timeStepFs != null && timeStepFs > 0) {
                    cmdBuilder.append(" --time-step-fs ").append(timeStepFs);
                    log.info("[后处理] 传递时间步长参数: {}fs", timeStepFs);
                }
            } else {
                log.warn("[后处理] 未找到SimulationInput记录，使用默认温度和时间步长");
            }
        } catch (Exception e) {
            log.warn("[后处理] 获取SimulationInput参数失败，使用默认值: {}", e.getMessage());
        }

        String cmdStr = cmdBuilder.toString();
        List<String> command = Arrays.asList("bash", "-c", cmdStr);
        log.info("[后处理] Python命令构建完成: {}", cmdStr);
        return command;
    }

    /**
     * 读取所有后处理结果JSON文件
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>使用PathUtil.getPostProcessingPath()获取后处理目录</li>
     *   <li>列出所有*_result.json文件</li>
     *   <li>读取每个文件并解析为Map</li>
     *   <li>根据文件名提取性质名称并映射为Java propertyName</li>
     * </ol>
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     * @return 性质名称到结果数据Map的映射
     */
    public Map<String, Map<String, Object>> readResultJsonFiles(Long userId, Long jobId) {
        log.info("[后处理] 开始读取结果JSON文件，用户ID: {}，任务ID: {}", userId, jobId);

        Map<String, Map<String, Object>> resultMap = new LinkedHashMap<>();

        // 获取后处理目录路径
        Path postProcessingPath = pathUtil.getPostProcessingPath(userId, jobId);

        // 检查目录是否存在
        if (!Files.exists(postProcessingPath)) {
            log.warn("[后处理] 后处理目录不存在: {}", postProcessingPath);
            return resultMap;
        }

        // 列出所有*_result.json文件
        try (Stream<Path> stream = Files.list(postProcessingPath)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith("_result.json"))
                    .forEach(path -> {
                        try {
                            // 读取文件内容
                            String content = Files.readString(path, StandardCharsets.UTF_8);

                            // 解析JSON为Map
                            @SuppressWarnings("unchecked")
                            Map<String, Object> data = objectMapper.readValue(content,
                                    objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));

                            // 从文件名提取性质名称（如 density_result.json -> density）
                            String filename = path.getFileName().toString();
                            String pythonPropertyName = filename.replace("_result.json", "");

                            // 映射为Java propertyName
                            String javaPropertyName = mapToJavaPropertyName(pythonPropertyName);

                            resultMap.put(javaPropertyName, data);
                            log.info("[后处理] 读取结果文件成功: {} -> propertyName={}", filename, javaPropertyName);

                        } catch (IOException e) {
                            log.error("[后处理] 读取结果文件失败: {}", path, e);
                        }
                    });
        } catch (IOException e) {
            log.error("[后处理] 列出后处理目录文件失败: {}", postProcessingPath, e);
        }

        log.info("[后处理] 结果文件读取完成，共读取{}个文件", resultMap.size());
        return resultMap;
    }

    /**
     * 将Python输出文件名中的性质名称映射为Java propertyName
     *
     * <p>映射关系：</p>
     * <ul>
     *   <li>density -> density</li>
     *   <li>conductivity -> conductivity</li>
     *   <li>viscosity -> viscosity</li>
     *   <li>dielectric_constant -> dielectric</li>
     *   <li>solvation_structure -> solvation</li>
     * </ul>
     *
     * @param pythonPropertyName Python侧性质名称
     * @return Java侧propertyName
     */
    private String mapToJavaPropertyName(String pythonPropertyName) {
        return PROPERTY_NAME_MAPPING.getOrDefault(pythonPropertyName, pythonPropertyName);
    }

    /**
     * 将Java propertyName映射回Python侧性质名称（用于生成文件名）
     *
     * <p>映射关系（反向）：</p>
     * <ul>
     *   <li>density -> density</li>
     *   <li>conductivity -> conductivity</li>
     *   <li>viscosity -> viscosity</li>
     *   <li>dielectric -> dielectric_constant</li>
     *   <li>solvation -> solvation_structure</li>
     * </ul>
     *
     * @param javaPropertyName Java侧propertyName
     * @return Python侧性质名称
     */
    private String mapToPythonPropertyName(String javaPropertyName) {
        switch (javaPropertyName) {
            case "dielectric":
                return "dielectric_constant";
            case "solvation":
                return "solvation_structure";
            default:
                return javaPropertyName;
        }
    }

    /**
     * 根据性质名称构建子表数据DTO
     *
     * <p>根据propertyName从subTableMap中提取对应字段，构建子表DTO对象。</p>
     *
     * @param propertyName 性质名称
     * @param subTableMap 子表数据Map
     * @return 子表DTO对象
     */
    private Object buildSubTableDto(String propertyName, Map<String, Object> subTableMap) {
        switch (propertyName) {
            case "density":
                return buildDensityResultDto(subTableMap);
            case "conductivity":
                return buildConductivityResultDto(subTableMap);
            case "viscosity":
                return buildViscosityResultDto(subTableMap);
            case "dielectric":
                return buildDielectricResultDto(subTableMap);
            case "solvation":
                return buildSolvationResultDto(subTableMap);
            default:
                log.warn("[后处理] 未知的性质类型，跳过子表DTO构建: {}", propertyName);
                return null;
        }
    }

    /**
     * 构建密度结果子表DTO
     *
     * @param data 子表数据Map
     * @return DensityResultDto对象
     */
    private DensityResultDto buildDensityResultDto(Map<String, Object> data) {
        DensityResultDto dto = new DensityResultDto();
        dto.setDensityTensor(extractString(data, "density_tensor"));
        dto.setComponentDensity(extractString(data, "component_density"));
        return dto;
    }

    /**
     * 构建电导率结果子表DTO
     *
     * @param data 子表数据Map
     * @return ConductivityResultDto对象
     */
    private ConductivityResultDto buildConductivityResultDto(Map<String, Object> data) {
        ConductivityResultDto dto = new ConductivityResultDto();
        dto.setConductivityTensor(extractString(data, "conductivity_tensor"));
        dto.setIonContribution(extractString(data, "ion_contribution"));
        dto.setElectricFieldStrength(extractDouble(data, "electric_field_strength"));
        dto.setResistivity(extractDouble(data, "resistivity"));
        return dto;
    }

    /**
     * 构建粘度结果子表DTO
     *
     * @param data 子表数据Map
     * @return ViscosityResultDto对象
     */
    private ViscosityResultDto buildViscosityResultDto(Map<String, Object> data) {
        ViscosityResultDto dto = new ViscosityResultDto();
        dto.setViscosityValue(extractDouble(data, "viscosity_value"));
        dto.setShearRate(extractDouble(data, "shear_rate"));
        dto.setStressResponse(extractDouble(data, "stress_response"));
        dto.setKinematicViscosity(extractDouble(data, "kinematic_viscosity"));
        return dto;
    }

    /**
     * 构建介电常数结果子表DTO
     *
     * @param data 子表数据Map
     * @return DielectricResultDto对象
     */
    private DielectricResultDto buildDielectricResultDto(Map<String, Object> data) {
        DielectricResultDto dto = new DielectricResultDto();
        dto.setDielectricConstantTensor(extractString(data, "dielectric_constant_tensor"));
        dto.setStaticDielectricConstant(extractDouble(data, "static_dielectric_constant"));
        dto.setDielectricSpectrumData(extractString(data, "dielectric_spectrum_data"));
        dto.setDipoleMomentData(extractString(data, "dipole_moment_data"));
        dto.setComponentContribution(extractString(data, "component_contribution"));
        dto.setSystemSize(extractString(data, "system_size"));
        return dto;
    }

    /**
     * 构建溶剂化结构结果子表DTO
     *
     * @param data 子表数据Map
     * @return SolvationResultDto对象
     */
    private SolvationResultDto buildSolvationResultDto(Map<String, Object> data) {
        SolvationResultDto dto = new SolvationResultDto();
        dto.setCentralIonType(extractString(data, "central_ion_type"));
        dto.setSolvationShellStructure(extractString(data, "solvation_shell_structure"));
        dto.setAverageCoordinationNumber(extractDouble(data, "average_coordination_number"));
        dto.setCoordinationDistance(extractDouble(data, "coordination_distance"));
        dto.setRdfCharacteristicPeak(extractString(data, "rdf_characteristic_peak"));
        dto.setHydrogenBondFeature(extractString(data, "hydrogen_bond_feature"));
        dto.setSolvationStability(extractDouble(data, "solvation_stability"));
        dto.setIonSolventInteractionEnergy(extractDouble(data, "ion_solvent_interaction_energy"));
        return dto;
    }

    /**
     * 从Map中提取字符串值
     *
     * @param map 数据Map
     * @param key 键名
     * @return 字符串值，不存在时返回null
     */
    private String extractString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        // 如果值不是String类型，转换为JSON字符串
        if (value instanceof String) {
            return (String) value;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("[后处理] 转换值为字符串失败: key={}, value={}", key, value, e);
            return value.toString();
        }
    }

    /**
     * 从Map中提取Double值
     *
     * <p>支持Number类型和String类型的值转换。</p>
     *
     * @param map 数据Map
     * @param key 键名
     * @return Double值，不存在或转换失败时返回null
     */
    private Double extractDouble(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                log.warn("[后处理] 转换值为Double失败: key={}, value={}", key, value, e);
                return null;
            }
        }
        log.warn("[后处理] 不支持的Double转换类型: key={}, type={}", key, value.getClass().getSimpleName());
        return null;
    }

    /**
     * 将任务标记为失败状态
     *
     * @param job 模拟任务对象
     * @param errorMessage 错误信息
     */
    private void markJobAsFailed(SimulationJob job, String errorMessage) {
        try {
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(errorMessage);
            job.setEndTime(java.time.LocalDateTime.now());
            simulationRepository.save(job);
            log.info("[后处理] 任务已标记为FAILED，任务ID: {}，错误: {}", job.getJobId(), errorMessage);
        } catch (Exception e) {
            log.error("[后处理] 标记任务失败状态时异常，任务ID: {}", job.getJobId(), e);
        }
    }
}
