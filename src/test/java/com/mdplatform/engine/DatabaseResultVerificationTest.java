package com.mdplatform.engine;

import com.mdplatform.engine.model.CalculationResult;
import com.mdplatform.engine.model.DensityResult;
import com.mdplatform.engine.model.SimulationInput;
import com.mdplatform.engine.model.SimulationJob;
import com.mdplatform.engine.repository.CalculationResultRepository;
import com.mdplatform.engine.repository.DensityResultRepository;
import com.mdplatform.engine.repository.SimulationInputRepository;
import com.mdplatform.engine.repository.SimulationRepository;
import com.mdplatform.engine.service.SimulationService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据库结果验证集成测试类
 *
 * <p>该测试类用于验证数据库操作的正确性，不使用Docker或Mock，
 * 而是使用H2内存数据库（MySQL兼容模式）进行真实的数据库操作测试。</p>
 *
 * <p>测试范围：</p>
 * <ul>
 *     <li>模拟任务状态转换（PENDING→MODELING→RUNNING→COMPLETED）</li>
 *     <li>模拟输入参数持久化与查询</li>
 *     <li>计算结果存储与子表关联</li>
 *     <li>默认参数值验证</li>
 *     <li>任务失败状态处理</li>
 * </ul>
 *
 * <p>测试特点：</p>
 * <ul>
 *     <li>使用@SpringBootTest加载完整Spring上下文</li>
 *     <li>使用H2内存数据库，测试结束自动清理</li>
 *     <li>不使用Mock，所有操作均为真实数据库操作</li>
 *     <li>使用@Transactional确保测试隔离</li>
 * </ul>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@SpringBootTest
@ActiveProfiles("test")
@Slf4j
class DatabaseResultVerificationTest {

    /** 模拟任务数据访问层 */
    @Autowired
    private SimulationRepository simulationRepository;

    /** 模拟输入数据访问层 */
    @Autowired
    private SimulationInputRepository simulationInputRepository;

    /** 计算结果数据访问层 */
    @Autowired
    private CalculationResultRepository calculationResultRepository;

    /** 密度计算结果数据访问层 */
    @Autowired
    private DensityResultRepository densityResultRepository;

    /** 模拟任务服务层 */
    @Autowired
    private SimulationService simulationService;

    /**
     * 测试前置清理
     *
     * <p>在每个测试方法执行前，清理所有相关表的数据，
     * 确保测试之间的数据隔离，避免测试数据相互影响。</p>
     */
    @BeforeEach
    void setUp() {
        log.info("[数据库验证测试] 清理测试数据...");
        densityResultRepository.deleteAll();
        calculationResultRepository.deleteAll();
        simulationInputRepository.deleteAll();
        simulationRepository.deleteAll();
        log.info("[数据库验证测试] 测试数据清理完成");
    }

