package work.anyway.packages.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import work.anyway.interfaces.data.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 内存实现的数据服务
 * 用于开发和测试，数据存储在内存中
 */
@Service("memoryDataService")
public class MemoryDataServiceImpl implements DataService, TypedDataService {

  private static final Logger LOG = LoggerFactory.getLogger(MemoryDataServiceImpl.class);

  // 存储所有集合的数据
  private final Map<String, Map<String, Map<String, Object>>> collections = new ConcurrentHashMap<>();

  // ID 生成器
  private final Map<String, AtomicLong> idGenerators = new ConcurrentHashMap<>();

  public MemoryDataServiceImpl() {
    LOG.info("Initializing MemoryDataServiceImpl with sample data");
    initializeSampleData();
  }

  /**
   * 初始化示例数据
   */
  private void initializeSampleData() {
    // 初始化用户数据
    Map<String, Object> user1 = new HashMap<>();
    user1.put("name", "张三");
    user1.put("email", "zhangsan@example.com");
    user1.put("role", "admin");
    save("users", user1);

    Map<String, Object> user2 = new HashMap<>();
    user2.put("name", "李四");
    user2.put("email", "lisi@example.com");
    user2.put("role", "user");
    save("users", user2);

    // 初始化产品数据
    Map<String, Object> product1 = new HashMap<>();
    product1.put("name", "笔记本电脑");
    product1.put("price", 5999.00);
    product1.put("category", "电子产品");
    product1.put("stock", 50);
    save("products", product1);

    Map<String, Object> product2 = new HashMap<>();
    product2.put("name", "无线鼠标");
    product2.put("price", 99.00);
    product2.put("category", "电脑配件");
    product2.put("stock", 200);
    save("products", product2);

    // 初始化订单数据
    Map<String, Object> order1 = new HashMap<>();
    order1.put("orderNo", "ORD-2024-001");
    order1.put("userId", "1");
    order1.put("productId", "1");
    order1.put("quantity", 1);
    order1.put("totalAmount", 5999.00);
    order1.put("status", "已完成");
    save("orders", order1);

    LOG.info("Sample data initialized: {} collections", collections.size());
  }

  @Override
  public Map<String, Object> save(String collection, Map<String, Object> data) {
    LOG.debug("Saving data to collection: {}", collection);

    Map<String, Map<String, Object>> collectionData = collections.computeIfAbsent(collection,
        k -> new ConcurrentHashMap<>());
    AtomicLong idGenerator = idGenerators.computeIfAbsent(collection, k -> new AtomicLong(0));

    // 复制数据以避免外部修改
    Map<String, Object> dataCopy = new HashMap<>(data);

    // 生成 ID
    if (!dataCopy.containsKey("id")) {
      dataCopy.put("id", String.valueOf(idGenerator.incrementAndGet()));
    }

    String id = String.valueOf(dataCopy.get("id"));

    // 添加时间戳
    long now = System.currentTimeMillis();
    dataCopy.put("createdAt", now);
    dataCopy.put("updatedAt", now);

    // 保存数据
    collectionData.put(id, dataCopy);

    LOG.info("Saved data with id {} to collection {}", id, collection);
    return new HashMap<>(dataCopy);
  }

  @Override
  public Optional<Map<String, Object>> findById(String collection, String id) {
    LOG.debug("Finding data by id {} in collection {}", id, collection);

    Map<String, Map<String, Object>> collectionData = collections.get(collection);
    if (collectionData == null) {
      return Optional.empty();
    }

    Map<String, Object> data = collectionData.get(id);
    return data == null ? Optional.empty() : Optional.of(new HashMap<>(data));
  }

  @Override
  public List<Map<String, Object>> findAll(String collection) {
    LOG.debug("Finding all data in collection {}", collection);

    Map<String, Map<String, Object>> collectionData = collections.get(collection);
    if (collectionData == null) {
      return new ArrayList<>();
    }

    return collectionData.values().stream()
        .map(HashMap::new)
        .collect(Collectors.toList());
  }

  @Override
  public List<Map<String, Object>> findByCriteria(String collection, Map<String, Object> criteria) {
    LOG.debug("Finding data by criteria {} in collection {}", criteria, collection);

    return findAll(collection).stream()
        .filter(data -> matchesCriteria(data, criteria))
        .collect(Collectors.toList());
  }

  @Override
  public boolean update(String collection, String id, Map<String, Object> data) {
    LOG.debug("Updating data with id {} in collection {}", id, collection);

    Map<String, Map<String, Object>> collectionData = collections.get(collection);
    if (collectionData == null || !collectionData.containsKey(id)) {
      LOG.warn("Data not found for update: collection={}, id={}", collection, id);
      return false;
    }

    Map<String, Object> existingData = collectionData.get(id);
    Map<String, Object> updatedData = new HashMap<>(existingData);

    // 更新字段
    updatedData.putAll(data);

    // 保持 ID 不变
    updatedData.put("id", id);

    // 更新时间戳
    updatedData.put("updatedAt", System.currentTimeMillis());

    collectionData.put(id, updatedData);

    LOG.info("Updated data with id {} in collection {}", id, collection);
    return true;
  }

