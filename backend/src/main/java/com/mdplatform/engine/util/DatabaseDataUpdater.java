package com.mdplatform.engine.util;

import java.sql.*;
import java.util.*;

/**
 * 数据库数据更新工具 - 更新NULL字段的默认值
 *
 * <p>功能：
 *     1. 检查并更新 simulation_input_table 的NULL字段
 *     2. 检查并更新 molecule_template_table 的NULL字段
 *     3. 检查并更新 simulation_jobs_table 的NULL字段
 * </p>
 *
 * <p>使用方法：
 *     通过环境变量配置数据库连接信息：
 *     - DB_URL: 数据库连接URL
 *     - DB_USER: 数据库用户名
 *     - DB_PASSWORD: 数据库密码
 * </p>
 *
 * @author MDPlatform
 * @version 1.1
 */
public class DatabaseDataUpdater {

    /** 数据库连接URL，从环境变量读取 */
    private static final String DB_URL = getEnvOrThrow("DB_URL");
    /** 数据库用户名，从环境变量读取 */
    private static final String DB_USER = getEnvOrThrow("DB_USER");
    /** 数据库密码，从环境变量读取 */
    private static final String DB_PASSWORD = getEnvOrThrow("DB_PASSWORD");

    /**
     * 从环境变量获取值，若未设置则抛出异常
     *
     * @param key 环境变量名称
     * @return 环境变量的值
     * @throws IllegalStateException 如果环境变量未设置
     */
    private static String getEnvOrThrow(String key) {
        String value = System.getenv(key);
        if (value == null || value.isEmpty()) {
            throw new IllegalStateException("环境变量 " + key + " 未设置，请配置数据库连接信息");
        }
        return value;
    }

    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("数据库数据完整性检查和更新工具");
        System.out.println("========================================");

        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            System.out.println("\n✅ 数据库连接成功");

            // 1. 检查simulation_input_table的NULL字段
            checkAndUpdateSimulationInput(conn);

            // 2. 检查molecule_template_table的NULL字段
            checkAndUpdateMoleculeTemplate(conn);

            // 3. 检查simulation_jobs_table的NULL字段
            checkAndUpdateSimulationJobs(conn);

