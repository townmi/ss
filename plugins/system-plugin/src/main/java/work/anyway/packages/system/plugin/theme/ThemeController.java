package work.anyway.packages.system.plugin.theme;

import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import work.anyway.annotations.*;
import work.anyway.interfaces.system.Theme;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 主题控制器
 * 提供主题管理界面和资源服务
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Controller
@RequestMapping("/")
public class ThemeController {

  private static final Logger LOG = LoggerFactory.getLogger(ThemeController.class);

  @Autowired
  private ThemeManager themeManager;

  /**
   * 主题管理页面
   */
  @GetMapping("/page/system/theme")
  @MenuItem(title = "主题管理", parentId = "system", order = 200, permissions = { "system.manage" })
  @Intercepted({ "SystemRequestLog" })
  public void themeManagementPage(RoutingContext ctx) {
    try {
      List<Theme> themes = themeManager.getAvailableThemes();
      Theme currentTheme = themeManager.getCurrentTheme();

      Map<String, Object> data = new HashMap<>();
      data.put("title", "主题管理");
      data.put("themes", themes.stream().map(this::themeToMap).collect(Collectors.toList()));
      data.put("currentTheme", currentTheme != null ? currentTheme.getName() : "default");
      data.put("themeCount", themes.size());

      // 设置模板数据供主题系统使用
      ctx.put("templateData", data);
      ctx.put("_layout", "base");

      // 渲染内容（主题系统会自动应用布局）
      String content = renderThemeManagementContent(data);

      // 设置响应头但不结束响应，让拦截器处理
      ctx.response()
          .putHeader("content-type", "text/html; charset=utf-8");

      // 如果没有主题处理器，直接发送响应
      if (ctx.get("_theme_processor_available") == null) {
        ctx.response().end(content);
      }

    } catch (Exception e) {
      LOG.error("Error rendering theme management page", e);
      ctx.fail(500, e);
    }
  }

