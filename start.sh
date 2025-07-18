#!/bin/bash
# Start script for Linux/Mac
# 启动脚本 - 运行 Host 应用程序

# 颜色定义
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

# 检查 JAR 文件是否存在
if [ ! -f "$HOST_JAR" ]; then
    echo -e "${YELLOW}警告: Host JAR 文件不存在${NC}"
    echo -e "${YELLOW}请先运行 ./build.sh 构建项目${NC}"
    exit 1
fi

# 设置 JVM 参数
JVM_OPTS="-Xms256m -Xmx512m"

# 检查是否传入了额外的参数
if [ $# -gt 0 ]; then
    echo -e "${GREEN}使用自定义参数: $*${NC}"
fi

# 启动应用
echo -e "${GREEN}启动应用...${NC}"
echo -e "${CYAN}JAR: $HOST_JAR${NC}"
echo -e "${CYAN}JVM 参数: $JVM_OPTS${NC}"
echo -e "${CYAN}========================================${NC}"

exec java $JVM_OPTS "$@" -jar "$HOST_JAR" 