package work.anyway.packages.user.plugin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.anyway.interfaces.plugin.Plugin;
import work.anyway.interfaces.user.UserService;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class UserPlugin implements Plugin {

  private static final Logger LOG = LoggerFactory.getLogger(UserPlugin.class);
  private UserService userService; // 将由容器自动注入
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public String getName() {
    return "User Plugin";
  }

  @Override
  public String getVersion() {
    return "1.0.0";
  }

  @Override
  public void initialize(Router router) {
    LOG.info("Initializing User Plugin...");

    // 检查 userService 是否已被注入
    if (userService == null) {
      LOG.error("UserService was not injected!");
      throw new IllegalStateException("UserService is required but was not injected");
    }

    // 模板引擎将在第一次使用时初始化

    // API 端点
    // GET /users - 获取所有用户
    router.get("/users").handler(this::getAllUsers);

    // GET /users/:id - 根据ID获取用户
    router.get("/users/:id").handler(this::getUserById);

    // POST /users - 创建用户
    router.post("/users").handler(this::createUser);

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
    LOG.info("  API: GET /users, GET /users/:id, POST /users");
    LOG.info("  Pages: GET /page/users/, GET /page/users/list, GET /page/users/:id, GET /page/users/create");
  }

  // API 处理方法
  private void getAllUsers(RoutingContext ctx) {
    try {
      Object users = userService.getAllUsers();
      sendJsonResponse(ctx.response(), users);
    } catch (Exception e) {
      LOG.error("Error getting all users", e);
      ctx.response().setStatusCode(500).end("Internal Server Error");
    }
  }

  private void getUserById(RoutingContext ctx) {
    try {
      String userId = ctx.pathParam("id");
      Object user = userService.getUserById(userId);

      if (user != null) {
        sendJsonResponse(ctx.response(), user);
      } else {
        ctx.response().setStatusCode(404).end("User not found");
      }
    } catch (Exception e) {
      LOG.error("Error getting user by id", e);
      ctx.response().setStatusCode(500).end("Internal Server Error");
    }
  }

  private void createUser(RoutingContext ctx) {
    try {
      JsonObject body = ctx.body().asJsonObject();
      if (body == null || body.isEmpty()) {
        ctx.response().setStatusCode(400).end("Request body is required");
        return;
      }

      Map<String, Object> userInfo = body.getMap();
      // 确保用户数据中包含ID字段
      String userId = userService.createUser(userInfo);
      userInfo.put("id", userId);

      JsonObject response = new JsonObject()
          .put("id", userId)
          .put("message", "User created successfully");
      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());
    } catch (Exception e) {
      LOG.error("Error creating user", e);
      ctx.response().setStatusCode(500).end("Internal Server Error");
    }
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
    try {
      // 获取所有用户数据 - getAllUsers 返回的是 Map
      Object usersObj = userService.getAllUsers();
      Map<String, Object> usersMap = (Map<String, Object>) usersObj;

      // 读取模板
      String template = readResourceFile("user-plugin/templates/users.html");
      if (template != null) {
        // 由于用户列表是动态的，我们需要手动构建表格行
        StringBuilder userRows = new StringBuilder();
        if (usersMap != null && !usersMap.isEmpty()) {
          // 添加默认用户
          userRows.append("<tr>")
              .append("<td class=\"user-id\">123</td>")
              .append("<td class=\"user-name\">John Doe</td>")
              .append("<td class=\"user-email\">john.doe@example.com</td>")
              .append("<td>2024-01-01</td>")
              .append("<td class=\"action-links\">")
              .append("<a href=\"/page/users/123\">查看详情</a>")
              .append("<a href=\"#\" onclick=\"editUser(this)\" data-id=\"123\">编辑</a>")
              .append("<a href=\"#\" onclick=\"deleteUser(this)\" data-id=\"123\" style=\"color: #e74c3c;\">删除</a>")
              .append("</td>")
              .append("</tr>");

          // 遍历 Map 中的用户
          for (Map.Entry<String, Object> entry : usersMap.entrySet()) {
            String userId = entry.getKey();
            Object userObj = entry.getValue();
            if (userObj instanceof Map) {
              Map<String, Object> user = (Map<String, Object>) userObj;
              userRows.append("<tr>")
                  .append("<td class=\"user-id\">").append(user.get("id") != null ? user.get("id") : userId)
                  .append("</td>")
                  .append("<td class=\"user-name\">").append(user.get("name") != null ? user.get("name") : "Unknown")
                  .append("</td>")
                  .append("<td class=\"user-email\">").append(user.get("email") != null ? user.get("email") : "-")
                  .append("</td>")
                  .append("<td>").append(user.get("createdAt") != null ? user.get("createdAt") : "-").append("</td>")
                  .append("<td class=\"action-links\">")
                  .append("<a href=\"/page/users/").append(user.get("id") != null ? user.get("id") : userId)
                  .append("\">查看详情</a>")
                  .append("<a href=\"#\" onclick=\"editUser(this)\" data-id=\"")
                  .append(user.get("id") != null ? user.get("id") : userId)
                  .append("\">编辑</a>")
                  .append("<a href=\"#\" onclick=\"deleteUser(this)\" data-id=\"")
                  .append(user.get("id") != null ? user.get("id") : userId)
                  .append("\" style=\"color: #e74c3c;\">删除</a>")
                  .append("</td>")
                  .append("</tr>");
            }
          }
          // 替换模板中的表格内容
          template = template.replaceAll("<!--USER_ROWS-->", userRows.toString());
          template = template.replaceAll("th:if=\"\\$\\{users != null and #lists\\.size\\(users\\) > 0\\}\"", "");
          template = template.replaceAll("th:if=\"\\$\\{users == null or #lists\\.size\\(users\\) == 0\\}\"",
              "style=\"display:none\"");
        } else {
          // 没有用户时显示空状态
          template = template.replaceAll("th:if=\"\\$\\{users != null and #lists\\.size\\(users\\) > 0\\}\"",
              "style=\"display:none\"");
          template = template.replaceAll("th:if=\"\\$\\{users == null or #lists\\.size\\(users\\) == 0\\}\"", "");
        }

        // 更新用户数量 - 包括默认用户
        int userCount = usersMap.size() + 1; // +1 for default user
        template = template.replaceAll("th:text=\"\\$\\{#lists\\.size\\(users\\)\\}\"[^>]*>\\d*</",
            ">" + userCount + "</");

        ctx.response()
            .putHeader("content-type", "text/html; charset=utf-8")
            .end(template);
      } else {
        ctx.response()
            .setStatusCode(404)
            .end("Template not found");
      }
    } catch (Exception e) {
      LOG.error("Error rendering users page", e);
      ctx.response().setStatusCode(500).end("Internal Server Error");
    }
  }

  private void getUserDetailPage(RoutingContext ctx) {
    try {
      String userId = ctx.pathParam("id");
      Map<String, Object> user = (Map<String, Object>) userService.getUserById(userId);

      // 读取模板
      String template = readResourceFile("user-plugin/templates/user-detail.html");
      if (template != null) {
        if (user != null) {
          // 替换用户信息
          template = template.replaceAll("th:text=\"\\$\\{user\\.name\\}\"[^>]*>[^<]*<", ">" + user.get("name") + "<");
          template = template.replaceAll("th:text=\"\\$\\{user\\.id\\}\"[^>]*>[^<]*<", ">" + user.get("id") + "<");
          template = template.replaceAll("th:text=\"\\$\\{user\\.email \\?\\: '未设置'\\}\"[^>]*>[^<]*<",
              ">" + (user.get("email") != null ? user.get("email") : "未设置") + "<");
          template = template.replaceAll("th:text=\"\\$\\{user\\.phone \\?\\: '未设置'\\}\"[^>]*>[^<]*<",
              ">" + (user.get("phone") != null ? user.get("phone") : "未设置") + "<");
          template = template.replaceAll("th:text=\"\\$\\{user\\.status \\?\\: '活跃'\\}\"[^>]*>[^<]*<",
              ">" + (user.get("status") != null ? user.get("status") : "活跃") + "<");
          template = template.replaceAll("th:text=\"\\$\\{user\\.createdAt \\?\\: '未知'\\}\"[^>]*>[^<]*<",
              ">" + (user.get("createdAt") != null ? user.get("createdAt") : "未知") + "<");

          // 其他信息
          template = template.replaceAll("th:text=\"\\$\\{user\\.department \\?\\: '未分配'\\}\"[^>]*>[^<]*<",
              ">" + (user.get("department") != null ? user.get("department") : "未分配") + "<");
          template = template.replaceAll("th:text=\"\\$\\{user\\.role \\?\\: '普通用户'\\}\"[^>]*>[^<]*<",
              ">" + (user.get("role") != null ? user.get("role") : "普通用户") + "<");
          template = template.replaceAll("th:text=\"\\$\\{user\\.lastLogin \\?\\: '从未登录'\\}\"[^>]*>[^<]*<",
              ">" + (user.get("lastLogin") != null ? user.get("lastLogin") : "从未登录") + "<");
          template = template.replaceAll("th:text=\"\\$\\{user\\.notes \\?\\: '无'\\}\"[^>]*>[^<]*<",
              ">" + (user.get("notes") != null ? user.get("notes") : "无") + "<");

          // 处理头像首字母
          String firstLetter = user.get("name") != null ? user.get("name").toString().substring(0, 1).toUpperCase()
              : "U";
          template = template.replaceAll(
              "th:text=\"\\$\\{#strings\\.substring\\(user\\.name, 0, 1\\)\\.toUpperCase\\(\\)\\}\"[^>]*>[^<]*<",
              ">" + firstLetter + "<");

          // 显示用户卡片，隐藏错误状态
          template = template.replaceAll("th:if=\"\\$\\{user != null\\}\"", "");
          template = template.replaceAll("th:if=\"\\$\\{user == null\\}\"", "style=\"display:none\"");

          // 设置 JavaScript 变量
          template = template.replaceAll("/\\*\\[\\[\\$\\{user\\?\\.id\\}\\]\\]\\*/\\s*null",
              "'" + user.get("id") + "'");
        } else {
          // 隐藏用户卡片，显示错误状态
          template = template.replaceAll("th:if=\"\\$\\{user != null\\}\"", "style=\"display:none\"");
          template = template.replaceAll("th:if=\"\\$\\{user == null\\}\"", "");
        }

        ctx.response()
            .putHeader("content-type", "text/html; charset=utf-8")
            .end(template);
      } else {
        ctx.response()
            .setStatusCode(404)
            .end("Template not found");
      }
    } catch (Exception e) {
      LOG.error("Error rendering user detail page", e);
      ctx.response().setStatusCode(500).end("Internal Server Error");
    }
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
}