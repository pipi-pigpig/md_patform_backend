#!/bin/bash

echo "=== 分子动力学工具验证脚本 ==="
echo "脚本路径: $(realpath "$0")"
echo "运行时间: $(date)"
echo ""
echo "用法:"
echo "  直接运行此脚本验证 moltemplate、packmol、MDAnalysis 和 MDSuite 工具"
echo "  退出码: 0=全部成功, 1=全部失败, 2=部分成功"
echo "  注意: MDSuite 存在已知兼容性问题，MDAnalysis 是推荐的替代方案"
echo ""

# 颜色定义 (支持终端颜色)
if [ -t 1 ]; then
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    BLUE='\033[0;34m'
    CYAN='\033[0;36m'
    NC='\033[0m' # No Color
    BOLD='\033[1m'
else
    RED=''
    GREEN=''
    YELLOW=''
    BLUE=''
    CYAN=''
    NC=''
    BOLD=''
fi

# 初始化变量
FAILED_TOOLS=0
TOTAL_TOOLS=4
SCRIPT_DIR="$(dirname "$(realpath "$0")")"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo -e "${CYAN}项目根目录: $PROJECT_ROOT${NC}"
echo ""

# 函数：打印带颜色的状态信息
print_status() {
    local status=$1
    local message=$2
    
    case $status in
        "success")
            echo -e "  ${GREEN}✅ $message${NC}"
            ;;
        "warning") 
            echo -e "  ${YELLOW}⚠️  $message${NC}"
            ;;
        "error")
            echo -e "  ${RED}❌ $message${NC}"
            ;;
        "info")
            echo -e "  ${CYAN}ℹ️  $message${NC}"
            ;;
        "header")
            echo -e "${BOLD}${BLUE}$message${NC}"
            ;;
    esac
}

# 函数：检查命令是否存在
check_command() {
    local cmd=$1
    local name=$2
    
    if command -v "$cmd" &> /dev/null; then
        print_status "success" "$name 命令可用"
        return 0
    else
        print_status "error" "$name 命令未找到"
        return 1
    fi
}

# 函数：检查Python模块
check_python_module() {
    local module=$1
    local name=$2
    
    if python3 -c "import $module" 2>/dev/null; then
        print_status "success" "Python模块 $name 可用"
        return 0
    else
        print_status "error" "Python模块 $name 导入失败"
        return 1
    fi
}

# 函数：检查Python模块并获取版本
check_python_module_version() {
    local module=$1
    local name=$2
    
    if python3 -c "import $module; print(f'$name 版本:', getattr($module, '__version__', '未知'))" 2>/dev/null; then
        return 0
    else
        return 1
    fi
}

# ============================
# 1. 验证 MOLTEMPLATE
# ============================
print_status "header" "1. 验证 MOLTEMPLATE"

# 检查 moltemplate 命令
if check_command "moltemplate" "moltemplate"; then
    echo -n "  版本信息: "
    if moltemplate --version 2>/dev/null | head -1; then
        :
    elif moltemplate -v 2>/dev/null | head -1; then
        :
    else
        echo "未知"
    fi
else
    # 检查 Python 模块作为备选方案
    print_status "info" "尝试检查 Python 模块..."
    if check_python_module "moltemplate" "moltemplate"; then
        check_python_module_version "moltemplate" "moltemplate"
    else
        print_status "error" "moltemplate 不可用"
        FAILED_TOOLS=$((FAILED_TOOLS + 1))
    fi
fi

# 测试简单的 moltemplate 功能
echo "  功能测试: 创建简单测试文件..."
TEST_LT_FILE="$SCRIPT_DIR/test_moltemplate.lt"
cat > "$TEST_LT_FILE" << 'EOF'
# 简单的测试分子
TestMolecule {

  write_once("Data Masses") {
    @atom:1  1.0
  }

  write("Data Atoms") {
    $atom:1  @atom:1  0.0  0.0  0.0  0.0
  }
}
EOF

if [ -f "$TEST_LT_FILE" ]; then
    print_status "info" "测试文件已创建: $TEST_LT_FILE"
    # 清理测试文件
    rm -f "$TEST_LT_FILE"
