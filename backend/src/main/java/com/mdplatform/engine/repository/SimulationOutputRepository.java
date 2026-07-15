package com.mdplatform.engine.repository;

import com.mdplatform.engine.model.SimulationRawOutput;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 模拟输出数据访问层，提供模拟原始输出的查询操作
 *
 * <p>功能：
 *     1. 根据任务ID查询模拟原始输出数据
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Repository
public interface SimulationOutputRepository extends JpaRepository<SimulationRawOutput, Long> {

    /**
     * 根据任务ID查询模拟原始输出数据
     *
     * <p>每个任务对应唯一的原始输出记录，包含轨迹文件路径、日志文件路径等信息</p>
     *
     * @param jobId 模拟任务ID
     * @return 匹配的模拟原始输出数据，若不存在则返回空的Optional
     */
    Optional<SimulationRawOutput> findByJobId(Long jobId);
}