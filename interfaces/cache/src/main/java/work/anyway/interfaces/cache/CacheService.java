package work.anyway.interfaces.cache;

public interface CacheService {
  Object get(String key);

  void put(String key, Object value, long ttlSeconds);

  void remove(String key);
}