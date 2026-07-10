package com.mdplatform.engine.repository;

import com.mdplatform.engine.model.ConductivityResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 电导率计算结果数据访问层，提供电导率子表的查询操作
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Repository
public interface ConductivityResultRepository extends JpaRepository<ConductivityResult, Long> {

    /**
     * 根据计算结果ID查询电导率专属数据
     *
     * @param resultId 计算结果ID（主表主键）
     * @return 电导率结果实体
     */
    ConductivityResult findByResultId(Long resultId);

    /**
     * 根据计算结果ID列表批量查询电导率专属数据
     *
     * @param resultIds 计算结果ID列表
     * @return 电导率结果实体列表
     */
    List<ConductivityResult> findByResultIdIn(List<Long> resultIds);
}
