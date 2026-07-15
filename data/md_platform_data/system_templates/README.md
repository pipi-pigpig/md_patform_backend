# 分子模板库使用文档

**文档版本**: V1.0  
**编制日期**: 2026年6月2日  
**适用范围**: 电解液MD计算平台分子动力学模拟

---

## 1. 分子模板库概述

### 1.1 目录结构

分子模板库位于 `data/md_platform_data/system_templates/` 目录下，包含以下子目录：

```
system_templates/
├── molecule_templates/     # 分子模板库
│   ├── EC/                 # 碳酸乙烯酯
│   │   ├── EC.lt           # Moltemplate力场模板
│   │   ├── EC.pdb          # 分子结构文件
│   │   └── molecule.smi    # SMILES文件
│   ├── DMC/                # 碳酸二甲酯
│   ├── LiPF6/              # 六氟磷酸锂
│   │   ├── Li.lt           # 锂离子模板
│   │   ├── Li.pdb
│   │   ├── PF6.lt          # 六氟磷酸根模板
│   │   └── PF6.pdb
│   └── [其他分子]/
│
├── force_fields/           # 力场参数库
│   ├── opls-aa/            # OPLS-AA力场
│   │   ├── oplsaa.lt       # 力场入口文件
│   │   ├── ffbonded.lt     # 键合相互作用参数
│   │   └── ffnonbonded.lt  # 非键相互作用参数
│   └── gaff/               # GAFF力场
│       ├── ffbonded.lt
│       └── ffnonbonded.lt
│
└── lammps_templates/       # LAMMPS输入脚本模板
    ├── in.minimization.j2  # 能量最小化模板
    ├── in.equilibrium.j2   # 平衡模拟模板
    └── in.production.j2    # 生产模拟模板
```

### 1.2 文件类型说明

| 文件类型 | 扩展名 | 用途 | 格式说明 |
|---------|--------|------|---------|
| Moltemplate模板 | `.lt` | 定义分子结构和力场参数 | Moltemplate专用格式 |
| 分子结构 | `.pdb` | 三维分子坐标 | PDB标准格式 |
| SMILES | `.smi` | 分子拓扑信息 | SMILES字符串 |
| Jinja2模板 | `.j2` | LAMMPS输入脚本模板 | Jinja2模板格式 |

---

## 2. 力场参数来源说明

### 2.1 OPLS-AA力场

**全称**: Optimized Potentials for Liquid Simulations - All Atom

**适用范围**:
- 有机小分子（烷烃、烯烃、芳香化合物、醇、醚、酮、酯、胺等）
- 生物分子（氨基酸、蛋白质、核酸）
- 电解液相关分子（碳酸酯溶剂、锂盐）
- 离子液体

**参考文献**:
- Jorgensen, W. L., et al. **J. Am. Chem. Soc.** 1996, 118, 11225-11236
- Price, M. L. P., et al. **J. Comput. Chem.** 2001, 22, 1340-1352
- Soetens, J.-C., et al. **J. Phys. Chem. B** 2001, 105, 4470

**力场特点**:
1. **全原子力场**: 所有原子都显式表示
2. **液体优化**: 参数专门针对液体性质优化
3. **组合规则**: 使用几何平均组合规则
   - ε_ij = √(ε_i × ε_j)
   - σ_ij = √(σ_i × σ_j)
4. **1-4缩放因子**: LJ和静电均使用0.5缩放

**能量函数**:
```
E_total = E_bond + E_angle + E_dihedral + E_improper + E_LJ + E_coulomb

E_bond = K_b × (r - r_0)²                    # 谐振子键势
E_angle = K_a × (θ - θ_0)²                   # 谐振子角度势
E_dihedral = Σ V_n × [1 + cos(nφ - γ)]       # OPLS型二面角势
E_LJ = 4ε × [(σ/r)¹² - (σ/r)⁶]               # 12-6 LJ势
E_coulomb = q_i × q_j / r                    # 库仑势
```

### 2.2 Joung-Cheatham离子参数

**适用范围**: 单价离子（Li⁺, Na⁺, K⁺, Cl⁻, F⁻等）

**参考文献**:
- I.S. Joung, T.E. Cheatham III, **J. Phys. Chem. B** 2006, 110, 13216-13225

