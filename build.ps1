#!/usr/bin/env pwsh
# Build script for Windows PowerShell
# 构建脚本 - 编译所有模块并复制到部署目录

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "开始构建 Direct-LLM-Rask 项目" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 设置错误处理
$ErrorActionPreference = "Stop"

# 获取脚本所在目录（项目根目录）
$ProjectRoot = $PSScriptRoot
$PluginsDir = Join-Path $ProjectRoot "libs/plugins"
$ServicesDir = Join-Path $ProjectRoot "libs/services"

# 检查 Maven Wrapper 是否存在
$MvnCmd = Join-Path $ProjectRoot "mvnw.cmd"
if (-not (Test-Path $MvnCmd)) {
    Write-Host "错误：找不到 Maven Wrapper (mvnw.cmd)" -ForegroundColor Red
    exit 1
}

# 步骤 1: 清理并构建整个项目
Write-Host "`n[1/4] 清理并构建所有模块..." -ForegroundColor Yellow
$BuildResult = & $MvnCmd clean package -DskipTests 2>&1
$BuildExitCode = $LASTEXITCODE

# 显示构建输出的最后几行（包含错误信息）
if ($BuildExitCode -ne 0) {
    Write-Host "`n构建失败！错误信息：" -ForegroundColor Red
    $BuildResult | Select-Object -Last 20 | ForEach-Object { Write-Host $_ }
    Write-Host "`n退出代码: $BuildExitCode" -ForegroundColor Red
    exit 1
}

# 步骤 2: 确保目录存在
Write-Host "`n[2/4] 准备部署目录..." -ForegroundColor Yellow
if (-not (Test-Path $PluginsDir)) {
    New-Item -ItemType Directory -Path $PluginsDir -Force | Out-Null
    Write-Host "创建插件目录: $PluginsDir" -ForegroundColor Green
}
if (-not (Test-Path $ServicesDir)) {
    New-Item -ItemType Directory -Path $ServicesDir -Force | Out-Null
    Write-Host "创建服务目录: $ServicesDir" -ForegroundColor Green
}

# 步骤 3: 复制服务 JAR 文件
Write-Host "`n[3/4] 复制服务 JAR 文件..." -ForegroundColor Yellow
$ServiceCount = 0

# 扫描 services 目录下的所有子目录
$ServicesPath = Join-Path $ProjectRoot "services"
Get-ChildItem -Path $ServicesPath -Directory | ForEach-Object {
    $ModuleName = $_.Name
    $TargetDir = Join-Path $_.FullName "target"
    
    if (Test-Path $TargetDir) {
        # 查找该模块的 JAR 文件（排除 original- 开头的）
        $JarFiles = Get-ChildItem -Path $TargetDir -Filter "*.jar" | 
            Where-Object { $_.Name -notlike "original-*" -and $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-javadoc.jar" }
        
        if ($JarFiles) {
            foreach ($jar in $JarFiles) {
                $DestPath = Join-Path $ServicesDir $jar.Name
                Copy-Item -Path $jar.FullName -Destination $DestPath -Force
                Write-Host "  ✓ 复制服务: $($jar.Name)" -ForegroundColor Green
                $ServiceCount++
            }
        }
    }
}

# 步骤 4: 复制插件 JAR 文件
Write-Host "`n[4/4] 复制插件 JAR 文件..." -ForegroundColor Yellow
$PluginCount = 0

# 扫描 plugins 目录下的所有子目录
$PluginsPath = Join-Path $ProjectRoot "plugins"
Get-ChildItem -Path $PluginsPath -Directory | ForEach-Object {
    $ModuleName = $_.Name
    $TargetDir = Join-Path $_.FullName "target"
    
    if (Test-Path $TargetDir) {
        # 查找该模块的 JAR 文件（排除 original- 开头的）
        $JarFiles = Get-ChildItem -Path $TargetDir -Filter "*.jar" | 
            Where-Object { $_.Name -notlike "original-*" -and $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-javadoc.jar" }
        
        if ($JarFiles) {
            foreach ($jar in $JarFiles) {
                $DestPath = Join-Path $PluginsDir $jar.Name
                Copy-Item -Path $jar.FullName -Destination $DestPath -Force
                Write-Host "  ✓ 复制插件: $($jar.Name)" -ForegroundColor Green
                $PluginCount++
            }
        }
    }
}

# 显示最终结果
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "构建完成！" -ForegroundColor Green
Write-Host "  - 服务: $ServiceCount 个 (位于 libs/services)" -ForegroundColor Cyan
Write-Host "  - 插件: $PluginCount 个 (位于 libs/plugins)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 列出目录内容
Write-Host "`n服务 JAR (libs/services):" -ForegroundColor Yellow
if (Test-Path $ServicesDir) {
    Get-ChildItem -Path $ServicesDir -Filter "*.jar" | ForEach-Object {
        Write-Host "  - $($_.Name) ($([math]::Round($_.Length / 1KB, 1)) KB)" -ForegroundColor Gray
    }
} else {
    Write-Host "  (空)" -ForegroundColor Gray
}

Write-Host "`n插件 JAR (libs/plugins):" -ForegroundColor Yellow
if (Test-Path $PluginsDir) {
    Get-ChildItem -Path $PluginsDir -Filter "*.jar" | ForEach-Object {
        Write-Host "  - $($_.Name) ($([math]::Round($_.Length / 1KB, 1)) KB)" -ForegroundColor Gray
    }
} else {
    Write-Host "  (空)" -ForegroundColor Gray
}

Write-Host "`n提示: 使用 ./start.bat 启动应用程序" -ForegroundColor Cyan 