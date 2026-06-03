package com.mdplatform.engine.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormulaCalculationResult {

    private List<MoleculeCountResult> molecules;

    private BoxSizeResult boxSize;

    private Integer totalAtoms;

    private Double totalCharge;

    private Boolean isElectricallyNeutral;
}