package com.mdplatform.management.repository;

import com.mdplatform.management.model.SimulationJob;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SimulationJobRepository extends JpaRepository<SimulationJob, Long> {

    long countByStatus(String status);

    long countByCreateTimeAfter(LocalDateTime since);

    @Query("SELECT j.status, COUNT(j) FROM MgmtSimulationJob j WHERE j.createTime >= :since GROUP BY j.status")
    List<Object[]> countByStatusSince(@Param("since") LocalDateTime since);

    @Query("SELECT FUNCTION('DATE', j.createTime) AS d, COUNT(j) FROM MgmtSimulationJob j " +
           "WHERE j.createTime >= :since GROUP BY FUNCTION('DATE', j.createTime) ORDER BY d ASC")
    List<Object[]> countDailySince(@Param("since") LocalDateTime since);

    @Query("SELECT j.status, COUNT(j) FROM MgmtSimulationJob j " +
           "WHERE j.userId = :userId AND j.createTime >= :since GROUP BY j.status")
    List<Object[]> countByUserIdAndStatusSince(@Param("userId") Long userId,
                                               @Param("since") LocalDateTime since);

    @Query("SELECT FUNCTION('DATE', j.createTime) AS d, COUNT(j) FROM MgmtSimulationJob j " +
           "WHERE j.userId = :userId AND j.createTime >= :since " +
           "GROUP BY FUNCTION('DATE', j.createTime) ORDER BY d ASC")
    List<Object[]> countDailyByUserIdSince(@Param("userId") Long userId,
                                           @Param("since") LocalDateTime since);

    Page<SimulationJob> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable);

    Page<SimulationJob> findByUserIdAndStatusOrderByCreateTimeDesc(Long userId, String status, Pageable pageable);

    @Query("SELECT j FROM MgmtSimulationJob j WHERE j.userId = :userId AND " +
           "(LOWER(j.jobName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(j.taskDescription) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY j.createTime DESC")
    Page<SimulationJob> searchByUserIdAndKeyword(@Param("userId") Long userId, @Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT j FROM MgmtSimulationJob j WHERE j.userId = :userId AND j.status = :status AND " +
           "(LOWER(j.jobName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(j.taskDescription) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY j.createTime DESC")
    Page<SimulationJob> searchByUserIdAndStatusAndKeyword(@Param("userId") Long userId, @Param("status") String status, @Param("keyword") String keyword, Pageable pageable);

    long countByUserIdAndStatus(Long userId, String status);

    long countByUserId(Long userId);

    boolean existsBySystemId(Long systemId);

    boolean existsBySystemIdAndStatusIn(Long systemId, List<String> statuses);

    List<SimulationJob> findBySystemId(Long systemId);

    @Query("SELECT j.status, COUNT(j) FROM MgmtSimulationJob j WHERE j.userId = :userId GROUP BY j.status")
    List<Object[]> getStatusCountsByUserId(@Param("userId") Long userId);
}
