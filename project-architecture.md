## 1. 模块层次与命名约定

| 层次 | 作用 | Maven `artifactId` 约定 |
|---|---|---|
| Parent / BOM | 统一依赖版本、公共属性 | `work-anyway-parent` |
| 基础接口层 (Interfaces) | 定义跨模块暴露的接口/DTO，仅声明依赖 | *父*: `interfaces` <br/>*各域 Jar*: `interfaces.<domain>` <br/>*插件接口*: `interfaces.plugin` |
| 服务实现层 (Services) | 系统服务的默认实现，依赖接口层 | *父*: `services` <br/>*各域实现*: `services.<domain>` |
| 插件层 (Plugins) | 基于 Plugin 接口的可动态加载插件 | *父*: `plugins` <br/>*各插件*: `plugins.<domain>-plugin` |
| Host 运行时 | **基于 Vert.x** 的运行时，提供 HTTP 服务和插件加载机制 | `host` |
| 业务插件 | 业务实现插件，可独立开发，运行时动态加载 | `biz-<domain>-plugin` |

命名规则：
1. `<domain>` 采用业务域英文缩写或全拼，使用中横线 `-` 连接多词。
2. **插件模块不可依赖其他插件**，跨插件调用通过 *interfaces* 定义的服务接口完成。
3. 服务实现 JAR 放置于 `libs/services/` 目录供 Host 启动时加载。
4. 插件 JAR 放置于 `libs/plugins/` 目录供 Host 启动时扫描加载。

-----
## 2. 依赖与版本管理

1. 统一依赖通过父级 `dependencyManagement` 导入：
   - `vertx-stack-depchain` (当前版本 **4.5.0**)
   - `jackson-bom` (当前版本 **2.17.2**)
2. 所有 Vert.x 和 Jackson 相关依赖在子模块 **禁止** 声明 `<version>`，避免版本漂移。
3. 插件对 `io.vertx:*` 和 `com.fasterxml.jackson:*` 依赖必须标记 `scope=provided`。
4. 日志使用 SLF4J API (版本 **2.0.7**)，运行时由 Host 提供实现。

-----
## 3. 插件开发规范

### 3.1 插件接口
所有插件必须实现 `work.anyway.interfaces.plugin.Plugin` 接口：
```java
public interface Plugin {
    String getName();
    String getVersion();
    void initialize(Router router);
}
```

### 3.2 服务注入
- 插件可通过字段名以 "Service" 结尾自动注入服务
- 服务实例由 Host 的 `ServiceContainer` 统一管理，保证单例
- 服务接口定义在 `interfaces.<domain>` 模块，实现在 `services.<domain>` 模块
- Host 启动时先加载服务 JAR，再加载插件 JAR，确保服务可用

### 3.3 SPI 配置
插件必须在 `META-INF/services/work.anyway.interfaces.plugin.Plugin` 文件中声明实现类。

### 3.4 构建配置
插件必须使用 `maven-shade-plugin` 将依赖的接口类打包进 JAR：
```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-shade-plugin</artifactId>
  <version>3.6.0</version>
  <configuration>
    <artifactSet>
      <includes>
        <include>work.anyway:interfaces.plugin</include>
        <include>work.anyway:interfaces.<domain></include>  <!-- 替换为实际依赖的接口模块 -->
      </includes>
    </artifactSet>
  </configuration>
</plugin>
```
**注意**：缺少此配置会导致运行时 `NoClassDefFoundError`。

### 3.5 页面开发规范

#### 3.5.1 路由规范
- **API 路由**：使用 RESTful 风格，如 `/users`, `/users/:id`
- **页面路由**：统一使用 `/page/<plugin-name>/` 前缀
  - 主页：`/page/<plugin-name>/`
  - 列表页：`/page/<plugin-name>/list`
  - 详情页：`/page/<plugin-name>/:id`
  - 创建页：`/page/<plugin-name>/create`
- **避免路由冲突**：每个插件必须使用独立的路由前缀

#### 3.5.2 模板文件组织
- 模板文件存放在 `src/main/resources/<plugin-name>/templates/` 目录
- 使用独立的资源路径避免类加载器冲突
- 常用模板文件：
  - `index.html` - 插件主页
  - `list.html` - 列表页面
  - `detail.html` - 详情页面
  - `create.html` / `edit.html` - 表单页面

#### 3.5.3 资源加载方式
```java
// 推荐：使用具体类名加载资源，避免类加载器冲突
InputStream is = MyPlugin.class.getResourceAsStream("/<plugin-name>/templates/index.html");

// 不推荐：使用 getClass().getClassLoader()
// InputStream is = getClass().getClassLoader().getResourceAsStream("templates/index.html");
```

