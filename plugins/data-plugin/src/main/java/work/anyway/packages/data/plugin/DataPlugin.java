package work.anyway.packages.data.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.anyway.interfaces.data.DataService;
import work.anyway.interfaces.data.PageResult;
import work.anyway.interfaces.data.QueryOptions;
import work.anyway.interfaces.data.TypedDataService;
import work.anyway.interfaces.plugin.Plugin;
import work.anyway.interfaces.plugin.ServiceRegistry;
import work.anyway.packages.data.MemoryDataServiceImpl;
import work.anyway.packages.data.SyncDatabaseDataServiceImpl;
import work.anyway.packages.data.AsyncDatabaseDataServiceImpl;
import work.anyway.packages.data.DataSourceManager;

import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 数据访问插件
 * 提供通用的数据管理功能，支持多数据源
 * 
 * @author 作者名
 * @since 1.0.0
 */
public class DataPlugin implements Plugin {

  private static final Logger LOG = LoggerFactory.getLogger(DataPlugin.class);

  private DataService dataService;
  private DataSourceManager dataSourceManager;
  private ServiceRegistry serviceRegistry;

  private ObjectMapper objectMapper; // 延迟初始化
  private final MustacheFactory mustacheFactory = new DefaultMustacheFactory();

  @Override
  public String getName() {
    return "data";
  }

  @Override
  public String getVersion() {
    return "1.0.0";
  }

  @Override
  public String getDescription() {
    return "通用数据管理插件，支持动态创建和管理数据集合";
  }

  @Override
  public String getIcon() {
    return "📊";
  }

  @Override
  public String getMainPagePath() {
    return "/page/data/";
  }

  @Override
  public void initialize(Router router) {
    // 旧版本兼容，直接调用新版本
    initialize(router, null);
  }

  @Override
  public void initialize(Router router, ServiceRegistry registry) {
    this.serviceRegistry = registry;

    // 初始化 ObjectMapper
    this.objectMapper = new ObjectMapper();
    LOG.info("ObjectMapper initialized");

    // 初始化数据源管理器
    initializeDataSourceManager();

    // 根据环境选择合适的数据服务实现
    if (dataSourceManager != null) {
      // 有数据源管理器，使用数据库模式
      Vertx vertx = registry != null ? registry.lookup(Vertx.class).orElse(null) : null;

      if (vertx != null) {
        // 有 Vertx 实例，使用异步数据库实现
        LOG.info("Initializing DataService with async database mode");
        dataService = new AsyncDatabaseDataServiceImpl(dataSourceManager, vertx);
      } else {
        // 没有 Vertx 实例，使用同步数据库实现
        LOG.warn("Vertx instance not found, falling back to synchronous database mode");
        dataService = new SyncDatabaseDataServiceImpl(dataSourceManager);
      }

      // 注册服务
      if (registry != null) {
        LOG.info("Registering database mode DataService");
        registry.register(DataService.class, dataService);
        if (dataService instanceof TypedDataService) {
          registry.register(TypedDataService.class, (TypedDataService) dataService);
        }
      }
    } else {
      // 没有数据源管理器，使用内存模式

      // 先检查是否已有 DataService
      if (dataService == null && registry != null) {
        Optional<DataService> serviceOpt = registry.lookup(DataService.class);
        if (serviceOpt.isPresent()) {
          dataService = serviceOpt.get();
          LOG.info("Using existing DataService instance");
        }
      }

      // 如果还是没有，创建内存实现
      if (dataService == null) {
        LOG.info("Initializing DataService with memory storage mode");
        dataService = new MemoryDataServiceImpl();

        if (registry != null) {
          registry.register(DataService.class, dataService);
          if (dataService instanceof TypedDataService) {
            registry.register(TypedDataService.class, (TypedDataService) dataService);
          }
        }
      }
    }

    // 配置请求体处理器
    router.route("/api/data/*").handler(BodyHandler.create());

    // API 路由
    router.get("/api/data/_datasources").handler(this::getDataSourcesInfo); // 数据源信息
    router.get("/api/data/:collection").handler(this::queryData);
    router.get("/api/data/:collection/:id").handler(this::getById);
    router.post("/api/data/:collection").handler(this::createData);
    router.put("/api/data/:collection/:id").handler(this::updateData);
    router.delete("/api/data/:collection/:id").handler(this::deleteData);
    router.post("/api/data/:collection/batch").handler(this::batchCreate);
    router.delete("/api/data/:collection/batch").handler(this::batchDelete);

    // 页面路由
    router.get("/page/data/").handler(this::getIndexPage);
    router.get("/page/data/collection/:collection").handler(this::getCollectionPage);
    router.get("/page/data/create/:collection").handler(this::getCreatePage);
    router.get("/page/data/edit/:collection/:id").handler(this::getEditPage);

  }

