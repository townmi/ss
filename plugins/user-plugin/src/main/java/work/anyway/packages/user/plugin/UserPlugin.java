package work.anyway.packages.user.plugin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.anyway.interfaces.plugin.Plugin;
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

public class UserPlugin implements Plugin {

  private static final Logger LOG = LoggerFactory.getLogger(UserPlugin.class);

  private UserService userService; // 将由容器自动注入
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final MustacheFactory mustacheFactory = new DefaultMustacheFactory();

  @Override
  public String getName() {
    return "User Plugin";
  }

  @Override
  public String getVersion() {
    return "1.0.0";
  }

  @Override
  public String getDescription() {
    return "管理系统用户，包括创建、查看、编辑用户信息";
  }

  @Override
  public String getIcon() {
    return "👤";
  }

  @Override
  public String getMainPagePath() {
    return "/page/users/";
  }

  @Override
  public void initialize(Router router) {
    LOG.info("Initializing User Plugin...");

    // 检查 dataService 是否已被注入
    if (userService == null) {
      LOG.error("UserService was not injected!");
      throw new IllegalStateException("UserService is required but was not injected");
    }

    // API 端点
    // GET /users - 获取所有用户
    router.get("/users").handler(this::getAllUsers);

    // GET /users/:id - 根据ID获取用户
    router.get("/users/:id").handler(this::getUserById);

    // POST /users - 创建用户
    router.post("/users").handler(this::createUser);

    // PUT /users/:id - 更新用户
    router.put("/users/:id").handler(this::updateUser);

    // DELETE /users/:id - 删除用户
    router.delete("/users/:id").handler(this::deleteUser);

    // 页面路由
    // GET /page/users/ - 用户管理主页
    router.get("/page/users/").handler(this::getIndexPage);

    // GET /page/users/list - 用户列表页面
    router.get("/page/users/list").handler(this::getUsersPage);

    // GET /page/users/create - 创建用户页面（必须在 :id 路由之前）
    router.get("/page/users/create").handler(this::getCreateUserPage);

    // GET /page/users/:id - 用户详情页面
    router.get("/page/users/:id").handler(this::getUserDetailPage);

    LOG.info("User Plugin initialized with endpoints:");
    LOG.info("  API: GET /users, GET /users/:id, POST /users, PUT /users/:id, DELETE /users/:id");
    LOG.info("  Pages: GET /page/users/, GET /page/users/list, GET /page/users/:id, GET /page/users/create");
  }

  // API 处理方法
  private void getAllUsers(RoutingContext ctx) {
    try {
      List<Map<String, Object>> users = userService.getAllUsers();
      sendJsonResponse(ctx.response(), users);
    } catch (Exception e) {
      LOG.error("Error getting all users", e);
      ctx.response().setStatusCode(500).end("Internal Server Error");
    }
  }

  private void getUserById(RoutingContext ctx) {
    try {
      String userId = ctx.pathParam("id");
      Optional<Map<String, Object>> user = userService.getUserById(userId);

      if (user.isPresent()) {
        sendJsonResponse(ctx.response(), user.get());
      } else {
        ctx.response().setStatusCode(404).end("User not found");
      }
    } catch (Exception e) {
      LOG.error("Error getting user by id", e);
      ctx.response().setStatusCode(500).end("Internal Server Error");
    }
  }

