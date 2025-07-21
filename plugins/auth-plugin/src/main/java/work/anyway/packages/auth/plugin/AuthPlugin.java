package work.anyway.packages.auth.plugin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
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
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class AuthPlugin implements Plugin {

  private static final Logger LOG = LoggerFactory.getLogger(AuthPlugin.class);
  private PermissionService permissionService; // 将由容器自动注入
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final MustacheFactory mustacheFactory = new DefaultMustacheFactory();

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
    String userId = ctx.pathParam("userId");

    // 使用 executeBlocking 避免阻塞事件循环
    ctx.vertx().<Set<String>>executeBlocking(promise -> {
      try {
        Set<String> permissions = permissionService.getUserPermissions(userId);
        promise.complete(permissions);
      } catch (Exception e) {
        promise.fail(e);
      }
    }, false, res -> {
      if (res.succeeded()) {
        try {
          Set<String> permissions = res.result();
          JsonObject response = new JsonObject()
              .put("userId", userId)
              .put("permissions", new JsonArray(new ArrayList<>(permissions)));

          ctx.response()
              .putHeader("content-type", "application/json")
              .end(response.encode());
        } catch (Exception e) {
          LOG.error("Error encoding response", e);
          ctx.response().setStatusCode(500).end("Internal Server Error");
        }
      } else {
        LOG.error("Error getting user permissions", res.cause());
        ctx.response().setStatusCode(500).end("Internal Server Error");
      }
    });
  }

  private void grantPermission(RoutingContext ctx) {
    String userId = ctx.pathParam("userId");
    JsonObject body = ctx.body().asJsonObject();
    String permission = body.getString("permission");

    if (permission == null || permission.isEmpty()) {
      ctx.response()
          .setStatusCode(400)
          .end("Permission is required");
      return;
    }

    // 使用 executeBlocking 避免阻塞事件循环
    ctx.vertx().<Void>executeBlocking(promise -> {
      try {
        permissionService.grantPermission(userId, permission);
        promise.complete();
      } catch (Exception e) {
        promise.fail(e);
      }
    }, false, res -> {
      if (res.succeeded()) {
        JsonObject response = new JsonObject()
            .put("success", true)
            .put("message", "Permission granted successfully");

        ctx.response()
            .putHeader("content-type", "application/json")
            .end(response.encode());
      } else {
        LOG.error("Error granting permission", res.cause());
        ctx.response().setStatusCode(500).end("Internal Server Error");
      }
    });
  }

  private void revokePermission(RoutingContext ctx) {
    String userId = ctx.pathParam("userId");
    String permission = ctx.pathParam("permission");

    // 使用 executeBlocking 避免阻塞事件循环
    ctx.vertx().<Void>executeBlocking(promise -> {
      try {
        permissionService.revokePermission(userId, permission);
        promise.complete();
      } catch (Exception e) {
        promise.fail(e);
      }
    }, false, res -> {
      if (res.succeeded()) {
        JsonObject response = new JsonObject()
            .put("success", true)
            .put("message", "Permission revoked successfully");

        ctx.response()
            .putHeader("content-type", "application/json")
            .end(response.encode());
      } else {
        LOG.error("Error revoking permission", res.cause());
        ctx.response().setStatusCode(500).end("Internal Server Error");
      }
    });
  }

  private void checkPermission(RoutingContext ctx) {
    String userId = ctx.pathParam("userId");
    String permission = ctx.pathParam("permission");

    // 使用 executeBlocking 避免阻塞事件循环
    ctx.vertx().<Boolean>executeBlocking(promise -> {
      try {
        boolean hasPermission = permissionService.hasPermission(userId, permission);
        promise.complete(hasPermission);
      } catch (Exception e) {
        promise.fail(e);
      }
    }, false, res -> {
      if (res.succeeded()) {
        JsonObject response = new JsonObject()
            .put("userId", userId)
            .put("permission", permission)
            .put("hasPermission", res.result());

        ctx.response()
            .putHeader("content-type", "application/json")
            .end(response.encode());
      } else {
        LOG.error("Error checking permission", res.cause());
        ctx.response().setStatusCode(500).end("Internal Server Error");
      }
    });
  }

  // 页面处理方法
  private void getIndexPage(RoutingContext ctx) {
    try {
      // 使用 Mustache 模板渲染
      Mustache mustache = mustacheFactory.compile("auth-plugin/templates/index.mustache");

      Map<String, Object> data = new HashMap<>();
      data.put("pluginName", getName());
      data.put("availablePermissions", AVAILABLE_PERMISSIONS);

      StringWriter writer = new StringWriter();
      mustache.execute(writer, data);

      ctx.response()
          .putHeader("content-type", "text/html; charset=utf-8")
          .end(writer.toString());
    } catch (Exception e) {
      LOG.error("Error rendering index page", e);
      ctx.response().setStatusCode(500).end("Internal Server Error");
    }
  }

  private void getPermissionsPage(RoutingContext ctx) {
    try {
      Mustache mustache = mustacheFactory.compile("auth-plugin/templates/permissions.mustache");

      Map<String, Object> data = new HashMap<>();
      data.put("availablePermissions", AVAILABLE_PERMISSIONS);

      StringWriter writer = new StringWriter();
      mustache.execute(writer, data);

      ctx.response()
          .putHeader("content-type", "text/html; charset=utf-8")
          .end(writer.toString());
    } catch (Exception e) {
      LOG.error("Error rendering permissions page", e);
      ctx.response().setStatusCode(500).end("Internal Server Error");
    }
  }

  private void getUserPermissionsPage(RoutingContext ctx) {
    String userId = ctx.pathParam("userId");

    // 使用 executeBlocking 获取用户权限
    ctx.vertx().<Set<String>>executeBlocking(promise -> {
      try {
        Set<String> permissions = permissionService.getUserPermissions(userId);
        promise.complete(permissions);
      } catch (Exception e) {
        promise.fail(e);
      }
    }, false, res -> {
      if (res.succeeded()) {
        try {
          Mustache mustache = mustacheFactory.compile("auth-plugin/templates/user-permissions.mustache");

          Map<String, Object> data = new HashMap<>();
          data.put("userId", userId);
          data.put("userPermissions", res.result());
          data.put("availablePermissions", AVAILABLE_PERMISSIONS);

          // 计算哪些权限用户还没有
          Set<String> missingPermissions = new HashSet<>(AVAILABLE_PERMISSIONS);
          missingPermissions.removeAll(res.result());
          data.put("missingPermissions", missingPermissions);

          StringWriter writer = new StringWriter();
          mustache.execute(writer, data);

          ctx.response()
              .putHeader("content-type", "text/html; charset=utf-8")
              .end(writer.toString());
        } catch (Exception e) {
          LOG.error("Error rendering user permissions page", e);
          ctx.response().setStatusCode(500).end("Internal Server Error");
        }
      } else {
        LOG.error("Error getting user permissions for page", res.cause());
        ctx.response().setStatusCode(500).end("Internal Server Error");
      }
    });
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