package work.anyway.packages.user.plugin;

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
import work.anyway.interfaces.user.User;
import work.anyway.interfaces.user.UserService;
import work.anyway.interfaces.user.AccountService;
import work.anyway.interfaces.user.AccountType;
import work.anyway.interfaces.user.UserAccount;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 用户管理插件
 */
@Plugin(name = "User Plugin", version = "1.0.0", description = "管理系统用户，包括创建、查看、编辑用户信息", icon = "👤", mainPagePath = "/page/users/")
// 声明权限定义
@PermissionDef(code = "user.view", name = "查看用户", description = "查看用户列表和详情", defaultRoles = { "admin", "manager" })
@PermissionDef(code = "user.create", name = "创建用户", description = "创建新用户", defaultRoles = { "admin" })
@PermissionDef(code = "user.edit", name = "编辑用户", description = "编辑用户信息", defaultRoles = { "admin" })
@PermissionDef(code = "user.delete", name = "删除用户", description = "删除用户", defaultRoles = { "admin" })
// 声明一级菜单
@MenuItem(id = "users", title = "用户管理", icon = "👤", order = 20)
@Controller
@RequestMapping("/")
@Intercepted({ "SystemRequestLog" }) // 类级别：所有方法都使用系统请求日志拦截器
public class UserPlugin {

  private static final Logger LOG = LoggerFactory.getLogger(UserPlugin.class);

  @Autowired
  private UserService userService;

  @Autowired(required = false)
  private AccountService accountService;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final MustacheFactory mustacheFactory = new DefaultMustacheFactory();

  // API 端点

