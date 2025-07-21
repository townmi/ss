package work.anyway.host;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;
import work.anyway.annotations.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Bean 后处理器，负责扫描 @Controller 和 @RequestMapping 注解并注册路由
 */
@Component
public class RouteMappingBeanPostProcessor implements BeanPostProcessor, ApplicationContextAware {
  private static final Logger LOG = LoggerFactory.getLogger(RouteMappingBeanPostProcessor.class);

  private ApplicationContext applicationContext;
  private Router router;
  private Vertx vertx;

  // 存储所有控制器和插件信息
  private final List<ControllerInfo> controllers = new ArrayList<>();
  private final List<PluginInfo> plugins = new ArrayList<>();

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.applicationContext = applicationContext;
  }

  @Autowired(required = false)
  public void setVertx(Vertx vertx) {
    this.vertx = vertx;
  }

  public void setRouter(Router router) {
    this.router = router;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
    Class<?> beanClass = bean.getClass();

    // 处理 @Controller 注解
    if (beanClass.isAnnotationPresent(Controller.class)) {
      LOG.debug("Found controller: {}", beanClass.getName());
      controllers.add(new ControllerInfo(bean, beanClass));
    }

    // 处理 @Plugin 注解
    if (beanClass.isAnnotationPresent(Plugin.class)) {
      Plugin plugin = beanClass.getAnnotation(Plugin.class);
      LOG.info("Found plugin: {} v{}", plugin.name(), plugin.version());

      // 创建 PluginInfo 对象
      PluginInfo pluginInfo = new PluginInfo(
          plugin.name(),
          plugin.version(),
          plugin.description(),
          plugin.icon(),
          plugin.mainPagePath());
      plugins.add(pluginInfo);
    }

    return bean;
  }

  /**
   * 注册所有收集到的路由
   */
  public void registerAllRoutes() {
    if (router == null || vertx == null) {
      throw new IllegalStateException("Router and Vertx must be set before registering routes");
    }

    for (ControllerInfo controller : controllers) {
      registerControllerRoutes(controller);
    }

    LOG.info("Registered {} controllers with routes", controllers.size());
    LOG.info("Found {} plugins", plugins.size());
  }

  private void registerControllerRoutes(ControllerInfo controllerInfo) {
    Object controller = controllerInfo.instance;
    Class<?> controllerClass = controllerInfo.clazz;

    // 获取类级别的 @RequestMapping
    RequestMapping classMapping = controllerClass.getAnnotation(RequestMapping.class);
    String basePath = "";
    if (classMapping != null) {
      String[] paths = classMapping.value().length > 0 ? classMapping.value() : classMapping.path();
      if (paths.length > 0) {
        basePath = paths[0];
        // 确保基础路径不以 / 结尾（除非是根路径）
        if (basePath.length() > 1 && basePath.endsWith("/")) {
          basePath = basePath.substring(0, basePath.length() - 1);
        }
      }
    }

    // 扫描所有方法
    for (Method method : controllerClass.getDeclaredMethods()) {
      // 检查各种映射注解
      if (method.isAnnotationPresent(RequestMapping.class)) {
        RequestMapping mapping = method.getAnnotation(RequestMapping.class);
        registerMethodRoute(basePath, mapping, controller, method);
      } else if (method.isAnnotationPresent(GetMapping.class)) {
        GetMapping mapping = method.getAnnotation(GetMapping.class);
        registerGetRoute(basePath, mapping, controller, method);
      } else if (method.isAnnotationPresent(PostMapping.class)) {
        PostMapping mapping = method.getAnnotation(PostMapping.class);
        registerPostRoute(basePath, mapping, controller, method);
      }
    }
  }

  private void registerMethodRoute(String basePath, RequestMapping mapping, Object controller, Method method) {
    String[] paths = mapping.value().length > 0 ? mapping.value() : mapping.path();
    String[] methods = mapping.method();

    for (String path : paths) {
      String fullPath = buildFullPath(basePath, path);

      if (methods.length == 0) {
        // 没有指定 HTTP 方法，注册所有方法
        Route route = router.route(fullPath);
        route.handler(ctx -> handleRequest(ctx, controller, method));
        LOG.info("Registered route: ALL {} -> {}#{}", fullPath, controller.getClass().getSimpleName(),
            method.getName());
      } else {
        // 注册指定的 HTTP 方法
        for (String httpMethodStr : methods) {
          HttpMethod httpMethod = HttpMethod.valueOf(httpMethodStr.toUpperCase());
          Route route = createRoute(httpMethod, fullPath);
          route.handler(ctx -> handleRequest(ctx, controller, method));
          LOG.info("Registered route: {} {} -> {}#{}", httpMethod, fullPath, controller.getClass().getSimpleName(),
              method.getName());
        }
      }
    }
  }

  private void registerGetRoute(String basePath, GetMapping mapping, Object controller, Method method) {
    String[] paths = mapping.value().length > 0 ? mapping.value() : mapping.path();

    for (String path : paths) {
      String fullPath = buildFullPath(basePath, path);
      Route route = router.get(fullPath);
      route.handler(ctx -> handleRequest(ctx, controller, method));
      LOG.info("Registered route: GET {} -> {}#{}", fullPath, controller.getClass().getSimpleName(), method.getName());
    }
  }

  private void registerPostRoute(String basePath, PostMapping mapping, Object controller, Method method) {
    String[] paths = mapping.value().length > 0 ? mapping.value() : mapping.path();

    for (String path : paths) {
      String fullPath = buildFullPath(basePath, path);
      Route route = router.post(fullPath);
      route.handler(ctx -> handleRequest(ctx, controller, method));
      LOG.info("Registered route: POST {} -> {}#{}", fullPath, controller.getClass().getSimpleName(), method.getName());
    }
  }

  private Route createRoute(HttpMethod method, String path) {
    if (method == HttpMethod.GET) {
      return router.get(path);
    } else if (method == HttpMethod.POST) {
      return router.post(path);
    } else if (method == HttpMethod.PUT) {
      return router.put(path);
    } else if (method == HttpMethod.DELETE) {
      return router.delete(path);
    } else if (method == HttpMethod.PATCH) {
      return router.patch(path);
    } else if (method == HttpMethod.HEAD) {
      return router.head(path);
    } else if (method == HttpMethod.OPTIONS) {
      return router.options(path);
    } else {
      return router.route(path).method(method);
    }
  }

  /**
   * 构建完整路径，处理路径分隔符
   */
  private String buildFullPath(String basePath, String path) {
    // 如果路径为空，返回基础路径
    if (path == null || path.isEmpty()) {
      return basePath.isEmpty() ? "/" : basePath;
    }

    // 如果基础路径为空或为根路径
    if (basePath == null || basePath.isEmpty() || basePath.equals("/")) {
      // 确保路径以 / 开头
      return path.startsWith("/") ? path : "/" + path;
    }

    // 确保只有一个 / 分隔符
    if (path.startsWith("/")) {
      return basePath + path;
    } else {
      return basePath + "/" + path;
    }
  }

  private void handleRequest(RoutingContext ctx, Object controller, Method method) {
    // 在 Vert.x 的工作线程中执行控制器方法
    vertx.executeBlocking(promise -> {
      try {
        // 判断方法参数
        Class<?>[] paramTypes = method.getParameterTypes();
        Object result;

        if (paramTypes.length == 0) {
          // 无参方法
          result = method.invoke(controller);
        } else if (paramTypes.length == 1 && paramTypes[0] == RoutingContext.class) {
          // 接受 RoutingContext 参数
          result = method.invoke(controller, ctx);
        } else {
          // 不支持的参数类型
          throw new IllegalArgumentException("Unsupported method parameters: " + method);
        }

        promise.complete(result);
      } catch (Exception e) {
        promise.fail(e);
      }
    }, res -> {
      if (res.succeeded()) {
        // 如果方法有返回值，可以在这里处理
        // 目前假设控制器方法自己处理响应
      } else {
        LOG.error("Error handling request", res.cause());
        ctx.fail(res.cause());
      }
    });
  }

  /**
   * 获取所有插件信息
   */
  public List<PluginInfo> getPlugins() {
    return new ArrayList<>(plugins);
  }

  // 内部类：控制器信息
  private static class ControllerInfo {
    final Object instance;
    final Class<?> clazz;

    ControllerInfo(Object instance, Class<?> clazz) {
      this.instance = instance;
      this.clazz = clazz;
    }
  }
}