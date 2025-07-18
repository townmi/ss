package work.anyway.packages.user;

import work.anyway.api.user.UserService;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UserServiceImpl implements UserService {

  private final Map<String, Object> store = new ConcurrentHashMap<>();

  @Override
  public Object getUserById(String userId) {
    if ("123".equals(userId)) {
      return new HashMap<String, Object>() {
        {
          put("id", "123");
          put("name", "John Doe");
          put("email", "john.doe@example.com");
          put("phone", "1234567890");
          put("address", "123 Main St, Anytown, USA");
          put("city", "Anytown");
          put("state", "CA");
          put("zip", "12345");
        }
      };
    }

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