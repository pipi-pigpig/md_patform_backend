package com.mdplatform.engine.repository;

import com.mdplatform.engine.model.SimulationInput;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 模拟输入数据访问层，提供模拟输入参数的查询操作
 *
 * <p>功能：
 *     1. 根据任务ID查询模拟输入参数
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Repository
public interface SimulationInputRepository extends JpaRepository<SimulationInput, Long> {

    /**
     * 根据任务ID查询模拟输入参数
     *
     * <p>每个任务对应唯一的输入参数记录</p>
     *
     * @param jobId 模拟任务ID
     * @return 匹配的模拟输入参数，若不存在则返回空的Optional
     */
    Optional<SimulationInput> findByJobId(Long jobId);
}