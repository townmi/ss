package work.anyway.packages.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import work.anyway.interfaces.data.DataService;
import work.anyway.interfaces.user.UserService;

import org.springframework.beans.factory.annotation.Autowired;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 用户服务实现
 * 使用 DataService 进行数据存储
 */
@Service
public class UserServiceImpl implements UserService {

  private static final Logger LOG = LoggerFactory.getLogger(UserServiceImpl.class);
  private static final String COLLECTION_NAME = "users";

  @Autowired
  private DataService dataService;

  @Override
  public List<Map<String, Object>> getAllUsers() {
    LOG.debug("Getting all users");
    return dataService.findAll(COLLECTION_NAME);
  }

  @Override
  public Optional<Map<String, Object>> getUserById(String userId) {
    LOG.debug("Getting user by id: {}", userId);
    return dataService.findById(COLLECTION_NAME, userId);
  }

  @Override
  public Map<String, Object> createUser(Map<String, Object> userInfo) {
    LOG.debug("Creating user with info: {}", userInfo);

    // 验证必要字段
    if (!userInfo.containsKey("email")) {
      throw new IllegalArgumentException("Email is required");
    }

    // 检查邮箱是否已存在
    String email = (String) userInfo.get("email");
    if (isEmailExists(email)) {
      throw new IllegalArgumentException("Email already exists: " + email);
    }

    // 设置默认值
    userInfo.putIfAbsent("status", "active");
    userInfo.putIfAbsent("createdAt", System.currentTimeMillis());

    // 保存用户
    Map<String, Object> savedUser = dataService.save(COLLECTION_NAME, userInfo);
    LOG.info("User created with id: {}", savedUser.get("id"));

    return savedUser;
  }

  @Override
  public boolean updateUser(String userId, Map<String, Object> userInfo) {
    LOG.debug("Updating user {} with info: {}", userId, userInfo);

    // 检查用户是否存在
    Optional<Map<String, Object>> existing = getUserById(userId);
    if (existing.isEmpty()) {
      LOG.warn("User not found: {}", userId);
      return false;
    }

    // 如果更新邮箱，检查新邮箱是否已被使用
    if (userInfo.containsKey("email")) {
      String newEmail = (String) userInfo.get("email");
      String currentEmail = (String) existing.get().get("email");

      if (!newEmail.equals(currentEmail) && isEmailExists(newEmail)) {
        throw new IllegalArgumentException("Email already exists: " + newEmail);
      }
    }

    // 更新时间戳
    userInfo.put("updatedAt", System.currentTimeMillis());

    boolean updated = dataService.update(COLLECTION_NAME, userId, userInfo);
    if (updated) {
      LOG.info("User updated: {}", userId);
    }

    return updated;
  }

  @Override
  public boolean deleteUser(String userId) {
    LOG.debug("Deleting user: {}", userId);

    boolean deleted = dataService.delete(COLLECTION_NAME, userId);
    if (deleted) {
      LOG.info("User deleted: {}", userId);
    } else {
      LOG.warn("User not found for deletion: {}", userId);
    }

    return deleted;
  }

  @Override
  public Optional<Map<String, Object>> findUserByEmail(String email) {
    LOG.debug("Finding user by email: {}", email);

    Map<String, Object> criteria = new HashMap<>();
    criteria.put("email", email);

    List<Map<String, Object>> users = dataService.findByCriteria(COLLECTION_NAME, criteria);
    return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
  }

  @Override
  public Optional<Map<String, Object>> authenticateUser(String username, String password) {
    LOG.debug("Authenticating user: {}", username);

    // 通过用户名或邮箱查找用户
    Optional<Map<String, Object>> user = findUserByEmail(username);

    if (user.isEmpty()) {
      // 尝试通过用户名查找
      Map<String, Object> criteria = new HashMap<>();
      criteria.put("username", username);
      List<Map<String, Object>> users = dataService.findByCriteria(COLLECTION_NAME, criteria);
      user = users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
    }

    if (user.isPresent()) {
      // 简单的密码验证（实际应用中应该使用加密）
      String storedPassword = (String) user.get().get("password");
      if (password.equals(storedPassword)) {
        LOG.info("User authenticated: {}", username);
        return user;
      }
    }

    LOG.warn("Authentication failed for user: {}", username);
    return Optional.empty();
  }

  @Override
  public boolean isEmailExists(String email) {
    LOG.debug("Checking if email exists: {}", email);
    return findUserByEmail(email).isPresent();
  }

  @Override
  public long getUserCount() {
    LOG.debug("Getting user count");
    return dataService.count(COLLECTION_NAME);
  }
}