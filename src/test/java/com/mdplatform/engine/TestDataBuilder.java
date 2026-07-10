package com.mdplatform.engine;

import com.mdplatform.engine.dto.*;

import java.util.Arrays;
import java.util.List;

/**
 * 端到端测试数据构建工具类
 *
 * <p>功能：
 *     1. 提供标准电解液配方的PipelineSubmitRequest构建方法
 *     2. 提供各种测试场景的请求对象构建方法
 *     3. 统一管理测试数据，避免在各测试类中重复定义
 * </p>
 *
 * <p>标准测试配方：EC:DMC = 3:7（摩尔比），1M LiPF6，温度353K</p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
public final class TestDataBuilder {

    /** 默认测试用户ID */
    public static final Long DEFAULT_TEST_USER_ID = 1L;

    /** 默认测试温度(K) */
    public static final Double DEFAULT_TEST_TEMPERATURE = 353.0;

    /** 默认目标计算性质 */
    public static final List<String> DEFAULT_TARGET_PROPERTIES = Arrays.asList("density", "conductivity");

    /** 私有构造方法，防止实例化工具类 */
    private TestDataBuilder() {
    }

    /**
     * 构建标准电解液配方的PipelineSubmitRequest
     *
     * <p>配方：EC:DMC = 3:7（摩尔比），1M LiPF6，温度353K，
     * 目标性质：density, conductivity</p>
     *
     * @return 标准测试用的PipelineSubmitRequest对象
     */
    public static PipelineSubmitRequest buildStandardPipelineRequest() {
        PipelineSubmitRequest request = new PipelineSubmitRequest();
        request.setFormula(buildStandardFormula());
        request.setTargetProperties(DEFAULT_TARGET_PROPERTIES);
        request.setJobName("E2E测试任务-标准电解液配方");
        request.setTemperature(DEFAULT_TEST_TEMPERATURE);
        request.setHardwareUsed("CPU");
        return request;
    }

    /**
     * 构建仅计算密度的PipelineSubmitRequest
     *
     * <p>配方：EC:DMC = 3:7（摩尔比），1M LiPF6，温度300K，
     * 目标性质：density</p>
     *
     * @return 仅计算密度的PipelineSubmitRequest对象
     */
    public static PipelineSubmitRequest buildDensityOnlyRequest() {
        PipelineSubmitRequest request = new PipelineSubmitRequest();
        request.setFormula(buildStandardFormula());
        request.setTargetProperties(Arrays.asList("density"));
        request.setJobName("E2E测试任务-仅密度");
        request.setTemperature(300.0);
        request.setHardwareUsed("CPU");
        return request;
    }

    /**
     * 构建标准电解液配方FormulaRequest
     *
     * <p>配方：EC:DMC = 3:7（摩尔比），1M LiPF6，温度353K</p>
     *
     * @return 标准测试用的FormulaRequest对象
     */
    public static FormulaRequest buildStandardFormula() {
        FormulaRequest formula = new FormulaRequest();

        // 溶剂：EC:DMC = 3:7（摩尔比）
        SolventInfo ec = new SolventInfo();
        ec.setName("EC");
        ec.setMoleFraction(0.3);

        SolventInfo dmc = new SolventInfo();
        dmc.setName("DMC");
        dmc.setMoleFraction(0.7);

        formula.setSolventInfo(Arrays.asList(ec, dmc));

        // 锂盐：LiPF6，浓度1M
        SaltInfo salt = new SaltInfo();
        salt.setCation("Li");
        salt.setAnion("PF6");
        salt.setConcentration(1.0);
        formula.setSaltInfo(salt);

        // 盒子尺寸：使用较小盒子（20Å）以降低系统原子数，避免邻居列表溢出
        // 50Å盒子会产生~12114原子和~73M邻居，导致LAMMPS初始化阶段挂死
        // 20Å盒子约产生~770原子，可在合理时间内完成全流程测试
        BoxSizeRequest boxSize = new BoxSizeRequest();
        boxSize.setX(20.0);
        boxSize.setY(20.0);
        boxSize.setZ(20.0);
        boxSize.setAuto(true);
        formula.setBoxSize(boxSize);

        // 温度
        formula.setTemperature(DEFAULT_TEST_TEMPERATURE);

        return formula;
    }
}
