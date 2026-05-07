package com.mdplatform.repository;

import com.mdplatform.model.SimulationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SimulationRepository extends JpaRepository<SimulationJob, Long> {

    List<SimulationJob> findAllByOrderByCreateTimeDesc();

    List<SimulationJob> findByUserId(Long userId);

    List<SimulationJob> findBySystemId(Long systemId);

    List<SimulationJob> findByStatus(String status);

    List<SimulationJob> findBySoftwareName(String softwareName);

    @Query("SELECT COUNT(s) FROM SimulationJob s WHERE s.status = :status")
    Long countByStatus(@Param("status") String status);

    // ===== 用户相关查询 =====

    /**
     * 按用户和状态查询
     */
    List<SimulationJob> findByUserIdAndStatus(Long userId, String status);

    /**
     * 按用户和软件查询
     */
    List<SimulationJob> findByUserIdAndSoftwareName(Long userId, String softwareName);

    /**
     * 按用户和系统查询
     */
    List<SimulationJob> findByUserIdAndSystemId(Long userId, Long systemId);

    /**
     * 按用户统计各状态数量
     */
    @Query("SELECT j.status, COUNT(j) FROM SimulationJob j WHERE j.userId = :userId GROUP BY j.status")
    List<Object[]> getStatusCountsByUserId(@Param("userId") Long userId);

    /**
     * 按用户统计总数
     */
    long countByUserId(Long userId);

    // ===== 性能优化查询 =====

    /**
     * 一次性获取所有任务及其对应的任务描述，解决 N+1 查询问题
     */
    @Query("SELECT j, s.taskDescription FROM SimulationJob j " +
           "LEFT JOIN ElectrolyteSystem s ON j.systemId = s.systemId " +
           "ORDER BY j.createTime DESC")
    List<Object[]> findAllWithTaskDescription();

    /**
     * 按用户获取任务及其描述，解决 N+1 查询问题
     */
    @Query("SELECT j, e.taskDescription FROM SimulationJob j " +
           "LEFT JOIN ElectrolyteSystem e ON j.systemId = e.systemId " +
           "WHERE j.userId = :userId " +
           "ORDER BY j.createTime DESC")
    List<Object[]> findByUserIdWithTaskDescription(@Param("userId") Long userId);

    /**
     * 使用单个查询获取所有状态统计，替代多次 countByStatus 调用
     */
    @Query("SELECT j.status, COUNT(j) FROM SimulationJob j GROUP BY j.status")
    List<Object[]> getStatusCounts();
}
