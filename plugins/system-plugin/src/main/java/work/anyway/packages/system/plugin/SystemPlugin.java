package work.anyway.packages.system.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import work.anyway.annotations.*;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统级插件，提供主页和健康检查等功能
 */
@Plugin(name = "System Plugin", version = "1.0.0", description = "系统管理插件，提供主页、健康检查等核心功能", icon = "⚙️", mainPagePath = "/page/")
@Controller
@RequestMapping("/")
public class SystemPlugin {

  private static final Logger LOG = LoggerFactory.getLogger(SystemPlugin.class);
  private final MustacheFactory mustacheFactory = new DefaultMustacheFactory();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  private PluginRegistry pluginRegistry;

  /**
   * 重定向到主页
   */
  @GetMapping("/")
  public void redirectToHome(RoutingContext ctx) {
    ctx.response()
        .setStatusCode(302)
        .putHeader("Location", "/page/")
        .end();
  }

  /**
   * 判断插件是否应该在主页显示
   * 可以通过配置或其他逻辑来决定哪些插件应该隐藏
   */
  private boolean shouldShowPlugin(PluginInfo plugin) {
    // 系统插件默认隐藏
    if ("System Plugin".equals(plugin.getName())) {
      return false;
    }

    // 未来可以添加更多的过滤逻辑，比如：
    // - 根据配置文件决定是否显示某些插件
    // - 根据插件的某些属性（如标签）决定是否显示
    // - 根据用户权限决定是否显示某些插件

    return true;
  }

  /**
   * 处理主页请求
   */
  @GetMapping("/page/")
  public void handleHomePage(RoutingContext ctx) {
    try {
      // 获取所有插件
      List<PluginInfo> plugins = pluginRegistry.getAllPlugins();

      // 准备插件数据，过滤掉不应该显示的插件
      List<Map<String, Object>> pluginData = new ArrayList<>();
      for (PluginInfo plugin : plugins) {
        // 使用 shouldShowPlugin 方法判断是否显示
        if (!shouldShowPlugin(plugin)) {
          continue;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("name", plugin.getName());
        data.put("version", plugin.getVersion());
        data.put("description", plugin.getDescription());
        data.put("icon", plugin.getIcon());

        String mainPage = plugin.getMainPagePath();
        if (mainPage != null && !mainPage.isEmpty()) {
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
      templateData.put("pluginCount", pluginData.size()); // 只计算显示的插件数量
      templateData.put("hasPlugins", !pluginData.isEmpty());

      // 添加总插件数（包括隐藏的）
      templateData.put("totalPluginCount", plugins.size());

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
  @GetMapping("/health")
  public void handleHealthCheck(RoutingContext ctx) {
    try {
      List<PluginInfo> plugins = pluginRegistry.getAllPlugins();

      JsonObject health = new JsonObject()
          .put("status", "UP")
          .put("timestamp", System.currentTimeMillis())
          .put("plugins", new JsonObject()
              .put("total", plugins.size())
              .put("loaded", plugins.stream()
                  .map(PluginInfo::getName)
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
  @GetMapping("/api/plugins")
  public void handlePluginList(RoutingContext ctx) {
    try {
      List<PluginInfo> plugins = pluginRegistry.getAllPlugins();

      List<Map<String, Object>> pluginList = new ArrayList<>();
      for (PluginInfo plugin : plugins) {
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
}