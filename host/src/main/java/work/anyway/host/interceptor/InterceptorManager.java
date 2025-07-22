package work.anyway.host.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import work.anyway.annotations.Interceptor;
import work.anyway.annotations.InterceptorComponent;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 拦截器管理器
 * 负责管理所有拦截器的注册和获取
 */
@Component
public class InterceptorManager {
  private static final Logger LOG = LoggerFactory.getLogger(InterceptorManager.class);

  // 存储所有拦截器
  private final List<InterceptorInfo> interceptors = new ArrayList<>();

  // 缓存方法对应的拦截器列表
  private final ConcurrentHashMap<String, List<Interceptor>> interceptorCache = new ConcurrentHashMap<>();

  /**
   * 注册拦截器
   */
  public void registerInterceptor(Interceptor interceptor) {
    InterceptorComponent annotation = interceptor.getClass().getAnnotation(InterceptorComponent.class);
    if (annotation != null) {
      String name = annotation.name();
      int order = annotation.order();
      String description = annotation.description();

      InterceptorInfo info = new InterceptorInfo(interceptor, name, description, order);
      interceptors.add(info);

      // 按顺序排序
      interceptors.sort(Comparator.comparingInt(InterceptorInfo::getOrder));

      // 清空缓存
      interceptorCache.clear();

      LOG.info("Registered interceptor: {} (order: {}, description: {})", name, order, description);
    } else {
      // 没有注解的拦截器使用默认配置
      String name = interceptor.getName();
      int order = interceptor.getOrder();

      InterceptorInfo info = new InterceptorInfo(interceptor, name, "No description", order);
      interceptors.add(info);
      interceptors.sort(Comparator.comparingInt(InterceptorInfo::getOrder));
      interceptorCache.clear();

      LOG.info("Registered interceptor: {} (order: {})", name, order);
    }
  }

  /**
   * 获取适用于指定方法的拦截器列表
   */
  public List<Interceptor> getApplicableInterceptors(Class<?> controllerClass, Method method) {
    String cacheKey = controllerClass.getName() + "#" + method.getName();

    return interceptorCache.computeIfAbsent(cacheKey, k -> {
      List<Interceptor> applicable = new ArrayList<>();

      // 检查方法级别的 @Intercepted 注解
      work.anyway.annotations.Intercepted methodAnnotation = method
          .getAnnotation(work.anyway.annotations.Intercepted.class);
      if (methodAnnotation != null) {
        addInterceptorsFromAnnotation(methodAnnotation, applicable);
      }

      // 检查类级别的 @Intercepted 注解
      work.anyway.annotations.Intercepted classAnnotation = controllerClass
          .getAnnotation(work.anyway.annotations.Intercepted.class);
      if (classAnnotation != null) {
        addInterceptorsFromAnnotation(classAnnotation, applicable);
      }

      // 如果没有任何注解，返回空列表
      if (methodAnnotation == null && classAnnotation == null) {
        return new ArrayList<>();
      }

      // 去重并排序
      return applicable.stream()
          .distinct()
          .sorted(Comparator.comparingInt(Interceptor::getOrder))
          .collect(Collectors.toList());
    });
  }

  private void addInterceptorsFromAnnotation(work.anyway.annotations.Intercepted annotation,
      List<Interceptor> applicable) {
    String[] names = annotation.value();

    if (names.length == 0) {
      // 没有指定名称，使用所有拦截器
      interceptors.forEach(info -> applicable.add(info.getInterceptor()));
    } else {
      // 使用指定的拦截器
      for (String name : names) {
        interceptors.stream()
            .filter(info -> info.getName().equals(name))
            .map(InterceptorInfo::getInterceptor)
            .findFirst()
            .ifPresent(applicable::add);
      }
    }
  }

  /**
   * 获取所有已注册的拦截器信息
   */
  public List<InterceptorInfo> getAllInterceptors() {
    return new ArrayList<>(interceptors);
  }

  /**
   * 拦截器信息类
   */
  public static class InterceptorInfo {
    private final Interceptor interceptor;
    private final String name;
    private final String description;
    private final int order;

    public InterceptorInfo(Interceptor interceptor, String name, String description, int order) {
      this.interceptor = interceptor;
      this.name = name;
      this.description = description;
      this.order = order;
    }

    public Interceptor getInterceptor() {
      return interceptor;
    }

    public String getName() {
      return name;
    }

    public String getDescription() {
      return description;
    }

    public int getOrder() {
      return order;
    }
  }
}