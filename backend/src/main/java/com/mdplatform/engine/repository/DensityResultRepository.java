package com.mdplatform.engine.repository;

import com.mdplatform.engine.model.DensityResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 密度计算结果数据访问层，提供密度子表的查询操作
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Repository
public interface DensityResultRepository extends JpaRepository<DensityResult, Long> {

    /**
     * 根据计算结果ID查询密度专属数据
     *
     * @param resultId 计算结果ID（主表主键）
     * @return 密度结果实体
     */
    DensityResult findByResultId(Long resultId);

    /**
     * 根据计算结果ID列表批量查询密度专属数据
     *
     * @param resultIds 计算结果ID列表
     * @return 密度结果实体列表
     */
    List<DensityResult> findByResultIdIn(List<Long> resultIds);
}
