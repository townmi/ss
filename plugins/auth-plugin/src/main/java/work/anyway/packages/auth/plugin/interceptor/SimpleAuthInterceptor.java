package work.anyway.packages.auth.plugin.interceptor;

import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import work.anyway.annotations.Interceptor;
import work.anyway.annotations.InterceptorComponent;
import work.anyway.packages.auth.plugin.utils.TokenValidator;

/**
 * 简单认证拦截器
 * 使用 JWT token 进行身份验证，设置用户上下文信息
 */
@InterceptorComponent(name = "SimpleAuth", description = "JWT-based user authentication interceptor", order = 25 // 认证拦截器优先级较高
)
public class SimpleAuthInterceptor implements Interceptor {

  private static final Logger LOG = LoggerFactory.getLogger(SimpleAuthInterceptor.class);

  @Autowired
  private TokenValidator tokenValidator;

  @Override
  public boolean preHandle(RoutingContext ctx) {
    String path = ctx.request().path();

    // 跳过不需要认证的路径
    if (isPublicPath(path)) {
      LOG.debug("Skipping authentication for public path: {}", path);
      return true;
    }

    // 提取 Authorization 头
    String authHeader = ctx.request().getHeader("Authorization");
    String token = tokenValidator.extractToken(authHeader);

    if (token == null) {
      LOG.warn("Access denied - No valid authorization token for path: {}", path);
      sendUnauthorized(ctx, "Authorization token required");
      return false;
    }

    // 验证 token
    TokenValidator.TokenValidationResult result = tokenValidator.validateToken(token);

    if (result.isFailure()) {
      LOG.warn("Access denied - Token validation failed for path: {} - {}", path, result.getErrorMessage());
      sendUnauthorized(ctx, result.getErrorMessage());
      return false;
    }

    // 验证成功，设置用户上下文信息
    ctx.put("currentUser", result.getTokenInfo().toMap());
    ctx.put("userId", result.getUserId());
    ctx.put("userEmail", result.getEmail());
    ctx.put("userRole", result.getRole());
    ctx.put("authToken", token);

    LOG.debug("User {} authenticated successfully for path: {}", result.getUserId(), path);
    return true;
  }

  @Override
  public void postHandle(RoutingContext ctx, Object result) {
    String userId = ctx.get("userId");
    if (userId != null) {
      LOG.debug("User {} completed operation on path: {}", userId, ctx.request().path());
    }
  }

  @Override
  public void afterCompletion(RoutingContext ctx, Exception ex) {
    if (ex != null) {
      String userId = ctx.get("userId");
      if (userId != null) {
        LOG.warn("User {} operation failed on path: {} - {}", userId, ctx.request().path(), ex.getMessage());
      }
    }
  }

  @Override
  public String getName() {
    return "SimpleAuth";
  }

  @Override
  public int getOrder() {
    return 25;
  }

  /**
   * 检查是否为公共路径（不需要认证）
   */
  private boolean isPublicPath(String path) {
    // 定义不需要认证的公共路径
    return path.startsWith("/page/auth/") || // 认证相关页面
        path.equals("/") || // 首页
        path.startsWith("/api/auth/login") || // 登录API
        path.startsWith("/api/auth/register") || // 注册API
        path.startsWith("/api/auth/refresh") || // 刷新token API
        path.startsWith("/api/public/") || // 公共API
        path.startsWith("/static/") || // 静态资源
        path.startsWith("/favicon.ico") || // 网站图标
        path.startsWith("/health") || // 健康检查
        path.startsWith("/metrics"); // 监控指标
  }

  /**
   * 发送未授权响应
   */
  private void sendUnauthorized(RoutingContext ctx, String message) {
    String acceptHeader = ctx.request().getHeader("Accept");

    // 根据请求类型返回不同格式的响应
    if (acceptHeader != null && acceptHeader.contains("text/html")) {
      // 浏览器请求，重定向到登录页面
      String currentPath = ctx.request().path();
      String redirectUrl = "/page/auth/login";

      // 添加重定向参数，登录成功后回到原页面
      if (!isAuthPage(currentPath)) {
        redirectUrl += "?redirect=" + java.net.URLEncoder.encode(currentPath, java.nio.charset.StandardCharsets.UTF_8);
      }

      ctx.response()
          .setStatusCode(302)
          .putHeader("Location", redirectUrl)
          .end();
    } else {
      // API 请求，返回 JSON 错误响应
      String jsonResponse = String.format(
          "{\"success\":false,\"error\":\"Unauthorized\",\"message\":\"%s\"}",
          message);

      ctx.response()
          .setStatusCode(401)
          .putHeader("Content-Type", "application/json")
          .end(jsonResponse);
    }
  }

  /**
   * 检查是否为认证相关页面
   */
  private boolean isAuthPage(String path) {
    return path.startsWith("/page/auth/");
  }
}