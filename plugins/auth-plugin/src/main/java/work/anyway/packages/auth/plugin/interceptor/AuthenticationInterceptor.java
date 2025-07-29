package work.anyway.packages.auth.plugin.interceptor;

import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import work.anyway.annotations.Interceptor;
import work.anyway.annotations.InterceptorComponent;
import work.anyway.interfaces.auth.PermissionService;
import work.anyway.packages.auth.plugin.utils.TokenValidator;

/**
 * 认证拦截器
 * 验证用户身份和权限，支持基于角色和资源的访问控制
 */
@InterceptorComponent(name = "Authentication", description = "User authentication and authorization interceptor with permission checking", order = 30 // 权限检查在身份认证之后
)
public class AuthenticationInterceptor implements Interceptor {

  private static final Logger LOG = LoggerFactory.getLogger(AuthenticationInterceptor.class);

  @Autowired
  private TokenValidator tokenValidator;

  @Autowired
  private PermissionService permissionService;

  @Override
  public boolean preHandle(RoutingContext ctx) {
    String path = ctx.request().path();
    String method = ctx.request().method().toString();

    // 跳过不需要权限检查的路径
    if (isPublicPath(path)) {
      LOG.debug("Skipping permission check for public path: {}", path);
      return true;
    }

    // 检查是否已通过身份认证（由 SimpleAuthInterceptor 设置）
    String userId = ctx.get("userId");
    String userRole = ctx.get("userRole");

    if (userId == null) {
      LOG.warn("Permission denied - User not authenticated for path: {} {}", method, path);
      sendForbidden(ctx, "Authentication required");
      return false;
    }

    // 检查用户权限
    if (!checkUserPermissions(userId, userRole, method, path)) {
      LOG.warn("Permission denied - User {} has no permission for {} {}", userId, method, path);
      sendForbidden(ctx, "Insufficient permissions");
      return false;
    }

    LOG.debug("User {} authorized successfully for {} {}", userId, method, path);
    return true;
  }

  @Override
  public void postHandle(RoutingContext ctx, Object result) {
    // 可以在这里记录用户操作日志或更新访问统计
    String userId = ctx.get("userId");
    if (userId != null) {
      LOG.debug("User {} completed authorized operation on path: {}", userId, ctx.request().path());
    }
  }

  @Override
  public void afterCompletion(RoutingContext ctx, Exception ex) {
    if (ex != null) {
      String userId = ctx.get("userId");
      if (userId != null) {
        LOG.warn("User {} authorized operation failed on path: {} - {}", userId, ctx.request().path(), ex.getMessage());
      }
    }
  }

  @Override
  public String getName() {
    return "Authentication";
  }

  @Override
  public int getOrder() {
    return 30;
  }

  /**
   * 检查是否为公共路径（不需要权限验证）
   */
  private boolean isPublicPath(String path) {
    // 定义不需要权限检查的路径
    return path.startsWith("/page/auth/") || // 认证相关页面
        path.equals("/") || // 首页
        path.startsWith("/api/auth/") || // 认证相关API
        path.startsWith("/api/public/") || // 公共API
        path.startsWith("/static/") || // 静态资源
        path.startsWith("/favicon.ico") || // 网站图标
        path.startsWith("/health") || // 健康检查
        path.startsWith("/metrics") || // 监控指标
        path.startsWith("/page/") && isBasicPageAccess(path); // 基础页面访问
  }

  /**
   * 检查是否为基础页面访问（普通用户可访问）
   */
  private boolean isBasicPageAccess(String path) {
    // 定义普通用户可以访问的页面
    return path.equals("/page/") || // 系统首页
        path.startsWith("/page/users/") || // 用户相关页面（可以在具体方法中再次检查权限）
        path.startsWith("/page/profile/"); // 个人资料页面
  }

