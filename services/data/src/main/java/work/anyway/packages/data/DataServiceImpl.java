package work.anyway.packages.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.anyway.interfaces.data.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 数据访问服务实现
 * 支持多数据源和类型安全的数据访问
 * 
 * @author 作者名
 * @since 1.0.0
 */
public class DataServiceImpl implements TypedDataService {

  private static final Logger LOG = LoggerFactory.getLogger(DataServiceImpl.class);

  // 数据源管理器（可选）
  private DataSourceManager dataSourceManager;

  // 使用 ConcurrentHashMap 保证线程安全（内存存储模式）
  private final Map<String, Map<String, Map<String, Object>>> dataStore = new ConcurrentHashMap<>();

  // ID 生成器
  private final Map<String, AtomicLong> idGenerators = new ConcurrentHashMap<>();

  // 是否使用数据库模式
  private final boolean useDatabaseMode;

  /**
   * 默认构造函数 - 使用内存存储
   */
  public DataServiceImpl() {
    this.useDatabaseMode = false;
    this.dataSourceManager = null;
    LOG.info("DataServiceImpl initialized: Using memory storage mode");
  }

  /**
   * 数据库模式构造函数
   * 
   * @param dataSourceManager 数据源管理器
   */
  public DataServiceImpl(DataSourceManager dataSourceManager) {
    this.dataSourceManager = dataSourceManager;
    this.useDatabaseMode = true;
    LOG.info("DataServiceImpl initialized: Using database storage mode");
  }

  @Override
  public Map<String, Object> save(String collection, Map<String, Object> data) {
    LOG.debug("Saving data to collection: {}", collection);

    Map<String, Map<String, Object>> collectionData = getOrCreateCollection(collection);

    // 生成 ID
    String id = data.containsKey("id") ? String.valueOf(data.get("id")) : generateId(collection);

    // 创建数据副本并添加 ID
    Map<String, Object> savedData = new HashMap<>(data);
    savedData.put("id", id);
    savedData.put("createdAt", new Date());
    savedData.put("updatedAt", new Date());

    collectionData.put(id, savedData);

    LOG.info("Data saved successfully, collection: {}, ID: {}", collection, id);
    return new HashMap<>(savedData);
  }

  @Override
  public Optional<Map<String, Object>> findById(String collection, String id) {
    LOG.debug("Finding data by ID, collection: {}, ID: {}", collection, id);

    Map<String, Map<String, Object>> collectionData = getCollection(collection);
    if (collectionData == null) {
      return Optional.empty();
    }

    Map<String, Object> data = collectionData.get(id);
    return Optional.ofNullable(data).map(HashMap::new);
  }

  @Override
  public List<Map<String, Object>> findAll(String collection) {
    LOG.debug("Finding all data, collection: {}", collection);

    Map<String, Map<String, Object>> collectionData = getCollection(collection);
    if (collectionData == null) {
      return Collections.emptyList();
    }

    return collectionData.values().stream()
        .map(HashMap::new)
        .collect(Collectors.toList());
  }

  @Override
  public List<Map<String, Object>> findByCriteria(String collection, Map<String, Object> criteria) {
    LOG.debug("Finding data by criteria, collection: {}, criteria: {}", collection, criteria);

    Map<String, Map<String, Object>> collectionData = getCollection(collection);
    if (collectionData == null) {
      return Collections.emptyList();
    }

    return collectionData.values().stream()
        .filter(data -> matchesCriteria(data, criteria))
        .map(HashMap::new)
        .collect(Collectors.toList());
  }

  @Override
  public boolean update(String collection, String id, Map<String, Object> data) {
    LOG.debug("Updating data, collection: {}, ID: {}", collection, id);

    Map<String, Map<String, Object>> collectionData = getCollection(collection);
    if (collectionData == null || !collectionData.containsKey(id)) {
      LOG.warn("Update failed, data not found, collection: {}, ID: {}", collection, id);
      return false;
    }

    Map<String, Object> existingData = collectionData.get(id);

    // 更新数据（保留 ID 和创建时间）
    Map<String, Object> updatedData = new HashMap<>(data);
    updatedData.put("id", id);
    updatedData.put("createdAt", existingData.get("createdAt"));
    updatedData.put("updatedAt", new Date());

    collectionData.put(id, updatedData);

    LOG.info("Data updated successfully, collection: {}, ID: {}", collection, id);
    return true;
  }

  @Override
  public boolean delete(String collection, String id) {
    LOG.debug("Deleting data, collection: {}, ID: {}", collection, id);

    Map<String, Map<String, Object>> collectionData = getCollection(collection);
    if (collectionData == null) {
      return false;
    }

    Map<String, Object> removed = collectionData.remove(id);
    boolean success = removed != null;

    if (success) {
      LOG.info("Data deleted successfully, collection: {}, ID: {}", collection, id);
    } else {
      LOG.warn("Delete failed, data not found, collection: {}, ID: {}", collection, id);
    }

    return success;
  }

  @Override
  public long count(String collection) {
    LOG.debug("Counting data, collection: {}", collection);

    Map<String, Map<String, Object>> collectionData = getCollection(collection);
    return collectionData == null ? 0 : collectionData.size();
  }

  @Override
  public long countByCriteria(String collection, Map<String, Object> criteria) {
    LOG.debug("Counting data by criteria, collection: {}, criteria: {}", collection, criteria);

    Map<String, Map<String, Object>> collectionData = getCollection(collection);
    if (collectionData == null) {
      return 0;
    }

    return collectionData.values().stream()
        .filter(data -> matchesCriteria(data, criteria))
        .count();
  }

  /**
   * 获取或创建集合
   */
  private Map<String, Map<String, Object>> getOrCreateCollection(String collection) {
    return dataStore.computeIfAbsent(collection, k -> new ConcurrentHashMap<>());
  }

  /**
   * 获取集合
   */
  private Map<String, Map<String, Object>> getCollection(String collection) {
    return dataStore.get(collection);
  }

  /**
   * 生成唯一 ID
   */
  private String generateId(String collection) {
    AtomicLong generator = idGenerators.computeIfAbsent(collection, k -> new AtomicLong(0));
    return String.valueOf(generator.incrementAndGet());
  }

  /**
   * 检查数据是否匹配查询条件
   */
  private boolean matchesCriteria(Map<String, Object> data, Map<String, Object> criteria) {
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

  @Override
  public PageResult<Map<String, Object>> query(String collection, QueryOptions options) {
    LOG.debug("Paging query data, collection: {}, options: page={}, pageSize={}, sortBy={}",
        collection, options.getPage(), options.getPageSize(), options.getSortBy());

    Map<String, Map<String, Object>> collectionData = getCollection(collection);
    if (collectionData == null) {
      return new PageResult<>(Collections.emptyList(), 0, options.getPage(), options.getPageSize());
    }

    // 过滤数据
    List<Map<String, Object>> filteredData = collectionData.values().stream()
        .filter(data -> matchesCriteria(data, options.getFilters()))
        .map(HashMap::new)
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
}