# 修复自动化计算流水线验证清单

## Packmol模板路径修复验证

- [ ] PackmolService.buildDockerCommand()包含--template-dir参数
- [ ] --template-dir参数值为Docker容器内正确路径（/workspace/data/system_templates/molecule_templates）
- [ ] Python脚本能通过--template-dir找到EC.pdb、DMC.pdb、Li.pdb、PF6.pdb模板文件

## Packmol I/O路径修复验证

- [ ] run_packmol_packing()中temp_dir使用基于job_dir的绝对路径
- [ ] packmol.inp中structure路径仅为文件名（如EC.pdb），不含目录前缀
- [ ] packmol.inp中output路径仅为文件名（packed_system.pdb），不含目录前缀
- [ ] Packmol执行后packed_system.pdb文件存在于temp_dir目录下
- [ ] validate_pdb_output()使用正确的绝对路径验证PDB文件
- [ ] move_outputs_to_inputs()使用正确的绝对路径复制文件到inputs目录

## Python脚本执行模式统一验证

- [ ] MoltemplateSystemService.buildDockerCommand()使用bash -c "cd /workspace/scripts && python3 -m modeling.run_modeling ..."格式
- [ ] MoltemplateExecutionService.buildMoltemplateExecutionCommand()使用bash -c格式
- [ ] MoltemplateExecutionService.buildFileOrganizeCommand()使用bash -c格式
- [ ] 所有Service的DOCKER_SCRIPT_PATH常量已移除

## 编译验证

- [ ] mvn compile -q 编译成功

## Docker容器内Packmol验证

- [ ] 在md_engine容器中手动执行packmol模式Python脚本成功
- [ ] Packmol输出日志显示执行成功
- [ ] packed_system.pdb文件在job_dir/inputs/目录下存在
- [ ] packed_system.pdb文件包含有效的原子记录

## 端到端自动化验证

- [ ] POST /api/pipeline/submit 提交任务后，状态流转为PENDING→MODELING→RUNNING→POST_PROCESSING→COMPLETED
- [ ] Packmol步骤成功生成packed_system.pdb（不出现"PDB文件未生成"错误）
- [ ] 不出现因JSON格式不匹配导致的FAILED
- [ ] 不出现因模板路径缺失导致的FAILED
- [ ] CalculationResult数据库记录包含计算结果
- [ ] 真实测试不使用mock，在Docker环境中实际执行