  // API 处理方法

  private void queryData(RoutingContext ctx) {
    String collection = ctx.pathParam("collection");

    // 构建查询选项
    QueryOptions options = QueryOptions.create()
        .page(getIntParam(ctx, "page", 1))
        .pageSize(getIntParam(ctx, "pageSize", 20));

    // 排序
    String sortBy = ctx.queryParam("sortBy").stream().findFirst().orElse(null);
    if (sortBy != null) {
      options.sortBy(sortBy);
      boolean ascending = getBoolParam(ctx, "ascending", true);
      if (ascending) {
        options.ascending();
      } else {
        options.descending();
      }
    }

    // 过滤条件
    ctx.queryParams().forEach(entry -> {
      String key = entry.getKey();
      if (!key.equals("page") && !key.equals("pageSize") &&
          !key.equals("sortBy") && !key.equals("ascending")) {
        options.filter(key, entry.getValue());
      }
    });

    // 使用 executeBlocking 避免阻塞事件循环
    ctx.vertx().<PageResult<Map<String, Object>>>executeBlocking(promise -> {
      try {
        PageResult<Map<String, Object>> result = dataService.query(collection, options);
        promise.complete(result);
      } catch (Exception e) {
        promise.fail(e);
      }
    }, false, res -> {
      if (res.succeeded()) {
        PageResult<Map<String, Object>> result = res.result();
        JsonObject response = new JsonObject()
            .put("success", true)
            .put("data", new JsonArray(result.getData()))
            .put("total", result.getTotal())
            .put("page", result.getPage())
            .put("pageSize", result.getPageSize())
            .put("totalPages", result.getTotalPages());

        ctx.response()
            .putHeader("content-type", "application/json")
            .end(response.encode());
      } else {
        LOG.error("Query data failed", res.cause());
        sendError(ctx, 500, "Query failed: " + res.cause().getMessage());
      }
    });
  }

  private void getById(RoutingContext ctx) {
    String collection = ctx.pathParam("collection");
    String id = ctx.pathParam("id");

    // 使用 executeBlocking 避免阻塞事件循环
    ctx.vertx().<Optional<Map<String, Object>>>executeBlocking(promise -> {
      try {
        Optional<Map<String, Object>> data = dataService.findById(collection, id);
        promise.complete(data);
      } catch (Exception e) {
        promise.fail(e);
      }
    }, false, res -> {
      if (res.succeeded()) {
        Optional<Map<String, Object>> data = res.result();
        if (data.isPresent()) {
          JsonObject response = new JsonObject()
              .put("success", true)
              .put("data", new JsonObject(data.get()));

          ctx.response()
              .putHeader("content-type", "application/json")
              .end(response.encode());
        } else {
          sendError(ctx, 404, "Data not found");
        }
      } else {
        LOG.error("Get data failed", res.cause());
        sendError(ctx, 500, "Get failed: " + res.cause().getMessage());
      }
    });
  }

  private void createData(RoutingContext ctx) {
    String collection = ctx.pathParam("collection");
    JsonObject body = ctx.body().asJsonObject();

    // 使用 executeBlocking 避免阻塞事件循环
    ctx.vertx().<Map<String, Object>>executeBlocking(promise -> {
      try {
        Map<String, Object> data = objectMapper.convertValue(body.getMap(), Map.class);
        Map<String, Object> saved = dataService.save(collection, data);
        promise.complete(saved);
      } catch (Exception e) {
        promise.fail(e);
      }
    }, false, res -> {
      if (res.succeeded()) {
        Map<String, Object> saved = res.result();
        JsonObject response = new JsonObject()
            .put("success", true)
            .put("data", new JsonObject(saved));

        ctx.response()
            .setStatusCode(201)
            .putHeader("content-type", "application/json")
            .end(response.encode());
      } else {
        LOG.error("Create data failed", res.cause());
        sendError(ctx, 500, "Create failed: " + res.cause().getMessage());
      }
    });
  }

