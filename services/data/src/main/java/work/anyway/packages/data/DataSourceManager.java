package work.anyway.packages.data;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.mysqlclient.MySQLPool;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据源管理器
 * 负责管理多个数据库连接池
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Component
public class DataSourceManager implements InitializingBean, DisposableBean {
  private static final Logger LOG = LoggerFactory.getLogger(DataSourceManager.class);

  private final Vertx vertx;
  private final Map<String, Pool> dataSources = new ConcurrentHashMap<>();
  private final Map<String, JsonObject> dataSourceConfigs = new ConcurrentHashMap<>();
  private String defaultDataSource = "default";

  /**
   * 构造函数
   * 
   * @param vertx Vert.x 实例
   */
  @Autowired
  public DataSourceManager(Vertx vertx) {
    LOG.info("Initializing DataSourceManager with Vertx instance: {}", vertx);
    this.vertx = vertx;
    LOG.info("DataSourceManager initialized successfully");
  }

  /**
   * Spring 初始化回调
   */
  @Override
  public void afterPropertiesSet() throws Exception {
    LOG.info("Initializing DataSource configurations...");

    // 加载数据源配置
    Properties config = loadDataSourceConfig();

    // 解析并注册数据源
    Map<String, JsonObject> parsedConfigs = parseDataSourceConfigs(config);

    for (Map.Entry<String, JsonObject> entry : parsedConfigs.entrySet()) {
      String dsName = entry.getKey();
      JsonObject dsConfig = entry.getValue();

      LOG.info("Registering datasource: {}", dsName);
      LOG.debug("  Type: {}", dsConfig.getString("type"));
      LOG.debug("  Host: {}", dsConfig.getString("host"));
      LOG.debug("  Port: {}", dsConfig.getInteger("port"));
      LOG.debug("  Database: {}", dsConfig.getString("database"));
      LOG.debug("  User: {}", dsConfig.getString("user"));

      registerDataSource(dsName, dsConfig);
    }

    // 设置默认数据源
    String defaultDs = config.getProperty("datasource.default");
    if (defaultDs != null && hasDataSource(defaultDs)) {
      setDefaultDataSource(defaultDs);
      LOG.info("Default datasource set to: {}", defaultDs);
    }
  }

  /**
   * 加载数据源配置
   */
  private Properties loadDataSourceConfig() {
    Properties props = new Properties();

    // 从 application.properties 加载
    try (InputStream is = getClass().getResourceAsStream("/application.properties")) {
      if (is != null) {
        props.load(is);
        LOG.info("Loaded datasource configuration from application.properties");
      }
    } catch (Exception e) {
      LOG.debug("Could not load application.properties", e);
    }

    // 从系统属性加载（覆盖文件配置）
    System.getProperties().forEach((key, value) -> {
      String keyStr = key.toString();
      if (keyStr.startsWith("datasource.")) {
        props.setProperty(keyStr, value.toString());
      }
    });

    return props;
  }

  /**
   * 解析数据源配置
   */
  private Map<String, JsonObject> parseDataSourceConfigs(Properties props) {
    Map<String, JsonObject> configs = new HashMap<>();

    // 解析配置格式: datasource.<name>.<property>
    props.forEach((key, value) -> {
      String keyStr = key.toString();

      if (keyStr.startsWith("datasource.") && !keyStr.equals("datasource.default")) {
        String[] parts = keyStr.split("\\.", 3);
        if (parts.length >= 3) {
          String dsName = parts[1];
          String property = parts[2];

          JsonObject config = configs.computeIfAbsent(dsName, k -> new JsonObject());

          // 尝试解析数字类型
          String valueStr = value.toString().trim();
          if (valueStr.matches("\\d+")) {
            config.put(property, Integer.parseInt(valueStr));
          } else if (valueStr.equalsIgnoreCase("true") || valueStr.equalsIgnoreCase("false")) {
            config.put(property, Boolean.parseBoolean(valueStr));
          } else {
            config.put(property, valueStr);
          }
        }
      }
    });

    LOG.info("Parsed {} datasource configurations", configs.size());
    return configs;
  }

  /**
   * 注册数据源配置
   * 配置会在第一次使用时创建连接池
   * 
   * @param name   数据源名称
   * @param config 数据源配置
   */
  public void registerDataSource(String name, JsonObject config) {
    LOG.info("Registering datasource: {}", name);
    dataSourceConfigs.put(name, config);
  }

  /**
   * 获取数据源连接池
   * 如果连接池尚未创建，将在第一次调用时创建
   * 
   * @param dataSourceName 数据源名称
   * @return 连接池实例
   */
  public Pool getPool(String dataSourceName) {
    if (dataSourceName == null || dataSourceName.isEmpty()) {
      dataSourceName = defaultDataSource;
    }

    return dataSources.computeIfAbsent(dataSourceName, this::createPool);
  }

  /**
   * 获取默认数据源连接池
   * 
   * @return 默认连接池实例
   */
  public Pool getDefaultPool() {
    return getPool(defaultDataSource);
  }

