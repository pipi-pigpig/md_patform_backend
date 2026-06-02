package com.mdplatform.engine.controller;

import com.mdplatform.engine.model.MoleculeTemplate;
import com.mdplatform.engine.service.MoleculeTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/molecules")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class MoleculeTemplateController {

    private final MoleculeTemplateService moleculeTemplateService;

    @GetMapping
    public ResponseEntity<List<MoleculeTemplate>> getAllTemplates() {
        List<MoleculeTemplate> templates = moleculeTemplateService.getAllTemplates();
        return ResponseEntity.ok(templates);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MoleculeTemplate> getTemplate(@PathVariable Long id) {
        return moleculeTemplateService.getTemplateById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/name/{name}")
    public ResponseEntity<MoleculeTemplate> getTemplateByName(@PathVariable String name) {
        return moleculeTemplateService.getTemplateByName(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<MoleculeTemplate>> getTemplatesByType(@PathVariable String type) {
        List<MoleculeTemplate> templates = moleculeTemplateService.getTemplatesByType(type);
        return ResponseEntity.ok(templates);
    }

    @GetMapping("/system")
    public ResponseEntity<List<MoleculeTemplate>> getSystemTemplates() {
        List<MoleculeTemplate> templates = moleculeTemplateService.getSystemTemplates();
        return ResponseEntity.ok(templates);
    }

    @PostMapping
    public ResponseEntity<MoleculeTemplate> createTemplate(@RequestBody MoleculeTemplate template) {
        try {
            MoleculeTemplate created = moleculeTemplateService.createTemplate(template);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            log.error("Failed to create molecule template", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<MoleculeTemplate> updateTemplate(@PathVariable Long id, @RequestBody MoleculeTemplate template) {
        return moleculeTemplateService.updateTemplate(id, template)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        boolean deleted = moleculeTemplateService.deleteTemplate(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}