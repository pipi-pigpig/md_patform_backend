package com.mdplatform.engine.controller;

import com.mdplatform.common.security.SecurityUtils;
import com.mdplatform.engine.dto.CalculationResultDto;
import com.mdplatform.engine.service.CalculationResultService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * 计算结果控制器，提供计算结果的查询和管理接口
 *
 * <p>功能：提供计算结果的CRUD操作，支持按任务ID和性质类型查询，
 * 返回结果自动包含对应性质子表的专属数据。</p>
 *
 * @author 电解液MD平台
 * @version 2.0.0
 */
@RestController
@RequestMapping("/api/results")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "计算结果", description = "计算结果的查询和管理接口，支持子表数据联合查询")
public class CalculationResultController {

    private final CalculationResultService calculationResultService;

    /**
     * 获取所有计算结果列表（含子表数据）
     *
     * @return 计算结果DTO列表
     */
    @GetMapping
    @Operation(summary = "获取所有计算结果列表")
    public ResponseEntity<List<CalculationResultDto>> getAllResults() {
        List<CalculationResultDto> results = calculationResultService.getAllResults();
        return ResponseEntity.ok(results);
    }

    /**
     * 根据ID获取计算结果详情（含子表数据）
     *
     * @param id 计算结果ID
     * @return 计算结果DTO
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取计算结果详情")
    public ResponseEntity<CalculationResultDto> getResult(@PathVariable Long id) {
        return calculationResultService.getResultById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 根据任务ID获取计算结果列表（含子表数据）
     *
     * @param jobId 任务ID
     * @return 计算结果DTO列表
     */
    @GetMapping("/job/{jobId}")
    @Operation(summary = "根据任务ID获取计算结果列表")
    public ResponseEntity<List<CalculationResultDto>> getResultsByJobId(@PathVariable Long jobId) {
        List<CalculationResultDto> results = calculationResultService.getResultsByJobId(jobId);
        return ResponseEntity.ok(results);
    }

    /**
     * 根据任务ID和性质类型获取计算结果（含子表数据）
     *
     * @param jobId        任务ID
     * @param propertyName 性质名称（density/viscosity/conductivity/dielectric/solvation）
     * @return 计算结果DTO列表
     */
    @GetMapping("/job/{jobId}/property/{propertyName}")
    @Operation(summary = "根据任务ID和性质类型获取计算结果")
    public ResponseEntity<List<CalculationResultDto>> getResultsByJobIdAndProperty(
            @PathVariable Long jobId,
            @PathVariable String propertyName) {
        List<CalculationResultDto> results = calculationResultService.getResultsByJobIdAndPropertyName(jobId, propertyName);
        return ResponseEntity.ok(results);
    }

    /**
     * 根据性质类型获取所有计算结果（含子表数据）
     *
     * @param propertyName 性质名称
     * @return 计算结果DTO列表
     */
    @GetMapping("/property/{propertyName}")
    @Operation(summary = "根据性质类型获取所有计算结果")
    public ResponseEntity<List<CalculationResultDto>> getResultsByProperty(
            @PathVariable String propertyName) {
        List<CalculationResultDto> results = calculationResultService.getResultsByPropertyName(propertyName);
        return ResponseEntity.ok(results);
    }

    /**
     * 创建新的计算结果（同时保存主表和子表数据）
     *
     * @param dto 计算结果DTO
     * @return 创建的计算结果DTO
     */
    @PostMapping
    @Operation(summary = "创建新的计算结果")
    public ResponseEntity<CalculationResultDto> createResult(@Valid @RequestBody CalculationResultDto dto) {
        try {
            CalculationResultDto created = calculationResultService.createResult(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            log.error("[计算结果] 创建失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 删除计算结果（级联删除子表数据）
     *
     * @param id 计算结果ID
     * @return 无内容响应
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除计算结果")
    public ResponseEntity<Void> deleteResult(@PathVariable Long id) {
        boolean deleted = calculationResultService.deleteResult(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
