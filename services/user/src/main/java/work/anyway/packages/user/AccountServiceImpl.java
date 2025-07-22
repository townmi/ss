package work.anyway.packages.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import work.anyway.interfaces.data.Repository;
import work.anyway.interfaces.data.TypedDataService;
import work.anyway.interfaces.data.QueryCriteria;
import work.anyway.interfaces.user.AccountService;
import work.anyway.interfaces.user.AccountType;
import work.anyway.interfaces.user.UserAccount;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 账户服务实现
 * 使用类型安全的 Repository 进行数据存储
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Service
public class AccountServiceImpl implements AccountService {

  private static final Logger LOG = LoggerFactory.getLogger(AccountServiceImpl.class);

  private final Repository<UserAccount> accountRepository;

  @Autowired
  public AccountServiceImpl(@Qualifier("enhancedDataService") TypedDataService dataService) {
    this.accountRepository = dataService.getRepository("user_accounts", UserAccount.class);
  }

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

    // Repository 会自动处理 ID 和时间戳
    UserAccount savedAccount = accountRepository.save(account);
    LOG.info("Account created with id: {}", savedAccount.getId());
    return savedAccount;
  }

  @Override
  public Optional<UserAccount> getAccountById(String accountId) {
    LOG.debug("Getting account by id: {}", accountId);
    return accountRepository.findById(accountId);
  }

  @Override
  public Optional<UserAccount> findAccount(String identifier, AccountType accountType) {
    LOG.debug("Finding account by identifier: {} and type: {}", identifier, accountType);

    List<UserAccount> accounts = accountRepository.findBy(
        QueryCriteria.<UserAccount>create()
            .eq("identifier", identifier)
            .eq("accountType", accountType));
    return accounts.isEmpty() ? Optional.empty() : Optional.of(accounts.get(0));
  }

  @Override
  public List<UserAccount> getUserAccounts(String userId) {
    LOG.debug("Getting accounts for user: {}", userId);

    return accountRepository.findBy(
        QueryCriteria.<UserAccount>create()
            .eq("userId", userId));
  }

  @Override
  public Optional<UserAccount> getPrimaryAccount(String userId) {
    LOG.debug("Getting primary account for user: {}", userId);

    List<UserAccount> accounts = accountRepository.findBy(
        QueryCriteria.<UserAccount>create()
            .eq("userId", userId)
            .eq("primaryAccount", true));
    return accounts.isEmpty() ? Optional.empty() : Optional.of(accounts.get(0));
  }

  @Override
  public Optional<UserAccount> getEmailAccount(String userId) {
    LOG.debug("Getting email account for user: {}", userId);

    List<UserAccount> accounts = accountRepository.findBy(
        QueryCriteria.<UserAccount>create()
            .eq("userId", userId)
            .eq("accountType", AccountType.EMAIL));
    return accounts.isEmpty() ? Optional.empty() : Optional.of(accounts.get(0));
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

    // 设置ID以确保更新正确的记录
    account.setId(accountId);

    // Repository 会自动处理更新时间戳
    boolean updated = accountRepository.update(account);

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

    boolean deleted = accountRepository.delete(accountId);
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

    List<UserAccount> accounts = accountRepository.findBy(
        QueryCriteria.<UserAccount>create()
            .eq("userId", userId)
            .eq("accountType", accountType));
    return !accounts.isEmpty();
  }

  @Override
  public long getAccountCount() {
    LOG.debug("Getting account count");
    return accountRepository.count();
  }

  @Override
  public long getAccountCountByType(AccountType accountType) {
    LOG.debug("Getting account count by type: {}", accountType);

    return accountRepository.countBy(
        QueryCriteria.<UserAccount>create()
            .eq("accountType", accountType));
  }

}