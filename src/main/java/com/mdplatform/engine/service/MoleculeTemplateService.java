package com.mdplatform.engine.service;

import com.mdplatform.engine.model.MoleculeTemplate;
import com.mdplatform.engine.repository.MoleculeTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class MoleculeTemplateService {

    private final MoleculeTemplateRepository moleculeTemplateRepository;

    public List<MoleculeTemplate> getAllTemplates() {
        return moleculeTemplateRepository.findAll();
    }

    public Optional<MoleculeTemplate> getTemplateById(Long id) {
        return moleculeTemplateRepository.findById(id);
    }

    public Optional<MoleculeTemplate> getTemplateByName(String name) {
        return moleculeTemplateRepository.findByMoleculeName(name);
    }

    public List<MoleculeTemplate> getTemplatesByType(String type) {
        return moleculeTemplateRepository.findByMoleculeType(type);
    }

    public List<MoleculeTemplate> getSystemTemplates() {
        return moleculeTemplateRepository.findByIsSystemTemplateTrue();
    }

    @Transactional
    public MoleculeTemplate createTemplate(MoleculeTemplate template) {
        log.info("Creating molecule template: {}", template.getMoleculeName());
        return moleculeTemplateRepository.save(template);
    }

    @Transactional
    public Optional<MoleculeTemplate> updateTemplate(Long id, MoleculeTemplate template) {
        return moleculeTemplateRepository.findById(id).map(existingTemplate -> {
            existingTemplate.setMoleculeName(template.getMoleculeName());
            existingTemplate.setMoleculeType(template.getMoleculeType());
            existingTemplate.setFormula(template.getFormula());
            existingTemplate.setMolecularWeight(template.getMolecularWeight());
            existingTemplate.setSmiles(template.getSmiles());
            existingTemplate.setForceFieldParams(template.getForceFieldParams());
            existingTemplate.setDescription(template.getDescription());
            log.info("Updated molecule template with id: {}", id);
            return moleculeTemplateRepository.save(existingTemplate);
        });
    }

    @Transactional
    public boolean deleteTemplate(Long id) {
        if (moleculeTemplateRepository.existsById(id)) {
            moleculeTemplateRepository.deleteById(id);
            log.info("Deleted molecule template with id: {}", id);
            return true;
        }
        return false;
    }
}