#### 3.5.4 页面开发最佳实践
1. **无框架依赖**：使用原生 HTML/CSS/JavaScript，避免引入重型前端框架
2. **响应式设计**：支持桌面和移动设备
3. **统一风格**：使用一致的 UI 组件和配色方案
4. **错误处理**：提供友好的错误提示和空状态展示
5. **性能优化**：
   - 内联关键 CSS 和 JavaScript
   - 避免外部资源依赖
   - 使用适当的缓存策略

#### 3.5.5 模板处理
- 对于静态页面：直接返回 HTML 内容
- 对于动态页面：使用简单的字符串替换或 JSON 数据渲染
- 避免使用重型模板引擎（如 Thymeleaf），保持插件轻量级

-----
## 4. Host 启动与插件加载

### 4.1 配置文件
Host 使用 `application.properties` 配置：
```properties
# HTTP 服务器
http.port=8080
http.host=0.0.0.0

# 插件目录
plugins.directory=libs/plugins
plugins.enabled=true

# 服务目录
services.directory=libs/services
services.enabled=true
```

### 4.2 启动方式
```bash
# Windows
start.bat

# Linux/Mac
./start.sh

# 直接运行
java -jar host/target/host-1.0.0-SNAPSHOT.jar
```

### 4.3 加载机制
1. Host 启动时先扫描 `services.directory` 目录下的服务 JAR 文件
2. 然后扫描 `plugins.directory` 目录下的插件 JAR 文件
3. 使用统一的 `URLClassLoader` 加载所有 JAR
4. 通过 `ServiceLoader` 发现并实例化插件
5. 自动注入依赖的服务
6. 调用插件的 `initialize` 方法注册路由

-----
## 5. 构建与运行

### 5.1 构建命令
```bash
# 编译所有模块
./mvnw clean package -DskipTests

# 仅构建 host
./mvnw clean package -pl host -am

# 构建特定服务
./mvnw clean package -pl services/user -am

# 构建特定插件
./mvnw clean package -pl plugins/user-plugin -am
```

### 5.2 构建脚本
项目提供了跨平台的构建脚本：
- `build.bat` - Windows CMD 脚本
- `build.ps1` - Windows PowerShell 脚本
- `build.sh` - Linux/Mac Bash 脚本

这些脚本会自动：
1. 编译所有模块
2. 复制服务 JAR 到 `libs/services/`
3. 复制插件 JAR 到 `libs/plugins/`

### 5.3 部署
1. 服务 JAR 必须复制到 `libs/services/` 目录
2. 插件 JAR 必须复制到 `libs/plugins/` 目录
3. 重启 Host 以加载新的服务和插件

-----
## 6. 项目结构示例

```
work-anyway/
├── pom.xml                          # 父 POM
├── interfaces/                      # 接口层
│   ├── pom.xml                      # 接口父 POM
│   ├── plugin/                      # 插件接口
│   ├── user/                        # 用户服务接口
│   ├── auth/                        # 认证服务接口
│   ├── cache/                       # 缓存服务接口
│   └── data/                        # 数据服务接口
├── services/                        # 服务实现层
│   ├── pom.xml                      # 服务父 POM
│   ├── user/                        # 用户服务实现
│   ├── auth/                        # 认证服务实现
│   ├── cache/                       # 缓存服务实现
│   └── data/                        # 数据服务实现
├── plugins/                         # 插件层
│   ├── pom.xml                      # 插件父 POM
│   ├── user-plugin/                 # 用户插件（REST API + 页面）
│   │   └── src/main/resources/
│   │       └── user-plugin/
│   │           └── templates/       # 用户管理页面模板
│   ├── auth-plugin/                 # 权限插件（REST API + 页面）
│   │   └── src/main/resources/
│   │       └── auth-plugin/
│   │           └── templates/       # 权限管理页面模板
│   └── data-plugin/                 # 数据插件（REST API + 页面）
│       └── src/main/resources/
│           └── data-plugin/
│               └── templates/       # 数据管理页面模板
├── host/                            # Vert.x 运行时
│   └── src/main/resources/
│       └── application.properties   # 配置文件
├── libs/                            # 运行时库目录
│   ├── services/                    # 服务 JAR 部署目录
│   └── plugins/                     # 插件 JAR 部署目录
├── build.bat                        # Windows 构建脚本
├── build.ps1                        # PowerShell 构建脚本
├── build.sh                         # Linux/Mac 构建脚本
├── start.bat                        # Windows 启动脚本
└── start.sh                         # Linux/Mac 启动脚本
```

-----
## 7. 开发指南

### 7.1 创建新服务
1. 在 `interfaces/<domain>/` 定义服务接口
2. 在 `services/` 下创建 `<domain>` 模块
3. 添加接口依赖：
   ```xml
   <dependency>
     <groupId>work.anyway</groupId>
     <artifactId>interfaces.<domain></artifactId>
     <version>${project.version}</version>
   </dependency>
   ```
4. 实现服务接口
5. 构建并部署到 `libs/services/`

