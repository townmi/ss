package work.anyway.packages.user.plugin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import work.anyway.annotations.*;
import work.anyway.interfaces.user.UserService;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * 用户管理插件
 */
@Plugin(name = "User Plugin", version = "1.0.0", description = "管理系统用户，包括创建、查看、编辑用户信息", icon = "👤", mainPagePath = "/page/users/")
@Controller
@RequestMapping("/")
public class UserPlugin {

  private static final Logger LOG = LoggerFactory.getLogger(UserPlugin.class);

  @Autowired
  private UserService userService;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final MustacheFactory mustacheFactory = new DefaultMustacheFactory();

  // API 端点

  /**
   * 获取所有用户
   */
  @GetMapping("/users")
  public void getAllUsers(RoutingContext ctx) {
    try {
      List<Map<String, Object>> users = userService.getAllUsers();
      LOG.debug("Retrieved {} users", users.size());

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("data", users)
          .put("total", users.size());

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());
    } catch (Exception e) {
      LOG.error("Failed to get all users", e);
      sendError(ctx, 500, "Failed to retrieve users: " + e.getMessage());
    }
  }

  /**
   * 根据ID获取用户
   */
  @GetMapping("/users/:id")
  public void getUserById(RoutingContext ctx) {
    String userId = ctx.pathParam("id");
    LOG.debug("Getting user by ID: {}", userId);

    try {
      Optional<Map<String, Object>> userOpt = userService.getUserById(userId);

      if (userOpt.isPresent()) {
        JsonObject response = new JsonObject()
            .put("success", true)
            .put("data", userOpt.get());

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
   * 创建用户
   */
  @PostMapping("/users")
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
      Map<String, Object> userData = body.getMap();
      Map<String, Object> createdUser = userService.createUser(userData);
      LOG.info("User created successfully: {}", createdUser.get("id"));

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("data", createdUser)
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
  public void updateUser(RoutingContext ctx) {
    String userId = ctx.pathParam("id");
    JsonObject body = ctx.getBodyAsJson();
    LOG.debug("Updating user {} with data: {}", userId, body);

    if (body == null || body.isEmpty()) {
      sendError(ctx, 400, "Request body is required");
      return;
    }

    try {
      Map<String, Object> userData = body.getMap();
      boolean updated = userService.updateUser(userId, userData);

      if (updated) {
        LOG.info("User updated successfully: {}", userId);

        // 获取更新后的用户信息
        Optional<Map<String, Object>> updatedUser = userService.getUserById(userId);

        JsonObject response = new JsonObject()
            .put("success", true)
            .put("data", updatedUser.orElse(null))
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
      Optional<Map<String, Object>> userOpt = userService.findUserByEmail(email);

      if (userOpt.isPresent()) {
        JsonObject response = new JsonObject()
            .put("success", true)
            .put("data", userOpt.get());

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

  /**
   * 验证用户登录
   */
  @PostMapping("/users/authenticate")
  public void authenticateUser(RoutingContext ctx) {
    JsonObject body = ctx.getBodyAsJson();

    if (body == null || !body.containsKey("email") || !body.containsKey("password")) {
      sendError(ctx, 400, "Email and password are required");
      return;
    }

    String email = body.getString("email");
    String password = body.getString("password");

    try {
      Optional<Map<String, Object>> userOpt = userService.authenticateUser(email, password);

      if (userOpt.isPresent()) {
        LOG.info("User authenticated successfully: {}", email);

        JsonObject response = new JsonObject()
            .put("success", true)
            .put("data", userOpt.get())
            .put("message", "Authentication successful");

        ctx.response()
            .putHeader("content-type", "application/json")
            .end(response.encode());
      } else {
        LOG.warn("Authentication failed for email: {}", email);
        sendError(ctx, 401, "Invalid email or password");
      }
    } catch (Exception e) {
      LOG.error("Authentication error for email: {}", email, e);
      sendError(ctx, 500, "Authentication failed: " + e.getMessage());
    }
  }

  // 页面路由

  /**
   * 用户列表页面
   */
  @GetMapping("/page/users/")
  public void getUsersPage(RoutingContext ctx) {
    try {
      List<Map<String, Object>> users = userService.getAllUsers();

      Map<String, Object> data = new HashMap<>();
      data.put("users", users);
      data.put("userCount", users.size());
      data.put("hasUsers", !users.isEmpty());

      String html = renderTemplate("users.mustache", data);

      ctx.response()
          .putHeader("content-type", "text/html; charset=utf-8")
          .end(html);
    } catch (Exception e) {
      LOG.error("Failed to render users page", e);
      ctx.response()
          .setStatusCode(500)
          .end("Internal Server Error");
    }
  }

  /**
   * 创建用户页面
   */
  @GetMapping("/page/users/create")
  public void getCreateUserPage(RoutingContext ctx) {
    try {
      Map<String, Object> data = new HashMap<>();
      data.put("action", "create");
      data.put("submitUrl", "/users");
      data.put("method", "POST");

      String html = renderTemplate("create-user.html", data);

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
   * 用户详情页面
   */
  @GetMapping("/page/users/:id")
  public void getUserDetailPage(RoutingContext ctx) {
    String userId = ctx.pathParam("id");

    try {
      Optional<Map<String, Object>> userOpt = userService.getUserById(userId);

      if (userOpt.isPresent()) {
        Map<String, Object> data = new HashMap<>();
        data.put("user", userOpt.get());

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
    try (InputStream is = getClass().getResourceAsStream("/user-plugin/templates/" + templateName)) {
      if (is == null) {
        throw new RuntimeException("Template not found: " + templateName);
      }

      Mustache mustache = mustacheFactory.compile(
          new InputStreamReader(is, StandardCharsets.UTF_8),
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