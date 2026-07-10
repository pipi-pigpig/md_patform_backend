package com.mdplatform.engine.repository;

import com.mdplatform.engine.model.ElectrolyteSystem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 电解液系统数据访问层，提供电解液配方的CRUD操作和自定义查询
 *
 * <p>功能：
 *     1. 根据用户ID查询电解液配方
 *     2. 查询公开的配方模板
 *     3. 按关键字搜索电解液配方
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Repository
public interface SystemRepository extends JpaRepository<ElectrolyteSystem, Long> {

    /**
     * 查询所有电解液系统，按创建时间降序排列
     *
     * @return 按创建时间降序排列的电解液系统列表
     */
    List<ElectrolyteSystem> findAllByOrderByCreateTimeDesc();

    /**
     * 根据用户ID查询电解液系统列表
     *
     * @param userId 用户ID
     * @return 该用户创建的所有电解液系统列表
     */
    List<ElectrolyteSystem> findByUserId(Long userId);

    /**
     * 查询所有公开的电解液配方模板
     *
     * <p>公开模板的isPublicTemplate字段为true，所有用户可见可用</p>
     *
     * @return 公开的电解液配方模板列表
     */
    List<ElectrolyteSystem> findByIsPublicTemplateTrue();

    /**
     * 按关键字搜索电解液系统（模糊匹配系统名称或任务描述）
     *
     * @param keyword 搜索关键字
     * @return 匹配关键字的电解液系统列表
     */
    @Query("SELECT e FROM ElectrolyteSystem e WHERE e.systemName LIKE %:keyword% OR e.taskDescription LIKE %:keyword%")
    List<ElectrolyteSystem> searchByKeyword(@Param("keyword") String keyword);
}