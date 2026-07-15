package com.mdplatform.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 配方计算结果DTO - 用于返回配方计算的完整结果
 *
 * <p>功能：
 *     1. 封装配方计算的所有结果信息，包括分子数量、盒子尺寸、电荷等
 *     2. 提供电中性校验结果
 *     3. 用于前端展示和后续建模流程
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormulaCalculationResult {

    /** 各分子类型的数量计算结果列表 */
    private List<MoleculeCountResult> molecules;

    /** 盒子尺寸计算结果 */
    private BoxSizeResult boxSize;

    /** 系统总原子数 */
    private Integer totalAtoms;

    /** 系统总电荷 */
    private Double totalCharge;

    /** 系统是否电中性 */
    private Boolean isElectricallyNeutral;
}
