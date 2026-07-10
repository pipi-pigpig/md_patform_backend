package com.mdplatform.engine;

import com.mdplatform.engine.dto.PipelineProgressDto;
import com.mdplatform.engine.dto.PipelineStepConstants;
import com.mdplatform.engine.model.SimulationJob;
import com.mdplatform.engine.repository.SimulationInputRepository;
import com.mdplatform.engine.repository.SimulationRepository;
import com.mdplatform.engine.service.PipelineService;
import com.mdplatform.engine.service.SimulationService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 全流程状态追踪集成测试
 *
 * <p>该测试类验证全流程管线的状态追踪和进度更新功能，包括：</p>
 * <ul>
 *   <li>进度更新格式的正确性验证</li>
 *   <li>进度百分比计算的准确性验证</li>
 *   <li>已完成任务进度强制为100%的验证</li>
 *   <li>失败状态下的错误处理和标记验证</li>
 *   <li>状态API返回信息的完整性验证</li>
 *   <li>进度递增序列的正确性验证</li>
 * </ul>
 *
 * <p>测试特点：</p>
 * <ul>
 *   <li>使用H2内存数据库进行真实数据库操作（非mock）</li>
 *   <li>使用Spring Boot完整上下文进行集成测试</li>
 *   <li>每个测试方法在独立事务中运行，测试后自动回滚</li>
 *   <li>由于@Modifying查询直接操作数据库绕过JPA一级缓存，
 *       使用findById+save模式更新数据以确保JPA缓存一致性，
 *       并在必要时刷新和清除EntityManager缓存</li>
 * </ul>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@SpringBootTest
@ActiveProfiles("test")
@Slf4j
class PipelineStatusTrackingTest {

    /** 全流程编排服务，提供进度查询功能 */
    @Autowired
    private PipelineService pipelineService;

    /** 模拟任务服务，提供任务状态管理功能 */
    @Autowired
    private SimulationService simulationService;

    /** 模拟任务仓库，用于直接操作数据库记录 */
    @Autowired
    private SimulationRepository simulationRepository;

    /** 模拟输入参数仓库，用于管理任务输入参数 */
    @Autowired
    private SimulationInputRepository simulationInputRepository;

    /** JPA实体管理器，用于刷新和清除一级缓存，确保@Modifying查询后的数据可见性 */
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 刷新并清除JPA一级缓存
     *
     * <p>由于simulationRepository的updateResultSummaryById()和updateStatusById()
     * 使用@Modifying注解直接执行UPDATE SQL语句，绕过了JPA的一级缓存。
     * 如果不清除缓存，后续的findById()调用会返回缓存中的旧数据，
     * 导致进度查询结果不正确。因此需要在每次@Modifying操作后调用此方法。</p>
     */
    private void flushAndClearCache() {
        entityManager.flush();
        entityManager.clear();
    }

    /**
     * 创建标准测试任务
     *
     * <p>构建并持久化一个SimulationJob对象，包含标准测试数据，
     * 用于后续的进度追踪测试。</p>
     *
     * @return 已持久化的SimulationJob对象
     */
    private SimulationJob createTestJob() {
        SimulationJob job = new SimulationJob();
        job.setJobName("状态追踪测试任务");
        job.setUserId(1L);
        job.setSystemId(0L);
        job.setSoftwareName("LAMMPS");
        job.setSoftwareVersion("2023");
        job.setStatus("PENDING");
        job.setTargetProperties("[\"density\"]");
        job.setHardwareUsed("CPU");
        job.setCpuCores("8");
        job.setHardwareEnvironment("CPU 8核");
        job.setJobRootPath("user_1/jobs/job_test/");
        job.setRandomSeed(12345);
        return simulationRepository.save(job);
    }

    /**
     * 通过findById+save模式更新resultSummary
     *
     * <p>使用JPA标准持久化路径更新resultSummary字段，确保更新经过JPA一级缓存，
     * 避免@Modifying查询导致的缓存不一致问题。</p>
     *
     * @param jobId          任务ID
     * @param resultSummary  结果摘要JSON字符串
     */
    private void updateResultSummary(Long jobId, String resultSummary) {
        SimulationJob job = simulationRepository.findById(jobId).orElseThrow();
        job.setResultSummary(resultSummary);
        simulationRepository.save(job);
    }

    /**
     * 通过findById+save模式更新任务状态和结束时间
     *
     * <p>使用JPA标准持久化路径更新status和endTime字段，确保更新经过JPA一级缓存，
     * 避免@Modifying查询导致的缓存不一致问题。</p>
     *
     * @param jobId   任务ID
     * @param status  目标状态
     * @param endTime 结束时间
     */
    private void updateJobStatus(Long jobId, String status, LocalDateTime endTime) {
        SimulationJob job = simulationRepository.findById(jobId).orElseThrow();
        job.setStatus(status);
        job.setEndTime(endTime);
        simulationRepository.save(job);
    }

