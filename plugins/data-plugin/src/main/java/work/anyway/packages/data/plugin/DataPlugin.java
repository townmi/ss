package work.anyway.packages.data.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import work.anyway.annotations.*;
import work.anyway.interfaces.data.DataService;
import work.anyway.interfaces.data.PageResult;
import work.anyway.interfaces.data.QueryOptions;
import work.anyway.packages.data.DataSourceManager;

import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 数据访问插件
 * 提供通用的数据管理功能，支持多数据源
 */
@Plugin(name = "Data Plugin", version = "1.0.0", description = "通用数据管理插件，支持动态创建和管理数据集合", icon = "📊", mainPagePath = "/page/data/")
@Controller
@RequestMapping("/")
@MenuItem(id = "data", title = "数据管理", icon = "📊", order = 40)
@PermissionDef(code = "data.view", name = "查看数据", defaultRoles = { "admin", "user" })
@PermissionDef(code = "data.manage", name = "管理数据", defaultRoles = { "admin" })
public class DataPlugin {

  private static final Logger LOG = LoggerFactory.getLogger(DataPlugin.class);

  @Autowired
  private DataService dataService;

  @Autowired(required = false)
  private DataSourceManager dataSourceManager;

  @Autowired
  private Vertx vertx;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final MustacheFactory mustacheFactory = new DefaultMustacheFactory();

  /**
   * 主页路由
   */
  @GetMapping("/page/data/")
  @MenuItem(title = "数据概览", parentId = "data", order = 1, permissions = { "data.view" })
  public void handleMainPage(RoutingContext ctx) {
    try {
      // 获取所有集合
      List<String> collections = getAllCollections();

      Map<String, Object> data = new HashMap<>();
      data.put("title", "数据管理");
      data.put("collections", collections);
      data.put("hasCollections", !collections.isEmpty());
      data.put("collectionCount", collections.size());

      // 设置模板数据供主题系统使用
      ctx.put("templateData", data);
      ctx.put("_layout", "base");

      String html = renderTemplate("index.mustache", data);
      ctx.put("_rendered_content", html);

      // 设置响应头但不结束响应，让拦截器处理
      ctx.response()
          .putHeader("content-type", "text/html; charset=utf-8");

      // 如果没有主题处理器，直接发送响应
      if (ctx.get("_theme_processor_available") == null) {
        ctx.response().end(html);
      }
    } catch (Exception e) {
      LOG.error("Error rendering main page", e);
      ctx.response().setStatusCode(500).end("Internal Server Error");
    }
  }

