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
}