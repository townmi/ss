package work.anyway.packages.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import work.anyway.interfaces.data.DataService;
import work.anyway.interfaces.user.AccountService;
import work.anyway.interfaces.user.AccountType;
import work.anyway.interfaces.user.UserAccount;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 账户服务实现
 * 使用 DataService 进行数据存储
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Service
public class AccountServiceImpl implements AccountService {

  private static final Logger LOG = LoggerFactory.getLogger(AccountServiceImpl.class);
  private static final String COLLECTION_NAME = "user_accounts";

  @Autowired
  private DataService dataService;

  @Override
  public UserAccount createAccount(UserAccount account) {
    LOG.debug("Creating account: {}", account);

    // 验证必要字段
    if (account.getUserId() == null || account.getUserId().trim().isEmpty()) {
      throw new IllegalArgumentException("User ID is required");
    }
    if (account.getAccountType() == null) {
      throw new IllegalArgumentException("Account type is required");
    }
    if (account.getIdentifier() == null || account.getIdentifier().trim().isEmpty()) {
      throw new IllegalArgumentException("Identifier is required");
    }

    // 检查账户是否已存在
    if (isIdentifierExists(account.getIdentifier(), account.getAccountType())) {
      throw new IllegalArgumentException("Account already exists: " + account.getIdentifier());
    }

    // 生成唯一的账户ID
    if (account.getId() == null || account.getId().trim().isEmpty()) {
      String accountId = UUID.randomUUID().toString();
      account.setId(accountId);
      LOG.debug("Generated new account ID: {}", accountId);
    }

    // 设置默认值
    if (account.getVerified() == null) {
      account.setVerified(false);
    }
    if (account.getPrimaryAccount() == null) {
      account.setPrimaryAccount(false);
    }
    if (account.getCreatedAt() == null) {
      account.setCreatedAt(new Date());
    }
    account.setUpdatedAt(new Date());

    // 转换为Map并保存
    Map<String, Object> accountMap = accountToMap(account);
    Map<String, Object> savedAccountMap = dataService.save(COLLECTION_NAME, accountMap);

    UserAccount savedAccount = mapToAccount(savedAccountMap);
    LOG.info("Account created with id: {}", savedAccount.getId());
    return savedAccount;
  }

  @Override
  public Optional<UserAccount> getAccountById(String accountId) {
    LOG.debug("Getting account by id: {}", accountId);
    Optional<Map<String, Object>> accountMap = dataService.findById(COLLECTION_NAME, accountId);
    return accountMap.map(this::mapToAccount);
  }

  @Override
  public Optional<UserAccount> findAccount(String identifier, AccountType accountType) {
    LOG.debug("Finding account by identifier: {} and type: {}", identifier, accountType);

    Map<String, Object> criteria = new HashMap<>();
    criteria.put("identifier", identifier);
    criteria.put("accountType", accountType.getCode());

    List<Map<String, Object>> accounts = dataService.findByCriteria(COLLECTION_NAME, criteria);
    return accounts.isEmpty() ? Optional.empty() : Optional.of(mapToAccount(accounts.get(0)));
  }

  @Override
  public List<UserAccount> getUserAccounts(String userId) {
    LOG.debug("Getting accounts for user: {}", userId);

    Map<String, Object> criteria = new HashMap<>();
    criteria.put("userId", userId);

    List<Map<String, Object>> accountMaps = dataService.findByCriteria(COLLECTION_NAME, criteria);
    return accountMaps.stream()
        .map(this::mapToAccount)
        .collect(Collectors.toList());
  }

  @Override
  public Optional<UserAccount> getPrimaryAccount(String userId) {
    LOG.debug("Getting primary account for user: {}", userId);

    Map<String, Object> criteria = new HashMap<>();
    criteria.put("userId", userId);
    criteria.put("primaryAccount", true);

    List<Map<String, Object>> accounts = dataService.findByCriteria(COLLECTION_NAME, criteria);
    return accounts.isEmpty() ? Optional.empty() : Optional.of(mapToAccount(accounts.get(0)));
  }

  @Override
  public Optional<UserAccount> getEmailAccount(String userId) {
    LOG.debug("Getting email account for user: {}", userId);

    Map<String, Object> criteria = new HashMap<>();
    criteria.put("userId", userId);
    criteria.put("accountType", AccountType.EMAIL.getCode());

    List<Map<String, Object>> accounts = dataService.findByCriteria(COLLECTION_NAME, criteria);
    return accounts.isEmpty() ? Optional.empty() : Optional.of(mapToAccount(accounts.get(0)));
  }

