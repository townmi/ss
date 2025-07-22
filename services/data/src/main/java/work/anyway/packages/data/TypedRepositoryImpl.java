package work.anyway.packages.data;

import io.vertx.core.Vertx;
import io.vertx.sqlclient.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.anyway.interfaces.data.*;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 类型安全的仓库实现
 * 直接操作实体对象，无需 Map 转换
 * 
 * @param <T> 实体类型
 * @author 作者名
 * @since 1.0.0
 */
public class TypedRepositoryImpl<T extends Entity> implements Repository<T> {

  private static final Logger LOG = LoggerFactory.getLogger(TypedRepositoryImpl.class);

  private final Vertx vertx;
  private final Pool pool;
  private final Class<T> entityClass;
  private final EntityMetadata<T> metadata;

  public TypedRepositoryImpl(Vertx vertx, Pool pool, Class<T> entityClass) {
    this.vertx = vertx;
    this.pool = pool;
    this.entityClass = entityClass;
    this.metadata = EntityMetadata.of(entityClass);

    LOG.info("Created TypedRepository for entity: {}, table: {}",
        entityClass.getSimpleName(), metadata.getTableName());
  }

  @Override
  public T save(T entity) {
    if (entity.getId() == null || entity.getId().isEmpty()) {
      entity.setId(UUID.randomUUID().toString());
      entity.setCreatedAt(new Date());
    }
    entity.setUpdatedAt(new Date());

    CompletableFuture<T> future = new CompletableFuture<>();

    vertx.<T>executeBlocking(promise -> {
      String sql = metadata.getInsertSql();
      Tuple params = entityToTuple(entity);

      LOG.debug("Executing INSERT: {}", sql);
      LOG.debug("Parameters: {}", params);

      pool.preparedQuery(sql)
          .execute(params)
          .onSuccess(rows -> {
            LOG.debug("Entity saved successfully: {}", entity.getId());
            promise.complete(entity);
          })
          .onFailure(err -> {
            LOG.error("Failed to save entity", err);
            promise.fail(err);
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
      throw new RuntimeException("Failed to save entity", e);
    }
  }

  @Override
  public Optional<T> findById(String id) {
    CompletableFuture<Optional<T>> future = new CompletableFuture<>();

    vertx.<Optional<T>>executeBlocking(promise -> {
      String sql = metadata.getSelectByIdSql();

      LOG.debug("Executing SELECT: {}", sql);
      LOG.debug("ID: {}", id);

      pool.preparedQuery(sql)
          .execute(Tuple.of(id))
          .onSuccess(rows -> {
            if (rows.size() > 0) {
              T entity = rowToEntity(rows.iterator().next());
              promise.complete(Optional.of(entity));
            } else {
              promise.complete(Optional.empty());
            }
          })
          .onFailure(err -> {
            LOG.error("Failed to find entity by id", err);
            promise.fail(err);
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
      LOG.error("Failed to find entity by id", e);
      return Optional.empty();
    }
  }

  @Override
  public List<T> findAll() {
    CompletableFuture<List<T>> future = new CompletableFuture<>();

    vertx.<List<T>>executeBlocking(promise -> {
      String sql = "SELECT * FROM " + metadata.getFullTableName();

      LOG.debug("Executing SELECT ALL: {}", sql);

      pool.query(sql)
          .execute()
          .onSuccess(rows -> {
            List<T> entities = new ArrayList<>();
            for (Row row : rows) {
              entities.add(rowToEntity(row));
            }
            promise.complete(entities);
          })
          .onFailure(err -> {
            LOG.error("Failed to find all entities", err);
            promise.fail(err);
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
      LOG.error("Failed to find all entities", e);
      return Collections.emptyList();
    }
  }

  @Override
  public List<T> findBy(QueryCriteria<T> criteria) {
    CompletableFuture<List<T>> future = new CompletableFuture<>();

    vertx.<List<T>>executeBlocking(promise -> {
      // 构建 WHERE 子句
      StringBuilder sql = new StringBuilder("SELECT * FROM ");
      sql.append(metadata.getFullTableName());

      List<Object> params = new ArrayList<>();
      Map<String, Object> conditions = criteria.getConditions();

      if (!conditions.isEmpty()) {
        sql.append(" WHERE ");
        StringJoiner whereClause = new StringJoiner(" AND ");

        conditions.forEach((key, value) -> {
          EntityMetadata.FieldMetadata field = metadata.getFields().get(key);
          if (field != null) {
            whereClause.add(field.getColumnName() + " = ?");
            params.add(value);
          }
        });

        sql.append(whereClause);
      }

      // 添加排序
      if (criteria.getOrderBy() != null) {
        EntityMetadata.FieldMetadata field = metadata.getFields().get(criteria.getOrderBy());
        if (field != null) {
          sql.append(" ORDER BY ").append(field.getColumnName());
          sql.append(criteria.isAscending() ? " ASC" : " DESC");
        }
      }

      LOG.debug("Executing QUERY: {}", sql);
      LOG.debug("Parameters: {}", params);

      pool.preparedQuery(sql.toString())
          .execute(Tuple.from(params))
          .onSuccess(rows -> {
            List<T> entities = new ArrayList<>();
            for (Row row : rows) {
              entities.add(rowToEntity(row));
            }

            // 应用自定义过滤器
            if (criteria.getCustomFilter() != null) {
              entities = entities.stream()
                  .filter(criteria.getCustomFilter())
                  .collect(Collectors.toList());
            }

            promise.complete(entities);
          })
          .onFailure(err -> {
            LOG.error("Failed to query entities", err);
            promise.fail(err);
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
      LOG.error("Failed to query entities", e);
      return Collections.emptyList();
    }
  }

  @Override
  public boolean update(T entity) {
    if (entity.getId() == null || entity.getId().isEmpty()) {
      throw new IllegalArgumentException("Entity must have ID for update");
    }

    entity.setUpdatedAt(new Date());

    CompletableFuture<Boolean> future = new CompletableFuture<>();

    vertx.<Boolean>executeBlocking(promise -> {
      String sql = metadata.getUpdateSql();
      Tuple params = updateToTuple(entity);

      LOG.debug("Executing UPDATE: {}", sql);
      LOG.debug("Parameters: {}", params);

      pool.preparedQuery(sql)
          .execute(params)
          .onSuccess(rows -> {
            promise.complete(rows.rowCount() > 0);
          })
          .onFailure(err -> {
            LOG.error("Failed to update entity", err);
            promise.fail(err);
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
      LOG.error("Failed to update entity", e);
      return false;
    }
  }

  @Override
  public boolean delete(String id) {
    CompletableFuture<Boolean> future = new CompletableFuture<>();

    vertx.<Boolean>executeBlocking(promise -> {
      String sql = metadata.getDeleteSql();

      LOG.debug("Executing DELETE: {}", sql);
      LOG.debug("ID: {}", id);

      pool.preparedQuery(sql)
          .execute(Tuple.of(id))
          .onSuccess(rows -> {
            promise.complete(rows.rowCount() > 0);
          })
          .onFailure(err -> {
            LOG.error("Failed to delete entity", err);
            promise.fail(err);
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
      LOG.error("Failed to delete entity", e);
      return false;
    }
  }

  @Override
  public int batchSave(List<T> entities) {
    int saved = 0;
    for (T entity : entities) {
      try {
        save(entity);
        saved++;
      } catch (Exception e) {
        LOG.error("Failed to save entity in batch", e);
      }
    }
    return saved;
  }

  @Override
  public int batchDelete(List<String> ids) {
    int deleted = 0;
    for (String id : ids) {
      if (delete(id)) {
        deleted++;
      }
    }
    return deleted;
  }

  @Override
  public PageResult<T> findPage(QueryOptions options) {
    // 简化实现，实际应该使用 LIMIT 和 OFFSET
    List<T> allData = findAll();

    // 应用过滤
    if (!options.getFilters().isEmpty()) {
      allData = allData.stream()
          .filter(entity -> matchesFilters(entity, options.getFilters()))
          .collect(Collectors.toList());
    }

    // 应用排序
    if (options.getSortBy() != null) {
      EntityMetadata.FieldMetadata field = metadata.getFields().get(options.getSortBy());
      if (field != null) {
        allData.sort((a, b) -> {
          Object aValue = field.getValue(a);
          Object bValue = field.getValue(b);

          if (aValue == null && bValue == null)
            return 0;
          if (aValue == null)
            return options.isAscending() ? -1 : 1;
          if (bValue == null)
            return options.isAscending() ? 1 : -1;

          if (aValue instanceof Comparable && bValue instanceof Comparable) {
            int result = ((Comparable) aValue).compareTo(bValue);
            return options.isAscending() ? result : -result;
          }

          return 0;
        });
      }
    }

    // 分页
    int total = allData.size();
    int fromIndex = Math.min(options.getOffset(), total);
    int toIndex = Math.min(fromIndex + options.getPageSize(), total);

    List<T> pageData = allData.subList(fromIndex, toIndex);

    return new PageResult<>(pageData, total, options.getPage(), options.getPageSize());
  }

  @Override
  public long count() {
    // 简化实现
    return findAll().size();
  }

  @Override
  public long countBy(QueryCriteria<T> criteria) {
    // 简化实现
    return findBy(criteria).size();
  }

  /**
   * 将实体转换为 Tuple（用于 INSERT）
   */
  private Tuple entityToTuple(T entity) {
    List<Object> values = new ArrayList<>();

    for (EntityMetadata.FieldMetadata field : metadata.getPersistentFields()) {
      Object value = field.getValue(entity);
      values.add(convertForDatabase(value));
    }

    return Tuple.from(values);
  }

  /**
   * 将实体转换为 Tuple（用于 UPDATE）
   */
  private Tuple updateToTuple(T entity) {
    List<Object> values = new ArrayList<>();

    // 先添加非主键字段
    for (EntityMetadata.FieldMetadata field : metadata.getPersistentFields()) {
      if (!field.isPrimaryKey()) {
        Object value = field.getValue(entity);
        values.add(convertForDatabase(value));
      }
    }

    // 最后添加主键
    if (metadata.getPrimaryKeyField() != null) {
      Object pkValue = metadata.getPrimaryKeyField().getValue(entity);
      values.add(convertForDatabase(pkValue));
    }

    return Tuple.from(values);
  }

  /**
   * 将 Row 转换为实体
   */
  private T rowToEntity(Row row) {
    try {
      T entity = entityClass.getDeclaredConstructor().newInstance();

      for (EntityMetadata.FieldMetadata field : metadata.getPersistentFields()) {
        String columnName = field.getColumnName();

        // 检查列是否存在
        try {
          Object value = row.getValue(columnName);

          // 类型转换
          value = convertFromDatabase(value, field.getField().getType());

          field.setValue(entity, value);
        } catch (NoSuchElementException e) {
          // 列不存在，跳过
          LOG.debug("Column {} not found in result set, skipping", columnName);
        }
      }

      return entity;
    } catch (Exception e) {
      throw new RuntimeException("Failed to create entity from row", e);
    }
  }

  /**
   * 转换值为数据库兼容格式
   */
  private Object convertForDatabase(Object value) {
    if (value == null) {
      return null;
    }

    // Date 转 LocalDateTime
    if (value instanceof Date) {
      return ((Date) value).toInstant()
          .atZone(ZoneId.systemDefault())
          .toLocalDateTime();
    }

    // Boolean 转 Integer (0/1)
    if (value instanceof Boolean) {
      return ((Boolean) value) ? 1 : 0;
    }

    // Enum 转 String
    if (value instanceof Enum) {
      return ((Enum<?>) value).name();
    }

    return value;
  }

  /**
   * 从数据库格式转换值
   */
  private Object convertFromDatabase(Object value, Class<?> targetType) {
    if (value == null) {
      return null;
    }

    // LocalDateTime 转 Date
    if (targetType == Date.class && value instanceof LocalDateTime) {
      return Date.from(((LocalDateTime) value)
          .atZone(ZoneId.systemDefault())
          .toInstant());
    }

    // Integer/Boolean 转 Boolean
    if (targetType == Boolean.class || targetType == boolean.class) {
      if (value instanceof Number) {
        return ((Number) value).intValue() != 0;
      }
      if (value instanceof Boolean) {
        return value;
      }
    }

    // String 转 Enum
    if (targetType.isEnum() && value instanceof String) {
      String stringValue = (String) value;
      // 尝试直接匹配
      try {
        return Enum.valueOf((Class<Enum>) targetType, stringValue);
      } catch (IllegalArgumentException e) {
        // 尝试大写匹配
        try {
          return Enum.valueOf((Class<Enum>) targetType, stringValue.toUpperCase());
        } catch (IllegalArgumentException e2) {
          // 尝试通过反射查找具有 getCode() 方法的枚举
          try {
            Object[] enumConstants = targetType.getEnumConstants();
            for (Object enumConstant : enumConstants) {
              try {
                // 尝试调用 getCode() 方法
                java.lang.reflect.Method getCodeMethod = targetType.getMethod("getCode");
                Object code = getCodeMethod.invoke(enumConstant);
                if (stringValue.equals(code)) {
                  return enumConstant;
                }
              } catch (NoSuchMethodException nsme) {
                // 没有 getCode 方法，跳过
                break;
              }
            }
          } catch (Exception ex) {
            // 忽略反射错误
          }
          throw new IllegalArgumentException("Cannot convert '" + stringValue + "' to " + targetType.getName());
        }
      }
    }

    return value;
  }

  /**
   * 检查实体是否匹配过滤条件
   */
  private boolean matchesFilters(T entity, Map<String, Object> filters) {
    for (Map.Entry<String, Object> filter : filters.entrySet()) {
      EntityMetadata.FieldMetadata field = metadata.getFields().get(filter.getKey());
      if (field != null) {
        Object entityValue = field.getValue(entity);
        Object filterValue = filter.getValue();

        if (!Objects.equals(entityValue, filterValue)) {
          return false;
        }
      }
    }
    return true;
  }
}