    /**
     * 测试进度更新格式
     *
     * <p>验证通过updateResultSummaryById()更新进度JSON后，
     * pipelineService.getPipelineProgress()能够正确解析并返回进度信息。</p>
     *
     * <p>测试步骤：</p>
     * <ol>
     *   <li>创建测试任务</li>
     *   <li>通过updateResultSummaryById更新进度JSON（currentStep=3, completedSteps=[0,1,2]）</li>
     *   <li>刷新JPA缓存确保数据可见</li>
     *   <li>调用getPipelineProgress查询进度</li>
     *   <li>验证currentStep、stepName、completedSteps、totalSteps字段</li>
     * </ol>
     */
    @Test
    @DisplayName("测试进度更新格式 - 验证resultSummary JSON解析正确性")
    @Transactional
    void testProgressUpdateFormat() {
        log.info("开始测试进度更新格式...");

        // 创建测试任务
        SimulationJob job = createTestJob();
        Long jobId = job.getJobId();
        log.info("测试任务已创建: jobId={}", jobId);

        // 构建进度JSON并通过findById+save模式更新到resultSummary
        // 注意：使用findById+save而非updateResultSummaryById，因为H2的JSON列类型
        // 会导致@Modifying查询写入的值在读取时出现双重转义问题
        String progressJson = "{\"currentStep\":3,\"stepName\":\"Packmol分子堆积\",\"completedSteps\":[0,1,2],\"totalSteps\":11}";
        updateResultSummary(jobId, progressJson);
        log.info("进度JSON已更新: {}", progressJson);

        // 查询进度信息
        PipelineProgressDto progress = pipelineService.getPipelineProgress(jobId);

        // 验证进度字段
        assertNotNull(progress, "进度信息不应为null");
        assertEquals(3, progress.getCurrentStep(), "当前步骤应为3");
        assertEquals("Packmol分子堆积", progress.getStepName(), "步骤名称应为Packmol分子堆积");
        assertTrue(progress.getCompletedSteps().contains(0), "已完成步骤应包含0");
        assertTrue(progress.getCompletedSteps().contains(1), "已完成步骤应包含1");
        assertTrue(progress.getCompletedSteps().contains(2), "已完成步骤应包含2");
        assertEquals(11, progress.getTotalSteps(), "总步骤数应为11");

        log.info("进度更新格式测试通过");
    }

    /**
     * 测试进度百分比计算
     *
     * <p>验证PipelineStepConstants.calculateProgressPercent()方法的计算准确性，
     * 以及通过getPipelineProgress()返回的progressPercent字段。</p>
     *
     * <p>测试步骤：</p>
     * <ol>
     *   <li>直接测试calculateProgressPercent：0步→0%，5步→45%，11步→100%</li>
     *   <li>创建任务并设置所有11步完成</li>
     *   <li>刷新JPA缓存确保数据可见</li>
     *   <li>调用getPipelineProgress验证progressPercent为100</li>
     * </ol>
     */
    @Test
    @DisplayName("测试进度百分比计算 - 验证calculateProgressPercent准确性")
    @Transactional
    void testProgressPercentCalculation() {
        log.info("开始测试进度百分比计算...");

        // 直接测试calculateProgressPercent方法
        // 0步完成 → 0%
        assertEquals(0, PipelineStepConstants.calculateProgressPercent(0),
                "0步完成时进度应为0%");

        // 5步完成 → (5*100/11) = 45%
        assertEquals(45, PipelineStepConstants.calculateProgressPercent(5),
                "5步完成时进度应为45%");

        // 11步完成 → 100%
        assertEquals(100, PipelineStepConstants.calculateProgressPercent(11),
                "11步完成时进度应为100%");

        // 创建任务并设置所有步骤完成
        SimulationJob job = createTestJob();
        Long jobId = job.getJobId();

        // 构建所有步骤完成的进度JSON，使用findById+save模式更新
        List<Integer> allSteps = new ArrayList<>();
        for (int i = 0; i < PipelineStepConstants.TOTAL_STEPS; i++) {
            allSteps.add(i);
        }
        String allCompletedJson = "{\"currentStep\":10,\"stepName\":\"后处理分析\",\"completedSteps\":"
                + allSteps.toString() + ",\"totalSteps\":11}";
        updateResultSummary(jobId, allCompletedJson);

        // 查询进度并验证progressPercent
        PipelineProgressDto progress = pipelineService.getPipelineProgress(jobId);
        assertNotNull(progress, "进度信息不应为null");
        assertEquals(100, progress.getProgressPercent(),
                "所有步骤完成时进度应为100%");

        log.info("进度百分比计算测试通过");
    }