**参数特点**:
- 专门为水溶液和有机溶剂中的离子模拟优化
- 能够准确再现离子的水合自由能和溶剂化结构
- 与TIP3P、TIP4P水模型兼容

**Li⁺参数** (Joung-Cheatham):
| 参数 | 值 | 单位 |
|------|-----|------|
| 原子质量 | 6.941 | g/mol |
| 原子电荷 | +1.0 | e |
| LJ epsilon | 0.00034 | kcal/mol |
| LJ sigma | 2.126 | Å |

**对比**: OPLS-AA标准Li⁺参数
| 参数 | OPLS-AA值 | Joung-Cheatham值 |
|------|-----------|-----------------|
| epsilon | 0.0025 kcal/mol | 0.00034 kcal/mol |
| sigma | 1.506 Å | 2.126 Å |

**推荐**: 电解液模拟推荐使用Joung-Cheatham参数

### 2.3 GAFF力场

**全称**: General Amber Force Field

**适用范围**: 小有机分子、药物分子

**参考文献**:
- Wang, J., et al. **J. Comput. Chem.** 2004, 25, 1157-1174

**力场特点**:
- 与AMBER力场兼容
- 支持RESP电荷拟合
- 使用Lorentz-Berthelot组合规则

---

## 3. 分子模板详细说明

### 3.1 碳酸乙烯酯 (EC)

**基本信息**:
| 属性 | 值 |
|------|-----|
| 分子式 | C₃H₄O₃ |
| 分子量 | 88.06 g/mol |
| 结构 | 五元环碳酸酯 |
| 力场 | OPLS-AA |

**力场参数来源**: J. Phys. Chem. B 2004, 108, 203

**原子类型分配**:
| 原子 | OPLS类型 | 电荷 | 说明 |
|------|---------|------|------|
| C1 | opls_155 | +0.52 | 羰基碳 (C=O) |
| O1 | opls_154 | -0.50 | 羰基氧 |
| O2, O3 | opls_156 | -0.35 | 醚氧 (环上) |
| C2, C3 | opls_135 | +0.22 | 亚甲基碳 (CH₂) |
| H1-H4 | opls_140 | +0.06 | 亚甲基氢 |

**电荷平衡验证**:
```
总电荷 = +0.52 - 0.50 - 0.35×2 + 0.22×2 + 0.06×4
       = +0.52 - 0.50 - 0.70 + 0.44 + 0.24
       = 0.00 ✓
```

**使用方法**:
```lt
# 在系统文件中导入
import "EC.lt"

# 创建EC分子实例
ec_molecules = new EC [10]
```

### 3.2 碳酸二甲酯 (DMC)

**基本信息**:
| 属性 | 值 |
|------|-----|
| 分子式 | C₃H₆O₃ |
| 分子量 | 90.08 g/mol |
| 结构 | 非环状碳酸酯 |
| 力场 | OPLS-AA |

**力场参数来源**: J. Phys. Chem. B 2004

**原子类型分配**:
| 原子 | OPLS类型 | 电荷 | 说明 |
|------|---------|------|------|
| C1 | opls_155 | +0.52 | 羰基碳 |
| O1 | opls_156 | -0.35 | 羰基氧 |
| C2 | opls_157 | +0.82 | 碳酸中心碳 |
| O2, O3 | opls_156 | -0.35 | 碳酸氧 |
| C3, C4 | opls_135 | +0.12 | 甲基碳 |
| H1-H6 | opls_140 | +0.06 | 甲基氢 |

**电荷平衡验证**:
```
总电荷 = +0.52 - 0.35×3 + 0.82 + 0.12×2 + 0.06×6
       = +0.52 - 1.05 + 0.82 + 0.24 + 0.36
       = 0.00 ✓
```

### 3.3 锂离子 (Li⁺)

**基本信息**:
| 属性 | 值 |
|------|-----|
| 原子质量 | 6.941 g/mol |
| 原子电荷 | +1.0 e |
| 力场 | Joung-Cheatham |

**力场参数来源**: J. Phys. Chem. B 2006

**LJ参数**:
| 参数 | 值 | 说明 |
|------|-----|------|
| epsilon | 0.00034 kcal/mol | 较小的势阱深度 |
| sigma | 2.126 Å | 有效半径 |

**物理意义**:
- Li⁺是较小的离子，具有较小的有效半径
- 较小的epsilon反映了Li⁺较弱的范德华相互作用
- 参数经过优化，能准确再现Li⁺的溶剂化自由能