  /**
   * 集合详情页面
   */
  @GetMapping("/page/data/collection/:name")
  public void handleCollectionPage(RoutingContext ctx) {
    String collectionName = ctx.pathParam("name");

    vertx.executeBlocking(promise -> {
      try {
        // 获取查询参数
        int page = Integer.parseInt(ctx.request().getParam("page", "1"));
        int pageSize = Integer.parseInt(ctx.request().getParam("pageSize", "20"));
        String sortBy = ctx.request().getParam("sortBy", "id");
        boolean ascending = "asc".equals(ctx.request().getParam("order", "asc"));

        // 构建查询选项
        QueryOptions options = QueryOptions.create()
            .page(page)
            .pageSize(pageSize)
            .sortBy(sortBy);

        if (!ascending) {
          options.descending();
        }

        // 查询数据
        PageResult<Map<String, Object>> result = dataService.query(collectionName, options);

        // 准备模板数据
        Map<String, Object> data = new HashMap<>();
        data.put("title", "数据集合: " + collectionName);
        data.put("collectionName", collectionName);
        data.put("collection", collectionName); // 添加 collection 键，模板中也使用了
        data.put("items", result.getData()); // 改为 items 以匹配模板
        data.put("total", result.getTotal());
        data.put("page", result.getPage());
        data.put("pageSize", result.getPageSize());
        data.put("totalPages", result.getTotalPages());
        data.put("hasRecords", !result.getData().isEmpty());
        data.put("hasPrevious", result.hasPrevious());
        data.put("hasNext", result.hasNext());
        data.put("sortBy", sortBy);
        data.put("order", ascending ? "asc" : "desc");

        // 分页信息
        if (result.hasPrevious()) {
          data.put("previousPage", page - 1);
        }
        if (result.hasNext()) {
          data.put("nextPage", page + 1);
        }

        // 获取字段名
        if (!result.getData().isEmpty()) {
          Set<String> fields = new LinkedHashSet<>();
          fields.add("id"); // 确保 ID 在第一位
          result.getData().forEach(record -> fields.addAll(record.keySet()));
          data.put("fields", new ArrayList<>(fields));
        } else {
          data.put("fields", Collections.singletonList("id"));
        }

        // 设置模板数据供主题系统使用
        ctx.put("templateData", data);
        ctx.put("_layout", "base");

        String html = renderTemplate("collection.mustache", data);
        promise.complete(html);
      } catch (Exception e) {
        promise.fail(e);
      }
    }, res -> {
      if (res.succeeded()) {
        String html = (String) res.result();
        ctx.put("_rendered_content", html);

        // 设置响应头但不结束响应，让拦截器处理
        ctx.response()
            .putHeader("content-type", "text/html; charset=utf-8");

        // 如果没有主题处理器，直接发送响应
        if (ctx.get("_theme_processor_available") == null) {
          ctx.response().end(html);
        }
      } else {
        LOG.error("Error rendering collection page", res.cause());
        ctx.response().setStatusCode(500).end("Internal Server Error");
      }
    });
  }

  /**
   * 创建记录页面
   */
  @GetMapping("/page/data/collection/:name/create")
  public void handleCreatePage(RoutingContext ctx) {
    String collectionName = ctx.pathParam("name");

    Map<String, Object> data = new HashMap<>();
    data.put("title", "创建记录 - " + collectionName);
    data.put("collectionName", collectionName);
    data.put("action", "create");
    data.put("submitUrl", "/api/data/" + collectionName);
    data.put("method", "POST");

    try {
      // 设置模板数据供主题系统使用
      ctx.put("templateData", data);
      ctx.put("_layout", "base");

      String html = renderTemplate("create.mustache", data);
      ctx.put("_rendered_content", html);

      // 设置响应头但不结束响应，让拦截器处理
      ctx.response()
          .putHeader("content-type", "text/html; charset=utf-8");

      // 如果没有主题处理器，直接发送响应
      if (ctx.get("_theme_processor_available") == null) {
        ctx.response().end(html);
      }
    } catch (Exception e) {
      LOG.error("Error rendering create page", e);
      ctx.response().setStatusCode(500).end("Internal Server Error");
    }
  }

