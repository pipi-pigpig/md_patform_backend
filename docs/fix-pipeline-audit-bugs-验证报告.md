# fix-pipeline-audit-bugs 修复验证报告

> 验证日期：2026-06-14
> 验证方式：逐文件对比修复前后的代码
> Trae声称：所有18个checklist项已完成

---

## 总体验证结论：❌ 未完全通过

Trae完成了大部分修复工作（17/21项正确），但引入了**3个新Bug**会导致Python脚本在运行时因argparse报错而失败。

---

## 逐项验证详情

### ✅ 已验证通过的修复 (17项)

| # | Checklist项 | 验证结果 | 证据 |
|---|-----------|---------|------|
| 1 | MoltemplateSystemService脚本路径修复 | ✅ 通过 | `DOCKER_SCRIPT_PATH` 已改为 `run_modeling.py`，添加了 `--mode moltemplate-system` |
| 2 | moltemplate_utils.py coords_result修复 | ✅ 通过 | 第3022行：`total_atoms = sum(len(inst.atoms) for inst in molecule_instances)` |
| 3 | validate_system_lt()兼容新格式 | ✅ 通过 | 移除了对 `system = { }` 块的强制检查，兼容导入语句+实例定义 |
| 4 | PackmolService --output参数移除 | ✅ 通过 | 改用 `--job-dir` 参数，文档注释说明了原因 |
| 5 | calculate_molecule_counts()循环依赖 | ✅ 通过 | 无box_size时基于 `DEFAULT_TARGET_MOLECULE_COUNT` 和密度估算 |
| 6 | Dockerfile依赖补充 | ✅ 通过 | 添加了 MDAnalysis, cupy-cuda12x, Jinja2, torch |
| 7 | ConductivityCalculator O(n²)→O(n) | ✅ 通过 | `cumulative_trapezoid` 替代 for循环 |
| 8 | ViscosityCalculator O(n²)→O(n) | ✅ 通过 | `cumulative_trapezoid` 替代 for循环 |
| 9 | MoltemplateExecutionService --job-dir修复 | ✅ 通过 | 传任务根目录 `pathUtil.getJobRootPath()` 而非 inputs 子目录 |
| 10 | mergeLammpsLogFiles命令注入 | ✅ 通过 | 路径添加了双引号包裹 |
| 11 | markJobAsFailed JPA脱管 | ✅ 通过 | 使用 `findById` 重新获取托管实体再保存 |
| 12 | PipelineService hardwareUsed默认值 | ✅ 通过 | 默认值改为 `"AUTO"`，LAMMPS后根据GPU检测结果更新 |
| 13 | PipelineService状态更新竞态 | ✅ 通过 | `SimulationRepository.updateStatusById()` 使用 `@Query UPDATE` 直接更新 |
| 14 | DockerService nvidia-smi完整路径 | ✅ 通过 | 改为 `/usr/bin/nvidia-smi` |
| 15 | packmol_utils.py文件句柄泄漏 | ✅ 通过 | 使用 try/finally 确保 stdin 文件句柄关闭 |
| 16 | stage5_post_processing.py默认体积 | ✅ 通过 | 使用 `config.post_processing.DEFAULT_VOLUME_ANGSTROM3` |
| 17 | convertToDockerPath统一到PathUtil | ✅ 通过 | PathUtil新增 `convertToDockerPath()` 方法，各Service改为调用此方法 |

### ✅ 额外验证通过的改进

| # | 改进项 | 验证结果 |
|---|--------|---------|
| 18 | JsonOutputParser统一类 | ✅ 通过 — 新建 `engine/util/JsonOutputParser.java`，支持标记提取+反向搜索+JSON验证 |
| 19 | MoltemplateService统一使用PathUtil/JsonOutputParser | ✅ 通过 |
| 20 | MoltemplateExecutionService统一使用PathUtil/JsonOutputParser | ✅ 通过 |
| 21 | config.py新增 `DEFAULT_TARGET_MOLECULE_COUNT` 和 `DEFAULT_VOLUME_ANGSTROM3` | ✅ 通过 |

---

## ❌ 新引入的Bug (3项)

### Bug A（致命）：Java端传递 `--user-id`，Python端未定义此参数

**影响范围**：PackmolService、MoltemplateSystemService、MoltemplateExecutionService

**问题描述**：Trae在修复Java端各Service时，统一添加了 `--user-id` 参数传递给Python脚本（用于保持参数传递一致性），但**没有同步修改 `run_modeling.py` 的argparse定义**添加此参数。

**根因代码**：

PackmolService.java:299-300：
```java
command.add("--user-id");
command.add(userId.toString());
```

