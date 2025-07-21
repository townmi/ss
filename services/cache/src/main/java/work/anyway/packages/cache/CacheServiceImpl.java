package work.anyway.packages.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import work.anyway.interfaces.cache.CacheService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简单的内存缓存实现
 */
@Service
public class CacheServiceImpl implements CacheService {

  private static final Logger LOG = LoggerFactory.getLogger(CacheServiceImpl.class);

  private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

  @Override
  public Object get(String key) {
    CacheEntry entry = cache.get(key);
    if (entry == null) {
      return null;
    }

    if (entry.isExpired()) {
      cache.remove(key);
      LOG.debug("Cache expired for key: {}", key);
      return null;
    }

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

    public boolean isExpired() {
      return System.currentTimeMillis() > expirationTime;
    }
  }
}