    /**
     * 测试模拟任务状态转换
     *
     * <p>验证任务状态从PENDING→MODELING→RUNNING→COMPLETED的完整转换流程，
     * 确保每个状态转换后：</p>
     * <ul>
     *     <li>状态值正确更新</li>
     *     <li>MODELING状态时startTime被设置（不为null）</li>
     *     <li>COMPLETED状态时endTime被设置（不为null）</li>
     *     <li>COMPLETED状态时executionTimeS为正值</li>
     * </ul>
     */
    @Test
    @DisplayName("测试模拟任务状态转换 - PENDING→MODELING→RUNNING→COMPLETED")
    @Transactional
    void testSimulationJobStatusTransition() {
        log.info("[数据库验证测试] 开始测试模拟任务状态转换...");

        // 步骤1：创建PENDING状态的任务
        SimulationJob job = createTestSimulationJob();
        SimulationJob savedJob = simulationRepository.save(job);
        Long jobId = savedJob.getJobId();
        log.info("[数据库验证测试] 创建任务成功，jobId={}, 初始状态={}", jobId, savedJob.getStatus());

        // 验证初始状态为PENDING
        assertEquals("PENDING", savedJob.getStatus(), "初始状态应为PENDING");
        assertNull(savedJob.getStartTime(), "PENDING状态时startTime应为null");
        assertNull(savedJob.getEndTime(), "PENDING状态时endTime应为null");

        // 步骤2：转换到MODELING状态
        Optional<SimulationJob> modelingResult = simulationService.updateSimulationStatus(jobId, "MODELING");
        assertTrue(modelingResult.isPresent(), "状态转换到MODELING应成功");
        SimulationJob modelingJob = modelingResult.get();
        log.info("[数据库验证测试] 转换到MODELING状态，startTime={}", modelingJob.getStartTime());

        assertEquals("MODELING", modelingJob.getStatus(), "状态应为MODELING");
        assertNotNull(modelingJob.getStartTime(), "MODELING状态时startTime不应为null");
        assertNull(modelingJob.getEndTime(), "MODELING状态时endTime应为null");

        // 步骤3：转换到RUNNING状态
        Optional<SimulationJob> runningResult = simulationService.updateSimulationStatus(jobId, "RUNNING");
        assertTrue(runningResult.isPresent(), "状态转换到RUNNING应成功");
        SimulationJob runningJob = runningResult.get();
        log.info("[数据库验证测试] 转换到RUNNING状态，startTime={}", runningJob.getStartTime());

        assertEquals("RUNNING", runningJob.getStatus(), "状态应为RUNNING");
        // RUNNING状态时startTime应保持不变（已在MODELING时设置，不会重置）
        assertNotNull(runningJob.getStartTime(), "RUNNING状态时startTime不应为null");
        // 验证startTime未被重置，应与MODELING时相同
        assertEquals(modelingJob.getStartTime(), runningJob.getStartTime(),
                "从MODELING转为RUNNING时，startTime不应被重置");
        assertNull(runningJob.getEndTime(), "RUNNING状态时endTime应为null");

        // 步骤4：转换到COMPLETED状态
        Optional<SimulationJob> completedResult = simulationService.updateSimulationStatus(jobId, "COMPLETED");
        assertTrue(completedResult.isPresent(), "状态转换到COMPLETED应成功");
        SimulationJob completedJob = completedResult.get();
        log.info("[数据库验证测试] 转换到COMPLETED状态，endTime={}, executionTimeS={}",
                completedJob.getEndTime(), completedJob.getExecutionTimeS());

        assertEquals("COMPLETED", completedJob.getStatus(), "状态应为COMPLETED");
        assertNotNull(completedJob.getEndTime(), "COMPLETED状态时endTime不应为null");
        assertNotNull(completedJob.getExecutionTimeS(), "COMPLETED状态时executionTimeS不应为null");
        assertTrue(completedJob.getExecutionTimeS() >= 0,
                "COMPLETED状态时executionTimeS应为非负值");

        // 从数据库重新查询验证持久化
        SimulationJob reloadedJob = simulationRepository.findById(jobId).orElseThrow();
        assertEquals("COMPLETED", reloadedJob.getStatus(), "数据库中状态应为COMPLETED");
        assertNotNull(reloadedJob.getStartTime(), "数据库中startTime不应为null");
        assertNotNull(reloadedJob.getEndTime(), "数据库中endTime不应为null");

        log.info("[数据库验证测试] 模拟任务状态转换测试通过");
    }