  private void updateData(RoutingContext ctx) {
    String collection = ctx.pathParam("collection");
    String id = ctx.pathParam("id");
    JsonObject body = ctx.body().asJsonObject();

    // 使用 executeBlocking 避免阻塞事件循环
    ctx.vertx().<Boolean>executeBlocking(promise -> {
      try {
        Map<String, Object> data = objectMapper.convertValue(body.getMap(), Map.class);
        boolean success = dataService.update(collection, id, data);
        promise.complete(success);
      } catch (Exception e) {
        promise.fail(e);
      }
    }, false, res -> {
      if (res.succeeded()) {
        boolean success = res.result();
        if (success) {
          JsonObject response = new JsonObject()
              .put("success", true)
              .put("message", "更新成功");

          ctx.response()
              .putHeader("content-type", "application/json")
              .end(response.encode());
        } else {
          sendError(ctx, 404, "数据不存在");
        }
      } else {
        LOG.error("Update data failed", res.cause());
        sendError(ctx, 500, "Update failed: " + res.cause().getMessage());
      }
    });
  }

  private void deleteData(RoutingContext ctx) {
    String collection = ctx.pathParam("collection");
    String id = ctx.pathParam("id");

    // 使用 executeBlocking 避免阻塞事件循环
    ctx.vertx().<Boolean>executeBlocking(promise -> {
      try {
        boolean success = dataService.delete(collection, id);
        promise.complete(success);
      } catch (Exception e) {
        promise.fail(e);
      }
    }, false, res -> {
      if (res.succeeded()) {
        boolean success = res.result();
        if (success) {
          JsonObject response = new JsonObject()
              .put("success", true)
              .put("message", "删除成功");

          ctx.response()
              .putHeader("content-type", "application/json")
              .end(response.encode());
        } else {
          sendError(ctx, 404, "数据不存在");
        }
      } else {
        LOG.error("Delete data failed", res.cause());
        sendError(ctx, 500, "Delete failed: " + res.cause().getMessage());
      }
    });
  }

  private void batchCreate(RoutingContext ctx) {
    String collection = ctx.pathParam("collection");
    JsonArray body = ctx.body().asJsonArray();

    // 使用 executeBlocking 避免阻塞事件循环
    ctx.vertx().<Integer>executeBlocking(promise -> {
      try {
        List<Map<String, Object>> dataList = new ArrayList<>();
        for (Object item : body) {
          if (item instanceof JsonObject) {
            dataList.add(((JsonObject) item).getMap());
          }
        }

        int savedCount = dataService.batchSave(collection, dataList);
        promise.complete(savedCount);
      } catch (Exception e) {
        promise.fail(e);
      }
    }, false, res -> {
      if (res.succeeded()) {
        int savedCount = res.result();
        JsonObject response = new JsonObject()
            .put("success", true)
            .put("message", String.format("批量创建成功，共创建 %d 条数据", savedCount))
            .put("count", savedCount);

        ctx.response()
            .setStatusCode(201)
            .putHeader("content-type", "application/json")
            .end(response.encode());
      } else {
        LOG.error("Batch create failed", res.cause());
        sendError(ctx, 500, "Batch create failed: " + res.cause().getMessage());
      }
    });
  }

  private void batchDelete(RoutingContext ctx) {
    String collection = ctx.pathParam("collection");
    JsonObject body = ctx.body().asJsonObject();
    JsonArray ids = body.getJsonArray("ids");

    // 使用 executeBlocking 避免阻塞事件循环
    ctx.vertx().<Integer>executeBlocking(promise -> {
      try {
        List<String> idList = new ArrayList<>();
        for (Object id : ids) {
          idList.add(String.valueOf(id));
        }

        int deletedCount = dataService.batchDelete(collection, idList);
        promise.complete(deletedCount);
      } catch (Exception e) {
        promise.fail(e);
      }
    }, false, res -> {
      if (res.succeeded()) {
        int deletedCount = res.result();
        JsonObject response = new JsonObject()
            .put("success", true)
            .put("message", String.format("批量删除成功，共删除 %d 条数据", deletedCount))
            .put("count", deletedCount);

        ctx.response()
            .putHeader("content-type", "application/json")
            .end(response.encode());
      } else {
        LOG.error("Batch delete failed", res.cause());
        sendError(ctx, 500, "Batch delete failed: " + res.cause().getMessage());
      }
    });
  }

  // 页面处理方法

