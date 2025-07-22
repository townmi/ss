package work.anyway.packages.system.plugin.interceptor;

import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.anyway.annotations.Interceptor;
import work.anyway.annotations.InterceptorComponent;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 操作日志拦截器
 * 记录用户的操作行为和业务逻辑
 */
@InterceptorComponent(name = "OperationLog", description = "Operation logging interceptor for business actions", order = 50 // 业务日志拦截器优先级中等
)
public class OperationLogInterceptor implements Interceptor {
  private static final Logger LOG = LoggerFactory.getLogger(OperationLogInterceptor.class);
  private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

  @Override
  public boolean preHandle(RoutingContext ctx) {
    // 记录操作开始时间
    ctx.put("operationStartTime", System.currentTimeMillis());
    ctx.put("operationTimestamp", LocalDateTime.now().format(FORMATTER));

    String userId = ctx.get("userId");
    String path = ctx.request().path();

    // 只记录API操作，跳过页面访问
    if (path.startsWith("/api/")) {
      LOG.info("Operation started - User: {}, Method: {}, Path: {}, Timestamp: {}",
          userId != null ? userId : "anonymous",
          ctx.request().method(),
          path,
          ctx.get("operationTimestamp"));
    }

    return true;
  }

  @Override
  public void postHandle(RoutingContext ctx, Object result) {
    Long startTime = ctx.get("operationStartTime");
    String path = ctx.request().path();

    if (startTime != null && path.startsWith("/api/")) {
      long duration = System.currentTimeMillis() - startTime;
      String userId = ctx.get("userId");

      LOG.info("Operation processed - User: {}, Path: {}, Duration: {}ms, Result: {}",
          userId != null ? userId : "anonymous",
          path,
          duration,
          result != null ? result.getClass().getSimpleName() : "void");
    }
  }

  @Override
  public void afterCompletion(RoutingContext ctx, Exception ex) {
    Long startTime = ctx.get("operationStartTime");
    String path = ctx.request().path();

    if (startTime != null && path.startsWith("/api/")) {
      long duration = System.currentTimeMillis() - startTime;
      String userId = ctx.get("userId");

      if (ex != null) {
        LOG.error("Operation failed - User: {}, Path: {}, Duration: {}ms, Error: {}",
            userId != null ? userId : "anonymous",
            path,
            duration,
            ex.getMessage());
      } else {
        LOG.info("Operation completed - User: {}, Path: {}, Duration: {}ms, Status: {}",
            userId != null ? userId : "anonymous",
            path,
            duration,
            ctx.response().getStatusCode());
      }
    }
  }

  @Override
  public String getName() {
    return "OperationLog";
  }

  @Override
  public int getOrder() {
    return 50;
  }
}