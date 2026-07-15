package com.mdplatform.engine.controller;

import com.mdplatform.engine.model.MoleculeTemplate;
import com.mdplatform.engine.service.MoleculeTemplateService;
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
 * 分子模板控制器，提供分子模板的查询和管理接口
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api/molecules")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "分子模板", description = "分子模板的查询和管理接口")
public class MoleculeTemplateController {

    private final MoleculeTemplateService moleculeTemplateService;

    /**
     * 获取所有分子模板列表
     *
     * @return 分子模板列表
     */
    @GetMapping
    @Operation(summary = "获取所有分子模板列表")
    public ResponseEntity<List<MoleculeTemplate>> getAllTemplates() {
        List<MoleculeTemplate> templates = moleculeTemplateService.getAllTemplates();
        return ResponseEntity.ok(templates);
    }

    /**
     * 根据ID获取分子模板详情
     *
     * @param id 分子模板ID
     * @return 分子模板
     */
    @GetMapping("/{id}")
    @Operation(summary = "根据ID获取分子模板详情")
    public ResponseEntity<MoleculeTemplate> getTemplate(@PathVariable Long id) {
        return moleculeTemplateService.getTemplateById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 根据名称获取分子模板
     *
     * @param name 分子模板名称
     * @return 分子模板
     */
    @GetMapping("/name/{name}")
    @Operation(summary = "根据名称获取分子模板")
    public ResponseEntity<MoleculeTemplate> getTemplateByName(@PathVariable String name) {
        return moleculeTemplateService.getTemplateByName(name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 根据类型获取分子模板列表
     *
     * @param type 分子类型
     * @return 分子模板列表
     */
    @GetMapping("/type/{type}")
    @Operation(summary = "根据类型获取分子模板列表")
    public ResponseEntity<List<MoleculeTemplate>> getTemplatesByType(@PathVariable String type) {
        List<MoleculeTemplate> templates = moleculeTemplateService.getTemplatesByType(type);
        return ResponseEntity.ok(templates);
    }

    /**
     * 获取系统预置分子模板列表
     *
     * @return 系统预置分子模板列表
     */
    @GetMapping("/system")
    @Operation(summary = "获取系统预置分子模板列表")
    public ResponseEntity<List<MoleculeTemplate>> getSystemTemplates() {
        List<MoleculeTemplate> templates = moleculeTemplateService.getSystemTemplates();
        return ResponseEntity.ok(templates);
    }

    /**
     * 创建新的分子模板
     *
     * @param template 分子模板实体
     * @return 创建的分子模板
     */
    @PostMapping
    @Operation(summary = "创建新的分子模板")
    public ResponseEntity<MoleculeTemplate> createTemplate(@Valid @RequestBody MoleculeTemplate template) {
        try {
            MoleculeTemplate created = moleculeTemplateService.createTemplate(template);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            log.error("Failed to create molecule template", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 更新分子模板
     *
     * @param id       分子模板ID
     * @param template 更新后的分子模板实体
     * @return 更新后的分子模板
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新分子模板")
    public ResponseEntity<MoleculeTemplate> updateTemplate(@PathVariable Long id, @Valid @RequestBody MoleculeTemplate template) {
        return moleculeTemplateService.updateTemplate(id, template)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 删除分子模板
     *
     * @param id 分子模板ID
     * @return 无内容响应
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除分子模板")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        boolean deleted = moleculeTemplateService.deleteTemplate(id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
