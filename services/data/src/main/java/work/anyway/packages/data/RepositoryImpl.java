package work.anyway.packages.data;

import work.anyway.interfaces.data.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 类型安全的仓库实现
 * 将 DataService 的通用接口包装为类型安全的接口
 * 
 * @param <T> 实体类型
 * @author 作者名
 * @since 1.0.0
 */
public class RepositoryImpl<T extends Entity> implements Repository<T> {

  private static final Logger LOG = LoggerFactory.getLogger(RepositoryImpl.class);

  private final DataService dataService;
  private final CollectionDef collectionDef;
  private final Class<T> entityClass;
  private final ObjectMapper objectMapper;

  /**
   * 构造函数
   * 
   * @param dataService   数据服务
   * @param collectionDef 集合定义
   * @param entityClass   实体类型
   */
  public RepositoryImpl(DataService dataService, CollectionDef collectionDef, Class<T> entityClass) {
    this.dataService = dataService;
    this.collectionDef = collectionDef;
    this.entityClass = entityClass;
    this.objectMapper = new ObjectMapper();

    LOG.info("Creating repository instance: collection={}, entityType={}",
        collectionDef.getFullName(), entityClass.getSimpleName());
  }

  @Override
  public T save(T entity) {
    if (entity.getId() == null || entity.getId().isEmpty()) {
      entity.setId(UUID.randomUUID().toString());
      entity.setCreatedAt(new Date());
    }
    entity.setUpdatedAt(new Date());

    Map<String, Object> data = entityToMap(entity);
    Map<String, Object> saved = dataService.save(collectionDef.getFullName(), data);

    LOG.debug("Entity saved successfully, type: {}, ID: {}", entityClass.getSimpleName(), entity.getId());
    return mapToEntity(saved);
  }

  @Override
  public Optional<T> findById(String id) {
    return dataService.findById(collectionDef.getFullName(), id)
        .map(this::mapToEntity);
  }

  @Override
  public List<T> findAll() {
    return dataService.findAll(collectionDef.getFullName())
        .stream()
        .map(this::mapToEntity)
        .collect(Collectors.toList());
  }

  @Override
  public List<T> findBy(QueryCriteria<T> criteria) {
    // 转换查询条件
    Map<String, Object> conditions = convertCriteriaToConditions(criteria);

    List<Map<String, Object>> results = dataService.findByCriteria(
        collectionDef.getFullName(),
        conditions);

    List<T> entities = results.stream()
        .map(this::mapToEntity)
        .collect(Collectors.toList());

    // 应用自定义过滤器
    if (criteria.getCustomFilter() != null) {
      entities = entities.stream()
          .filter(criteria.getCustomFilter())
          .collect(Collectors.toList());
    }

    // 应用排序
    if (criteria.getOrderBy() != null) {
      entities.sort((a, b) -> {
        try {
          Object aValue = getFieldValue(a, criteria.getOrderBy());
          Object bValue = getFieldValue(b, criteria.getOrderBy());

          if (aValue == null && bValue == null)
            return 0;
          if (aValue == null)
            return criteria.isAscending() ? -1 : 1;
          if (bValue == null)
            return criteria.isAscending() ? 1 : -1;

          if (aValue instanceof Comparable && bValue instanceof Comparable) {
            int result = ((Comparable) aValue).compareTo(bValue);
            return criteria.isAscending() ? result : -result;
          }

          return 0;
        } catch (Exception e) {
          LOG.warn("Failed to access sort field: {}", criteria.getOrderBy(), e);
          return 0;
        }
      });
    }

    return entities;
  }

  @Override
  public boolean update(T entity) {
    if (entity.getId() == null || entity.getId().isEmpty()) {
      throw new IllegalArgumentException("Entity must have ID for update");
    }

    entity.setUpdatedAt(new Date());
    Map<String, Object> data = entityToMap(entity);

    boolean success = dataService.update(collectionDef.getFullName(), entity.getId(), data);
    if (success) {
      LOG.debug("Entity updated successfully, type: {}, ID: {}", entityClass.getSimpleName(), entity.getId());
    }

    return success;
  }

