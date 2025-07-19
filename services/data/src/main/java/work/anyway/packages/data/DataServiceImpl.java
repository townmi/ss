package work.anyway.packages.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.anyway.interfaces.data.DataService;
import work.anyway.interfaces.data.PageResult;
import work.anyway.interfaces.data.QueryOptions;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 数据访问服务实现
 * 使用内存存储实现数据持久化
 * 
 * @author 作者名
 * @since 1.0.0
 */
public class DataServiceImpl implements DataService {
  
  private static final Logger LOG = LoggerFactory.getLogger(DataServiceImpl.class);
  
  // 使用 ConcurrentHashMap 保证线程安全
  private final Map<String, Map<String, Map<String, Object>>> dataStore = new ConcurrentHashMap<>();
  
  // ID 生成器
  private final Map<String, AtomicLong> idGenerators = new ConcurrentHashMap<>();
  
  @Override
  public Map<String, Object> save(String collection, Map<String, Object> data) {
    LOG.debug("保存数据到集合: {}", collection);
    
    Map<String, Map<String, Object>> collectionData = getOrCreateCollection(collection);
    
    // 生成 ID
    String id = data.containsKey("id") ? 
        String.valueOf(data.get("id")) : 
        generateId(collection);
    
    // 创建数据副本并添加 ID
    Map<String, Object> savedData = new HashMap<>(data);
    savedData.put("id", id);
    savedData.put("createdAt", new Date());
    savedData.put("updatedAt", new Date());
    
    collectionData.put(id, savedData);
    
    LOG.info("数据保存成功，集合: {}, ID: {}", collection, id);
    return new HashMap<>(savedData);
  }
  
  @Override
  public Optional<Map<String, Object>> findById(String collection, String id) {
    LOG.debug("根据ID查找数据，集合: {}, ID: {}", collection, id);
    
    Map<String, Map<String, Object>> collectionData = getCollection(collection);
    if (collectionData == null) {
      return Optional.empty();
    }
    
    Map<String, Object> data = collectionData.get(id);
    return Optional.ofNullable(data).map(HashMap::new);
  }
  
  @Override
  public List<Map<String, Object>> findAll(String collection) {
    LOG.debug("查找所有数据，集合: {}", collection);
    
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
    LOG.debug("根据条件查询数据，集合: {}, 条件: {}", collection, criteria);
    
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
    LOG.debug("更新数据，集合: {}, ID: {}", collection, id);
    
    Map<String, Map<String, Object>> collectionData = getCollection(collection);
    if (collectionData == null || !collectionData.containsKey(id)) {
      LOG.warn("更新失败，数据不存在，集合: {}, ID: {}", collection, id);
      return false;
    }
    
    Map<String, Object> existingData = collectionData.get(id);
    
    // 更新数据（保留 ID 和创建时间）
    Map<String, Object> updatedData = new HashMap<>(data);
    updatedData.put("id", id);
    updatedData.put("createdAt", existingData.get("createdAt"));
    updatedData.put("updatedAt", new Date());
    
    collectionData.put(id, updatedData);
    
    LOG.info("数据更新成功，集合: {}, ID: {}", collection, id);
    return true;
  }
  
  @Override
  public boolean delete(String collection, String id) {
    LOG.debug("删除数据，集合: {}, ID: {}", collection, id);
    
    Map<String, Map<String, Object>> collectionData = getCollection(collection);
    if (collectionData == null) {
      return false;
    }
    
    Map<String, Object> removed = collectionData.remove(id);
    boolean success = removed != null;
    
    if (success) {
      LOG.info("数据删除成功，集合: {}, ID: {}", collection, id);
    } else {
      LOG.warn("删除失败，数据不存在，集合: {}, ID: {}", collection, id);
    }
    
    return success;
  }
  
  @Override
  public long count(String collection) {
    LOG.debug("统计数据数量，集合: {}", collection);
    
    Map<String, Map<String, Object>> collectionData = getCollection(collection);
    return collectionData == null ? 0 : collectionData.size();
  }
  
  @Override
  public long countByCriteria(String collection, Map<String, Object> criteria) {
    LOG.debug("根据条件统计数据数量，集合: {}, 条件: {}", collection, criteria);
    
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
    LOG.debug("分页查询数据，集合: {}, 选项: page={}, pageSize={}, sortBy={}", 
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
        
        if (aValue == null && bValue == null) return 0;
        if (aValue == null) return options.isAscending() ? -1 : 1;
        if (bValue == null) return options.isAscending() ? 1 : -1;
        
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
    LOG.debug("批量保存数据，集合: {}, 数量: {}", collection, dataList.size());
    
    int savedCount = 0;
    for (Map<String, Object> data : dataList) {
      try {
        save(collection, data);
        savedCount++;
      } catch (Exception e) {
        LOG.error("批量保存失败，跳过该条数据", e);
      }
    }
    
    LOG.info("批量保存完成，集合: {}, 成功: {}/{}", collection, savedCount, dataList.size());
    return savedCount;
  }
  
  @Override
  public int batchDelete(String collection, List<String> ids) {
    LOG.debug("批量删除数据，集合: {}, 数量: {}", collection, ids.size());
    
    int deletedCount = 0;
    for (String id : ids) {
      if (delete(collection, id)) {
        deletedCount++;
      }
    }
    
    LOG.info("批量删除完成，集合: {}, 成功: {}/{}", collection, deletedCount, ids.size());
    return deletedCount;
  }
} 