package work.anyway.annotations;

import io.vertx.ext.web.RoutingContext;

/**
 * 模板处理器接口
 * 用于处理和转换模板内容，支持主题等扩展功能
 * 
 * @author 作者名
 * @since 1.0.0
 */
public interface TemplateProcessor {

  /**
   * 处理模板内容
   * 
   * @param content 原始内容
   * @param ctx     路由上下文
   * @return 处理后的内容
   */
  String process(String content, RoutingContext ctx);

  /**
   * 获取处理器优先级
   * 数值越小优先级越高
   * 
   * @return 优先级
   */
  default int getOrder() {
    return 0;
  }

  /**
   * 是否启用此处理器
   * 
   * @return 是否启用
   */
  default boolean isEnabled() {
    return true;
  }
}