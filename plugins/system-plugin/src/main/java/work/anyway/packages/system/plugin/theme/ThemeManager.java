package work.anyway.packages.system.plugin.theme;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import work.anyway.annotations.TemplateProcessor;
import work.anyway.interfaces.system.Theme;
import work.anyway.interfaces.system.ThemeConfig;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 主题管理器
 * 负责加载、管理和应用主题
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Component
@Order(100) // 确保在其他处理器之后执行
public class ThemeManager implements TemplateProcessor, InitializingBean {

  private static final Logger LOG = LoggerFactory.getLogger(ThemeManager.class);

  @Value("${theme.directory:themes}")
  private String themeDirectory;

  @Value("${theme.active:default}")
  private String activeTheme;

  @Value("${theme.fallback:default}")
  private String fallbackTheme;

  @Value("${theme.cache.enabled:true}")
  private boolean cacheEnabled;

  @Value("${theme.watch.enabled:false}")
  private boolean watchEnabled;

  private final Map<String, Theme> themes = new ConcurrentHashMap<>();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private File themeRootDir;
  private WatchService watchService;

  // 为每个主题创建独立的 MustacheFactory
  private final Map<String, MustacheFactory> themeMustacheFactories = new ConcurrentHashMap<>();

  @Override
  public void afterPropertiesSet() throws Exception {
    initialize();
  }

  private void initialize() {
    try {
      LOG.info("Initializing ThemeManager...");
      LOG.info("Theme directory: {}", themeDirectory);
      LOG.info("Active theme: {}", activeTheme);
      LOG.info("Fallback theme: {}", fallbackTheme);

      // 确保 activeTheme 不为空
      if (activeTheme == null || activeTheme.trim().isEmpty()) {
        LOG.warn("Active theme is null or empty, using default");
        activeTheme = "default";
      }

      if (fallbackTheme == null || fallbackTheme.trim().isEmpty()) {
        LOG.warn("Fallback theme is null or empty, using default");
        fallbackTheme = "default";
      }

      // 确定主题目录
      this.themeRootDir = resolveThemeDirectory();

      if (!themeRootDir.exists() || !themeRootDir.isDirectory()) {
        LOG.error("Theme directory not found: {}", themeRootDir.getAbsolutePath());
        return;
      }

      LOG.info("Loading themes from: {}", themeRootDir.getAbsolutePath());

      // 加载所有主题
      loadAllThemes();

      // 验证活动主题
      if (!themes.containsKey(activeTheme)) {
        LOG.warn("Active theme '{}' not found, falling back to '{}'", activeTheme, fallbackTheme);
        activeTheme = fallbackTheme;

        // 如果回退主题也不存在，使用第一个可用主题
        if (!themes.containsKey(activeTheme) && !themes.isEmpty()) {
          String firstTheme = themes.keySet().iterator().next();
          LOG.warn("Fallback theme '{}' not found, using first available theme: '{}'", fallbackTheme, firstTheme);
          activeTheme = firstTheme;
        }
      }

      LOG.info("Active theme set to: {}", activeTheme);

      // 开发模式下监控文件变化
      if (watchEnabled) {
        startThemeWatcher();
      }

    } catch (Exception e) {
      LOG.error("Failed to initialize theme manager", e);
    }
  }

  @Override
  public String process(String content, RoutingContext ctx) {
    Theme theme = getCurrentTheme();
    if (theme == null) {
      return content;
    }

    try {
      // 准备数据
      Map<String, Object> data = prepareThemeData(ctx);
      data.put("content", content);

      // 获取布局
      String layoutName = ctx.get("_layout") != null ? ctx.get("_layout") : "base";

      // 应用主题布局
      return renderLayout(theme, layoutName, data);

    } catch (Exception e) {
      LOG.error("Failed to apply theme", e);
      return content;
    }
  }

