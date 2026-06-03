package com.mdplatform.engine.service;

import com.mdplatform.engine.model.CalculationResult;
import com.mdplatform.engine.repository.CalculationResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class CalculationResultService {

    private final CalculationResultRepository calculationResultRepository;

    public List<CalculationResult> getAllResults() {
        return calculationResultRepository.findAll();
    }

    public Optional<CalculationResult> getResultById(Long id) {
        return calculationResultRepository.findById(id);
    }

    public List<CalculationResult> getResultsByJobId(Long jobId) {
        return calculationResultRepository.findByJobId(jobId);
    }

    public CalculationResult createResult(CalculationResult result) {
        CalculationResult savedResult = calculationResultRepository.save(result);
        log.info("Created calculation result with id: {}", savedResult.getResultId());
        return savedResult;
    }

    public boolean deleteResult(Long id) {
        if (calculationResultRepository.existsById(id)) {
            calculationResultRepository.deleteById(id);
            log.info("Deleted calculation result with id: {}", id);
            return true;
        }
        return false;
    }
}