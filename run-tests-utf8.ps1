# ============================================================
# PowerShell测试启动脚本 - 解决Windows控制台中文乱码问题
# ============================================================
#
# 功能：
# 1. 设置PowerShell控制台编码为UTF-8
# 2. 设置Java JVM编码为UTF-8
# 3. 运行Maven测试
#
# 使用方法：
# .\run-tests-utf8.ps1
# .\run-tests-utf8.ps1 -TestClass "PipelineE2EFullTest"
# .\run-tests-utf8.ps1 -TestClass "PipelineE2EFullTest,PipelineJsonOutputE2ETest"
#
# 编码配置说明：
# - Windows默认控制台编码为GBK（代码页936）
# - Java/Python默认输出编码为UTF-8
# - 当UTF-8输出到GBK控制台时会出现乱码
# - 通过设置控制台编码为UTF-8（代码页65001）解决乱码问题
# ============================================================

param(
    [string]$TestClass = "",
    [string]$MavenOpts = "-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8"
)

# 设置PowerShell控制台编码为UTF-8（代码页65001）
Write-Host "设置控制台编码为UTF-8..." -ForegroundColor Green
$OutputEncoding = [System.Text.Encoding]::UTF-8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF-8
[Console]::InputEncoding = [System.Text.Encoding]::UTF-8

# 设置控制台代码页为UTF-8（65001）
try {
    $null = cmd /c "chcp 65001 >nul 2>&1"
    Write-Host "控制台代码页已设置为UTF-8 (65001)" -ForegroundColor Green
} catch {
    Write-Host "警告：无法设置控制台代码页，可能需要手动设置" -ForegroundColor Yellow
}

# 设置环境变量：Java JVM编码为UTF-8
$env:JAVA_TOOL_OPTIONS = "-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8"
$env:MAVEN_OPTS = $MavenOpts

Write-Host "Java JVM编码已设置为UTF-8" -ForegroundColor Green
Write-Host "Maven编码已设置为UTF-8" -ForegroundColor Green

# 切换到backend目录
$backendDir = $PSScriptRoot
Write-Host "切换到目录: $backendDir" -ForegroundColor Green

# 构建Maven测试命令
$mavenCommand = "mvn test -pl ."
if ($TestClass -ne "") {
    $mavenCommand += " -Dtest=`"$TestClass`""
}
$mavenCommand += " -DfailIfNoTests=false"

Write-Host "执行命令: $mavenCommand" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 执行Maven测试
Invoke-Expression $mavenCommand

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "测试完成" -ForegroundColor Green