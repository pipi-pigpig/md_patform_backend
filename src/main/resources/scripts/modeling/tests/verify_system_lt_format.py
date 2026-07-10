"""
验证system.lt文件格式脚本

用于生成示例system.lt文件并验证其格式是否符合Moltemplate规范

作者: AI Assistant
版本: 1.0.0
"""

import tempfile
import shutil
from pathlib import Path
import sys

# 添加父目录到路径
sys.path.insert(0, str(Path(__file__).parent.parent))

from utils.moltemplate_utils import (
    generate_system_lt_file,
    validate_system_lt,
    MoleculeInstance,
    AtomRecord,
)


def create_test_system_lt():
    """创建测试system.lt文件"""
    
    # 创建临时目录
    temp_dir = tempfile.mkdtemp(prefix="verify_system_lt_")
    
    try:
        # 创建测试模板文件
        ec_lt_content = """EC {
  write_once("Data Masses") {
    @atom:C1 12.0
    @atom:C2 12.0
    @atom:O1 16.0
  }
  
  write('Data Atoms') {
    $atom:1 @atom:C1 $mol:... 0.0 0.0 0.0 0.0
    $atom:2 @atom:C2 $mol:... 0.0 1.0 0.0 0.0
    $atom:3 @atom:O1 $mol:... 0.0 2.0 0.0 0.0
  }
}
"""
        ec_lt_path = Path(temp_dir) / "EC.lt"
        ec_lt_path.write_text(ec_lt_content, encoding='utf-8')
        
        li_lt_content = """Li {
  write_once("Data Masses") {
    @atom:LI 6.9
  }
  
  write('Data Atoms') {
    $atom:1 @atom:LI $mol:... 1.0 0.0 0.0 0.0
  }
}
"""
        li_lt_path = Path(temp_dir) / "Li.lt"
        li_lt_path.write_text(li_lt_content, encoding='utf-8')
        
        # 创建分子实例
        molecule_instances = [
            MoleculeInstance(
                molecule_name="EC",
                instance_id=1,
                atoms=[
                    AtomRecord(
                        serial=1, name="C1", alt_loc="", res_name="EC", 
                        chain_id="A", res_seq=1, icode="", 
                        x=0.0, y=0.0, z=0.0, 
                        occupancy=1.0, temp_factor=0.0, 
                        element="C", charge="", record_type="ATOM"
                    ),
                    AtomRecord(
                        serial=2, name="C2", alt_loc="", res_name="EC", 
                        chain_id="A", res_seq=1, icode="", 
                        x=1.0, y=0.0, z=0.0, 
                        occupancy=1.0, temp_factor=0.0, 
                        element="C", charge="", record_type="ATOM"
                    ),
                    AtomRecord(
                        serial=3, name="O1", alt_loc="", res_name="EC", 
                        chain_id="A", res_seq=1, icode="", 
                        x=2.0, y=0.0, z=0.0, 
                        occupancy=1.0, temp_factor=0.0, 
                        element="O", charge="", record_type="ATOM"
                    ),
                ],
                chain_id="A",
                start_line=1,
                end_line=3
            ),
            MoleculeInstance(
                molecule_name="Li",
                instance_id=1,
                atoms=[
                    AtomRecord(
                        serial=1, name="LI", alt_loc="", res_name="Li", 
                        chain_id="B", res_seq=2, icode="", 
                        x=3.0, y=0.0, z=0.0, 
                        occupancy=1.0, temp_factor=0.0, 
                        element="Li", charge="", record_type="ATOM"
                    ),
                ],
                chain_id="B",
                start_line=4,
                end_line=4
            ),
        ]
        
        # 定义盒子尺寸
        box_size = {"x": 50.0, "y": 50.0, "z": 50.0}
        
        # 定义模板文件名（相对路径）
        template_file_names = {
            "EC": "EC.lt",
            "Li": "Li.lt"
        }
        
        # 生成system.lt文件
        output_path = Path(temp_dir) / "system.lt"
        result = generate_system_lt_file(
            template_file_names,
            molecule_instances,
            box_size,
            forcefield_type="oplsaa",
            output_path=str(output_path)
        )
        
        # 验证生成结果
        if not result["success"]:
            print(f"[ERROR] 生成失败: {result['error']}")
            return False
        
        print("[SUCCESS] system.lt文件生成成功")
        print(f"   文件路径: {result['system_lt_path']}")
        print(f"   总分子数: {result['generation_summary']['total_molecules']}")
        print(f"   总原子数: {result['generation_summary']['total_atoms']}")
        
        # 读取并显示文件内容（使用UTF-8编码）
        system_lt_content = result["system_lt_content"]
        print("\n" + "="*80)
        print("生成的system.lt文件内容:")
        print("="*80)
        # 尝试使用UTF-8编码输出，如果失败则跳过
        try:
            # 使用UTF-8编码输出到控制台
            import io
            import sys
            if sys.stdout.encoding != 'utf-8':
                # 创建UTF-8编码的输出流
                sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
            print(system_lt_content)
        except UnicodeEncodeError:
            print("[INFO] 文件内容包含特殊字符，无法在当前控制台显示")
            print("[INFO] 文件已成功生成，请查看文件路径")
        print("="*80)
        
        # 验证文件格式
        validation_result = validate_system_lt(str(output_path))
        
        if not validation_result.get("valid", False):
            print(f"\n[ERROR] 文件格式验证失败:")
            for error in validation_result.get("validation_errors", []):
                print(f"   - {error}")
            return False
        
        print("\n[SUCCESS] system.lt文件格式验证成功")
        print(f"   包含导入语句: {validation_result['has_imports']}")
        print(f"   包含分子实例定义: {validation_result['has_molecule_instances']}")
        print(f"   包含坐标数据: {validation_result['has_coordinates']}")
        print(f"   包含盒子边界: {validation_result['has_boundary']}")
        print(f"   包含system块: {validation_result['has_system_block']}")
        print(f"   括号匹配: {validation_result.get('brackets_matched', 'N/A')}")
        
        # 验证必需元素
        required_elements = {
            "导入语句": "import" in system_lt_content,
            "system块": "system = {" in system_lt_content,
            "分子实例定义": "new EC" in system_lt_content or "new Li" in system_lt_content,
            "坐标数据": "Data Atoms" in system_lt_content,
            "盒子边界": "Data Boundary" in system_lt_content,
        }
        
        print("\n" + "="*80)
        print("必需元素验证:")
        print("="*80)
        all_present = True
        for element, present in required_elements.items():
            status = "[OK]" if present else "[MISSING]"
            print(f"{status} {element}: {'存在' if present else '缺失'}")
            if not present:
                all_present = False
        
        if all_present:
            print("\n[SUCCESS] 所有必需元素都存在")
        else:
            print("\n[ERROR] 缺少必需元素")
            return False
        
        return True
        
    finally:
        # 清理临时目录
        shutil.rmtree(temp_dir, ignore_errors=True)


if __name__ == "__main__":
    success = create_test_system_lt()
    
    if success:
        print("\n" + "="*80)
        print("验证总结:")
        print("="*80)
        print("[OK] system.lt文件生成功能正常")
        print("[OK] 文件格式符合Moltemplate规范")
        print("[OK] 包含所有必需元素（导入语句、分子实例定义、坐标数据、盒子边界）")
        print("[OK] 文件语法正确")
        print("="*80)
        sys.exit(0)
    else:
        print("\n[ERROR] 验证失败")
        sys.exit(1)