  /**
   * 解析主题目录路径
   */
  private File resolveThemeDirectory() {
    // 首先检查是否通过系统属性指定了主题目录
    String sysThemeDir = System.getProperty("theme.directory");
    if (sysThemeDir != null && !sysThemeDir.isEmpty()) {
      File dir = new File(sysThemeDir);
      if (dir.exists() && dir.isDirectory()) {
        return dir;
      }
    }

    // 使用配置文件中的值
    File dir = new File(themeDirectory);

    if (dir.isAbsolute()) {
      return dir;
    }

    // 相对路径处理
    // 1. 尝试相对于应用根目录
    String appRoot = System.getProperty("app.root", System.getProperty("user.dir"));
    File appDir = new File(appRoot, themeDirectory);
    if (appDir.exists() && appDir.isDirectory()) {
      return appDir;
    }

    // 2. 尝试相对于工作目录
    File workingDir = new File(System.getProperty("user.dir"), themeDirectory);
    if (workingDir.exists() && workingDir.isDirectory()) {
      return workingDir;
    }

    // 3. 创建目录如果不存在
    if (!appDir.exists()) {
      LOG.info("Creating theme directory: {}", appDir.getAbsolutePath());
      appDir.mkdirs();
    }

    return appDir;
  }

  /**
   * 加载所有主题
   */
  private void loadAllThemes() {
    File[] themeDirs = themeRootDir.listFiles(File::isDirectory);
    if (themeDirs == null) {
      LOG.warn("No themes found in directory: {}", themeRootDir);
      return;
    }

    for (File themeDir : themeDirs) {
      try {
        Theme theme = loadTheme(themeDir);
        themes.put(theme.getName(), theme);

        // 创建并存储主题的 MustacheFactory
        MustacheFactory factory = createThemeMustacheFactory(theme);
        themeMustacheFactories.put(theme.getName(), factory);

        LOG.info("Loaded theme: {} ({})", theme.getName(), theme.getDisplayName());
      } catch (Exception e) {
        LOG.error("Failed to load theme from: {}", themeDir, e);
      }
    }

    LOG.info("Total themes loaded: {}", themes.size());
  }

  /**
   * 加载单个主题
   */
  private Theme loadTheme(File themeDir) throws IOException {
    // 读取 theme.json
    File themeConfigFile = new File(themeDir, "theme.json");
    if (!themeConfigFile.exists()) {
      throw new IllegalArgumentException("theme.json not found in: " + themeDir);
    }

    ThemeConfig config = objectMapper.readValue(themeConfigFile, ThemeConfig.class);

    // 构建主题对象
    Theme.ThemeBuilder builder = Theme.builder()
        .name(themeDir.getName())
        .displayName(config.getName())
        .version(config.getVersion())
        .description(config.getDescription())
        .author(config.getAuthor())
        .parent(config.getParent())
        .baseDirectory(themeDir)
        .variables(config.getVariables())
        .staticPath(new File(themeDir, "static").getAbsolutePath());

    // 加载布局
    Map<String, Theme.LayoutInfo> layouts = new HashMap<>();
    if (config.getLayouts() != null) {
      for (Map.Entry<String, ThemeConfig.LayoutConfig> entry : config.getLayouts().entrySet()) {
        layouts.put(entry.getKey(), Theme.LayoutInfo.builder()
            .file(entry.getValue().getFile())
            .description(entry.getValue().getDescription())
            .build());
      }
    }
    builder.layouts(layouts);

    // 加载组件
    Map<String, String> components = loadComponents(themeDir);
    builder.components(components);

    Theme theme = builder.build();

    // 为主题创建专用的 MustacheFactory
    themeMustacheFactories.put(theme.getName(), createThemeMustacheFactory(theme));

    return theme;
  }

  /**
   * 加载组件映射
   */
  private Map<String, String> loadComponents(File themeDir) {
    Map<String, String> components = new HashMap<>();
    File componentsDir = new File(themeDir, "components");

    if (componentsDir.exists() && componentsDir.isDirectory()) {
      File[] componentFiles = componentsDir.listFiles((dir, name) -> name.endsWith(".mustache"));
      if (componentFiles != null) {
        for (File file : componentFiles) {
          String name = file.getName().replace(".mustache", "");
          components.put(name, "components/" + file.getName());
        }
      }
    }

    return components;
  }