    /**
     * 测试已完成任务进度强制为100%
     *
     * <p>验证当任务状态为COMPLETED时，即使resultSummary中记录的是部分进度，
     * getPipelineProgress()也应强制返回100%的进度。</p>
     *
     * <p>测试步骤：</p>
     * <ol>
     *   <li>创建测试任务</li>
     *   <li>设置resultSummary为部分进度（5步完成）</li>
     *   <li>将任务状态更新为COMPLETED</li>
     *   <li>调用getPipelineProgress验证progressPercent为100</li>
     * </ol>
     */
    @Test
    @DisplayName("测试已完成任务进度强制100% - 验证COMPLETED状态覆盖部分进度")
    @Transactional
    void testProgressPercentWhenCompleted() {
        log.info("开始测试已完成任务进度强制100%...");

        // 创建测试任务
        SimulationJob job = createTestJob();
        Long jobId = job.getJobId();

        // 设置部分进度的resultSummary，使用findById+save模式更新
        String partialProgressJson = "{\"currentStep\":5,\"stepName\":\"分子模板调取\",\"completedSteps\":[0,1,2,3,4],\"totalSteps\":11}";
        updateResultSummary(jobId, partialProgressJson);

        // 将任务状态更新为COMPLETED，使用findById+save模式更新
        updateJobStatus(jobId, "COMPLETED", LocalDateTime.now());

        // 查询进度并验证progressPercent被强制为100%
        PipelineProgressDto progress = pipelineService.getPipelineProgress(jobId);
        assertNotNull(progress, "进度信息不应为null");
        assertEquals(100, progress.getProgressPercent(),
                "COMPLETED状态下进度应强制为100%");
        assertEquals("COMPLETED", progress.getStatus(),
                "任务状态应为COMPLETED");

        log.info("已完成任务进度强制100%测试通过");
    }

    /**
     * 测试失败状态下的错误处理
     *
     * <p>验证当任务状态为FAILED时，getPipelineProgress()能正确返回错误信息，
     * 并且步骤名称会附加"(失败)"标记。</p>
     *
     * <p>测试步骤：</p>
     * <ol>
     *   <li>创建测试任务</li>
     *   <li>设置resultSummary为部分进度</li>
     *   <li>设置errorMessage为"Docker服务不可用"</li>
     *   <li>将任务状态更新为FAILED</li>
     *   <li>调用getPipelineProgress验证status、errorMessage、stepName</li>
     * </ol>
     */
    @Test
    @DisplayName("测试失败状态错误处理 - 验证FAILED状态的错误信息和失败标记")
    @Transactional
    void testErrorHandlingWithFailedStatus() {
        log.info("开始测试失败状态错误处理...");

        // 创建测试任务
        SimulationJob job = createTestJob();
        Long jobId = job.getJobId();

        // 设置部分进度的resultSummary和FAILED状态，使用findById+save模式更新
        String partialProgressJson = "{\"currentStep\":6,\"stepName\":\"Moltemplate命令执行\",\"completedSteps\":[0,1,2,3,4,5],\"totalSteps\":11}";
        SimulationJob savedJob = simulationRepository.findById(jobId).orElseThrow();
        savedJob.setResultSummary(partialProgressJson);
        savedJob.setErrorMessage("Docker服务不可用");
        savedJob.setStatus("FAILED");
        savedJob.setEndTime(LocalDateTime.now());
        simulationRepository.save(savedJob);

        // 查询进度并验证失败状态下的字段
        PipelineProgressDto progress = pipelineService.getPipelineProgress(jobId);
        assertNotNull(progress, "进度信息不应为null");
        assertEquals("FAILED", progress.getStatus(),
                "任务状态应为FAILED");
        assertEquals("Docker服务不可用", progress.getErrorMessage(),
                "错误信息应为Docker服务不可用");
        assertTrue(progress.getStepName().contains("(失败)"),
                "步骤名称应包含(失败)标记，实际值: " + progress.getStepName());

        log.info("失败状态错误处理测试通过");
    }

