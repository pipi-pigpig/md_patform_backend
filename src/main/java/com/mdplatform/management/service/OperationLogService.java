package com.mdplatform.management.service;

import com.mdplatform.management.model.SysOperationLog;
import com.mdplatform.management.repository.OperationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class OperationLogService {

    private final OperationLogRepository operationLogRepository;

    public List<SysOperationLog> getLogsByUserId(Long userId) {
        return operationLogRepository.findByUserIdOrderByOperationTimeDesc(userId);
    }

    @Transactional
    public SysOperationLog createLog(SysOperationLog log) {
        return operationLogRepository.save(log);
    }

    @Transactional
    public boolean deleteLog(Long id) {
        if (operationLogRepository.existsById(id)) {
            operationLogRepository.deleteById(id);
            return true;
        }
        return false;
    }
}