  @Override
  public boolean delete(String collection, String id) {
    LOG.debug("Deleting data with id {} from collection {}", id, collection);

    Map<String, Map<String, Object>> collectionData = collections.get(collection);
    if (collectionData == null) {
      return false;
    }

    Map<String, Object> removed = collectionData.remove(id);
    boolean deleted = removed != null;

    if (deleted) {
      LOG.info("Deleted data with id {} from collection {}", id, collection);
    } else {
      LOG.warn("Data not found for deletion: collection={}, id={}", collection, id);
    }

    return deleted;
  }

  @Override
  public long count(String collection) {
    Map<String, Map<String, Object>> collectionData = collections.get(collection);
    return collectionData == null ? 0 : collectionData.size();
  }

  @Override
  public long countByCriteria(String collection, Map<String, Object> criteria) {
    return findByCriteria(collection, criteria).size();
  }

  @Override
  public PageResult<Map<String, Object>> query(String collection, QueryOptions options) {
    LOG.debug("Querying collection {} with options: page={}, pageSize={}",
        collection, options.getPage(), options.getPageSize());

    // 获取过滤后的数据
    List<Map<String, Object>> filteredData = options.getFilters().isEmpty()
        ? findAll(collection)
        : findByCriteria(collection, options.getFilters());

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

        int result = compareValues(aValue, bValue);
        return options.isAscending() ? result : -result;
      });
    }

    // 分页
    int total = filteredData.size();
    int start = (options.getPage() - 1) * options.getPageSize();
    int end = Math.min(start + options.getPageSize(), total);

    List<Map<String, Object>> pageData = start < total
        ? filteredData.subList(start, end)
        : new ArrayList<>();

    return new PageResult<>(pageData, total, options.getPage(), options.getPageSize());
  }

  @Override
  public int batchSave(String collection, List<Map<String, Object>> dataList) {
    int saved = 0;
    for (Map<String, Object> data : dataList) {
      try {
        save(collection, data);
        saved++;
      } catch (Exception e) {
        LOG.error("Error saving data in batch", e);
      }
    }
    return saved;
  }

  @Override
  public int batchDelete(String collection, List<String> ids) {
    int deleted = 0;
    for (String id : ids) {
      if (delete(collection, id)) {
        deleted++;
      }
    }
    return deleted;
  }

  // TypedDataService 实现
  @Override
  public <T extends Entity> Repository<T> getRepository(CollectionDef collectionDef, Class<T> entityClass) {
    return new RepositoryImpl<>(this, collectionDef, entityClass);
  }

  @Override
  public <T extends Entity> Repository<T> getRepository(String table, Class<T> entityClass) {
    CollectionDef collectionDef = CollectionDef.builder(table).build();
    return new RepositoryImpl<>(this, collectionDef, entityClass);
  }

  @Override
  public <T extends Entity> Repository<T> getRepository(String dataSource, String table, Class<T> entityClass) {
    CollectionDef collectionDef = CollectionDef.builder(table).dataSource(dataSource).build();
    return new RepositoryImpl<>(this, collectionDef, entityClass);
  }

  // 辅助方法
  private boolean matchesCriteria(Map<String, Object> data, Map<String, Object> criteria) {
    for (Map.Entry<String, Object> entry : criteria.entrySet()) {
      String key = entry.getKey();
      Object expectedValue = entry.getValue();
      Object actualValue = data.get(key);

      if (!Objects.equals(expectedValue, actualValue)) {
        return false;
      }
    }
    return true;
  }

  private int compareValues(Object a, Object b) {
    if (a instanceof Comparable && b instanceof Comparable) {
      return ((Comparable) a).compareTo(b);
    }
    return String.valueOf(a).compareTo(String.valueOf(b));
  }

  /**
   * 清空所有数据（仅用于测试）
   */
  public void clearAll() {
    collections.clear();
    idGenerators.clear();
    LOG.warn("All data cleared from memory");
  }

  /**
   * 清空指定集合的所有数据
   * 仅供测试使用
   * 
   * @param collection 集合名称
   */
  public void clearCollection(String collection) {
    collections.remove(collection);
    idGenerators.remove(collection);
    LOG.warn("Collection {} cleared from memory", collection);
  }

  @Override
  public List<String> listCollections() {
    LOG.debug("Listing all collections from memory");
    return new ArrayList<>(collections.keySet());
  }
}