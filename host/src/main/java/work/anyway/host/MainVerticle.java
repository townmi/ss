package work.anyway.host;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.anyway.interfaces.plugin.Plugin;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public class MainVerticle extends AbstractVerticle {

  private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);
  private final List<Plugin> loadedPlugins = new ArrayList<>();
  private ServiceContainer serviceContainer;

  @Override
  public void start(Promise<Void> startPromise) {
    Router router = Router.router(vertx);

    // 添加全局 BodyHandler
    router.route().handler(BodyHandler.create());

    // 加载插件
    if (ConfigLoader.getBoolean("plugins.enabled", true)) {
      loadPlugins(router);
    }

    // 启动 HTTP 服务器
    int port = ConfigLoader.getInt("http.port", 8080);
    String host = ConfigLoader.getString("http.host", "0.0.0.0");

    vertx.createHttpServer()
        .requestHandler(router)
        .listen(port, host, result -> {
          if (result.succeeded()) {
            LOG.info("HTTP server started on {}:{}", host, port);
            LOG.info("Loaded {} plugins", loadedPlugins.size());
            startPromise.complete();
          } else {
            startPromise.fail(result.cause());
          }
        });
  }

  private void loadPlugins(Router router) {
    try {
      // 清空已加载插件列表
      loadedPlugins.clear();

      // 先加载服务
      List<URL> allUrls = new ArrayList<>();

      // 加载服务 JARs
      String serviceDir = ConfigLoader.getString("services.directory", "libs/services");
      File servicesDirectory = new File(serviceDir);
      if (servicesDirectory.exists() && servicesDirectory.isDirectory()) {
        LOG.info("Loading services from: {}", servicesDirectory.getAbsolutePath());
        File[] serviceJars = servicesDirectory.listFiles((d, name) -> name.endsWith(".jar"));
        if (serviceJars != null) {
          for (File jar : serviceJars) {
            allUrls.add(jar.toURI().toURL());
            LOG.info("Found service JAR: {}", jar.getName());
          }
        }
      }

      // 加载插件 JARs
      String pluginDir = ConfigLoader.getString("plugins.directory", "libs/plugins");
      LOG.info("Loading plugins from: {}", pluginDir);

      File dir = new File(pluginDir);
      if (!dir.exists() || !dir.isDirectory()) {
        LOG.warn("Plugin directory not found: {}", dir.getAbsolutePath());
        return;
      }

      File[] jarFiles = dir.listFiles((d, name) -> name.endsWith(".jar"));
      if (jarFiles == null || jarFiles.length == 0) {
        LOG.info("No plugin JARs found");
        return;
      }

      for (File jarFile : jarFiles) {
        allUrls.add(jarFile.toURI().toURL());
        LOG.info("Found plugin JAR: {}", jarFile.getName());
      }

      // 创建包含所有 JARs 的 ClassLoader
      URLClassLoader classLoader = new URLClassLoader(
          allUrls.toArray(new URL[0]),
          this.getClass().getClassLoader());

      // 创建服务容器
      serviceContainer = new ServiceContainer(classLoader);

      // 注册 Vertx 实例到 ServiceContainer
      serviceContainer.register(Vertx.class, vertx);
      LOG.info("Registered Vertx instance to ServiceContainer");

      ServiceLoader<Plugin> serviceLoader = ServiceLoader.load(Plugin.class, classLoader);

      // 先找到并初始化 SystemPlugin，确保它先被加载
      Plugin systemPlugin = null;
      List<Plugin> otherPlugins = new ArrayList<>();

      for (Plugin plugin : serviceLoader) {
        if (plugin.getClass().getName().endsWith("SystemPlugin")) {
          systemPlugin = plugin;
        } else {
          otherPlugins.add(plugin);
        }
      }

      // 如果找到 SystemPlugin，先初始化它并清空插件列表
      if (systemPlugin != null) {
        try {
          LOG.info("Initializing system plugin first: {} v{}", systemPlugin.getName(), systemPlugin.getVersion());

          // 清空插件列表
          Class<?> systemPluginClass = systemPlugin.getClass();
          systemPluginClass.getMethod("clearPlugins").invoke(null);

          // 注入服务并初始化
          injectServices(systemPlugin);
          systemPlugin.initialize(router, serviceContainer);
          loadedPlugins.add(systemPlugin);

          LOG.info("System plugin loaded successfully");
        } catch (Exception e) {
          LOG.error("Failed to initialize system plugin", e);
        }
      }

      // 然后加载其他插件
      for (Plugin plugin : otherPlugins) {
        try {
          LOG.info("Initializing plugin: {} v{}", plugin.getName(), plugin.getVersion());

          // 如果插件需要服务注入，使用反射设置
          injectServices(plugin);

          plugin.initialize(router, serviceContainer);
          loadedPlugins.add(plugin);

          // 注册到 SystemPlugin（如果存在）
          if (systemPlugin != null) {
            try {
              systemPlugin.getClass().getMethod("registerPlugin", Plugin.class).invoke(null, plugin);
            } catch (Exception e) {
              LOG.error("Failed to register plugin to SystemPlugin", e);
            }
          }

          LOG.info("Plugin loaded successfully: {}", plugin.getName());
        } catch (Exception e) {
          LOG.error("Failed to initialize plugin: " + plugin.getName(), e);
        }
      }

    } catch (Exception e) {
      LOG.error("Failed to load plugins", e);
    }
  }

  private void injectServices(Plugin plugin) {
    try {
      // 使用反射查找所有需要注入的字段
      Class<?> clazz = plugin.getClass();

      java.lang.reflect.Field[] fields = clazz.getDeclaredFields();

      for (java.lang.reflect.Field field : fields) {
        // 查找类型为 Service 结尾的字段
        if (field.getType().getName().endsWith("Service")) {
          try {
            field.setAccessible(true);
            Object currentValue = field.get(plugin);

            // 如果字段为空，尝试注入
            if (currentValue == null) {
              Object service = serviceContainer.getService(field.getType().getName());
              if (service != null) {
                field.set(plugin, service);
                LOG.info("Injected {} into plugin {}", field.getType().getSimpleName(), plugin.getName());
              } else {
                LOG.warn("No service found for type: {}", field.getType().getName());
              }
            }
          } catch (Exception e) {
            LOG.warn("Failed to inject service {} into plugin {}: {}",
                field.getType().getSimpleName(), plugin.getName(), e.getMessage());
          }
        }
      }
    } catch (Exception e) {
      LOG.error("Failed to inject services into plugin " + plugin.getName(), e);
    } catch (Error e) {
      LOG.error("Error while injecting services into plugin " + plugin.getName(), e);
      throw e;
    }
  }

  public static void main(String[] args) {
    // 设置 Vert.x 选项
    System.setProperty("vertx.logger-delegate-factory-class-name",
        "io.vertx.core.logging.SLF4JLogDelegateFactory");

    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(new MainVerticle());
  }
}