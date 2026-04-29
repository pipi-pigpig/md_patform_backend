package com.mdplatform.repository;

import com.mdplatform.model.SimulationInput;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SimulationInputRepository extends JpaRepository<SimulationInput, Long> {

    Optional<SimulationInput> findByJobId(Long jobId);
}
