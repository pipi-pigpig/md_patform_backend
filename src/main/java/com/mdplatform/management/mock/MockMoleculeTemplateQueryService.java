package com.mdplatform.management.mock;

import com.mdplatform.management.dto.MoleculeSnapshot;
import com.mdplatform.management.service.MoleculeTemplateQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 分子模板查询服务的 Mock 实现。
 * <p>
 * 硬编码前端 Systems.vue 下拉框中所有可选分子，覆盖联调所需的溶剂与锂盐模板：
 * <ul>
 *   <li>溶剂：EC、DMC、EMC、DEC、PC、WATER、ACN</li>
 *   <li>锂盐：LiPF6、LiFSI、LiTFSI、LiBF4、NaCl（按整体分子计原子数）</li>
 * </ul>
 * 真实实现由 engine 方通过 MoleculeTemplateService 提供，待接入后切换至 Default 实现。
 */
@Service
@Profile("mock")
@Slf4j
public class MockMoleculeTemplateQueryService implements MoleculeTemplateQueryService {

    private final Map<String, MoleculeSnapshot> templates = Map.ofEntries(
            Map.entry("EC", new MoleculeSnapshot("EC", "C3H4O3", 10, 0)),
            Map.entry("DMC", new MoleculeSnapshot("DMC", "C3H6O3", 12, 0)),
            Map.entry("EMC", new MoleculeSnapshot("EMC", "C4H8O3", 16, 0)),
            Map.entry("DEC", new MoleculeSnapshot("DEC", "C5H10O3", 19, 0)),
            Map.entry("PC", new MoleculeSnapshot("PC", "C4H6O3", 13, 0)),
            Map.entry("WATER", new MoleculeSnapshot("WATER", "H2O", 3, 0)),
            Map.entry("ACN", new MoleculeSnapshot("ACN", "C2H3N", 6, 0)),
            Map.entry("LiPF6", new MoleculeSnapshot("LiPF6", "LiPF6", 7, 0)),
            Map.entry("LiFSI", new MoleculeSnapshot("LiFSI", "LiF2NO4S2", 8, 0)),
            Map.entry("LiTFSI", new MoleculeSnapshot("LiTFSI", "LiC2F6NO4S2", 14, 0)),
            Map.entry("LiBF4", new MoleculeSnapshot("LiBF4", "LiBF4", 6, 0)),
            Map.entry("NaCl", new MoleculeSnapshot("NaCl", "NaCl", 2, 0))
    );

    @Override
    public Optional<MoleculeSnapshot> findByName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(templates.get(name.trim()));
    }

    @Override
    public java.util.List<String> systemTemplateNames() {
        return templates.keySet().stream().sorted().collect(Collectors.toList());
    }
}