  /**
   * 获取所有用户 - 需要认证
   */
  @GetMapping("/api/users")
  @Intercepted({ "SimpleAuth", "OperationLog" }) // 方法级别：需要认证和操作日志记录
  @RequirePermission("user.view")
  public void getAllUsers(RoutingContext ctx) {
    try {
      List<User> users = userService.getAllUsers();
      LOG.debug("Retrieved {} users", users.size());

      // 转换为Map格式以保持API兼容性
      List<Map<String, Object>> userMaps = users.stream()
          .map(this::convertUserToMap)
          .toList();

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("data", userMaps)
          .put("total", userMaps.size());

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());
    } catch (Exception e) {
      LOG.error("Failed to get all users", e);
      sendError(ctx, 500, "Failed to retrieve users: " + e.getMessage());
    }
  }

  /**
   * 根据ID获取用户 - 需要认证
   */
  @GetMapping("/api/users/:id")
  @Intercepted({ "SimpleAuth", "OperationLog" })
  @RequirePermission("user.view")
  public void getUserById(RoutingContext ctx) {
    String userId = ctx.pathParam("id");
    LOG.debug("Getting user by ID: {}", userId);

    try {
      Optional<User> userOpt = userService.getUserById(userId);

      if (userOpt.isPresent()) {
        Map<String, Object> userMap = convertUserToMap(userOpt.get());
        JsonObject response = new JsonObject()
            .put("success", true)
            .put("data", userMap);

        ctx.response()
            .putHeader("content-type", "application/json")
            .end(response.encode());
      } else {
        LOG.warn("User not found: {}", userId);
        sendError(ctx, 404, "User not found");
      }
    } catch (Exception e) {
      LOG.error("Failed to get user by ID: {}", userId, e);
      sendError(ctx, 500, "Failed to retrieve user: " + e.getMessage());
    }
  }

  /**
   * 创建用户 - 需要认证
   */
  @PostMapping("/api/users")
  @Intercepted({ "SimpleAuth", "OperationLog" })
  @RequirePermission("user.create")
  public void createUser(RoutingContext ctx) {
    JsonObject body = ctx.getBodyAsJson();
    LOG.debug("Creating user with data: {}", body);

    if (body == null || body.isEmpty()) {
      sendError(ctx, 400, "Request body is required");
      return;
    }

    // 验证必填字段
    if (!body.containsKey("name") || body.getString("name").trim().isEmpty()) {
      sendError(ctx, 400, "Name is required");
      return;
    }

    if (!body.containsKey("email") || body.getString("email").trim().isEmpty()) {
      sendError(ctx, 400, "Email is required");
      return;
    }

    try {
      // 1. 创建User实体
      User user = new User();
      user.setName(body.getString("name"));
      user.setPhone(body.getString("phone"));
      user.setDepartment(body.getString("department"));
      user.setRole(body.getString("role", "user"));
      user.setStatus(body.getString("status", "active"));

      User createdUser = userService.createUser(user);
      LOG.info("User created successfully: {}", createdUser.getId());

      // 注意：UserPlugin只负责用户基本信息管理
      // 如果需要创建带认证功能的用户，请使用AuthPlugin的注册接口
      // 这里只创建用户基本信息，不处理密码和认证相关逻辑

      Map<String, Object> userMap = convertUserToMap(createdUser);
      JsonObject response = new JsonObject()
          .put("success", true)
          .put("data", userMap)
          .put("message", "User created successfully");

      ctx.response()
          .setStatusCode(201)
          .putHeader("content-type", "application/json")
          .end(response.encode());
    } catch (Exception e) {
      LOG.error("Failed to create user", e);
      if (e.getMessage() != null && e.getMessage().contains("already exists")) {
        sendError(ctx, 409, e.getMessage());
      } else {
        sendError(ctx, 500, "Failed to create user: " + e.getMessage());
      }
    }
  }

  /**
   * 更新用户
   */
  @RequestMapping(value = "/users/:id", method = "PUT")
  @Intercepted({ "SimpleAuth", "OperationLog" })
  @RequirePermission("user.edit")
  public void updateUser(RoutingContext ctx) {
    String userId = ctx.pathParam("id");
    JsonObject body = ctx.getBodyAsJson();
    LOG.debug("Updating user {} with data: {}", userId, body);

    if (body == null || body.isEmpty()) {
      sendError(ctx, 400, "Request body is required");
      return;
    }

    try {
      // 构建User实体进行更新
      User userUpdate = new User();
      if (body.containsKey("name"))
        userUpdate.setName(body.getString("name"));
      if (body.containsKey("phone"))
        userUpdate.setPhone(body.getString("phone"));
      if (body.containsKey("department"))
        userUpdate.setDepartment(body.getString("department"));
      if (body.containsKey("role"))
        userUpdate.setRole(body.getString("role"));
      if (body.containsKey("status"))
        userUpdate.setStatus(body.getString("status"));

      boolean updated = userService.updateUser(userId, userUpdate);

      if (updated) {
        LOG.info("User updated successfully: {}", userId);

        // 更新邮箱账户（如果提供）
        if (body.containsKey("email") && accountService != null) {
          String newEmail = body.getString("email");
          accountService.getEmailAccount(userId).ifPresent(emailAccount -> {
            emailAccount.setIdentifier(newEmail);
            accountService.updateAccount(emailAccount.getId(), emailAccount);
          });
        }

        // 获取更新后的用户信息
        Optional<User> updatedUser = userService.getUserById(userId);
        Map<String, Object> userMap = updatedUser.map(this::convertUserToMap).orElse(null);

        JsonObject response = new JsonObject()
            .put("success", true)
            .put("data", userMap)
            .put("message", "User updated successfully");

        ctx.response()
            .putHeader("content-type", "application/json")
            .end(response.encode());
      } else {
        LOG.warn("User not found for update: {}", userId);
        sendError(ctx, 404, "User not found");
      }
    } catch (Exception e) {
      LOG.error("Failed to update user: {}", userId, e);
      sendError(ctx, 500, "Failed to update user: " + e.getMessage());
    }
  }

  /**
   * 删除用户
   */
  @RequestMapping(value = "/users/:id", method = "DELETE")
  @Intercepted({ "SimpleAuth", "OperationLog" })
  @RequirePermission("user.delete")
  public void deleteUser(RoutingContext ctx) {
    String userId = ctx.pathParam("id");
    LOG.debug("Deleting user: {}", userId);

    try {
      boolean deleted = userService.deleteUser(userId);

      if (deleted) {
        LOG.info("User deleted successfully: {}", userId);

        JsonObject response = new JsonObject()
            .put("success", true)
            .put("message", "User deleted successfully");

        ctx.response()
            .putHeader("content-type", "application/json")
            .end(response.encode());
      } else {
        LOG.warn("User not found for deletion: {}", userId);
        sendError(ctx, 404, "User not found");
      }
    } catch (Exception e) {
      LOG.error("Failed to delete user: {}", userId, e);
      sendError(ctx, 500, "Failed to delete user: " + e.getMessage());
    }
  }

  /**
   * 根据邮箱查找用户
   */
  @GetMapping("/users/email/:email")
  public void getUserByEmail(RoutingContext ctx) {
    String email = ctx.pathParam("email");
    LOG.debug("Getting user by email: {}", email);

    try {
      Optional<User> userOpt = userService.findUserByEmail(email);

      if (userOpt.isPresent()) {
        Map<String, Object> userMap = convertUserToMap(userOpt.get());
        JsonObject response = new JsonObject()
            .put("success", true)
            .put("data", userMap);

        ctx.response()
            .putHeader("content-type", "application/json")
            .end(response.encode());
      } else {
        LOG.warn("User not found with email: {}", email);
        sendError(ctx, 404, "User not found");
      }
    } catch (Exception e) {
      LOG.error("Failed to get user by email: {}", email, e);
      sendError(ctx, 500, "Failed to retrieve user: " + e.getMessage());
    }
  }

  // 页面路由

  /**
   * 用户管理首页
   */
  @GetMapping("/page/users/")
  @MenuItem(title = "用户概览", parentId = "users", order = 1, permissions = { "user.view" })
  public void getUserHomePage(RoutingContext ctx) {
    LOG.info("Rendering user management homepage...");
    try {
      // 获取用户统计数据
      List<User> allUsers = userService.getAllUsers();

      // 计算统计信息
      long totalUsers = allUsers.size();
      long activeUsers = allUsers.stream()
          .filter(User::isActive)
          .count();
      long adminUsers = allUsers.stream()
          .filter(user -> "admin".equals(user.getRole()))
          .count();

      // 计算今日新增用户（简化实现，实际应该根据创建时间计算）
      long newUsersToday = 0; // TODO: 实现基于时间的统计

      Map<String, Object> data = new HashMap<>();
      data.put("pluginName", "User Plugin");
      data.put("pluginVersion", "1.0.0");
      data.put("userCount", totalUsers);
      data.put("activeUserCount", activeUsers);
      data.put("adminUserCount", adminUsers);
      data.put("newUsersToday", newUsersToday);

      LOG.info("Rendering homepage with stats: total={}, active={}, admin={}",
          totalUsers, activeUsers, adminUsers);
      String html = renderTemplate("index.mustache", data);
      LOG.info("Homepage template rendered successfully, HTML length: {}", html.length());

      ctx.response()
          .putHeader("content-type", "text/html; charset=utf-8")
          .end(html);

      LOG.info("User homepage response sent successfully");
    } catch (Exception e) {
      LOG.error("Failed to render user homepage", e);
      ctx.response()
          .setStatusCode(500)
          .putHeader("content-type", "text/html; charset=utf-8")
          .end("<html><body><h1>Internal Server Error</h1><p>" + e.getMessage() + "</p></body></html>");
    }
  }

  /**
   * 用户列表页面 - 必须在 /:id 路由之前定义
   */
  @GetMapping("/page/users/list")
  @MenuItem(title = "用户列表", parentId = "users", order = 2, permissions = { "user.view" })
  public void getUsersPage(RoutingContext ctx) {
    LOG.info("Rendering users page...");
    try {
      LOG.info("Getting all users from service...");
      List<User> users = userService.getAllUsers();
      LOG.info("Retrieved {} users from service", users.size());

      // 转换为Map格式供模板使用
      List<Map<String, Object>> userMaps = users.stream()
          .map(this::convertUserToMap)
          .toList();

      Map<String, Object> data = new HashMap<>();
      data.put("users", userMaps);
      data.put("userCount", userMaps.size());
      data.put("hasUsers", !userMaps.isEmpty());

      LOG.info("Rendering template with data: userCount={}, hasUsers={}", users.size(), !users.isEmpty());
      String html = renderTemplate("users.mustache", data);
      LOG.info("Template rendered successfully, HTML length: {}", html.length());

      ctx.response()
          .putHeader("content-type", "text/html; charset=utf-8")
          .end(html);

      LOG.info("Users page response sent successfully");
    } catch (Exception e) {
      LOG.error("Failed to render users page", e);
      ctx.response()
          .setStatusCode(500)
          .putHeader("content-type", "text/html; charset=utf-8")
          .end("<html><body><h1>Internal Server Error</h1><p>" + e.getMessage() + "</p></body></html>");
    }
  }

  /**
   * 创建用户页面 - 必须在 /:id 路由之前定义
   */
  @GetMapping("/page/users/create")
  @MenuItem(title = "创建用户", parentId = "users", order = 3, permissions = { "user.create" })
  public void getCreateUserPage(RoutingContext ctx) {
    try {
      Map<String, Object> data = new HashMap<>();
      data.put("action", "create");
      data.put("submitUrl", "/users");
      data.put("method", "POST");

      String html = renderTemplate("create-user.mustache", data);

      ctx.response()
          .putHeader("content-type", "text/html; charset=utf-8")
          .end(html);
    } catch (Exception e) {
      LOG.error("Failed to render create user page", e);
      ctx.response()
          .setStatusCode(500)
          .end("Internal Server Error");
    }
  }

  /**
   * 编辑用户页面 - 必须在 /:id 路由之前定义
   */
  @GetMapping("/page/users/:id/edit")
  public void getEditUserPage(RoutingContext ctx) {
    String userId = ctx.pathParam("id");

    try {
      Optional<User> userOpt = userService.getUserById(userId);

      if (userOpt.isPresent()) {
        Map<String, Object> userMap = convertUserToMap(userOpt.get());
        Map<String, Object> data = new HashMap<>();
        data.put("user", userMap);

        String html = renderTemplate("edit-user.mustache", data);

        ctx.response()
            .putHeader("content-type", "text/html; charset=utf-8")
            .end(html);
      } else {
        ctx.response()
            .setStatusCode(404)
            .end("User not found");
      }
    } catch (Exception e) {
      LOG.error("Failed to render edit user page", e);
      ctx.response()
          .setStatusCode(500)
          .end("Internal Server Error");
    }
  }

  /**
   * 用户详情页面 - 必须在最后定义，避免与具体路径冲突
   */
  @GetMapping("/page/users/:id")
  public void getUserDetailPage(RoutingContext ctx) {
    String userId = ctx.pathParam("id");

    try {
      Optional<User> userOpt = userService.getUserById(userId);

      if (userOpt.isPresent()) {
        Map<String, Object> userMap = convertUserToMap(userOpt.get());
        Map<String, Object> data = new HashMap<>();
        data.put("user", userMap);

        String html = renderTemplate("user-detail.mustache", data);

        ctx.response()
            .putHeader("content-type", "text/html; charset=utf-8")
            .end(html);
      } else {
        ctx.response()
            .setStatusCode(404)
            .end("User not found");
      }
    } catch (Exception e) {
      LOG.error("Failed to render user detail page", e);
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
    LOG.info("Rendering template: {}", templateName);
    LOG.debug("Template data: {}", data);

    try (InputStream is = getClass().getResourceAsStream("/user-plugin/templates/" + templateName)) {
      if (is == null) {
        LOG.error("Template not found: /user-plugin/templates/{}", templateName);
        throw new RuntimeException("Template not found: " + templateName);
      }

      LOG.info("Template found, compiling...");
      Mustache mustache = mustacheFactory.compile(
          new InputStreamReader(is, StandardCharsets.UTF_8),
          templateName);

      StringWriter writer = new StringWriter();
      LOG.info("Executing template...");
      mustache.execute(writer, data).flush();
      String result = writer.toString();

      LOG.info("Template executed successfully, result length: {}", result.length());
      return result;
    } catch (Exception e) {
      LOG.error("Error rendering template: " + templateName, e);
      throw new RuntimeException("Template rendering error: " + e.getMessage(), e);
    }
  }

  /**
   * 将User实体转换为Map格式，保持API兼容性
   */
  private Map<String, Object> convertUserToMap(User user) {
    Map<String, Object> userMap = new HashMap<>();
    userMap.put("id", user.getId());
    userMap.put("name", user.getName());
    userMap.put("phone", user.getPhone());
    userMap.put("department", user.getDepartment());
    userMap.put("role", user.getRole());
    userMap.put("status", user.getStatus());
    userMap.put("createdAt", user.getCreatedAt());
    userMap.put("updatedAt", user.getUpdatedAt());
    userMap.put("lastLogin", user.getLastLogin());

    // 获取邮箱信息
    if (accountService != null) {
      accountService.getEmailAccount(user.getId()).ifPresent(emailAccount -> {
        userMap.put("email", emailAccount.getIdentifier());
        userMap.put("emailVerified", emailAccount.isVerified());
      });
    }

    return userMap;
  }
}