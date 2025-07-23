package work.anyway.packages.auth.plugin;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.springframework.beans.factory.annotation.Autowired;
import work.anyway.annotations.*;
import work.anyway.interfaces.auth.PermissionService;
import work.anyway.interfaces.auth.Permission;
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
    try {
      List<Permission> availablePermissions = permissionService.getAllPermissions();
      JsonArray permissionsArray = new JsonArray();
      for (Permission permission : availablePermissions) {
        JsonObject permObj = new JsonObject()
            .put("code", permission.getCode())
            .put("name", permission.getName())
            .put("description", permission.getDescription())
            .put("pluginName", permission.getPluginName())
            .put("isActive", permission.getIsActive());
        permissionsArray.add(permObj);
      }

      JsonObject data = new JsonObject()
          .put("permissions", permissionsArray)
          .put("count", permissionsArray.size());

      sendSuccess(ctx, data);
    } catch (Exception e) {
      LOG.error("Failed to get available permissions", e);
      sendError(ctx, 500, "Failed to retrieve permissions: " + e.getMessage());
    }
  }

  /**
   * 权限管理页面
   * GET /auth/permissions/page
   */
  @GetMapping("/page")
  @RenderTemplate("permissions-management")
  @Intercepted({ "TemplateRendering" })
  public void getPermissionsPage(RoutingContext ctx) {
    try {
      // 获取所有权限
      List<Permission> permissions = permissionService.getAllPermissions();

      // 转换为页面显示需要的格式
      List<Map<String, Object>> permissionList = new ArrayList<>();
      for (Permission permission : permissions) {
        Map<String, Object> permMap = new HashMap<>();
        permMap.put("code", permission.getCode());
        permMap.put("name", permission.getName());
        permMap.put("description", permission.getDescription());
        permMap.put("pluginName", permission.getPluginName());
        permMap.put("isActive", permission.getIsActive());
        permissionList.add(permMap);
      }

      Map<String, Object> data = new HashMap<>();
      data.put("pluginName", PLUGIN_NAME);
      data.put("availablePermissions", permissionList);
      data.put("pageTitle", "权限管理");
      data.put("title", "权限管理");

      // 设置数据，框架自动处理渲染
      ctx.put("viewData", data);

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
  @RenderTemplate("user-permissions")
  @Intercepted({ "TemplateRendering" })
  public void getUserPermissionsPage(RoutingContext ctx) {
    String userId = ctx.pathParam("userId");

    try {
      Set<String> userPermissions = permissionService.getUserPermissions(userId);

      // 获取所有权限
      List<Permission> availablePermissions = permissionService.getAllPermissions();

      // 构建权限状态列表
      List<Map<String, Object>> permissionList = new ArrayList<>();
      for (Permission permission : availablePermissions) {
        Map<String, Object> permItem = new HashMap<>();
        permItem.put("code", permission.getCode());
        permItem.put("name", permission.getName());
        permItem.put("granted", userPermissions.contains(permission.getCode()));
        permItem.put("description", permission.getDescription());
        permItem.put("pluginName", permission.getPluginName());
        permissionList.add(permItem);
      }

      Map<String, Object> data = new HashMap<>();
      data.put("userId", userId);
      data.put("permissions", permissionList);
      data.put("grantedCount", userPermissions.size());
      data.put("totalCount", availablePermissions.size());
      data.put("pageTitle", "用户权限管理");
      data.put("title", "用户权限管理");

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

      // 设置数据，框架自动处理渲染
      ctx.put("viewData", data);

    } catch (Exception e) {
      LOG.error("Failed to render user permissions page", e);
      ctx.response()
          .setStatusCode(500)
          .end("Internal Server Error");
    }
  }
}