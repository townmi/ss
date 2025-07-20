package work.anyway.host;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration loader utility
 */
public class ConfigLoader {

  private static final Properties properties = new Properties();

  static {
    loadProperties();
    propagateDataSourceConfig();
  }

  private static void loadProperties() {
    // 1. 尝试从系统属性指定的文件加载
    String configFile = System.getProperty("config.file");
    if (configFile != null) {
      try (InputStream is = new FileInputStream(configFile)) {
        properties.load(is);
        System.out.println("Loaded configuration from: " + configFile);
      } catch (IOException e) {
        System.err.println("Failed to load config file: " + configFile);
      }
    }

    // 2. 尝试从 classpath 加载
    try (InputStream is = ConfigLoader.class.getResourceAsStream("/application.properties")) {
      if (is != null) {
        properties.load(is);
        System.out.println("Loaded configuration from classpath");
      }
    } catch (IOException e) {
      System.err.println("Failed to load configuration from classpath");
    }

    // 3. 系统属性覆盖配置文件
    System.getProperties().forEach((key, value) -> {
      String keyStr = key.toString();
      if (keyStr.startsWith("http.") || keyStr.startsWith("plugins.") ||
          keyStr.startsWith("vertx.") || keyStr.startsWith("service.") ||
          keyStr.startsWith("datasource.")) {
        properties.setProperty(keyStr, value.toString());
      }
    });
  }

  public static String getString(String key, String defaultValue) {
    return properties.getProperty(key, defaultValue);
  }

  public static int getInt(String key, int defaultValue) {
    String value = properties.getProperty(key);
    if (value != null) {
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException e) {
        System.err.println("Invalid integer value for " + key + ": " + value);
      }
    }
    return defaultValue;
  }

  public static boolean getBoolean(String key, boolean defaultValue) {
    String value = properties.getProperty(key);
    if (value != null) {
      return Boolean.parseBoolean(value);
    }
    return defaultValue;
  }

  public static long getLong(String key, long defaultValue) {
    String value = properties.getProperty(key);
    if (value != null) {
      try {
        return Long.parseLong(value);
      } catch (NumberFormatException e) {
        System.err.println("Invalid long value for " + key + ": " + value);
      }
    }
    return defaultValue;
  }

  /**
   * 将数据源配置传播到系统属性
   * 这样 DataPlugin 就可以读取到这些配置
   */
  private static void propagateDataSourceConfig() {
    final int[] count = { 0 };
    properties.forEach((key, value) -> {
      String keyStr = key.toString();
      if (keyStr.startsWith("datasource.")) {
        System.setProperty(keyStr, value.toString());
        String displayValue = keyStr.contains("password") ? "******" : value.toString();
        System.out.println("  Setting system property: " + keyStr + " = " + displayValue);
        count[0]++;
      }
    });
    System.out.println("Propagated " + count[0] + " datasource configuration(s) to system properties");
  }

  /**
   * 获取所有配置属性
   */
  public static Properties getProperties() {
    return new Properties(properties);
  }
}