  /**
   * 创建连接池
   * 
   * @param dataSourceName 数据源名称
   * @return 连接池实例
   */
  private Pool createPool(String dataSourceName) {
    JsonObject config = dataSourceConfigs.get(dataSourceName);
    if (config == null) {
      throw new IllegalArgumentException("Datasource not configured: " + dataSourceName);
    }

    LOG.info("Creating datasource connection pool: {}", dataSourceName);

    String type = config.getString("type", "postgresql");

    switch (type.toLowerCase()) {
      case "postgresql":
      case "postgres":
      case "pg":
        return createPostgreSQLPool(config);
      case "mysql":
        return createMySQLPool(config);
      default:
        throw new UnsupportedOperationException("Unsupported database type: " + type);
    }
  }

  /**
   * 创建 PostgreSQL 连接池
   * 
   * @param config 配置信息
   * @return PostgreSQL 连接池
   */
  private Pool createPostgreSQLPool(JsonObject config) {
    PgConnectOptions connectOptions = new PgConnectOptions()
        .setHost(config.getString("host", "localhost"))
        .setPort(config.getInteger("port", 5432))
        .setDatabase(config.getString("database"))
        .setUser(config.getString("user"))
        .setPassword(config.getString("password"));

    // 添加额外的连接选项
    if (config.containsKey("properties")) {
      JsonObject properties = config.getJsonObject("properties");
      properties.forEach(entry -> {
        connectOptions.addProperty(entry.getKey(), String.valueOf(entry.getValue()));
      });
    }

    PoolOptions poolOptions = createPoolOptions(config);

    return PgPool.pool(vertx, connectOptions, poolOptions);
  }

  /**
   * 创建 MySQL 连接池
   * 
   * @param config 配置信息
   * @return MySQL 连接池
   */
  private Pool createMySQLPool(JsonObject config) {
    MySQLConnectOptions connectOptions = new MySQLConnectOptions()
        .setHost(config.getString("host", "localhost"))
        .setPort(config.getInteger("port", 3306))
        .setDatabase(config.getString("database"))
        .setUser(config.getString("user"))
        .setPassword(config.getString("password"));

    // 添加额外的连接选项
    if (config.containsKey("properties")) {
      JsonObject properties = config.getJsonObject("properties");
      properties.forEach(entry -> {
        connectOptions.addProperty(entry.getKey(), String.valueOf(entry.getValue()));
      });
    }

    PoolOptions poolOptions = createPoolOptions(config);

    return MySQLPool.pool(vertx, connectOptions, poolOptions);
  }

  /**
   * 创建连接池选项
   * 
   * @param config 配置信息
   * @return 连接池选项
   */
  private PoolOptions createPoolOptions(JsonObject config) {
    PoolOptions poolOptions = new PoolOptions()
        .setMaxSize(config.getInteger("maxPoolSize", 10))
        .setMaxWaitQueueSize(config.getInteger("maxWaitQueueSize", -1));

    if (config.containsKey("connectionTimeout")) {
      poolOptions.setConnectionTimeout(config.getInteger("connectionTimeout"));
    }

    if (config.containsKey("idleTimeout")) {
      poolOptions.setIdleTimeout(config.getInteger("idleTimeout"));
    }

    if (config.containsKey("maxLifetime")) {
      poolOptions.setMaxLifetime(config.getInteger("maxLifetime"));
    }

    return poolOptions;
  }

  /**
   * 设置默认数据源
   * 
   * @param name 默认数据源名称
   */
  public void setDefaultDataSource(String name) {
    if (!dataSourceConfigs.containsKey(name)) {
      throw new IllegalArgumentException("数据源不存在: " + name);
    }
    this.defaultDataSource = name;
    LOG.info("Setting default datasource: {}", name);
  }

  /**
   * 检查数据源是否已注册
   * 
   * @param name 数据源名称
   * @return 是否已注册
   */
  public boolean hasDataSource(String name) {
    return dataSourceConfigs.containsKey(name);
  }

  /**
   * 获取所有已注册的数据源名称
   * 
   * @return 数据源名称集合
   */
  public Map<String, JsonObject> getDataSourceConfigs() {
    return new ConcurrentHashMap<>(dataSourceConfigs);
  }

  /**
   * 获取默认数据源名称
   * 
   * @return 默认数据源名称
   */
  public String getDefaultDataSource() {
    return defaultDataSource;
  }

  /**
   * 关闭指定数据源
   * 
   * @param name 数据源名称
   */
  public void closeDataSource(String name) {
    Pool pool = dataSources.remove(name);
    if (pool != null) {
      LOG.info("Closing datasource: {}", name);
      pool.close();
    }
  }

  /**
   * Spring 销毁回调
   */
  @Override
  public void destroy() throws Exception {
    LOG.info("Closing all datasource connection pools");
    dataSources.forEach((name, pool) -> {
      try {
        pool.close();
        LOG.debug("Datasource {} closed", name);
      } catch (Exception e) {
        LOG.error("Failed to close datasource {}", name, e);
      }
    });
    dataSources.clear();
  }
}