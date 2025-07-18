@echo off
setlocal enabledelayedexpansion
REM Build script for Windows CMD
REM 构建脚本 - 编译所有模块并复制插件到部署目录

echo ========================================
echo 开始构建 Direct-LLM-Rask 项目
echo ========================================

REM 设置变量
set PROJECT_ROOT=%~dp0
set PLUGINS_DIR=%PROJECT_ROOT%libs\plugins
set PACKAGES_DIR=%PROJECT_ROOT%packages

REM 步骤 1: 清理并构建整个项目
echo.
echo [1/3] 清理并构建所有模块...
call mvnw.cmd clean package -DskipTests
if %ERRORLEVEL% neq 0 (
    echo 构建失败！
    exit /b 1
)

REM 步骤 2: 确保插件目录存在
echo.
echo [2/3] 准备插件目录...
if not exist "%PLUGINS_DIR%" (
    mkdir "%PLUGINS_DIR%"
    echo 创建插件目录: %PLUGINS_DIR%
)

REM 步骤 3: 自动扫描并复制所有 JAR 文件
echo.
echo [3/3] 扫描并复制 JAR 文件到部署目录...

REM 初始化计数器
set /a COPIED_COUNT=0
set /a PLUGIN_COUNT=0
set /a SERVICE_COUNT=0

REM 扫描 packages 目录下的所有子目录
for /d %%D in ("%PACKAGES_DIR%\*") do (
    if exist "%%D\target" (
        REM 获取模块名
        for %%F in ("%%D") do set MODULE_NAME=%%~nxF
        
        REM 复制该模块的 JAR 文件
        for %%J in ("%%D\target\*.jar") do (
            set JAR_NAME=%%~nxJ
            
            REM 检查是否是需要跳过的文件
            echo !JAR_NAME! | findstr /B "original-" >nul
            if errorlevel 1 (
                echo !JAR_NAME! | findstr /E "-sources.jar -javadoc.jar" >nul
                if errorlevel 1 (
                    REM 判断是插件还是服务
                    echo !MODULE_NAME! | findstr /E "-plugin" >nul
                    if not errorlevel 1 (
                        echo   √ 复制插件: %%~nxJ
                        set /a PLUGIN_COUNT+=1
                    ) else (
                        echo   √ 复制服务: %%~nxJ
                        set /a SERVICE_COUNT+=1
                    )
                    
                    copy /Y "%%J" "%PLUGINS_DIR%\" >nul
                    set /a COPIED_COUNT+=1
                )
            )
        )
    )
)

REM 显示最终结果
echo.
echo ========================================
echo 构建完成！
echo 共复制 %COPIED_COUNT% 个 JAR 文件到插件目录
echo   - 插件: %PLUGIN_COUNT% 个
echo   - 服务: %SERVICE_COUNT% 个
echo 插件目录: %PLUGINS_DIR%
echo ========================================

REM 列出插件目录内容
echo.
echo 当前插件目录内容:

echo.
echo 插件 JAR:
for %%F in ("%PLUGINS_DIR%\*-plugin-*.jar") do (
    if exist "%%F" echo   - %%~nxF
)

echo.
echo 服务 JAR:
for %%F in ("%PLUGINS_DIR%\*.jar") do (
    echo %%~nxF | findstr /V "\-plugin\-" >nul
    if not errorlevel 1 echo   - %%~nxF
)

echo.
echo 提示: 使用 start.bat 启动应用程序
pause 