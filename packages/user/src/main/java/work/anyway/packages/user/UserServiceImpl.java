package work.anyway.packages.user;

import work.anyway.interfaces.user.UserService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UserServiceImpl implements UserService {

  private final Map<String, Object> store = new ConcurrentHashMap<>();

  @Override
  public Object getUserById(String userId) {
    return store.get(userId);
  }

  @Override
  public String createUser(Object userInfo) {
    String id = "U" + System.nanoTime();
    store.put(id, userInfo);
    return id;
  }

  @Override
  public Object getAllUsers() {
    return store;
  }
}