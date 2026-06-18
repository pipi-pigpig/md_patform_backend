package com.mdplatform.management.service;

import com.mdplatform.management.dto.ElectrolyteSystemCreateRequest;
import com.mdplatform.management.dto.ValidationResult;

/**
 * 配方校验服务。
 * <p>
 * 校验规则（参考《项目常规功能详细设计v1.0》§3）：
 * <ul>
 *   <li>溶剂摩尔分数之和 = 1.0（误差 1e-3）</li>
 *   <li>每个溶剂/盐/添加剂名称在分子模板表中存在</li>
 *   <li>体系电中性：所有组分净电荷加权合计为 0</li>
 *   <li>盒子边长 x/y/z ≥ 20Å</li>
 *   <li>计算体系总原子数 totalAtomCount = Σ(摩尔分数 × atomCount)</li>
 * </ul>
 * <p>
 * 真实实现由 engine 方提供（通过 MoltemplateService 计算原子数、MoleculeTemplateService 校验模板存在性）；
 * 本方通过 Mock 实现进行联调。
 */
public interface FormulaValidationService {

    ValidationResult validate(ElectrolyteSystemCreateRequest request);
}
