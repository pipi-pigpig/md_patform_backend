package com.mdplatform.engine.repository;

import com.mdplatform.engine.model.SimulationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 模拟任务数据访问层，提供模拟任务的CRUD操作和自定义查询
 *
 * <p>功能：
 *     1. 基于用户ID、系统ID、状态等条件的模拟任务查询
 *     2. 模拟任务状态统计（按用户分组、全局分组）
 *     3. 关联查询模拟任务与电解液配方的任务描述
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Repository
public interface SimulationRepository extends JpaRepository<SimulationJob, Long> {

    /**
     * 查询所有模拟任务，按创建时间降序排列
     *
     * @return 按创建时间降序排列的模拟任务列表
     */
    List<SimulationJob> findAllByOrderByCreateTimeDesc();

    /**
     * 根据用户ID查询模拟任务列表
     *
     * @param userId 用户ID
     * @return 该用户创建的所有模拟任务列表
     */
    List<SimulationJob> findByUserId(Long userId);

    /**
     * 根据电解液系统ID查询关联的模拟任务列表
     *
     * @param systemId 电解液系统ID
     * @return 关联该系统的所有模拟任务列表
     */
    List<SimulationJob> findBySystemId(Long systemId);

    /**
     * 根据任务状态查询模拟任务列表
     *
     * @param status 任务状态（如PENDING、RUNNING、COMPLETED、FAILED等）
     * @return 指定状态的所有模拟任务列表
     */
    List<SimulationJob> findByStatus(String status);

    /**
     * 根据软件名称查询模拟任务列表
     *
     * @param softwareName 软件名称（如LAMMPS、GROMACS等）
     * @return 使用指定软件的所有模拟任务列表
     */
    List<SimulationJob> findBySoftwareName(String softwareName);

    /**
     * 统计指定状态的模拟任务数量
     *
     * @param status 任务状态
     * @return 该状态的模拟任务总数
     */
    @Query("SELECT COUNT(s) FROM SimulationJob s WHERE s.status = :status")
    Long countByStatus(@Param("status") String status);

    /**
     * 根据用户ID和任务状态查询模拟任务列表
     *
     * @param userId 用户ID
     * @param status 任务状态
     * @return 该用户指定状态的模拟任务列表
     */
    List<SimulationJob> findByUserIdAndStatus(Long userId, String status);

    /**
     * 根据用户ID和软件名称查询模拟任务列表
     *
     * @param userId       用户ID
     * @param softwareName 软件名称
     * @return 该用户使用指定软件的模拟任务列表
     */
    List<SimulationJob> findByUserIdAndSoftwareName(Long userId, String softwareName);

    /**
     * 根据用户ID和电解液系统ID查询模拟任务列表
     *
     * @param userId   用户ID
     * @param systemId 电解液系统ID
     * @return 该用户关联指定系统的模拟任务列表
     */
    List<SimulationJob> findByUserIdAndSystemId(Long userId, Long systemId);

    /**
     * 按状态分组统计指定用户的模拟任务数量
     *
     * <p>返回结构：Object[0]为状态字符串，Object[1]为该状态的任务数量（Long）</p>
     *
     * @param userId 用户ID
     * @return 状态统计结果列表，每个元素为[状态, 数量]数组
     */
    @Query("SELECT j.status, COUNT(j) FROM SimulationJob j WHERE j.userId = :userId GROUP BY j.status")
    List<Object[]> getStatusCountsByUserId(@Param("userId") Long userId);

    /**
     * 统计指定用户的模拟任务总数
     *
     * @param userId 用户ID
     * @return 该用户的模拟任务总数
     */
    long countByUserId(Long userId);

    /**
     * 查询所有模拟任务并关联电解液配方的任务描述，按创建时间降序排列
     *
     * <p>返回结构：Object[0]为SimulationJob对象，Object[1]为任务描述字符串</p>
     *
     * @return 关联任务描述的模拟任务列表
     */
    @Query("SELECT j, s.taskDescription FROM SimulationJob j " +
           "LEFT JOIN ElectrolyteSystem s ON j.systemId = s.systemId " +
           "ORDER BY j.createTime DESC")
    List<Object[]> findAllWithTaskDescription();

    /**
     * 根据用户ID查询模拟任务并关联电解液配方的任务描述，按创建时间降序排列
     *
     * <p>返回结构：Object[0]为SimulationJob对象，Object[1]为任务描述字符串</p>
     *
     * @param userId 用户ID
     * @return 该用户关联任务描述的模拟任务列表
     */
    @Query("SELECT j, e.taskDescription FROM SimulationJob j " +
           "LEFT JOIN ElectrolyteSystem e ON j.systemId = e.systemId " +
           "WHERE j.userId = :userId " +
           "ORDER BY j.createTime DESC")
    List<Object[]> findByUserIdWithTaskDescription(@Param("userId") Long userId);

    /**
     * 按状态分组统计全局模拟任务数量
     *
     * <p>返回结构：Object[0]为状态字符串，Object[1]为该状态的任务数量（Long）</p>
     *
     * @return 全局状态统计结果列表，每个元素为[状态, 数量]数组
     */
    @Query("SELECT j.status, COUNT(j) FROM SimulationJob j GROUP BY j.status")
    List<Object[]> getStatusCounts();

    /**
     * 直接更新任务状态（避免竞态条件）
     *
     * <p>使用直接UPDATE语句替代findById+save模式，避免并发场景下的丢失更新问题。
     * 适用于异步流水线执行中需要更新任务状态的场景。</p>
     *
     * @param jobId   任务ID
     * @param status  目标状态
     * @param endTime 结束时间（可为null表示未结束）
     * @return 受影响的行数，0表示任务不存在或状态未变更
     */
    @Modifying
    @Transactional
    @Query("UPDATE SimulationJob j SET j.status = :status, j.endTime = :endTime WHERE j.jobId = :jobId")
    int updateStatusById(@Param("jobId") Long jobId, @Param("status") String status, @Param("endTime") LocalDateTime endTime);

    /**
     * 直接更新任务结果摘要（避免竞态条件）
     *
     * <p>使用直接UPDATE语句替代findById+save模式，避免并发场景下进度更新
     * 覆盖其他字段的修改（如状态、硬件信息等）。</p>
     *
     * @param jobId         任务ID
     * @param resultSummary 结果摘要JSON字符串
     * @return 受影响的行数，0表示任务不存在或摘要未变更
     */
    @Modifying
    @Transactional
    @Query("UPDATE SimulationJob j SET j.resultSummary = :resultSummary WHERE j.jobId = :jobId")
    int updateResultSummaryById(@Param("jobId") Long jobId, @Param("resultSummary") String resultSummary);
}