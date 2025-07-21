package work.anyway.packages.data;

import io.vertx.core.Promise;
import io.vertx.sqlclient.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.anyway.interfaces.data.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 同步数据库数据服务实现
 * 使用阻塞方式访问数据库，适用于没有 Vertx 环境的场景
 * 
 * @author 作者名
 * @since 1.0.0
 */
public class SyncDatabaseDataServiceImpl implements TypedDataService {

  private static final Logger LOG = LoggerFactory.getLogger(SyncDatabaseDataServiceImpl.class);

  private final DataSourceManager dataSourceManager;

  /**
   * 构造函数
   * 
   * @param dataSourceManager 数据源管理器
   */
  public SyncDatabaseDataServiceImpl(DataSourceManager dataSourceManager) {
    this.dataSourceManager = dataSourceManager;
    LOG.info("SyncDatabaseDataServiceImpl initialized: Using synchronous database storage mode");
  }

  @Override
  public Map<String, Object> save(String collection, Map<String, Object> data) {
    LOG.debug("Saving data to collection: {}", collection);

    try {
      Pool pool = dataSourceManager.getDefaultPool();

      // 要求必须有 ID
      if (!data.containsKey("id")) {
        throw new IllegalArgumentException("ID is required");
      }

      String id = String.valueOf(data.get("id"));

      // 准备数据 - 不修改原始数据
      Map<String, Object> savedData = new HashMap<>(data);

      // 构建 INSERT SQL
      List<String> columns = new ArrayList<>();
      List<String> placeholders = new ArrayList<>();
      List<Object> values = new ArrayList<>();

      savedData.forEach((key, value) -> {
        // 转换 camelCase 到 snake_case
        String columnName = camelToSnake(key);
        columns.add(columnName);
        placeholders.add("?");
        values.add(value);
      });

      String sql = String.format("INSERT INTO %s (%s) VALUES (%s)",
          collection,
          String.join(", ", columns),
          String.join(", ", placeholders));

      LOG.debug("Executing SQL: {}", sql);

      // 执行同步查询（简化实现）
      Promise<RowSet<Row>> promise = Promise.promise();
      pool.preparedQuery(sql)
          .execute(Tuple.from(values), promise);

      // 等待结果（注意：这会阻塞，实际应用中应该使用异步方式）
      while (!promise.future().isComplete()) {
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("Interrupted while waiting for database operation", e);
        }
      }

      if (promise.future().succeeded()) {
        LOG.info("Data saved to database successfully, collection: {}, ID: {}", collection, id);
        return savedData;
      } else {
        throw new RuntimeException("Failed to save data: " + promise.future().cause().getMessage());
      }

    } catch (Exception e) {
      LOG.error("Failed to save data to database", e);
      throw new RuntimeException("Database operation failed", e);
    }
  }

  @Override
  public Optional<Map<String, Object>> findById(String collection, String id) {
    LOG.debug("Finding data by ID, collection: {}, ID: {}", collection, id);

    try {
      Pool pool = dataSourceManager.getDefaultPool();

      String sql = String.format("SELECT * FROM %s WHERE id = ?", collection);
      LOG.debug("Executing SQL: {}", sql);

      Promise<RowSet<Row>> promise = Promise.promise();
      pool.preparedQuery(sql)
          .execute(Tuple.of(id), promise);

      // 等待结果
      while (!promise.future().isComplete()) {
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("Interrupted while waiting for database operation", e);
        }
      }

      if (promise.future().succeeded()) {
        RowSet<Row> rows = promise.future().result();
        if (rows.size() > 0) {
          Row row = rows.iterator().next();
          Map<String, Object> result = rowToMap(row);
          return Optional.of(result);
        }
        return Optional.empty();
      } else {
        throw new RuntimeException("Failed to find data: " + promise.future().cause().getMessage());
      }

    } catch (Exception e) {
      LOG.error("Failed to find data in database", e);
      return Optional.empty();
    }
  }

  @Override
  public List<Map<String, Object>> findAll(String collection) {
    LOG.debug("Finding all data, collection: {}", collection);

    try {
      Pool pool = dataSourceManager.getDefaultPool();

      String sql = String.format("SELECT * FROM %s", collection);
      LOG.debug("Executing SQL: {}", sql);

      Promise<RowSet<Row>> promise = Promise.promise();
      pool.query(sql)
          .execute(promise);

      // 等待结果
      while (!promise.future().isComplete()) {
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("Interrupted while waiting for database operation", e);
        }
      }

      if (promise.future().succeeded()) {
        RowSet<Row> rows = promise.future().result();
        List<Map<String, Object>> results = new ArrayList<>();
        for (Row row : rows) {
          results.add(rowToMap(row));
        }
        return results;
      } else {
        throw new RuntimeException("Failed to find all data: " + promise.future().cause().getMessage());
      }

    } catch (Exception e) {
      LOG.error("Failed to find all data in database", e);
      return Collections.emptyList();
    }
  }

  @Override
  public List<Map<String, Object>> findByCriteria(String collection, Map<String, Object> criteria) {
    LOG.debug("Finding data by criteria, collection: {}, criteria: {}", collection, criteria);

    // 简化实现，实际应该构建 WHERE 子句
    return findAll(collection).stream()
        .filter(data -> matchesCriteria(data, criteria))
        .collect(Collectors.toList());
  }

  @Override
  public boolean update(String collection, String id, Map<String, Object> data) {
    LOG.debug("Updating data, collection: {}, ID: {}", collection, id);

    try {
      Pool pool = dataSourceManager.getDefaultPool();

      // 准备更新数据 - 不修改原始数据
      Map<String, Object> updateData = new HashMap<>(data);
      // 移除不应该更新的字段
      updateData.remove("id");
      updateData.remove("created_at");

      // 构建 UPDATE SQL
      List<String> setClauses = new ArrayList<>();
      List<Object> values = new ArrayList<>();

      updateData.forEach((key, value) -> {
        String columnName = camelToSnake(key);
        setClauses.add(columnName + " = ?");
        values.add(value);
      });

      values.add(id); // WHERE 条件的值

      String sql = String.format("UPDATE %s SET %s WHERE id = ?",
          collection,
          String.join(", ", setClauses));

      LOG.debug("Executing SQL: {}", sql);

      Promise<RowSet<Row>> promise = Promise.promise();
      pool.preparedQuery(sql)
          .execute(Tuple.from(values), promise);

      // 等待结果
      while (!promise.future().isComplete()) {
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("Interrupted while waiting for database operation", e);
        }
      }

      if (promise.future().succeeded()) {
        RowSet<Row> result = promise.future().result();
        boolean success = result.rowCount() > 0;
        if (success) {
          LOG.info("Data updated in database successfully, collection: {}, ID: {}", collection, id);
        }
        return success;
      } else {
        throw new RuntimeException("Failed to update data: " + promise.future().cause().getMessage());
      }

    } catch (Exception e) {
      LOG.error("Failed to update data in database", e);
      return false;
    }
  }

  @Override
  public boolean delete(String collection, String id) {
    LOG.debug("Deleting data, collection: {}, ID: {}", collection, id);

    try {
      Pool pool = dataSourceManager.getDefaultPool();

      String sql = String.format("DELETE FROM %s WHERE id = ?", collection);
      LOG.debug("Executing SQL: {}", sql);

      Promise<RowSet<Row>> promise = Promise.promise();
      pool.preparedQuery(sql)
          .execute(Tuple.of(id), promise);

      // 等待结果
      while (!promise.future().isComplete()) {
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("Interrupted while waiting for database operation", e);
        }
      }

      if (promise.future().succeeded()) {
        RowSet<Row> result = promise.future().result();
        boolean success = result.rowCount() > 0;
        if (success) {
          LOG.info("Data deleted from database successfully, collection: {}, ID: {}", collection, id);
        }
        return success;
      } else {
        throw new RuntimeException("Failed to delete data: " + promise.future().cause().getMessage());
      }

    } catch (Exception e) {
      LOG.error("Failed to delete data from database", e);
      return false;
    }
  }

  @Override
  public long count(String collection) {
    LOG.debug("Counting data, collection: {}", collection);

    try {
      Pool pool = dataSourceManager.getDefaultPool();

      String sql = String.format("SELECT COUNT(*) FROM %s", collection);
      LOG.debug("Executing SQL: {}", sql);

      Promise<RowSet<Row>> promise = Promise.promise();
      pool.query(sql)
          .execute(promise);

      // 等待结果
      while (!promise.future().isComplete()) {
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("Interrupted while waiting for database operation", e);
        }
      }

      if (promise.future().succeeded()) {
        RowSet<Row> rows = promise.future().result();
        if (rows.size() > 0) {
          Row row = rows.iterator().next();
          return row.getLong(0);
        }
        return 0;
      } else {
        throw new RuntimeException("Failed to count data: " + promise.future().cause().getMessage());
      }

    } catch (Exception e) {
      LOG.error("Failed to count data in database", e);
      return 0;
    }
  }

  @Override
  public long countByCriteria(String collection, Map<String, Object> criteria) {
    LOG.debug("Counting data by criteria, collection: {}, criteria: {}", collection, criteria);

    // 简化实现，实际应该构建 WHERE 子句
    return findAll(collection).stream()
        .filter(data -> matchesCriteria(data, criteria))
        .count();
  }

  @Override
  public PageResult<Map<String, Object>> query(String collection, QueryOptions options) {
    LOG.debug("Paging query data, collection: {}, options: page={}, pageSize={}, sortBy={}",
        collection, options.getPage(), options.getPageSize(), options.getSortBy());

    try {
      Pool pool = dataSourceManager.getDefaultPool();

      // 构建 SELECT SQL
      String selectColumns = "*"; // 查询所有列
      String whereClause = buildWhereClause(options.getFilters());
      String orderByClause = buildOrderByClause(options.getSortBy(), options.isAscending());
      String limitClause = buildLimitClause(options.getOffset(), options.getPageSize());

      String sql = String.format("SELECT %s FROM %s %s %s %s",
          selectColumns,
          collection,
          whereClause,
          orderByClause,
          limitClause);

      LOG.debug("Executing SQL: {}", sql);

      Promise<RowSet<Row>> promise = Promise.promise();
      pool.preparedQuery(sql)
          .execute(buildTupleForFilters(options.getFilters()), promise);

      // 等待结果
      while (!promise.future().isComplete()) {
        try {
          Thread.sleep(10);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new RuntimeException("Interrupted while waiting for database operation", e);
        }
      }

      if (promise.future().succeeded()) {
        RowSet<Row> rows = promise.future().result();
        List<Map<String, Object>> results = new ArrayList<>();
        for (Row row : rows) {
          results.add(rowToMap(row));
        }

        // 获取总数
        long total = countByCriteria(collection, options.getFilters());
        return new PageResult<>(results, total, options.getPage(), options.getPageSize());
      } else {
        throw new RuntimeException("Failed to query data from database: " + promise.future().cause().getMessage());
      }

    } catch (Exception e) {
      LOG.error("Failed to query data from database", e);
      return new PageResult<>(Collections.emptyList(), 0, options.getPage(), options.getPageSize());
    }
  }

  @Override
  public int batchSave(String collection, List<Map<String, Object>> dataList) {
    LOG.debug("Batch saving data, collection: {}, count: {}", collection, dataList.size());

    int savedCount = 0;
    for (Map<String, Object> data : dataList) {
      try {
        save(collection, data);
        savedCount++;
      } catch (Exception e) {
        LOG.error("Batch save failed, skipping this record", e);
      }
    }

    LOG.info("Batch save completed, collection: {}, success: {}/{}", collection, savedCount, dataList.size());
    return savedCount;
  }

  @Override
  public int batchDelete(String collection, List<String> ids) {
    LOG.debug("Batch deleting data, collection: {}, count: {}", collection, ids.size());

    int deletedCount = 0;
    for (String id : ids) {
      if (delete(collection, id)) {
        deletedCount++;
      }
    }

    LOG.info("Batch delete completed, collection: {}, success: {}/{}", collection, deletedCount, ids.size());
    return deletedCount;
  }

  @Override
  public <T extends Entity> Repository<T> getRepository(CollectionDef collectionDef, Class<T> entityClass) {
    return new RepositoryImpl<>(this, collectionDef, entityClass);
  }

  @Override
  public <T extends Entity> Repository<T> getRepository(String table, Class<T> entityClass) {
    CollectionDef collectionDef = CollectionDef.builder(table).entityClass(entityClass).build();
    return new RepositoryImpl<>(this, collectionDef, entityClass);
  }

  @Override
  public <T extends Entity> Repository<T> getRepository(String dataSource, String table, Class<T> entityClass) {
    CollectionDef collectionDef = CollectionDef.builder(table)
        .dataSource(dataSource)
        .entityClass(entityClass)
        .build();
    return new RepositoryImpl<>(this, collectionDef, entityClass);
  }

  /**
   * 将 Row 对象转换为 Map 对象
   */
  private Map<String, Object> rowToMap(Row row) {
    Map<String, Object> map = new HashMap<>();
    for (int i = 0; i < row.size(); i++) {
      String columnName = row.getColumnName(i);
      Object value = row.getValue(i);
      // 转换 snake_case 到 camelCase
      String key = snakeToCamel(columnName);
      map.put(key, value);
    }
    return map;
  }

  /**
   * 将 camelCase 字符串转换为 snake_case 字符串
   */
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

  /**
   * 将 snake_case 字符串转换为 camelCase 字符串
   */
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

  /**
   * 检查数据是否匹配查询条件
   */
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

      // 简单的相等比较
      if (!Objects.equals(value, dataValue)) {
        return false;
      }
    }

    return true;
  }

  private String buildWhereClause(Map<String, Object> filters) {
    if (filters == null || filters.isEmpty()) {
      return "";
    }

    List<String> conditions = new ArrayList<>();
    for (Map.Entry<String, Object> entry : filters.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      // 转换 camelCase 到 snake_case
      String columnName = camelToSnake(key);

      if (value == null) {
        conditions.add(columnName + " IS NULL");
      } else {
        conditions.add(columnName + " = ?");
      }
    }

    return "WHERE " + String.join(" AND ", conditions);
  }

  private String buildOrderByClause(String sortBy, boolean ascending) {
    if (sortBy == null || sortBy.isEmpty()) {
      return "";
    }

    // 转换 camelCase 到 snake_case
    String columnName = camelToSnake(sortBy);
    return "ORDER BY " + columnName + (ascending ? " ASC" : " DESC");
  }

  private String buildLimitClause(int offset, int pageSize) {
    if (pageSize <= 0) {
      return "";
    }

    return "LIMIT " + pageSize + " OFFSET " + offset;
  }

  private Tuple buildTupleForFilters(Map<String, Object> filters) {
    List<Object> values = new ArrayList<>();
    if (filters != null) {
      for (Map.Entry<String, Object> entry : filters.entrySet()) {
        Object value = entry.getValue();

        // 只添加非 null 值，因为 null 值使用 IS NULL 而不是 = ?
        if (value != null) {
          values.add(value);
        }
      }
    }
    return Tuple.from(values);
  }
}