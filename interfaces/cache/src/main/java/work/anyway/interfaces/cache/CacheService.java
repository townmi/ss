package work.anyway.interfaces.cache;

/**
 * 缓存服务接口
 * 提供基础缓存功能和认证相关的扩展功能
 */
public interface CacheService {

  // 基础缓存操作

  /**
   * 获取缓存值
   * 
   * @param key 键
   * @return 值，如果不存在或已过期则返回null
   */
  Object get(String key);

  /**
   * 存储缓存值
   * 
   * @param key        键
   * @param value      值
   * @param ttlSeconds 存活时间（秒）
   */
  void put(String key, Object value, long ttlSeconds);

  /**
   * 移除缓存项
   * 
   * @param key 键
   */
  void remove(String key);

  // 认证功能扩展

  /**
   * 检查键是否存在且未过期
   * 
   * @param key 键
   * @return 是否存在
   */
  boolean exists(String key);

  /**
   * 原子性递增计数器
   * 如果键不存在，则初始化为delta值并设置TTL
   * 
   * @param key        键
   * @param delta      增量
   * @param ttlSeconds TTL（仅在键不存在时设置）
   * @return 递增后的值
   */
  long increment(String key, long delta, long ttlSeconds);

  /**
   * 获取值并刷新TTL
   * 
   * @param key           键
   * @param newTtlSeconds 新的TTL
   * @return 值，如果不存在或已过期则返回null
   */
  Object getAndRefresh(String key, long newTtlSeconds);

  /**
   * 批量删除匹配模式的键
   * 支持通配符 * 匹配
   * 
   * @param pattern 模式，如 "user:*" 匹配所有以user:开头的键
   */
  void removePattern(String pattern);

  /**
   * 获取缓存统计信息
   * 
   * @return 统计信息
   */
  CacheStats getStats();

  /**
   * 缓存统计信息
   */
  class CacheStats {
    private final long size;
    private final long hitCount;
    private final long missCount;

    public CacheStats(long size, long hitCount, long missCount) {
      this.size = size;
      this.hitCount = hitCount;
      this.missCount = missCount;
    }

    public long getSize() {
      return size;
    }

    public long getHitCount() {
      return hitCount;
    }

    public long getMissCount() {
      return missCount;
    }

    public double getHitRate() {
      long total = hitCount + missCount;
      return total == 0 ? 0.0 : (double) hitCount / total;
    }
  }
}