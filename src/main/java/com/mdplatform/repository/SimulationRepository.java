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
}