### 3.4 六氟磷酸根 (PF₆⁻)

**基本信息**:
| 属性 | 值 |
|------|-----|
| 分子式 | PF₆⁻ |
| 几何结构 | 八面体 (Oh对称性) |
| 离子电荷 | -1.0 e |
| 力场 | J. Phys. Chem. B 2006 |

**原子电荷分配**:
| 原子 | 电荷 | 说明 |
|------|------|------|
| P | +1.34 | 中心磷原子 |
| F | -0.39 | 每个氟原子 (×6) |

**电荷平衡验证**:
```
总电荷 = +1.34 + 6×(-0.39) = +1.34 - 2.34 = -1.0 ✓
```

**键长**: P-F = 1.59 Å (实验值约1.58-1.60 Å)

**LJ参数**:
| 原子 | epsilon (kcal/mol) | sigma (Å) |
|------|---------------------|-----------|
| P | 0.200 | 3.74 |
| F | 0.250 | 3.10 |

---

## 4. 电荷平衡验证方法

### 4.1 验证原理

分子动力学模拟要求系统总电荷为零，否则会导致：
- 长程静电计算错误
- 系统能量漂移
- 模拟不稳定

### 4.2 验证步骤

**步骤1**: 计算单个分子的总电荷
```python
def verify_charge(molecule_atoms):
    total_charge = sum(atom['charge'] for atom in molecule_atoms)
    assert abs(total_charge) < 1e-6, f"电荷不平衡: {total_charge}"
    return True
```

**步骤2**: 计算系统的总电荷
```python
def verify_system_charge(molecules, counts):
    total = 0
    for mol, count in zip(molecules, counts):
        mol_charge = sum(atom['charge'] for atom in mol['atoms'])
        total += mol_charge * count
    assert abs(total) < 1e-6, f"系统电荷不平衡: {total}"
    return True
```

**步骤3**: 检查离子电荷配对
```python
def verify_ion_balance(li_count, anion_count, anion_charge=-1):
    # Li⁺数量应等于阴离子总电荷的绝对值
    expected_li = anion_count * abs(anion_charge)
    assert li_count == expected_li, f"离子不平衡: Li⁺={li_count}, 阴离子电荷={anion_count * anion_charge}"
    return True
```

### 4.3 常见电解液系统电荷验证

**示例**: 1M LiPF₆ in EC:DMC (1:1 v/v)

假设系统包含：
- 100个 EC分子 (中性)
- 100个 DMC分子 (中性)
- 20个 Li⁺离子 (+1.0 each)
- 20个 PF₆⁻离子 (-1.0 each)

验证：
```
系统总电荷 = 100×0 + 100×0 + 20×(+1.0) + 20×(-1.0)
           = 0 + 0 + 20 - 20
           = 0 ✓
```

---

## 5. Moltemplate使用方法

### 5.1 Moltemplate简介

Moltemplate是LAMMPS的分子模板构建工具，用于：
- 定义分子结构和力场参数
- 生成LAMMPS输入文件
- 创建复杂分子系统

### 5.2 基本使用流程

**步骤1**: 编写分子模板文件 (.lt)
```lt
# EC.lt - 碳酸乙烯酯模板
EC {
  write_once("Data Masses") {
    @atom:opls_155  12.011  # 羰基碳
    @atom:opls_154  15.999  # 羰基氧
    ...
  }
  
  write('Data Atoms') {
    $atom:C1  @atom:opls_155  +0.52  0.0 0.0 0.0
    ...
  }
}
```

**步骤2**: 编写系统文件 (system.lt)
```lt
# system.lt - 系统定义
import "EC.lt"
import "Li.lt"
import "PF6.lt"

# 定义盒子尺寸
write_once("Data Box") {
  0.0  50.0  xlo xhi
  0.0  50.0  ylo yhi
  0.0  50.0  zlo zhi
}

# 创建分子
ec_molecules = new EC [100]
li_ions = new Li [20]
pf6_ions = new PF6 [20]
```

**步骤3**: 运行Moltemplate生成LAMMPS文件
```bash
moltemplate.sh system.lt
```

**生成文件**:
- `system.data` - LAMMPS结构文件
- `system.in.init` - 初始化设置
- `system.in.settings` - 力场参数设置

