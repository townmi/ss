package work.anyway.host;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Configuration loader utility
 */
public class ConfigLoader {

  private static final Logger LOG = LoggerFactory.getLogger(ConfigLoader.class);
  private static final Properties properties = new Properties();

  // 可配置的系统属性前缀
  private static final Set<String> SYSTEM_PROPERTY_PREFIXES = new HashSet<>(Arrays.asList(
      "http.", "plugins.", "vertx.", "service.", "datasource.", "spring.", "login."));

  static {
    loadProperties();
    propagateDataSourceConfig();
    propagateLoginSecurityConfig();
  }

  private static void loadProperties() {
    // 1. 尝试从系统属性指定的文件加载
    String configFile = System.getProperty("config.file");
    if (configFile != null) {
      // trim optional surrounding quotes
      LOG.info("Loading configuration from: {}", configFile);
      if (configFile.length() >= 2
          && configFile.startsWith("\"")
          && configFile.endsWith("\"")) {
        configFile = configFile.substring(1, configFile.length() - 1);
      }
      try (InputStream is = new FileInputStream(configFile)) {
        properties.load(is);
        LOG.debug("Loaded configuration successfully  Properties: {}", properties);
      } catch (IOException e) {
        LOG.error("Failed to load config file: {}", configFile, e);
      }
    } else {
      LOG.info("No config file specified, using classpath");
    }

    // 2. 尝试从 classpath 加载
    try (InputStream is = ConfigLoader.class.getResourceAsStream("/application.properties")) {
      if (is != null) {
        properties.load(is);
        LOG.debug("Loaded configuration from classpath");
      }
    } catch (IOException e) {
      LOG.error("Failed to load configuration from classpath", e);
    }

    // 3. 系统属性覆盖配置文件
    System.getProperties().forEach((key, value) -> {
      String keyStr = key.toString();
      if (isSystemPropertyAllowed(keyStr)) {
        properties.setProperty(keyStr, value.toString());
      }
    });
  }

  /**
   * 检查系统属性是否允许覆盖配置
   */
  private static boolean isSystemPropertyAllowed(String key) {
    return SYSTEM_PROPERTY_PREFIXES.stream().anyMatch(key::startsWith);
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
        LOG.error("Invalid integer value for {}: {}", key, value);
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
        LOG.error("Invalid long value for {}: {}", key, value);
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
        LOG.debug("  Setting system property: {} = {}", keyStr, displayValue);
        count[0]++;
      }
    });
    LOG.debug("Propagated {} datasource configuration(s) to system properties", count[0]);
  }

  /**
   * 将登录安全配置传播到系统属性
   * 这样 LoginSecurityConfig 就可以读取到这些配置
   */
  private static void propagateLoginSecurityConfig() {
    final int[] count = { 0 };
    properties.forEach((key, value) -> {
      String keyStr = key.toString();
      if (keyStr.startsWith("login.security.") ||
          keyStr.startsWith("login.logs.") ||
          keyStr.startsWith("login.attempts.")) {
        System.setProperty(keyStr, value.toString());
        LOG.debug("  Setting login security property: {} = {}", keyStr, value.toString());
        count[0]++;
      }
    });
    LOG.debug("Propagated {} login security configuration(s) to system properties", count[0]);
  }

  /**
   * 获取所有配置属性
   */
  public static Properties getProperties() {
    return properties;
  }
}