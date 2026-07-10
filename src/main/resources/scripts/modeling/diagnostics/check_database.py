#!/usr/bin/env python3
"""
数据库检查脚本 - 检查所有表的字段和数据情况
"""
import mysql.connector
import json

# 数据库配置
DB_CONFIG = {
    'host': 'gateway01.ap-southeast-1.prod.aws.tidbcloud.com',
    'port': 4000,
    'user': '2Z2dkBWwpS3FSfM.root',
    'password': 'rrlKa4WDsqsSi5Iv',
    'database': 'md_database',
    'ssl_ca': None,  # TiDB Cloud需要SSL
    'ssl_verify_cert': False,
    'ssl_verify_identity': False
}

def check_table_structure(cursor, table_name):
    """检查表结构"""
    print(f"\n{'='*60}")
    print(f"表: {table_name}")
    print('='*60)
    
    # 获取列信息
    cursor.execute(f"DESCRIBE {table_name}")
    columns = cursor.fetchall()
    
    print(f"\n{'字段名':<35} {'类型':<20} {'可空':<6} {'键':<5} {'默认值'}")
    print('-'*90)
    for col in columns:
        field = col[0]
        type_ = col[1]
        null = col[2]
        key = col[3]
        default = col[4] if len(col) > 4 else ''
        print(f"{field:<35} {type_:<20} {null:<6} {key:<5} {str(default) if default else ''}")
    
    return [col[0] for col in columns]

def check_table_data(cursor, table_name, limit=3):
    """检查数据"""
    try:
        cursor.execute(f"SELECT * FROM {table_name} LIMIT {limit}")
        rows = cursor.fetchall()
        
        if not rows:
            print("\n[空表] 无数据")
            return
        
        # 获取列名
        col_names = [desc[0] for desc in cursor.description]
        
        print(f"\n--- 数据预览 (共{cursor.rowcount}行，显示前{limit}行) ---")
        for i, row in enumerate(rows):
            print(f"\n[行 {i+1}]")
            for j, val in enumerate(row):
                if val is None:
                    print(f"  ⚠️  {col_names[j]}: NULL")
                elif isinstance(val, (dict, list)):
                    print(f"  ✅ {col_names[j]}: {json.dumps(val, ensure_ascii=False)[:100]}...")
                else:
                    val_str = str(val)
                    if len(val_str) > 80:
                        val_str = val_str[:80] + "..."
                    print(f"  ✅ {col_names[j]}: {val_str}")
                    
    except Exception as e:
        print(f"\n[错误] 无法获取数据: {e}")

def main():
    print("="*60)
    print("数据库完整性检查工具")
    print("="*60)
    
    conn = mysql.connector.connect(**DB_CONFIG)
    cursor = conn.cursor()
    
    # 获取所有表
    cursor.execute("SHOW TABLES")
    tables = [t[0] for t in cursor.fetchall()]
    print(f"\n发现 {len(tables)} 个数据表: {', '.join(tables)}")
    
    # 重点检查的表
    priority_tables = [
        'simulation_input_table',
        'molecule_template_table', 
        'electrolyte_systems_table',
        'simulation_jobs_table'
    ]
    
    # 先检查重点表
    for table in priority_tables:
        if table in tables:
            check_table_structure(cursor, table)
            check_table_data(cursor, table)
    
    # 检查其他表
    for table in tables:
        if table not in priority_tables:
            check_table_structure(cursor, table)
            check_table_data(cursor, table)
    
    cursor.close()
    conn.close()
    
    print("\n" + "="*60)
    print("检查完成!")
    print("="*60)

if __name__ == "__main__":
    main()