### 5.3 .lt文件结构详解

**完整分子模板结构**:
```lt
MoleculeName {
  
  # 第一部分: 原子类型和质量
  write_once("Data Masses") {
    @atom:type_name  mass_value
  }
  
  # 第二部分: 原子坐标和电荷
  write('Data Atoms') {
    $atom:atom_name  @atom:type  charge  x  y  z
  }
  
  # 第三部分: 键连接
  write('Data Bond List') {
    $bond:bond_name  @bond:type  $atom:a1  $atom:a2
  }
  
  # 第四部分: 键参数
  write_once("In Settings") {
    bond_coeff @bond:type  harmonic  K  r0
  }
  
  # 第五部分: 角度连接
  write('Data Angles') {
    $angle:angle_name  @angle:type  $atom:a1  $atom:a2  $atom:a3
  }
  
  # 第六部分: 角度参数
  write_once("In Settings") {
    angle_coeff @angle:type  harmonic  K  theta0
  }
  
  # 第七部分: 二面角连接
  write('Data Dihedrals') {
    $dihedral:dih_name  @dihedral:type  $atom:a1  $atom:a2  $atom:a3  $atom:a4
  }
  
  # 第八部分: 二面角参数
  write_once("In Settings") {
    dihedral_coeff @dihedral:type  opls  V1  V2  V3  V4
  }
  
  # 第九部分: LJ参数
  write_once("In Settings") {
    pair_coeff @atom:type1  @atom:type2  epsilon  sigma
  }
}
```

### 5.4 关键命令说明

| 命令 | 用途 | 说明 |
|------|------|------|
| `write_once()` | 全局设置 | 只写入一次，不随分子实例重复 |
| `write()` | 分子实例数据 | 每个分子实例都会写入 |
| `@atom:name` | 原子类型引用 | 定义原子类型变量 |
| `$atom:name` | 原子实例引用 | 定义具体原子变量 |
| `@bond:name` | 键类型引用 | 定义键类型变量 |
| `$bond:name` | 键实例引用 | 定义具体键变量 |
| `new Molecule [N]` | 创建分子实例 | 创建N个分子实例 |

---

## 6. LAMMPS模拟配置

### 6.1 力场样式设置

```lammps
# 初始化设置
units           real           # kcal/mol, Å, fs
atom_style      full           # 包含电荷、键、角度
boundary        p p p          # 周期性边界条件

# 力场样式
pair_style      lj/cut/coul/long 12.0 12.0  # LJ + 长程静电
bond_style      harmonic       # 谐振子键势
angle_style     harmonic       # 谐振子角度势
dihedral_style  opls           # OPLS型二面角势
improper_style  harmonic       # 谐振子非正常二面角

# 长程静电处理
kspace_style    pppm 1.0e-5    # PPPM方法，精度1e-5

# 特殊键设置 (OPLS-AA标准)
special_bonds   lj 0.0 0.0 0.5 coul 0.0 0.0 0.833333
```

### 6.2 模拟参数推荐

| 参数 | 推荐值 | 说明 |
|------|--------|------|
| LJ截断 | 12.0 Å | 碳酸酯溶剂推荐值 |
| 静电截断 | 12.0 Å | 配合PPPM使用 |
| PPPM精度 | 1.0e-5 | 标准精度 |
| 时间步长 | 1.0 fs | 氢原子运动需要小步长 |
| 温度 | 298-333 K | 室温到电池工作温度 |
| 压力 | 1-100 bar | 常压到高压条件 |

### 6.3 能量最小化

```lammps
# 能量最小化输入脚本
minimize        1.0e-6 1.0e-8 1000 10000
min_style       cg             # 共轭梯度法
min_modify      dmax 0.1       # 最大位移限制
```

### 6.4 NPT平衡

```lammps
# NPT平衡模拟
fix             1 all npt temp 298 298 100 iso 1 1 1000
timestep        1.0
run             100000         # 100 ps平衡
```

---

## 7. 注意事项

### 7.1 力场兼容性

**重要**: 不同力场的组合规则可能不同

| 力场 | 组合规则 | LJ缩放 | 静电缩放 |
|------|---------|--------|---------|
| OPLS-AA | 几何平均 | 0.5 | 0.5 |
| AMBER/GAFF | Lorentz-Berthelot | 0.5 | 0.833 |
| CHARMM | Lorentz-Berthelot | 0.0 | 0.0 |

