package com.mdplatform.engine.service;

import com.mdplatform.engine.model.SimulationRawOutput;
import com.mdplatform.engine.repository.SimulationOutputRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class SimulationOutputService {

    private final SimulationOutputRepository simulationOutputRepository;

    public List<SimulationRawOutput> getAllOutputs() {
        return simulationOutputRepository.findAll();
    }

    public Optional<SimulationRawOutput> getOutputById(Long id) {
        return simulationOutputRepository.findById(id);
    }

    public Optional<SimulationRawOutput> getOutputByJobId(Long jobId) {
        return simulationOutputRepository.findByJobId(jobId);
    }

    public SimulationRawOutput createOutput(SimulationRawOutput output) {
        SimulationRawOutput savedOutput = simulationOutputRepository.save(output);
        log.info("Created simulation output with id: {}", savedOutput.getOutputId());
        return savedOutput;
    }

    public Optional<SimulationRawOutput> updateOutput(Long id, SimulationRawOutput output) {
        return simulationOutputRepository.findById(id).map(existingOutput -> {
            output.setOutputId(id);
            SimulationRawOutput updatedOutput = simulationOutputRepository.save(output);
            log.info("Updated simulation output with id: {}", id);
            return updatedOutput;
        });
    }

    public boolean deleteOutput(Long id) {
        if (simulationOutputRepository.existsById(id)) {
            simulationOutputRepository.deleteById(id);
            log.info("Deleted simulation output with id: {}", id);
            return true;
        }
        return false;
    }
}