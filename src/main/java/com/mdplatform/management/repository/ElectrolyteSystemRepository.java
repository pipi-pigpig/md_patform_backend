package com.mdplatform.management.repository;

import com.mdplatform.management.model.ElectrolyteSystem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ElectrolyteSystemRepository extends JpaRepository<ElectrolyteSystem, Long> {

    Page<ElectrolyteSystem> findByUserIdOrderByCreateTimeDesc(Long userId, Pageable pageable);

    Page<ElectrolyteSystem> findByUserIdAndSystemNameContainingIgnoreCaseOrderByCreateTimeDesc(
            Long userId, String systemName, Pageable pageable);

    List<ElectrolyteSystem> findByIsPublicTemplateTrueOrderByCreateTimeDesc();

    @Query("SELECT e FROM ElectrolyteSystem e WHERE e.userId = :userId " +
           "AND (LOWER(e.systemName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(e.taskDescription) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "ORDER BY e.createTime DESC")
    Page<ElectrolyteSystem> searchByUserIdAndKeyword(
            @Param("userId") Long userId, @Param("keyword") String keyword, Pageable pageable);

    boolean existsBySystemIdAndUserId(Long systemId, Long userId);
}