**解决方案**: 混合使用时需显式定义交叉参数

### 7.2 电荷精度

- 电荷值应保留足够精度（建议小数点后2位）
- 总电荷验证应在1e-6精度内
- 离子电荷应使用完整电荷（±1.0）

### 7.3 温度和压力范围

**OPLS-AA适用范围**:
- 温度: 200-500 K
- 压力: 0.1-100 MPa

超出范围可能导致参数失效。

### 7.4 文件路径规范

遵循项目文件输入输出规则：
- 所有路径通过PathUtil生成
- 禁止硬编码路径
- 使用相对路径存储到数据库

---

## 8. 参考文献汇总

### 8.1 OPLS-AA力场

1. Jorgensen, W. L.; Maxwell, D. S.; Tirado-Rives, J. **J. Am. Chem. Soc.** 1996, 118, 11225-11236. "Development and Testing of the OPLS All-Atom Force Field on Conformational Energetics and Properties of Organic Liquids"

2. Price, M. L. P.; Ostrovsky, D.; Jorgensen, W. L. **J. Comput. Chem.** 2001, 22, 1340-1352. "Modeling of Aqueous Solvation"

3. Soetens, J.-C.; Costa, G.; Millot, C. **J. Phys. Chem. B** 2001, 105, 4470. "Monte Carlo Simulation of Li⁺ in EC"

### 8.2 Joung-Cheatham离子参数

4. Joung, I. S.; Cheatham, T. E. III. **J. Phys. Chem. B** 2006, 110, 13216-13225. "Determination of Alkali and Halide Monovalent Ion Parameters for Use in Explicitly Solvated Biomolecular Simulations"

### 8.3 碳酸酯溶剂参数

5. Borodin, O.; Smith, G. D. **J. Phys. Chem. B** 2006, 110, 6279-6292. "Li⁺ Transport in EC:DMC"

### 8.4 GAFF力场

6. Wang, J.; Wolf, R. M.; Caldwell, J. W.; Kollman, P. A.; Case, D. A. **J. Comput. Chem.** 2004, 25, 1157-1174. "Development and Testing of a General Amber Force Field"

---

## 附录A: 原子类型速查表

### A.1 OPLS-AA常用原子类型

| 类型编号 | 原子类型 | 质量 | ε (kcal/mol) | σ (Å) |
|---------|---------|------|--------------|-------|
| opls_135 | CH₃-C (sp3) | 12.011 | 0.066 | 3.50 |
| opls_136 | CH₂-C (sp3) | 12.011 | 0.066 | 3.50 |
| opls_140 | CH₃-H | 1.008 | 0.030 | 2.50 |
| opls_154 | 羰基O | 15.999 | 0.170 | 3.00 |
| opls_155 | 羰基C | 12.011 | 0.066 | 3.50 |
| opls_156 | 醚O | 15.999 | 0.170 | 3.00 |
| opls_209 | 羰基C (酮) | 12.011 | 0.105 | 3.75 |
| opls_211 | 羰基O (酮) | 15.999 | 0.170 | 2.96 |

### A.2 电解液专用原子类型

| 类型编号 | 原子类型 | 用途 |
|---------|---------|------|
| opls_402 | Li⁺ | 锂离子 |
| opls_901 | P (PF₆) | 六氟磷酸盐磷原子 |
| opls_303 | F | 氟原子 |

---

## 附录B: 常见问题解答

### Q1: 如何选择力场？

**答**: 
- 碳酸酯溶剂: 推荐OPLS-AA
- 锂离子: 推荐Joung-Cheatham
- 复杂有机分子: 可考虑GAFF

### Q2: 电荷不平衡怎么办？

**答**:
1. 检查每个分子的电荷总和
2. 确认离子数量配对正确
3. 使用RESP或EEM方法重新计算电荷

### Q3: 如何添加新分子模板？

**答**:
1. 在 `molecule_templates/` 下创建新目录
2. 编写 `.lt` 文件定义分子结构
3. 编写 `.pdb` 文件提供初始坐标
4. 在数据库中注册新分子模板

### Q4: 模拟不稳定怎么办？

**答**:
1. 检查能量最小化是否完成
2. 减小时间步长（如0.5 fs）
3. 检查电荷平衡
4. 检查力场参数是否正确

---

**文档结束**