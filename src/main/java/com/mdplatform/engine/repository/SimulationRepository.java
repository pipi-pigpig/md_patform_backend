package com.mdplatform.engine.repository;

import com.mdplatform.engine.model.SimulationJob;
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

    List<SimulationJob> findByUserIdAndStatus(Long userId, String status);

    List<SimulationJob> findByUserIdAndSoftwareName(Long userId, String softwareName);

    List<SimulationJob> findByUserIdAndSystemId(Long userId, Long systemId);

    @Query("SELECT j.status, COUNT(j) FROM SimulationJob j WHERE j.userId = :userId GROUP BY j.status")
    List<Object[]> getStatusCountsByUserId(@Param("userId") Long userId);

    long countByUserId(Long userId);

    @Query("SELECT j, s.taskDescription FROM SimulationJob j " +
           "LEFT JOIN ElectrolyteSystem s ON j.systemId = s.systemId " +
           "ORDER BY j.createTime DESC")
    List<Object[]> findAllWithTaskDescription();

    @Query("SELECT j, e.taskDescription FROM SimulationJob j " +
           "LEFT JOIN ElectrolyteSystem e ON j.systemId = e.systemId " +
           "WHERE j.userId = :userId " +
           "ORDER BY j.createTime DESC")
    List<Object[]> findByUserIdWithTaskDescription(@Param("userId") Long userId);

    @Query("SELECT j.status, COUNT(j) FROM SimulationJob j GROUP BY j.status")
    List<Object[]> getStatusCounts();
}