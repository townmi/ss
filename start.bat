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
REM Use external config file in project root directory
set "CONFIG_FILE=%BASE%application.properties"

REM === Check if external config exists ===
if not exist "%CONFIG_FILE%" (
    echo Warning: External config file not found at %CONFIG_FILE%
    echo Application will use default configuration from JAR
)

REM === Convert to absolute path and handle spaces ===
pushd "%BASE%"
set "ABS_BASE=%CD%"
popd

REM === Set system properties ===
REM Pass the config file path as an absolute path with quotes handled properly
set JAVA_OPTS=-Dconfig.file="%ABS_BASE%\application.properties"
set JAVA_OPTS=%JAVA_OPTS% -Dplugins.directory="%ABS_BASE%\libs\plugins"
set JAVA_OPTS=%JAVA_OPTS% -Dservices.directory="%ABS_BASE%\libs\services"
set JAVA_OPTS=%JAVA_OPTS% -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.SLF4JLogDelegateFactory

REM === Theme configuration ===
set JAVA_OPTS=%JAVA_OPTS% -Dapp.root="%ABS_BASE%"
set JAVA_OPTS=%JAVA_OPTS% -Dtheme.directory="%ABS_BASE%\themes"
set JAVA_OPTS=%JAVA_OPTS% -Dfile.encoding=UTF-8

REM === Optional: Override settings from config file ===
if not "%HTTP_PORT%"=="" set JAVA_OPTS=%JAVA_OPTS% -Dhttp.port=%HTTP_PORT%
if not "%PLUGINS_DIR%"=="" set JAVA_OPTS=%JAVA_OPTS% -Dplugins.directory="%PLUGINS_DIR%"
if not "%THEME_DIR%"=="" set JAVA_OPTS=%JAVA_OPTS% -Dtheme.directory="%THEME_DIR%"
if not "%ACTIVE_THEME%"=="" set JAVA_OPTS=%JAVA_OPTS% -Dtheme.active=%ACTIVE_THEME%

REM === Start Host ===
echo ========================================
echo Starting Host with Vert.x...
echo Config: %CONFIG_FILE%
echo Port: Reading from config file
echo Plugin directory: %ABS_BASE%\libs\plugins
echo Service directory: %ABS_BASE%\libs\services
echo Theme directory: %ABS_BASE%\themes
echo Active theme: %ACTIVE_THEME% (default if not set)
echo ========================================
echo.

REM Do not change directory, run directly with full path
java %JAVA_OPTS% -jar "%BASE%host\target\host-1.0.0-SNAPSHOT.jar" %*