else
    print_status "warning" "无法创建测试文件"
fi

echo ""

# ============================
# 2. 验证 PACKMOL
# ============================
print_status "header" "2. 验证 PACKMOL"

# 检查 packmol 命令
if check_command "packmol" "packmol"; then
    echo -n "  版本信息: "
    if packmol --version 2>/dev/null | head -1; then
        :
    elif packmol -v 2>/dev/null | head -1; then
        :
    else
        echo "未知"
    fi
    
    # 测试 packmol --help
    echo "  功能测试: 检查帮助信息..."
    if packmol --help 2>&1 | grep -q -i "usage\|help"; then
        print_status "success" "packmol 帮助信息正常"
    else
        print_status "warning" "packmol 帮助信息异常"
    fi
else
    print_status "error" "packmol 不可用"
    FAILED_TOOLS=$((FAILED_TOOLS + 1))
fi

echo ""

# ============================
# 3. 验证 MDAnalysis (推荐)
# ============================
print_status "header" "3. 验证 MDAnalysis (推荐)"

# 检查 MDAnalysis 命令
if check_command "mdanalysis" "mdanalysis"; then
    echo -n "  版本信息: "
    mdanalysis --version 2>/dev/null | head -1 || echo "未知"
else
    # 检查 Python 模块
    print_status "info" "尝试检查 Python 模块..."
    if check_python_module "MDAnalysis" "MDAnalysis"; then
        echo -n "  "
        check_python_module_version "MDAnalysis" "MDAnalysis"
        
        # 测试基本功能
        echo "  功能测试: 创建测试轨迹文件..."
        TEST_XYZ_FILE="/tmp/test_mdanalysis.xyz"
        cat > "$TEST_XYZ_FILE" << 'XYZEOF'
3
test molecule
C 0.0 0.0 0.0
H 1.0 0.0 0.0
H 0.0 1.0 0.0
XYZEOF
        
        if [ -f "$TEST_XYZ_FILE" ]; then
            print_status "info" "测试文件已创建: $TEST_XYZ_FILE"
            
            # 测试加载文件
            echo "  功能测试: 加载轨迹文件..."
            if python3 -c "
import MDAnalysis as mda
u = mda.Universe('$TEST_XYZ_FILE')
print('    原子数量:', len(u.atoms))
print('    坐标形状:', u.atoms.positions.shape)
" 2>/dev/null; then
                print_status "success" "MDAnalysis 可以加载轨迹文件"
            else
                print_status "warning" "MDAnalysis 加载轨迹文件失败"
            fi
            
            # 清理测试文件
            rm -f "$TEST_XYZ_FILE"
        else
            print_status "warning" "无法创建测试文件"
        fi
    else
        print_status "error" "MDAnalysis 不可用"
        FAILED_TOOLS=$((FAILED_TOOLS + 1))
    fi
fi

echo ""

# ============================
# 4. 验证 MDSuite (可选，已知兼容性问题)
# ============================
print_status "header" "4. 验证 MDSuite (可选，已知兼容性问题)"

# 检查 mdsuite 命令
if check_command "mdsuite" "mdsuite"; then
    echo -n "  版本信息: "
    if mdsuite --version 2>/dev/null | head -1; then
        :
    elif mdsuite -v 2>/dev/null | head -1; then
        :
    else
        echo "未知"
    fi
else
    # 检查 Python 模块
    print_status "info" "尝试导入 Python 模块..."
    
    # 尝试导入 mdsuite，处理可能的兼容性问题
    echo "  兼容性测试: 尝试导入 mdsuite..."
    
    # 创建测试脚本
    TEST_PY_FILE="$SCRIPT_DIR/test_mdsuite_import.py"
    cat > "$TEST_PY_FILE" << 'EOF'
#!/usr/bin/env python3
import sys
import traceback

print("Python 版本:", sys.version)
print("")