    /**
     * 测试状态API返回完整信息
     *
     * <p>验证getPipelineProgress()返回的PipelineProgressDto中
     * 所有字段都被正确填充，包括jobId、userId、status、currentStep、
     * stepName、totalSteps、progressPercent等。</p>
     *
     * <p>测试步骤：</p>
     * <ol>
     *   <li>创建测试任务，状态设为MODELING</li>
     *   <li>设置resultSummary为currentStep=2</li>
     *   <li>调用getPipelineProgress查询进度</li>
     *   <li>验证所有PipelineProgressDto字段均正确填充</li>
     * </ol>
     */
    @Test
    @DisplayName("测试状态API返回完整信息 - 验证PipelineProgressDto所有字段")
    @Transactional
    void testStatusApiReturnsCorrectInfo() {
        log.info("开始测试状态API返回完整信息...");

        // 创建测试任务
        SimulationJob job = createTestJob();
        Long jobId = job.getJobId();

        // 更新任务状态为MODELING并设置进度，使用findById+save模式
        String progressJson = "{\"currentStep\":2,\"stepName\":\"盒子尺寸计算\",\"completedSteps\":[0,1],\"totalSteps\":11}";
        SimulationJob savedJob = simulationRepository.findById(jobId).orElseThrow();
        savedJob.setStatus("MODELING");
        savedJob.setStartTime(LocalDateTime.now());
        savedJob.setResultSummary(progressJson);
        simulationRepository.save(savedJob);

        // 查询进度信息
        PipelineProgressDto progress = pipelineService.getPipelineProgress(jobId);

        // 验证所有字段均正确填充
        assertNotNull(progress, "进度信息不应为null");
        assertEquals(jobId, progress.getJobId(), "jobId应匹配");
        assertEquals(1L, progress.getUserId(), "userId应为1");
        assertEquals("MODELING", progress.getStatus(), "状态应为MODELING");
        assertEquals(2, progress.getCurrentStep(), "当前步骤应为2");
        assertEquals("盒子尺寸计算", progress.getStepName(), "步骤名称应为盒子尺寸计算");
        assertEquals(11, progress.getTotalSteps(), "总步骤数应为11");
        assertNotNull(progress.getProgressPercent(), "进度百分比不应为null");
        assertTrue(progress.getProgressPercent() > 0, "进度百分比应大于0");
        assertNotNull(progress.getCreateTime(), "创建时间不应为null");

        log.info("状态API返回完整信息测试通过");
    }

    /**
     * 测试进度递增序列
     *
     * <p>验证通过顺序更新resultSummary模拟进度递增时，
     * getPipelineProgress()能正确反映每一步的进度变化。</p>
     *
     * <p>测试步骤：</p>
     * <ol>
     *   <li>创建测试任务</li>
     *   <li>依次更新步骤0到步骤10的进度</li>
     *   <li>每次更新后调用getPipelineProgress验证currentStep和completedSteps</li>
     * </ol>
     */
    @Test
    @DisplayName("测试进度递增序列 - 验证步骤0到10的顺序进度更新")
    @Transactional
    void testProgressIncrementSequence() {
        log.info("开始测试进度递增序列...");

        // 创建测试任务
        SimulationJob job = createTestJob();
        Long jobId = job.getJobId();

        // 依次模拟步骤0到步骤10的进度更新
        List<Integer> completedSteps = new ArrayList<>();
        for (int step = 0; step < PipelineStepConstants.TOTAL_STEPS; step++) {
            completedSteps.add(step);

            // 构建当前步骤的进度JSON
            String stepName = PipelineStepConstants.getStepName(step);
            String progressJson = "{\"currentStep\":" + step
                    + ",\"stepName\":\"" + stepName + "\""
                    + ",\"completedSteps\":" + completedSteps.toString()
                    + ",\"totalSteps\":" + PipelineStepConstants.TOTAL_STEPS + "}";

            // 使用findById+save模式更新，确保JPA缓存一致性
            SimulationJob currentJob = simulationRepository.findById(jobId).orElseThrow();
            currentJob.setResultSummary(progressJson);
            simulationRepository.save(currentJob);

            // 查询进度并验证
            PipelineProgressDto progress = pipelineService.getPipelineProgress(jobId);
            assertNotNull(progress, "步骤" + step + "的进度信息不应为null");
            assertEquals(step, progress.getCurrentStep(),
                    "步骤" + step + "的currentStep应为" + step);
            assertEquals(completedSteps.size(), progress.getCompletedSteps().size(),
                    "步骤" + step + "的completedSteps数量应为" + completedSteps.size());

            // 验证已完成步骤列表包含所有已完成的步骤编号
            for (int completedStep : completedSteps) {
                assertTrue(progress.getCompletedSteps().contains(completedStep),
                        "步骤" + step + "的completedSteps应包含" + completedStep);
            }

            log.info("步骤{}进度验证通过: currentStep={}, stepName={}, completedSteps={}",
                    step, progress.getCurrentStep(), progress.getStepName(),
                    progress.getCompletedSteps().size());
        }

        log.info("进度递增序列测试通过");
    }
}
