# Checklist

- [x] EC.lt分子模板使用正确的Moltemplate继承语法 `EC inherits OPLSAA { ... }`
- [x] DMC.lt分子模板使用正确的Moltemplate继承语法 `DMC inherits OPLSAA { ... }`
- [x] PF6.lt分子模板使用正确的Moltemplate继承语法 `PF6 inherits OPLSAA { ... }`
- [x] Li.lt分子模板使用独立的Joung-Cheatham参数
- [x] 所有分子模板Data Atoms包含 `$mol:.` 分子ID
- [x] 所有分子模板使用Data Bonds格式定义键
- [x] Dockerfile包含LAMMPS包：MOLECULE, KSPACE, RIGID, OPT
- [x] md-engine容器成功重新构建
- [x] LAMMPS验证显示GPU, KSPACE, MOLECULE, OPT, RIGID包已安装
- [x] Moltemplate成功处理system_test.lt测试文件
- [x] 生成的LAMMPS输入文件格式正确
- [x] ffnonbonded.lt包含所有原子类型的LJ参数
- [x] ffbonded.lt包含键、角度、二面角参数
- [x] import路径正确指向力场文件目录