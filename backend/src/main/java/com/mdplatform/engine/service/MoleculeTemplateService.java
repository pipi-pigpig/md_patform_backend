package com.mdplatform.engine.service;

import com.mdplatform.engine.model.MoleculeTemplate;
import com.mdplatform.engine.repository.MoleculeTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 分子模板服务类
 *
 * <p>提供分子模板（MoleculeTemplate）的增删改查业务逻辑，
 * 支持按名称、类型、系统模板等维度查询分子模板。</p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MoleculeTemplateService {

    private final MoleculeTemplateRepository moleculeTemplateRepository;

    /**
     * 获取所有分子模板
     *
     * @return 分子模板列表
     */
    public List<MoleculeTemplate> getAllTemplates() {
        return moleculeTemplateRepository.findAll();
    }

    /**
     * 根据模板ID查询分子模板
     *
     * @param id 模板ID
     * @return 包含分子模板的Optional对象，若不存在则为空
     */
    public Optional<MoleculeTemplate> getTemplateById(Long id) {
        return moleculeTemplateRepository.findById(id);
    }

    /**
     * 根据分子名称查询分子模板
     *
     * @param name 分子名称
     * @return 包含分子模板的Optional对象，若不存在则为空
     */
    public Optional<MoleculeTemplate> getTemplateByName(String name) {
        return moleculeTemplateRepository.findByMoleculeName(name);
    }

    /**
     * 根据分子类型查询分子模板
     *
     * @param type 分子类型（如solvent、salt、additive）
     * @return 匹配类型的分子模板列表
     */
    public List<MoleculeTemplate> getTemplatesByType(String type) {
        return moleculeTemplateRepository.findByMoleculeType(type);
    }

    /**
     * 获取所有系统预置模板
     *
     * @return 系统预置分子模板列表
     */
    public List<MoleculeTemplate> getSystemTemplates() {
        return moleculeTemplateRepository.findByIsSystemTemplateTrue();
    }

    /**
     * 创建新的分子模板
     *
     * @param template 待创建的分子模板对象
     * @return 保存后的分子模板对象
     */
    @Transactional
    public MoleculeTemplate createTemplate(MoleculeTemplate template) {
        log.info("Creating molecule template: {}", template.getMoleculeName());
        return moleculeTemplateRepository.save(template);
    }

    /**
     * 更新分子模板信息
     *
     * @param id 模板ID
     * @param template 包含更新信息的分子模板对象
     * @return 包含更新后模板的Optional对象，若模板不存在则为空
     */
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

    /**
     * 删除分子模板
     *
     * @param id 模板ID
     * @return true表示删除成功，false表示模板不存在
     */
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