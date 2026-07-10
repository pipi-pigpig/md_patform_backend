package com.mdplatform.engine.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdplatform.common.util.PathUtil;
import com.mdplatform.engine.util.JsonOutputParser;
import com.mdplatform.engine.model.SimulationInput;
import com.mdplatform.engine.model.SimulationJob;
import com.mdplatform.engine.repository.SimulationInputRepository;
import com.mdplatform.engine.repository.SimulationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

/**
 * LAMMPS模板渲染服务
 *
 * <p>该服务负责使用Jinja2模板引擎动态生成LAMMPS输入脚本。
 * 由于Java没有原生的Jinja2库，采用通过Docker容器中的Python脚本渲染模板的方式。</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>从数据库读取任务配置（SimulationJob、SimulationInput）</li>
 *   <li>解析target_properties（JSON数组）</li>
 *   <li>构建模板上下文参数（Map&lt;String, Object&gt;）</li>
 *   <li>通过DockerService在md_engine容器中执行Python脚本渲染模板</li>
 *   <li>将渲染结果写入inputs目录</li>
 *   <li>验证生成的脚本文件</li>
 * </ul>
 *
 * <p>模板渲染流程：</p>
 * <ol>
 *   <li>从数据库读取模拟参数（SimulationJob、SimulationInput）</li>
 *   <li>构建模板上下文参数（温度、压力、步数、目标性质等）</li>
 *   <li>将上下文参数序列化为JSON字符串</li>
 *   <li>通过DockerService在md_engine容器中执行Python脚本</li>
 *   <li>Python脚本使用Jinja2库渲染模板文件</li>
 *   <li>渲染结果写入任务的inputs目录</li>
 *   <li>验证生成的脚本文件是否完整</li>
 * </ol>
 *
 * <p>文件路径规范：</p>
 * <pre>
 * system_templates/lammps_templates/       # Jinja2模板目录
 * ├── in.minimization.j2                   # 能量最小化模板
 * ├── in.equilibrium.j2                    # 平衡模拟模板
 * ├── in.production.j2                     # 生产模拟模板
 * └── compute_*.j2                         # 各种计算配置模板
 *
 * user_{userId}/jobs/job_{jobId}/inputs/   # 生成的脚本输出目录
 * ├── in.minimization                      # 能量最小化脚本
 * ├── in.equilibrium                       # 平衡模拟脚本
 * └── in.production                        # 生产模拟脚本
 * </pre>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 * @since 2026-06-07
 */
@Service
@Slf4j
public class LammpsTemplateService {

    /** Docker服务，用于在容器中执行Python脚本 */
    private final DockerService dockerService;

    /** 路径工具类，用于生成符合规范的文件路径 */
    private final PathUtil pathUtil;

    /** 模拟输入服务，用于查询模拟输入参数 */
    private final SimulationInputService simulationInputService;

    /** 模拟任务仓库，用于查询任务信息 */
    private final SimulationRepository simulationRepository;

    /** 模拟输入仓库，用于查询模拟输入参数 */
    private final SimulationInputRepository simulationInputRepository;

    /** JSON解析器，用于序列化模板上下文参数 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 文件存储根路径，通过配置注入避免硬编码 */
    @Value("${md-platform.file-storage.root-path}")
    private String rootPath;

    /** Docker容器名称，通过配置注入 */
    @Value("${app.docker.md-container-name:md-engine}")
    private String mdContainerName;

    /** Python模板渲染脚本在容器中的路径 */
    @Value("${md-platform.scripts.lammps-template-utils-path:scripts/modeling/utils/lammps_template_utils.py}")
    private String lammpsTemplateUtilsPath;

    /** 容器内数据根目录路径 */
    @Value("${app.docker.md-data-path:/workspace/data}")
    private String mdDataPath;

