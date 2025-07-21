package work.anyway.host;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import work.anyway.annotations.PluginInfo;
import work.anyway.annotations.PluginRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * 插件注册表实现，管理所有已加载的插件
 */
@Component
public class PluginRegistryImpl implements PluginRegistry {

  @Autowired(required = false)
  private RouteMappingBeanPostProcessor routeProcessor;

  /**
   * 获取所有已注册的插件
   */
  @Override
  public List<PluginInfo> getAllPlugins() {
    if (routeProcessor != null) {
      return routeProcessor.getPlugins();
    }
    return new ArrayList<>();
  }

  /**
   * 根据名称查找插件
   */
  @Override
  public PluginInfo findPluginByName(String name) {
    return getAllPlugins().stream()
        .filter(p -> p.getName().equals(name))
        .findFirst()
        .orElse(null);
  }
}