  private void getIndexPage(RoutingContext ctx) {
    try {
      Mustache mustache = mustacheFactory.compile("data-plugin/templates/index.mustache");

      Map<String, Object> data = new HashMap<>();
      data.put("pluginName", getName());
      data.put("pluginVersion", getVersion());

      StringWriter writer = new StringWriter();
      mustache.execute(writer, data);

      ctx.response()
          .putHeader("content-type", "text/html; charset=utf-8")
          .end(writer.toString());
    } catch (Exception e) {
      LOG.error("Failed to render page", e);
      ctx.response().setStatusCode(500).end("Internal Server Error");
    }
  }

  private void getCollectionPage(RoutingContext ctx) {
    String collection = ctx.pathParam("collection");

    // 获取集合数据用于展示
    ctx.vertx().<PageResult<Map<String, Object>>>executeBlocking(promise -> {
      try {
        QueryOptions options = QueryOptions.create()
            .page(1)
            .pageSize(20);
        PageResult<Map<String, Object>> result = dataService.query(collection, options);
        promise.complete(result);
      } catch (Exception e) {
        promise.fail(e);
      }
    }, false, res -> {
      if (res.succeeded()) {
        try {
          Mustache mustache = mustacheFactory.compile("data-plugin/templates/collection.mustache");

          PageResult<Map<String, Object>> result = res.result();
          Map<String, Object> data = new HashMap<>();
          data.put("collection", collection);
          data.put("items", result.getData());
          data.put("total", result.getTotal());
          data.put("page", result.getPage());
          data.put("totalPages", result.getTotalPages());

          StringWriter writer = new StringWriter();
          mustache.execute(writer, data);

          ctx.response()
              .putHeader("content-type", "text/html; charset=utf-8")
              .end(writer.toString());
        } catch (Exception e) {
          LOG.error("Failed to render page", e);
          ctx.response().setStatusCode(500).end("Internal Server Error");
        }
      } else {
        LOG.error("Failed to load collection data", res.cause());
        ctx.response().setStatusCode(500).end("Internal Server Error");
      }
    });
  }

  private void getCreatePage(RoutingContext ctx) {
    try {
      String collection = ctx.pathParam("collection");
      Mustache mustache = mustacheFactory.compile("data-plugin/templates/create.mustache");

      Map<String, Object> data = new HashMap<>();
      data.put("collection", collection);

      StringWriter writer = new StringWriter();
      mustache.execute(writer, data);

      ctx.response()
          .putHeader("content-type", "text/html; charset=utf-8")
          .end(writer.toString());
    } catch (Exception e) {
      LOG.error("Failed to render page", e);
      ctx.response().setStatusCode(500).end("Internal Server Error");
    }
  }

  private void getEditPage(RoutingContext ctx) {
    String collection = ctx.pathParam("collection");
    String id = ctx.pathParam("id");

    // 获取要编辑的数据
    ctx.vertx().<Optional<Map<String, Object>>>executeBlocking(promise -> {
      try {
        Optional<Map<String, Object>> item = dataService.findById(collection, id);
        promise.complete(item);
      } catch (Exception e) {
        promise.fail(e);
      }
    }, false, res -> {
      if (res.succeeded()) {
        Optional<Map<String, Object>> item = res.result();
        if (item.isPresent()) {
          try {
            Mustache mustache = mustacheFactory.compile("data-plugin/templates/edit.mustache");

            Map<String, Object> data = new HashMap<>();
            data.put("collection", collection);
            data.put("id", id);
            data.put("item", item.get());
            data.put("itemJson", objectMapper.writeValueAsString(item.get()));

            StringWriter writer = new StringWriter();
            mustache.execute(writer, data);

            ctx.response()
                .putHeader("content-type", "text/html; charset=utf-8")
                .end(writer.toString());
          } catch (Exception e) {
            LOG.error("Failed to render page", e);
            ctx.response().setStatusCode(500).end("Internal Server Error");
          }
        } else {
          ctx.response().setStatusCode(404).end("Data not found");
        }
      } else {
        LOG.error("Failed to load data for edit", res.cause());
        ctx.response().setStatusCode(500).end("Internal Server Error");
      }
    });
  }

  // 辅助方法

  private void sendError(RoutingContext ctx, int statusCode, String message) {
    JsonObject error = new JsonObject()
        .put("success", false)
        .put("error", message);

    ctx.response()
        .setStatusCode(statusCode)
        .putHeader("content-type", "application/json")
        .end(error.encode());
  }

  private int getIntParam(RoutingContext ctx, String name, int defaultValue) {
    try {
      String value = ctx.queryParam(name).stream().findFirst().orElse(null);
      return value != null ? Integer.parseInt(value) : defaultValue;
    } catch (Exception e) {
      return defaultValue;
    }
  }