    /**
     * 构造函数，通过依赖注入获取所需服务
     *
     * @param dockerService Docker服务实例
     * @param pathUtil 路径工具类实例
     * @param simulationInputService 模拟输入服务实例
     * @param simulationRepository 模拟任务仓库实例
     * @param simulationInputRepository 模拟输入仓库实例
     */
    public LammpsTemplateService(DockerService dockerService,
                                  PathUtil pathUtil,
                                  SimulationInputService simulationInputService,
                                  SimulationRepository simulationRepository,
                                  SimulationInputRepository simulationInputRepository) {
        this.dockerService = dockerService;
        this.pathUtil = pathUtil;
        this.simulationInputService = simulationInputService;
        this.simulationRepository = simulationRepository;
        this.simulationInputRepository = simulationInputRepository;
    }

    /**
     * 生成LAMMPS输入脚本
     *
     * <p>根据任务配置和目标性质，使用Jinja2模板引擎动态生成LAMMPS输入脚本。
     * 该方法执行以下步骤：</p>
     * <ol>
     *   <li>从数据库读取模拟参数</li>
     *   <li>构建模板上下文参数</li>
     *   <li>通过Docker执行Python脚本渲染模板</li>
     *   <li>验证生成的脚本</li>
     *   <li>返回结果</li>
     * </ol>
     *
     * @param userId 用户ID，用于确定文件存储路径
     * @param jobId 任务ID，用于确定文件存储路径和查询任务配置
     * @param targetProperties 目标性质列表（如["density","conductivity"]）
     * @return 生成结果Map，包含success、rendered_files、errors等字段
     * @throws IllegalArgumentException 当参数无效时抛出
     * @throws RuntimeException 当模板渲染失败时抛出
     */
    public Map<String, Object> generateInputScripts(Long userId, Long jobId, List<String> targetProperties) {
        log.info("[LAMMPS模板] 开始生成LAMMPS输入脚本: userId={}, jobId={}, targetProperties={}",
                userId, jobId, targetProperties);

        // 参数校验
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId无效: " + userId);
        }
        if (jobId == null || jobId <= 0) {
            throw new IllegalArgumentException("jobId无效: " + jobId);
        }
        if (targetProperties == null || targetProperties.isEmpty()) {
            log.warn("[LAMMPS模板] targetProperties为空，将使用默认配置");
            targetProperties = Collections.singletonList("density");
        }

