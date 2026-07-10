package com.mdplatform.engine.repository;

import com.mdplatform.engine.model.ViscosityResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 粘度计算结果数据访问层，提供粘度子表的查询操作
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Repository
public interface ViscosityResultRepository extends JpaRepository<ViscosityResult, Long> {

    /**
     * 根据计算结果ID查询粘度专属数据
     *
     * @param resultId 计算结果ID（主表主键）
     * @return 粘度结果实体
     */
    ViscosityResult findByResultId(Long resultId);

    /**
     * 根据计算结果ID列表批量查询粘度专属数据
     *
     * @param resultIds 计算结果ID列表
     * @return 粘度结果实体列表
     */
    List<ViscosityResult> findByResultIdIn(List<Long> resultIds);
}
