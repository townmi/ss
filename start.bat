@echo off
REM === Host with Vert.x - Dynamic Plugin Loading System ===
set BASE=%~dp0

REM === Check if host is built ===
if not exist "%BASE%host\target\host-1.0.0-SNAPSHOT.jar" (
    echo Error: host JAR not found!
    echo Please run: mvnw clean package -pl host -am
    exit /b 1
)

REM === Set configuration file path ===
set "CONFIG_FILE=%BASE%host\src\main\resources\application.properties"

REM === Set system properties ===
set "JAVA_OPTS=-Dconfig.file=%CONFIG_FILE%"
set "JAVA_OPTS=%JAVA_OPTS% -Dplugins.directory=%BASE%libs\plugins"
set "JAVA_OPTS=%JAVA_OPTS% -Dservices.directory=%BASE%libs\services"
set "JAVA_OPTS=%JAVA_OPTS% -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.SLF4JLogDelegateFactory"

REM === Optional: Override settings from config file ===
if not "%HTTP_PORT%"=="" set "JAVA_OPTS=%JAVA_OPTS% -Dhttp.port=%HTTP_PORT%"
if not "%PLUGINS_DIR%"=="" set "JAVA_OPTS=%JAVA_OPTS% -Dplugins.directory=%PLUGINS_DIR%"

REM === Start Host ===
echo ========================================
echo Starting Host with Vert.x...
echo Config: %CONFIG_FILE%
echo Port: 8080 (default, set HTTP_PORT to override)
echo Plugin directory: %BASE%libs\plugins
echo Service directory: %BASE%libs\services
echo ========================================
echo.

REM Do not change directory, run directly with full path
java %JAVA_OPTS% -jar "%BASE%host\target\host-1.0.0-SNAPSHOT.jar" %*