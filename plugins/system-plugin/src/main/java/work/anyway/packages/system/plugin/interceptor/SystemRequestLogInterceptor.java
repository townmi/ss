package work.anyway.packages.system.plugin.interceptor;

import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import work.anyway.annotations.Interceptor;
import work.anyway.annotations.InterceptorComponent;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 系统级请求日志拦截器
 * 记录所有HTTP请求的基本信息
 */
@InterceptorComponent(name = "SystemRequestLog", description = "System-level request logging interceptor", order = 10 // 系统级拦截器具有最高优先级
)
public class SystemRequestLogInterceptor implements Interceptor {
  private static final Logger LOG = LoggerFactory.getLogger(SystemRequestLogInterceptor.class);
  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

  @Override
  public boolean preHandle(RoutingContext ctx) {
    long startTime = System.currentTimeMillis();
    String requestId = UUID.randomUUID().toString().substring(0, 8);
    String timestamp = LocalDateTime.now().format(FORMATTER);

    // 设置到上下文中供其他拦截器使用
    ctx.put("systemRequestId", requestId);
    ctx.put("systemStartTime", startTime);

    // 设置 MDC 用于日志追踪
    MDC.put("requestId", requestId);

    // 记录请求开始
    LOG.info(
        "Request started - ID: {}, Method: {}, Path: {}, Query: {}, RemoteAddress: {}, UserAgent: {}, Timestamp: {}",
        requestId,
        ctx.request().method(),
        ctx.request().path(),
        ctx.request().query(),
        ctx.request().remoteAddress(),
        ctx.request().getHeader("User-Agent"),
        timestamp);

    return true;
  }

  @Override
  public void afterCompletion(RoutingContext ctx, Exception ex) {
    Long startTime = ctx.get("systemStartTime");
    String requestId = ctx.get("systemRequestId");

    if (startTime != null && requestId != null) {
      long duration = System.currentTimeMillis() - startTime;

      if (ex != null) {
        LOG.error("Request failed - ID: {}, Duration: {}ms, Error: {}",
            requestId, duration, ex.getMessage());
      } else {
        LOG.info("Request completed - ID: {}, Duration: {}ms, Status: {}, ContentLength: {}",
            requestId, duration, ctx.response().getStatusCode(), ctx.response().bytesWritten());
      }
    }

    // 清理 MDC
    MDC.clear();
  }

  @Override
  public String getName() {
    return "SystemRequestLog";
  }

  @Override
  public int getOrder() {
    return 10;
  }
}