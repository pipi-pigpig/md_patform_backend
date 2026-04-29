-- 创建数据库
CREATE DATABASE IF NOT EXISTS md_database CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE md_database;

-- 电解液系统表
CREATE TABLE electrolyte_systems (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     name VARCHAR(200) NOT NULL,
                                     description TEXT,
                                     solvent_type ENUM('WATER', 'ACETONITRILE', 'DMSO', 'ETHANOL', 'OTHER') NOT NULL,
                                     salt_formula VARCHAR(100),
                                     concentration DECIMAL(10, 4),
                                     temperature DECIMAL(10, 2),
                                     pressure DECIMAL(10, 2),
                                     created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                     INDEX idx_solvent_type (solvent_type),
                                     INDEX idx_salt_formula (salt_formula)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 模拟任务表
CREATE TABLE simulation_jobs (
                                 id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                 system_id BIGINT,
                                 job_name VARCHAR(200) NOT NULL,
                                 description TEXT,
                                 computing_unit VARCHAR(200),
                                 software ENUM('LAMMPS', 'GROMACS') NOT NULL,
                                 status ENUM('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED') DEFAULT 'PENDING',
                                 input_file_path VARCHAR(500),
                                 output_file_path VARCHAR(500),
                                 parameters JSON,
                                 hardware_used ENUM('CPU', 'GPU', 'BOTH') DEFAULT 'CPU',
                                 start_time TIMESTAMP NULL,
                                 end_time TIMESTAMP NULL,
                                 execution_time BIGINT,
                                 result_summary JSON,
                                 created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                                 updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                 FOREIGN KEY (system_id) REFERENCES electrolyte_systems(id) ON DELETE SET NULL,
                                 INDEX idx_status (status),
                                 INDEX idx_software (software),
                                 INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 插入示例数据
INSERT INTO electrolyte_systems (name, description, solvent_type, salt_formula, concentration, temperature, pressure)
VALUES
    ('LiPF6 in EC/DMC', '典型锂离子电池电解液', 'OTHER', 'LiPF6', 1.0, 298.15, 1.0),
    ('NaCl in Water', '水溶液电解液', 'WATER', 'NaCl', 0.1, 298.15, 1.0),
    ('LiTFSI in DOL/DME', '锂硫电池电解液', 'OTHER', 'LiTFSI', 0.5, 298.15, 1.0),
    ('EMIM-TFSI Ionic Liquid', '离子液体电解液', 'OTHER', 'EMIM-TFSI', 1.0, 298.15, 1.0);

INSERT INTO simulation_jobs (job_name, description, computing_unit, software, status, hardware_used, created_at)
VALUES
    ('锂离子电池MD模拟', '研究LiPF6在EC/DMC混合溶剂中的离子传输行为和扩散系数，为锂离子电池电解液优化提供理论依据', '清华大学化学工程系', 'LAMMPS', 'COMPLETED', 'GPU', NOW()),
    ('水溶液NaCl模拟', '模拟不同浓度NaCl水溶液的微观结构和动力学性质，分析离子水合壳层结构', '北京大学物理学院', 'GROMACS', 'RUNNING', 'BOTH', NOW()),
    ('离子液体测试', '测试EMIM-TFSI离子液体的热稳定性和电化学窗口，评估其作为电解液的应用潜力', '中科院化学研究所', 'LAMMPS', 'PENDING', 'CPU', NOW());

-- 创建用户（如果需要认证）
-- CREATE USER 'mduser'@'%' IDENTIFIED BY 'mdpassword123';
-- GRANT ALL PRIVILEGES ON md_database.* TO 'mduser'@'%';
-- FLUSH PRIVILEGES;