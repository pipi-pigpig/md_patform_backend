package com.mdplatform.engine.repository;

import com.mdplatform.engine.model.SimulationRawOutput;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SimulationOutputRepository extends JpaRepository<SimulationRawOutput, Long> {

    Optional<SimulationRawOutput> findByJobId(Long jobId);
}