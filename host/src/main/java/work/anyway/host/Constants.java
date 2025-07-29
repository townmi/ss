package work.anyway.host;

/**
 * 应用程序常量定义
 */
public final class Constants {

  private Constants() {
    // 防止实例化
  }

  // HTTP 服务器配置
  public static final String DEFAULT_HTTP_HOST = "0.0.0.0";
  public static final int DEFAULT_HTTP_PORT = 8080;

  // 目录配置
  public static final String DEFAULT_SERVICES_DIR = "libs/services";
  public static final String DEFAULT_PLUGINS_DIR = "libs/plugins";

  // Spring 配置
  public static final String DEFAULT_SCAN_PACKAGES = "work.anyway";

  // 配置键名
  public static final String CONFIG_HTTP_HOST = "http.host";
  public static final String CONFIG_HTTP_PORT = "http.port";
  public static final String CONFIG_SERVICES_DIR = "services.directory";
  public static final String CONFIG_PLUGINS_DIR = "plugins.directory";
  public static final String CONFIG_SCAN_PACKAGES = "spring.scan.packages";

  // 文件扩展名
  public static final String JAR_EXTENSION = ".jar";

  // 日志消息模板
  public static final String LOG_LOADING_FROM = "Loading {} from: {}";
  public static final String LOG_FOUND_JAR = "Found {} JAR: {}";
  public static final String LOG_HTTP_SERVER_STARTED = "HTTP server started on {}:{}";

  // 开发模式配置
  public static final String CONFIG_DEV_MODE = "dev.mode";
  public static final String CONFIG_DEV_SERVICES_CLASSES_DIR = "dev.services.classes.directory";
  public static final String CONFIG_DEV_PLUGINS_CLASSES_DIR = "dev.plugins.classes.directory";
  public static final String CONFIG_DEV_INTERFACES_CLASSES_DIR = "dev.interfaces.classes.directory";
  public static final String CONFIG_DEV_HOT_RELOAD = "dev.hot.reload";

  // 默认开发模式目录
  public static final String DEFAULT_DEV_SERVICES_CLASSES_DIR = "services/*/target/classes";
  public static final String DEFAULT_DEV_PLUGINS_CLASSES_DIR = "plugins/*/target/classes";
  public static final String DEFAULT_DEV_INTERFACES_CLASSES_DIR = "interfaces/*/target/classes";
}