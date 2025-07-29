package work.anyway.packages.auth.plugin.interceptor;

import io.vertx.ext.web.RoutingContext;
import io.vertx.core.http.Cookie;
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
@InterceptorComponent(name = "NoAuth", description = "No authentication interceptor", order = 25 // 认证拦截器优先级较高
)
public class NoAuthInterceptor implements Interceptor {

  private static final Logger LOG = LoggerFactory.getLogger(NoAuthInterceptor.class);

  @Autowired
  private TokenValidator tokenValidator;

  @Override
  public boolean preHandle(RoutingContext ctx) {
    String path = ctx.request().path();

    // 提取 token（支持 Authorization 头和 Cookie）
    String token = extractTokenFromRequest(ctx);

    if (token == null) {
      return true;
    }

    // 验证 token
    TokenValidator.TokenValidationResult result = tokenValidator.validateTokenWithoutUserCheck(token);

    if (result.isFailure()) {
      return true;
    }

    // 验证成功，设置用户上下文信息
    ctx.put("currentUser", result.getTokenInfo().toMap());
    ctx.put("userId", result.getUserId());
    ctx.put("userEmail", result.getEmail());
    ctx.put("userRole", result.getRole());
    ctx.put("authToken", token);

    LOG.info("User {} authenticated successfully for path: {}", result.getUserId(), path);
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
    return "NoAuth";
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
   * 从请求中提取 token（支持 Authorization 头和 Cookie）
   */
  private String extractTokenFromRequest(RoutingContext ctx) {
    // 1. 优先从 Authorization 头获取（API 请求）
    String authHeader = ctx.request().getHeader("Authorization");
    String token = tokenValidator.extractToken(authHeader);

    if (token != null) {
      return token;
    }

    // 2. 从 Cookie 中获取（页面请求）
    Cookie tokenCookie = ctx.getCookie("auth_token");
    if (tokenCookie != null && !tokenCookie.getValue().isEmpty()) {
      return tokenCookie.getValue();
    }

    return null;
  }
}