  @Override
  public boolean verifyCredentials(String identifier, String credentials, AccountType accountType) {
    LOG.debug("Verifying credentials for identifier: {} and type: {}", identifier, accountType);

    Optional<UserAccount> accountOpt = findAccount(identifier, accountType);
    if (accountOpt.isEmpty()) {
      return false;
    }

    UserAccount account = accountOpt.get();
    String storedCredentials = account.getCredentials();

    // 简化验证逻辑：只做基本的字符串比较
    // 密码的哈希验证由AuthenticationService负责
    return credentials != null && credentials.equals(storedCredentials);
  }

  @Override
  public boolean updateAccount(String accountId, UserAccount account) {
    LOG.debug("Updating account {} with info: {}", accountId, account);

    // 检查账户是否存在
    Optional<UserAccount> existing = getAccountById(accountId);
    if (existing.isEmpty()) {
      LOG.warn("Account not found: {}", accountId);
      return false;
    }

    // 更新时间戳
    account.setUpdatedAt(new Date());

    // 转换为Map并更新
    Map<String, Object> accountMap = accountToMap(account);
    boolean updated = dataService.update(COLLECTION_NAME, accountId, accountMap);

    if (updated) {
      LOG.info("Account updated: {}", accountId);
    }
    return updated;
  }

  @Override
  public boolean updateCredentials(String accountId, String credentials) {
    LOG.debug("Updating credentials for account: {}", accountId);

    Optional<UserAccount> accountOpt = getAccountById(accountId);
    if (accountOpt.isEmpty()) {
      return false;
    }

    UserAccount account = accountOpt.get();
    account.setCredentials(credentials);
    return updateAccount(accountId, account);
  }

  @Override
  public boolean setVerificationStatus(String accountId, boolean verified) {
    LOG.debug("Setting verification status for account {} to: {}", accountId, verified);

    Optional<UserAccount> accountOpt = getAccountById(accountId);
    if (accountOpt.isEmpty()) {
      return false;
    }

    UserAccount account = accountOpt.get();
    account.setVerified(verified);
    return updateAccount(accountId, account);
  }

  @Override
  public boolean setPrimaryAccount(String userId, String accountId) {
    LOG.debug("Setting primary account for user {} to account: {}", userId, accountId);

    // 首先取消该用户的所有主账户标记
    List<UserAccount> userAccounts = getUserAccounts(userId);
    for (UserAccount account : userAccounts) {
      if (account.isPrimaryAccount()) {
        account.setPrimaryAccount(false);
        updateAccount(account.getId(), account);
      }
    }

    // 设置新的主账户
    Optional<UserAccount> accountOpt = getAccountById(accountId);
    if (accountOpt.isEmpty()) {
      return false;
    }

    UserAccount account = accountOpt.get();
    if (!account.getUserId().equals(userId)) {
      LOG.warn("Account {} does not belong to user {}", accountId, userId);
      return false;
    }

    account.setPrimaryAccount(true);
    return updateAccount(accountId, account);
  }

  @Override
  public boolean recordLogin(String accountId) {
    LOG.debug("Recording login for account: {}", accountId);

    Optional<UserAccount> accountOpt = getAccountById(accountId);
    if (accountOpt.isEmpty()) {
      return false;
    }

    UserAccount account = accountOpt.get();
    account.setLastLogin(new Date());
    return updateAccount(accountId, account);
  }

  @Override
  public boolean deleteAccount(String accountId) {
    LOG.debug("Deleting account: {}", accountId);

    boolean deleted = dataService.delete(COLLECTION_NAME, accountId);
    if (deleted) {
      LOG.info("Account deleted: {}", accountId);
    } else {
      LOG.warn("Account not found for deletion: {}", accountId);
    }
    return deleted;
  }

  @Override
  public boolean isIdentifierExists(String identifier, AccountType accountType) {
    LOG.debug("Checking if identifier exists: {} for type: {}", identifier, accountType);
    return findAccount(identifier, accountType).isPresent();
  }

  @Override
  public boolean hasAccountType(String userId, AccountType accountType) {
    LOG.debug("Checking if user {} has account type: {}", userId, accountType);

    Map<String, Object> criteria = new HashMap<>();
    criteria.put("userId", userId);
    criteria.put("accountType", accountType.getCode());

    List<Map<String, Object>> accounts = dataService.findByCriteria(COLLECTION_NAME, criteria);
    return !accounts.isEmpty();
  }

