# Tasks

## Jinja2模板完善

- [x] Task 1: 完善in.minimization.j2模板
  - [x] SubTask 1.1: 添加完整的初始化设置（units、atom_style、力场样式、kspace_style）
  - [x] SubTask 1.2: 添加read_data system.data
  - [x] SubTask 1.3: 添加邻居列表设置
  - [x] SubTask 1.4: 添加thermo输出设置
  - [x] SubTask 1.5: 添加min_style和minimize命令（使用模板变量）
  - [x] SubTask 1.6: 添加write_data minimized.data

- [x] Task 2: 完善in.equilibrium.j2模板
  - [x] SubTask 2.1: 添加完整的初始化设置
  - [x] SubTask 2.2: 添加read_data minimized.data
  - [x] SubTask 2.3: 添加velocity初始化
  - [x] SubTask 2.4: 添加NVT平衡阶段（temp、tau_t、nsteps_nvt变量）
  - [x] SubTask 2.5: 添加NPT平衡阶段（temp、press、tau_t、tau_p、nsteps_npt变量）
  - [x] SubTask 2.6: 添加thermo和dump输出
  - [x] SubTask 2.7: 添加write_data equilibrated.data

- [x] Task 3: 完善in.production.j2模板
  - [x] SubTask 3.1: 添加完整的初始化设置
  - [x] SubTask 3.2: 添加read_data equilibrated.data
  - [x] SubTask 3.3: 添加NPT生产模拟设置
  - [x] SubTask 3.4: 添加thermo输出设置
  - [x] SubTask 3.5: 添加根据target_properties动态生成计算命令的条件逻辑
  - [x] SubTask 3.6: 添加write_data final.data

- [x] Task 4: 创建5个计算配置模板
  - [x] SubTask 4.1: 创建compute_density.j2（密度计算：thermo_style添加density、fix ave/time）
  - [x] SubTask 4.2: 创建compute_conductivity.j2（电导率：compute property/atom charge、dump charge轨迹）
  - [x] SubTask 4.3: 创建compute_viscosity.j2（粘度：compute pressure、fix ave/time压力张量）
  - [x] SubTask 4.4: 创建compute_dielectric.j2（介电常数：compute dipole、fix ave/time偶极矩）
  - [x] SubTask 4.5: 创建compute_rdf.j2（RDF：compute rdf、fix ave/time rdf）

## LammpsTemplateService实现

- [x] Task 5: 创建LammpsTemplateService Java服务类
  - [x] SubTask 5.1: 创建LammpsTemplateService.java
  - [x] SubTask 5.2: 实现generateInputScripts()方法（生成3个阶段脚本）
  - [x] SubTask 5.3: 实现buildTemplateContext()方法（从数据库读取参数构建模板上下文）
  - [x] SubTask 5.4: 实现Jinja2模板渲染逻辑（使用Python脚本渲染）
  - [x] SubTask 5.5: 实现target_properties解析和传递
  - [x] SubTask 5.6: 实现渲染结果写入inputs目录
  - [x] SubTask 5.7: 实现错误处理和日志记录

- [x] Task 6: 集成LammpsTemplateService到建模流程
  - [x] SubTask 6.1: 在MoltemplateService中调用LammpsTemplateService
  - [x] SubTask 6.2: 在步骤7（文件整理）之后添加步骤7.5（Jinja2脚本生成）
  - [x] SubTask 6.3: 更新FullModelingResult DTO添加脚本生成结果字段

## Packmol坐标替换修复

- [x] Task 7: 修复Python端Moltemplate命令添加-pdb参数
  - [x] SubTask 7.1: 修改execute_moltemplate_command()函数，添加pdb_file参数
  - [x] SubTask 7.2: 当packed_system.pdb存在时，添加-pdb参数到命令
  - [x] SubTask 7.3: 更新run_moltemplate_execution()函数传递pdb_file参数
  - [x] SubTask 7.4: 更新run_modeling.py入口脚本添加--pdb-file参数

- [x] Task 8: 修复Java端MoltemplateExecutionService添加-pdb参数
  - [x] SubTask 8.1: 修改executeMoltemplate()方法，检查packed_system.pdb是否存在
  - [x] SubTask 8.2: 当PDB文件存在时，在Docker命令中添加-pdb参数

- [x] Task 9: 实现system.in.init自动修复
  - [x] SubTask 9.1: 在organize_lammps_input_files()中添加init文件修复逻辑
  - [x] SubTask 9.2: 检查并添加atom_style full
  - [x] SubTask 9.3: 检查并添加kspace_style pppm 1.0e-4
  - [x] SubTask 9.4: 去除重复的力场样式定义
  - [x] SubTask 9.5: 在Java端MoltemplateExecutionService中也添加init文件修复

## 验证

- [x] Task 10: 端到端验证
  - [x] SubTask 10.1: 验证Jinja2模板渲染生成正确的LAMMPS脚本
  - [x] SubTask 10.2: 验证target_properties条件逻辑正确
  - [x] SubTask 10.3: 验证Moltemplate使用-pdb参数生成正确的坐标
  - [x] SubTask 10.4: 验证system.in.init自动修复
  - [x] SubTask 10.5: 在Docker容器中真实执行LAMMPS最小化验证坐标正确
  - [x] SubTask 10.6: Spring Boot编译成功

# Task Dependencies

- Task 5 依赖 Task 1-4（需要模板文件先完善）
- Task 6 依赖 Task 5
- Task 7-8 可并行执行
- Task 9 可与 Task 7-8 并行
- Task 10 依赖 Task 1-9
