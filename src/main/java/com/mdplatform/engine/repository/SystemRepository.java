package com.mdplatform.engine.repository;

import com.mdplatform.engine.model.ElectrolyteSystem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SystemRepository extends JpaRepository<ElectrolyteSystem, Long> {

    List<ElectrolyteSystem> findAllByOrderByCreateTimeDesc();

    List<ElectrolyteSystem> findByUserId(Long userId);

    List<ElectrolyteSystem> findByIsPublicTemplateTrue();

    @Query("SELECT e FROM ElectrolyteSystem e WHERE e.systemName LIKE %:keyword% OR e.taskDescription LIKE %:keyword%")
    List<ElectrolyteSystem> searchByKeyword(@Param("keyword") String keyword);
}