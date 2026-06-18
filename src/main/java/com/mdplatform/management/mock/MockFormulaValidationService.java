package com.mdplatform.management.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdplatform.management.dto.ElectrolyteSystemCreateRequest;
import com.mdplatform.management.dto.MoleculeSnapshot;
import com.mdplatform.management.dto.ValidationResult;
import com.mdplatform.management.service.FormulaValidationService;
import com.mdplatform.management.service.MoleculeTemplateQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 配方校验服务的 Mock 实现。
 * <p>
 * 校验规则：
 * <ol>
 *   <li>溶剂摩尔分数之和 = 1.0（误差 1e-3）</li>
 *   <li>每个溶剂/盐/添加剂名称在分子模板表中存在</li>
 *   <li>盒子边长 x/y/z ≥ 20Å</li>
 *   <li>体系电中性：Σ(摩尔分数 × netCharge) + 盐净电荷合计 = 0
 *       （Mock 模板净电荷全 0，盐浓度统一按 0 处理；真实实现需查模板 netCharge 累加）</li>
 *   <li>计算 totalAtomCount = Σ(溶剂摩尔分数 × atomCount)</li>
 * </ol>
 */
@Service
@Profile("mock")
@RequiredArgsConstructor
@Slf4j
public class MockFormulaValidationService implements FormulaValidationService {

    private static final double MOLE_FRACTION_TOLERANCE = 1e-3;
    private static final double MIN_BOX_SIZE = 20.0;

    private final MoleculeTemplateQueryService moleculeTemplateQueryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ValidationResult validate(ElectrolyteSystemCreateRequest request) {
        List<String> errors = new ArrayList<>();
        int totalAtomCount = 0;

        // 1. 解析并校验溶剂信息
        JsonNode solventNode = parseJson(request.getSolventInfoJson(), "溶剂信息", errors);
        if (solventNode != null) {
            JsonNode solvents = solventNode.get("solvents");
            if (solvents == null || !solvents.isArray() || solvents.isEmpty()) {
                errors.add("溶剂列表不能为空");
            } else {
                double moleFractionSum = 0.0;
                for (JsonNode s : solvents) {
                    String name = textField(s, "name");
                    Double moleFraction = doubleField(s, "moleFraction");
                    if (name == null) {
                        errors.add("溶剂名称不能为空");
                        continue;
                    }
                    Optional<MoleculeSnapshot> snapshot = moleculeTemplateQueryService.findByName(name);
                    if (snapshot.isEmpty()) {
                        errors.add("分子模板不存在: " + name);
                        continue;
                    }
                    if (moleFraction == null || moleFraction < 0) {
                        errors.add("溶剂 " + name + " 摩尔分数无效");
                        continue;
                    }
                    moleFractionSum += moleFraction;
                    if (snapshot.get().getAtomCount() != null) {
                        totalAtomCount += moleFraction * snapshot.get().getAtomCount();
                    }
                }
                if (Math.abs(moleFractionSum - 1.0) > MOLE_FRACTION_TOLERANCE) {
                    errors.add("溶剂摩尔分数之和必须为1.0，当前为 " + String.format("%.4f", moleFractionSum));
                }
            }
        }

        // 2. 解析并校验盐信息（仅校验模板存在性）
        JsonNode saltNode = parseJson(request.getSaltInfoJson(), "盐信息", errors);
        if (saltNode != null) {
            JsonNode salts = saltNode.get("salts");
            if (salts != null && salts.isArray()) {
                for (JsonNode s : salts) {
                    String name = textField(s, "name");
                    if (name != null && moleculeTemplateQueryService.findByName(name).isEmpty()) {
                        errors.add("分子模板不存在: " + name);
                    }
                }
            }
        }

        // 3. 解析并校验添加剂信息（仅校验模板存在性）
        JsonNode additiveNode = parseJson(request.getAdditiveInfoJson(), "添加剂信息", errors);
        if (additiveNode != null) {
            JsonNode additives = additiveNode.get("additives");
            if (additives != null && additives.isArray()) {
                for (JsonNode a : additives) {
                    String name = textField(a, "name");
                    if (name != null && moleculeTemplateQueryService.findByName(name).isEmpty()) {
                        errors.add("分子模板不存在: " + name);
                    }
                }
            }
        }

        // 4. 校验盒子边长
        JsonNode boxNode = parseJson(request.getBoxSizeJson(), "盒子尺寸", errors);
        if (boxNode != null) {
            Double x = doubleField(boxNode, "x");
            Double y = doubleField(boxNode, "y");
            Double z = doubleField(boxNode, "z");
            if (x != null && x < MIN_BOX_SIZE) {
                errors.add("盒子边长x必须≥" + MIN_BOX_SIZE + "Å");
            }
            if (y != null && y < MIN_BOX_SIZE) {
                errors.add("盒子边长y必须≥" + MIN_BOX_SIZE + "Å");
            }
            if (z != null && z < MIN_BOX_SIZE) {
                errors.add("盒子边长z必须≥" + MIN_BOX_SIZE + "Å");
            }
        }

        // 5. 电中性校验（Mock 数据净电荷全 0，跳过；真实实现需累加 Σ(摩尔分数 × netCharge) + 盐净电荷）
        // TODO: 真实实现需在此处实现电中性校验

        if (!errors.isEmpty()) {
            log.debug("Formula validation failed: {}", errors);
            return ValidationResult.failure(errors);
        }
        return ValidationResult.success(totalAtomCount);
    }

    private JsonNode parseJson(String json, String fieldLabel, List<String> errors) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            errors.add(fieldLabel + " JSON 解析失败: " + e.getMessage());
            return null;
        }
    }

    private String textField(JsonNode node, String fieldName) {
        JsonNode child = node.get(fieldName);
        return (child == null || child.isNull()) ? null : child.asText();
    }

    private Double doubleField(JsonNode node, String fieldName) {
        JsonNode child = node.get(fieldName);
        if (child == null || child.isNull()) {
            return null;
        }
        if (child.isNumber()) {
            return child.asDouble();
        }
        try {
            return Double.parseDouble(child.asText());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
