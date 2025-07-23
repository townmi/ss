package work.anyway.host;

import io.vertx.core.Vertx;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import work.anyway.annotations.PluginRegistry;

/**
 * Spring 配置类
 */
@Configuration
public class SpringConfiguration {

  private static Vertx vertxInstance;

  /**
   * 设置 Vertx 实例（由 MainVerticle 调用）
   */
  public static void setVertxInstance(Vertx vertx) {
    vertxInstance = vertx;
  }

  /**
   * 创建 Vertx Bean
   */
  @Bean
  public Vertx vertx() {
    return vertxInstance;
  }

  /**
   * 创建插件注册表 Bean
   */
  @Bean
  @Primary
  public PluginRegistry pluginRegistry() {
    return new PluginRegistryImpl();
  }
}