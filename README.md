# Direct-LLM-Rask

基于 Vert.x 的插件化系统，支持动态加载插件和服务。

## 项目结构

```
direct-llm-rask/
├── interface/          # 接口定义层
│   ├── plugin/        # 插件接口
│   ├── user/          # 用户服务接口
│   ├── auth/          # 认证服务接口
│   └── cache/         # 缓存服务接口
├── packages/          # 实现层
│   ├── user/          # 用户服务实现
│   ├── auth/          # 认证服务实现
│   ├── cache/         # 缓存服务实现
│   ├── user-plugin/   # 用户管理插件
│   └── auth-plugin/   # 权限管理插件
├── host/              # Vert.x 运行时主机
├── libs/plugins/      # 插件部署目录
└── .cursor/rules/     # 项目规范文档
```

## 快速开始

### 前置要求

- Java 17 或更高版本
- Maven 3.6+（项目包含 Maven Wrapper）

### 构建项目

#### Windows (PowerShell)
```powershell
./build.ps1
```

#### Windows (CMD)
```cmd
build.bat
```

#### Linux/Mac
```bash
chmod +x build.sh
./build.sh
```

构建脚本会自动：
1. 编译所有模块
2. 运行测试（可通过 `-DskipTests` 跳过）
3. 将插件 JAR 复制到 `libs/plugins/` 目录

### 运行应用

#### Windows
```cmd
start.bat
```

#### Linux/Mac
```bash
chmod +x start.sh
./start.sh
```

### 自定义配置

可以通过系统属性覆盖默认配置：

```bash
# 修改端口
java -Dhttp.port=9090 -jar host/target/host-1.0.0-SNAPSHOT.jar

# Windows PowerShell
./start.bat -Dhttp.port=9090
```

## 插件开发

### 创建新插件

1. 在 `packages/` 下创建新模块
2. 实现 `work.anyway.api.plugin.Plugin` 接口
3. 在 `META-INF/services/work.anyway.api.plugin.Plugin` 中声明实现类
4. 构建并部署到 `libs/plugins/`

详细开发指南请参考 `.cursor/rules/project-architecture.mdc`

## API 文档

应用启动后，访问以下地址：

- 主页：http://localhost:8080/page/
- 用户管理：http://localhost:8080/page/users/
- 权限管理：http://localhost:8080/page/auth/

### REST API

- 用户管理 API：`/users`, `/users/:id`
- 权限管理 API：`/permissions`, `/permissions/:id`

## 开发规范

- Java 代码格式化：使用 Google Java Format
- 项目架构规范：见 `.cursor/rules/project-architecture.mdc`
- 代码风格指南：见 `.cursor/rules/java-formatting.mdc`

## 许可证

[添加许可证信息] 