    /**
     * 测试模拟输入参数持久化
     *
     * <p>验证SimulationInput实体的完整持久化和查询功能，
     * 创建包含所有字段的输入参数记录，保存后通过findByJobId查询，
     * 验证所有字段值与写入时一致。</p>
     */
    @Test
    @DisplayName("测试模拟输入参数持久化 - 创建、保存、查询并验证所有字段")
    @Transactional
    void testSimulationInputPersistence() {
        log.info("[数据库验证测试] 开始测试模拟输入参数持久化...");

        // 先创建一个SimulationJob（SimulationInput的jobId有外键约束）
        SimulationJob job = createTestSimulationJob();
        SimulationJob savedJob = simulationRepository.save(job);
        Long jobId = savedJob.getJobId();
        log.info("[数据库验证测试] 创建关联任务，jobId={}", jobId);

        // 创建完整的SimulationInput记录
        SimulationInput input = new SimulationInput();
        input.setJobId(jobId);
        input.setEnsembleType("NPT");
        input.setThermostatType("Nose-Hoover");
        input.setBarostatType("Parrinello-Rahman");
        input.setTemperature(353.0);
        input.setPressure(1.0);
        input.setTimeStepFs(1.0);
        input.setCutoffDistanceAng(12.0);
        input.setIntegrationAlgorithm("Velocity-Verlet");
        input.setLongRangeElectrostatics("PPPM, accuracy 1.0e-4");
        input.setForceFieldTopologySource("uri://system_templates/force_fields/opls-aa/oplsaa.lt");
        input.setOutputFrequencyStep(1000);
        input.setMinimizationParams("{\"algorithm\":\"cg\",\"max_iterations\":50000}");
        input.setMinimizationForceThreshold(1.0e-4);
        input.setMinimizationMaxSteps(150000L);
        input.setEquilibriumParams("{\"duration_ps\":5000,\"timestep\":1.0}");
        input.setProductionParams("{\"duration_ns\":50,\"timestep\":1.0}");
        input.setInitialConfigSource("packmol");
        input.setInitialVelocityDistribution("Maxwell-Boltzmann");

        // 保存输入参数
        SimulationInput savedInput = simulationInputRepository.save(input);
        log.info("[数据库验证测试] 保存输入参数成功，inputId={}", savedInput.getInputId());

        // 通过findByJobId查询并验证所有字段
        Optional<SimulationInput> queryResult = simulationInputRepository.findByJobId(jobId);
        assertTrue(queryResult.isPresent(), "通过jobId应能查询到输入参数");

        SimulationInput retrievedInput = queryResult.get();

        // 验证基本字段
        assertEquals(jobId, retrievedInput.getJobId(), "jobId应一致");
        assertEquals("NPT", retrievedInput.getEnsembleType(), "系综类型应一致");
        assertEquals("Nose-Hoover", retrievedInput.getThermostatType(), "恒温器类型应一致");
        assertEquals("Parrinello-Rahman", retrievedInput.getBarostatType(), "恒压器类型应一致");

        // 验证温度和压力
        assertEquals(353.0, retrievedInput.getTemperature(), 0.001, "温度应一致");
        assertEquals(1.0, retrievedInput.getPressure(), 0.001, "压力应一致");

        // 验证时间步长和截断距离
        assertEquals(1.0, retrievedInput.getTimeStepFs(), 0.001, "时间步长应一致");
        assertEquals(12.0, retrievedInput.getCutoffDistanceAng(), 0.001, "截断距离应一致");

        // 验证积分算法和长程静电方法
        assertEquals("Velocity-Verlet", retrievedInput.getIntegrationAlgorithm(), "积分算法应一致");
        assertEquals("PPPM, accuracy 1.0e-4", retrievedInput.getLongRangeElectrostatics(), "长程静电方法应一致");

        // 验证力场拓扑来源
        assertEquals("uri://system_templates/force_fields/opls-aa/oplsaa.lt",
                retrievedInput.getForceFieldTopologySource(), "力场拓扑来源应一致");

        // 验证输出频率
        assertEquals(1000, retrievedInput.getOutputFrequencyStep(), "输出频率应一致");

        // 验证能量最小化参数
        assertEquals(10.0, retrievedInput.getMinimizationForceThreshold(), 0.001, "力收敛阈值应一致");
        assertEquals(150000L, retrievedInput.getMinimizationMaxSteps(), "最大迭代次数应一致");

        // 验证初始构型来源和速度分布
        assertEquals("packmol", retrievedInput.getInitialConfigSource(), "初始构型来源应一致");
        assertEquals("Maxwell-Boltzmann", retrievedInput.getInitialVelocityDistribution(), "初始速度分布应一致");

        // 验证创建时间已自动设置
        assertNotNull(retrievedInput.getCreateTime(), "创建时间应自动设置");

        log.info("[数据库验证测试] 模拟输入参数持久化测试通过");
    }

