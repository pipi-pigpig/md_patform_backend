package com.mdplatform.engine.repository;

import com.mdplatform.engine.model.SimulationInput;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SimulationInputRepository extends JpaRepository<SimulationInput, Long> {

    Optional<SimulationInput> findByJobId(Long jobId);
}