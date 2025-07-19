#!/bin/bash
# Build script for Linux/Mac
# 构建脚本 - 编译所有模块并复制到部署目录

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${CYAN}========================================${NC}"
echo -e "${CYAN}开始构建 Direct-LLM-Rask 项目${NC}"
echo -e "${CYAN}========================================${NC}"

# 获取脚本所在目录（项目根目录）
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PLUGINS_DIR="${PROJECT_ROOT}/libs/plugins"
SERVICES_DIR="${PROJECT_ROOT}/libs/services"

# 检查 Maven Wrapper 是否存在
MVN_CMD="${PROJECT_ROOT}/mvnw"
if [ ! -f "$MVN_CMD" ]; then
    echo -e "${RED}错误：找不到 Maven Wrapper (mvnw)${NC}"
    exit 1
fi

# 确保 mvnw 可执行
chmod +x "$MVN_CMD"

# 步骤 1: 清理并构建整个项目
echo -e "\n${YELLOW}[1/4] 清理并构建所有模块...${NC}"
"$MVN_CMD" clean package -DskipTests
if [ $? -ne 0 ]; then
    echo -e "${RED}构建失败！${NC}"
    exit 1
fi

# 步骤 2: 确保目录存在
echo -e "\n${YELLOW}[2/4] 准备部署目录...${NC}"
if [ ! -d "$PLUGINS_DIR" ]; then
    mkdir -p "$PLUGINS_DIR"
    echo -e "${GREEN}创建插件目录: $PLUGINS_DIR${NC}"
fi
if [ ! -d "$SERVICES_DIR" ]; then
    mkdir -p "$SERVICES_DIR"
    echo -e "${GREEN}创建服务目录: $SERVICES_DIR${NC}"
fi

# 步骤 3: 复制服务 JAR 文件
echo -e "\n${YELLOW}[3/4] 复制服务 JAR 文件...${NC}"
SERVICE_COUNT=0

# 扫描 services 目录下的所有子目录
for module_dir in "$PROJECT_ROOT"/services/*; do
    if [ -d "$module_dir" ]; then
        module_name=$(basename "$module_dir")
        target_dir="$module_dir/target"
        
        if [ -d "$target_dir" ]; then
            # 查找 JAR 文件（排除 original-、sources、javadoc）
            for jar in "$target_dir"/*.jar; do
                if [ -f "$jar" ]; then
                    jar_name=$(basename "$jar")
                    
                    # 跳过不需要的 JAR 文件
                    if [[ "$jar_name" != original-* ]] && \
                       [[ "$jar_name" != *-sources.jar ]] && \
                       [[ "$jar_name" != *-javadoc.jar ]]; then
                        
                        # 复制文件
                        cp -f "$jar" "$SERVICES_DIR/"
                        echo -e "  ${GREEN}✓ 复制服务: $jar_name${NC}"
                        ((SERVICE_COUNT++))
                    fi
                fi
            done
        fi
    fi
done

# 步骤 4: 复制插件 JAR 文件
echo -e "\n${YELLOW}[4/4] 复制插件 JAR 文件...${NC}"
PLUGIN_COUNT=0

# 扫描 plugins 目录下的所有子目录
for module_dir in "$PROJECT_ROOT"/plugins/*; do
    if [ -d "$module_dir" ]; then
        module_name=$(basename "$module_dir")
        target_dir="$module_dir/target"
        
        if [ -d "$target_dir" ]; then
            # 查找 JAR 文件（排除 original-、sources、javadoc）
            for jar in "$target_dir"/*.jar; do
                if [ -f "$jar" ]; then
                    jar_name=$(basename "$jar")
                    
                    # 跳过不需要的 JAR 文件
                    if [[ "$jar_name" != original-* ]] && \
                       [[ "$jar_name" != *-sources.jar ]] && \
                       [[ "$jar_name" != *-javadoc.jar ]]; then
                        
                        # 复制文件
                        cp -f "$jar" "$PLUGINS_DIR/"
                        echo -e "  ${GREEN}✓ 复制插件: $jar_name${NC}"
                        ((PLUGIN_COUNT++))
                    fi
                fi
            done
        fi
    fi
done

# 显示最终结果
echo -e "\n${CYAN}========================================${NC}"
echo -e "${GREEN}构建完成！${NC}"
echo -e "${CYAN}  - 服务: $SERVICE_COUNT 个 (位于 libs/services)${NC}"
echo -e "${CYAN}  - 插件: $PLUGIN_COUNT 个 (位于 libs/plugins)${NC}"
echo -e "${CYAN}========================================${NC}"

# 列出目录内容
echo -e "\n${YELLOW}服务 JAR (libs/services):${NC}"
if [ -d "$SERVICES_DIR" ] && [ "$(ls -A "$SERVICES_DIR"/*.jar 2>/dev/null)" ]; then
    for jar in "$SERVICES_DIR"/*.jar; do
        jar_name=$(basename "$jar")
        jar_size=$(du -h "$jar" | cut -f1)
        echo -e "  - $jar_name ($jar_size)"
    done
else
    echo -e "  (空)"
fi

echo -e "\n${YELLOW}插件 JAR (libs/plugins):${NC}"
if [ -d "$PLUGINS_DIR" ] && [ "$(ls -A "$PLUGINS_DIR"/*.jar 2>/dev/null)" ]; then
    for jar in "$PLUGINS_DIR"/*.jar; do
        jar_name=$(basename "$jar")
        jar_size=$(du -h "$jar" | cut -f1)
        echo -e "  - $jar_name ($jar_size)"
    done
else
    echo -e "  (空)"
fi

echo -e "\n${CYAN}提示: 使用 ./start.sh 启动应用程序${NC}" 