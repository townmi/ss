package work.anyway.packages.data;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import work.anyway.interfaces.data.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.sql.Timestamp;
import java.util.Date;

/**
 * 异步数据库数据服务实现
 * 使用 Vert.x 的异步 API 避免阻塞事件循环
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Service("asyncDatabaseDataService")
public class AsyncDatabaseDataServiceImpl implements TypedDataService {

  private static final Logger LOG = LoggerFactory.getLogger(AsyncDatabaseDataServiceImpl.class);

  protected final DataSourceManager dataSourceManager;
  protected final Vertx vertx;

  // 缓存，用于存储临时数据
  private final Map<String, Map<String, Map<String, Object>>> memoryCache = new ConcurrentHashMap<>();

  @Autowired
  public AsyncDatabaseDataServiceImpl(DataSourceManager dataSourceManager, Vertx vertx) {
    this.dataSourceManager = dataSourceManager;
    this.vertx = vertx;
    LOG.info("AsyncDatabaseDataServiceImpl initialized with async database mode");
  }

  @Override
  public Map<String, Object> save(String collection, Map<String, Object> data) {
    LOG.debug("Saving data to collection: {}", collection);

    // 为了保持接口兼容性，这里使用阻塞方式等待异步操作完成
    // 但是会在 worker 线程中执行，不会阻塞事件循环
    CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();

    vertx.<Map<String, Object>>executeBlocking(promise -> {
      saveAsync(collection, data).onComplete(ar -> {
        if (ar.succeeded()) {
          promise.complete(ar.result());
        } else {
          promise.fail(ar.cause());
        }
      });
    }, false, res -> {
      if (res.succeeded()) {
        future.complete(res.result());
      } else {
        future.completeExceptionally(res.cause());
      }
    });

    try {
      return future.get();
    } catch (Exception e) {
      LOG.error("Failed to save data", e);
      // 失败时使用内存缓存
      return saveToMemory(collection, data);
    }
  }

  private Future<Map<String, Object>> saveAsync(String collection, Map<String, Object> data) {
    Promise<Map<String, Object>> promise = Promise.promise();

    try {
      Pool pool = dataSourceManager.getDefaultPool();

      // 准备数据 - 不修改原始数据，只是复制
      Map<String, Object> savedData = new HashMap<>(data);

      // 如果没有提供 ID，由调用方负责生成
      if (!savedData.containsKey("id")) {
        promise.fail(new IllegalArgumentException("ID is required"));
        return promise.future();
      }

      String id = String.valueOf(savedData.get("id"));

      // 构建 INSERT SQL
      List<String> columns = new ArrayList<>();
      List<String> placeholders = new ArrayList<>();
      List<Object> values = new ArrayList<>();

      savedData.forEach((key, value) -> {
        String columnName = camelToSnake(key);
        columns.add(columnName);
        placeholders.add("?");
        // Convert value to database-compatible format
        values.add(convertForDatabase(value));
      });

      String sql = String.format("INSERT INTO %s (%s) VALUES (%s)",
          collection,
          String.join(", ", columns),
          String.join(", ", placeholders));

      LOG.debug("Executing SQL: {}", sql);

      pool.preparedQuery(sql)
          .execute(Tuple.from(values))
          .onSuccess(rows -> {
            LOG.info("Data saved to database successfully, collection: {}, ID: {}", collection, id);
            promise.complete(savedData);
          })
          .onFailure(err -> {
            LOG.error("Failed to save data to database", err);
            promise.fail(err);
          });

    } catch (Exception e) {
      promise.fail(e);
    }

    return promise.future();
  }

  @Override
  public Optional<Map<String, Object>> findById(String collection, String id) {
    LOG.debug("Finding data by ID, collection: {}, ID: {}", collection, id);

    CompletableFuture<Optional<Map<String, Object>>> future = new CompletableFuture<>();

    vertx.<Optional<Map<String, Object>>>executeBlocking(promise -> {
      findByIdAsync(collection, id).onComplete(ar -> {
        if (ar.succeeded()) {
          promise.complete(ar.result());
        } else {
          promise.fail(ar.cause());
        }
      });
    }, false, res -> {
      if (res.succeeded()) {
        future.complete(res.result());
      } else {
        future.completeExceptionally(res.cause());
      }
    });

    try {
      return future.get();
    } catch (Exception e) {
      LOG.error("Failed to find data by id", e);
      return Optional.empty();
    }
  }

  private Future<Optional<Map<String, Object>>> findByIdAsync(String collection, String id) {
    Promise<Optional<Map<String, Object>>> promise = Promise.promise();

    try {
      Pool pool = dataSourceManager.getDefaultPool();

      String sql = String.format("SELECT * FROM %s WHERE id = ?", collection);
      LOG.debug("Executing SQL: {}", sql);

      pool.preparedQuery(sql)
          .execute(Tuple.of(id))
          .onSuccess(rows -> {
            if (rows.size() > 0) {
              Row row = rows.iterator().next();
              Map<String, Object> result = rowToMap(row);
              promise.complete(Optional.of(result));
            } else {
              promise.complete(Optional.empty());
            }
          })
          .onFailure(err -> {
            LOG.error("Failed to find data by id", err);
            promise.fail(err);
          });

    } catch (Exception e) {
      promise.fail(e);
    }

    return promise.future();
  }

  @Override
  public List<Map<String, Object>> findAll(String collection) {
    LOG.debug("Finding all data, collection: {}", collection);

    CompletableFuture<List<Map<String, Object>>> future = new CompletableFuture<>();

    vertx.<List<Map<String, Object>>>executeBlocking(promise -> {
      findAllAsync(collection).onComplete(ar -> {
        if (ar.succeeded()) {
          promise.complete(ar.result());
        } else {
          promise.fail(ar.cause());
        }
      });
    }, false, res -> {
      if (res.succeeded()) {
        future.complete(res.result());
      } else {
        future.completeExceptionally(res.cause());
      }
    });

    try {
      return future.get();
    } catch (Exception e) {
      LOG.error("Failed to find all data", e);
      // 失败时返回内存缓存的数据
      return findAllFromMemory(collection);
    }
  }

  private Future<List<Map<String, Object>>> findAllAsync(String collection) {
    Promise<List<Map<String, Object>>> promise = Promise.promise();

    try {
      Pool pool = dataSourceManager.getDefaultPool();

      String sql = String.format("SELECT * FROM %s", collection);
      LOG.debug("Executing SQL: {}", sql);

      pool.query(sql)
          .execute()
          .onSuccess(rows -> {
            List<Map<String, Object>> results = new ArrayList<>();
            LOG.debug("Query returned {} rows", rows.size());
            for (Row row : rows) {
              Map<String, Object> rowData = rowToMap(row);
              LOG.debug("Row data: {}", rowData);
              results.add(rowData);
            }
            LOG.debug("Total results: {}", results.size());
            promise.complete(results);
          })
          .onFailure(err -> {
            LOG.error("Failed to find all data", err);
            promise.fail(err);
          });

    } catch (Exception e) {
      promise.fail(e);
    }

    return promise.future();
  }

  @Override
  public List<Map<String, Object>> findByCriteria(String collection, Map<String, Object> criteria) {
    // 简化实现，委托给 findAll 然后在内存中过滤
    return findAll(collection).stream()
        .filter(data -> matchesCriteria(data, criteria))
        .collect(Collectors.toList());
  }

  @Override
  public boolean update(String collection, String id, Map<String, Object> data) {
    LOG.debug("Updating data, collection: {}, ID: {}", collection, id);

    CompletableFuture<Boolean> future = new CompletableFuture<>();

    vertx.<Boolean>executeBlocking(promise -> {
      updateAsync(collection, id, data).onComplete(ar -> {
        if (ar.succeeded()) {
          promise.complete(ar.result());
        } else {
          promise.fail(ar.cause());
        }
      });
    }, false, res -> {
      if (res.succeeded()) {
        future.complete(res.result());
      } else {
        future.completeExceptionally(res.cause());
      }
    });

    try {
      return future.get();
    } catch (Exception e) {
      LOG.error("Failed to update data", e);
      return false;
    }
  }

  private Future<Boolean> updateAsync(String collection, String id, Map<String, Object> data) {
    Promise<Boolean> promise = Promise.promise();

    try {
      Pool pool = dataSourceManager.getDefaultPool();

      // 准备更新数据 - 不修改原始数据，只是复制
      Map<String, Object> updateData = new HashMap<>(data);
      // 移除不应该更新的字段
      updateData.remove("id");

      // 构建 UPDATE SQL
      List<String> setClauses = new ArrayList<>();
      List<Object> values = new ArrayList<>();

      updateData.forEach((key, value) -> {
        String columnName = camelToSnake(key);
        setClauses.add(columnName + " = ?");
        // Convert value to database-compatible format
        values.add(convertForDatabase(value));
      });

      values.add(id);

      String sql = String.format("UPDATE %s SET %s WHERE id = ?",
          collection,
          String.join(", ", setClauses));

      LOG.debug("Executing SQL: {}", sql);

      pool.preparedQuery(sql)
          .execute(Tuple.from(values))
          .onSuccess(rows -> {
            boolean success = rows.rowCount() > 0;
            if (success) {
              LOG.info("Data updated successfully, collection: {}, ID: {}", collection, id);
            }
            promise.complete(success);
          })
          .onFailure(err -> {
            LOG.error("Failed to update data", err);
            promise.fail(err);
          });

    } catch (Exception e) {
      promise.fail(e);
    }

    return promise.future();
  }

  @Override
  public boolean delete(String collection, String id) {
    LOG.debug("Deleting data, collection: {}, ID: {}", collection, id);

    CompletableFuture<Boolean> future = new CompletableFuture<>();

    vertx.<Boolean>executeBlocking(promise -> {
      deleteAsync(collection, id).onComplete(ar -> {
        if (ar.succeeded()) {
          promise.complete(ar.result());
        } else {
          promise.fail(ar.cause());
        }
      });
    }, false, res -> {
      if (res.succeeded()) {
        future.complete(res.result());
      } else {
        future.completeExceptionally(res.cause());
      }
    });

    try {
      return future.get();
    } catch (Exception e) {
      LOG.error("Failed to delete data", e);
      return false;
    }
  }

  private Future<Boolean> deleteAsync(String collection, String id) {
    Promise<Boolean> promise = Promise.promise();

    try {
      Pool pool = dataSourceManager.getDefaultPool();

      String sql = String.format("DELETE FROM %s WHERE id = ?", collection);
      LOG.debug("Executing SQL: {}", sql);

      pool.preparedQuery(sql)
          .execute(Tuple.of(id))
          .onSuccess(rows -> {
            boolean success = rows.rowCount() > 0;
            if (success) {
              LOG.info("Data deleted successfully, collection: {}, ID: {}", collection, id);
            }
            promise.complete(success);
          })
          .onFailure(err -> {
            LOG.error("Failed to delete data", err);
            promise.fail(err);
          });

    } catch (Exception e) {
      promise.fail(e);
    }

    return promise.future();
  }

  @Override
  public long count(String collection) {
    // 简化实现
    return findAll(collection).size();
  }

  @Override
  public long countByCriteria(String collection, Map<String, Object> criteria) {
    // 简化实现
    return findByCriteria(collection, criteria).size();
  }

  @Override
  public PageResult<Map<String, Object>> query(String collection, QueryOptions options) {
    // 简化实现
    List<Map<String, Object>> allData = findAll(collection);

    // 过滤
    List<Map<String, Object>> filteredData = allData.stream()
        .filter(data -> matchesCriteria(data, options.getFilters()))
        .collect(Collectors.toList());

    // 排序
    if (options.getSortBy() != null) {
      filteredData.sort((a, b) -> {
        Object aValue = a.get(options.getSortBy());
        Object bValue = b.get(options.getSortBy());

        if (aValue == null && bValue == null)
          return 0;
        if (aValue == null)
          return options.isAscending() ? -1 : 1;
        if (bValue == null)
          return options.isAscending() ? 1 : -1;

        int result = 0;
        if (aValue instanceof Comparable && bValue instanceof Comparable) {
          result = ((Comparable) aValue).compareTo(bValue);
        } else {
          result = aValue.toString().compareTo(bValue.toString());
        }

        return options.isAscending() ? result : -result;
      });
    }

    // 分页
    int total = filteredData.size();
    int fromIndex = Math.min(options.getOffset(), total);
    int toIndex = Math.min(fromIndex + options.getPageSize(), total);

    List<Map<String, Object>> pageData = filteredData.subList(fromIndex, toIndex);

    return new PageResult<>(pageData, total, options.getPage(), options.getPageSize());
  }

  @Override
  public int batchSave(String collection, List<Map<String, Object>> dataList) {
    int savedCount = 0;
    for (Map<String, Object> data : dataList) {
      try {
        save(collection, data);
        savedCount++;
      } catch (Exception e) {
        LOG.error("Batch save failed for one record", e);
      }
    }
    return savedCount;
  }

  @Override
  public int batchDelete(String collection, List<String> ids) {
    int deletedCount = 0;
    for (String id : ids) {
      if (delete(collection, id)) {
        deletedCount++;
      }
    }
    return deletedCount;
  }

  @Override
  public <T extends BaseEntity> Repository<T> getRepository(CollectionDef collectionDef, Class<T> entityClass) {
    return new RepositoryImpl<>(this, collectionDef, entityClass);
  }

  @Override
  public <T extends BaseEntity> Repository<T> getRepository(String table, Class<T> entityClass) {
    CollectionDef collectionDef = CollectionDef.builder(table).entityClass(entityClass).build();
    return new RepositoryImpl<>(this, collectionDef, entityClass);
  }

  @Override
  public <T extends BaseEntity> Repository<T> getRepository(String dataSource, String table, Class<T> entityClass) {
    CollectionDef collectionDef = CollectionDef.builder(table)
        .dataSource(dataSource)
        .entityClass(entityClass)
        .build();
    return new RepositoryImpl<>(this, collectionDef, entityClass);
  }

  // 辅助方法

  private Map<String, Object> rowToMap(Row row) {
    Map<String, Object> map = new HashMap<>();
    for (int i = 0; i < row.size(); i++) {
      String columnName = row.getColumnName(i);
      Object value = row.getValue(i);
      // Convert database values to application-friendly format
      value = convertFromDatabase(value);
      // 转换 snake_case 到 camelCase
      String key = snakeToCamel(columnName);
      map.put(key, value);
    }
    return map;
  }

  /**
   * Convert values to database-compatible format
   */
  private Object convertForDatabase(Object value) {
    if (value == null) {
      return null;
    }

    // Convert Java Date to Timestamp
    if (value instanceof Date) {
      return new Timestamp(((Date) value).getTime());
    }

    // Convert Long timestamp to Timestamp (if it looks like a timestamp)
    if (value instanceof Long) {
      Long longValue = (Long) value;
      // Check if this might be a timestamp (year 2000 to 2100 range)
      if (longValue > 946684800000L && longValue < 4102444800000L) {
        return new Timestamp(longValue);
      }
    }

    // Return value as-is for other types
    return value;
  }

  /**
   * Convert database values to application-friendly format
   */
  private Object convertFromDatabase(Object value) {
    if (value == null) {
      return null;
    }

    // Convert LocalDateTime to timestamp for JSON serialization
    if (value instanceof LocalDateTime) {
      LocalDateTime dateTime = (LocalDateTime) value;
      return dateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    // Convert Timestamp to Long
    if (value instanceof Timestamp) {
      return ((Timestamp) value).getTime();
    }

    // Return value as-is for other types
    return value;
  }

  private String camelToSnake(String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);
      if (Character.isUpperCase(c)) {
        if (i > 0) {
          sb.append('_');
        }
        sb.append(Character.toLowerCase(c));
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  private String snakeToCamel(String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }
    StringBuilder sb = new StringBuilder();
    boolean nextUpper = false;
    for (int i = 0; i < str.length(); i++) {
      char c = str.charAt(i);
      if (c == '_') {
        nextUpper = true;
      } else {
        if (nextUpper) {
          sb.append(Character.toUpperCase(c));
          nextUpper = false;
        } else {
          sb.append(c);
        }
      }
    }
    return sb.toString();
  }

  private boolean matchesCriteria(Map<String, Object> data, Map<String, Object> criteria) {
    if (criteria == null || criteria.isEmpty()) {
      return true;
    }
    for (Map.Entry<String, Object> entry : criteria.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      if (!data.containsKey(key)) {
        return false;
      }

      Object dataValue = data.get(key);
      if (!Objects.equals(value, dataValue)) {
        return false;
      }
    }
    return true;
  }

  // 内存缓存相关方法（用于降级）

  private Map<String, Object> saveToMemory(String collection, Map<String, Object> data) {
    Map<String, Map<String, Object>> collectionData = getOrCreateCollection(collection);

    // 要求必须有 ID
    if (!data.containsKey("id")) {
      throw new IllegalArgumentException("ID is required");
    }

    String id = String.valueOf(data.get("id"));
    Map<String, Object> savedData = new HashMap<>(data);
    collectionData.put(id, savedData);

    LOG.info("Data saved to memory cache, collection: {}, ID: {}", collection, id);
    return new HashMap<>(savedData);
  }

  private List<Map<String, Object>> findAllFromMemory(String collection) {
    Map<String, Map<String, Object>> collectionData = memoryCache.get(collection);
    if (collectionData == null) {
      return Collections.emptyList();
    }
    return collectionData.values().stream()
        .map(HashMap::new)
        .collect(Collectors.toList());
  }

  private Map<String, Map<String, Object>> getOrCreateCollection(String collection) {
    return memoryCache.computeIfAbsent(collection, k -> new ConcurrentHashMap<>());
  }

  @Override
  public List<String> listCollections() {
    LOG.debug("Listing all collections");

    // 如果有数据库连接，查询数据库中的表
    Pool pool = dataSourceManager.getDefaultPool();
    if (pool != null) {
      try {
        CompletableFuture<List<String>> future = new CompletableFuture<>();

        // 获取默认数据源配置来判断数据库类型
        Map<String, JsonObject> configs = dataSourceManager.getDataSourceConfigs();
        String defaultDs = dataSourceManager.getDefaultDataSource();
        JsonObject config = configs.get(defaultDs);
        String dbType = config != null ? config.getString("type", "postgresql") : "postgresql";

        // 根据数据库类型使用不同的查询
        String query = dbType.toLowerCase().contains("postgres")
            ? "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE'"
            : "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'";

        pool.query(query)
            .execute()
            .onSuccess(rows -> {
              List<String> collections = new ArrayList<>();
              for (Row row : rows) {
                collections.add(row.getString("TABLE_NAME"));
              }
              future.complete(collections);
            })
            .onFailure(err -> {
              LOG.error("Failed to list collections from database", err);
              // 失败时返回内存中的集合
              future.complete(new ArrayList<>(memoryCache.keySet()));
            });

        return future.get();
      } catch (Exception e) {
        LOG.error("Error listing collections", e);
      }
    }

    // 如果没有数据库连接，返回内存中的集合
    return new ArrayList<>(memoryCache.keySet());
  }
}