### 7.2 创建新插件
1. 在 `plugins/` 下创建 `<domain>-plugin` 模块
2. 添加依赖：
   ```xml
   <dependency>
     <groupId>work.anyway</groupId>
     <artifactId>interfaces.plugin</artifactId>
     <version>${project.version}</version>
   </dependency>
   <!-- 如需使用服务 -->
   <dependency>
     <groupId>work.anyway</groupId>
     <artifactId>interfaces.<domain></artifactId>
     <version>${project.version}</version>
   </dependency>
   ```
3. 实现 `Plugin` 接口
4. 配置 `maven-shade-plugin`（参考 3.4 节）
5. 创建 SPI 配置文件
6. 构建并部署到 `libs/plugins/`

-----
## 8. 注意事项

1. **无 Quarkus 依赖**：整个项目基于 Vert.x，不使用 Quarkus
2. **动态加载**：支持运行时加载插件，无需重启 Host
3. **服务共享**：所有插件共享同一套服务实例
4. **配置覆盖**：可通过系统属性覆盖配置文件设置
5. **类加载隔离**：服务和插件使用统一的类加载器，确保互相可见
6. **插件打包**：插件必须使用 `maven-shade-plugin` 将接口类打包进 JAR，否则会出现 `NoClassDefFoundError`

-----
## 9. 页面开发示例

### 9.1 插件页面结构
```
plugins/<domain>-plugin/
└── src/main/resources/
    └── <domain>-plugin/
        └── templates/
            ├── index.html      # 插件主页
            ├── list.html       # 列表页面
            ├── detail.html     # 详情页面
            └── form.html       # 表单页面
```

### 9.2 路由注册示例
```java
@Override
public void initialize(Router router) {
    // API 路由
    router.get("/api/users").handler(this::getUsers);
    router.post("/api/users").handler(this::createUser);
    
    // 页面路由
    router.get("/page/users/").handler(this::getIndexPage);
    router.get("/page/users/list").handler(this::getListPage);
    router.get("/page/users/create").handler(this::getCreatePage);
    router.get("/page/users/:id").handler(this::getDetailPage);
}
```

### 9.3 页面渲染示例
```java
private void getIndexPage(RoutingContext ctx) {
    try {
        String html = readResourceFile("user-plugin/templates/index.html");
        if (html != null) {
            ctx.response()
                .putHeader("content-type", "text/html; charset=utf-8")
                .end(html);
        } else {
            ctx.response().setStatusCode(404).end("Template not found");
        }
    } catch (Exception e) {
        LOG.error("Error rendering page", e);
        ctx.response().setStatusCode(500).end("Internal Server Error");
    }
}

private String readResourceFile(String path) {
    try (InputStream is = UserPlugin.class.getResourceAsStream("/" + path)) {
        if (is == null) return null;
        try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name())) {
            scanner.useDelimiter("\\A");
            return scanner.hasNext() ? scanner.next() : "";
        }
    } catch (Exception e) {
        LOG.error("Error reading resource: " + path, e);
        return null;
    }
}
```

### 9.4 Host 主页路由
Host 可以在 `/page/` 提供插件导航页面：
```java
router.get("/page/").handler(ctx -> {
    // 生成插件列表页面
    StringBuilder html = new StringBuilder();
    html.append("<h1>已加载插件</h1>");
    for (Plugin plugin : loadedPlugins) {
        html.append("<a href='/page/").append(plugin.getName().toLowerCase())
            .append("/'>").append(plugin.getName()).append("</a><br>");
    }
    ctx.response()
        .putHeader("content-type", "text/html; charset=utf-8")
        .end(html.toString());
});
```

-----
## 10. 开发环境规范

### 10.1 命令执行环境
- **Windows 系统**：必须使用 PowerShell 执行所有命令
  - 推荐使用 PowerShell 7+ (pwsh.exe)
  - 避免使用 CMD 或 Git Bash，可能导致路径和编码问题
  
### 10.2 常用命令示例
```powershell
# Maven 构建命令
./mvnw clean package -DskipTests

# 运行 Host
java -jar host/target/host-1.0.0-SNAPSHOT.jar

# 复制服务到部署目录
Copy-Item services/user/target/services.user-1.0.0-SNAPSHOT.jar -Destination libs/services/

# 复制插件到部署目录
Copy-Item plugins/user-plugin/target/plugins.user-plugin-1.0.0-SNAPSHOT.jar -Destination libs/plugins/

# 查看部署目录
Get-ChildItem libs/services/
Get-ChildItem libs/plugins/

# 启动应用
./start.bat
```

### 10.3 路径处理
- 使用正斜杠 `/` 或反斜杠 `\` 都可以，PowerShell 会自动处理
- 推荐使用相对路径，避免硬编码绝对路径
- 如需绝对路径，使用 `$PWD` 获取当前目录：
  ```powershell
  $PWD/libs/plugins/
  ``` 