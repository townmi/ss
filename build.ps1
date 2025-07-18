#!/usr/bin/env pwsh
# Build script for Windows PowerShell
# 构建脚本 - 编译所有模块并复制插件到部署目录

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "开始构建 Direct-LLM-Rask 项目" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 设置错误处理
$ErrorActionPreference = "Stop"

# 获取脚本所在目录（项目根目录）
$ProjectRoot = $PSScriptRoot
$PluginsDir = Join-Path $ProjectRoot "libs/plugins"
$PackagesDir = Join-Path $ProjectRoot "packages"

# 检查 Maven Wrapper 是否存在
$MvnCmd = Join-Path $ProjectRoot "mvnw"
if (-not (Test-Path $MvnCmd)) {
    Write-Host "错误：找不到 Maven Wrapper (mvnw)" -ForegroundColor Red
    exit 1
}

# 步骤 1: 清理并构建整个项目
Write-Host "`n[1/3] 清理并构建所有模块..." -ForegroundColor Yellow
& $MvnCmd clean package -DskipTests
if ($LASTEXITCODE -ne 0) {
    Write-Host "构建失败！" -ForegroundColor Red
    exit 1
}

# 步骤 2: 确保插件目录存在
Write-Host "`n[2/3] 准备插件目录..." -ForegroundColor Yellow
if (-not (Test-Path $PluginsDir)) {
    New-Item -ItemType Directory -Path $PluginsDir -Force | Out-Null
    Write-Host "创建插件目录: $PluginsDir" -ForegroundColor Green
}

# 步骤 3: 自动扫描并复制所有 JAR 文件
Write-Host "`n[3/3] 扫描并复制 JAR 文件到部署目录..." -ForegroundColor Yellow

$CopiedCount = 0
$PluginCount = 0
$ServiceCount = 0

# 扫描 packages 目录下的所有子目录
Get-ChildItem -Path $PackagesDir -Directory | ForEach-Object {
    $ModuleName = $_.Name
    $TargetDir = Join-Path $_.FullName "target"
    
    if (Test-Path $TargetDir) {
        # 查找该模块的 JAR 文件（排除 original- 开头的）
        $JarFiles = Get-ChildItem -Path $TargetDir -Filter "*.jar" | 
            Where-Object { $_.Name -notlike "original-*" -and $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-javadoc.jar" }
        
        if ($JarFiles) {
            # 判断是插件还是服务
            $IsPlugin = $ModuleName -like "*-plugin"
            $ModuleType = if ($IsPlugin) { "插件" } else { "服务" }
            
            foreach ($jar in $JarFiles) {
                $DestPath = Join-Path $PluginsDir $jar.Name
                Copy-Item -Path $jar.FullName -Destination $DestPath -Force
                Write-Host "  ✓ 复制 $ModuleType : $($jar.Name)" -ForegroundColor Green
                $CopiedCount++
                
                if ($IsPlugin) { $PluginCount++ } else { $ServiceCount++ }
            }
        }
    }
}

# 显示最终结果
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "构建完成！" -ForegroundColor Green
Write-Host "共复制 $CopiedCount 个 JAR 文件到插件目录" -ForegroundColor Green
Write-Host "  - 插件: $PluginCount 个" -ForegroundColor Cyan
Write-Host "  - 服务: $ServiceCount 个" -ForegroundColor Cyan
Write-Host "插件目录: $PluginsDir" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 列出插件目录内容（按类型分组）
Write-Host "`n当前插件目录内容:" -ForegroundColor Yellow

# 先列出插件
Write-Host "`n插件 JAR:" -ForegroundColor Cyan
Get-ChildItem -Path $PluginsDir -Filter "*-plugin-*.jar" | ForEach-Object {
    Write-Host "  - $($_.Name) ($([math]::Round($_.Length / 1KB, 1)) KB)" -ForegroundColor Gray
}

# 再列出服务
Write-Host "`n服务 JAR:" -ForegroundColor Cyan
Get-ChildItem -Path $PluginsDir -Filter "*.jar" | 
    Where-Object { $_.Name -notlike "*-plugin-*" } | 
    ForEach-Object {
        Write-Host "  - $($_.Name) ($([math]::Round($_.Length / 1KB, 1)) KB)" -ForegroundColor Gray
    }

Write-Host "`n提示: 使用 ./start.bat 启动应用程序" -ForegroundColor Cyan 