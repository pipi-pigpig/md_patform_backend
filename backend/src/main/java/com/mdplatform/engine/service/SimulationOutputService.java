package com.mdplatform.engine.service;

import com.mdplatform.engine.model.SimulationRawOutput;
import com.mdplatform.engine.repository.SimulationOutputRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 模拟输出服务类
 *
 * <p>提供模拟原始输出（SimulationRawOutput）的增删改查业务逻辑，
 * 支持按任务ID查询模拟输出数据。</p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SimulationOutputService {

    private final SimulationOutputRepository simulationOutputRepository;

    /**
     * 获取所有模拟输出记录
     *
     * @return 模拟输出列表
     */
    public List<SimulationRawOutput> getAllOutputs() {
        return simulationOutputRepository.findAll();
    }

    /**
     * 根据输出ID查询模拟输出
     *
     * @param id 输出ID
     * @return 包含模拟输出的Optional对象，若不存在则为空
     */
    public Optional<SimulationRawOutput> getOutputById(Long id) {
        return simulationOutputRepository.findById(id);
    }

    /**
     * 根据任务ID查询模拟输出
     *
     * @param jobId 任务ID
     * @return 包含模拟输出的Optional对象，若不存在则为空
     */
    public Optional<SimulationRawOutput> getOutputByJobId(Long jobId) {
        return simulationOutputRepository.findByJobId(jobId);
    }

    /**
     * 创建新的模拟输出记录
     *
     * @param output 待创建的模拟输出对象
     * @return 保存后的模拟输出对象
     */
    @Transactional
    public SimulationRawOutput createOutput(SimulationRawOutput output) {
        SimulationRawOutput savedOutput = simulationOutputRepository.save(output);
        log.info("Created simulation output with id: {}", savedOutput.getOutputId());
        return savedOutput;
    }

    /**
     * 更新模拟输出信息
     *
     * @param id 输出ID
     * @param output 包含更新信息的模拟输出对象
     * @return 包含更新后输出的Optional对象，若输出不存在则为空
     */
    @Transactional
    public Optional<SimulationRawOutput> updateOutput(Long id, SimulationRawOutput output) {
        return simulationOutputRepository.findById(id).map(existingOutput -> {
            output.setOutputId(id);
            SimulationRawOutput updatedOutput = simulationOutputRepository.save(output);
            log.info("Updated simulation output with id: {}", id);
            return updatedOutput;
        });
    }

    /**
     * 删除模拟输出记录
     *
     * @param id 输出ID
     * @return true表示删除成功，false表示输出不存在
     */
    @Transactional
    public boolean deleteOutput(Long id) {
        if (simulationOutputRepository.existsById(id)) {
            simulationOutputRepository.deleteById(id);
            log.info("Deleted simulation output with id: {}", id);
            return true;
        }
        return false;
    }
}