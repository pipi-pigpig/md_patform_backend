package com.mdplatform.management.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * 配方校验结果。
 */
@Data
@AllArgsConstructor
public class ValidationResult {
    private boolean success;
    private Integer totalAtomCount;
    private List<String> errors;

    public static ValidationResult failure(List<String> errors) {
        return new ValidationResult(false, null, errors);
    }

    public static ValidationResult success(int totalAtomCount) {
        return new ValidationResult(true, totalAtomCount, Collections.emptyList());
    }
}
