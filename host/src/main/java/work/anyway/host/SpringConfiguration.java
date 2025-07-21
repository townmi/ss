package work.anyway.host;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import work.anyway.annotations.PluginRegistry;

/**
 * Spring 配置类
 */
@Configuration
public class SpringConfiguration {

  /**
   * 创建插件注册表 Bean
   */
  @Bean
  @Primary
  public PluginRegistry pluginRegistry() {
    return new PluginRegistryImpl();
  }
}