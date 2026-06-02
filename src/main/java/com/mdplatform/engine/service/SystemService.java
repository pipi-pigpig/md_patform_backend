package com.mdplatform.engine.service;

import com.mdplatform.engine.model.ElectrolyteSystem;
import com.mdplatform.engine.repository.SystemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class SystemService {

    private final SystemRepository systemRepository;

    public List<ElectrolyteSystem> getAllSystems() {
        return systemRepository.findAllByOrderByCreateTimeDesc();
    }

    public Optional<ElectrolyteSystem> getSystemById(Long id) {
        return systemRepository.findById(id);
    }

    public ElectrolyteSystem createSystem(ElectrolyteSystem system) {
        system.setSystemId(null);
        system.setCreateTime(LocalDateTime.now());
        system.setUpdateTime(LocalDateTime.now());
        ElectrolyteSystem saved = systemRepository.save(system);
        log.info("Created system: {} with id: {}", saved.getSystemName(), saved.getSystemId());
        return saved;
    }

    public Optional<ElectrolyteSystem> updateSystem(Long id, ElectrolyteSystem system) {
        return systemRepository.findById(id).map(existing -> {
            if (system.getSystemName() != null) existing.setSystemName(system.getSystemName());
            if (system.getUserId() != null) existing.setUserId(system.getUserId());
            if (system.getTaskDescription() != null) existing.setTaskDescription(system.getTaskDescription());
            if (system.getSolventInfo() != null) existing.setSolventInfo(system.getSolventInfo());
            if (system.getSaltInfo() != null) existing.setSaltInfo(system.getSaltInfo());
            if (system.getAdditiveInfo() != null) existing.setAdditiveInfo(system.getAdditiveInfo());
            if (system.getTemperature() != null) existing.setTemperature(system.getTemperature());
            if (system.getPressure() != null) existing.setPressure(system.getPressure());
            if (system.getBoxSize() != null) existing.setBoxSize(system.getBoxSize());
            if (system.getBoundaryConditions() != null) existing.setBoundaryConditions(system.getBoundaryConditions());
            if (system.getTotalAtomCount() != null) existing.setTotalAtomCount(system.getTotalAtomCount());
            if (system.getIsPublicTemplate() != null) existing.setIsPublicTemplate(system.getIsPublicTemplate());
            existing.setUpdateTime(LocalDateTime.now());

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

    public List<ElectrolyteSystem> getSystemsByUserId(Long userId) {
        return systemRepository.findByUserId(userId);
    }

    public List<ElectrolyteSystem> getPublicTemplates() {
        return systemRepository.findByIsPublicTemplateTrue();
    }
}