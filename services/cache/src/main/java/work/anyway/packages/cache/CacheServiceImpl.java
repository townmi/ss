package work.anyway.packages.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import work.anyway.interfaces.cache.CacheService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * 增强的内存缓存实现
 * 支持基础缓存操作和认证相关的扩展功能
 */
@Service
public class CacheServiceImpl implements CacheService {

  private static final Logger LOG = LoggerFactory.getLogger(CacheServiceImpl.class);

  private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
  private final AtomicLong hitCount = new AtomicLong(0);
  private final AtomicLong missCount = new AtomicLong(0);

  @Override
  public Object get(String key) {
    CacheEntry entry = cache.get(key);
    if (entry == null) {
      missCount.incrementAndGet();
      LOG.debug("Cache miss for key: {}", key);
      return null;
    }

    if (entry.isExpired()) {
      cache.remove(key);
      missCount.incrementAndGet();
      LOG.debug("Cache expired for key: {}", key);
      return null;
    }

    hitCount.incrementAndGet();
    LOG.debug("Cache hit for key: {}", key);
    return entry.getValue();
  }

  @Override
  public void put(String key, Object value, long ttlSeconds) {
    long expirationTime = System.currentTimeMillis() + (ttlSeconds * 1000);
    cache.put(key, new CacheEntry(value, expirationTime));
    LOG.debug("Cached value for key: {} with TTL: {}s", key, ttlSeconds);
  }

  @Override
  public void remove(String key) {
    cache.remove(key);
    LOG.debug("Removed cache entry for key: {}", key);
  }

  @Override
  public boolean exists(String key) {
    CacheEntry entry = cache.get(key);
    if (entry == null) {
      return false;
    }

    if (entry.isExpired()) {
      cache.remove(key);
      return false;
    }

    return true;
  }

  @Override
  public long increment(String key, long delta, long ttlSeconds) {
    CacheEntry entry = cache.get(key);
    long newValue;

    if (entry == null || entry.isExpired()) {
      // 键不存在或已过期，初始化为delta值
      newValue = delta;
      put(key, newValue, ttlSeconds);
      LOG.debug("Initialized counter for key: {} with value: {}", key, newValue);
    } else {
      // 键存在，递增值
      Object currentObj = entry.getValue();
      long currentValue = (currentObj instanceof Long) ? (Long) currentObj : 0L;
      newValue = currentValue + delta;

      // 更新值但保持原有的过期时间
      cache.put(key, new CacheEntry(newValue, entry.getExpirationTime()));
      LOG.debug("Incremented counter for key: {} from {} to {}", key, currentValue, newValue);
    }

    return newValue;
  }

  @Override
  public Object getAndRefresh(String key, long newTtlSeconds) {
    CacheEntry entry = cache.get(key);
    if (entry == null) {
      missCount.incrementAndGet();
      LOG.debug("Cache miss for key: {}", key);
      return null;
    }

    if (entry.isExpired()) {
      cache.remove(key);
      missCount.incrementAndGet();
      LOG.debug("Cache expired for key: {}", key);
      return null;
    }

    // 刷新TTL
    long newExpirationTime = System.currentTimeMillis() + (newTtlSeconds * 1000);
    cache.put(key, new CacheEntry(entry.getValue(), newExpirationTime));

    hitCount.incrementAndGet();
    LOG.debug("Cache hit and refreshed TTL for key: {} with new TTL: {}s", key, newTtlSeconds);
    return entry.getValue();
  }

  @Override
  public void removePattern(String pattern) {
    // 将通配符模式转换为正则表达式
    String regex = pattern.replace("*", ".*");
    Pattern compiledPattern = Pattern.compile(regex);

    int removedCount = 0;
    var iterator = cache.entrySet().iterator();
    while (iterator.hasNext()) {
      var entry = iterator.next();
      if (compiledPattern.matcher(entry.getKey()).matches()) {
        iterator.remove();
        removedCount++;
      }
    }

    LOG.debug("Removed {} cache entries matching pattern: {}", removedCount, pattern);
  }

  @Override
  public CacheStats getStats() {
    // 清理过期的条目以获得准确的大小
    cleanupExpired();

    return new CacheStats(
        cache.size(),
        hitCount.get(),
        missCount.get());
  }

  /**
   * 清理过期的缓存条目
   */
  private void cleanupExpired() {
    long currentTime = System.currentTimeMillis();
    int removedCount = 0;

    var iterator = cache.entrySet().iterator();
    while (iterator.hasNext()) {
      var entry = iterator.next();
      if (entry.getValue().isExpired(currentTime)) {
        iterator.remove();
        removedCount++;
      }
    }

    if (removedCount > 0) {
      LOG.debug("Cleaned up {} expired cache entries", removedCount);
    }
  }

  /**
   * 缓存条目
   */
  private static class CacheEntry {
    private final Object value;
    private final long expirationTime;

    public CacheEntry(Object value, long expirationTime) {
      this.value = value;
      this.expirationTime = expirationTime;
    }

    public Object getValue() {
      return value;
    }

    public long getExpirationTime() {
      return expirationTime;
    }

    public boolean isExpired() {
      return isExpired(System.currentTimeMillis());
    }

    public boolean isExpired(long currentTime) {
      return currentTime > expirationTime;
    }
  }
}