  /**
   * 编辑记录页面
   */
  @GetMapping("/page/data/collection/:name/edit/:id")
  public void handleEditPage(RoutingContext ctx) {
    String collectionName = ctx.pathParam("name");
    String id = ctx.pathParam("id");

    vertx.executeBlocking(promise -> {
      try {
        Optional<Map<String, Object>> record = dataService.findById(collectionName, id);

        if (record.isEmpty()) {
          promise.fail(new RuntimeException("Record not found"));
          return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("title", "编辑记录 - " + collectionName);
        data.put("collectionName", collectionName);
        data.put("action", "edit");
        data.put("submitUrl", "/api/data/" + collectionName + "/" + id);
        data.put("method", "PUT");
        data.put("record", record.get());
        data.put("recordJson", objectMapper.writeValueAsString(record.get()));

        // 设置模板数据供主题系统使用
        ctx.put("templateData", data);
        ctx.put("_layout", "base");

        String html = renderTemplate("edit.mustache", data);
        promise.complete(html);
      } catch (Exception e) {
        promise.fail(e);
      }
    }, res -> {
      if (res.succeeded()) {
        String html = (String) res.result();
        ctx.put("_rendered_content", html);

        // 设置响应头但不结束响应，让拦截器处理
        ctx.response()
            .putHeader("content-type", "text/html; charset=utf-8");

        // 如果没有主题处理器，直接发送响应
        if (ctx.get("_theme_processor_available") == null) {
          ctx.response().end(html);
        }
      } else {
        LOG.error("Error rendering edit page", res.cause());
        ctx.response().setStatusCode(404).end("Record not found");
      }
    });
  }

  // API 路由

  /**
   * 获取所有集合
   */
  @GetMapping("/api/data/collections")
  public void handleGetCollections(RoutingContext ctx) {
    try {
      List<String> collections = getAllCollections();
      ctx.response()
          .putHeader("content-type", "application/json")
          .end(new JsonArray(collections).encode());
    } catch (Exception e) {
      LOG.error("Error getting collections", e);
      ctx.fail(500, e);
    }
  }

  /**
   * 获取集合中的所有数据
   */
  @GetMapping("/api/data/:collection")
  public void handleGetAll(RoutingContext ctx) {
    String collection = ctx.pathParam("collection");

    vertx.executeBlocking(promise -> {
      try {
        // 获取查询参数
        String pageParam = ctx.request().getParam("page");
        String pageSizeParam = ctx.request().getParam("pageSize");

        if (pageParam != null || pageSizeParam != null) {
          // 分页查询
          int page = pageParam != null ? Integer.parseInt(pageParam) : 1;
          int pageSize = pageSizeParam != null ? Integer.parseInt(pageSizeParam) : 20;
          String sortBy = ctx.request().getParam("sortBy", "id");
          boolean ascending = !"desc".equals(ctx.request().getParam("order"));

          QueryOptions options = QueryOptions.create()
              .page(page)
              .pageSize(pageSize)
              .sortBy(sortBy);

          if (!ascending) {
            options.descending();
          }

          // 处理过滤条件
          ctx.request().params().forEach(entry -> {
            String key = entry.getKey();
            if (!key.equals("page") && !key.equals("pageSize") &&
                !key.equals("sortBy") && !key.equals("order")) {
              options.filter(key, entry.getValue());
            }
          });

          PageResult<Map<String, Object>> result = dataService.query(collection, options);

          JsonObject response = new JsonObject()
              .put("success", true) // 添加 success 字段
              .put("data", new JsonArray(result.getData()))
              .put("total", result.getTotal())
              .put("page", result.getPage())
              .put("pageSize", result.getPageSize())
              .put("totalPages", result.getTotalPages());

          promise.complete(response.encode());
        } else {
          // 获取所有数据
          List<Map<String, Object>> data = dataService.findAll(collection);
          LOG.debug("Found {} records in collection {}", data.size(), collection);
          if (!data.isEmpty()) {
            LOG.debug("First record: {}", data.get(0));
          }

          // 返回统一的响应格式
          JsonObject response = new JsonObject()
              .put("success", true)
              .put("data", new JsonArray(data))
              .put("total", data.size());

          promise.complete(response.encode());
        }
      } catch (Exception e) {
        promise.fail(e);
      }
    }, res -> {
      if (res.succeeded()) {
        ctx.response()
            .putHeader("content-type", "application/json")
            .end((String) res.result());
      } else {
        LOG.error("Error getting data", res.cause());
        ctx.fail(500, res.cause());
      }
    });
  }

  /**
   * 根据ID获取数据
   */
  @GetMapping("/api/data/:collection/:id")
  public void handleGetById(RoutingContext ctx) {
    String collection = ctx.pathParam("collection");
    String id = ctx.pathParam("id");

    vertx.executeBlocking(promise -> {
      try {
        Optional<Map<String, Object>> data = dataService.findById(collection, id);
        if (data.isPresent()) {
          promise.complete(JsonObject.mapFrom(data.get()).encode());
        } else {
          promise.fail(new RuntimeException("Not found"));
        }
      } catch (Exception e) {
        promise.fail(e);
      }
    }, res -> {
      if (res.succeeded()) {
        ctx.response()
            .putHeader("content-type", "application/json")
            .end((String) res.result());
      } else {
        if (res.cause().getMessage().equals("Not found")) {
          ctx.response().setStatusCode(404).end();
        } else {
          LOG.error("Error getting data by id", res.cause());
          ctx.fail(500, res.cause());
        }
      }
    });
  }

  /**
   * 创建数据
   */
  @PostMapping("/api/data/:collection")
  public void handleCreate(RoutingContext ctx) {
    String collection = ctx.pathParam("collection");

    vertx.executeBlocking(promise -> {
      try {
        JsonObject body = ctx.getBodyAsJson();
        if (body == null || body.isEmpty()) {
          promise.fail(new RuntimeException("Request body is required"));
          return;
        }

        Map<String, Object> data = body.getMap();
        Map<String, Object> saved = dataService.save(collection, data);
        promise.complete(JsonObject.mapFrom(saved).encode());
      } catch (Exception e) {
        promise.fail(e);
      }
    }, res -> {
      if (res.succeeded()) {
        ctx.response()
            .setStatusCode(201)
            .putHeader("content-type", "application/json")
            .end((String) res.result());
      } else {
        if (res.cause().getMessage().contains("Request body is required")) {
          ctx.response()
              .setStatusCode(400)
              .end(new JsonObject().put("error", res.cause().getMessage()).encode());
        } else {
          LOG.error("Error creating data", res.cause());
          ctx.fail(500, res.cause());
        }
      }
    });
  }

  /**
   * 更新数据
   */
  @RequestMapping(value = "/api/data/:collection/:id", method = "PUT")
  public void handleUpdate(RoutingContext ctx) {
    String collection = ctx.pathParam("collection");
    String id = ctx.pathParam("id");

    vertx.executeBlocking(promise -> {
      try {
        JsonObject body = ctx.getBodyAsJson();
        if (body == null || body.isEmpty()) {
          promise.fail(new RuntimeException("Request body is required"));
          return;
        }

        Map<String, Object> data = body.getMap();
        boolean updated = dataService.update(collection, id, data);

        if (updated) {
          promise.complete(new JsonObject().put("success", true).encode());
        } else {
          promise.fail(new RuntimeException("Not found"));
        }
      } catch (Exception e) {
        promise.fail(e);
      }
    }, res -> {
      if (res.succeeded()) {
        ctx.response()
            .putHeader("content-type", "application/json")
            .end((String) res.result());
      } else {
        if (res.cause().getMessage().equals("Not found")) {
          ctx.response().setStatusCode(404).end();
        } else if (res.cause().getMessage().contains("Request body is required")) {
          ctx.response()
              .setStatusCode(400)
              .end(new JsonObject().put("error", res.cause().getMessage()).encode());
        } else {
          LOG.error("Error updating data", res.cause());
          ctx.fail(500, res.cause());
        }
      }
    });
  }

  /**
   * 删除数据
   */
  @RequestMapping(value = "/api/data/:collection/:id", method = "DELETE")
  public void handleDelete(RoutingContext ctx) {
    String collection = ctx.pathParam("collection");
    String id = ctx.pathParam("id");

    vertx.executeBlocking(promise -> {
      try {
        boolean deleted = dataService.delete(collection, id);

        if (deleted) {
          promise.complete(new JsonObject().put("success", true).encode());
        } else {
          promise.fail(new RuntimeException("Not found"));
        }
      } catch (Exception e) {
        promise.fail(e);
      }
    }, res -> {
      if (res.succeeded()) {
        ctx.response()
            .putHeader("content-type", "application/json")
            .end((String) res.result());
      } else {
        if (res.cause().getMessage().equals("Not found")) {
          ctx.response().setStatusCode(404).end();
        } else {
          LOG.error("Error deleting data", res.cause());
          ctx.fail(500, res.cause());
        }
      }
    });
  }

  /**
   * 批量创建数据
   */
  @PostMapping("/api/data/:collection/batch")
  public void handleBatchCreate(RoutingContext ctx) {
    String collection = ctx.pathParam("collection");

    vertx.executeBlocking(promise -> {
      try {
        JsonArray body = ctx.getBodyAsJsonArray();
        if (body == null || body.isEmpty()) {
          promise.fail(new RuntimeException("Request body is required"));
          return;
        }

        List<Map<String, Object>> dataList = new ArrayList<>();
        for (int i = 0; i < body.size(); i++) {
          dataList.add(body.getJsonObject(i).getMap());
        }

        int saved = dataService.batchSave(collection, dataList);
        promise.complete(new JsonObject()
            .put("success", true)
            .put("count", saved)
            .encode());
      } catch (Exception e) {
        promise.fail(e);
      }
    }, res -> {
      if (res.succeeded()) {
        ctx.response()
            .setStatusCode(201)
            .putHeader("content-type", "application/json")
            .end((String) res.result());
      } else {
        if (res.cause().getMessage().contains("Request body is required")) {
          ctx.response()
              .setStatusCode(400)
              .end(new JsonObject().put("error", res.cause().getMessage()).encode());
        } else {
          LOG.error("Error batch creating data", res.cause());
          ctx.fail(500, res.cause());
        }
      }
    });
  }

  /**
   * 批量删除数据
   */
  @RequestMapping(value = "/api/data/:collection/batch", method = "DELETE")
  public void handleBatchDelete(RoutingContext ctx) {
    String collection = ctx.pathParam("collection");

    vertx.executeBlocking(promise -> {
      try {
        JsonArray body = ctx.getBodyAsJsonArray();
        if (body == null || body.isEmpty()) {
          promise.fail(new RuntimeException("Request body is required"));
          return;
        }

        List<String> ids = new ArrayList<>();
        for (int i = 0; i < body.size(); i++) {
          ids.add(body.getString(i));
        }

        int deleted = dataService.batchDelete(collection, ids);
        promise.complete(new JsonObject()
            .put("success", true)
            .put("count", deleted)
            .encode());
      } catch (Exception e) {
        promise.fail(e);
      }
    }, res -> {
      if (res.succeeded()) {
        ctx.response()
            .putHeader("content-type", "application/json")
            .end((String) res.result());
      } else {
        if (res.cause().getMessage().contains("Request body is required")) {
          ctx.response()
              .setStatusCode(400)
              .end(new JsonObject().put("error", res.cause().getMessage()).encode());
        } else {
          LOG.error("Error batch deleting data", res.cause());
          ctx.fail(500, res.cause());
        }
      }
    });
  }

  /**
   * 获取数据源信息
   */
  @GetMapping("/api/data/datasources")
  public void handleGetDataSources(RoutingContext ctx) {
    try {
      JsonObject response = new JsonObject();

      if (dataSourceManager != null) {
        // 简化实现，返回默认数据源
        response.put("dataSources", new JsonArray().add("default"));
        response.put("defaultDataSource", "default");
      } else {
        response.put("dataSources", new JsonArray().add("memory"));
        response.put("defaultDataSource", "memory");
      }

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());
    } catch (Exception e) {
      LOG.error("Error getting data sources", e);
      ctx.fail(500, e);
    }
  }

  // 辅助方法

  private List<String> getAllCollections() {
    // 使用 DataService 的 listCollections 方法获取所有集合
    try {
      List<String> collections = dataService.listCollections();
      LOG.debug("Found {} collections", collections.size());
      return collections;
    } catch (Exception e) {
      LOG.error("Error getting collections", e);
      // 如果失败，返回一些默认集合
      return Arrays.asList("users", "products", "orders");
    }
  }

  private String renderTemplate(String templateName, Map<String, Object> data) {
    try (InputStream is = getClass().getResourceAsStream("/data-plugin/templates/" + templateName)) {
      if (is == null) {
        throw new RuntimeException("Template not found: " + templateName);
      }

      Mustache mustache = mustacheFactory.compile(
          new java.io.InputStreamReader(is, StandardCharsets.UTF_8),
          templateName);

      StringWriter writer = new StringWriter();
      mustache.execute(writer, data).flush();
      return writer.toString();
    } catch (Exception e) {
      LOG.error("Error rendering template: " + templateName, e);
      throw new RuntimeException("Template rendering error", e);
    }
  }
}