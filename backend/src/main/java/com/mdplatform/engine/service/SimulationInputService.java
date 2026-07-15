package com.mdplatform.engine.service;

import com.mdplatform.engine.model.SimulationInput;
import com.mdplatform.engine.repository.SimulationInputRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 模拟输入服务类
 *
 * <p>提供模拟输入（SimulationInput）的增删改查业务逻辑，
 * 支持按任务ID查询模拟输入配置。</p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SimulationInputService {

    private final SimulationInputRepository simulationInputRepository;

    /**
     * 获取所有模拟输入记录
     *
     * @return 模拟输入列表
     */
    public List<SimulationInput> getAllInputs() {
        return simulationInputRepository.findAll();
    }

    /**
     * 根据输入ID查询模拟输入
     *
     * @param id 输入ID
     * @return 包含模拟输入的Optional对象，若不存在则为空
     */
    public Optional<SimulationInput> getInputById(Long id) {
        return simulationInputRepository.findById(id);
    }

    /**
     * 根据任务ID查询模拟输入
     *
     * @param jobId 任务ID
     * @return 包含模拟输入的Optional对象，若不存在则为空
     */
    public Optional<SimulationInput> getInputByJobId(Long jobId) {
        return simulationInputRepository.findByJobId(jobId);
    }

    /**
     * 创建新的模拟输入记录
     *
     * @param input 待创建的模拟输入对象
     * @return 保存后的模拟输入对象
     */
    @Transactional
    public SimulationInput createInput(SimulationInput input) {
        SimulationInput savedInput = simulationInputRepository.save(input);
        log.info("Created simulation input with id: {}", savedInput.getInputId());
        return savedInput;
    }

    /**
     * 更新模拟输入信息
     *
     * @param id 输入ID
     * @param input 包含更新信息的模拟输入对象
     * @return 包含更新后输入的Optional对象，若输入不存在则为空
     */
    @Transactional
    public Optional<SimulationInput> updateInput(Long id, SimulationInput input) {
        return simulationInputRepository.findById(id).map(existingInput -> {
            input.setInputId(id);
            SimulationInput updatedInput = simulationInputRepository.save(input);
            log.info("Updated simulation input with id: {}", id);
            return updatedInput;
        });
    }

    /**
     * 删除模拟输入记录
     *
     * @param id 输入ID
     * @return true表示删除成功，false表示输入不存在
     */
    @Transactional
    public boolean deleteInput(Long id) {
        if (simulationInputRepository.existsById(id)) {
            simulationInputRepository.deleteById(id);
            log.info("Deleted simulation input with id: {}", id);
            return true;
        }
        return false;
    }
}