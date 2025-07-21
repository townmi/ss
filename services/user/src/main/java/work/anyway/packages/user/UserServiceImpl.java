package work.anyway.packages.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.anyway.interfaces.data.DataService;
import work.anyway.interfaces.user.UserService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户服务实现
 * 使用 DataService 进行数据持久化
 */
public class UserServiceImpl implements UserService {

  private static final Logger LOG = LoggerFactory.getLogger(UserServiceImpl.class);
  private static final String COLLECTION_NAME = "users";
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private DataService dataService;

  // 默认构造函数，用于服务容器自动注入
  public UserServiceImpl() {
  }

  // 用于手动注入 DataService
  public UserServiceImpl(DataService dataService) {
    this.dataService = dataService;
  }

  // Setter 方法，用于依赖注入
  public void setDataService(DataService dataService) {
    this.dataService = dataService;
  }

  @Override
  public List<Map<String, Object>> getAllUsers() {
    try {
      return dataService.findAll(COLLECTION_NAME);
    } catch (Exception e) {
      LOG.error("Error getting all users", e);
      return new ArrayList<>();
    }
  }

  @Override
  public Optional<Map<String, Object>> getUserById(String userId) {
    try {
      return dataService.findById(COLLECTION_NAME, userId);
    } catch (Exception e) {
      LOG.error("Error getting user by id: " + userId, e);
      return Optional.empty();
    }
  }

  @Override
  public Map<String, Object> createUser(Map<String, Object> userInfo) {
    try {
      // 生成 ID
      if (!userInfo.containsKey("id")) {
        userInfo.put("id", UUID.randomUUID().toString());
      }

      // 添加默认值
      if (!userInfo.containsKey("status")) {
        userInfo.put("status", "active");
      }
      if (!userInfo.containsKey("role")) {
        userInfo.put("role", "user");
      }
      if (!userInfo.containsKey("createdAt")) {
        userInfo.put("createdAt", LocalDateTime.now().format(DATE_FORMAT));
      }

      // 验证必要字段
      if (!userInfo.containsKey("name") || userInfo.get("name") == null
          || userInfo.get("name").toString().trim().isEmpty()) {
        throw new IllegalArgumentException("User name is required");
      }

      // 检查邮箱是否已存在
      if (userInfo.containsKey("email") && userInfo.get("email") != null) {
        String email = userInfo.get("email").toString();
        if (isEmailExists(email)) {
          throw new IllegalArgumentException("Email already exists: " + email);
        }
      }

      return dataService.save(COLLECTION_NAME, userInfo);
    } catch (Exception e) {
      LOG.error("Error creating user", e);
      throw new RuntimeException("Failed to create user: " + e.getMessage(), e);
    }
  }

  @Override
  public boolean updateUser(String userId, Map<String, Object> userInfo) {
    try {
      // 检查用户是否存在
      Optional<Map<String, Object>> existingUser = getUserById(userId);
      if (!existingUser.isPresent()) {
        LOG.warn("User not found for update: " + userId);
        return false;
      }

      // 如果更新邮箱，检查新邮箱是否已被其他用户使用
      if (userInfo.containsKey("email") && userInfo.get("email") != null) {
        String newEmail = userInfo.get("email").toString();
        Optional<Map<String, Object>> userWithEmail = findUserByEmail(newEmail);
        if (userWithEmail.isPresent() && !userWithEmail.get().get("id").equals(userId)) {
          throw new IllegalArgumentException("Email already exists: " + newEmail);
        }
      }

      // 添加更新时间
      userInfo.put("updatedAt", LocalDateTime.now().format(DATE_FORMAT));

      return dataService.update(COLLECTION_NAME, userId, userInfo);
    } catch (Exception e) {
      LOG.error("Error updating user: " + userId, e);
      throw new RuntimeException("Failed to update user: " + e.getMessage(), e);
    }
  }

  @Override
  public boolean deleteUser(String userId) {
    try {
      return dataService.delete(COLLECTION_NAME, userId);
    } catch (Exception e) {
      LOG.error("Error deleting user: " + userId, e);
      return false;
    }
  }

  @Override
  public Optional<Map<String, Object>> findUserByEmail(String email) {
    if (email == null || email.trim().isEmpty()) {
      return Optional.empty();
    }

    try {
      List<Map<String, Object>> users = getAllUsers();
      return users.stream()
          .filter(user -> email.equalsIgnoreCase(String.valueOf(user.get("email"))))
          .findFirst();
    } catch (Exception e) {
      LOG.error("Error finding user by email: " + email, e);
      return Optional.empty();
    }
  }

  @Override
  public Optional<Map<String, Object>> authenticateUser(String username, String password) {
    if (username == null || password == null) {
      return Optional.empty();
    }

    try {
      // 先尝试通过邮箱查找
      Optional<Map<String, Object>> user = findUserByEmail(username);

      // 如果通过邮箱找不到，尝试通过用户名查找
      if (!user.isPresent()) {
        List<Map<String, Object>> users = getAllUsers();
        user = users.stream()
            .filter(u -> username.equals(u.get("username")) || username.equals(u.get("name")))
            .findFirst();
      }

      // 验证密码（这里简化处理，实际应该使用加密）
      if (user.isPresent()) {
        String storedPassword = String.valueOf(user.get().get("password"));
        if (password.equals(storedPassword)) {
          // 更新最后登录时间
          Map<String, Object> updateData = new HashMap<>();
          updateData.put("lastLogin", LocalDateTime.now().format(DATE_FORMAT));
          updateUser(String.valueOf(user.get().get("id")), updateData);

          return user;
        }
      }

      return Optional.empty();
    } catch (Exception e) {
      LOG.error("Error authenticating user: " + username, e);
      return Optional.empty();
    }
  }

  @Override
  public boolean isEmailExists(String email) {
    return findUserByEmail(email).isPresent();
  }

  @Override
  public long getUserCount() {
    try {
      return getAllUsers().size();
    } catch (Exception e) {
      LOG.error("Error getting user count", e);
      return 0;
    }
  }
}