package com.mdplatform.engine.controller;

import com.mdplatform.engine.model.CalculationResult;
import com.mdplatform.engine.service.CalculationResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/results")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class CalculationResultController {

    private final CalculationResultService calculationResultService;

    @GetMapping
    public ResponseEntity<List<CalculationResult>> getAllResults() {
        List<CalculationResult> results = calculationResultService.getAllResults();
        return ResponseEntity.ok(results);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CalculationResult> getResult(@PathVariable Long id) {
        return calculationResultService.getResultById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/job/{jobId}")
    public ResponseEntity<List<CalculationResult>> getResultsByJobId(@PathVariable Long jobId) {
        List<CalculationResult> results = calculationResultService.getResultsByJobId(jobId);
        return ResponseEntity.ok(results);
    }

    @PostMapping
    public ResponseEntity<CalculationResult> createResult(@RequestBody CalculationResult result) {
        try {
            CalculationResult created = calculationResultService.createResult(result);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            log.error("Failed to create calculation result", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteResult(@PathVariable Long id) {
        boolean deleted = calculationResultService.deleteResult(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}