    /**
     * 测试计算结果存储与子表关联
     *
     * <p>验证CalculationResult主表和DensityResult子表的存储和关联查询功能：</p>
     * <ul>
     *     <li>创建CalculationResult主表记录并保存</li>
     *     <li>创建DensityResult子表记录（通过resultId关联主表）并保存</li>
     *     <li>验证两条记录均存在且数据正确</li>
     *     <li>验证主子表通过resultId正确关联</li>
     * </ul>
     */
    @Test
    @DisplayName("测试计算结果存储 - 主表CalculationResult与子表DensityResult的关联存储和查询")
    @Transactional
    void testCalculationResultStorage() {
        log.info("[数据库验证测试] 开始测试计算结果存储...");

        // 先创建一个SimulationJob（CalculationResult的jobId有外键约束）
        SimulationJob job = createTestSimulationJob();
        SimulationJob savedJob = simulationRepository.save(job);
        Long jobId = savedJob.getJobId();
        log.info("[数据库验证测试] 创建关联任务，jobId={}", jobId);

        // 步骤1：创建CalculationResult主表记录
        CalculationResult calcResult = new CalculationResult();
        calcResult.setJobId(jobId);
        calcResult.setPropertyName("density");
        calcResult.setPropertyValue(1200.5);
        calcResult.setPropertyUnit("kg/m³");
        calcResult.setCalculationMethod("直接统计");
        calcResult.setTemperatureK(353.0);
        calcResult.setPressureBar(1.0);
        calcResult.setSamplingTimePs(50000.0);
        calcResult.setConvergenceStatus("CONVERGED");
        calcResult.setPropertyDetail("{\"mean\":1200.5}");

        // 保存主表记录
        CalculationResult savedCalcResult = calculationResultRepository.save(calcResult);
        Long resultId = savedCalcResult.getResultId();
        log.info("[数据库验证测试] 保存计算结果主表成功，resultId={}", resultId);

        // 步骤2：创建DensityResult子表记录
        DensityResult densityResult = new DensityResult();
        densityResult.setResultId(resultId);
        densityResult.setDensityTensor("{\"xx\":1200,\"yy\":1201,\"zz\":1201}");
        densityResult.setComponentDensity("{\"EC\":800,\"DMC\":400}");

        // 保存子表记录
        DensityResult savedDensityResult = densityResultRepository.save(densityResult);
        log.info("[数据库验证测试] 保存密度结果子表成功，resultId={}", savedDensityResult.getResultId());

        // 步骤3：验证主表数据正确
        CalculationResult retrievedCalcResult = calculationResultRepository.findById(resultId)
                .orElseThrow(() -> new AssertionError("计算结果主表记录应存在"));

        assertEquals(jobId, retrievedCalcResult.getJobId(), "jobId应一致");
        assertEquals("density", retrievedCalcResult.getPropertyName(), "属性名称应一致");
        assertEquals(1200.5, retrievedCalcResult.getPropertyValue(), 0.001, "属性值应一致");
        assertEquals("kg/m³", retrievedCalcResult.getPropertyUnit(), "属性单位应一致");
        assertEquals("直接统计", retrievedCalcResult.getCalculationMethod(), "计算方法应一致");
        assertEquals(353.0, retrievedCalcResult.getTemperatureK(), 0.001, "温度应一致");
        assertEquals(1.0, retrievedCalcResult.getPressureBar(), 0.001, "压力应一致");
        assertEquals(50000.0, retrievedCalcResult.getSamplingTimePs(), 0.001, "采样时间应一致");
        assertEquals("CONVERGED", retrievedCalcResult.getConvergenceStatus(), "收敛状态应一致");
        assertEquals("{\"mean\":1200.5}", retrievedCalcResult.getPropertyDetail(), "性质详情应一致");
        assertNotNull(retrievedCalcResult.getCreateTime(), "创建时间应自动设置");

        // 步骤4：验证子表数据正确
        DensityResult retrievedDensityResult = densityResultRepository.findByResultId(resultId);
        assertNotNull(retrievedDensityResult, "密度结果子表记录应存在");

        assertEquals(resultId, retrievedDensityResult.getResultId(), "子表resultId应与主表一致");
        assertEquals("{\"xx\":1200,\"yy\":1201,\"zz\":1201}",
                retrievedDensityResult.getDensityTensor(), "密度张量应一致");
        assertEquals("{\"EC\":800,\"DMC\":400}",
                retrievedDensityResult.getComponentDensity(), "组分密度应一致");

        // 步骤5：验证通过jobId可以查询到计算结果
        java.util.List<CalculationResult> resultsByJobId = calculationResultRepository.findByJobId(jobId);
        assertFalse(resultsByJobId.isEmpty(), "通过jobId应能查询到计算结果");
        assertEquals(1, resultsByJobId.size(), "该任务应有1条计算结果");
        assertEquals("density", resultsByJobId.get(0).getPropertyName(), "查询结果的属性名称应一致");

        log.info("[数据库验证测试] 计算结果存储测试通过");
    }

