#!/usr/bin/env python3
"""检查TiDB Cloud数据库表"""
import pymysql
import ssl

# TiDB Cloud配置
config = {
    'host': 'gateway01.ap-southeast-1.prod.aws.tidbcloud.com',
    'port': 4000,
    'user': '2Z2dkBWwpS3FSfM.root',
    'password': 'rrlKa4WDsqsSi5Iv',
    'database': 'md_database',
    'ssl': {'ssl_ca': None, 'ssl_verify_cert': False, 'ssl_verify_identity': False}
}

try:
    conn = pymysql.connect(**config)
    cursor = conn.cursor()
    
    # 显示数据库
    cursor.execute("SHOW DATABASES")
    print("数据库列表:", [row[0] for row in cursor.fetchall()])
    
    # 切换到md_database
    cursor.execute("USE md_database")
    
    # 显示表
    cursor.execute("SHOW TABLES")
    tables = cursor.fetchall()
    print("\nmd_database中的表:", [t[0] for t in tables])
    
    # 检查simulation_jobs_table是否存在
    if tables:
        cursor.execute("SELECT COUNT(*) FROM simulation_jobs_table")
        count = cursor.fetchone()
        print(f"\nsimulation_jobs_table记录数: {count[0]}")
        
        # 显示最近5条记录
        cursor.execute("SELECT job_id, job_name, status, create_time FROM simulation_jobs_table ORDER BY job_id DESC LIMIT 5")
        jobs = cursor.fetchall()
        print("\n最近5条任务:")
        for job in jobs:
            print(f"  ID={job[0]}, 名称={job[1]}, 状态={job[2]}, 创建时间={job[3]}")
    
    cursor.close()
    conn.close()
    print("\n连接成功!")
    
except Exception as e:
    print(f"连接失败: {e}")