package com.mdplatform.repository;

import com.mdplatform.model.ElectrolyteSystem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemRepository extends JpaRepository<ElectrolyteSystem, Long> {

    List<ElectrolyteSystem> findAllByOrderByCreatedAtDesc();

    List<ElectrolyteSystem> findBySolventType(ElectrolyteSystem.SolventType solventType);

    @Query("SELECT e FROM ElectrolyteSystem e WHERE e.saltFormula LIKE %:keyword% OR e.name LIKE %:keyword% OR e.description LIKE %:keyword%")
    List<ElectrolyteSystem> searchByKeyword(@Param("keyword") String keyword);

    @Query("SELECT DISTINCT e.saltFormula FROM ElectrolyteSystem e WHERE e.saltFormula IS NOT NULL")
    List<String> findAllSaltFormulas();
}