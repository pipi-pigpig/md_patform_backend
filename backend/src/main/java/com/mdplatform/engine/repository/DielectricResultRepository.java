package com.mdplatform.engine.repository;

import com.mdplatform.engine.model.DielectricResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 介电常数计算结果数据访问层，提供介电常数子表的查询操作
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Repository
public interface DielectricResultRepository extends JpaRepository<DielectricResult, Long> {

    /**
     * 根据计算结果ID查询介电常数专属数据
     *
     * @param resultId 计算结果ID（主表主键）
     * @return 介电常数结果实体
     */
    DielectricResult findByResultId(Long resultId);

    /**
     * 根据计算结果ID列表批量查询介电常数专属数据
     *
     * @param resultIds 计算结果ID列表
     * @return 介电常数结果实体列表
     */
    List<DielectricResult> findByResultIdIn(List<Long> resultIds);
}
