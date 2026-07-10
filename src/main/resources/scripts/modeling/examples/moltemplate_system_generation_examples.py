"""
Moltemplate系统生成完整流程使用示例

本示例展示如何使用run_moltemplate_system_generation函数
从Packmol生成的PDB文件到生成完整的system.lt文件
"""

import json
from pathlib import Path
from utils.moltemplate_utils import (
    run_moltemplate_system_generation,
    run_moltemplate_system_generation_simple,
)


# ==================== 示例1: 基本使用 ====================

def example_basic_usage():
    """基本使用示例"""
    
    # 配方数据（从Java后端传入或从JSON文件读取）
    formula_data = {
        "molecules": [
            {"name": "EC", "count": 500},  # 碳酸乙烯酯 500个分子
            {"name": "DMC", "count": 300},  # 碳酸二甲酯 300个分子
            {"name": "Li", "count": 50},    # 锂离子 50个
            {"name": "PF6", "count": 50}    # 六氟磷酸根 50个
        ],
        "box_size": {
            "x": 48.0,  # 盒子X尺寸（埃）
            "y": 48.0,  # 盒子Y尺寸（埃）
            "z": 48.0   # 盒子Z尺寸（埃）
        }
    }
    
    # 执行完整流程
    result = run_moltemplate_system_generation(
        packed_pdb_path="inputs/packed_system.pdb",  # Packmol生成的PDB文件
        formula_data=formula_data,                    # 配方数据
        work_dir_path="user_1/jobs/job_123",          # 任务工作目录
        template_library_path="system_templates/molecule_templates",  # 分子模板库路径
        forcefield_type="oplsaa",                     # 力场类型
        output_json_path="results/moltemplate_result.json"  # 结果输出路径（可选）
    )
    
    # 检查执行结果
    if result["success"]:
        print("✅ Moltemplate系统生成成功！")
        print(f"   system.lt文件路径: {result['system_lt_path']}")
        print(f"   总原子数: {result['statistics']['total_atoms']}")
        print(f"   总分子数: {result['statistics']['total_molecules']}")
        print(f"   执行耗时: {result['execution_summary']['total_duration']}秒")
    else:
        print("❌ Moltemplate系统生成失败！")
        print(f"   失败步骤: {result['failed_step']}")
        print(f"   错误信息: {result['error']}")
    
    return result


# ==================== 示例2: 简化版使用（从JSON文件读取配方） ====================

def example_simple_usage():
    """简化版使用示例（从JSON文件读取配方）"""
    
    # 配方JSON文件内容示例
    formula_json_content = {
        "molecules": [
            {"name": "EC", "count": 500},
            {"name": "Li", "count": 50}
        ],
        "box_size": {"x": 48.0, "y": 48.0, "z": 48.0}
    }
    
    # 将配方数据写入JSON文件
    formula_json_path = Path("inputs/formula.json")
    with open(formula_json_path, 'w') as f:
        json.dump(formula_json_content, f)
    
    # 执行简化版流程
    result = run_moltemplate_system_generation_simple(
        packed_pdb_path="inputs/packed_system.pdb",
        formula_json_path=str(formula_json_path),
        work_dir_path="user_1/jobs/job_123",
        template_library_path="system_templates/molecule_templates",
        forcefield_type="oplsaa"
    )
    
    # 检查执行结果
    if result["success"]:
        print("✅ 系统生成成功！")
    else:
        print("❌ 系统生成失败！")
    
    return result


# ==================== 示例3: 详细结果分析 ====================

def example_detailed_analysis():
    """详细结果分析示例"""
    
    # 执行流程
    result = run_moltemplate_system_generation(
        packed_pdb_path="inputs/packed_system.pdb",
        formula_data={
            "molecules": [{"name": "EC", "count": 500}],
            "box_size": {"x": 48.0, "y": 48.0, "z": 48.0}
        },
        work_dir_path="user_1/jobs/job_123",
        template_library_path="system_templates/molecule_templates",
        forcefield_type="oplsaa"
    )
    
    # 分析执行摘要
    execution_summary = result["execution_summary"]
    print("\n========== 执行摘要 ==========")
    print(f"总步骤数: {execution_summary['total_steps']}")
    print(f"成功步骤: {execution_summary['successful_steps']}")
    print(f"失败步骤: {execution_summary['failed_steps']}")
    print(f"总耗时: {execution_summary['total_duration']}秒")
    print(f"开始时间: {execution_summary['start_time']}")
    print(f"结束时间: {execution_summary['end_time']}")
    
    # 分析各步骤执行结果
    print("\n========== 各步骤执行结果 ==========")
    for step_result in result["step_results"]:
        status = "✅ 成功" if step_result["success"] else "❌ 失败"
        print(f"{step_result['step_name']}: {status}, 耗时 {step_result['duration']}秒")
        if step_result["error_message"]:
            print(f"  错误信息: {step_result['error_message']}")
    
    # 分析统计信息
    statistics = result["statistics"]
    print("\n========== 统计信息 ==========")
    print(f"总原子数: {statistics['total_atoms']}")
    print(f"总分子数: {statistics['total_molecules']}")
    print(f"分子类型数: {statistics['molecule_types']}")
    print(f"分子数量统计: {statistics['molecule_counts']}")
    print(f"盒子尺寸: {statistics['box_size']}")
    print(f"盒子体积: {statistics['box_volume']} Å³")
    print(f"力场类型: {statistics['forcefield_type']}")
    print(f"模板文件列表: {statistics['template_files']}")
    print(f"system.lt文件大小: {statistics['system_lt_file_size']} 字符")
    
    # 分析执行时间
    execution_time = statistics["execution_time"]
    print("\n========== 执行时间分析 ==========")
    print(f"总耗时: {execution_time['total_duration']}秒")
    print("各步骤耗时:")
    for step_name, duration in execution_time["step_durations"].items():
        print(f"  {step_name}: {duration}秒")
    
    # 分析验证状态
    validation_status = statistics["validation_status"]
    print("\n========== 验证状态 ==========")
    print(f"验证结果: {'通过' if validation_status['valid'] else '失败'}")
    print(f"错误数量: {validation_status['errors_count']}")
    print(f"警告数量: {validation_status['warnings_count']}")
    
    return result


