@echo off
REM === Host with Vert.x - 动态插件加载系统 ===
set BASE=%~dp0

REM === 检查 host 是否已构建 ===
if not exist "%BASE%host\target\host-1.0.0-SNAPSHOT.jar" (
    echo Error: host JAR not found!
    echo Please run: mvnw clean package -pl host -am
    exit /b 1
)

REM === 设置配置文件路径 ===
set "CONFIG_FILE=%BASE%host\src\main\resources\application.properties"

REM === 设置系统属性 ===
set "JAVA_OPTS=-Dconfig.file=%CONFIG_FILE%"
set "JAVA_OPTS=%JAVA_OPTS% -Dplugins.directory=%BASE%libs\plugins"
set "JAVA_OPTS=%JAVA_OPTS% -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.SLF4JLogDelegateFactory"

REM === 可选：覆盖配置文件中的设置 ===
if not "%HTTP_PORT%"=="" set "JAVA_OPTS=%JAVA_OPTS% -Dhttp.port=%HTTP_PORT%"
if not "%PLUGINS_DIR%"=="" set "JAVA_OPTS=%JAVA_OPTS% -Dplugins.directory=%PLUGINS_DIR%"

REM === 启动 Host ===
echo ========================================
echo Starting Host with Vert.x...
echo Config: %CONFIG_FILE%
echo Port: 8080 (default, set HTTP_PORT to override)
echo Plugin directory: %BASE%libs\plugins
echo ========================================
echo.

cd /d "%BASE%host"
java %JAVA_OPTS% -jar target\host-1.0.0-SNAPSHOT.jar %*