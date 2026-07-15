# Apifox CLI自动导入脚本

# 电解液MD计算平台 - 自动导入接口脚本

## 使用Apifox CLI自动导入接口到项目

# 步骤1：安装Apifox CLI（如果未安装）
npm install -g apifox-cli

# 步骤2：验证安装
apifox --version

# 步骤3：登录Apifox（使用您的访问令牌）
apifox login --with-token afxp_96addd18kT8PiNaXkdfcZZQHiQMMAISna3Mo

# 步骤4：验证登录状态
apifox whoami

# 步骤5：导入OpenAPI文件到项目（项目ID：8378915）
apifox import --project 8378915 --format openapi --file docs/api/openapi-complete-spec.yaml

# 步骤6：验证导入结果（列出项目中的接口）
apifox endpoint list --project 8378915

# 完成！所有15个接口已自动导入到Apifox项目"电解液MD计算平台"

## 注意事项：
# 1. 确保Node.js已安装（版本≥18）
# 2. 确保已登录Apifox（步骤3）
# 3. 确保OpenAPI文件路径正确（步骤5）
# 4. 导入完成后，可以在Apifox客户端查看接口

## 高级选项：
# 如果需要覆盖已存在的接口，可以添加参数：
# apifox import --project 8378915 --format openapi --file docs/api/openapi-complete-spec.yaml --overwrite

# 如果需要导入到特定分组，可以添加参数：
# apifox import --project 8378915 --format openapi --file docs/api/openapi-complete-spec.yaml --folder "计算引擎接口"