package com.mdplatform.engine.service;

import com.mdplatform.engine.model.ElectrolyteSystem;
import com.mdplatform.engine.repository.SystemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 电解液系统服务类
 *
 * <p>提供电解液系统（ElectrolyteSystem）的增删改查业务逻辑，
 * 包括系统创建、更新、删除、搜索及按用户/公开模板查询等功能。</p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SystemService {

    private final SystemRepository systemRepository;

    /**
     * 获取所有电解液系统，按创建时间降序排列
     *
     * @return 电解液系统列表
     */
    public List<ElectrolyteSystem> getAllSystems() {
        return systemRepository.findAllByOrderByCreateTimeDesc();
    }

    /**
     * 根据系统ID查询电解液系统
     *
     * @param id 系统ID
     * @return 包含电解液系统的Optional对象，若不存在则为空
     */
    public Optional<ElectrolyteSystem> getSystemById(Long id) {
        return systemRepository.findById(id);
    }

    /**
     * 创建新的电解液系统
     *
     * @param system 待创建的电解液系统对象
     * @return 保存后的电解液系统对象
     */
    @Transactional
    public ElectrolyteSystem createSystem(ElectrolyteSystem system) {
        system.setSystemId(null);
        system.setCreateTime(LocalDateTime.now());
        system.setUpdateTime(LocalDateTime.now());
        ElectrolyteSystem saved = systemRepository.save(system);
        log.info("Created system: {} with id: {}", saved.getSystemName(), saved.getSystemId());
        return saved;
    }

    /**
     * 更新电解液系统信息
     *
     * <p>仅更新非空字段，保留原有未修改的字段值。</p>
     *
     * @param id 系统ID
     * @param system 包含更新信息的电解液系统对象
     * @return 包含更新后系统的Optional对象，若系统不存在则为空
     */
    @Transactional
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

    /**
     * 删除电解液系统
     *
     * @param id 系统ID
     * @return true表示删除成功，false表示系统不存在
     */
    @Transactional
    public boolean deleteSystem(Long id) {
        if (systemRepository.existsById(id)) {
            systemRepository.deleteById(id);
            log.info("Deleted system with id: {}", id);
            return true;
        }
        return false;
    }

    /**
     * 根据关键词搜索电解液系统
     *
     * @param keyword 搜索关键词
     * @return 匹配的电解液系统列表
     */
    public List<ElectrolyteSystem> searchSystems(String keyword) {
        return systemRepository.searchByKeyword(keyword);
    }

    /**
     * 根据用户ID查询该用户创建的所有电解液系统
     *
     * @param userId 用户ID
     * @return 该用户的电解液系统列表
     */
    public List<ElectrolyteSystem> getSystemsByUserId(Long userId) {
        return systemRepository.findByUserId(userId);
    }

    /**
     * 获取所有公开模板类型的电解液系统
     *
     * @return 公开模板电解液系统列表
     */
    public List<ElectrolyteSystem> getPublicTemplates() {
        return systemRepository.findByIsPublicTemplateTrue();
    }
}