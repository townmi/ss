package work.anyway.packages.auth.plugin;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.anyway.packages.auth.plugin.utils.JwtTokenUtil;

import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Auth插件基础控制器
 * 
 * 提供所有Auth插件控制器的通用功能：
 * - 错误响应处理
 * - 模板渲染
 * - 用户上下文获取
 * - 通用验证方法
 * 
 * @author 作者名
 * @since 1.0.0
 */
public abstract class BaseAuthController {

  protected final Logger LOG = LoggerFactory.getLogger(getClass());

  private static final MustacheFactory mustacheFactory = new DefaultMustacheFactory();

  /**
   * 发送错误响应
   * 
   * @param ctx        路由上下文
   * @param statusCode HTTP状态码
   * @param message    错误消息
   */
  protected void sendError(RoutingContext ctx, int statusCode, String message) {
    JsonObject error = new JsonObject()
        .put("success", false)
        .put("error", message)
        .put("timestamp", System.currentTimeMillis());

    ctx.response()
        .setStatusCode(statusCode)
        .putHeader("content-type", "application/json")
        .end(error.encode());
  }

  /**
   * 发送成功响应
   * 
   * @param ctx  路由上下文
   * @param data 响应数据
   */
  protected void sendSuccess(RoutingContext ctx, JsonObject data) {
    JsonObject response = new JsonObject()
        .put("success", true)
        .put("data", data)
        .put("timestamp", System.currentTimeMillis());

    ctx.response()
        .putHeader("content-type", "application/json")
        .end(response.encode());
  }

  /**
   * 发送成功响应（带消息）
   * 
   * @param ctx     路由上下文
   * @param message 成功消息
   * @param data    响应数据
   */
  protected void sendSuccess(RoutingContext ctx, String message, JsonObject data) {
    JsonObject response = new JsonObject()
        .put("success", true)
        .put("message", message)
        .put("data", data)
        .put("timestamp", System.currentTimeMillis());

    ctx.response()
        .putHeader("content-type", "application/json")
        .end(response.encode());
  }

  /**
   * 渲染Mustache模板
   * 
   * @param templateName 模板名称
   * @param data         模板数据
   * @return 渲染后的HTML字符串
   */
  protected String renderTemplate(String templateName, Map<String, Object> data) {
    String templatePath = "/auth-plugin/templates/" + templateName;

    try (InputStream is = getClass().getResourceAsStream(templatePath)) {
      if (is == null) {
        throw new RuntimeException("Template not found: " + templatePath);
      }

      Mustache mustache = mustacheFactory.compile(
          new java.io.InputStreamReader(is, StandardCharsets.UTF_8),
          templateName);

      StringWriter writer = new StringWriter();
      mustache.execute(writer, data).flush();
      return writer.toString();

    } catch (Exception e) {
      LOG.error("Error rendering template: " + templateName, e);
      throw new RuntimeException("Template rendering error", e);
    }
  }

  /**
   * 从认证上下文获取当前用户ID
   * 
   * @param ctx 路由上下文
   * @return 用户ID，如果未认证则返回null
   */
  protected String getCurrentUserId(RoutingContext ctx) {
    // 从认证拦截器设置的上下文中获取用户ID
    String userId = ctx.get("userId");
    return userId;
  }

  /**
   * 从认证上下文获取当前用户角色
   * 
   * @param ctx 路由上下文
   * @return 用户角色，如果未认证则返回null
   */
  protected String getCurrentUserRole(RoutingContext ctx) {
    // 从认证拦截器设置的上下文中获取用户角色
    String role = ctx.get("userRole");
    return role;
  }

  /**
   * 从请求头获取Bearer Token
   * 
   * @param ctx 路由上下文
   * @return Token字符串，如果不存在则返回null
   */
  protected String getBearerToken(RoutingContext ctx) {
    String authHeader = ctx.request().getHeader("Authorization");

    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      return authHeader.substring(7);
    }

    return null;
  }

  /**
   * 验证请求体是否存在
   * 
   * @param ctx 路由上下文
   * @return 如果请求体有效返回true，否则发送错误响应并返回false
   */
  protected boolean validateRequestBody(RoutingContext ctx) {
    JsonObject body = ctx.getBodyAsJson();

    if (body == null || body.isEmpty()) {
      sendError(ctx, 400, "Request body is required");
      return false;
    }

    return true;
  }

  /**
   * 获取客户端IP地址
   * 
   * @param ctx 路由上下文
   * @return 客户端IP地址
   */
  protected String getClientIp(RoutingContext ctx) {
    // 首先检查X-Forwarded-For头（用于代理情况）
    String xForwardedFor = ctx.request().getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
      // 取第一个IP（真实客户端IP）
      return xForwardedFor.split(",")[0].trim();
    }

    // 检查X-Real-IP头
    String xRealIp = ctx.request().getHeader("X-Real-IP");
    if (xRealIp != null && !xRealIp.isEmpty()) {
      return xRealIp.trim();
    }

    // 使用远程地址
    return ctx.request().remoteAddress().host();
  }

  /**
   * 获取用户代理字符串
   * 
   * @param ctx 路由上下文
   * @return User-Agent字符串
   */
  protected String getUserAgent(RoutingContext ctx) {
    String userAgent = ctx.request().getHeader("User-Agent");
    return userAgent != null ? userAgent : "Unknown";
  }
}