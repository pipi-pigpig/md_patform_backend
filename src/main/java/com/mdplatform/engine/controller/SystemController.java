package com.mdplatform.engine.controller;

import com.mdplatform.engine.dto.SystemCreateRequest;
import com.mdplatform.engine.dto.SystemDto;
import com.mdplatform.engine.model.ElectrolyteSystem;
import com.mdplatform.engine.service.SystemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/systems")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class SystemController {

    private final SystemService systemService;

    @GetMapping
    public ResponseEntity<List<SystemDto>> getAllSystems() {
        List<ElectrolyteSystem> systems = systemService.getAllSystems();
        List<SystemDto> dtoList = systems.stream()
                .map(SystemDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtoList);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SystemDto> getSystemById(@PathVariable Long id) {
        return systemService.getSystemById(id)
                .map(SystemDto::fromEntity)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<SystemDto> createSystem(@RequestBody SystemCreateRequest request) {
        try {
            ElectrolyteSystem system = request.toEntity();
            ElectrolyteSystem created = systemService.createSystem(system);
            return ResponseEntity.status(HttpStatus.CREATED).body(SystemDto.fromEntity(created));
        } catch (Exception e) {
            log.error("Failed to create system", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<SystemDto> updateSystem(
            @PathVariable Long id,
            @RequestBody SystemCreateRequest request) {
        ElectrolyteSystem system = request.toEntity();
        return systemService.updateSystem(id, system)
                .map(s -> ResponseEntity.ok(SystemDto.fromEntity(s)))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSystem(@PathVariable Long id) {
        boolean deleted = systemService.deleteSystem(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @GetMapping("/search")
    public ResponseEntity<List<SystemDto>> searchSystems(@RequestParam String keyword) {
        List<ElectrolyteSystem> systems = systemService.searchSystems(keyword);
        List<SystemDto> dtoList = systems.stream()
                .map(SystemDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtoList);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<SystemDto>> getSystemsByUserId(@PathVariable Long userId) {
        List<ElectrolyteSystem> systems = systemService.getSystemsByUserId(userId);
        List<SystemDto> dtoList = systems.stream()
                .map(SystemDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtoList);
    }

    @GetMapping("/public")
    public ResponseEntity<List<SystemDto>> getPublicTemplates() {
        List<ElectrolyteSystem> systems = systemService.getPublicTemplates();
        List<SystemDto> dtoList = systems.stream()
                .map(SystemDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtoList);
    }
}