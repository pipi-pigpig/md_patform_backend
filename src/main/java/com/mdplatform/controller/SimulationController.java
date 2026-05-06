package com.mdplatform.controller;

import com.mdplatform.dto.SimulationDto;
import com.mdplatform.dto.SimulationStatsDto;
import com.mdplatform.model.SimulationJob;
import com.mdplatform.service.SimulationService;
import com.mdplatform.service.SystemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/simulations")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class SimulationController {

    private final SimulationService simulationService;
    private final SystemService systemService;

    @GetMapping
    public ResponseEntity<List<SimulationDto>> getAllSimulations() {
        List<SimulationJob> simulations = simulationService.getAllSimulations();
        List<SimulationDto> dtoList = simulations.stream()
                .map(job -> {
                    java.util.Optional<com.mdplatform.model.ElectrolyteSystem> system = systemService.getSystemById(job.getSystemId());
                    if (system.isPresent()) {
                        return SimulationDto.fromEntityWithDescription(job, system.get().getTaskDescription());
                    } else {
                        return SimulationDto.fromEntity(job);
                    }
                })
                .collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(dtoList);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SimulationDto> getSimulationById(@PathVariable Long id) {
        return simulationService.getSimulationById(id)
                .map(job -> {
                    java.util.Optional<com.mdplatform.model.ElectrolyteSystem> system = systemService.getSystemById(job.getSystemId());
                    if (system.isPresent()) {
                        return SimulationDto.fromEntityWithDescription(job, system.get().getTaskDescription());
                    } else {
                        return SimulationDto.fromEntity(job);
                    }
                })
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<SimulationJob> createSimulation(@RequestBody SimulationJob job) {
        try {
            SimulationJob created = simulationService.createSimulation(job);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            log.error("Failed to create simulation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<SimulationJob> updateSimulation(
            @PathVariable Long id,
            @RequestBody SimulationJob job) {
        return simulationService.updateSimulation(id, job)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSimulation(@PathVariable Long id) {
        boolean deleted = simulationService.deleteSimulation(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<SimulationJob>> getSimulationsByStatus(@PathVariable String status) {
        List<SimulationJob> simulations = simulationService.getSimulationsByStatus(status);
        return ResponseEntity.ok(simulations);
    }

    @GetMapping("/software/{softwareName}")
    public ResponseEntity<List<SimulationJob>> getSimulationsBySoftwareName(@PathVariable String softwareName) {
        List<SimulationJob> simulations = simulationService.getSimulationsBySoftwareName(softwareName);
        return ResponseEntity.ok(simulations);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<SimulationJob> updateSimulationStatus(
            @PathVariable Long id,
            @RequestBody String status) {
        return simulationService.updateSimulationStatus(id, status)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelSimulation(@PathVariable Long id) {
        boolean updated = simulationService.updateSimulationStatus(id, "CANCELLED").isPresent();
        return updated ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/stats")
    public ResponseEntity<SimulationStatsDto> getSystemStatistics() {
        SimulationStatsDto stats = simulationService.getSystemStatistics();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<SimulationJob>> getSimulationsByUserId(@PathVariable Long userId) {
        List<SimulationJob> simulations = simulationService.getSimulationsByUserId(userId);
        return ResponseEntity.ok(simulations);
    }

    @GetMapping("/system/{systemId}")
    public ResponseEntity<List<SimulationJob>> getSimulationsBySystemId(@PathVariable Long systemId) {
        List<SimulationJob> simulations = simulationService.getSimulationsBySystemId(systemId);
        return ResponseEntity.ok(simulations);
    }
}
