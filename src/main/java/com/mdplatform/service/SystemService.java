package com.mdplatform.service;

import com.mdplatform.model.ElectrolyteSystem;
import com.mdplatform.repository.SystemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class SystemService {

    private final SystemRepository systemRepository;

    public List<ElectrolyteSystem> getAllSystems() {
        return systemRepository.findAllByOrderByCreatedAtDesc();
    }

    public Optional<ElectrolyteSystem> getSystemById(Long id) {
        return systemRepository.findById(id);
    }

    public ElectrolyteSystem createSystem(ElectrolyteSystem system) {
        system.setId(null); // 确保是新建
        ElectrolyteSystem saved = systemRepository.save(system);
        log.info("Created system: {} with id: {}", saved.getName(), saved.getId());
        return saved;
    }

    public Optional<ElectrolyteSystem> updateSystem(Long id, ElectrolyteSystem system) {
        return systemRepository.findById(id).map(existing -> {
            if (system.getName() != null) existing.setName(system.getName());
            if (system.getDescription() != null) existing.setDescription(system.getDescription());
            if (system.getSolventType() != null) existing.setSolventType(system.getSolventType());
            if (system.getSaltFormula() != null) existing.setSaltFormula(system.getSaltFormula());
            if (system.getConcentration() != null) existing.setConcentration(system.getConcentration());
            if (system.getTemperature() != null) existing.setTemperature(system.getTemperature());
            if (system.getPressure() != null) existing.setPressure(system.getPressure());

            ElectrolyteSystem updated = systemRepository.save(existing);
            log.info("Updated system with id: {}", id);
            return updated;
        });
    }

    public boolean deleteSystem(Long id) {
        if (systemRepository.existsById(id)) {
            systemRepository.deleteById(id);
            log.info("Deleted system with id: {}", id);
            return true;
        }
        return false;
    }

    public List<ElectrolyteSystem> searchSystems(String keyword) {
        return systemRepository.searchByKeyword(keyword);
    }

    public List<String> getAllSaltFormulas() {
        return systemRepository.findAllSaltFormulas();
    }

    public List<ElectrolyteSystem> getSystemsBySolvent(ElectrolyteSystem.SolventType solventType) {
        return systemRepository.findBySolventType(solventType);
    }
}