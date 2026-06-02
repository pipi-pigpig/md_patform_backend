package com.mdplatform.engine.service;

import com.mdplatform.engine.model.SimulationInput;
import com.mdplatform.engine.repository.SimulationInputRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class SimulationInputService {

    private final SimulationInputRepository simulationInputRepository;

    public List<SimulationInput> getAllInputs() {
        return simulationInputRepository.findAll();
    }

    public Optional<SimulationInput> getInputById(Long id) {
        return simulationInputRepository.findById(id);
    }

    public Optional<SimulationInput> getInputByJobId(Long jobId) {
        return simulationInputRepository.findByJobId(jobId);
    }

    public SimulationInput createInput(SimulationInput input) {
        SimulationInput savedInput = simulationInputRepository.save(input);
        log.info("Created simulation input with id: {}", savedInput.getInputId());
        return savedInput;
    }

    public Optional<SimulationInput> updateInput(Long id, SimulationInput input) {
        return simulationInputRepository.findById(id).map(existingInput -> {
            input.setInputId(id);
            SimulationInput updatedInput = simulationInputRepository.save(input);
            log.info("Updated simulation input with id: {}", id);
            return updatedInput;
        });
    }

    public boolean deleteInput(Long id) {
        if (simulationInputRepository.existsById(id)) {
            simulationInputRepository.deleteById(id);
            log.info("Deleted simulation input with id: {}", id);
            return true;
        }
        return false;
    }
}