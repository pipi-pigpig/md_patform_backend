package com.mdplatform.repository;

import com.mdplatform.model.SimulationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SimulationRepository extends JpaRepository<SimulationJob, Long> {

    List<SimulationJob> findAllByOrderByCreatedAtDesc();

    List<SimulationJob> findByStatus(SimulationJob.JobStatus status);

    List<SimulationJob> findBySoftware(SimulationJob.Software software);

    @Query("SELECT COUNT(s) FROM SimulationJob s WHERE s.status = :status")
    Long countByStatus(@Param("status") SimulationJob.JobStatus status);

    // 🔧 修复这里的查询：使用 systemId 而不是 system.id
    @Query("SELECT s FROM SimulationJob s WHERE s.systemId = :systemId ORDER BY s.createdAt DESC")
    List<SimulationJob> findBySystemId(@Param("systemId") Long systemId);

    @Query("SELECT s FROM SimulationJob s WHERE s.hardwareUsed = :hardware ORDER BY s.createdAt DESC")
    List<SimulationJob> findByHardware(@Param("hardware") SimulationJob.HardwareType hardware);
}