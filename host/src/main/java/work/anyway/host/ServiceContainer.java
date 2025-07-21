package work.anyway.host;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.anyway.interfaces.plugin.ServiceRegistry;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple service container that creates and caches service instances on demand
 */
public class ServiceContainer implements ServiceRegistry {

  private static final Logger LOG = LoggerFactory.getLogger(ServiceContainer.class);
  private final Map<String, Object> services = new ConcurrentHashMap<>();
  private final ClassLoader pluginClassLoader;

  public ServiceContainer(ClassLoader pluginClassLoader) {
    this.pluginClassLoader = pluginClassLoader;
  }

  /**
   * Get or create a service instance by class name
   */
  @SuppressWarnings("unchecked")
  public <T> T getService(String className) {
    return (T) services.computeIfAbsent(className, this::createService);
  }

  /**
   * Get or create a service instance by class
   */
  public <T> T getService(Class<T> serviceClass) {
    return getService(serviceClass.getName());
  }

  /**
   * Register a service instance manually
   */
  @Override
  public <T> void register(Class<T> serviceClass, T instance) {
    services.put(serviceClass.getName(), instance);
    LOG.info("Registered service instance: {} -> {}", serviceClass.getName(), instance.getClass().getName());
  }

  /**
   * Lookup a service by its interface
   */
  @Override
  public <T> Optional<T> lookup(Class<T> serviceClass) {
    Object service = services.get(serviceClass.getName());
    if (service != null) {
      return Optional.of(serviceClass.cast(service));
    }
    // Try to create the service if not found
    try {
      T createdService = getService(serviceClass);
      return Optional.ofNullable(createdService);
    } catch (Exception e) {
      LOG.debug("Service not found: {}", serviceClass.getName());
      return Optional.empty();
    }
  }

  private Object createService(String className) {
    try {
      LOG.info("Creating service instance for: {}", className);

      // 首先尝试查找实现类
      String implClassName = className + "Impl";
      Class<?> clazz = null;

      // 尝试常见的实现类命名模式
      String[] possibleNames = {
          implClassName,
          className.replace(".interfaces.", ".packages.") + "Impl",
          className.replace("Service", "ServiceImpl")
      };

      for (String name : possibleNames) {
        try {
          clazz = pluginClassLoader.loadClass(name);
          LOG.info("Found implementation class: {}", name);
          break;
        } catch (ClassNotFoundException e) {
          // 继续尝试下一个
        }
      }

      // 如果没找到实现类，尝试直接加载原类名
      if (clazz == null) {
        clazz = pluginClassLoader.loadClass(className);
      }

      // 创建实例
      Object instance = clazz.getDeclaredConstructor().newInstance();
      LOG.info("Created service instance: {} -> {}", className, instance.getClass().getName());

      // 注入依赖
      injectDependencies(instance);

      return instance;

    } catch (Exception e) {
      LOG.error("Failed to create service: " + className, e);
      return null;
    }
  }

  /**
   * 注入服务的依赖
   */
  private void injectDependencies(Object instance) {
    Class<?> clazz = instance.getClass();

    // 查找所有字段
    java.lang.reflect.Field[] fields = clazz.getDeclaredFields();

    for (java.lang.reflect.Field field : fields) {
      // 查找类型为 Service 结尾的字段
      if (field.getType().getName().endsWith("Service")) {
        try {
          field.setAccessible(true);
          Object currentValue = field.get(instance);

          // 如果字段为空，尝试注入
          if (currentValue == null) {
            Object service = getService(field.getType().getName());
            if (service != null) {
              field.set(instance, service);
              LOG.info("Injected {} into {}", field.getType().getSimpleName(), instance.getClass().getSimpleName());
            } else {
              LOG.warn("No service found for type: {}", field.getType().getName());
            }
          }
        } catch (Exception e) {
          LOG.warn("Failed to inject service {} into {}: {}",
              field.getType().getSimpleName(), instance.getClass().getSimpleName(), e.getMessage());
        }
      }
    }

    // 查找 setter 方法
    java.lang.reflect.Method[] methods = clazz.getMethods();
    for (java.lang.reflect.Method method : methods) {
      if (method.getName().startsWith("set") &&
          method.getParameterCount() == 1 &&
          method.getParameterTypes()[0].getName().endsWith("Service")) {
        try {
          Object service = getService(method.getParameterTypes()[0].getName());
          if (service != null) {
            method.invoke(instance, service);
            LOG.info("Injected {} via setter into {}",
                method.getParameterTypes()[0].getSimpleName(),
                instance.getClass().getSimpleName());
          }
        } catch (Exception e) {
          LOG.warn("Failed to inject service via setter {} into {}: {}",
              method.getName(), instance.getClass().getSimpleName(), e.getMessage());
        }
      }
    }
  }
}