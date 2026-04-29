package com.mdplatform.controller;

import com.mdplatform.model.ElectrolyteSystem;
import com.mdplatform.service.SystemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/systems")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class SystemController {

    private final SystemService systemService;

    @GetMapping
    public ResponseEntity<List<ElectrolyteSystem>> getAllSystems() {
        List<ElectrolyteSystem> systems = systemService.getAllSystems();
        return ResponseEntity.ok(systems);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ElectrolyteSystem> getSystem(@PathVariable Long id) {
        return systemService.getSystemById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ElectrolyteSystem> createSystem(@RequestBody ElectrolyteSystem system) {
        try {
            ElectrolyteSystem created = systemService.createSystem(system);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            log.error("Failed to create system", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ElectrolyteSystem> updateSystem(
            @PathVariable Long id,
            @RequestBody ElectrolyteSystem system) {
        return systemService.updateSystem(id, system)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSystem(@PathVariable Long id) {
        boolean deleted = systemService.deleteSystem(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<ElectrolyteSystem>> searchSystems(@RequestParam String keyword) {
        List<ElectrolyteSystem> systems = systemService.searchSystems(keyword);
        return ResponseEntity.ok(systems);
    }
}