package work.anyway.annotations;

import io.vertx.ext.web.RoutingContext;

/**
 * 拦截器接口
 * 业务插件可以实现此接口来创建自定义拦截器
 */
public interface Interceptor {

  /**
   * 前置处理
   * 
   * @param ctx 路由上下文
   * @return true 继续处理，false 中断处理
   */
  boolean preHandle(RoutingContext ctx);

  /**
   * 后置处理（在业务方法执行后，响应发送前）
   * 
   * @param ctx    路由上下文
   * @param result 方法执行结果
   */
  default void postHandle(RoutingContext ctx, Object result) {
    // 默认空实现
  }

  /**
   * 完成后处理（在响应发送后或异常发生时）
   * 
   * @param ctx 路由上下文
   * @param ex  异常（如果有）
   */
  default void afterCompletion(RoutingContext ctx, Exception ex) {
    // 默认空实现
  }

  /**
   * 获取拦截器名称
   * 
   * @return 拦截器名称
   */
  String getName();

  /**
   * 获取执行顺序
   * 
   * @return 执行顺序，数字越小优先级越高
   */
  default int getOrder() {
    return 0;
  }
}