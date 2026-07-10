#!/usr/bin/env python3
"""
数据库数据检查和更新脚本 - 检查NULL字段并填充默认值
"""
import mysql.connector
import json
from datetime import datetime

# 数据库配置
DB_CONFIG = {
    'host': 'gateway01.ap-southeast-1.prod.aws.tidbcloud.com',
    'port': 4000,
    'user': '2Z2dkBWwpS3FSfM.root',
    'password': 'rrlKa4WDsqsSi5Iv',
    'database': 'md_database',
    'ssl_ca': None,
    'ssl_verify_cert': False,
    'ssl_verify_identity': False
}

def check_null_fields(cursor, table_name):
    """检查表中的NULL字段"""
    print(f"\n{'='*60}")
    print(f"检查表: {table_name}")
    print('='*60)
    
    # 获取所有记录
    cursor.execute(f"SELECT * FROM {table_name}")
    rows = cursor.fetchall()
    
    if not rows:
        print("[空表] 无数据记录")
        return
    
    # 获取列名
    col_names = [desc[0] for desc in cursor.description]
    
    # 统计每列的NULL数量
    null_counts = {}
    for col in col_names:
        null_count = sum(1 for row in rows if row[col_names.index(col)] is None)
        null_counts[col] = null_count
    
    # 显示NULL统计
    print(f"\n总记录数: {len(rows)}")
    print("\nNULL字段统计:")
    for col, count in null_counts.items():
        if count > 0:
            print(f"  ⚠️  {col}: {count}/{len(rows)} 条记录为NULL ({count/len(rows)*100:.1f}%)")
    
    # 显示前3条记录的详细数据
    print(f"\n前3条记录详情:")
    for i, row in enumerate(rows[:3]):
        print(f"\n[记录 {i+1}]")
        for j, val in enumerate(row):
            if val is None:
                print(f"  ⚠️  {col_names[j]}: NULL")
            else:
                val_str = str(val)
                if len(val_str) > 60:
                    val_str = val_str[:60] + "..."
                print(f"  ✅ {col_names[j]}: {val_str}")
    
    return null_counts

def update_simulation_input_defaults(cursor, conn):
    """更新simulation_input_table的默认值"""
    print("\n" + "="*60)
    print("更新 simulation_input_table 默认值")
    print("="*60)
    
    # 检查需要更新的字段
    cursor.execute("""
        SELECT id, temperature, pressure, force_field_topology_source,
               minimization_force_threshold, minimization_max_steps,
               equilibrium_density_std_threshold, equilibrium_temp_fluctuation_range,
               minimum_equilibrium_time, minimum_production_time
        FROM simulation_input_table
    """)
    
    rows = cursor.fetchall()
    updated_count = 0
    
    for row in rows:
        id_val = row[0]
        updates = {}
        
        # 检查每个字段是否为NULL，设置默认值
        if row[1] is None:  # temperature
            updates['temperature'] = 298.15
        if row[2] is None:  # pressure
            updates['pressure'] = 1.0
        if row[3] is None:  # force_field_topology_source
            updates['force_field_topology_source'] = 'uri://system/oplsaa'
        if row[4] is None:  # minimization_force_threshold
            updates['minimization_force_threshold'] = 10.0
        if row[5] is None:  # minimization_max_steps
            updates['minimization_max_steps'] = 150000
        if row[6] is None:  # equilibrium_density_std_threshold
            updates['equilibrium_density_std_threshold'] = 0.01
        if row[7] is None:  # equilibrium_temp_fluctuation_range
            updates['equilibrium_temp_fluctuation_range'] = 5.0
        if row[8] is None:  # minimum_equilibrium_time
            updates['minimum_equilibrium_time'] = 5000.0
        if row[9] is None:  # minimum_production_time
            updates['minimum_production_time'] = 50.0
        
        # 如果有需要更新的字段
        if updates:
            set_clause = ", ".join([f"{k} = {v if isinstance(v, (int, float)) else f'{v}'}" for k, v in updates.items()])
            sql = f"UPDATE simulation_input_table SET {set_clause} WHERE id = {id_val}"
            try:
                cursor.execute(sql)
                updated_count += 1
                print(f"  ✅ 更新记录ID={id_val}: {list(updates.keys())}")
            except Exception as e:
                print(f"  ❌ 更新记录ID={id_val}失败: {e}")
    
    conn.commit()
    print(f"\n总计更新 {updated_count} 条记录")

def main():
    print("="*60)
    print("数据库数据完整性检查工具")
    print("="*60)
    
    conn = mysql.connector.connect(**DB_CONFIG)
    cursor = conn.cursor()
    
    # 重点检查的表
    priority_tables = [
        'simulation_input_table',
        'molecule_template_table',
        'electrolyte_systems_table',
        'simulation_jobs_table',
        'calculation_result_table',
        'simulation_raw_output_table'
    ]
    
    # 检查所有表的NULL字段
    for table in priority_tables:
        try:
            check_null_fields(cursor, table)
        except Exception as e:
            print(f"\n[错误] 无法检查表 {table}: {e}")
    
    # 更新simulation_input_table的默认值
    try:
        update_simulation_input_defaults(cursor, conn)
    except Exception as e:
        print(f"\n[错误] 更新失败: {e}")
    
    cursor.close()
    conn.close()
    
    print("\n" + "="*60)
    print("检查和更新完成!")
    print("="*60)

if __name__ == "__main__":
    main()