        try {
            // 步骤1：从数据库读取模拟参数
            log.info("[LAMMPS模板] 步骤1：从数据库读取模拟参数");
            SimulationJob job = simulationRepository.findById(jobId).orElse(null);
            SimulationInput input = simulationInputRepository.findByJobId(jobId).orElse(null);

            // 步骤2：构建模板上下文
            log.info("[LAMMPS模板] 步骤2：构建模板上下文参数");
            Map<String, Object> context = buildTemplateContext(userId, jobId, targetProperties, job, input);

            // 步骤3：通过Docker执行Python脚本渲染模板
            log.info("[LAMMPS模板] 步骤3：通过Docker执行Python脚本渲染模板");
            Map<String, Object> renderResult = executeTemplateRendering(userId, jobId, context);

            // 步骤4：验证生成的脚本
            if (Boolean.TRUE.equals(renderResult.get("success"))) {
                log.info("[LAMMPS模板] 步骤4：验证生成的脚本");
                boolean valid = validateGeneratedScripts(userId, jobId,
                        (List<String>) renderResult.getOrDefault("rendered_files", Collections.emptyList()));
                renderResult.put("validation_passed", valid);

                if (!valid) {
                    log.warn("[LAMMPS模板] 生成的脚本验证未通过");
                }
            }

            log.info("[LAMMPS模板] LAMMPS输入脚本生成完成: success={}, rendered_files={}",
                    renderResult.get("success"), renderResult.get("rendered_files"));

            return renderResult;

        } catch (Exception e) {
            log.error("[LAMMPS模板] 生成LAMMPS输入脚本失败: userId={}, jobId={}, error={}",
                    userId, jobId, e.getMessage(), e);

            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("errors", Collections.singletonList("模板渲染失败: " + e.getMessage()));
            errorResult.put("rendered_files", Collections.emptyList());
            return errorResult;
        }
    }

    /**
     * 构建模板上下文参数
     *
     * <p>根据任务配置和模拟输入参数，构建Jinja2模板渲染所需的上下文参数。
     * 优先使用数据库中的参数值，如果数据库中没有则使用默认值。</p>
     *
     * <p>上下文参数包含以下类别：</p>
     * <ul>
     *   <li>基础信息：任务ID、生成时间</li>
     *   <li>热力学参数：温度、压力</li>
     *   <li>模拟参数：时间步长、截断距离、耦合常数</li>
     *   <li>各阶段步数：NVT步数、NPT步数、生产步数</li>
     *   <li>输出频率：热力学输出频率、轨迹输出频率、平均输出频率</li>
     *   <li>最小化参数：最小化算法、收敛标准</li>
     *   <li>目标性质：需要计算的物理性质列表</li>
     *   <li>随机种子：确保模拟可重复性</li>
     * </ul>
     *
     * @param userId 用户ID
     * @param jobId 任务ID
     * @param targetProperties 目标性质列表
     * @param job 模拟任务对象，可为null
     * @param input 模拟输入参数对象，可为null
     * @return 模板上下文参数Map
     */
    private Map<String, Object> buildTemplateContext(Long userId, Long jobId,
                                                      List<String> targetProperties,
                                                      SimulationJob job,
                                                      SimulationInput input) {
        Map<String, Object> context = new HashMap<>();

        // ========== 基础信息 ==========
        context.put("job_id", jobId.toString());
        context.put("generation_time", LocalDateTime.now().toString());

        // ========== 热力学参数 ==========
        // 优先使用数据库中的温度值，否则使用默认值300K
        if (input != null && input.getTemperature() != null) {
            context.put("temp", input.getTemperature());
        } else {
            context.put("temp", 300.0);
        }

        // 优先使用数据库中的压力值，否则使用默认值1.0 bar
        if (input != null && input.getPressure() != null) {
            context.put("press", input.getPressure());
        } else {
            context.put("press", 1.0);
        }

        // ========== 模拟参数 ==========
        // 时间步长（fs），优先使用数据库值
        if (input != null && input.getTimeStepFs() != null) {
            context.put("timestep", input.getTimeStepFs());
        } else {
            context.put("timestep", 1.0);
        }

        // 截断距离（Å），优先使用数据库值
        // 默认8.0 Å：避免密集Packmol堆积系统中邻居列表溢出导致LAMMPS挂死
        // 12.0 Å的截断在密集系统中会产生过多邻居，导致初始化时CPU 100%假死
        if (input != null && input.getCutoffDistanceAng() != null) {
            context.put("cutoff", input.getCutoffDistanceAng());
        } else {
            context.put("cutoff", 8.0);
        }

        // 恒温器耦合常数（时间步数）
        context.put("tau_t", 100.0);
        // 恒压器耦合常数（时间步数）
        context.put("tau_p", 1000.0);

        // ========== 各阶段步数 ==========
        // 注意：使用较小的步数值以确保测试能在合理时间内完成
        // nsteps_nvt: NVT平衡步数, nsteps_npt: NPT平衡步数, nsteps: 生产模拟步数
        // 生产环境中建议调整为 nsteps_nvt=25000, nsteps_npt=25000, nsteps=100000
        context.put("nsteps_nvt", 500);
        context.put("nsteps_npt", 500);
        context.put("nsteps", 1000);

        // NVE/limit 逐渐松弛阶段步数（软势最小化后过渡到真实力场的必要步骤）
        // nsteps_nve_small: 小cutoff(4.0A) NVE步数，安全松弛避免能量爆炸
        // nsteps_nve_medium: 中cutoff(6.0A) NVE步数，逐步引入更多邻居
        // nsteps_nve_full: 全cutoff(8.0A) NVE步数，达到完整相互作用范围
        context.put("nsteps_nve_small", 500);
        context.put("nsteps_nve_medium", 500);
        context.put("nsteps_nve_full", 500);

        // ========== 输出频率 ==========
        // 优先使用数据库中的输出频率值
        if (input != null && input.getOutputFrequencyStep() != null) {
            int outputFreq = input.getOutputFrequencyStep();
            context.put("thermo_freq", outputFreq);
            // dump_freq确保至少能捕获一些轨迹帧：取outputFreq与50的较小值
            // 对于短测试模拟（nsteps=1000），dump_freq=50可生成20个轨迹帧
            context.put("dump_freq", Math.min(outputFreq, 50));
            context.put("ave_freq", outputFreq);
        } else {
            context.put("thermo_freq", 100);
            context.put("dump_freq", 50);
            context.put("ave_freq", 100);
        }

        // ========== 最小化参数 ==========
        // 软势三步最小化策略（SD）：
        //   使用软势 A*(1-r/rc)^2 逐步分离Packmol堆积产生的原子重叠。
        //   etol/ftol=1.0e-6 确保充分收敛，避免残余重叠导致LJ能量爆炸。
        context.put("min_style", "sd");
        context.put("etol", 1.0e-6);
        if (input != null && input.getMinimizationForceThreshold() != null) {
            context.put("ftol", input.getMinimizationForceThreshold());
        } else {
            context.put("ftol", 1.0e-6);
        }
        // CG/SD迭代上限
        // 使用maxiter=5000以确保软势最小化充分收敛，避免残余原子重叠导致LJ能量爆炸。
        // 此前的500步过少，导致最小化在未收敛时停止，残余的原子重叠在切换到真实LJ时
        // 产生1e156量级的能量爆炸。
        context.put("maxiter", 5000);
        context.put("maxeval", 5000);

        // ========== 目标性质 ==========
        context.put("target_properties", targetProperties);

        // ========== 随机种子 ==========
        // 优先使用任务中配置的随机种子，确保模拟可重复性
        if (job != null && job.getRandomSeed() != null) {
            context.put("seed", job.getRandomSeed());
        } else {
            context.put("seed", 12345);
        }

        // ========== 系综和恒温器/恒压器类型 ==========
        if (input != null) {
            context.put("ensemble_type", input.getEnsembleType());
            context.put("thermostat_type", input.getThermostatType());
            if (input.getBarostatType() != null) {
                context.put("barostat_type", input.getBarostatType());
            } else {
                context.put("barostat_type", "Parrinello-Rahman");
            }
        } else {
            context.put("ensemble_type", "NPT");
            context.put("thermostat_type", "Nose-Hoover");
            context.put("barostat_type", "Parrinello-Rahman");
        }

        log.debug("[LAMMPS模板] 模板上下文参数构建完成: {}", context.keySet());
        return context;
    }

    /**
     * 通过Docker执行Python脚本渲染模板
     *
     * <p>在md_engine容器中执行Python脚本，使用Jinja2模板引擎渲染LAMMPS输入脚本。
     * 该方法将模板上下文参数序列化为JSON，通过命令行参数传递给Python脚本。</p>
     *
     * <p>执行流程：</p>
     * <ol>
     *   <li>将模板上下文参数序列化为JSON字符串</li>
     *   <li>构建容器内的模板目录和输出目录路径</li>
     *   <li>通过DockerService在容器中执行Python脚本</li>
     *   <li>解析Python脚本的JSON输出结果</li>
     * </ol>
     *
     * @param userId 用户ID，用于确定文件存储路径
     * @param jobId 任务ID，用于确定文件存储路径
     * @param context 模板上下文参数Map
     * @return 渲染结果Map，包含success、rendered_files、errors等字段
     * @throws JsonProcessingException 当JSON序列化失败时抛出
     * @throws RuntimeException 当Docker命令执行失败时抛出
     */
    private Map<String, Object> executeTemplateRendering(Long userId, Long jobId,
                                                          Map<String, Object> context) throws JsonProcessingException {
        log.info("[LAMMPS模板] 开始执行模板渲染: userId={}, jobId={}", userId, jobId);

        // 将上下文参数序列化为JSON字符串
        String contextJson = objectMapper.writeValueAsString(context);
        log.debug("[LAMMPS模板] 上下文参数JSON长度: {}", contextJson.length());

        // 构建容器内的路径，使用PathUtil生成路径并通过convertToDockerPath转换为容器路径，禁止硬编码
        // 模板目录：使用PathUtil获取LAMMPS模板目录并转换为Docker容器路径
        String containerTemplateDir = pathUtil.convertToDockerPath(pathUtil.getLammpsTemplatesPath());
        // 输出目录：使用PathUtil获取输入目录并转换为Docker容器路径
        String containerOutputDir = pathUtil.convertToDockerPath(pathUtil.getInputPath(userId, jobId));

        // 确保输出目录存在
        List<String> mkdirCmd = Arrays.asList("mkdir", "-p", containerOutputDir);
        dockerService.executeCommandInContainer(mdContainerName, mkdirCmd, "/workspace");

        // 构建Python脚本执行命令
        // 使用python3执行lammps_template_utils.py，传入模板目录、输出目录和上下文JSON
        List<String> commands = Arrays.asList(
                "python3",
                "/workspace/" + lammpsTemplateUtilsPath,
                "--template-dir", containerTemplateDir,
                "--output-dir", containerOutputDir,
                "--context-json", contextJson
        );

        log.info("[LAMMPS模板] 执行Docker命令渲染模板");
        log.debug("[LAMMPS模板] 模板目录: {}", containerTemplateDir);
        log.debug("[LAMMPS模板] 输出目录: {}", containerOutputDir);

        // 执行Docker命令
        String output = dockerService.executeCommandInContainer(mdContainerName, commands, "/workspace");

        // 解析Python脚本的输出结果
        Map<String, Object> renderResult = parseRenderOutput(output);

        log.info("[LAMMPS模板] 模板渲染完成: success={}", renderResult.get("success"));
        return renderResult;
    }

    /**
     * 解析Python脚本渲染输出
     *
     * <p>使用JsonOutputParser工具类从Python脚本的标准输出中提取JSON格式的渲染结果。
     * 该方法支持多行JSON格式（由Python的json.dumps(indent=2)生成），
     * 通过反向搜索机制可靠地提取JSON内容。</p>
     *
     * @param output Python脚本的标准输出
     * @return 渲染结果Map，包含success、rendered_files、errors等字段
     */
    private Map<String, Object> parseRenderOutput(String output) {
        Map<String, Object> result = new HashMap<>();

        if (output == null || output.trim().isEmpty()) {
            log.error("[LAMMPS模板] Python脚本输出为空");
            result.put("success", false);
            result.put("errors", Collections.singletonList("Python脚本输出为空"));
            result.put("rendered_files", Collections.emptyList());
            return result;
        }

        try {
            // 使用JsonOutputParser提取JSON字符串（支持多行JSON，不限于单行匹配）
            String jsonStr = JsonOutputParser.extractJson(output);

            if (jsonStr != null) {
                // 解析JSON结果
                Map<String, Object> parsed = objectMapper.readValue(jsonStr,
                        objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class));
                result.putAll(parsed);
            } else {
                // 没有找到JSON输出，可能是脚本执行失败
                log.error("[LAMMPS模板] 未找到Python脚本的JSON输出");
                result.put("success", false);
                result.put("errors", Collections.singletonList("未找到Python脚本的JSON输出"));
                result.put("raw_output", output);
                result.put("rendered_files", Collections.emptyList());
            }

        } catch (Exception e) {
            log.error("[LAMMPS模板] 解析Python脚本输出失败: {}", e.getMessage());
            result.put("success", false);
            result.put("errors", Collections.singletonList("解析输出失败: " + e.getMessage()));
            result.put("raw_output", output);
            result.put("rendered_files", Collections.emptyList());
        }

        return result;
    }

    /**
     * 验证生成的脚本文件
     *
     * <p>检查生成的LAMMPS输入脚本文件是否存在且非空。
     * 验证以下文件：</p>
     * <ul>
     *   <li>in.minimization - 能量最小化脚本</li>
     *   <li>in.equilibrium - 平衡模拟脚本</li>
     *   <li>in.production - 生产模拟脚本</li>
     * </ul>
     *
     * @param userId 用户ID，用于确定文件存储路径
     * @param jobId 任务ID，用于确定文件存储路径
     * @param renderedFiles 已渲染的文件名列表
     * @return true表示所有必要文件验证通过，false表示至少一个文件验证失败
     */
    private boolean validateGeneratedScripts(Long userId, Long jobId, List<String> renderedFiles) {
        log.info("[LAMMPS模板] 验证生成的脚本文件: userId={}, jobId={}", userId, jobId);

        Path inputPath = pathUtil.getInputPath(userId, jobId);

        // 必要的脚本文件列表
        List<String> requiredScripts = Arrays.asList(
                "in.minimization",
                "in.equilibrium",
                "in.production"
        );

        boolean allValid = true;
        for (String scriptName : requiredScripts) {
            Path scriptPath = inputPath.resolve(scriptName);

            // 检查文件是否存在
            if (!java.nio.file.Files.exists(scriptPath)) {
                log.warn("[LAMMPS模板] 脚本文件不存在: {}", scriptPath);
                allValid = false;
                continue;
            }

            // 检查文件是否非空
            try {
                long fileSize = java.nio.file.Files.size(scriptPath);
                if (fileSize == 0) {
                    log.warn("[LAMMPS模板] 脚本文件为空: {}", scriptPath);
                    allValid = false;
                } else {
                    log.debug("[LAMMPS模板] 脚本文件验证通过: {}, 大小={}字节", scriptPath, fileSize);
                }
            } catch (Exception e) {
                log.error("[LAMMPS模板] 检查脚本文件大小失败: {}", scriptPath, e);
                allValid = false;
            }
        }

        if (allValid) {
            log.info("[LAMMPS模板] 所有脚本文件验证通过");
        } else {
            log.warn("[LAMMPS模板] 部分脚本文件验证未通过");
        }

        return allValid;
    }

    /**
     * 解析target_properties JSON字符串为列表
     *
     * <p>将数据库中存储的target_properties JSON字符串解析为Java List。
     * 支持以下JSON格式：</p>
     * <ul>
     *   <li>JSON数组：["density","conductivity"]</li>
     *   <li>逗号分隔字符串：density,conductivity</li>
     * </ul>
     *
     * @param targetPropertiesJson target_properties JSON字符串
     * @return 目标性质列表
     */
    public List<String> parseTargetProperties(String targetPropertiesJson) {
        if (targetPropertiesJson == null || targetPropertiesJson.trim().isEmpty()) {
            log.warn("[LAMMPS模板] target_properties为空，使用默认值[density]");
            return Collections.singletonList("density");
        }

        try {
            // 尝试解析为JSON数组
            return objectMapper.readValue(targetPropertiesJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            // JSON解析失败，尝试按逗号分隔解析
            log.warn("[LAMMPS模板] JSON解析失败，尝试按逗号分隔解析: {}", targetPropertiesJson);
            String[] parts = targetPropertiesJson.split(",");
            List<String> result = new ArrayList<>();
            for (String part : parts) {
                String trimmed = part.trim()
                        .replaceAll("[\\[\\]\"]", "")  // 移除方括号和引号
                        .trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
            return result.isEmpty() ? Collections.singletonList("density") : result;
        }
    }
}
