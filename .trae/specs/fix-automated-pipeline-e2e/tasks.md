# Tasks

## 阶段一：修复Packmol核心阻塞问题

- [ ] Task 1: 修复PackmolService缺少--template-dir参数
  - [ ] SubTask 1.1: 在PackmolService.executePackmol()中计算分子模板库的Docker路径（参考MoltemplateSystemService的做法：`pathUtil.getMoleculeTemplatePath("EC").getParent()`然后`convertToDockerPath()`）
  - [ ] SubTask 1.2: 在buildDockerCommand()方法中添加`--template-dir`参数，传递Docker路径
  - [ ] SubTask 1.3: 移除未使用的DOCKER_SCRIPT_PATH常量（如果还存在）

- [ ] Task 2: 修复packmol_utils.py中Packmol I/O路径解析错误
  - [ ] SubTask 2.1: 修改run_packmol_packing()中的temp_dir为基于job_dir的绝对路径（如`Path(job_dir) / "temp" / "packmol_temp"`），而非相对路径`Path("temp") / "packmol_temp"`
  - [ ] SubTask 2.2: 修改molecules列表中pdb_file字段：在generate_packmol_input_script()调用前，将pdb_file设为仅文件名（如`EC.pdb`），而非含目录前缀的路径（如`temp/packmol_temp/EC.pdb`）
  - [ ] SubTask 2.3: 修改output_pdb_path为仅文件名`packed_system.pdb`，而非含目录前缀的路径，因为Packmol的CWD已是temp_dir
  - [ ] SubTask 2.4: 修改validate_pdb_output()和move_outputs_to_inputs()的调用，使用正确的绝对路径（基于job_dir拼接）
  - [ ] SubTask 2.5: 修改fetch_molecule_templates()和copy_pdb_files_to_workdir()，确保template_dir使用绝对路径

- [ ] Task 3: 修复PackmolRunner中temp_dir的路径处理
  - [ ] SubTask 3.1: PackmolRunner.__init__()中temp_dir应支持绝对路径，mkdir时使用绝对路径
  - [ ] SubTask 3.2: generate_packmol_input_script()中的structure和output路径应为相对于Packmol CWD的路径（仅文件名）

## 阶段二：统一Python脚本执行模式

- [ ] Task 4: 统一MoltemplateSystemService的Python执行模式
  - [ ] SubTask 4.1: 将buildDockerCommand()从`python3 /workspace/scripts/modeling/run_modeling.py`改为`bash -c "cd /workspace/scripts && python3 -m modeling.run_modeling ..."`格式
  - [ ] SubTask 4.2: 移除DOCKER_SCRIPT_PATH常量

- [ ] Task 5: 统一MoltemplateExecutionService的Python执行模式
  - [ ] SubTask 5.1: 将buildMoltemplateExecutionCommand()从`python3 /workspace/scripts/modeling/run_modeling.py`改为`bash -c "cd /workspace/scripts && python3 -m modeling.run_modeling ..."`格式
  - [ ] SubTask 5.2: 将buildFileOrganizeCommand()从`python3 /workspace/scripts/modeling/run_modeling.py`改为`bash -c "cd /workspace/scripts && python3 -m modeling.run_modeling ..."`格式
  - [ ] SubTask 5.3: 移除DOCKER_SCRIPT_PATH常量

## 阶段三：编译验证

- [ ] Task 6: 编译验证
  - [ ] SubTask 6.1: Spring Boot项目编译成功（mvn compile -q通过）

## 阶段四：真实端到端测试

- [ ] Task 7: 在Docker容器中手动验证Packmol步骤
  - [ ] SubTask 7.1: 在md_engine容器中手动执行packmol模式Python脚本，验证模板路径正确、PDB文件生成
  - [ ] SubTask 7.2: 检查Packmol输出日志，确认执行成功且packed_system.pdb文件存在

- [ ] Task 8: 运行PipelineJsonOutputE2ETest验证Python脚本JSON输出格式
  - [ ] SubTask 8.1: 执行测试验证molecule-count、box-size、packmol、file-organize模式的JSON输出

- [ ] Task 9: 运行PipelineE2EFullTest验证完整流水线
  - [ ] SubTask 9.1: 执行测试验证任务状态流转PENDING→MODELING→RUNNING→POST_PROCESSING→COMPLETED
  - [ ] SubTask 9.2: 修复测试中发现的新问题并重新测试

# Task Dependencies

- Task 2 依赖 Task 1（需要先确认template_dir的传递方式）
- Task 3 依赖 Task 2（PackmolRunner路径处理需要与run_packmol_packing()协调）
- Task 6 依赖 Task 1-5（所有代码修改完成后编译验证）
- Task 7 依赖 Task 6（编译通过后才能在Docker中测试）
- Task 8 依赖 Task 6
- Task 9 依赖 Task 7-8（先验证Python脚本输出正确，再测试完整流水线）