  @Override
  public long getAccountCount() {
    LOG.debug("Getting account count");
    return dataService.count(COLLECTION_NAME);
  }

  @Override
  public long getAccountCountByType(AccountType accountType) {
    LOG.debug("Getting account count by type: {}", accountType);

    Map<String, Object> criteria = new HashMap<>();
    criteria.put("accountType", accountType.getCode());

    return dataService.countByCriteria(COLLECTION_NAME, criteria);
  }

  // 辅助方法

  /**
   * 将Map转换为UserAccount实体
   */
  private UserAccount mapToAccount(Map<String, Object> map) {
    UserAccount account = new UserAccount();
    account.setId((String) map.get("id"));
    account.setUserId((String) map.get("userId"));

    String accountTypeCode = (String) map.get("accountType");
    account.setAccountType(AccountType.fromCode(accountTypeCode));

    account.setIdentifier((String) map.get("identifier"));
    account.setCredentials((String) map.get("credentials"));
    account.setVerified(convertToBoolean(map.get("verified")));
    account.setPrimaryAccount(convertToBoolean(map.get("primaryAccount")));
    account.setRegistrationIp((String) map.get("registrationIp"));

    // 处理日期字段
    Object createdAt = map.get("createdAt");
    if (createdAt instanceof Long) {
      account.setCreatedAt(new Date((Long) createdAt));
    } else if (createdAt instanceof Date) {
      account.setCreatedAt((Date) createdAt);
    }

    Object updatedAt = map.get("updatedAt");
    if (updatedAt instanceof Long) {
      account.setUpdatedAt(new Date((Long) updatedAt));
    } else if (updatedAt instanceof Date) {
      account.setUpdatedAt((Date) updatedAt);
    }

    Object lastLogin = map.get("lastLogin");
    if (lastLogin instanceof Long) {
      account.setLastLogin(new Date((Long) lastLogin));
    } else if (lastLogin instanceof Date) {
      account.setLastLogin((Date) lastLogin);
    }

    return account;
  }

  /**
   * 安全地将数据库值转换为Boolean
   * 处理不同数据库驱动返回的不同类型（Byte、Integer、Boolean等）
   */
  private Boolean convertToBoolean(Object value) {
    if (value == null) {
      return false;
    }

    if (value instanceof Boolean) {
      return (Boolean) value;
    }

    if (value instanceof Byte) {
      return ((Byte) value) != 0;
    }

    if (value instanceof Integer) {
      return ((Integer) value) != 0;
    }

    if (value instanceof Long) {
      return ((Long) value) != 0L;
    }

    if (value instanceof String) {
      String str = ((String) value).toLowerCase();
      return "true".equals(str) || "1".equals(str) || "yes".equals(str);
    }

    // 默认返回false
    LOG.warn("Unknown boolean value type: {} ({}), defaulting to false",
        value.getClass().getSimpleName(), value);
    return false;
  }

  /**
   * 将UserAccount实体转换为Map
   */
  private Map<String, Object> accountToMap(UserAccount account) {
    Map<String, Object> map = new HashMap<>();

    if (account.getId() != null) {
      map.put("id", account.getId());
    }
    if (account.getUserId() != null) {
      map.put("userId", account.getUserId());
    }
    if (account.getAccountType() != null) {
      map.put("accountType", account.getAccountType().getCode());
    }
    if (account.getIdentifier() != null) {
      map.put("identifier", account.getIdentifier());
    }
    if (account.getCredentials() != null) {
      map.put("credentials", account.getCredentials());
    }
    if (account.getVerified() != null) {
      map.put("verified", account.getVerified());
    }
    if (account.getPrimaryAccount() != null) {
      map.put("primaryAccount", account.getPrimaryAccount());
    }
    if (account.getRegistrationIp() != null) {
      map.put("registrationIp", account.getRegistrationIp());
    }
    if (account.getCreatedAt() != null) {
      map.put("createdAt", account.getCreatedAt().getTime());
    }
    if (account.getUpdatedAt() != null) {
      map.put("updatedAt", account.getUpdatedAt().getTime());
    }
    if (account.getLastLogin() != null) {
      map.put("lastLogin", account.getLastLogin().getTime());
    }

    return map;
  }
}