MoltemplateSystemService.java:222-223：
```java
command.add("--user-id");
command.add(userId.toString());
```

MoltemplateExecutionService.java（buildMoltemplateExecutionCommand中）：
```java
command.add("--user-id");
command.add(userId.toString());
```

但 `run_modeling.py` 中只定义了这些参数：
- `--job-id`
- `--job-dir`
- `--formula-file`
- `--mode`
- `--density`
- `--output-file`
- `--template-dir`
- `--packmol-path`
- `--packed-pdb-file`
- `--template-library-path`
- `--forcefield-type`
- `--system-lt-file`
- `--moltemplate-path`
- `--pdb-file`
- 等等

**没有 `--user-id`！**

**运行时错误**：
```
run_modeling.py: error: unrecognized arguments: --user-id 1
```

**修复方案**：在 `run_modeling.py` 的 `parse_arguments()` 中添加：
```python
parser.add_argument("--user-id", type=str, default=None, help="用户ID")
```

---

### Bug B（致命）：MoltemplateSystemService 传递 `--output`，Python端未定义此参数

**影响范围**：MoltemplateSystemService（步骤4）

**问题描述**：PackmolService的 `--output` 参数已正确移除，但 MoltemplateSystemService 的 `buildDockerCommand()` 仍然在第230行传递 `--output systemLtPath`。

`run_modeling.py` 只定义了 `--output-file` 和 `--output-dir`，没有 `--output`。

**根因代码**([MoltemplateSystemService.java:230](backend/src/main/java/com/mdplatform/engine/service/MoltemplateSystemService.java#L230))：
```java
command.add("--output");
command.add(systemLtPath);
```

注释中（第204行）也写的是 `--output {systemLtPath}`，但Python端argparse不认识。

**运行时错误**：
```
run_modeling.py: error: unrecognized arguments: --output /workspace/data/user_1/jobs/job_1/inputs/system.lt
```

**修复方案**：将 `--output` 改为 `--output-file`（Python端已定义）：
```java
command.add("--output-file");
command.add(systemLtPath);
```

---

### Bug C（中等）：MoltemplateSystemService 缺少 `--template-library-path` 和 `--packed-pdb-file` 参数

**影响范围**：MoltemplateSystemService（步骤4）

**问题描述**：`run_modeling.py` 的 `--mode moltemplate-system` 模式需要以下参数：
- `--packed-pdb-file` — Packmol生成的PDB文件路径（**必需**）
- `--template-library-path` — 分子模板库路径（**必需**）
- `--forcefield-type` — 力场类型
- `--forcefield-library-path` — 力场库路径

但当前的 `buildDockerCommand()` 传递的是：
- `--formula-file` — Python端 `run_moltemplate_system_mode()` 中使用但可能路径不对
- `--pdb-file` — 但Python端 `--mode moltemplate-system` 期望的是 `--packed-pdb-file`
- `--output` — 不存在的参数（见Bug B）

**修复方案**：调整 MoltemplateSystemService 的参数映射为Python端实际期望的参数名。

---

## ⚠️ 需要验证的边缘问题

### E1：MoltemplateService 中仍有私有的 convertToDockerPath 方法

`MoltemplateService.java` 虽然在第428行使用了 `pathUtil.convertToDockerPath()`，但该类中仍保留了私有的 `convertToDockerPath()` 方法（第851行），可能产生混淆。

**建议**：删除 MoltemplateService 中的私有 `convertToDockerPath()` 方法，确保全部通过 PathUtil。

### E2：MoltemplateExecutionService 中仍有私有的 convertToDockerPath 和 extractJsonFromOutput

MoltemplateExecutionService 虽然使用了 PathUtil 和 JsonOutputParser，但旧的私有方法可能仍然存在。

### E3：`--pdb-file` vs `--packed-pdb-file` 参数名不一致

MoltemplateSystemService 使用 `--pdb-file`，但 `run_modeling.py` 的 `--mode moltemplate-system` 期望 `--packed-pdb-file`。

---

## 总结

| 类别 | 数量 |
|------|------|
| ✅ 正确修复 | 17 项 |
| ✅ 额外改进 | 4 项 |
| ❌ 新引入Bug | 3 项（2个致命+1个中等） |

**总体评价**：Trae完成了大部分代码重构工作（统一PathUtil、统一JsonOutputParser、性能优化等），但**Java-Python参数对齐不完整**导致3个新Bug。核心问题是只修改了Java端的调用方式，未同步修改Python端的argparse定义。这3个Bug都需要修复后，全流程才能真正端到端运行。
