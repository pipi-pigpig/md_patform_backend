package com.mdplatform.management.repository;

import com.mdplatform.management.model.SysOperationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OperationLogRepository extends JpaRepository<SysOperationLog, Long> {

    List<SysOperationLog> findByUserIdOrderByOperationTimeDesc(Long userId);

    List<SysOperationLog> findByOperationType(String operationType);
}