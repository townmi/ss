package work.anyway.packages.auth.plugin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.anyway.interfaces.auth.PermissionService;
import work.anyway.interfaces.plugin.Plugin;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class AuthPlugin implements Plugin {

  private static final Logger LOG = LoggerFactory.getLogger(AuthPlugin.class);
  private PermissionService permissionService; // 将由容器自动注入
  private final ObjectMapper objectMapper = new ObjectMapper();

  // 预定义的权限列表
  private static final List<String> AVAILABLE_PERMISSIONS = Arrays.asList(
      "user.create", "user.read", "user.update", "user.delete",
      "admin.access", "admin.manage",
      "system.config", "system.monitor",
      "report.view", "report.export");

  @Override
  public String getName() {
    return "Auth Plugin";
  }

  @Override
  public String getVersion() {
    return "1.0.0";
  }

  @Override
  public String getDescription() {
    return "管理用户权限，控制系统访问权限";
  }

  @Override
  public String getIcon() {
    return "🔐";
  }

  @Override
  public String getMainPagePath() {
    return "/page/auth/";
  }

  @Override
  public void initialize(Router router) {
    LOG.info("Initializing Auth Plugin...");

    // 检查 permissionService 是否已被注入
    if (permissionService == null) {
      LOG.error("PermissionService was not injected!");
      throw new IllegalStateException("PermissionService is required but was not injected");
    }

    // API 端点
    // GET /auth/permissions/:userId - 获取用户权限
    router.get("/auth/permissions/:userId").handler(this::getUserPermissions);

    // POST /auth/permissions/:userId - 授予权限
    router.post("/auth/permissions/:userId").handler(this::grantPermission);

    // DELETE /auth/permissions/:userId/:permission - 撤销权限
    router.delete("/auth/permissions/:userId/:permission").handler(this::revokePermission);

    // GET /auth/check/:userId/:permission - 检查权限
    router.get("/auth/check/:userId/:permission").handler(this::checkPermission);

    // 页面路由
    // GET /page/auth/ - 权限管理主页
    router.get("/page/auth/").handler(this::getIndexPage);

    // GET /page/auth/permissions - 权限管理页面
    router.get("/page/auth/permissions").handler(this::getPermissionsPage);

    // GET /page/auth/user/:userId - 用户权限详情页面
    router.get("/page/auth/user/:userId").handler(this::getUserPermissionsPage);

    LOG.info("Auth Plugin initialized with endpoints:");
    LOG.info("  API: GET/POST /auth/permissions/:userId, DELETE /auth/permissions/:userId/:permission");
    LOG.info("  Pages: GET /page/auth/, GET /page/auth/permissions, GET /page/auth/user/:userId");
  }

  // API 处理方法
  private void getUserPermissions(RoutingContext ctx) {
    try {
      String userId = ctx.pathParam("userId");

      // 获取用户的所有权限
      List<String> permissions = new ArrayList<>();
      for (String permission : AVAILABLE_PERMISSIONS) {
        if (permissionService.hasPermission(userId, permission)) {
          permissions.add(permission);
        }
      }

      JsonObject response = new JsonObject()
          .put("userId", userId)
          .put("permissions", new JsonArray(permissions));

      sendJsonResponse(ctx.response(), response);
    } catch (Exception e) {
      LOG.error("Error getting user permissions", e);
      ctx.response().setStatusCode(500).end("Internal Server Error");
    }
  }

  private void grantPermission(RoutingContext ctx) {
    try {
      String userId = ctx.pathParam("userId");
      JsonObject body = ctx.body().asJsonObject();

      if (body == null || !body.containsKey("permission")) {
        ctx.response().setStatusCode(400).end("Permission is required");
        return;
      }

      String permission = body.getString("permission");
      permissionService.grantPermission(userId, permission);

      JsonObject response = new JsonObject()
          .put("message", "Permission granted successfully")
          .put("userId", userId)
          .put("permission", permission);

      sendJsonResponse(ctx.response(), response);
    } catch (Exception e) {
      LOG.error("Error granting permission", e);
      ctx.response().setStatusCode(500).end("Internal Server Error");
    }
  }

  private void revokePermission(RoutingContext ctx) {
    try {
      String userId = ctx.pathParam("userId");
      String permission = ctx.pathParam("permission");

      permissionService.revokePermission(userId, permission);

      JsonObject response = new JsonObject()
          .put("message", "Permission revoked successfully")
          .put("userId", userId)
          .put("permission", permission);

      sendJsonResponse(ctx.response(), response);
    } catch (Exception e) {
      LOG.error("Error revoking permission", e);
      ctx.response().setStatusCode(500).end("Internal Server Error");
    }
  }

  private void checkPermission(RoutingContext ctx) {
    try {
      String userId = ctx.pathParam("userId");
      String permission = ctx.pathParam("permission");

      boolean hasPermission = permissionService.hasPermission(userId, permission);

      JsonObject response = new JsonObject()
          .put("userId", userId)
          .put("permission", permission)
          .put("hasPermission", hasPermission);

      sendJsonResponse(ctx.response(), response);
    } catch (Exception e) {
      LOG.error("Error checking permission", e);
      ctx.response().setStatusCode(500).end("Internal Server Error");
    }
  }

  // 页面处理方法
  private void getIndexPage(RoutingContext ctx) {
    try {
      LOG.info("AuthPlugin: Loading index.html from classpath");
      String html = readResourceFile("auth-plugin/templates/index.html");
      if (html != null) {
        // 添加一个标识来确认是哪个插件的页面
        html = html.replace("</body>", "<!-- AuthPlugin --></body>");
        ctx.response()
            .putHeader("content-type", "text/html; charset=utf-8")
            .end(html);
      } else {
        ctx.response()
            .setStatusCode(404)
            .end("Template not found");
      }
    } catch (Exception e) {
      LOG.error("Error rendering index page", e);
      ctx.response().setStatusCode(500).end("Internal Server Error");
    }
  }

  private void getPermissionsPage(RoutingContext ctx) {
    try {
      String html = readResourceFile("auth-plugin/templates/permissions.html");
      if (html != null) {
        ctx.response()
            .putHeader("content-type", "text/html; charset=utf-8")
            .end(html);
      } else {
        ctx.response()
            .setStatusCode(404)
            .end("Template not found");
      }
    } catch (Exception e) {
      LOG.error("Error rendering permissions page", e);
      ctx.response().setStatusCode(500).end("Internal Server Error");
    }
  }

  private void getUserPermissionsPage(RoutingContext ctx) {
    try {
      String userId = ctx.pathParam("userId");
      String template = readResourceFile("auth-plugin/templates/user-permissions.html");

      if (template != null) {
        // 替换用户ID
        template = template.replaceAll("\\{\\{userId\\}\\}", userId);

        ctx.response()
            .putHeader("content-type", "text/html; charset=utf-8")
            .end(template);
      } else {
        ctx.response()
            .setStatusCode(404)
            .end("Template not found");
      }
    } catch (Exception e) {
      LOG.error("Error rendering user permissions page", e);
      ctx.response().setStatusCode(500).end("Internal Server Error");
    }
  }

  private void sendJsonResponse(HttpServerResponse response, Object data) {
    try {
      String json = objectMapper.writeValueAsString(data);
      response
          .putHeader("content-type", "application/json")
          .end(json);
    } catch (JsonProcessingException e) {
      LOG.error("Error serializing response", e);
      response.setStatusCode(500).end("Error serializing response");
    }
  }

  private String readResourceFile(String path) {
    // 使用当前类的类加载器，而不是系统类加载器
    try (InputStream is = AuthPlugin.class.getResourceAsStream("/" + path)) {
      if (is == null) {
        LOG.error("Resource not found: " + path);
        return null;
      }
      try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name())) {
        scanner.useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : "";
      }
    } catch (Exception e) {
      LOG.error("Error reading resource: " + path, e);
      return null;
    }
  }
}