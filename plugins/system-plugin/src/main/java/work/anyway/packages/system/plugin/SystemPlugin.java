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
@Plugin(name = "System Plugin", version = "1.0.0", description = "系统管理插件，提供主页、健康检查等核心功能", icon = "⚙️", mainPagePath = "")
// 声明权限定义
@PermissionDef(code = "system.view", name = "查看系统信息", description = "查看系统状态和信息", defaultRoles = { "admin", "manager" })
@PermissionDef(code = "system.manage", name = "系统管理", description = "管理系统配置和插件", defaultRoles = { "admin" })
// 声明一级菜单
@MenuItem(id = "system", title = "系统管理", icon = "⚙️", order = 100)
@Controller
@RequestMapping("")
@Intercepted({ "NoAuth" })
public class SystemPlugin {

  private static final Logger LOG = LoggerFactory.getLogger(SystemPlugin.class);
  private final MustacheFactory mustacheFactory = new DefaultMustacheFactory();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Autowired
  private PluginRegistry pluginRegistry;

  /**
   * 获取主页
   */
  @GetMapping("/")
  @RenderTemplate("home")
  public void geyHomePage(RoutingContext ctx) {
    // 获取所有插件
    List<PluginInfo> plugins = pluginRegistry.getAllPlugins();

    // 准备插件数据，过滤掉不应该显示的插件
    List<Map<String, Object>> pluginData = new ArrayList<>();
    for (PluginInfo plugin : plugins) {
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
    Map<String, Object> data = new HashMap<>();
    data.put("plugins", pluginData);
    data.put("pluginCount", pluginData.size()); // 只计算显示的插件数量
    data.put("hasPlugins", !pluginData.isEmpty());
    data.put("title", "系统概览");

    // 添加总插件数（包括隐藏的）
    data.put("totalPluginCount", plugins.size());

    // 设置模板数据供主题系统使用
    // 设置数据，框架会自动渲染
    ctx.put("viewData", data);
  }

  @GetMapping("/settings")
  @RenderTemplate("settings")
  public void getSettingsPage(RoutingContext ctx) {
    // 获取用户信息
    Map<String, Object> userInfo = ctx.get("currentUser");
    ctx.put("viewData", userInfo);
  }

  @GetMapping("/profile")
  @RenderTemplate("profile")
  public void getProfilePage(RoutingContext ctx) {
    // 获取用户信息
    Map<String, Object> userInfo = ctx.get("currentUser");
    ctx.put("viewData", userInfo);
  }

  /**
   * 处理插件列表API请求
   */
  @GetMapping("/api/plugins")
  public void getPluginList(RoutingContext ctx) {
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
}