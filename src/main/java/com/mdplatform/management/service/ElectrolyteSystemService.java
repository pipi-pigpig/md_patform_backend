package com.mdplatform.management.service;

import com.mdplatform.management.dto.*;
import com.mdplatform.management.dto.ValidationResult;
import com.mdplatform.management.model.ElectrolyteSystem;
import com.mdplatform.management.repository.ElectrolyteSystemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ElectrolyteSystemService {

    private static final double MIN_TEMPERATURE = 200.0;
    private static final double MAX_TEMPERATURE = 500.0;
    private static final double MIN_PRESSURE = 0.1;
    private static final double MAX_PRESSURE = 1000.0;
    private static final int MAX_NAME_LENGTH = 200;

    private final ElectrolyteSystemRepository systemRepository;
    private final SimulationJobCheckService simulationJobCheckService;
    private final FormulaValidationService formulaValidationService;

    public ElectrolyteSystemResponse createSystem(Long userId, ElectrolyteSystemCreateRequest request) {
        validateCreateRequest(request);
        ValidationResult validationResult = formulaValidationService.validate(request);
        if (!validationResult.isSuccess()) {
            throw new IllegalArgumentException(validationResult.getErrors().get(0));
        }

        ElectrolyteSystem system = new ElectrolyteSystem();
        system.setSystemName(request.getName());
        system.setUserId(userId);
        system.setTaskDescription(request.getTaskDescription());
        system.setSolventInfo(request.getSolventInfoJson());
        system.setSaltInfo(request.getSaltInfoJson());
        system.setAdditiveInfo(request.getAdditiveInfoJson());
        system.setTemperature(request.getTemperature());
        system.setPressure(request.getPressure());
        system.setBoxSize(request.getBoxSizeJson());
        system.setBoundaryConditions(request.getBoundaryConditions() != null ? request.getBoundaryConditions() : "p p p");
        system.setTotalAtomCount(validationResult.getTotalAtomCount());
        system.setIsPublicTemplate(false);
        system.setCreateTime(LocalDateTime.now());
        system.setUpdateTime(LocalDateTime.now());

        ElectrolyteSystem saved = systemRepository.save(system);
        log.info("Created electrolyte system: {} for user: {} (totalAtomCount={})", saved.getSystemName(), userId, saved.getTotalAtomCount());
        return ElectrolyteSystemResponse.fromEntity(saved);
    }

    public ElectrolyteSystemResponse updateSystem(Long userId, Long systemId, ElectrolyteSystemCreateRequest request) {
        ElectrolyteSystem existing = findAndCheckOwnership(userId, systemId);

        if (simulationJobCheckService.hasRunningJobs(systemId)) {
            throw new IllegalArgumentException("配方有关联的运行中任务，无法编辑");
        }

        validateCreateRequest(request);
        ValidationResult validationResult = formulaValidationService.validate(request);
        if (!validationResult.isSuccess()) {
            throw new IllegalArgumentException(validationResult.getErrors().get(0));
        }

        existing.setSystemName(request.getName());
        existing.setTaskDescription(request.getTaskDescription());
        existing.setSolventInfo(request.getSolventInfoJson());
        existing.setSaltInfo(request.getSaltInfoJson());
        existing.setAdditiveInfo(request.getAdditiveInfoJson());
        existing.setTemperature(request.getTemperature());
        existing.setPressure(request.getPressure());
        existing.setBoxSize(request.getBoxSizeJson());
        if (request.getBoundaryConditions() != null) {
            existing.setBoundaryConditions(request.getBoundaryConditions());
        }
        existing.setTotalAtomCount(validationResult.getTotalAtomCount());
        existing.setUpdateTime(LocalDateTime.now());

        ElectrolyteSystem updated = systemRepository.save(existing);
        log.info("Updated electrolyte system: {} for user: {}", systemId, userId);
        return ElectrolyteSystemResponse.fromEntity(updated);
    }

    public void deleteSystem(Long userId, Long systemId) {
        ElectrolyteSystem existing = findAndCheckOwnership(userId, systemId);

        if (simulationJobCheckService.hasAssociatedJobs(systemId)) {
            throw new IllegalArgumentException("配方存在关联任务，无法删除");
        }

        systemRepository.deleteById(systemId);
        log.info("Deleted electrolyte system: {} for user: {}", systemId, userId);
    }

    public PageResponse<ElectrolyteSystemListResponse> listSystems(Long userId, String keyword, int page, int pageSize) {
        int safePage = Math.max(page - 1, 0);
        int safeSize = Math.max(pageSize, 1);
        PageRequest pageRequest = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createTime"));

        Page<ElectrolyteSystem> result;
        if (keyword != null && !keyword.isBlank()) {
            result = systemRepository.searchByUserIdAndKeyword(userId, keyword.trim(), pageRequest);
        } else {
            result = systemRepository.findByUserIdOrderByCreateTimeDesc(userId, pageRequest);
        }

        List<ElectrolyteSystemListResponse> list = result.getContent().stream()
                .map(ElectrolyteSystemListResponse::fromEntity)
                .collect(Collectors.toList());

        return PageResponse.of(result.getTotalElements(), page, safeSize, list);
    }

    public ElectrolyteSystemResponse getSystemDetail(Long systemId) {
        ElectrolyteSystem system = systemRepository.findById(systemId)
                .orElseThrow(() -> new IllegalArgumentException("配方不存在"));
        return ElectrolyteSystemResponse.fromEntity(system);
    }

    public ElectrolyteSystemResponse saveAsTemplate(Long userId, Long systemId, boolean isPublic) {
        ElectrolyteSystem existing = findAndCheckOwnership(userId, systemId);

        existing.setIsPublicTemplate(isPublic);
        existing.setUpdateTime(LocalDateTime.now());

        ElectrolyteSystem updated = systemRepository.save(existing);
        log.info("Saved electrolyte system {} as template (public={}) for user: {}", systemId, isPublic, userId);
        return ElectrolyteSystemResponse.fromEntity(updated);
    }

    public ElectrolyteSystemResponse createFromTemplate(Long userId, Long templateId, String newName) {
        ElectrolyteSystem template = systemRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("模板配方不存在"));

        if (!template.getIsPublicTemplate() && !template.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权限复用该模板");
        }

        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("新配方名称不能为空");
        }

        ElectrolyteSystem newSystem = new ElectrolyteSystem();
        newSystem.setSystemName(newName);
        newSystem.setUserId(userId);
        newSystem.setTaskDescription(template.getTaskDescription());
        newSystem.setSolventInfo(template.getSolventInfo());
        newSystem.setSaltInfo(template.getSaltInfo());
        newSystem.setAdditiveInfo(template.getAdditiveInfo());
        newSystem.setMoleculeStatistics(template.getMoleculeStatistics());
        newSystem.setTemperature(template.getTemperature());
        newSystem.setPressure(template.getPressure());
        newSystem.setBoxSize(template.getBoxSize());
        newSystem.setBoundaryConditions(template.getBoundaryConditions());
        newSystem.setTotalAtomCount(template.getTotalAtomCount());
        newSystem.setIsPublicTemplate(false);
        newSystem.setCreateTime(LocalDateTime.now());
        newSystem.setUpdateTime(LocalDateTime.now());

        ElectrolyteSystem saved = systemRepository.save(newSystem);
        log.info("Created electrolyte system from template {} for user: {}", templateId, userId);
        return ElectrolyteSystemResponse.fromEntity(saved);
    }

    private void validateCreateRequest(ElectrolyteSystemCreateRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("配方名称不能为空");
        }
        if (request.getName().length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("配方名称不能超过" + MAX_NAME_LENGTH + "字符");
        }
        if (request.getSolventInfoJson() == null || request.getSolventInfoJson().isBlank()) {
            throw new IllegalArgumentException("溶剂信息不能为空");
        }
        if (request.getTemperature() != null && (request.getTemperature() < MIN_TEMPERATURE || request.getTemperature() > MAX_TEMPERATURE)) {
            throw new IllegalArgumentException("温度范围应在" + MIN_TEMPERATURE + "-" + MAX_TEMPERATURE + "K之间");
        }
        if (request.getPressure() != null && (request.getPressure() < MIN_PRESSURE || request.getPressure() > MAX_PRESSURE)) {
            throw new IllegalArgumentException("压强范围应在" + MIN_PRESSURE + "-" + MAX_PRESSURE + "bar之间");
        }
    }

    private ElectrolyteSystem findAndCheckOwnership(Long userId, Long systemId) {
        ElectrolyteSystem existing = systemRepository.findById(systemId)
                .orElseThrow(() -> new IllegalArgumentException("配方不存在"));
        if (!existing.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权限操作该配方");
        }
        return existing;
    }
}
