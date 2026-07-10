package com.mdplatform.engine.service;

import com.mdplatform.engine.dto.*;
import com.mdplatform.engine.model.*;
import com.mdplatform.engine.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 计算结果服务类，提供计算结果的增删查业务逻辑，
 * 支持主表与性质子表的联合操作
 *
 * <p>功能：
 *     1. 查询计算结果时自动关联对应性质子表数据
 *     2. 创建计算结果时同时保存主表和子表数据
 *     3. 删除计算结果时级联删除子表数据
 *     4. 支持按任务ID、性质类型等条件查询
 * </p>
 *
 * @author 电解液MD平台
 * @version 2.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CalculationResultService {

    private final CalculationResultRepository calculationResultRepository;
    private final DensityResultRepository densityResultRepository;
    private final ViscosityResultRepository viscosityResultRepository;
    private final ConductivityResultRepository conductivityResultRepository;
    private final DielectricResultRepository dielectricResultRepository;
    private final SolvationResultRepository solvationResultRepository;

    /**
     * 获取所有计算结果列表（含子表数据）
     *
     * @return 计算结果DTO列表
     */
    public List<CalculationResultDto> getAllResults() {
        log.info("[计算结果] 查询所有计算结果");
        List<CalculationResult> results = calculationResultRepository.findAll();
        List<CalculationResultDto> dtoList = results.stream()
                .map(this::buildDtoWithSubTable)
                .collect(Collectors.toList());
        log.info("[计算结果] 查询完成，共{}条记录", dtoList.size());
        return dtoList;
    }

    /**
     * 根据ID获取计算结果详情（含子表数据）
     *
     * @param id 计算结果ID
     * @return 计算结果DTO
     */
    public Optional<CalculationResultDto> getResultById(Long id) {
        log.info("[计算结果] 根据ID查询计算结果: id={}", id);
        return calculationResultRepository.findById(id)
                .map(this::buildDtoWithSubTable);
    }

    /**
     * 根据任务ID获取计算结果列表（含子表数据）
     *
     * @param jobId 任务ID
     * @return 计算结果DTO列表
     */
    public List<CalculationResultDto> getResultsByJobId(Long jobId) {
        log.info("[计算结果] 根据任务ID查询计算结果: jobId={}", jobId);
        List<CalculationResult> results = calculationResultRepository.findByJobIdOrderByCreateTimeDesc(jobId);
        List<CalculationResultDto> dtoList = results.stream()
                .map(this::buildDtoWithSubTable)
                .collect(Collectors.toList());
        log.info("[计算结果] 查询完成: jobId={}, 结果数={}", jobId, dtoList.size());
        return dtoList;
    }

    /**
     * 根据任务ID和性质类型获取计算结果列表（含子表数据）
     *
     * @param jobId        任务ID
     * @param propertyName 性质名称
     * @return 计算结果DTO列表
     */
    public List<CalculationResultDto> getResultsByJobIdAndPropertyName(Long jobId, String propertyName) {
        log.info("[计算结果] 根据任务ID和性质类型查询: jobId={}, propertyName={}", jobId, propertyName);
        List<CalculationResultDto> dtoList = calculationResultRepository.findByJobIdAndPropertyName(jobId, propertyName)
                .map(result -> {
                    List<CalculationResultDto> list = new ArrayList<>();
                    list.add(buildDtoWithSubTable(result));
                    return list;
                })
                .orElse(new ArrayList<>());
        log.info("[计算结果] 查询完成: jobId={}, propertyName={}, 结果数={}", jobId, propertyName, dtoList.size());
        return dtoList;
    }

    /**
     * 根据性质类型获取所有计算结果（含子表数据）
     *
     * @param propertyName 性质名称
     * @return 计算结果DTO列表
     */
    public List<CalculationResultDto> getResultsByPropertyName(String propertyName) {
        log.info("[计算结果] 根据性质类型查询: propertyName={}", propertyName);
        List<CalculationResult> results = calculationResultRepository.findByPropertyName(propertyName);
        List<CalculationResultDto> dtoList = results.stream()
                .map(this::buildDtoWithSubTable)
                .collect(Collectors.toList());
        log.info("[计算结果] 查询完成: propertyName={}, 结果数={}", propertyName, dtoList.size());
        return dtoList;
    }

    /**
     * 创建新的计算结果（同时保存主表和子表数据）
     *
     * @param dto 计算结果DTO，包含公共字段和子表数据
     * @return 保存后的计算结果DTO
     */
    @Transactional
    public CalculationResultDto createResult(CalculationResultDto dto) {
        log.info("[计算结果] 创建计算结果: jobId={}, propertyName={}", dto.getJobId(), dto.getPropertyName());

        // 1. 保存主表
        CalculationResult result = new CalculationResult();
        result.setJobId(dto.getJobId());
        result.setPropertyName(dto.getPropertyName());
        result.setPropertyValue(dto.getPropertyValue());
        result.setPropertyUnit(dto.getPropertyUnit());
        result.setCalculationMethod(dto.getCalculationMethod());
        result.setTemperatureK(dto.getTemperatureK());
        result.setPressureBar(dto.getPressureBar());
        result.setSamplingTimePs(dto.getSamplingTimePs());
        result.setConvergenceStatus(dto.getConvergenceStatus());
        result.setPropertyDetail(dto.getPropertyDetail());
        result.setRawDataPath(dto.getRawDataPath());
        result.setChartDataPath(dto.getChartDataPath());

        CalculationResult savedResult = calculationResultRepository.save(result);
        Long resultId = savedResult.getResultId();

        // 2. 根据propertyName保存对应子表
        Object detailData = dto.getPropertyDetailData();
        if (detailData != null) {
            saveSubTableData(resultId, dto.getPropertyName(), detailData);
        }

        log.info("[计算结果] 创建成功: resultId={}", resultId);
        return buildDtoWithSubTable(savedResult);
    }

    /**
     * 删除计算结果（级联删除子表数据）
     *
     * @param id 计算结果ID
     * @return true表示删除成功，false表示结果不存在
     */
    @Transactional
    public boolean deleteResult(Long id) {
        log.info("[计算结果] 删除计算结果: id={}", id);
        if (calculationResultRepository.existsById(id)) {
            // 子表通过外键ON DELETE CASCADE自动删除
            calculationResultRepository.deleteById(id);
            log.info("[计算结果] 删除成功: id={}", id);
            return true;
        }
        log.warn("[计算结果] 计算结果不存在，删除失败: id={}", id);
        return false;
    }

    /**
     * 根据CalculationResult实体构建包含子表数据的DTO
     *
     * @param result 计算结果主表实体
     * @return 包含子表数据的DTO
     */
    private CalculationResultDto buildDtoWithSubTable(CalculationResult result) {
        CalculationResultDto dto = CalculationResultDto.fromEntity(result);
        if (result.getResultId() != null && result.getPropertyName() != null) {
            dto.setPropertyDetailData(loadSubTableData(result.getResultId(), result.getPropertyName()));
        }
        return dto;
    }

    /**
     * 根据性质类型加载子表数据
     *
     * @param resultId     计算结果ID
     * @param propertyName 性质名称
     * @return 子表数据DTO对象
     */
    private Object loadSubTableData(Long resultId, String propertyName) {
        switch (propertyName) {
            case "density":
                return DensityResultDto.fromEntity(densityResultRepository.findByResultId(resultId));
            case "viscosity":
                return ViscosityResultDto.fromEntity(viscosityResultRepository.findByResultId(resultId));
            case "conductivity":
                return ConductivityResultDto.fromEntity(conductivityResultRepository.findByResultId(resultId));
            case "dielectric":
                return DielectricResultDto.fromEntity(dielectricResultRepository.findByResultId(resultId));
            case "solvation":
                return SolvationResultDto.fromEntity(solvationResultRepository.findByResultId(resultId));
            default:
                log.warn("[计算结果] 未知的性质类型: {}", propertyName);
                return null;
        }
    }

    /**
     * 根据性质类型保存子表数据
     *
     * @param resultId     计算结果ID
     * @param propertyName 性质名称
     * @param detailData   子表数据对象
     */
    private void saveSubTableData(Long resultId, String propertyName, Object detailData) {
        switch (propertyName) {
            case "density":
                if (detailData instanceof DensityResultDto) {
                    DensityResult entity = new DensityResult();
                    entity.setResultId(resultId);
                    DensityResultDto dto = (DensityResultDto) detailData;
                    entity.setDensityTensor(dto.getDensityTensor());
                    entity.setComponentDensity(dto.getComponentDensity());
                    densityResultRepository.save(entity);
                }
                break;
            case "viscosity":
                if (detailData instanceof ViscosityResultDto) {
                    ViscosityResult entity = new ViscosityResult();
                    entity.setResultId(resultId);
                    ViscosityResultDto dto = (ViscosityResultDto) detailData;
                    entity.setViscosityValue(dto.getViscosityValue());
                    entity.setShearRate(dto.getShearRate());
                    entity.setStressResponse(dto.getStressResponse());
                    entity.setKinematicViscosity(dto.getKinematicViscosity());
                    viscosityResultRepository.save(entity);
                }
                break;
            case "conductivity":
                if (detailData instanceof ConductivityResultDto) {
                    ConductivityResult entity = new ConductivityResult();
                    entity.setResultId(resultId);
                    ConductivityResultDto dto = (ConductivityResultDto) detailData;
                    entity.setConductivityTensor(dto.getConductivityTensor());
                    entity.setIonContribution(dto.getIonContribution());
                    entity.setElectricFieldStrength(dto.getElectricFieldStrength());
                    entity.setResistivity(dto.getResistivity());
                    conductivityResultRepository.save(entity);
                }
                break;
            case "dielectric":
                if (detailData instanceof DielectricResultDto) {
                    DielectricResult entity = new DielectricResult();
                    entity.setResultId(resultId);
                    DielectricResultDto dto = (DielectricResultDto) detailData;
                    entity.setDielectricConstantTensor(dto.getDielectricConstantTensor());
                    entity.setStaticDielectricConstant(dto.getStaticDielectricConstant());
                    entity.setDielectricSpectrumData(dto.getDielectricSpectrumData());
                    entity.setDipoleMomentData(dto.getDipoleMomentData());
                    entity.setComponentContribution(dto.getComponentContribution());
                    entity.setSystemSize(dto.getSystemSize());
                    dielectricResultRepository.save(entity);
                }
                break;
            case "solvation":
                if (detailData instanceof SolvationResultDto) {
                    SolvationResult entity = new SolvationResult();
                    entity.setResultId(resultId);
                    SolvationResultDto dto = (SolvationResultDto) detailData;
                    entity.setCentralIonType(dto.getCentralIonType());
                    entity.setSolvationShellStructure(dto.getSolvationShellStructure());
                    entity.setAverageCoordinationNumber(dto.getAverageCoordinationNumber());
                    entity.setCoordinationDistance(dto.getCoordinationDistance());
                    entity.setRdfCharacteristicPeak(dto.getRdfCharacteristicPeak());
                    entity.setHydrogenBondFeature(dto.getHydrogenBondFeature());
                    entity.setSolvationStability(dto.getSolvationStability());
                    entity.setIonSolventInteractionEnergy(dto.getIonSolventInteractionEnergy());
                    solvationResultRepository.save(entity);
                }
                break;
            default:
                log.warn("[计算结果] 未知的性质类型，跳过子表保存: {}", propertyName);
        }
    }
}
