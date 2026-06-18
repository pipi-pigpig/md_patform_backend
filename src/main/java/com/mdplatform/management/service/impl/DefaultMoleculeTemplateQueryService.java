package com.mdplatform.management.service.impl;

import com.mdplatform.management.dto.MoleculeSnapshot;
import com.mdplatform.management.service.MoleculeTemplateQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 分子模板查询服务的真实实现 stub（待 engine 方接入）。
 * <p>
 * 当前抛出 UnsupportedOperationException，待另一方通过 Apifox 对接 MoleculeTemplateService 后填充。
 */
@Service
@Profile("!mock")
@Slf4j
public class DefaultMoleculeTemplateQueryService implements MoleculeTemplateQueryService {

    @Override
    public Optional<MoleculeSnapshot> findByName(String name) {
        throw new UnsupportedOperationException("待 MD 引擎方接入 MoleculeTemplateService");
    }

    @Override
    public List<String> systemTemplateNames() {
        throw new UnsupportedOperationException("待 MD 引擎方接入 MoleculeTemplateService");
    }
}
