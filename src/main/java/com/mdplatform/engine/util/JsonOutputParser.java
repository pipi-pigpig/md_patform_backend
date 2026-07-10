package com.mdplatform.engine.util;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * JSON输出解析工具 - 从外部工具（Python脚本、LAMMPS等）的输出中提取JSON内容
 *
 * <p>功能：</p>
 * <ol>
 *   <li>优先通过标记（===JSON_RESULT=== / ===END_JSON===）提取JSON</li>
 *   <li>标记不存在时，从输出末尾向前搜索有效的JSON对象或数组</li>
 *   <li>通过ObjectMapper验证提取的字符串是否为合法JSON</li>
 * </ol>
 *
 * <p>解析策略说明：</p>
 * <ul>
 *   <li>策略1（标记提取）：Python脚本使用 ===JSON_RESULT=== / ===END_JSON=== 标记包裹JSON输出，
 *       这是最可靠的方式，能精确提取JSON内容</li>
 *   <li>策略2（反向搜索）：当标记不存在时，从输出末尾向前逐个查找 { 或 [ 字符，
 *       尝试从该位置截取子串并用ObjectMapper验证。从末尾向前搜索是因为
 *       LAMMPS/Moltemplate的日志输出中可能包含花括号，但有效的JSON通常出现在输出的最后部分</li>
 * </ul>
 *
 * <p>使用方法：</p>
 * <pre>
 *   String jsonStr = JsonOutputParser.extractJson(pythonOutput);
 *   if (jsonStr != null) {
 *       MyResult result = objectMapper.readValue(jsonStr, MyResult.class);
 *   }
 * </pre>
 *
 * @author 电解液MD平台
 * @version 1.0.0
 * @since 2026-06-14
 */
@Slf4j
public final class JsonOutputParser {

    /** JSON结果开始标记，Python脚本输出JSON时使用此标记包裹 */
    private static final String JSON_START_MARKER = "===JSON_RESULT===";

    /** JSON结果结束标记，Python脚本输出JSON时使用此标记包裹 */
    private static final String JSON_END_MARKER = "===END_JSON===";

    /** JSON解析器，用于验证提取的字符串是否为合法JSON */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 严格模式的JSON解析器，启用FAIL_ON_TRAILING_TOKENS以拒绝含末尾残留内容的JSON */
    private static final ObjectMapper STRICT_OBJECT_MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    /**
     * 私有构造函数，防止实例化
     */
    private JsonOutputParser() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * 从外部工具输出中提取JSON字符串
     *
     * <p>提取策略（按优先级）：</p>
     * <ol>
     *   <li>策略1：查找 ===JSON_RESULT=== / ===END_JSON=== 标记，提取标记之间的内容</li>
     *   <li>策略2：从输出末尾向前搜索，尝试从每个 { 或 [ 位置截取子串并用ObjectMapper验证，
     *       返回最后一个能成功解析的合法JSON字符串</li>
     * </ol>
     *
     * @param output 外部工具的完整标准输出
     * @return 提取出的合法JSON字符串；如果未找到有效JSON则返回null
     */
    public static String extractJson(String output) {
        if (output == null || output.trim().isEmpty()) {
            log.debug("[JSON解析] 输出为空，无法提取JSON");
            return null;
        }

        String trimmedOutput = output.trim();

        // 策略1：尝试通过标记提取JSON
        String markerResult = extractByMarkers(trimmedOutput);
        if (markerResult != null) {
            log.debug("[JSON解析] 通过标记成功提取JSON");
            return markerResult;
        }

        // 策略2：从末尾向前搜索有效的JSON
        String searchResult = extractByBackwardSearch(trimmedOutput);
        if (searchResult != null) {
            log.debug("[JSON解析] 通过反向搜索成功提取JSON");
            return searchResult;
        }

        log.warn("[JSON解析] 未能从输出中提取有效JSON，输出长度: {}", trimmedOutput.length());
        return null;
    }

    /**
     * 策略1：通过 ===JSON_RESULT=== / ===END_JSON=== 标记提取JSON
     *
     * <p>Python脚本约定使用标记包裹JSON输出，格式如下：</p>
     * <pre>
     * [执行日志...]
     * ===JSON_RESULT===
     * {"key": "value"}
     * ===END_JSON===
     * </pre>
     *
     * @param output 待解析的输出字符串
     * @return 标记之间的JSON字符串；如果标记不存在或内容无效则返回null
     */
    private static String extractByMarkers(String output) {
        int jsonStart = output.indexOf(JSON_START_MARKER);
        int jsonEnd = output.indexOf(JSON_END_MARKER);

        if (jsonStart < 0 || jsonEnd <= jsonStart) {
            log.debug("[JSON解析] 未找到有效的JSON标记");
            return null;
        }

        // 提取标记之间的内容
        String jsonContent = output.substring(
                jsonStart + JSON_START_MARKER.length(), jsonEnd).trim();

        if (jsonContent.isEmpty()) {
            log.warn("[JSON解析] JSON标记之间存在空内容");
            return null;
        }

        // 验证提取的内容是否为合法JSON
        if (isValidJson(jsonContent)) {
            return jsonContent;
        }

        log.warn("[JSON解析] JSON标记之间的内容不是合法JSON: {}", jsonContent);
        return null;
    }

    /**
     * 策略2：从输出末尾向前搜索有效的JSON对象
     *
     * <p>优先搜索JSON对象（{}），仅当找不到对象时才搜索JSON数组（[]）。
     * 使用STRICT_OBJECT_MAPPER验证，拒绝带有末尾残留内容的非法JSON。</p>
     *
     * <p>增强策略：当从某个 { 位置截取的完整子串不是合法JSON时，
     * 不立即跳过，而是从该位置尝试截取更短的子串（从 { 到每个 } 或 ] 位置），
     * 以处理输出末尾有额外注释或内容的情况。</p>
     *
     * @param output 待解析的输出字符串
     * @return 合法的JSON字符串；如果未找到则返回null
     */
    private static String extractByBackwardSearch(String output) {
        // 第一遍：优先搜索JSON对象（{}）
        for (int i = output.length() - 1; i >= 0; i--) {
            if (output.charAt(i) == '{') {
                // 尝试从当前 { 位置到不同的结束位置截取，以处理JSON后有额外内容的情况
                for (int j = output.length(); j > i; j--) {
                    String candidate = output.substring(i, j).trim();
                    if (isValidJson(candidate)) {
                        return candidate;
                    }
                }
            }
        }

        // 第二遍：找不到对象时，搜索JSON数组（[]）
        for (int i = output.length() - 1; i >= 0; i--) {
            if (output.charAt(i) == '[') {
                for (int j = output.length(); j > i; j--) {
                    String candidate = output.substring(i, j).trim();
                    if (isValidJson(candidate)) {
                        return candidate;
                    }
                }
            }
        }

        log.debug("[JSON解析] 反向搜索未找到有效JSON");
        return null;
    }

    /**
     * 验证字符串是否为合法的JSON（严格模式）
     *
     * <p>使用启用了FAIL_ON_TRAILING_TOKENS的ObjectMapper验证JSON字符串，
     * 拒绝带有末尾残留内容的非法JSON（如 "[]\n}" 会因为末尾的 "}" 而被拒绝）。</p>
     *
     * @param jsonStr 待验证的字符串
     * @return true表示是合法JSON，false表示不是
     */
    private static boolean isValidJson(String jsonStr) {
        try {
            STRICT_OBJECT_MAPPER.readTree(jsonStr);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
