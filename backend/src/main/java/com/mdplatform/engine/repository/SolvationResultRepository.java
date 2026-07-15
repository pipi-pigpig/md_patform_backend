package com.mdplatform.engine.repository;

import com.mdplatform.engine.model.SolvationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 溶剂化结构计算结果数据访问层，提供溶剂化结构子表的查询操作
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Repository
public interface SolvationResultRepository extends JpaRepository<SolvationResult, Long> {

    /**
     * 根据计算结果ID查询溶剂化结构专属数据
     *
     * @param resultId 计算结果ID（主表主键）
     * @return 溶剂化结构结果实体
     */
    SolvationResult findByResultId(Long resultId);

    /**
     * 根据计算结果ID列表批量查询溶剂化结构专属数据
     *
     * @param resultIds 计算结果ID列表
     * @return 溶剂化结构结果实体列表
     */
    List<SolvationResult> findByResultIdIn(List<Long> resultIds);
}
