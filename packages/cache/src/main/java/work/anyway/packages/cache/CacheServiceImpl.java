package work.anyway.packages.cache;

import work.anyway.interfaces.cache.CacheService;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CacheServiceImpl implements CacheService {

  private static class Entry {
    final Object value;
    final long expireAt;

    Entry(Object value, long expireAt) {
      this.value = value;
      this.expireAt = expireAt;
    }
  }

  private final Map<String, Entry> map = new ConcurrentHashMap<>();

  @Override
  public Object get(String key) {
    Entry e = map.get(key);
    if (e == null)
      return null;
    if (e.expireAt > 0 && e.expireAt < Instant.now().toEpochMilli()) {
      map.remove(key);
      return null;
    }
    return e.value;
  }

  @Override
  public void put(String key, Object value, long ttlSeconds) {
    long expireAt = ttlSeconds > 0 ? Instant.now().toEpochMilli() + ttlSeconds * 1000 : 0;
    map.put(key, new Entry(value, expireAt));
  }

  @Override
  public void remove(String key) {
    map.remove(key);
  }
}