  /**
   * 为主题创建自定义的 MustacheFactory
   */
  private MustacheFactory createThemeMustacheFactory(Theme theme) {
    return new DefaultMustacheFactory() {
      @Override
      public Reader getReader(String resourceName) {
        LOG.debug("Loading template resource: {}", resourceName);

        // 处理组件引用
        File componentFile = null;

        // 清理资源名称
        String cleanName = resourceName;
        if (cleanName.startsWith("./")) {
          cleanName = cleanName.substring(2);
        }

        // 如果资源名称以 layouts/ 开头，说明是从 layouts 目录中引用的 partial
        // 去掉 layouts/ 前缀，因为实际的 partial 在 components 目录
        if (cleanName.startsWith("layouts/")) {
          cleanName = cleanName.substring(8); // 移除 "layouts/"
        }

        // 去掉可能的扩展名（Mustache 会自动添加）
        if (cleanName.endsWith(".mustache")) {
          cleanName = cleanName.substring(0, cleanName.length() - 9);
        }

        // 尝试多个位置
        List<String> searchPaths = new ArrayList<>();

        // 1. 直接路径（如果已经包含目录）
        searchPaths.add(cleanName);
        if (!cleanName.endsWith(".mustache")) {
          searchPaths.add(cleanName + ".mustache");
        }

        // 只有在没有目录路径时才添加前缀
        if (!cleanName.contains("/")) {
          // 2. components 目录
          searchPaths.add("components/" + cleanName);
          searchPaths.add("components/" + cleanName + ".mustache");

          // 3. partials 目录
          searchPaths.add("partials/" + cleanName);
          searchPaths.add("partials/" + cleanName + ".mustache");

          // 4. 布局目录
          searchPaths.add("layouts/" + cleanName);
          searchPaths.add("layouts/" + cleanName + ".mustache");
        }

        // 尝试所有路径
        for (String path : searchPaths) {
          componentFile = new File(theme.getBaseDirectory(), path);
          if (componentFile.exists() && componentFile.isFile()) {
            try {
              LOG.debug("Found template file: {}", componentFile.getAbsolutePath());
              return new InputStreamReader(new FileInputStream(componentFile), StandardCharsets.UTF_8);
            } catch (Exception e) {
              LOG.error("Failed to read template file: {}", componentFile, e);
            }
          }
        }

        // 如果找不到文件，记录错误并返回空模板
        LOG.warn("Template resource not found: {} in theme: {}", resourceName, theme.getName());
        LOG.debug("Searched paths: {}", searchPaths);

        // 返回一个空模板，避免 NPE
        return new StringReader("");
      }
    };
  }

  /**
   * 准备主题数据
   */
  private Map<String, Object> prepareThemeData(RoutingContext ctx) {
    Map<String, Object> data = new HashMap<>();

    // 确保 activeTheme 不为空
    String currentTheme = activeTheme;
    if (currentTheme == null || currentTheme.trim().isEmpty()) {
      currentTheme = "default";
      LOG.warn("activeTheme is null/empty in prepareThemeData, using default");
    }

    // 添加系统信息
    data.put("systemName", "Direct-LLM-Rask");
    data.put("theme", currentTheme);
    data.put("themeUrl", "/theme/" + currentTheme);

    // 添加用户信息 - 统一处理
    String userId = ctx.get("userId");
    String userEmail = ctx.get("userEmail");
    String userRole = ctx.get("userRole");
    Map<String, Object> currentUserMap = ctx.get("currentUser");
    // LOG.info("currentUserMap: {}", currentUserMap);

    boolean isAuthenticated = userId != null;
    data.put("isAuthenticated", isAuthenticated);

    if (isAuthenticated) {
      // 构建完整的用户信息
      Map<String, Object> userInfo = new HashMap<>();
      if (currentUserMap != null) {
        userInfo.putAll(currentUserMap);
      }

      // 确保基本信息存在
      userInfo.put("id", userId);
      userInfo.put("email", userEmail);
      userInfo.put("role", userRole);

      // 如果没有名称，使用邮箱前缀
      if (!userInfo.containsKey("name") || userInfo.get("name") == null) {
        String displayName = userEmail != null && userEmail.contains("@")
            ? userEmail.substring(0, userEmail.indexOf("@"))
            : "用户";
        userInfo.put("name", displayName);
      }

      data.put("currentUser", userInfo);
      data.put("userId", userId);
      data.put("userEmail", userEmail);
      data.put("userRole", userRole);

      // 添加角色判断
      data.put("isAdmin", "admin".equals(userRole));
      data.put("isManager", "manager".equals(userRole) || "admin".equals(userRole));

      LOG.debug("User authenticated - ID: {}, Email: {}, Role: {}", userId, userEmail, userRole);
    } else {
      data.put("currentUser", null);
      data.put("userId", null);
      data.put("userEmail", null);
      data.put("userRole", null);
      data.put("isAdmin", false);
      data.put("isManager", false);
      LOG.debug("User not authenticated");
    }

    // 添加导航信息
    data.put("currentPath", ctx.request().path());
    data.put("breadcrumbs", generateBreadcrumbs(ctx));

    // 添加请求信息
    data.put("requestId", ctx.get("systemRequestId"));

    // 添加模板数据
    Object templateData = ctx.get("templateData");
    if (templateData instanceof Map) {
      data.putAll((Map<String, Object>) templateData);
    }

    // 合并视图数据
    Map<String, Object> viewData = ctx.get("viewData");
    if (viewData != null) {
      data.putAll(viewData);
    }

    return data;
  }