            System.out.println("\n========================================");
            System.out.println("✅ 所有检查和更新完成!");
            System.out.println("========================================");

        } catch (SQLException e) {
            System.err.println("❌ 数据库连接失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 检查并更新simulation_input_table的NULL字段
     *
     * @param conn 数据库连接
     * @throws SQLException 数据库操作异常
     */
    private static void checkAndUpdateSimulationInput(Connection conn) throws SQLException {
        System.out.println("\n========================================");
        System.out.println("检查表: simulation_input_table");
        System.out.println("========================================");

        // 先查看表结构
        Statement stmt = conn.createStatement();
        ResultSet cols = stmt.executeQuery("DESCRIBE simulation_input_table");
        System.out.println("\n表结构:");
        String primaryKey = null;
        while (cols.next()) {
            String field = cols.getString("Field");
            String type = cols.getString("Type");
            String key = cols.getString("Key");
            System.out.println("  " + field + " | " + type + " | " + key);
            if ("PRI".equals(key)) {
                primaryKey = field;
            }
        }

        if (primaryKey == null) {
            System.out.println("❌ 未找到主键，无法更新");
            stmt.close();
            return;
        }

        System.out.println("\n主键: " + primaryKey);

        // 查询所有记录
        String querySQL = "SELECT " + primaryKey + ", temperature, pressure, force_field_topology_source, " +
                "minimization_force_threshold, minimization_max_steps, " +
                "equilibrium_density_std_threshold, equilibrium_temp_fluctuation_range, " +
                "minimum_equilibrium_time, minimum_production_time " +
                "FROM simulation_input_table";

        ResultSet rs = stmt.executeQuery(querySQL);

        List<Map<String, Object>> recordsToUpdate = new ArrayList<>();

        while (rs.next()) {
            Object id = rs.getObject(primaryKey);
            Map<String, Object> updates = new LinkedHashMap<>();

            // 检查每个字段是否为NULL，设置合理的默认值
            if (rs.getObject("temperature") == null) {
                updates.put("temperature", 298.15);
            }
            if (rs.getObject("pressure") == null) {
                updates.put("pressure", 1.0);
            }

            // force_field_topology_source: 使用实际的力场文件相对路径
            // 实际文件位置: system_templates/force_fields/opls-aa/oplsaa.lt
            if (rs.getObject("force_field_topology_source") == null ||
                rs.getString("force_field_topology_source") != null &&
                rs.getString("force_field_topology_source").startsWith("uri://")) {
                // 更新为实际的力场文件路径
                updates.put("force_field_topology_source", "system_templates/force_fields/opls-aa/oplsaa.lt");
            }

            if (rs.getObject("minimization_force_threshold") == null) {
                updates.put("minimization_force_threshold", 1.0e-4);
            }
            if (rs.getObject("minimization_max_steps") == null) {
                updates.put("minimization_max_steps", 150000);
            }
            if (rs.getObject("equilibrium_density_std_threshold") == null) {
                updates.put("equilibrium_density_std_threshold", 0.01);
            }
            if (rs.getObject("equilibrium_temp_fluctuation_range") == null) {
                updates.put("equilibrium_temp_fluctuation_range", 5.0);
            }
            if (rs.getObject("minimum_equilibrium_time") == null) {
                updates.put("minimum_equilibrium_time", 5000.0);
            }
            if (rs.getObject("minimum_production_time") == null) {
                updates.put("minimum_production_time", 50.0);
            }

            if (!updates.isEmpty()) {
                updates.put("_primaryKey", id);
                updates.put("_primaryKeyName", primaryKey);
                recordsToUpdate.add(updates);
            }
        }

        System.out.println("发现 " + recordsToUpdate.size() + " 条记录需要更新");

        // 使用PreparedStatement执行更新，防止SQL注入
        int updatedCount = 0;
        for (Map<String, Object> record : recordsToUpdate) {
            Object pkValue = record.remove("_primaryKey");
            String pkName = (String) record.remove("_primaryKeyName");

            StringBuilder setClause = new StringBuilder();
            List<Object> paramValues = new ArrayList<>();
            for (Map.Entry<String, Object> entry : record.entrySet()) {
                if (setClause.length() > 0) {
                    setClause.append(", ");
                }
                setClause.append(entry.getKey()).append(" = ?");
                paramValues.add(entry.getValue());
            }

            String updateSQL = "UPDATE simulation_input_table SET " + setClause +
                    " WHERE " + pkName + " = ?";

            try (PreparedStatement pstmt = conn.prepareStatement(updateSQL)) {
                // 设置SET子句参数
                for (int i = 0; i < paramValues.size(); i++) {
                    pstmt.setObject(i + 1, paramValues.get(i));
                }
                // 设置WHERE条件参数
                pstmt.setObject(paramValues.size() + 1, pkValue);
                pstmt.executeUpdate();
                updatedCount++;
                System.out.println("  ✅ 更新记录" + pkName + "=" + pkValue + ": " + record.keySet());
            } catch (SQLException e) {
                System.out.println("  ❌ 更新记录" + pkName + "=" + pkValue + "失败: " + e.getMessage());
            }
        }

        System.out.println("总计更新 " + updatedCount + " 条记录");
        stmt.close();
    }

    /**
     * 检查并更新molecule_template_table的NULL字段
     *
     * @param conn 数据库连接
     * @throws SQLException 数据库操作异常
     */
    private static void checkAndUpdateMoleculeTemplate(Connection conn) throws SQLException {
        System.out.println("\n========================================");
        System.out.println("检查表: molecule_template_table");
        System.out.println("========================================");

        // 先查看表结构和主键
        Statement stmt1 = conn.createStatement();
        ResultSet cols = stmt1.executeQuery("DESCRIBE molecule_template_table");
        String primaryKey = null;
        while (cols.next()) {
            if ("PRI".equals(cols.getString("Key"))) {
                primaryKey = cols.getString("Field");
            }
        }

        if (primaryKey == null) {
            System.out.println("❌ 未找到主键，无法更新");
            stmt1.close();
            return;
        }

        // 使用不同的Statement对象查询
        Statement stmt2 = conn.createStatement();
        String querySQL = "SELECT " + primaryKey + ", force_field_topology_source FROM molecule_template_table";
        ResultSet rs = stmt2.executeQuery(querySQL);

        // 使用PreparedStatement执行更新，防止SQL注入
        String updateSQL = "UPDATE molecule_template_table SET force_field_topology_source = ? WHERE " + primaryKey + " = ?";
        int updatedCount = 0;
        while (rs.next()) {
            Object pkValue = rs.getObject(primaryKey);
            if (rs.getObject("force_field_topology_source") == null ||
                (rs.getString("force_field_topology_source") != null &&
                 rs.getString("force_field_topology_source").startsWith("uri://"))) {
                try (PreparedStatement pstmt = conn.prepareStatement(updateSQL)) {
                    pstmt.setString(1, "system_templates/force_fields/opls-aa/oplsaa.lt");
                    pstmt.setObject(2, pkValue);
                    pstmt.executeUpdate();
                    updatedCount++;
                    System.out.println("  ✅ 更新记录" + primaryKey + "=" + pkValue);
                } catch (SQLException e) {
                    System.out.println("  ❌ 更新记录" + primaryKey + "=" + pkValue + "失败: " + e.getMessage());
                }
            }
        }

        System.out.println("总计更新 " + updatedCount + " 条记录");
        stmt1.close();
        stmt2.close();
    }

    /**
     * 检查并更新simulation_jobs_table的NULL字段
     *
     * @param conn 数据库连接
     * @throws SQLException 数据库操作异常
     */
    private static void checkAndUpdateSimulationJobs(Connection conn) throws SQLException {
        System.out.println("\n========================================");
        System.out.println("检查表: simulation_jobs_table");
        System.out.println("========================================");

        // 先查看表结构和主键
        Statement stmt1 = conn.createStatement();
        ResultSet cols = stmt1.executeQuery("DESCRIBE simulation_jobs_table");
        String primaryKey = null;
        while (cols.next()) {
            if ("PRI".equals(cols.getString("Key"))) {
                primaryKey = cols.getString("Field");
            }
        }

        if (primaryKey == null) {
            System.out.println("❌ 未找到主键，无法更新");
            stmt1.close();
            return;
        }

        // 使用不同的Statement对象查询
        Statement stmt2 = conn.createStatement();
        String querySQL = "SELECT " + primaryKey + ", hardware_environment FROM simulation_jobs_table";
        ResultSet rs = stmt2.executeQuery(querySQL);

        // 使用PreparedStatement执行更新，防止SQL注入
        String updateSQL = "UPDATE simulation_jobs_table SET hardware_environment = ? WHERE " + primaryKey + " = ?";
        int updatedCount = 0;
        while (rs.next()) {
            Object pkValue = rs.getObject(primaryKey);
            if (rs.getObject("hardware_environment") == null) {
                try (PreparedStatement pstmt = conn.prepareStatement(updateSQL)) {
                    pstmt.setString(1, "CPU 8核");
                    pstmt.setObject(2, pkValue);
                    pstmt.executeUpdate();
                    updatedCount++;
                    System.out.println("  ✅ 更新记录" + primaryKey + "=" + pkValue);
                } catch (SQLException e) {
                    System.out.println("  ❌ 更新记录" + primaryKey + "=" + pkValue + "失败: " + e.getMessage());
                }
            }
        }

        System.out.println("总计更新 " + updatedCount + " 条记录");
        stmt1.close();
        stmt2.close();
    }
}
