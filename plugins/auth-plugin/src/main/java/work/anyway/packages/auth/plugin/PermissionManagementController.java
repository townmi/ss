package work.anyway.packages.auth.plugin;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.springframework.beans.factory.annotation.Autowired;
import work.anyway.annotations.*;
import work.anyway.interfaces.auth.PermissionService;
import work.anyway.interfaces.user.UserService;
import work.anyway.interfaces.user.AccountService;

import java.util.*;

import static work.anyway.packages.auth.plugin.AuthPluginConstants.*;

/**
 * 权限管理控制器
 * 
 * Auth插件的权限管理模块，提供权限分配和管理功能：
 * - 权限查询：查看用户权限、检查权限
 * - 权限分配：授予权限、撤销权限、批量操作
 * - 权限配置：查看可用权限列表
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Controller
@RequestMapping("/auth/permissions")
@Intercepted({ INTERCEPTOR_OPERATION }) // 需要认证和操作日志
public class PermissionManagementController extends BaseAuthController {

  @Autowired
  private PermissionService permissionService;

  @Autowired(required = false)
  private UserService userService;

  @Autowired(required = false)
  private AccountService accountService;

  // 预定义的权限列表
  private static final List<String> AVAILABLE_PERMISSIONS = Arrays.asList(DEFAULT_PERMISSIONS);

  /**
   * 获取用户权限
   * GET /auth/permissions/:userId
   */
  @GetMapping("/:userId")
  public void getUserPermissions(RoutingContext ctx) {
    String userId = ctx.pathParam("userId");
    LOG.debug("Getting permissions for user: {}", userId);

    try {
      Set<String> permissions = permissionService.getUserPermissions(userId);

      JsonObject data = new JsonObject()
          .put("userId", userId)
          .put("permissions", new JsonArray(new ArrayList<>(permissions)))
          .put("count", permissions.size());

      sendSuccess(ctx, data);

    } catch (Exception e) {
      LOG.error("Failed to get permissions for user: {}", userId, e);
      sendError(ctx, 500, "Failed to retrieve permissions: " + e.getMessage());
    }
  }

  /**
   * 授予权限
   * POST /auth/permissions/:userId
   * Body: { "permission": "user.read" }
   */
  @PostMapping("/:userId")
  public void grantPermission(RoutingContext ctx) {
    String userId = ctx.pathParam("userId");

    if (!validateRequestBody(ctx)) {
      return;
    }

    JsonObject body = ctx.getBodyAsJson();
    String permission = body.getString("permission");

    if (permission == null || permission.trim().isEmpty()) {
      sendError(ctx, 400, "Permission is required");
      return;
    }

    LOG.debug("Granting permission '{}' to user: {}", permission, userId);

    try {
      permissionService.grantPermission(userId, permission);
      LOG.info("Permission '{}' granted to user: {} by: {}",
          permission, userId, getCurrentUserId(ctx));

      sendSuccess(ctx, "Permission granted successfully",
          new JsonObject()
              .put("userId", userId)
              .put("permission", permission));

    } catch (Exception e) {
      LOG.error("Failed to grant permission '{}' to user: {}", permission, userId, e);
      sendError(ctx, 500, "Failed to grant permission: " + e.getMessage());
    }
  }

  /**
   * 撤销权限
   * DELETE /auth/permissions/:userId/:permission
   */
  @RequestMapping(value = "/:userId/:permission", method = "DELETE")
  public void revokePermission(RoutingContext ctx) {
    String userId = ctx.pathParam("userId");
    String permission = ctx.pathParam("permission");
    LOG.debug("Revoking permission '{}' from user: {}", permission, userId);

    try {
      permissionService.revokePermission(userId, permission);
      LOG.info("Permission '{}' revoked from user: {} by: {}",
          permission, userId, getCurrentUserId(ctx));

      sendSuccess(ctx, "Permission revoked successfully",
          new JsonObject()
              .put("userId", userId)
              .put("permission", permission));

    } catch (Exception e) {
      LOG.error("Failed to revoke permission '{}' from user: {}", permission, userId, e);
      sendError(ctx, 500, "Failed to revoke permission: " + e.getMessage());
    }
  }

  /**
   * 检查权限
   * GET /auth/permissions/check/:userId/:permission
   */
  @GetMapping("/check/:userId/:permission")
  public void checkPermission(RoutingContext ctx) {
    String userId = ctx.pathParam("userId");
    String permission = ctx.pathParam("permission");

    try {
      boolean hasPermission = permissionService.hasPermission(userId, permission);

      JsonObject data = new JsonObject()
          .put("userId", userId)
          .put("permission", permission)
          .put("hasPermission", hasPermission);

      sendSuccess(ctx, data);

    } catch (Exception e) {
      LOG.error("Failed to check permission '{}' for user: {}", permission, userId, e);
      sendError(ctx, 500, "Failed to check permission: " + e.getMessage());
    }
  }

  /**
   * 批量授予权限
   * POST /auth/permissions/:userId/batch
   * Body: { "permissions": ["user.read", "user.write"] }
   */
  @PostMapping("/:userId/batch")
  public void grantPermissionsBatch(RoutingContext ctx) {
    String userId = ctx.pathParam("userId");

    if (!validateRequestBody(ctx)) {
      return;
    }

    JsonObject body = ctx.getBodyAsJson();
    JsonArray permissionsArray = body.getJsonArray("permissions");

    if (permissionsArray == null || permissionsArray.isEmpty()) {
      sendError(ctx, 400, "Permissions array is required");
      return;
    }

    List<String> permissions = new ArrayList<>();
    for (int i = 0; i < permissionsArray.size(); i++) {
      permissions.add(permissionsArray.getString(i));
    }

    LOG.debug("Granting {} permissions to user: {}", permissions.size(), userId);

    try {
      int successCount = 0;
      List<String> failed = new ArrayList<>();

      for (String permission : permissions) {
        try {
          permissionService.grantPermission(userId, permission);
          successCount++;
        } catch (Exception e) {
          failed.add(permission);
          LOG.warn("Failed to grant permission '{}': {}", permission, e.getMessage());
        }
      }

      LOG.info("Granted {}/{} permissions to user: {} by: {}",
          successCount, permissions.size(), userId, getCurrentUserId(ctx));

      JsonObject data = new JsonObject()
          .put("userId", userId)
          .put("requested", permissions.size())
          .put("granted", successCount)
          .put("failed", new JsonArray(failed));

      if (successCount == permissions.size()) {
        sendSuccess(ctx, "All permissions granted successfully", data);
      } else if (successCount > 0) {
        sendSuccess(ctx, "Partially granted permissions", data);
      } else {
        sendError(ctx, 500, "Failed to grant any permissions");
      }

    } catch (Exception e) {
      LOG.error("Failed to grant permissions to user: {}", userId, e);
      sendError(ctx, 500, "Failed to grant permissions: " + e.getMessage());
    }
  }

  /**
   * 获取所有可用权限
   * GET /auth/permissions/available
   */
  @GetMapping("/available")
  public void getAvailablePermissions(RoutingContext ctx) {
    JsonObject data = new JsonObject()
        .put("permissions", new JsonArray(AVAILABLE_PERMISSIONS))
        .put("count", AVAILABLE_PERMISSIONS.size());

    sendSuccess(ctx, data);
  }

  /**
   * 权限管理页面
   * GET /auth/permissions/page
   */
  @GetMapping("/page")
  public void getPermissionsPage(RoutingContext ctx) {
    try {
      Map<String, Object> data = new HashMap<>();
      data.put("pluginName", PLUGIN_NAME);
      data.put("availablePermissions", AVAILABLE_PERMISSIONS);
      data.put("pageTitle", "权限管理");

      String html = renderTemplate("permissions-management.mustache", data);

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
   * GET /auth/permissions/page/user/:userId
   */
  @GetMapping("/page/user/:userId")
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
        permItem.put("description", getPermissionDescription(permission));
        permissionList.add(permItem);
      }

      Map<String, Object> data = new HashMap<>();
      data.put("userId", userId);
      data.put("permissions", permissionList);
      data.put("grantedCount", userPermissions.size());
      data.put("totalCount", AVAILABLE_PERMISSIONS.size());
      data.put("pageTitle", "用户权限管理");

      // 获取用户信息
      if (userService != null) {
        userService.getUserById(userId).ifPresent(user -> {
          data.put("userName", user.getName());
          // 获取邮箱信息
          if (accountService != null) {
            accountService.getEmailAccount(user.getId()).ifPresent(emailAccount -> {
              data.put("userEmail", emailAccount.getIdentifier());
            });
          }
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

  /**
   * 获取权限描述
   */
  private String getPermissionDescription(String permission) {
    Map<String, String> descriptions = new HashMap<>();
    descriptions.put("user.create", "创建用户");
    descriptions.put("user.read", "查看用户");
    descriptions.put("user.update", "更新用户");
    descriptions.put("user.delete", "删除用户");
    descriptions.put("admin.access", "访问管理功能");
    descriptions.put("admin.manage", "管理系统配置");
    descriptions.put("system.config", "配置系统参数");
    descriptions.put("system.monitor", "监控系统状态");
    descriptions.put("report.view", "查看报表");
    descriptions.put("report.export", "导出报表");

    return descriptions.getOrDefault(permission, permission);
  }
}