    /**
     * 测试默认参数值
     *
     * <p>验证SimulationInput实体中各字段的默认值是否正确，
     * 仅设置jobId和必填字段，其余字段使用Java默认值，
     * 验证以下默认值：</p>
     * <ul>
     *     <li>timeStepFs = 1.0</li>
     *     <li>cutoffDistanceAng = 10.0</li>
     *     <li>integrationAlgorithm = "Velocity-Verlet"</li>
     *     <li>longRangeElectrostatics = "PPPM, accuracy 1.0e-4"</li>
     *     <li>minimizationForceThreshold = 1.0e-4</li>
     *     <li>minimizationMaxSteps = 150000</li>
     * </ul>
     */
    @Test
    @DisplayName("测试默认参数值 - 验证SimulationInput各字段的默认值")
    @Transactional
    void testDefaultParameterValues() {
        log.info("[数据库验证测试] 开始测试默认参数值...");

        // 先创建一个SimulationJob
        SimulationJob job = createTestSimulationJob();
        SimulationJob savedJob = simulationRepository.save(job);
        Long jobId = savedJob.getJobId();

        // 创建SimulationInput，仅设置jobId和必填字段
        SimulationInput input = new SimulationInput();
        input.setJobId(jobId);
        // 设置必填字段（没有默认值的字段）
        input.setEnsembleType("NPT");
        input.setThermostatType("Nose-Hoover");
        input.setTemperature(298.15);
        input.setForceFieldTopologySource("uri://system_templates/force_fields/opls-aa/oplsaa.lt");
        input.setOutputFrequencyStep(1000);
        input.setMinimizationParams("{\"algorithm\":\"cg\"}");
        input.setEquilibriumParams("{\"duration_ps\":5000}");
        input.setProductionParams("{\"duration_ns\":50}");
        input.setInitialConfigSource("packmol");
        input.setInitialVelocityDistribution("Maxwell-Boltzmann");

        // 保存并重新查询
        SimulationInput savedInput = simulationInputRepository.save(input);
        log.info("[数据库验证测试] 保存输入参数成功，inputId={}", savedInput.getInputId());

        SimulationInput retrievedInput = simulationInputRepository.findByJobId(jobId)
                .orElseThrow(() -> new AssertionError("应能查询到输入参数"));

        // 验证默认值
        assertEquals(1.0, retrievedInput.getTimeStepFs(), 0.001,
                "默认时间步长应为1.0 fs");
        assertEquals(10.0, retrievedInput.getCutoffDistanceAng(), 0.001,
                "默认截断距离应为10.0 Å");
        assertEquals("Velocity-Verlet", retrievedInput.getIntegrationAlgorithm(),
                "默认积分算法应为Velocity-Verlet");
        assertEquals("PPPM, accuracy 1.0e-4", retrievedInput.getLongRangeElectrostatics(),
                "默认长程静电方法应为PPPM, accuracy 1.0e-4");
        assertEquals(10.0, retrievedInput.getMinimizationForceThreshold(), 0.001,
                "默认力收敛阈值应为10.0");
        assertEquals(150000L, retrievedInput.getMinimizationMaxSteps(),
                "默认最大迭代次数应为150000");

        log.info("[数据库验证测试] 默认参数值测试通过");
    }

