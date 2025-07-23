package work.anyway.host;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import static work.anyway.host.Constants.*;

/**
 * 主 Verticle，负责初始化 Spring Container 和启动 HTTP 服务器
 */
public class MainVerticle extends AbstractVerticle {

  private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);
  private AnnotationConfigApplicationContext springContext;

  @Override
  public void start(Promise<Void> startPromise) {
    try {
      // 设置 Vertx 实例到 Spring 配置中
      SpringConfiguration.setVertxInstance(vertx);

      // 初始化 Spring Container
      initializeSpringContainer();

      // 创建 Router
      Router router = Router.router(vertx);

      // 添加全局 BodyHandler
      router.route().handler(BodyHandler.create());

      // 注册路由
      registerRoutes(router);

      // 启动 HTTP 服务器
      startHttpServer(router, startPromise);

    } catch (Exception e) {
      LOG.error("Failed to start MainVerticle", e);
      startPromise.fail(e);
    }
  }

  @Override
  public void stop() throws Exception {
    if (springContext != null) {
      springContext.close();
    }
  }

  private void initializeSpringContainer() throws Exception {
    LOG.info("Initializing Spring Container...");

    springContext = new AnnotationConfigApplicationContext();

    // 注册 Vertx 实例到 Spring
    springContext.getBeanFactory().registerSingleton("vertx", vertx);

    // 加载外部 JAR 文件
    ClassLoader classLoader = loadExternalJars();
    springContext.setClassLoader(classLoader);

    // 注册配置类
    springContext.register(SpringConfiguration.class);
    springContext.register(RouteMappingBeanPostProcessor.class);

    // 扫描包
    LOG.debug("Scanning packages for components...");
    ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(springContext);

    // 从配置中获取要扫描的包，支持多个包用逗号分隔
    String packagesToScan = ConfigLoader.getString(CONFIG_SCAN_PACKAGES, DEFAULT_SCAN_PACKAGES);
    String[] packages = packagesToScan.split(",");
    for (String pkg : packages) {
      String trimmedPkg = pkg.trim();
      if (!trimmedPkg.isEmpty()) {
        LOG.debug("Scanning package: {}", trimmedPkg);
        scanner.scan(trimmedPkg);
      }
    }

    // 刷新容器
    springContext.refresh();

    LOG.debug("Spring Container initialized with {} beans", springContext.getBeanDefinitionCount());
  }

  private ClassLoader loadExternalJars() throws Exception {
    List<URL> urls = new ArrayList<>();

    // 加载服务 JARs
    String serviceDir = ConfigLoader.getString(CONFIG_SERVICES_DIR, DEFAULT_SERVICES_DIR);
    File servicesDirectory = new File(serviceDir);
    if (servicesDirectory.exists() && servicesDirectory.isDirectory()) {
      LOG.debug(LOG_LOADING_FROM, "services", servicesDirectory.getAbsolutePath());
      File[] serviceJars = servicesDirectory.listFiles((d, name) -> name.endsWith(JAR_EXTENSION));
      if (serviceJars != null) {
        for (File jar : serviceJars) {
          urls.add(jar.toURI().toURL());
          LOG.debug(LOG_FOUND_JAR, "service", jar.getName());
        }
      }
    }

    // 加载插件 JARs
    String pluginDir = ConfigLoader.getString(CONFIG_PLUGINS_DIR, DEFAULT_PLUGINS_DIR);
    File pluginsDirectory = new File(pluginDir);
    if (pluginsDirectory.exists() && pluginsDirectory.isDirectory()) {
      LOG.debug(LOG_LOADING_FROM, "plugins", pluginsDirectory.getAbsolutePath());
      File[] pluginJars = pluginsDirectory.listFiles((d, name) -> name.endsWith(JAR_EXTENSION));
      if (pluginJars != null) {
        for (File jar : pluginJars) {
          urls.add(jar.toURI().toURL());
          LOG.debug(LOG_FOUND_JAR, "plugin", jar.getName());
        }
      }
    }

    // 创建 URLClassLoader
    return new URLClassLoader(
        urls.toArray(new URL[0]),
        Thread.currentThread().getContextClassLoader());
  }

  private void registerRoutes(Router router) {
    // 从 Spring Container 获取 RouteMappingBeanPostProcessor
    RouteMappingBeanPostProcessor routeProcessor = springContext.getBean(RouteMappingBeanPostProcessor.class);

    // 设置 Router
    routeProcessor.setRouter(router);

    // 触发路由注册
    routeProcessor.registerAllRoutes();

    LOG.info("All routes registered");
  }

  private void startHttpServer(Router router, Promise<Void> startPromise) {
    int port = ConfigLoader.getInt(CONFIG_HTTP_PORT, DEFAULT_HTTP_PORT);
    String host = ConfigLoader.getString(CONFIG_HTTP_HOST, DEFAULT_HTTP_HOST);

    vertx.createHttpServer()
        .requestHandler(router)
        .listen(port, host, result -> {
          if (result.succeeded()) {
            LOG.info(LOG_HTTP_SERVER_STARTED, host, port);
            startPromise.complete();
          } else {
            startPromise.fail(result.cause());
          }
        });
  }
}