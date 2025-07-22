package work.anyway.packages.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import work.anyway.interfaces.data.DataService;
import work.anyway.interfaces.user.User;
import work.anyway.interfaces.user.UserService;

import org.springframework.beans.factory.annotation.Autowired;
import work.anyway.interfaces.user.AccountService;
import work.anyway.interfaces.user.AccountType;
import work.anyway.interfaces.user.UserAccount;
import java.util.*;
import java.util.stream.Collectors;

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

  @Autowired
  private AccountService accountService;

  @Override
  public List<User> getAllUsers() {
    LOG.debug("Getting all users");
    List<Map<String, Object>> userMaps = dataService.findAll(COLLECTION_NAME);
    return userMaps.stream()
        .map(this::mapToUser)
        .collect(Collectors.toList());
  }

  @Override
  public Optional<User> getUserById(String userId) {
    LOG.debug("Getting user by id: {}", userId);
    Optional<Map<String, Object>> userMap = dataService.findById(COLLECTION_NAME, userId);
    return userMap.map(this::mapToUser);
  }

  @Override
  public User createUser(User user) {
    LOG.debug("Creating user with info: {}", user);

    // 验证必要字段
    if (user.getName() == null || user.getName().trim().isEmpty()) {
      throw new IllegalArgumentException("Name is required");
    }

    // 生成唯一的用户ID
    if (user.getId() == null || user.getId().trim().isEmpty()) {
      String userId = UUID.randomUUID().toString();
      user.setId(userId);
      LOG.debug("Generated new user ID: {}", userId);
    }

    // 设置默认值
    if (user.getRole() == null) {
      user.setRole("user");
    }
    if (user.getStatus() == null) {
      user.setStatus("active");
    }
    if (user.getCreatedAt() == null) {
      user.setCreatedAt(new Date());
    }
    user.setUpdatedAt(new Date());

    // 转换为Map并保存
    Map<String, Object> userMap = userToMap(user);
    Map<String, Object> savedUserMap = dataService.save(COLLECTION_NAME, userMap);

    User savedUser = mapToUser(savedUserMap);
    LOG.info("User created with id: {}", savedUser.getId());
    return savedUser;
  }

  @Override
  public boolean updateUser(String userId, User user) {
    LOG.debug("Updating user {} with info: {}", userId, user);

    // 检查用户是否存在
    Optional<User> existing = getUserById(userId);
    if (existing.isEmpty()) {
      LOG.warn("User not found: {}", userId);
      return false;
    }

    // 更新时间戳
    user.setUpdatedAt(new Date());

    // 转换为Map并更新
    Map<String, Object> userMap = userToMap(user);
    boolean updated = dataService.update(COLLECTION_NAME, userId, userMap);

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
  public Optional<User> findUserByEmail(String email) {
    LOG.debug("Finding user by email: {}", email);

    Optional<UserAccount> accountOpt = accountService.findAccount(email, AccountType.EMAIL);
    if (accountOpt.isEmpty()) {
      return Optional.empty();
    }

    String userId = accountOpt.get().getUserId();
    return getUserById(userId);
  }

  @Override
  public Optional<User> findUserByPhone(String phone) {
    LOG.debug("Finding user by phone: {}", phone);

    Map<String, Object> criteria = new HashMap<>();
    criteria.put("phone", phone);

    List<Map<String, Object>> users = dataService.findByCriteria(COLLECTION_NAME, criteria);
    return users.isEmpty() ? Optional.empty() : Optional.of(mapToUser(users.get(0)));
  }

  @Override
  public boolean isEmailExists(String email) {
    LOG.debug("Checking if email exists: {}", email);
    return accountService.isIdentifierExists(email, AccountType.EMAIL);
  }

  @Override
  public boolean isPhoneExists(String phone) {
    LOG.debug("Checking if phone exists: {}", phone);
    return findUserByPhone(phone).isPresent();
  }

  @Override
  public long getUserCount() {
    LOG.debug("Getting user count");
    return dataService.count(COLLECTION_NAME);
  }

  @Override
  public List<User> getUsersByRole(String role) {
    LOG.debug("Getting users by role: {}", role);

    Map<String, Object> criteria = new HashMap<>();
    criteria.put("role", role);

    List<Map<String, Object>> userMaps = dataService.findByCriteria(COLLECTION_NAME, criteria);
    return userMaps.stream()
        .map(this::mapToUser)
        .collect(Collectors.toList());
  }

  @Override
  public List<User> getUsersByStatus(String status) {
    LOG.debug("Getting users by status: {}", status);

    Map<String, Object> criteria = new HashMap<>();
    criteria.put("status", status);

    List<Map<String, Object>> userMaps = dataService.findByCriteria(COLLECTION_NAME, criteria);
    return userMaps.stream()
        .map(this::mapToUser)
        .collect(Collectors.toList());
  }

  @Override
  public List<User> getUsersByDepartment(String department) {
    LOG.debug("Getting users by department: {}", department);

    Map<String, Object> criteria = new HashMap<>();
    criteria.put("department", department);

    List<Map<String, Object>> userMaps = dataService.findByCriteria(COLLECTION_NAME, criteria);
    return userMaps.stream()
        .map(this::mapToUser)
        .collect(Collectors.toList());
  }

  @Override
  public boolean updateUserRole(String userId, String role) {
    LOG.debug("Updating user {} role to: {}", userId, role);

    Optional<User> userOpt = getUserById(userId);
    if (userOpt.isEmpty()) {
      return false;
    }

    User user = userOpt.get();
    user.setRole(role);
    return updateUser(userId, user);
  }

  @Override
  public boolean updateUserStatus(String userId, String status) {
    LOG.debug("Updating user {} status to: {}", userId, status);

    Optional<User> userOpt = getUserById(userId);
    if (userOpt.isEmpty()) {
      return false;
    }

    User user = userOpt.get();
    user.setStatus(status);
    return updateUser(userId, user);
  }

  @Override
  public boolean updateLastLogin(String userId) {
    LOG.debug("Updating last login for user: {}", userId);

    Optional<User> userOpt = getUserById(userId);
    if (userOpt.isEmpty()) {
      return false;
    }

    User user = userOpt.get();
    user.setLastLogin(new Date());
    return updateUser(userId, user);
  }

  @Override
  public int batchCreateUsers(List<User> users) {
    LOG.debug("Batch creating {} users", users.size());

    int successCount = 0;
    for (User user : users) {
      try {
        createUser(user);
        successCount++;
      } catch (Exception e) {
        LOG.error("Failed to create user: {}", user.getName(), e);
      }
    }

    LOG.info("Batch created {}/{} users successfully", successCount, users.size());
    return successCount;
  }

  @Override
  public int batchUpdateUserStatus(List<String> userIds, String status) {
    LOG.debug("Batch updating {} users status to: {}", userIds.size(), status);

    int successCount = 0;
    for (String userId : userIds) {
      if (updateUserStatus(userId, status)) {
        successCount++;
      }
    }

    LOG.info("Batch updated {}/{} users status successfully", successCount, userIds.size());
    return successCount;
  }

  @Override
  public List<User> searchUsers(String keyword) {
    LOG.debug("Searching users with keyword: {}", keyword);

    if (keyword == null || keyword.trim().isEmpty()) {
      return getAllUsers();
    }

    // 简单的关键词搜索实现
    return getAllUsers().stream()
        .filter(user -> matchesKeyword(user, keyword.toLowerCase()))
        .collect(Collectors.toList());
  }

  @Override
  public boolean isUserAdmin(String userId) {
    LOG.debug("Checking if user {} is admin", userId);

    Optional<User> userOpt = getUserById(userId);
    return userOpt.map(User::isAdmin).orElse(false);
  }

  @Override
  public boolean isUserActive(String userId) {
    LOG.debug("Checking if user {} is active", userId);

    Optional<User> userOpt = getUserById(userId);
    return userOpt.map(User::isActive).orElse(false);
  }

  // 辅助方法

  /**
   * 将Map转换为User实体
   */
  private User mapToUser(Map<String, Object> map) {
    User user = new User();
    user.setId((String) map.get("id"));
    user.setName((String) map.get("name"));
    user.setPhone((String) map.get("phone"));
    user.setDepartment((String) map.get("department"));
    user.setRole((String) map.get("role"));
    user.setStatus((String) map.get("status"));
    user.setAvatarUrl((String) map.get("avatarUrl"));
    user.setNotes((String) map.get("notes"));

    // 处理日期字段
    Object createdAt = map.get("createdAt");
    if (createdAt instanceof Long) {
      user.setCreatedAt(new Date((Long) createdAt));
    } else if (createdAt instanceof Date) {
      user.setCreatedAt((Date) createdAt);
    }

    Object updatedAt = map.get("updatedAt");
    if (updatedAt instanceof Long) {
      user.setUpdatedAt(new Date((Long) updatedAt));
    } else if (updatedAt instanceof Date) {
      user.setUpdatedAt((Date) updatedAt);
    }

    Object lastLogin = map.get("lastLogin");
    if (lastLogin instanceof Long) {
      user.setLastLogin(new Date((Long) lastLogin));
    } else if (lastLogin instanceof Date) {
      user.setLastLogin((Date) lastLogin);
    }

    return user;
  }

  /**
   * 将User实体转换为Map
   */
  private Map<String, Object> userToMap(User user) {
    Map<String, Object> map = new HashMap<>();

    if (user.getId() != null) {
      map.put("id", user.getId());
    }
    if (user.getName() != null) {
      map.put("name", user.getName());
    }
    if (user.getPhone() != null) {
      map.put("phone", user.getPhone());
    }
    if (user.getDepartment() != null) {
      map.put("department", user.getDepartment());
    }
    if (user.getRole() != null) {
      map.put("role", user.getRole());
    }
    if (user.getStatus() != null) {
      map.put("status", user.getStatus());
    }
    if (user.getAvatarUrl() != null) {
      map.put("avatarUrl", user.getAvatarUrl());
    }
    if (user.getNotes() != null) {
      map.put("notes", user.getNotes());
    }
    if (user.getCreatedAt() != null) {
      map.put("createdAt", user.getCreatedAt().getTime());
    }
    if (user.getUpdatedAt() != null) {
      map.put("updatedAt", user.getUpdatedAt().getTime());
    }
    if (user.getLastLogin() != null) {
      map.put("lastLogin", user.getLastLogin().getTime());
    }

    return map;
  }

  /**
   * 检查用户是否匹配关键词
   */
  private boolean matchesKeyword(User user, String keyword) {
    if (user.getName() != null && user.getName().toLowerCase().contains(keyword)) {
      return true;
    }
    if (user.getPhone() != null && user.getPhone().contains(keyword)) {
      return true;
    }
    if (user.getDepartment() != null && user.getDepartment().toLowerCase().contains(keyword)) {
      return true;
    }
    return false;
  }
}