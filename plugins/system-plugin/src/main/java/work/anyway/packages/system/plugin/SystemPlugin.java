package work.anyway.packages.system.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.anyway.interfaces.plugin.Plugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统级插件，提供主页和健康检查等功能
 */
public class SystemPlugin implements Plugin {

  private static final Logger LOG = LoggerFactory.getLogger(SystemPlugin.class);
  private final MustacheFactory mustacheFactory = new DefaultMustacheFactory();
  private final ObjectMapper objectMapper = new ObjectMapper();
  
  // 保存所有已加载插件的引用（使用线程安全的列表）
  private static final List<Plugin> loadedPlugins = Collections.synchronizedList(new ArrayList<>());

  @Override
  public String getName() {
    return "System Plugin";
  }

  @Override
  public String getVersion() {
    return "1.0.0";
  }

  @Override
  public String getDescription() {
    return "系统管理插件，提供主页、健康检查等核心功能";
  }

  @Override
  public String getIcon() {
    return "⚙️";
  }

  @Override
  public void initialize(Router router) {
    // 主页路由
    router.get("/").handler(ctx -> {
      ctx.response()
          .setStatusCode(302)
          .putHeader("Location", "/page/")
          .end();
    });
    router.get("/page/").handler(this::handleHomePage);
    
    // 健康检查端点
    router.get("/health").handler(this::handleHealthCheck);
    
    // API 端点获取插件列表
    router.get("/api/plugins").handler(this::handlePluginList);
    
    LOG.info("System plugin initialized");
  }

  /**
   * 处理主页请求
   */
  private void handleHomePage(RoutingContext ctx) {
    try {
      // 准备插件数据
      List<Map<String, Object>> pluginData = new ArrayList<>();
      for (Plugin plugin : loadedPlugins) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", plugin.getName());
        data.put("version", plugin.getVersion());
        data.put("description", plugin.getDescription());
        data.put("icon", plugin.getIcon());
        
        String mainPage = plugin.getMainPagePath();
        if (mainPage != null) {
          data.put("hasPage", true);
          data.put("pageUrl", mainPage);
        } else {
          data.put("hasPage", false);
        }
        
        pluginData.add(data);
      }

      // 准备模板数据
      Map<String, Object> templateData = new HashMap<>();
      templateData.put("plugins", pluginData);
      templateData.put("pluginCount", loadedPlugins.size());
      templateData.put("hasPlugins", !pluginData.isEmpty());

      // 渲染模板
      String html = renderTemplate("home.mustache", templateData);
      
      ctx.response()
          .putHeader("content-type", "text/html; charset=utf-8")
          .end(html);
    } catch (Exception e) {
      LOG.error("Error rendering home page", e);
      ctx.response().setStatusCode(500).end("Internal Server Error");
    }
  }

  /**
   * 处理健康检查请求
   */
  private void handleHealthCheck(RoutingContext ctx) {
    try {
      JsonObject health = new JsonObject()
          .put("status", "UP")
          .put("timestamp", System.currentTimeMillis())
          .put("plugins", new JsonObject()
              .put("total", loadedPlugins.size())
              .put("loaded", loadedPlugins.stream()
                  .map(Plugin::getName)
                  .toList()));

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(health.encode());
    } catch (Exception e) {
      LOG.error("Error handling health check", e);
      ctx.response()
          .setStatusCode(500)
          .putHeader("content-type", "application/json")
          .end(new JsonObject()
              .put("status", "ERROR")
              .put("error", e.getMessage())
              .encode());
    }
  }

  /**
   * 处理插件列表API请求
   */
  private void handlePluginList(RoutingContext ctx) {
    try {
      List<Map<String, Object>> pluginList = new ArrayList<>();
      for (Plugin plugin : loadedPlugins) {
        Map<String, Object> info = new HashMap<>();
        info.put("name", plugin.getName());
        info.put("version", plugin.getVersion());
        info.put("description", plugin.getDescription());
        info.put("icon", plugin.getIcon());
        info.put("mainPagePath", plugin.getMainPagePath());
        pluginList.add(info);
      }

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(objectMapper.writeValueAsString(pluginList));
    } catch (Exception e) {
      LOG.error("Error handling plugin list", e);
      ctx.response()
          .setStatusCode(500)
          .putHeader("content-type", "application/json")
          .end("{\"error\":\"" + e.getMessage() + "\"}");
    }
  }

  /**
   * 渲染 Mustache 模板
   */
  private String renderTemplate(String templateName, Map<String, Object> data) {
    try (InputStream is = getClass().getResourceAsStream("/system-plugin/templates/" + templateName)) {
      if (is == null) {
        throw new RuntimeException("Template not found: " + templateName);
      }
      
      Mustache mustache = mustacheFactory.compile(new InputStreamReader(is, StandardCharsets.UTF_8), templateName);
      StringWriter writer = new StringWriter();
      mustache.execute(writer, data).flush();
      return writer.toString();
    } catch (Exception e) {
      LOG.error("Error rendering template: " + templateName, e);
      throw new RuntimeException("Template rendering error", e);
    }
  }

  /**
   * 静态方法供 Host 注册已加载的插件
   */
  public static void registerPlugin(Plugin plugin) {
    // 避免注册 SystemPlugin 自己
    if (plugin instanceof SystemPlugin) {
      return;
    }
    
    // 避免重复注册
    boolean exists = loadedPlugins.stream()
        .anyMatch(p -> p.getName().equals(plugin.getName()));
    
    if (!exists) {
      loadedPlugins.add(plugin);
      LOG.info("Registered plugin: {} v{}", plugin.getName(), plugin.getVersion());
    }
  }

  /**
   * 清空已注册的插件（用于重新加载时）
   */
  public static void clearPlugins() {
    loadedPlugins.clear();
  }
} 