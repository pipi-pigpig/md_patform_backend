package com.mdplatform.management.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 目标计算性质校验器。
 *
 * <p>合法值（与 Apifox 项目「电解液MD计算平台」创建模拟任务接口定义一致）：
 * <ul>
 *   <li>density — 密度</li>
 *   <li>conductivity — 电导率</li>
 *   <li>viscosity — 粘度</li>
 *   <li>dielectric_constant — 介电常数</li>
 *   <li>solvation_structure — 溶剂化结构</li>
 * </ul>
 */
public final class TargetPropertiesValidator {

    private static final Set<String> LEGAL_KEYS = Set.of(
            "density",
            "conductivity",
            "viscosity",
            "dielectric_constant",
            "solvation_structure"
    );

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TargetPropertiesValidator() {
    }

    /**
     * 解析并校验 target_properties JSON 字符串。
     *
     * @param json JSON 字符串，期望为非空字符串数组
     * @return 解析后的 key 列表
     * @throws IllegalArgumentException 当为空、非数组、含非法 key 或含非字符串元素时
     */
    public static List<String> parseAndValidate(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("目标计算性质不能为空");
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException("目标计算性质 JSON 格式无效: " + e.getMessage(), e);
        }

        if (!root.isArray()) {
            throw new IllegalArgumentException("目标计算性质必须是数组");
        }
        if (root.isEmpty()) {
            throw new IllegalArgumentException("目标计算性质不能为空");
        }

        List<String> keys = new ArrayList<>(root.size());
        for (JsonNode element : root) {
            if (!element.isTextual()) {
                throw new IllegalArgumentException("目标计算性质数组元素必须是字符串");
            }
            String key = element.asText();
            if (!LEGAL_KEYS.contains(key)) {
                throw new IllegalArgumentException("非法的目标计算性质: " + key);
            }
            keys.add(key);
        }
        return keys;
    }

    /**
     * 仅判断是否合法，不抛异常。
     */
    public static boolean isValid(String json) {
        try {
            parseAndValidate(json);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