  /**
   * 检查用户权限
   */
  private boolean checkUserPermissions(String userId, String userRole, String method, String path) {
    try {
      // 1. 超级管理员拥有所有权限
      if ("admin".equals(userRole)) {
        LOG.debug("Admin user {} granted access to {} {}", userId, method, path);
        return true;
      }

      // 2. 构建权限字符串
      String requiredPermission = buildPermissionString(method, path);
      if (requiredPermission == null) {
        // 如果无法构建权限字符串，说明是不需要特殊权限的操作
        return true;
      }

      // 3. 检查用户是否拥有所需权限
      boolean hasPermission = permissionService.hasPermission(userId, requiredPermission);

      if (!hasPermission) {
        // 4. 如果没有精确权限，检查是否有通用权限
        String generalPermission = buildGeneralPermission(path);
        if (generalPermission != null) {
          hasPermission = permissionService.hasPermission(userId, generalPermission);
        }
      }

      LOG.debug("Permission check for user {}: {} -> {}", userId, requiredPermission, hasPermission);
      return hasPermission;

    } catch (Exception e) {
      LOG.error("Error checking permission for user: {} on {} {}", userId, method, path, e);
      return false; // 权限检查失败时拒绝访问
    }
  }

  /**
   * 构建权限字符串
   * 格式: METHOD:RESOURCE 或 RESOURCE.ACTION
   */
  private String buildPermissionString(String method, String path) {
    // API 路径权限映射
    if (path.startsWith("/api/")) {
      return buildApiPermission(method, path);
    }

    // 页面路径权限映射
    if (path.startsWith("/page/")) {
      return buildPagePermission(method, path);
    }

    return null;
  }

  /**
   * 构建 API 权限字符串
   */
  private String buildApiPermission(String method, String path) {
    // 用户管理API
    if (path.startsWith("/api/users")) {
      switch (method) {
        case "GET":
          return "user.read";
        case "POST":
          return "user.create";
        case "PUT":
          return "user.update";
        case "DELETE":
          return "user.delete";
      }
    }

    // 权限管理API
    if (path.startsWith("/auth/permissions")) {
      switch (method) {
        case "GET":
          return "permission.read";
        case "POST":
          return "permission.grant";
        case "DELETE":
          return "permission.revoke";
      }
    }

    // 系统管理API
    if (path.startsWith("/api/system")) {
      return "system.manage";
    }

    // 报表API
    if (path.startsWith("/api/reports")) {
      switch (method) {
        case "GET":
          return "report.view";
        case "POST":
          return "report.export";
      }
    }

    return null;
  }

  /**
   * 构建页面权限字符串
   */
  private String buildPagePermission(String method, String path) {
    // 用户管理页面
    if (path.startsWith("/page/users/")) {
      if (path.contains("/create") || path.contains("/edit")) {
        return "user.manage";
      }
      return "user.view";
    }

    // 权限管理页面
    if (path.startsWith("/page/auth/permissions")) {
      return "permission.manage";
    }

    // 系统管理页面
    if (path.startsWith("/page/system/")) {
      return "system.config";
    }

    // 报表页面
    if (path.startsWith("/page/reports/")) {
      return "report.view";
    }

    return null;
  }

  /**
   * 构建通用权限字符串
   */
  private String buildGeneralPermission(String path) {
    if (path.startsWith("/api/users") || path.startsWith("/page/users/")) {
      return "user.access";
    }

    if (path.startsWith("/auth/permissions") || path.startsWith("/page/auth/")) {
      return "admin.access";
    }

    if (path.startsWith("/api/system") || path.startsWith("/page/system/")) {
      return "system.access";
    }

    return null;
  }

  /**
   * 发送权限不足响应
   */
  private void sendForbidden(RoutingContext ctx, String message) {
    String acceptHeader = ctx.request().getHeader("Accept");

    // 根据请求类型返回不同格式的响应
    if (acceptHeader != null && acceptHeader.contains("text/html")) {
      // 浏览器请求，显示权限不足页面
      String html = "<!DOCTYPE html>" +
          "<html><head><title>权限不足</title></head>" +
          "<body style='font-family: Arial; text-align: center; padding: 50px;'>" +
          "<h1>🚫 权限不足</h1>" +
          "<p>您没有访问此资源的权限</p>" +
          "<p style='color: #666;'>" + message + "</p>" +
          "<a href='/page/' style='color: #667eea;'>返回首页</a>" +
          "</body></html>";

      ctx.response()
          .setStatusCode(403)
          .putHeader("Content-Type", "text/html; charset=utf-8")
          .end(html);
    } else {
      // API 请求，返回 JSON 错误响应
      String jsonResponse = String.format(
          "{\"success\":false,\"error\":\"Forbidden\",\"message\":\"%s\"}",
          message);

      ctx.response()
          .setStatusCode(403)
          .putHeader("Content-Type", "application/json")
          .end(jsonResponse);
    }
  }
}