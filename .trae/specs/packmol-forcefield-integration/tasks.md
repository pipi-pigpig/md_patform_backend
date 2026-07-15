# Tasks

## 力场参数验证

- [x] Task 1: 验证分子模板力场参数完整性
  - [x] SubTask 1.1: 验证EC.lt的原子电荷总和为0（电中性）
  - [x] SubTask 1.2: 验证DMC.lt的原子电荷总和为0
  - [x] SubTask 1.3: 验证Li.lt的原子电荷为+1.0
  - [x] SubTask 1.4: 验证PF6.lt的原子电荷总和为-1.0
  - [x] SubTask 1.5: 验证所有分子模板的键参数完整性
  - [x] SubTask 1.6: 验证所有分子模板的LJ参数完整性

## Packmol堆积测试

- [x] Task 2: 创建力场参数集成测试脚本
  - [x] SubTask 2.1: 创建`test_packmol_forcefield.py`测试脚本
  - [x] SubTask 2.2: 实现使用真实.lt文件进行Packmol堆积测试
  - [x] SubTask 2.3: 验证packed_system.pdb原子类型匹配
  - [x] SubTask 2.4: 验证堆积后无原子重叠

## Moltemplate集成测试

- [x] Task 3: 验证Moltemplate与新力场参数的兼容性
  - [x] SubTask 3.1: 使用packed_system.pdb和.lt文件进行Moltemplate处理
  - [x] SubTask 3.2: 验证system.data文件生成成功
  - [x] SubTask 3.3: 验证system.data包含完整力场参数
  - [x] SubTask 3.4: 验证拓扑信息正确

## LAMMPS模拟测试

- [x] Task 4: 验证LAMMPS能正确运行模拟
  - [x] SubTask 4.1: 使用system.data运行LAMMPS能量最小化
  - [x] SubTask 4.2: 验证LAMMPS无错误输出
  - [x] SubTask 4.3: 验证能量计算正常

## 文档更新

- [x] Task 5: 更新力场参数使用文档
  - [x] SubTask 5.1: 更新分子模板力场参数来源说明
  - [x] SubTask 5.2: 更新集成测试运行说明

# Task Dependencies

- Task 2 依赖 Task 1（需要先验证力场参数完整性）
- Task 3 依赖 Task 2（需要Packmol堆积成功后才能进行Moltemplate处理）
- Task 4 依赖 Task 3（需要system.data文件才能运行LAMMPS）
- Task 5 依赖 Task 1-4（文档需要覆盖所有测试结果）