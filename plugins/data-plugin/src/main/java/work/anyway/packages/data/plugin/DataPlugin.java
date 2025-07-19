package work.anyway.packages.data.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import work.anyway.interfaces.plugin.Plugin;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 数据访问插件
 * 提供通用的数据管理功能
 * 
 * @author 作者名
 * @since 1.0.0
 */
public class DataPlugin implements Plugin {

  private static final Logger LOG = LoggerFactory.getLogger(DataPlugin.class);

  private DataService dataService; // 将由容器自动注入

  private ObjectMapper objectMapper; // 延迟初始化

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

    // 初始化 ObjectMapper
    this.objectMapper = new ObjectMapper();
    LOG.info("ObjectMapper initialized");

    // 检查 dataService 是否已被注入
    if (dataService == null) {
      LOG.warn("DataService was not injected, trying to create instance manually");
      throw new IllegalStateException("DataService is required but was not injected");
    }
    // 配置请求体处理器
    router.route("/api/data/*").handler(BodyHandler.create());

    // API 路由
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
    try {
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

      PageResult<Map<String, Object>> result = dataService.query(collection, options);

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

    } catch (Exception e) {
      LOG.error("查询数据失败", e);
      sendError(ctx, 500, "查询失败: " + e.getMessage());
    }
  }

  private void getById(RoutingContext ctx) {
    try {
      String collection = ctx.pathParam("collection");
      String id = ctx.pathParam("id");

      Optional<Map<String, Object>> data = dataService.findById(collection, id);

      if (data.isPresent()) {
        JsonObject response = new JsonObject()
            .put("success", true)
            .put("data", new JsonObject(data.get()));

        ctx.response()
            .putHeader("content-type", "application/json")
            .end(response.encode());
      } else {
        sendError(ctx, 404, "数据不存在");
      }

    } catch (Exception e) {
      LOG.error("获取数据失败", e);
      sendError(ctx, 500, "获取失败: " + e.getMessage());
    }
  }

  private void createData(RoutingContext ctx) {
    try {
      String collection = ctx.pathParam("collection");
      JsonObject body = ctx.body().asJsonObject();

      Map<String, Object> data = objectMapper.convertValue(body.getMap(), Map.class);
      Map<String, Object> saved = dataService.save(collection, data);

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("data", new JsonObject(saved));

      ctx.response()
          .setStatusCode(201)
          .putHeader("content-type", "application/json")
          .end(response.encode());

    } catch (Exception e) {
      LOG.error("创建数据失败", e);
      sendError(ctx, 500, "创建失败: " + e.getMessage());
    }
  }

  private void updateData(RoutingContext ctx) {
    try {
      String collection = ctx.pathParam("collection");
      String id = ctx.pathParam("id");
      JsonObject body = ctx.body().asJsonObject();

      Map<String, Object> data = objectMapper.convertValue(body.getMap(), Map.class);
      boolean success = dataService.update(collection, id, data);

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

    } catch (Exception e) {
      LOG.error("更新数据失败", e);
      sendError(ctx, 500, "更新失败: " + e.getMessage());
    }
  }

  private void deleteData(RoutingContext ctx) {
    try {
      String collection = ctx.pathParam("collection");
      String id = ctx.pathParam("id");

      boolean success = dataService.delete(collection, id);

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

    } catch (Exception e) {
      LOG.error("删除数据失败", e);
      sendError(ctx, 500, "删除失败: " + e.getMessage());
    }
  }

  private void batchCreate(RoutingContext ctx) {
    try {
      String collection = ctx.pathParam("collection");
      JsonArray body = ctx.body().asJsonArray();

      List<Map<String, Object>> dataList = new ArrayList<>();
      for (Object item : body) {
        if (item instanceof JsonObject) {
          dataList.add(((JsonObject) item).getMap());
        }
      }

      int savedCount = dataService.batchSave(collection, dataList);

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("message", String.format("批量创建成功，共创建 %d 条数据", savedCount))
          .put("count", savedCount);

      ctx.response()
          .setStatusCode(201)
          .putHeader("content-type", "application/json")
          .end(response.encode());

    } catch (Exception e) {
      LOG.error("批量创建失败", e);
      sendError(ctx, 500, "批量创建失败: " + e.getMessage());
    }
  }

  private void batchDelete(RoutingContext ctx) {
    try {
      String collection = ctx.pathParam("collection");
      JsonObject body = ctx.body().asJsonObject();
      JsonArray ids = body.getJsonArray("ids");

      List<String> idList = new ArrayList<>();
      for (Object id : ids) {
        idList.add(String.valueOf(id));
      }

      int deletedCount = dataService.batchDelete(collection, idList);

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("message", String.format("批量删除成功，共删除 %d 条数据", deletedCount))
          .put("count", deletedCount);

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());

    } catch (Exception e) {
      LOG.error("批量删除失败", e);
      sendError(ctx, 500, "批量删除失败: " + e.getMessage());
    }
  }

  // 页面处理方法

  private void getIndexPage(RoutingContext ctx) {
    try {
      String html = readResourceFile("data-plugin/templates/index.html");
      if (html != null) {
        ctx.response()
            .putHeader("content-type", "text/html; charset=utf-8")
            .end(html);
      } else {
        ctx.response().setStatusCode(404).end("页面不存在");
      }
    } catch (Exception e) {
      LOG.error("渲染页面失败", e);
      ctx.response().setStatusCode(500).end("服务器错误");
    }
  }

  private void getCollectionPage(RoutingContext ctx) {
    try {
      String collection = ctx.pathParam("collection");
      String html = readResourceFile("data-plugin/templates/collection.html");

      if (html != null) {
        // 替换集合名称
        html = html.replace("{{collection}}", collection);

        ctx.response()
            .putHeader("content-type", "text/html; charset=utf-8")
            .end(html);
      } else {
        ctx.response().setStatusCode(404).end("页面不存在");
      }
    } catch (Exception e) {
      LOG.error("渲染页面失败", e);
      ctx.response().setStatusCode(500).end("服务器错误");
    }
  }

  private void getCreatePage(RoutingContext ctx) {
    try {
      String collection = ctx.pathParam("collection");
      String html = readResourceFile("data-plugin/templates/create.html");

      if (html != null) {
        html = html.replace("{{collection}}", collection);

        ctx.response()
            .putHeader("content-type", "text/html; charset=utf-8")
            .end(html);
      } else {
        ctx.response().setStatusCode(404).end("页面不存在");
      }
    } catch (Exception e) {
      LOG.error("渲染页面失败", e);
      ctx.response().setStatusCode(500).end("服务器错误");
    }
  }

  private void getEditPage(RoutingContext ctx) {
    try {
      String collection = ctx.pathParam("collection");
      String id = ctx.pathParam("id");
      String html = readResourceFile("data-plugin/templates/edit.html");

      if (html != null) {
        html = html.replace("{{collection}}", collection)
            .replace("{{id}}", id);

        ctx.response()
            .putHeader("content-type", "text/html; charset=utf-8")
            .end(html);
      } else {
        ctx.response().setStatusCode(404).end("页面不存在");
      }
    } catch (Exception e) {
      LOG.error("渲染页面失败", e);
      ctx.response().setStatusCode(500).end("服务器错误");
    }
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
      LOG.error("读取资源文件失败: " + path, e);
      return null;
    }
  }
}