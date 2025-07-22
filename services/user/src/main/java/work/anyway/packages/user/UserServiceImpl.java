package work.anyway.packages.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import work.anyway.interfaces.data.Repository;
import work.anyway.interfaces.data.TypedDataService;
import work.anyway.interfaces.data.QueryCriteria;
import work.anyway.interfaces.user.User;
import work.anyway.interfaces.user.UserService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import work.anyway.interfaces.user.AccountService;
import work.anyway.interfaces.user.AccountType;
import work.anyway.interfaces.user.UserAccount;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 用户服务实现
 * 使用类型安全的 Repository 进行数据存储
 */
@Service
public class UserServiceImpl implements UserService {

  private static final Logger LOG = LoggerFactory.getLogger(UserServiceImpl.class);

  private final Repository<User> userRepository;

  @Autowired
  private AccountService accountService;

  @Autowired
  public UserServiceImpl(@Qualifier("enhancedDataService") TypedDataService dataService) {
    this.userRepository = dataService.getRepository("users", User.class);
  }

  @Override
  public List<User> getAllUsers() {
    LOG.debug("Getting all users");
    return userRepository.findAll();
  }

  @Override
  public Optional<User> getUserById(String userId) {
    LOG.debug("Getting user by id: {}", userId);
    return userRepository.findById(userId);
  }

  @Override
  public User createUser(User user) {
    LOG.debug("Creating user with info: {}", user);

    // 验证必要字段
    if (user.getName() == null || user.getName().trim().isEmpty()) {
      throw new IllegalArgumentException("Name is required");
    }

    // Repository 会自动处理 ID 和时间戳
    User savedUser = userRepository.save(user);
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

    // 设置ID以确保更新正确的记录
    user.setId(userId);

    // Repository 会自动处理更新时间戳
    boolean updated = userRepository.update(user);

    if (updated) {
      LOG.info("User updated: {}", userId);
    }
    return updated;
  }

  @Override
  public boolean deleteUser(String userId) {
    LOG.debug("Deleting user: {}", userId);

    boolean deleted = userRepository.delete(userId);
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

    List<User> users = userRepository.findBy(
        QueryCriteria.<User>create().eq("phone", phone));
    return users.isEmpty() ? Optional.empty() : Optional.of(users.get(0));
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
    return userRepository.count();
  }

  @Override
  public List<User> getUsersByRole(String role) {
    LOG.debug("Getting users by role: {}", role);
    return userRepository.findBy(
        QueryCriteria.<User>create().eq("role", role));
  }

  @Override
  public List<User> getUsersByStatus(String status) {
    LOG.debug("Getting users by status: {}", status);
    return userRepository.findBy(
        QueryCriteria.<User>create().eq("status", status));
  }

  @Override
  public List<User> getUsersByDepartment(String department) {
    LOG.debug("Getting users by department: {}", department);
    return userRepository.findBy(
        QueryCriteria.<User>create().eq("department", department));
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