  private void createUser(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();
    if (body == null || body.isEmpty()) {
      ctx.response().setStatusCode(400).end("Request body is required");
      return;
    }

    Map<String, Object> userInfo = body.getMap();

    // 在 worker 线程中执行数据库操作
    ctx.vertx().<Map<String, Object>>executeBlocking(promise -> {
      try {
        Map<String, Object> savedUser = userService.createUser(userInfo);
        promise.complete(savedUser);
      } catch (Exception e) {
        promise.fail(e);
      }
    }, false, res -> {
      if (res.failed()) {
        LOG.error("Error creating user", res.cause());
        ctx.response().setStatusCode(500).end("Internal Server Error");
        return;
      }

      Map<String, Object> savedUser = res.result();
      JsonObject response = new JsonObject()
          .put("id", savedUser.get("id"))
          .put("message", "User created successfully");
      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());
    });
  }

  private void updateUser(RoutingContext ctx) {
    String userId = ctx.pathParam("id");
    JsonObject body = ctx.body().asJsonObject();
    if (body == null || body.isEmpty()) {
      ctx.response().setStatusCode(400).end("Request body is required");
      return;
    }

    Map<String, Object> updateData = body.getMap();

    // 在 worker 线程中执行数据库操作
    ctx.vertx().<Boolean>executeBlocking(promise -> {
      try {
        boolean success = userService.updateUser(userId, updateData);
        promise.complete(success);
      } catch (Exception e) {
        promise.fail(e);
      }
    }, false, res -> {
      if (res.failed()) {
        LOG.error("Error updating user", res.cause());
        ctx.response().setStatusCode(500).end("Internal Server Error");
        return;
      }

      boolean success = res.result();
      if (success) {
        JsonObject response = new JsonObject()
            .put("id", userId)
            .put("message", "User updated successfully");
        ctx.response()
            .putHeader("content-type", "application/json")
            .end(response.encode());
      } else {
        ctx.response().setStatusCode(404).end("User not found");
      }
    });
  }

  private void deleteUser(RoutingContext ctx) {
    String userId = ctx.pathParam("id");

    // 在 worker 线程中执行数据库操作
    ctx.vertx().<Boolean>executeBlocking(promise -> {
      try {
        boolean success = userService.deleteUser(userId);
        promise.complete(success);
      } catch (Exception e) {
        promise.fail(e);
      }
    }, false, res -> {
      if (res.failed()) {
        LOG.error("Error deleting user", res.cause());
        ctx.response().setStatusCode(500).end("Internal Server Error");
        return;
      }

      boolean success = res.result();
      if (success) {
        JsonObject response = new JsonObject()
            .put("id", userId)
            .put("message", "User deleted successfully");
        ctx.response()
            .putHeader("content-type", "application/json")
            .end(response.encode());
      } else {
        ctx.response().setStatusCode(404).end("User not found");
      }
    });
  }

  // 页面处理方法
  private void getIndexPage(RoutingContext ctx) {
    try {
      // 直接从 classpath 读取模板
      LOG.info("UserPlugin: Loading index.html from classpath");
      String html = readResourceFile("user-plugin/templates/index.html");
      if (html != null) {
        // 添加一个标识来确认是哪个插件的页面
        html = html.replace("</body>", "<!-- UserPlugin --></body>");
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

  private void getUsersPage(RoutingContext ctx) {
    // 在 worker 线程中执行数据库操作，避免阻塞事件循环
    ctx.vertx().<List<Map<String, Object>>>executeBlocking(promise -> {
      try {
        List<Map<String, Object>> users = userService.getAllUsers();
        promise.complete(users);
      } catch (Exception e) {
        promise.fail(e);
      }
    }, false, res -> {
      if (res.failed()) {
        LOG.error("Failed to get users", res.cause());
        ctx.response().setStatusCode(500).end("Internal Server Error");
        return;
      }

      try {
        List<Map<String, Object>> users = res.result();

        // 准备模板数据
        Map<String, Object> templateData = new HashMap<>();

        // 处理用户数据，确保所有字段都有默认值
        List<Map<String, Object>> processedUsers = users.stream()
            .map(user -> {
              Map<String, Object> processedUser = new HashMap<>(user);
              // 确保必要字段有默认值
              processedUser.putIfAbsent("id", "N/A");
              processedUser.putIfAbsent("name", "Unknown");
              processedUser.putIfAbsent("email", "-");
              processedUser.putIfAbsent("createdAt", "N/A");
              return processedUser;
            })
            .collect(Collectors.toList());

        templateData.put("users", processedUsers);
        templateData.put("userCount", processedUsers.size());
        templateData.put("hasUsers", !processedUsers.isEmpty());

        // 渲染模板
        String html = renderTemplate("users.mustache", templateData);

        ctx.response()
            .putHeader("content-type", "text/html; charset=utf-8")
            .end(html);
      } catch (Exception e) {
        LOG.error("Error rendering users page", e);
        ctx.response().setStatusCode(500).end("Internal Server Error");
      }
    });
  }

  private void getUserDetailPage(RoutingContext ctx) {
    String userId = ctx.pathParam("id");

    // 在 worker 线程中执行数据库操作
    ctx.vertx().<Optional<Map<String, Object>>>executeBlocking(promise -> {
      try {
        Optional<Map<String, Object>> user = userService.getUserById(userId);
        promise.complete(user);
      } catch (Exception e) {
        promise.fail(e);
      }
    }, false, res -> {
      if (res.failed()) {
        LOG.error("Failed to get user by id", res.cause());
        ctx.response().setStatusCode(500).end("Internal Server Error");
        return;
      }

      try {
        Optional<Map<String, Object>> user = res.result();

        // 准备模板数据
        Map<String, Object> templateData = new HashMap<>();

        if (user.isPresent()) {
          Map<String, Object> userData = new HashMap<>(user.get());

          // 计算头像首字母
          String name = userData.get("name") != null ? userData.get("name").toString() : "Unknown";
          String firstLetter = name.substring(0, 1).toUpperCase();
          userData.put("firstLetter", firstLetter);

          templateData.put("user", userData);
        }

        // 渲染模板
        String html = renderTemplate("user-detail.mustache", templateData);

        ctx.response()
            .putHeader("content-type", "text/html; charset=utf-8")
            .end(html);
      } catch (Exception e) {
        LOG.error("Error rendering user detail page", e);
        ctx.response().setStatusCode(500).end("Internal Server Error");
      }
    });
  }

  private void getCreateUserPage(RoutingContext ctx) {
    try {
      // 读取创建用户页面模板
      String html = readResourceFile("user-plugin/templates/create-user.html");
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
      LOG.error("Error rendering create user page", e);
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
    try (InputStream is = UserPlugin.class.getResourceAsStream("/" + path)) {
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

  private String processSimpleTemplate(String template, Map<String, Object> data) {
    if (template == null || data == null) {
      return template;
    }

    String result = template;

    // 简单的占位符替换 - 支持 Thymeleaf 的 th:text 语法
    for (Map.Entry<String, Object> entry : data.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      // 替换 th:text="${key}" 形式的占位符
      String pattern = "th:text=\"\\$\\{" + key + "\\}\"[^>]*>([^<]*)<";
      String replacement = ">" + (value != null ? value.toString() : "") + "<";
      result = result.replaceAll(pattern, replacement);
    }

    return result;
  }

  /**
   * 使用 Mustache 渲染模板
   */
  private String renderTemplate(String templateName, Map<String, Object> data) {
    try (InputStream is = UserPlugin.class.getResourceAsStream("/user-plugin/templates/" + templateName)) {
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