  /**
   * 生成面包屑导航
   */
  private List<Map<String, String>> generateBreadcrumbs(RoutingContext ctx) {
    List<Map<String, String>> breadcrumbs = new ArrayList<>();

    // 添加首页
    Map<String, String> home = new HashMap<>();
    home.put("title", "首页");
    home.put("url", "/page/");
    breadcrumbs.add(home);

    // 根据路径生成面包屑
    String path = ctx.request().path();
    if (path.startsWith("/page/") && !path.equals("/page/")) {
      String[] parts = path.substring(6).split("/");
      StringBuilder urlBuilder = new StringBuilder("/page");

      for (String part : parts) {
        if (!part.isEmpty()) {
          urlBuilder.append("/").append(part);
          Map<String, String> crumb = new HashMap<>();
          crumb.put("title", capitalize(part));
          crumb.put("url", urlBuilder.toString());
          breadcrumbs.add(crumb);
        }
      }
    }

    return breadcrumbs;
  }

  /**
   * 渲染布局
   */
  private String renderLayout(Theme theme, String layoutName, Map<String, Object> data) throws IOException {
    Theme.LayoutInfo layoutInfo = theme.getLayouts().get(layoutName);
    if (layoutInfo == null) {
      LOG.warn("Layout '{}' not found in theme '{}', using content directly", layoutName, theme.getName());
      return (String) data.get("content");
    }

    // 加载布局模板
    File layoutFile = new File(theme.getBaseDirectory(), layoutInfo.getFile());
    if (!layoutFile.exists()) {
      LOG.error("Layout file not found: {}", layoutFile);
      return (String) data.get("content");
    }

    // 获取主题特定的 MustacheFactory
    MustacheFactory themeMustacheFactory = themeMustacheFactories.get(theme.getName());
    if (themeMustacheFactory == null) {
      LOG.error("MustacheFactory not found for theme: {}", theme.getName());
      return (String) data.get("content");
    }

    // 编译并渲染模板
    try (Reader reader = new InputStreamReader(new FileInputStream(layoutFile), StandardCharsets.UTF_8)) {
      Mustache mustache = themeMustacheFactory.compile(reader, layoutName);
      StringWriter writer = new StringWriter();

      // 直接使用数据，不需要额外的组件作用域
      mustache.execute(writer, data).flush();
      return writer.toString();
    }
  }

  /**
   * 获取当前主题
   */
  public Theme getCurrentTheme() {
    return themes.get(activeTheme);
  }

  /**
   * 获取指定主题
   */
  public Theme getTheme(String name) {
    return themes.get(name);
  }

  /**
   * 获取所有可用主题
   */
  public List<Theme> getAvailableThemes() {
    return new ArrayList<>(themes.values());
  }

