package com.mdplatform.management.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 分子模板快照（仅包含配方校验所需字段）。
 * 真实实现由 engine 方提供，本方通过 MoleculeTemplateQueryService 接口查询。
 */
@Data
@AllArgsConstructor
public class MoleculeSnapshot {
    private String moleculeName;
    private String formula;
    private Integer atomCount;
    private Integer netCharge;
}