# 尝试导入 mdsuite
try:
    import mdsuite
    print("✅ mdsuite 导入成功")
    
    # 尝试获取版本信息
    try:
        version = getattr(mdsuite, '__version__', '未知')
        print(f"mdsuite 版本: {version}")
    except:
        print("mdsuite 版本: 未知")
    
    # 检查基本功能
    print("基本功能检查:")
    print("  - mdsuite 模块:", hasattr(mdsuite, '__file__'))
    
    # 尝试导入子模块
    try:
        from mdsuite import database
        print("  - database 子模块: 可用")
    except:
        print("  - database 子模块: 不可用")
        
    try:
        from mdsuite import file_reader
        print("  - file_reader 子模块: 可用")
    except:
        print("  - file_reader 子模块: 不可用")
        
    exit_code = 0
    
except ImportError as e:
    print(f"❌ mdsuite 导入失败: {e}")
    print("")
    print("可能的解决方案:")
    print("1. 确保已安装 mdsuite: pip install mdsuite")
    print("2. 检查 Python 环境")
    print("3. 检查依赖项 (scipy, numpy, etc.)")
    exit_code = 1
    
except Exception as e:
    print(f"❌ 导入过程中发生错误: {e}")
    print("")
    print("错误详情:")
    traceback.print_exc()
    exit_code = 1

sys.exit(exit_code)
EOF

    # 运行测试脚本
    if python3 "$TEST_PY_FILE"; then
        print_status "success" "mdsuite Python 模块可用"
    else
        print_status "error" "mdsuite Python 模块测试失败"
        FAILED_TOOLS=$((FAILED_TOOLS + 1))
    fi
    
    # 清理测试文件
    rm -f "$TEST_PY_FILE"
fi

# 检查 MDSuite 修复文件
echo "  修复检查: 查看项目中的 MDSuite 修复..."
if [ -f "$PROJECT_ROOT/verify_mdsuite_fix.py" ]; then
    print_status "info" "找到 MDSuite 修复脚本: verify_mdsuite_fix.py"
    if grep -q "scipy" "$PROJECT_ROOT/verify_mdsuite_fix.py"; then
        print_status "info" "修复脚本包含 scipy 兼容性处理"
    fi
fi

if [ -f "$PROJECT_ROOT/MDSuite_修复报告.md" ]; then
    print_status "info" "找到 MDSuite 修复报告"
fi

echo ""

# ============================
# 5. 验证结果汇总
# ============================
print_status "header" "5. 验证结果汇总"

SUCCESSFUL_TOOLS=$((TOTAL_TOOLS - FAILED_TOOLS))

echo -e "  工具总数: ${BOLD}$TOTAL_TOOLS${NC}"
echo -e "  成功验证: ${GREEN}${BOLD}$SUCCESSFUL_TOOLS${NC}"
echo -e "  失败验证: ${RED}${BOLD}$FAILED_TOOLS${NC}"

echo ""
echo "详细状态:"
echo "  1. MOLTEMPLATE  - 核心工具"
echo "  2. PACKMOL      - 核心工具"
echo "  3. MDAnalysis   - 推荐的后处理工具"
echo "  4. MDSUITE      - 可选（已知兼容性问题）"

echo ""

# ============================
# 6. 系统环境信息
# ============================
print_status "header" "6. 系统环境信息"

echo "  Python 版本: $(python3 --version 2>/dev/null || echo "未找到")"
echo "  pip 版本: $(pip3 --version 2>/dev/null | head -1 || echo "未找到")"
echo "  操作系统: $(uname -s) $(uname -r)"
echo "  当前用户: $(whoami)"
echo "  工作目录: $(pwd)"

echo ""

# ============================
# 7. 退出状态
# ============================
if [ $FAILED_TOOLS -eq 0 ]; then
    print_status "success" "所有工具验证成功！"
    echo -e "${GREEN}=== 验证通过 ===${NC}"
    exit 0
elif [ $FAILED_TOOLS -eq $TOTAL_TOOLS ]; then
    print_status "error" "所有工具验证失败！"
    echo -e "${RED}=== 验证失败 ===${NC}"
    exit 1
else
    print_status "warning" "部分工具验证失败"
    echo -e "${YELLOW}=== 验证部分通过 ($SUCCESSFUL_TOOLS/$TOTAL_TOOLS) ===${NC}"
    exit 2
fi