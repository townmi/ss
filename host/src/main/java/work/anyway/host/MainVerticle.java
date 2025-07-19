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

    // 添加健康检查端点
    router.get("/health").handler(ctx -> {
      ctx.response()
          .putHeader("content-type", "application/json")
          .end("{\"status\":\"UP\",\"plugins\":" + loadedPlugins.size() + "}");
    });

    // 添加主页路由，显示所有插件
    router.get("/page/").handler(ctx -> {
      StringBuilder html = new StringBuilder();
      html.append("<!DOCTYPE html><html><head>");
      html.append("<meta charset='UTF-8'>");
      html.append("<title>插件管理系统</title>");
      html.append("<style>");
      html.append("* { margin: 0; padding: 0; box-sizing: border-box; }");
      html.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; ");
      html.append("background-color: #f5f5f5; color: #333; line-height: 1.6; }");
      html.append(".container { max-width: 1200px; margin: 0 auto; padding: 40px 20px; }");
      html.append(".hero { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); ");
      html.append("color: white; padding: 80px 20px; text-align: center; margin: -40px -20px 40px; }");
      html.append(".hero h1 { font-size: 48px; margin-bottom: 20px; }");
      html.append(".plugins-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); ");
      html.append("gap: 30px; }");
      html.append(".plugin-card { background: white; border-radius: 8px; padding: 30px; ");
      html.append("box-shadow: 0 2px 4px rgba(0,0,0,0.1); transition: transform 0.3s, box-shadow 0.3s; }");
      html.append(".plugin-card:hover { transform: translateY(-5px); box-shadow: 0 4px 12px rgba(0,0,0,0.15); }");
      html.append(".plugin-card h3 { color: #2c3e50; font-size: 24px; margin-bottom: 10px; }");
      html.append(".plugin-card p { color: #7f8c8d; margin-bottom: 20px; }");
      html.append(".plugin-card a { display: inline-block; padding: 10px 20px; background-color: #3498db; ");
      html.append("color: white; text-decoration: none; border-radius: 5px; transition: background-color 0.3s; }");
      html.append(".plugin-card a:hover { background-color: #2980b9; }");
      html.append("</style></head><body>");
      html.append("<div class='hero'><div class='container'>");
      html.append("<h1>插件管理系统</h1>");
      html.append("<p>已加载 ").append(loadedPlugins.size()).append(" 个插件</p>");
      html.append("</div></div>");
      html.append("<div class='container'>");
      html.append("<div class='plugins-grid'>");

      // 添加用户管理插件卡片
      if (loadedPlugins.stream().anyMatch(p -> p.getName().equals("User Plugin"))) {
        html.append("<div class='plugin-card'>");
        html.append("<h3>👤 用户管理插件</h3>");
        html.append("<p>管理系统用户，包括创建、查看、编辑用户信息</p>");
        html.append("<a href='/page/users/'>进入用户管理</a>");
        html.append("</div>");
      }

      // 添加权限管理插件卡片
      if (loadedPlugins.stream().anyMatch(p -> p.getName().equals("Auth Plugin"))) {
        html.append("<div class='plugin-card'>");
        html.append("<h3>🔐 权限管理插件</h3>");
        html.append("<p>管理用户权限，控制系统访问权限</p>");
        html.append("<a href='/page/auth/'>进入权限管理</a>");
        html.append("</div>");
      }

      html.append("</div></div></body></html>");

      ctx.response()
          .putHeader("content-type", "text/html; charset=utf-8")
          .end(html.toString());
    });

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

    try {
      URL[] urls = new URL[jarFiles.length];
      for (int i = 0; i < jarFiles.length; i++) {
        urls[i] = jarFiles[i].toURI().toURL();
        LOG.info("Found plugin JAR: {}", jarFiles[i].getName());
      }

      URLClassLoader classLoader = new URLClassLoader(urls, this.getClass().getClassLoader());

      // 创建服务容器
      serviceContainer = new ServiceContainer(classLoader);

      ServiceLoader<Plugin> serviceLoader = ServiceLoader.load(Plugin.class, classLoader);

      for (Plugin plugin : serviceLoader) {
        try {
          LOG.info("Initializing plugin: {} v{}", plugin.getName(), plugin.getVersion());

          // 如果插件需要服务注入，使用反射设置
          injectServices(plugin);

          plugin.initialize(router);
          loadedPlugins.add(plugin);
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
    // 使用反射查找所有需要注入的字段
    Class<?> clazz = plugin.getClass();

    for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
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
            }
          }
        } catch (Exception e) {
          LOG.warn("Failed to inject service {} into plugin {}: {}",
              field.getType().getSimpleName(), plugin.getName(), e.getMessage());
        }
      }
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