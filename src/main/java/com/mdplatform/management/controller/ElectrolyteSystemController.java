package com.mdplatform.management.controller;

import com.mdplatform.common.security.SecurityUtils;
import com.mdplatform.management.dto.*;
import com.mdplatform.management.service.ElectrolyteSystemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/electrolyte-systems")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class ElectrolyteSystemController {

    private final ElectrolyteSystemService electrolyteSystemService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createSystem(@RequestBody ElectrolyteSystemCreateRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            ElectrolyteSystemResponse response = electrolyteSystemService.createSystem(userId, request);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "配方创建成功");
            result.put("data", response);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateSystem(
            @PathVariable Long id,
            @RequestBody ElectrolyteSystemCreateRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            ElectrolyteSystemResponse response = electrolyteSystemService.updateSystem(userId, id, request);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "配方更新成功");
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteSystem(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            electrolyteSystemService.deleteSystem(userId, id);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "配方删除成功");
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listSystems(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int page_size) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        PageResponse<ElectrolyteSystemListResponse> response = electrolyteSystemService.listSystems(userId, keyword, page, page_size);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", response);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getSystemDetail(@PathVariable Long id) {
        try {
            ElectrolyteSystemResponse response = electrolyteSystemService.getSystemDetail(id);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(result);
        }
    }

    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchSystems(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int page_size) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        PageResponse<ElectrolyteSystemListResponse> response = electrolyteSystemService.listSystems(userId, keyword, page, page_size);
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", response);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}/template")
    public ResponseEntity<Map<String, Object>> saveAsTemplate(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        boolean isPublic = body != null && body.containsKey("is_public") && Boolean.TRUE.equals(body.get("is_public"));

        try {
            ElectrolyteSystemResponse response = electrolyteSystemService.saveAsTemplate(userId, id, isPublic);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "模板保存成功");
            result.put("data", response);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }
    }

    @PostMapping("/{id}/copy")
    public ResponseEntity<Map<String, Object>> createFromTemplate(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String newName = body.get("new_name");
        if (newName == null || newName.isBlank()) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "新配方名称不能为空");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }

        try {
            ElectrolyteSystemResponse response = electrolyteSystemService.createFromTemplate(userId, id, newName);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "配方复用成功");
            result.put("data", response);
            return ResponseEntity.status(HttpStatus.CREATED).body(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        }
    }
}
