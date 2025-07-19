#!/bin/bash
# Start script for Linux/Mac
# 启动脚本 - 运行 Host 应用程序

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${CYAN}========================================${NC}"
echo -e "${CYAN}启动 Direct-LLM-Rask 应用${NC}"
echo -e "${CYAN}========================================${NC}"

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOST_JAR="${SCRIPT_DIR}/host/target/host-1.0.0-SNAPSHOT.jar"
CONFIG_FILE="${SCRIPT_DIR}/host/src/main/resources/application.properties"

# 检查 JAR 文件是否存在
if [ ! -f "$HOST_JAR" ]; then
    echo -e "${RED}错误: Host JAR 文件不存在${NC}"
    echo -e "${YELLOW}请先运行 ./build.sh 构建项目${NC}"
    exit 1
fi

# 设置系统属性
JAVA_OPTS="-Dconfig.file=${CONFIG_FILE}"
JAVA_OPTS="${JAVA_OPTS} -Dplugins.directory=${SCRIPT_DIR}/libs/plugins"
JAVA_OPTS="${JAVA_OPTS} -Dservices.directory=${SCRIPT_DIR}/libs/services"
JAVA_OPTS="${JAVA_OPTS} -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.SLF4JLogDelegateFactory"

# 可选：覆盖配置文件中的设置
if [ ! -z "$HTTP_PORT" ]; then
    JAVA_OPTS="${JAVA_OPTS} -Dhttp.port=${HTTP_PORT}"
fi
if [ ! -z "$PLUGINS_DIR" ]; then
    JAVA_OPTS="${JAVA_OPTS} -Dplugins.directory=${PLUGINS_DIR}"
fi
if [ ! -z "$SERVICES_DIR" ]; then
    JAVA_OPTS="${JAVA_OPTS} -Dservices.directory=${SERVICES_DIR}"
fi

# 启动应用
echo -e "${GREEN}配置文件: ${CONFIG_FILE}${NC}"
echo -e "${GREEN}端口: 8080 (默认，设置 HTTP_PORT 环境变量来覆盖)${NC}"
echo -e "${GREEN}插件目录: ${SCRIPT_DIR}/libs/plugins${NC}"
echo -e "${GREEN}服务目录: ${SCRIPT_DIR}/libs/services${NC}"
echo -e "${CYAN}========================================${NC}"
echo

# 不要改变工作目录，直接使用完整路径运行
exec java ${JAVA_OPTS} "$@" -jar "${HOST_JAR}" 