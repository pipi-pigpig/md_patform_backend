package com.mdplatform.management.service.impl;

import com.mdplatform.management.dto.ElectrolyteSystemCreateRequest;
import com.mdplatform.management.dto.ValidationResult;
import com.mdplatform.management.service.FormulaValidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 配方校验服务的真实实现 stub（待 engine 方接入）。
 * <p>
 * 当前抛出 UnsupportedOperationException，待另一方通过 Apifox 对接 MoltemplateService
 * （计算原子数）与 MoleculeTemplateService（校验模板存在性）后填充。
 */
@Service
@Profile("!mock")
@Slf4j
public class DefaultFormulaValidationService implements FormulaValidationService {

    @Override
    public ValidationResult validate(ElectrolyteSystemCreateRequest request) {
        throw new UnsupportedOperationException("待 MD 引擎方接入 MoltemplateService 与 MoleculeTemplateService");
    }
}
