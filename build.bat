@echo off
setlocal enabledelayedexpansion
REM Build script for Windows CMD
REM 构建脚本 - 编译所有模块并复制到部署目录

echo ========================================
echo 开始构建 Direct-LLM-Rask 项目
echo ========================================

REM 设置变量
set PROJECT_ROOT=%~dp0
set PLUGINS_DIR=%PROJECT_ROOT%libs\plugins
set SERVICES_DIR=%PROJECT_ROOT%libs\services

REM 步骤 1: 清理并构建整个项目
echo.
echo [1/4] 清理并构建所有模块...
call mvnw.cmd clean package -DskipTests
if %ERRORLEVEL% neq 0 (
    echo 构建失败！
    exit /b 1
)

REM 步骤 2: 确保目录存在
echo.
echo [2/4] 准备部署目录...
if not exist "%PLUGINS_DIR%" (
    mkdir "%PLUGINS_DIR%"
    echo 创建插件目录: %PLUGINS_DIR%
)
if not exist "%SERVICES_DIR%" (
    mkdir "%SERVICES_DIR%"
    echo 创建服务目录: %SERVICES_DIR%
)

REM 步骤 3: 复制服务 JAR 文件
echo.
echo [3/4] 复制服务 JAR 文件...
set /a SERVICE_COUNT=0

REM 扫描 services 目录下的所有子目录
for /d %%D in ("%PROJECT_ROOT%services\*") do (
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
                    echo   √ 复制服务: %%~nxJ
                    copy /Y "%%J" "%SERVICES_DIR%\" >nul
                    set /a SERVICE_COUNT+=1
                )
            )
        )
    )
)

REM 步骤 4: 复制插件 JAR 文件
echo.
echo [4/4] 复制插件 JAR 文件...
set /a PLUGIN_COUNT=0

REM 扫描 plugins 目录下的所有子目录
for /d %%D in ("%PROJECT_ROOT%plugins\*") do (
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
                    echo   √ 复制插件: %%~nxJ
                    copy /Y "%%J" "%PLUGINS_DIR%\" >nul
                    set /a PLUGIN_COUNT+=1
                )
            )
        )
    )
)

REM 显示最终结果
echo.
echo ========================================
echo 构建完成！
echo   - 服务: %SERVICE_COUNT% 个 (位于 libs\services)
echo   - 插件: %PLUGIN_COUNT% 个 (位于 libs\plugins)
echo ========================================

REM 列出目录内容
echo.
echo 服务 JAR (libs\services):
if exist "%SERVICES_DIR%\*.jar" (
    for %%F in ("%SERVICES_DIR%\*.jar") do echo   - %%~nxF
) else (
    echo   (空)
)

echo.
echo 插件 JAR (libs\plugins):
if exist "%PLUGINS_DIR%\*.jar" (
    for %%F in ("%PLUGINS_DIR%\*.jar") do echo   - %%~nxF
) else (
    echo   (空)
)

echo.
echo 提示: 使用 start.bat 启动应用程序
pause 