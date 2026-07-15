package com.mdplatform.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分子数量计算结果DTO - 用于返回单个分子类型的数量计算结果
 *
 * <p>功能：
 *     1. 封装单个分子类型的名称、ID、数量和电荷信息
 *     2. 作为FormulaCalculationResult的子项使用
 * </p>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoleculeCountResult {

    /** 分子名称 */
    private String name;

    /** 分子模板ID */
    private Long moleculeId;

    /** 分子数量 */
    private Integer count;

    /** 分子电荷 */
    private Double charge;
}