  private boolean getBoolParam(RoutingContext ctx, String name, boolean defaultValue) {
    try {
      String value = ctx.queryParam(name).stream().findFirst().orElse(null);
      return value != null ? Boolean.parseBoolean(value) : defaultValue;
    } catch (Exception e) {
      return defaultValue;
    }
  }

  private String readResourceFile(String path) {
    try (InputStream is = DataPlugin.class.getResourceAsStream("/" + path)) {
      if (is == null)
        return null;
      try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name())) {
        scanner.useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : "";
      }
    } catch (Exception e) {
      LOG.error("Failed to read resource file: " + path, e);
      return null;
    }
  }

  /**
   * 初始化数据源管理器
   */
  private void initializeDataSourceManager() {
    LOG.info("========== Starting DataSource Manager Initialization ==========");
    try {
      // 获取 Vertx 实例
      Vertx vertx = null;
      if (serviceRegistry != null) {
        Optional<Vertx> vertxOpt = serviceRegistry.lookup(Vertx.class);
        if (vertxOpt.isPresent()) {
          vertx = vertxOpt.get();
          LOG.info("[OK] Successfully obtained Vertx instance");
        } else {
          LOG.warn("[FAIL] Vertx instance not found in ServiceRegistry");
        }
      } else {
        LOG.warn("[FAIL] ServiceRegistry is null");
      }

      if (vertx == null) {
        LOG.warn("[FAIL] Vertx instance not found, will use memory storage mode");
        return;
      }

      // 加载配置
      LOG.info("Loading datasource configuration...");
      Properties config = loadDataSourceConfig();
      if (config.isEmpty()) {
        LOG.info("[FAIL] No datasource configuration found, will use memory storage mode");
        return;
      }

      LOG.info("[OK] Successfully loaded configuration, total {} items", config.size());
      // 打印所有数据源相关配置（隐藏密码）
      LOG.info("Printing datasource configurations:");
      config.forEach((key, value) -> {
        String keyStr = key.toString();
        if (keyStr.startsWith("datasource.")) {
          String valueStr = keyStr.contains("password") ? "******" : value.toString();
          LOG.info("  Config item: {} = {}", keyStr, valueStr);
        }
      });
      LOG.info("Finished printing configurations");

      LOG.info("Creating DataSourceManager...");
      try {
        dataSourceManager = new DataSourceManager(vertx);
        LOG.info("DataSourceManager created successfully");
      } catch (Exception e) {
        LOG.error("Failed to create DataSourceManager", e);
        throw e;
      }

      // 解析并注册数据源
      LOG.info("Parsing datasource configurations...");
      Map<String, JsonObject> dataSourceConfigs = null;
      try {
        dataSourceConfigs = parseDataSourceConfigs(config);
        LOG.info("Parsed {} datasource configurations", dataSourceConfigs.size());

        // 打印解析出的配置详情
        dataSourceConfigs.forEach((name, cfg) -> {
          LOG.info("Datasource '{}' config: {}", name, cfg.encodePrettily());
        });
      } catch (Exception e) {
        LOG.error("Failed to parse datasource configurations", e);
        throw e;
      }

      for (Map.Entry<String, JsonObject> entry : dataSourceConfigs.entrySet()) {
        String dsName = entry.getKey();
        JsonObject dsConfig = entry.getValue();
        LOG.info("Registering datasource: {}", dsName);
        LOG.debug("  Type: {}", dsConfig.getString("type"));
        LOG.debug("  Host: {}", dsConfig.getString("host"));
        LOG.debug("  Port: {}", dsConfig.getInteger("port"));
        LOG.debug("  Database: {}", dsConfig.getString("database"));
        LOG.debug("  User: {}", dsConfig.getString("user"));
        LOG.debug("  Max Pool Size: {}", dsConfig.getInteger("maxPoolSize"));

        dataSourceManager.registerDataSource(dsName, dsConfig);
        LOG.info("[OK] Successfully registered datasource: {}", dsName);
      }

      // 设置默认数据源
      String defaultDs = config.getProperty("datasource.default");
      if (defaultDs != null) {
        if (dataSourceManager.hasDataSource(defaultDs)) {
          dataSourceManager.setDefaultDataSource(defaultDs);
          LOG.info("[OK] Set default datasource: {}", defaultDs);
        } else {
          LOG.warn("[FAIL] Default datasource {} not found", defaultDs);
        }
      } else {
        LOG.info("No default datasource configured");
      }

      LOG.info("========== DataSource Manager Initialization Completed ==========");
      LOG.info("[OK] Registered {} datasource(s)", dataSourceConfigs.size());
      LOG.info("[OK] Data access mode: Database Storage");

    } catch (Exception e) {
      LOG.error("[FAIL] Failed to initialize DataSource Manager, will use memory storage mode", e);
      LOG.info("[OK] Data access mode: Memory Storage");
    }
  }

  /**
   * 加载数据源配置
   */
  private Properties loadDataSourceConfig() {
    Properties props = new Properties();

    // 尝试从资源文件加载
    try (InputStream is = getClass().getResourceAsStream("/datasource.properties")) {
      if (is != null) {
        props.load(is);
        LOG.info("Loaded datasource configuration from datasource.properties");
      }
    } catch (Exception e) {
      LOG.debug("datasource.properties file not found or failed to load", e);
    }

    // 尝试从系统属性加载
    System.getProperties().forEach((key, value) -> {
      String keyStr = key.toString();
      if (keyStr.startsWith("datasource.")) {
        props.setProperty(keyStr, value.toString());
      }
    });

    // 尝试从环境变量加载
    System.getenv().forEach((key, value) -> {
      if (key.startsWith("DATASOURCE_")) {
        String propKey = key.toLowerCase().replace('_', '.');
        props.setProperty(propKey, value);
      }
    });

    return props;
  }

  /**
   * 解析数据源配置
   */
  private Map<String, JsonObject> parseDataSourceConfigs(Properties props) {
    LOG.debug("Starting to parse datasource configurations from properties");
    Map<String, JsonObject> configs = new HashMap<>();

    // 解析配置格式: datasource.<name>.<property>
    props.forEach((key, value) -> {
      String keyStr = key.toString();
      LOG.trace("Processing property: {} = {}", keyStr, keyStr.contains("password") ? "******" : value);

      if (keyStr.startsWith("datasource.") && !keyStr.equals("datasource.default")) {
        String[] parts = keyStr.split("\\.", 3);
        if (parts.length >= 3) {
          String dsName = parts[1];
          String property = parts[2];

          LOG.trace("Found datasource config: name={}, property={}", dsName, property);

          JsonObject config = configs.computeIfAbsent(dsName, k -> new JsonObject());

          // 尝试解析数字类型
          String valueStr = value.toString().trim(); // 去除前后空格
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

    LOG.debug("Finished parsing datasource configurations, found {} datasources", configs.size());
    return configs;
  }

  /**
   * 获取数据源信息
   */
  private void getDataSourcesInfo(RoutingContext ctx) {
    JsonObject response = new JsonObject();

    try {
      // 判断数据存储模式
      boolean isDatabaseMode = dataSourceManager != null;
      response.put("success", true);
      response.put("mode", isDatabaseMode ? "database" : "memory");

      if (isDatabaseMode) {
        // 获取所有数据源配置
        JsonArray datasources = new JsonArray();
        Map<String, JsonObject> configs = dataSourceManager.getDataSourceConfigs();

        for (Map.Entry<String, JsonObject> entry : configs.entrySet()) {
          String name = entry.getKey();
          JsonObject config = entry.getValue();

          JsonObject dsInfo = new JsonObject()
              .put("name", name)
              .put("type", config.getString("type", "unknown"))
              .put("host", config.getString("host", "N/A"))
              .put("port", config.getInteger("port", 0))
              .put("database", config.getString("database", "N/A"))
              .put("user", config.getString("user", "N/A"))
              .put("maxPoolSize", config.getInteger("maxPoolSize", 10));

          // 检查连接状态（简单判断）
          try {
            // 尝试获取连接池，如果成功则认为是已连接
            dataSourceManager.getPool(name);
            dsInfo.put("status", "connected");
          } catch (Exception e) {
            dsInfo.put("status", "disconnected");
          }

          datasources.add(dsInfo);
        }

        response.put("datasources", datasources);

        // 获取默认数据源
        String defaultDs = dataSourceManager.getDefaultDataSource();
        if (defaultDs != null) {
          response.put("defaultDataSource", defaultDs);
        }
      } else {
        response.put("datasources", new JsonArray());
        response.put("message", "使用内存存储模式，数据仅保存在内存中");
      }

    } catch (Exception e) {
      LOG.error("Failed to get datasource info", e);
      response.put("success", false);
      response.put("error", e.getMessage());
    }

    ctx.response()
        .putHeader("content-type", "application/json")
        .end(response.encode());
  }
}