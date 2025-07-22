package work.anyway.packages.auth.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import work.anyway.annotations.*;
import work.anyway.interfaces.auth.PermissionService;
import work.anyway.interfaces.user.UserService;

import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 权限管理插件
 */
@Plugin(name = "Auth Plugin", version = "1.0.0", description = "管理用户权限，控制系统访问权限", icon = "🔐", mainPagePath = "/page/auth/")
@Controller
@RequestMapping("/")
public class AuthPlugin {

  private static final Logger LOG = LoggerFactory.getLogger(AuthPlugin.class);

  @Autowired
  private PermissionService permissionService;

  @Autowired(required = false)
  private UserService userService;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final MustacheFactory mustacheFactory = new DefaultMustacheFactory();

  // 预定义的权限列表
  private static final List<String> AVAILABLE_PERMISSIONS = Arrays.asList(
      "user.create", "user.read", "user.update", "user.delete",
      "admin.access", "admin.manage",
      "system.config", "system.monitor",
      "report.view", "report.export");

  // API 端点

  /**
   * 获取用户权限
   */
  @GetMapping("/auth/permissions/:userId")
  public void getUserPermissions(RoutingContext ctx) {
    String userId = ctx.pathParam("userId");
    LOG.debug("Getting permissions for user: {}", userId);

    try {
      Set<String> permissions = permissionService.getUserPermissions(userId);

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("userId", userId)
          .put("permissions", new JsonArray(new ArrayList<>(permissions)));

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());
    } catch (Exception e) {
      LOG.error("Failed to get permissions for user: {}", userId, e);
      sendError(ctx, 500, "Failed to retrieve permissions: " + e.getMessage());
    }
  }

  /**
   * 授予权限
   */
  @PostMapping("/auth/permissions/:userId")
  public void grantPermission(RoutingContext ctx) {
    String userId = ctx.pathParam("userId");
    JsonObject body = ctx.getBodyAsJson();

    if (body == null || !body.containsKey("permission")) {
      sendError(ctx, 400, "Permission is required");
      return;
    }

    String permission = body.getString("permission");
    LOG.debug("Granting permission '{}' to user: {}", permission, userId);

    try {
      permissionService.grantPermission(userId, permission);
      LOG.info("Permission '{}' granted to user: {}", permission, userId);

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("message", "Permission granted successfully");

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());
    } catch (Exception e) {
      LOG.error("Failed to grant permission '{}' to user: {}", permission, userId, e);
      sendError(ctx, 500, "Failed to grant permission: " + e.getMessage());
    }
  }

  /**
   * 撤销权限
   */
  @RequestMapping(value = "/auth/permissions/:userId/:permission", method = "DELETE")
  public void revokePermission(RoutingContext ctx) {
    String userId = ctx.pathParam("userId");
    String permission = ctx.pathParam("permission");
    LOG.debug("Revoking permission '{}' from user: {}", permission, userId);

    try {
      permissionService.revokePermission(userId, permission);
      LOG.info("Permission '{}' revoked from user: {}", permission, userId);

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("message", "Permission revoked successfully");

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());
    } catch (Exception e) {
      LOG.error("Failed to revoke permission '{}' from user: {}", permission, userId, e);
      sendError(ctx, 500, "Failed to revoke permission: " + e.getMessage());
    }
  }

  /**
   * 检查权限
   */
  @GetMapping("/auth/check/:userId/:permission")
  public void checkPermission(RoutingContext ctx) {
    String userId = ctx.pathParam("userId");
    String permission = ctx.pathParam("permission");

    try {
      boolean hasPermission = permissionService.hasPermission(userId, permission);

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("userId", userId)
          .put("permission", permission)
          .put("hasPermission", hasPermission);

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());
    } catch (Exception e) {
      LOG.error("Failed to check permission '{}' for user: {}", permission, userId, e);
      sendError(ctx, 500, "Failed to check permission: " + e.getMessage());
    }
  }

  /**
   * 批量授予权限
   */
  @PostMapping("/auth/permissions/:userId/batch")
  public void grantPermissionsBatch(RoutingContext ctx) {
    String userId = ctx.pathParam("userId");
    JsonObject body = ctx.getBodyAsJson();

    if (body == null || !body.containsKey("permissions")) {
      sendError(ctx, 400, "Permissions array is required");
      return;
    }

    JsonArray permissionsArray = body.getJsonArray("permissions");
    List<String> permissions = new ArrayList<>();
    for (int i = 0; i < permissionsArray.size(); i++) {
      permissions.add(permissionsArray.getString(i));
    }

    LOG.debug("Granting {} permissions to user: {}", permissions.size(), userId);

    try {
      for (String permission : permissions) {
        permissionService.grantPermission(userId, permission);
      }
      LOG.info("Granted {} permissions to user: {}", permissions.size(), userId);

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("message", String.format("Granted %d permissions successfully", permissions.size()));

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());
    } catch (Exception e) {
      LOG.error("Failed to grant permissions to user: {}", userId, e);
      sendError(ctx, 500, "Failed to grant permissions: " + e.getMessage());
    }
  }

  /**
   * 获取所有可用权限
   */
  @GetMapping("/auth/permissions/available")
  public void getAvailablePermissions(RoutingContext ctx) {
    JsonObject response = new JsonObject()
        .put("success", true)
        .put("permissions", new JsonArray(AVAILABLE_PERMISSIONS));

    ctx.response()
        .putHeader("content-type", "application/json")
        .end(response.encode());
  }

  // 页面路由

  /**
   * 权限管理主页
   */
  @GetMapping("/page/auth/")
  public void getIndexPage(RoutingContext ctx) {
    try {
      Map<String, Object> data = new HashMap<>();
      data.put("pluginName", "Auth Plugin");
      data.put("pluginVersion", "1.0.0");
      data.put("availablePermissions", AVAILABLE_PERMISSIONS);

      String html = renderTemplate("index.mustache", data);

      ctx.response()
          .putHeader("content-type", "text/html; charset=utf-8")
          .end(html);
    } catch (Exception e) {
      LOG.error("Failed to render index page", e);
      ctx.response()
          .setStatusCode(500)
          .end("Internal Server Error");
    }
  }

  /**
   * 权限列表页面
   */
  @GetMapping("/page/auth/permissions")
  public void getPermissionsPage(RoutingContext ctx) {
    try {
      Map<String, Object> data = new HashMap<>();
      data.put("availablePermissions", AVAILABLE_PERMISSIONS);

      String html = renderTemplate("permissions.mustache", data);

      ctx.response()
          .putHeader("content-type", "text/html; charset=utf-8")
          .end(html);
    } catch (Exception e) {
      LOG.error("Failed to render permissions page", e);
      ctx.response()
          .setStatusCode(500)
          .end("Internal Server Error");
    }
  }

  /**
   * 用户权限管理页面
   */
  @GetMapping("/page/auth/user/:userId")
  public void getUserPermissionsPage(RoutingContext ctx) {
    String userId = ctx.pathParam("userId");

    try {
      Set<String> userPermissions = permissionService.getUserPermissions(userId);

      // 构建权限状态列表
      List<Map<String, Object>> permissionList = new ArrayList<>();
      for (String permission : AVAILABLE_PERMISSIONS) {
        Map<String, Object> permItem = new HashMap<>();
        permItem.put("name", permission);
        permItem.put("granted", userPermissions.contains(permission));
        permissionList.add(permItem);
      }

      Map<String, Object> data = new HashMap<>();
      data.put("userId", userId);
      data.put("permissions", permissionList);
      data.put("grantedCount", userPermissions.size());
      data.put("totalCount", AVAILABLE_PERMISSIONS.size());

      // 如果有 UserService，获取用户信息
      if (userService != null) {
        userService.getUserById(userId).ifPresent(user -> {
          data.put("userName", user.get("name"));
          data.put("userEmail", user.get("email"));
        });
      }

      String html = renderTemplate("user-permissions.mustache", data);

      ctx.response()
          .putHeader("content-type", "text/html; charset=utf-8")
          .end(html);
    } catch (Exception e) {
      LOG.error("Failed to render user permissions page", e);
      ctx.response()
          .setStatusCode(500)
          .end("Internal Server Error");
    }
  }

  // 辅助方法

  private void sendError(RoutingContext ctx, int statusCode, String message) {
    JsonObject error = new JsonObject()
        .put("success", false)
        .put("error", message);

    ctx.response()
        .setStatusCode(statusCode)
        .putHeader("content-type", "application/json")
        .end(error.encode());
  }

  private String renderTemplate(String templateName, Map<String, Object> data) {
    try (InputStream is = getClass().getResourceAsStream("/auth-plugin/templates/" + templateName)) {
      if (is == null) {
        throw new RuntimeException("Template not found: " + templateName);
      }

      Mustache mustache = mustacheFactory.compile(
          new java.io.InputStreamReader(is, StandardCharsets.UTF_8),
          templateName);

      StringWriter writer = new StringWriter();
      mustache.execute(writer, data).flush();
      return writer.toString();
    } catch (Exception e) {
      LOG.error("Error rendering template: " + templateName, e);
      throw new RuntimeException("Template rendering error", e);
    }
  }
}