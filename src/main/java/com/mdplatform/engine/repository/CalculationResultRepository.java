package com.mdplatform.engine.repository;

import com.mdplatform.engine.model.CalculationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 计算结果数据访问层，提供计算结果的CRUD操作和自定义查询
 *
 * <p>功能：
 *     1. 根据任务ID查询计算结果
 *     2. 根据属性名称查询计算结果
 *     3. 精确查询指定任务和属性的计算结果
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Repository
public interface CalculationResultRepository extends JpaRepository<CalculationResult, Long> {

    /**
     * 根据任务ID查询所有计算结果
     *
     * @param jobId 模拟任务ID
     * @return 该任务的所有计算结果列表
     */
    List<CalculationResult> findByJobId(Long jobId);

    /**
     * 根据任务ID查询计算结果，按创建时间降序排列
     *
     * @param jobId 模拟任务ID
     * @return 按创建时间降序排列的计算结果列表
     */
    List<CalculationResult> findByJobIdOrderByCreateTimeDesc(Long jobId);

    /**
     * 根据属性名称查询计算结果（如density、conductivity、viscosity等）
     *
     * @param propertyName 属性名称
     * @return 指定属性的所有计算结果列表
     */
    List<CalculationResult> findByPropertyName(String propertyName);

    /**
     * 根据任务ID和属性名称精确查询计算结果
     *
     * <p>每个任务的每个属性最多只有一条计算结果记录</p>
     *
     * @param jobId        模拟任务ID
     * @param propertyName 属性名称
     * @return 匹配的计算结果，若不存在则返回空的Optional
     */
    @Query("SELECT c FROM CalculationResult c WHERE c.jobId = :jobId AND c.propertyName = :propertyName")
    Optional<CalculationResult> findByJobIdAndPropertyName(@Param("jobId") Long jobId, @Param("propertyName") String propertyName);
}