#!/bin/bash
# Build script for Linux/Mac
# 构建脚本 - 编译所有模块并复制插件到部署目录

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
PACKAGES_DIR="${PROJECT_ROOT}/packages"

# 检查 Maven Wrapper 是否存在
MVN_CMD="${PROJECT_ROOT}/mvnw"
if [ ! -f "$MVN_CMD" ]; then
    echo -e "${RED}错误：找不到 Maven Wrapper (mvnw)${NC}"
    exit 1
fi

# 确保 mvnw 可执行
chmod +x "$MVN_CMD"

# 步骤 1: 清理并构建整个项目
echo -e "\n${YELLOW}[1/3] 清理并构建所有模块...${NC}"
"$MVN_CMD" clean package -DskipTests
if [ $? -ne 0 ]; then
    echo -e "${RED}构建失败！${NC}"
    exit 1
fi

# 步骤 2: 确保插件目录存在
echo -e "\n${YELLOW}[2/3] 准备插件目录...${NC}"
if [ ! -d "$PLUGINS_DIR" ]; then
    mkdir -p "$PLUGINS_DIR"
    echo -e "${GREEN}创建插件目录: $PLUGINS_DIR${NC}"
fi

# 步骤 3: 自动扫描并复制所有 JAR 文件
echo -e "\n${YELLOW}[3/3] 扫描并复制 JAR 文件到部署目录...${NC}"

# 复制计数器
COPIED_COUNT=0
PLUGIN_COUNT=0
SERVICE_COUNT=0

# 扫描 packages 目录下的所有子目录
for module_dir in "$PACKAGES_DIR"/*; do
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
                        
                        # 判断是插件还是服务
                        if [[ "$module_name" == *-plugin ]]; then
                            module_type="插件"
                            ((PLUGIN_COUNT++))
                        else
                            module_type="服务"
                            ((SERVICE_COUNT++))
                        fi
                        
                        # 复制文件
                        cp -f "$jar" "$PLUGINS_DIR/"
                        echo -e "  ${GREEN}✓ 复制 $module_type: $jar_name${NC}"
                        ((COPIED_COUNT++))
                    fi
                fi
            done
        fi
    fi
done

# 显示最终结果
echo -e "\n${CYAN}========================================${NC}"
echo -e "${GREEN}构建完成！${NC}"
echo -e "${GREEN}共复制 $COPIED_COUNT 个 JAR 文件到插件目录${NC}"
echo -e "${CYAN}  - 插件: $PLUGIN_COUNT 个${NC}"
echo -e "${CYAN}  - 服务: $SERVICE_COUNT 个${NC}"
echo -e "${CYAN}插件目录: $PLUGINS_DIR${NC}"
echo -e "${CYAN}========================================${NC}"

# 列出插件目录内容（按类型分组）
echo -e "\n${YELLOW}当前插件目录内容:${NC}"

# 先列出插件
echo -e "\n${CYAN}插件 JAR:${NC}"
for jar in "$PLUGINS_DIR"/*-plugin-*.jar; do
    if [ -f "$jar" ]; then
        jar_name=$(basename "$jar")
        jar_size=$(du -h "$jar" | cut -f1)
        echo -e "  - $jar_name ($jar_size)"
    fi
done

# 再列出服务
echo -e "\n${CYAN}服务 JAR:${NC}"
for jar in "$PLUGINS_DIR"/*.jar; do
    if [ -f "$jar" ]; then
        jar_name=$(basename "$jar")
        if [[ "$jar_name" != *-plugin-* ]]; then
            jar_size=$(du -h "$jar" | cut -f1)
            echo -e "  - $jar_name ($jar_size)"
        fi
    fi
done

echo -e "\n${CYAN}提示: 使用 ./start.sh 启动应用程序${NC}" 