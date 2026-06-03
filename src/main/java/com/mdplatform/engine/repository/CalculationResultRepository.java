package com.mdplatform.engine.repository;

import com.mdplatform.engine.model.CalculationResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CalculationResultRepository extends JpaRepository<CalculationResult, Long> {

    List<CalculationResult> findByJobId(Long jobId);

    List<CalculationResult> findByJobIdOrderByCreateTimeDesc(Long jobId);

    List<CalculationResult> findByPropertyName(String propertyName);

    @Query("SELECT c FROM CalculationResult c WHERE c.jobId = :jobId AND c.propertyName = :propertyName")
    Optional<CalculationResult> findByJobIdAndPropertyName(@Param("jobId") Long jobId, @Param("propertyName") String propertyName);
}