  /**
   * 获取所有主题信息 API
   */
  @GetMapping("/api/system/themes")
  @Intercepted({ "SimpleAuth" })
  public void getThemes(RoutingContext ctx) {
    try {
      List<Theme> themes = themeManager.getAvailableThemes();
      Theme currentTheme = themeManager.getCurrentTheme();

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("themes", Json.encode(themes.stream().map(this::themeToMap).collect(Collectors.toList())))
          .put("currentTheme", currentTheme != null ? currentTheme.getName() : "default");

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());

    } catch (Exception e) {
      LOG.error("Error getting themes", e);
      ctx.fail(500, e);
    }
  }

  /**
   * 切换主题 API
   */
  @PostMapping("/api/system/theme/switch")
  @Intercepted({ "SimpleAuth", "OperationLog" })
  @RequirePermission("system.manage")
  public void switchTheme(RoutingContext ctx) {
    try {
      JsonObject body = ctx.getBodyAsJson();
      String themeName = body.getString("theme");

      if (themeName == null || themeName.isEmpty()) {
        ctx.response()
            .setStatusCode(400)
            .end(new JsonObject()
                .put("success", false)
                .put("message", "Theme name is required")
                .encode());
        return;
      }

      themeManager.switchTheme(themeName);

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(new JsonObject()
              .put("success", true)
              .put("message", "Theme switched successfully")
              .put("theme", themeName)
              .encode());

    } catch (IllegalArgumentException e) {
      ctx.response()
          .setStatusCode(400)
          .end(new JsonObject()
              .put("success", false)
              .put("message", e.getMessage())
              .encode());
    } catch (Exception e) {
      LOG.error("Error switching theme", e);
      ctx.fail(500, e);
    }
  }

  /**
   * 重新加载主题 API
   */
  @PostMapping("/api/system/theme/reload")
  @Intercepted({ "SimpleAuth", "OperationLog" })
  @RequirePermission("system.manage")
  public void reloadTheme(RoutingContext ctx) {
    try {
      JsonObject body = ctx.getBodyAsJson();
      String themeName = body.getString("theme");

      if (themeName != null && !themeName.isEmpty()) {
        themeManager.reloadTheme(themeName);
      } else {
        // 重新加载所有主题
        themeManager.getAvailableThemes().forEach(theme -> themeManager.reloadTheme(theme.getName()));
      }

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(new JsonObject()
              .put("success", true)
              .put("message", "Theme(s) reloaded successfully")
              .encode());

    } catch (Exception e) {
      LOG.error("Error reloading theme", e);
      ctx.fail(500, e);
    }
  }

  /**
   * 服务主题静态资源
   */
  @GetMapping("/theme/:theme/static/*")
  public void serveStaticResource(RoutingContext ctx) {
    String themeName = ctx.pathParam("theme");
    String resourcePath = ctx.request().path()
        .substring(("/theme/" + themeName + "/static/").length());

    Theme theme = themeManager.getTheme(themeName);
    if (theme == null) {
      ctx.fail(404);
      return;
    }

    File resourceFile = new File(theme.getStaticPath(), resourcePath);

    // 安全检查：确保文件在主题目录内
    if (!isInThemeDirectory(resourceFile, theme)) {
      ctx.fail(403);
      return;
    }

    if (resourceFile.exists() && resourceFile.isFile()) {
      try {
        // 设置合适的 Content-Type
        String contentType = getContentType(resourcePath);

        // 读取文件内容
        byte[] content = Files.readAllBytes(resourceFile.toPath());

        ctx.response()
            .putHeader("Content-Type", contentType)
            .putHeader("Cache-Control", "public, max-age=3600")
            .end(io.vertx.core.buffer.Buffer.buffer(content));

      } catch (Exception e) {
        LOG.error("Error serving static resource: {}", resourcePath, e);
        ctx.fail(500, e);
      }
    } else {
      ctx.fail(404);
    }
  }

  /**
   * 动态生成主题 CSS
   */
  @GetMapping("/theme/:theme/theme.css")
  public void generateThemeCss(RoutingContext ctx) {
    String themeName = ctx.pathParam("theme");
    Theme theme = themeManager.getTheme(themeName);

    if (theme == null) {
      ctx.fail(404);
      return;
    }

    try {
      // 生成 CSS 变量
      StringBuilder css = new StringBuilder();
      css.append(":root {\n");

      if (theme.getVariables() != null) {
        theme.getVariables().forEach((key, value) -> {
          css.append("  --").append(key).append(": ").append(value).append(";\n");
        });
      }

      css.append("}\n");

      ctx.response()
          .putHeader("Content-Type", "text/css; charset=utf-8")
          .putHeader("Cache-Control", "public, max-age=3600")
          .end(css.toString());

    } catch (Exception e) {
      LOG.error("Error generating theme CSS", e);
      ctx.fail(500, e);
    }
  }

  /**
   * 将主题对象转换为 Map
   */
  private Map<String, Object> themeToMap(Theme theme) {
    Map<String, Object> map = new HashMap<>();
    map.put("name", theme.getName());
    map.put("displayName", theme.getDisplayName());
    map.put("version", theme.getVersion());
    map.put("description", theme.getDescription());
    map.put("author", theme.getAuthor());
    map.put("hasParent", theme.getParent() != null);
    map.put("parent", theme.getParent());
    return map;
  }

  /**
   * 检查文件是否在主题目录内
   */
  private boolean isInThemeDirectory(File file, Theme theme) {
    try {
      String filePath = file.getCanonicalPath();
      String themePath = new File(theme.getStaticPath()).getCanonicalPath();
      return filePath.startsWith(themePath);
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * 获取文件的 Content-Type
   */
  private String getContentType(String path) {
    if (path.endsWith(".css")) {
      return "text/css; charset=utf-8";
    } else if (path.endsWith(".js")) {
      return "application/javascript; charset=utf-8";
    } else if (path.endsWith(".png")) {
      return "image/png";
    } else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
      return "image/jpeg";
    } else if (path.endsWith(".gif")) {
      return "image/gif";
    } else if (path.endsWith(".svg")) {
      return "image/svg+xml";
    } else if (path.endsWith(".woff")) {
      return "font/woff";
    } else if (path.endsWith(".woff2")) {
      return "font/woff2";
    } else if (path.endsWith(".ttf")) {
      return "font/ttf";
    } else if (path.endsWith(".eot")) {
      return "application/vnd.ms-fontobject";
    } else {
      return "application/octet-stream";
    }
  }

  /**
   * 渲染主题管理内容
   */
  private String renderThemeManagementContent(Map<String, Object> data) {
    // 简单的内容模板，实际的样式由主题提供
    StringBuilder html = new StringBuilder();

    html.append("<div class=\"theme-management\">\n");
    html.append("  <h1>主题管理</h1>\n");
    html.append("  <p>当前主题：<strong>").append(data.get("currentTheme")).append("</strong></p>\n");
    html.append("  <div class=\"theme-list\">\n");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> themes = (List<Map<String, Object>>) data.get("themes");
    for (Map<String, Object> theme : themes) {
      html.append("    <div class=\"theme-item\">\n");
      html.append("      <h3>").append(theme.get("displayName")).append("</h3>\n");
      html.append("      <p>").append(theme.get("description")).append("</p>\n");
      html.append("      <p>版本：").append(theme.get("version")).append("</p>\n");
      html.append("      <p>作者：").append(theme.get("author")).append("</p>\n");

      if (!theme.get("name").equals(data.get("currentTheme"))) {
        html.append("      <button onclick=\"switchTheme('").append(theme.get("name")).append("')\">切换到此主题</button>\n");
      }

      html.append("    </div>\n");
    }

    html.append("  </div>\n");
    html.append("</div>\n");

    // 添加简单的 JavaScript
    html.append("<script>\n");
    html.append("function switchTheme(themeName) {\n");
    html.append("  fetch('/api/system/theme/switch', {\n");
    html.append("    method: 'POST',\n");
    html.append("    headers: { 'Content-Type': 'application/json' },\n");
    html.append("    body: JSON.stringify({ theme: themeName })\n");
    html.append("  }).then(response => response.json())\n");
    html.append("    .then(data => {\n");
    html.append("      if (data.success) {\n");
    html.append("        alert('主题切换成功！');\n");
    html.append("        location.reload();\n");
    html.append("      } else {\n");
    html.append("        alert('主题切换失败：' + data.message);\n");
    html.append("      }\n");
    html.append("    });\n");
    html.append("}\n");
    html.append("</script>\n");

    return html.toString();
  }
}