  /**
   * 切换主题
   */
  public void switchTheme(String themeName) {
    if (!themes.containsKey(themeName)) {
      throw new IllegalArgumentException("Theme not found: " + themeName);
    }

    this.activeTheme = themeName;
    LOG.info("Switched to theme: {}", themeName);
  }

  /**
   * 重新加载主题
   */
  public void reloadTheme(String themeName) {
    File themeDir = new File(themeRootDir, themeName);
    if (themeDir.exists() && themeDir.isDirectory()) {
      try {
        Theme theme = loadTheme(themeDir);
        themes.put(theme.getName(), theme);

        // 重新创建并存储主题的 MustacheFactory
        MustacheFactory factory = createThemeMustacheFactory(theme);
        themeMustacheFactories.put(theme.getName(), factory);

        LOG.info("Reloaded theme: {}", themeName);
      } catch (Exception e) {
        LOG.error("Failed to reload theme: {}", themeName, e);
      }
    }
  }

  /**
   * 启动文件监控
   */
  private void startThemeWatcher() {
    try {
      watchService = FileSystems.getDefault().newWatchService();

      // 注册主题目录
      themeRootDir.toPath().register(watchService,
          StandardWatchEventKinds.ENTRY_CREATE,
          StandardWatchEventKinds.ENTRY_MODIFY,
          StandardWatchEventKinds.ENTRY_DELETE);

      // 启动监控线程
      Thread watchThread = new Thread(this::watchThemes);
      watchThread.setDaemon(true);
      watchThread.start();

      LOG.info("Theme file watcher started");
    } catch (IOException e) {
      LOG.error("Failed to start theme watcher", e);
    }
  }

  /**
   * 监控主题文件变化
   */
  private void watchThemes() {
    while (!Thread.currentThread().isInterrupted()) {
      try {
        WatchKey key = watchService.take();
        for (WatchEvent<?> event : key.pollEvents()) {
          Path changed = (Path) event.context();
          LOG.debug("Theme file changed: {}", changed);

          // 重新加载受影响的主题
          String themeName = changed.toString().split("[/\\\\]")[0];
          if (themes.containsKey(themeName)) {
            reloadTheme(themeName);
          }
        }
        key.reset();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
  }

  /**
   * 首字母大写
   */
  private String capitalize(String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }
    return str.substring(0, 1).toUpperCase() + str.substring(1);
  }

  /**
   * 使用指定主题渲染内容
   */
  public String renderWithTheme(String themeName, String layoutName, Map<String, Object> data, RoutingContext ctx) {
    LOG.debug("renderWithTheme called - theme: {}, layout: {}", themeName, layoutName);
    data.putAll(prepareThemeData(ctx));
    Theme theme = themes.get(themeName);
    if (theme == null) {
      LOG.warn("Theme not found: {}, using default", themeName);
      theme = themes.get("default");
    }

    if (theme == null) {
      LOG.error("Default theme not found");
      return (String) data.get("content");
    }

    LOG.debug("Theme found: {}, layouts available: {}", theme.getName(), theme.getLayouts().keySet());

    try {
      // 获取布局
      Theme.LayoutInfo layout = theme.getLayouts().get(layoutName);
      if (layout == null) {
        LOG.warn("Layout not found: {} in theme: {}, available layouts: {}",
            layoutName, themeName, theme.getLayouts().keySet());
        return (String) data.get("content");
      }

      LOG.debug("Layout found: {}", layout.getFile());

      // 渲染布局
      return renderLayout(theme, layout, data);

    } catch (Exception e) {
      LOG.error("Failed to render with theme: {}", themeName, e);
      return (String) data.get("content");
    }
  }

  /**
   * 渲染布局
   */
  private String renderLayout(Theme theme, Theme.LayoutInfo layout, Map<String, Object> data) throws Exception {
    // 获取主题的 MustacheFactory
    MustacheFactory factory = themeMustacheFactories.get(theme.getName());
    if (factory == null) {
      LOG.error("MustacheFactory not found for theme: {}", theme.getName());
      return (String) data.get("content");
    }

    // 编译模板
    Mustache mustache = factory.compile(layout.getFile());

    // 渲染
    StringWriter writer = new StringWriter();
    mustache.execute(writer, data).flush();

    return writer.toString();
  }

}