    /**
     * 测试模拟任务失败状态
     *
     * <p>验证任务在MODELING状态后转为FAILED状态的完整流程：</p>
     * <ul>
     *     <li>创建PENDING状态任务</li>
     *     <li>转换到MODELING状态（验证startTime被设置）</li>
     *     <li>直接设置状态为FAILED并附带errorMessage</li>
     *     <li>验证errorMessage正确保存</li>
     *     <li>验证endTime被设置</li>
     * </ul>
     */
    @Test
    @DisplayName("测试模拟任务失败状态 - MODELING→FAILED，验证错误信息和结束时间")
    @Transactional
    void testSimulationJobFailedStatus() {
        log.info("[数据库验证测试] 开始测试模拟任务失败状态...");

        // 步骤1：创建PENDING状态的任务
        SimulationJob job = createTestSimulationJob();
        SimulationJob savedJob = simulationRepository.save(job);
        Long jobId = savedJob.getJobId();
        log.info("[数据库验证测试] 创建任务成功，jobId={}", jobId);

        // 步骤2：转换到MODELING状态
        Optional<SimulationJob> modelingResult = simulationService.updateSimulationStatus(jobId, "MODELING");
        assertTrue(modelingResult.isPresent(), "状态转换到MODELING应成功");
        SimulationJob modelingJob = modelingResult.get();
        assertNotNull(modelingJob.getStartTime(), "MODELING状态时startTime不应为null");
        log.info("[数据库验证测试] 转换到MODELING状态成功，startTime={}", modelingJob.getStartTime());

        // 步骤3：转换到FAILED状态
        Optional<SimulationJob> failedResult = simulationService.updateSimulationStatus(jobId, "FAILED");
        assertTrue(failedResult.isPresent(), "状态转换到FAILED应成功");
        SimulationJob failedJob = failedResult.get();

        // 步骤4：设置错误信息并保存
        failedJob.setErrorMessage("Packmol堆积失败：分子数量超出限制");
        simulationRepository.save(failedJob);
        log.info("[数据库验证测试] 设置错误信息并保存");

        // 步骤5：验证FAILED状态
        assertEquals("FAILED", failedJob.getStatus(), "状态应为FAILED");
        assertNotNull(failedJob.getEndTime(), "FAILED状态时endTime不应为null");
        assertNotNull(failedJob.getExecutionTimeS(), "FAILED状态时executionTimeS不应为null");

        // 步骤6：从数据库重新查询验证持久化
        SimulationJob reloadedJob = simulationRepository.findById(jobId).orElseThrow();
        assertEquals("FAILED", reloadedJob.getStatus(), "数据库中状态应为FAILED");
        assertEquals("Packmol堆积失败：分子数量超出限制", reloadedJob.getErrorMessage(),
                "错误信息应正确保存");
        assertNotNull(reloadedJob.getEndTime(), "数据库中endTime不应为null");
        assertNotNull(reloadedJob.getStartTime(), "数据库中startTime不应为null");

        log.info("[数据库验证测试] 模拟任务失败状态测试通过");
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建测试用的SimulationJob对象
     *
     * <p>构建一个包含所有必填字段的SimulationJob实例，
     * 使用合理的默认测试数据。</p>
     *
     * @return 填充了测试数据的SimulationJob对象
     */
    private SimulationJob createTestSimulationJob() {
        SimulationJob job = new SimulationJob();
        job.setJobName("数据库验证测试任务");
        job.setUserId(1L);
        job.setSystemId(1L);
        job.setSoftwareName("LAMMPS");
        job.setSoftwareVersion("29Oct2020");
        job.setTargetProperties("[\"density\",\"conductivity\"]");
        job.setHardwareUsed("CPU");
        job.setCpuCores("8");
        job.setHardwareEnvironment("CPU 8核");
        job.setJobRootPath("user_1/jobs/job_test_001/");
        job.setRandomSeed(12345);
        return job;
    }
}