# ==================== 示例4: Java后端调用示例 ====================

def example_java_backend_call():
    """Java后端调用示例（通过DockerService）"""
    
    # Java代码示例（仅供参考）
    java_code_example = """
// Java后端调用示例
public class MoltemplateService {
    
    public void generateSystemLt(Long userId, Long jobId) {
        // 1. 构建Python脚本参数
        String pdbPath = pathUtil.getInputPath(userId, jobId) + "/packed_system.pdb";
        String workDir = pathUtil.getJobRootPath(userId, jobId);
        String templateLib = "system_templates/molecule_templates";
        String formulaJson = pathUtil.getInputPath(userId, jobId) + "/formula.json";
        
        // 2. 构建Python命令
        List<String> command = Arrays.asList(
            "python3",
            "/workspace/scripts/modeling/run_modeling.py",
            "--mode", "moltemplate_generation",
            "--pdb", pdbPath,
            "--formula", formulaJson,
            "--workdir", workDir,
            "--template-lib", templateLib,
            "--forcefield", "oplsaa"
        );
        
        // 3. 在Docker容器中执行
        DockerExecutionResult result = dockerService.executeCommandInContainer(
            "md_engine",
            command,
            "/workspace"
        );
        
        // 4. 解析执行结果
        if (result.getExitCode() == 0) {
            String outputJson = result.getOutput();
            JSONObject resultJson = JSON.parseObject(outputJson);
            
            if (resultJson.getBoolean("success")) {
                // 成功，更新任务状态
                String systemLtPath = resultJson.getString("system_lt_path");
                updateJobStatus(jobId, "MOLTEMPLATE_SUCCESS", systemLtPath);
            } else {
                // 失败，记录错误信息
                String failedStep = resultJson.getString("failed_step");
                String error = resultJson.getString("error");
                updateJobStatus(jobId, "MOLTEMPLATE_FAILED", failedStep, error);
            }
        }
    }
}
"""
    
    print("Java后端调用示例代码：")
    print(java_code_example)


# ==================== 示例5: 错误处理示例 ====================

def example_error_handling():
    """错误处理示例"""
    
    # 测试各种错误场景
    
    # 场景1: PDB文件不存在
    result1 = run_moltemplate_system_generation(
        packed_pdb_path="nonexistent.pdb",
        formula_data={"molecules": [{"name": "EC", "count": 1}], "box_size": {"x": 50.0}},
        work_dir_path="test",
        template_library_path="templates"
    )
    
    if not result1["success"]:
        print("场景1错误处理:")
        print(f"  失败步骤: {result1['failed_step']}")
        print(f"  错误信息: {result1['error']}")
        print(f"  步骤结果: {result1['step_results']}")
    
    # 场景2: 模板文件缺失
    result2 = run_moltemplate_system_generation(
        packed_pdb_path="test.pdb",
        formula_data={"molecules": [{"name": "UnknownMol", "count": 1}], "box_size": {"x": 50.0}},
        work_dir_path="test",
        template_library_path="templates"
    )
    
    if not result2["success"]:
        print("\n场景2错误处理:")
        print(f"  失败步骤: {result2['failed_step']}")
        print(f"  错误信息: {result2['error']}")
    
    # 场景3: 配方数据格式错误
    result3 = run_moltemplate_system_generation(
        packed_pdb_path="test.pdb",
        formula_data={},  # 空配方数据
        work_dir_path="test",
        template_library_path="templates"
    )
    
    if not result3["success"]:
        print("\n场景3错误处理:")
        print(f"  失败步骤: {result3['failed_step']}")
        print(f"  错误信息: {result3['error']}")


# ==================== 主函数 ====================

if __name__ == "__main__":
    print("Moltemplate系统生成完整流程使用示例")
    print("=" * 60)
    
    # 运行示例
    print("\n示例1: 基本使用")
    # example_basic_usage()  # 需要实际文件才能运行
    
    print("\n示例2: 简化版使用")
    # example_simple_usage()  # 需要实际文件才能运行
    
    print("\n示例3: 详细结果分析")
    # example_detailed_analysis()  # 需要实际文件才能运行
    
    print("\n示例4: Java后端调用示例")
    example_java_backend_call()
    
    print("\n示例5: 错误处理示例")
    # example_error_handling()  # 需要实际文件才能运行
    
    print("\n提示: 以上示例需要实际的PDB文件和模板文件才能完整运行")
    print("请参考测试文件 test_moltemplate_system_generation.py 了解完整测试流程")