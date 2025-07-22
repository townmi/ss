package work.anyway.packages.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import work.anyway.interfaces.auth.AuthenticationService;
import work.anyway.interfaces.auth.SecurityService;
import work.anyway.interfaces.user.AccountService;
import work.anyway.interfaces.user.AccountType;
import work.anyway.interfaces.user.User;
import work.anyway.interfaces.user.UserAccount;
import work.anyway.interfaces.user.UserService;

import java.util.Optional;

/**
 * 认证服务实现
 * 提供统一的用户认证入口，支持多种登录方式
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Service
public class AuthenticationServiceImpl implements AuthenticationService {

  private static final Logger LOG = LoggerFactory.getLogger(AuthenticationServiceImpl.class);

  @Autowired
  private UserService userService;

  @Autowired
  private AccountService accountService;

  @Autowired
  private SecurityService securityService;

  @Override
  public AuthResult authenticateByEmail(String email, String password) {
    LOG.debug("Authenticating user by email: {}", email);

    try {
      // 1. 验证输入参数
      if (securityService.isBlank(email) || securityService.isBlank(password)) {
        return AuthResult.failure("Email and password are required");
      }

      // 2. 清理邮箱地址
      email = securityService.sanitizeEmail(email);
      if (!securityService.isValidEmail(email)) {
        return AuthResult.failure("Invalid email format");
      }

      // 3. 查找用户
      Optional<User> userOpt = userService.findUserByEmail(email);
      if (userOpt.isEmpty()) {
        LOG.debug("User not found for email: {}", email);
        return AuthResult.failure("Invalid email or password");
      }

      User user = userOpt.get();

      // 4. 检查用户状态
      if (!user.isActive()) {
        LOG.debug("User account is not active: {}", email);
        return AuthResult.failure("Account is not active");
      }

      // 5. 获取邮箱账户并验证密码
      Optional<UserAccount> emailAccountOpt = accountService.findAccount(email, AccountType.EMAIL);
      if (emailAccountOpt.isEmpty()) {
        LOG.debug("Email account not found: {}", email);
        return AuthResult.failure("Invalid email or password");
      }

      UserAccount emailAccount = emailAccountOpt.get();
      String storedPassword = emailAccount.getCredentials();

      // 6. 验证密码
      if (!securityService.verifyPassword(password, storedPassword)) {
        LOG.debug("Password verification failed for email: {}", email);
        return AuthResult.failure("Invalid email or password");
      }

      // 7. 记录登录时间
      accountService.recordLogin(emailAccount.getId());
      userService.updateLastLogin(user.getId());

      LOG.info("User authenticated successfully: {} (ID: {})", email, user.getId());
      return AuthResult.success(user.getId(), emailAccount.getId(), AccountType.EMAIL.getCode());

    } catch (Exception e) {
      LOG.error("Authentication error for email: {}", email, e);
      return AuthResult.failure("Authentication failed");
    }
  }

  @Override
  public AuthResult authenticateByPhone(String phone, String password) {
    LOG.debug("Authenticating user by phone: {}", phone);

    try {
      // 1. 验证输入参数
      if (securityService.isBlank(phone) || securityService.isBlank(password)) {
        return AuthResult.failure("Phone and password are required");
      }

      // 2. 查找用户
      Optional<User> userOpt = userService.findUserByPhone(phone);
      if (userOpt.isEmpty()) {
        LOG.debug("User not found for phone: {}", phone);
        return AuthResult.failure("Invalid phone or password");
      }

      User user = userOpt.get();

      // 3. 检查用户状态
      if (!user.isActive()) {
        LOG.debug("User account is not active: {}", phone);
        return AuthResult.failure("Account is not active");
      }

      // 4. 获取手机账户并验证密码
      Optional<UserAccount> phoneAccountOpt = accountService.findAccount(phone, AccountType.PHONE);
      if (phoneAccountOpt.isEmpty()) {
        LOG.debug("Phone account not found: {}", phone);
        return AuthResult.failure("Invalid phone or password");
      }

      UserAccount phoneAccount = phoneAccountOpt.get();
      String storedPassword = phoneAccount.getCredentials();

      // 5. 验证密码
      if (!securityService.verifyPassword(password, storedPassword)) {
        LOG.debug("Password verification failed for phone: {}", phone);
        return AuthResult.failure("Invalid phone or password");
      }

      // 6. 记录登录时间
      accountService.recordLogin(phoneAccount.getId());
      userService.updateLastLogin(user.getId());

      LOG.info("User authenticated successfully: {} (ID: {})", phone, user.getId());
      return AuthResult.success(user.getId(), phoneAccount.getId(), AccountType.PHONE.getCode());

    } catch (Exception e) {
      LOG.error("Authentication error for phone: {}", phone, e);
      return AuthResult.failure("Authentication failed");
    }
  }

  private AuthResult authenticateByThirdParty(String thirdPartyId, String token, AccountType accountType) {
    LOG.debug("Authenticating user by third party: {} ({})", thirdPartyId, accountType);

    try {
      // 1. 验证输入参数
      if (securityService.isBlank(thirdPartyId) || securityService.isBlank(token)) {
        return AuthResult.failure("Third party ID and token are required");
      }

      if (!accountType.isThirdParty()) {
        return AuthResult.failure("Invalid account type for third party authentication");
      }

      // 2. 查找第三方账户
      Optional<UserAccount> accountOpt = accountService.findAccount(thirdPartyId, accountType);
      if (accountOpt.isEmpty()) {
        LOG.debug("Third party account not found: {} ({})", thirdPartyId, accountType);
        return AuthResult.failure("Invalid third party credentials");
      }

      UserAccount account = accountOpt.get();

      // 3. 验证token
      if (!token.equals(account.getCredentials())) {
        LOG.debug("Token verification failed for third party: {} ({})", thirdPartyId, accountType);
        return AuthResult.failure("Invalid third party credentials");
      }

      // 4. 获取用户信息
      Optional<User> userOpt = userService.getUserById(account.getUserId());
      if (userOpt.isEmpty()) {
        LOG.error("User not found for account: {}", account.getUserId());
        return AuthResult.failure("User account not found");
      }

      User user = userOpt.get();

      // 5. 检查用户状态
      if (!user.isActive()) {
        LOG.debug("User account is not active: {}", user.getId());
        return AuthResult.failure("Account is not active");
      }

      // 6. 记录登录时间
      accountService.recordLogin(account.getId());
      userService.updateLastLogin(user.getId());

      LOG.info("User authenticated successfully via {}: {} (ID: {})",
          accountType, thirdPartyId, user.getId());
      return AuthResult.success(user.getId(), account.getId(), accountType.getCode());

    } catch (Exception e) {
      LOG.error("Third party authentication error: {} ({})", thirdPartyId, accountType, e);
      return AuthResult.failure("Authentication failed");
    }
  }

  @Override
  public AuthResult authenticateByGoogle(String googleToken) {
    LOG.debug("Authenticating user by Google token");
    return authenticateByThirdParty(googleToken, googleToken, AccountType.GOOGLE);
  }

  @Override
  public AuthResult authenticateByWechat(String wechatCode) {
    LOG.debug("Authenticating user by WeChat code");
    return authenticateByThirdParty(wechatCode, wechatCode, AccountType.WECHAT);
  }

  @Override
  public AuthResult authenticateByGithub(String githubToken) {
    LOG.debug("Authenticating user by GitHub token");
    return authenticateByThirdParty(githubToken, githubToken, AccountType.GITHUB);
  }

  @Override
  public AuthResult authenticate(String identifier, String credentials, String accountType) {
    LOG.debug("Generic authentication for identifier: {} with type: {}", identifier, accountType);

    try {
      AccountType type = AccountType.fromCode(accountType);
      if (type == null) {
        return AuthResult.failure("Invalid account type");
      }

      switch (type) {
        case EMAIL:
          return authenticateByEmail(identifier, credentials);
        case PHONE:
          return authenticateByPhone(identifier, credentials);
        case GOOGLE:
          return authenticateByGoogle(credentials);
        case WECHAT:
          return authenticateByWechat(credentials);
        case GITHUB:
          return authenticateByGithub(credentials);
        default:
          return AuthResult.failure("Unsupported account type");
      }
    } catch (Exception e) {
      LOG.error("Generic authentication error", e);
      return AuthResult.failure("Authentication failed");
    }
  }

  @Override
  public boolean validateUserStatus(String userId) {
    LOG.debug("Validating user status: {}", userId);

    if (securityService.isBlank(userId)) {
      return false;
    }

    Optional<User> userOpt = userService.getUserById(userId);
    return userOpt.isPresent() && userOpt.get().isActive();
  }

  @Override
  public void recordSuccessfulLogin(String userId, String accountId, String loginIp) {
    LOG.debug("Recording successful login for user: {}, account: {}, IP: {}",
        userId, accountId, loginIp);

    try {
      // 记录账户登录时间
      if (!securityService.isBlank(accountId)) {
        accountService.recordLogin(accountId);
      }

      // 记录用户登录时间
      if (!securityService.isBlank(userId)) {
        userService.updateLastLogin(userId);
      }

      // TODO: 可以扩展记录到登录日志表
      LOG.info("Login successful - User: {}, Account: {}, IP: {}", userId, accountId, loginIp);

    } catch (Exception e) {
      LOG.error("Failed to record successful login", e);
    }
  }

  @Override
  public void recordFailedLogin(String identifier, String reason, String loginIp) {
    LOG.debug("Recording failed login for identifier: {}, reason: {}, IP: {}",
        identifier, reason, loginIp);

    try {
      // TODO: 可以扩展记录到登录失败日志表
      LOG.warn("Login failed - Identifier: {}, Reason: {}, IP: {}", identifier, reason, loginIp);

    } catch (Exception e) {
      LOG.error("Failed to record failed login", e);
    }
  }

  @Override
  public boolean isLoginAttemptLimited(String identifier) {
    LOG.debug("Checking login attempt limit for identifier: {}", identifier);

    // TODO: 实现登录尝试限制逻辑
    // 可以使用缓存服务来记录失败次数
    return false;
  }

  @Override
  public void clearFailedLoginAttempts(String identifier) {
    LOG.debug("Clearing failed login attempts for identifier: {}", identifier);

    try {
      // TODO: 清除缓存中的失败记录
      LOG.debug("Failed login attempts cleared for: {}", identifier);

    } catch (Exception e) {
      LOG.error("Failed to clear login attempts", e);
    }
  }
}