  @Override
  public boolean delete(String id) {
    boolean success = dataService.delete(collectionDef.getFullName(), id);
    if (success) {
      LOG.debug("Entity deleted successfully, type: {}, ID: {}", entityClass.getSimpleName(), id);
    }
    return success;
  }

  @Override
  public int batchSave(List<T> entities) {
    List<Map<String, Object>> dataList = entities.stream()
        .peek(entity -> {
          if (entity.getId() == null || entity.getId().isEmpty()) {
            entity.setId(UUID.randomUUID().toString());
            entity.setCreatedAt(new Date());
          }
          entity.setUpdatedAt(new Date());
        })
        .map(this::entityToMap)
        .collect(Collectors.toList());

    int saved = dataService.batchSave(collectionDef.getFullName(), dataList);
    LOG.info("Batch saved entities, type: {}, count: {}", entityClass.getSimpleName(), saved);

    return saved;
  }

  @Override
  public int batchDelete(List<String> ids) {
    int deleted = dataService.batchDelete(collectionDef.getFullName(), ids);
    LOG.info("Batch deleted entities, type: {}, count: {}", entityClass.getSimpleName(), deleted);
    return deleted;
  }

  @Override
  public PageResult<T> findPage(QueryOptions options) {
    PageResult<Map<String, Object>> result = dataService.query(collectionDef.getFullName(), options);

    List<T> entities = result.getData().stream()
        .map(this::mapToEntity)
        .collect(Collectors.toList());

    return new PageResult<>(entities, result.getTotal(), result.getPage(), result.getPageSize());
  }

  @Override
  public long count() {
    return dataService.count(collectionDef.getFullName());
  }

  @Override
  public long countBy(QueryCriteria<T> criteria) {
    Map<String, Object> conditions = convertCriteriaToConditions(criteria);
    long count = dataService.countByCriteria(collectionDef.getFullName(), conditions);

    // 如果有自定义过滤器，需要在内存中过滤
    if (criteria.getCustomFilter() != null) {
      List<T> filtered = findBy(criteria);
      return filtered.size();
    }

    return count;
  }

  /**
   * 将实体转换为 Map
   * 
   * @param entity 实体对象
   * @return Map 对象
   */
  private Map<String, Object> entityToMap(T entity) {
    return objectMapper.convertValue(entity, Map.class);
  }

  /**
   * 将 Map 转换为实体
   * 
   * @param map Map 对象
   * @return 实体对象
   */
  private T mapToEntity(Map<String, Object> map) {
    return objectMapper.convertValue(map, entityClass);
  }

  /**
   * 转换查询条件
   * 将 QueryCriteria 的高级条件转换为简单的 Map 条件
   * 
   * @param criteria 查询条件
   * @return 条件 Map
   */
  private Map<String, Object> convertCriteriaToConditions(QueryCriteria<T> criteria) {
    Map<String, Object> conditions = new HashMap<>();

    // 这里简化处理，只处理等值条件
    // 实际应用中可以根据条件后缀（如 __gt, __lt 等）构建更复杂的查询
    criteria.getConditions().forEach((key, value) -> {
      if (!key.contains("__")) {
        // 简单的等值条件
        conditions.put(key, value);
      }
      // TODO: 处理复杂条件（如 __gt, __lt, __like 等）
    });

    return conditions;
  }

  /**
   * 获取实体的字段值
   * 使用反射获取字段值，用于排序
   * 
   * @param entity    实体对象
   * @param fieldName 字段名
   * @return 字段值
   */
  private Object getFieldValue(T entity, String fieldName) {
    try {
      Map<String, Object> map = entityToMap(entity);
      return map.get(fieldName);
    } catch (Exception e) {
      LOG.warn("Failed to get field value: {}", fieldName, e);
      return null;
    }
  }
}