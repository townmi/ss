package work.anyway.packages.auth.plugin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import work.anyway.annotations.*;
import work.anyway.interfaces.auth.PermissionService;
import work.anyway.interfaces.auth.SecurityService;
import work.anyway.interfaces.auth.LoginSecurityService;
import work.anyway.interfaces.auth.LoginLogService;
import work.anyway.interfaces.auth.LoginAttemptResult;
import work.anyway.interfaces.auth.LoginAttempt;
import work.anyway.interfaces.auth.LoginLog;
import work.anyway.interfaces.cache.CacheService;
import work.anyway.interfaces.user.User;
import work.anyway.interfaces.user.UserService;
import work.anyway.interfaces.user.AccountService;
import work.anyway.interfaces.user.AccountType;
import work.anyway.interfaces.user.UserAccount;
import work.anyway.packages.auth.plugin.utils.JwtTokenUtil;
import work.anyway.interfaces.auth.Permission;

import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 认证管理插件
 * 
 * 提供完整的用户认证和权限管理功能：
 * - 用户认证：登录、注册、登出、令牌管理
 * - 权限管理：权限分配、权限检查
 * - 安全功能：密码重置、邮箱验证、登录保护
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Plugin(name = "Auth Plugin", version = "1.0.0", description = "管理用户认证和权限，提供完整的身份验证和访问控制功能", icon = "🔐", mainPagePath = "/auth/")
// 声明权限定义
@PermissionDef(code = "auth.manage", name = "认证管理", description = "管理用户认证和登录设置", defaultRoles = { "admin" })
@PermissionDef(code = "permission.view", name = "查看权限", description = "查看权限列表和分配情况", defaultRoles = { "admin",
    "manager" })
@PermissionDef(code = "permission.manage", name = "管理权限", description = "分配和撤销权限", defaultRoles = { "admin" })
@PermissionDef(code = "security.view", name = "查看安全信息", description = "查看安全日志和统计", defaultRoles = { "admin",
    "security" })
@PermissionDef(code = "security.manage", name = "安全管理", description = "管理安全设置和黑名单", defaultRoles = { "admin" })
// 声明一级菜单
@MenuItem(id = "auth", title = "认证与权限", icon = "🔐", order = 30)
@Controller
@RequestMapping("/auth")
@Intercepted({ "SystemRequestLog" }) // 插件级别的基础日志记录
public class AuthPlugin {

  private static final Logger LOG = LoggerFactory.getLogger(AuthPlugin.class);

  @Autowired
  private PermissionService permissionService;

  @Autowired(required = false)
  private UserService userService;

  @Autowired(required = false)
  private AccountService accountService;

  @Autowired
  private CacheService cacheService;

  @Autowired
  private JwtTokenUtil jwtTokenUtil;

  @Autowired
  private SecurityService securityService;

  @Autowired(required = false)
  private LoginSecurityService loginSecurityService;

  @Autowired(required = false)
  private LoginLogService loginLogService;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final MustacheFactory mustacheFactory = new DefaultMustacheFactory();

  // 认证 API 端点

  /**
   * 用户注册
   */
  @PostMapping("/register")
  public void registerUser(RoutingContext ctx) {
    JsonObject body = ctx.getBodyAsJson();
    LOG.debug("User registration attempt");

    if (body == null || body.isEmpty()) {
      sendError(ctx, 400, "Request body is required");
      return;
    }

    try {
      // 验证必填字段
      String email = body.getString("email");
      String password = body.getString("password");
      String name = body.getString("name");

      if (securityService.isBlank(email)) {
        sendError(ctx, 400, "Email is required");
        return;
      }

      if (securityService.isBlank(password)) {
        sendError(ctx, 400, "Password is required");
        return;
      }

      if (securityService.isBlank(name)) {
        sendError(ctx, 400, "Name is required");
        return;
      }

      // 数据清理和验证
      email = securityService.sanitizeEmail(email);
      name = name.trim();

      if (!securityService.isValidEmail(email)) {
        sendError(ctx, 400, "Invalid email format");
        return;
      }

      if (!securityService.isStrongPassword(password)) {
        sendError(ctx, 400,
            "Password must be at least 8 characters with uppercase, lowercase, number and special character");
        return;
      }

      if (!securityService.isValidLength(name, 2, 50)) {
        sendError(ctx, 400, "Name must be between 2 and 50 characters");
        return;
      }

      // 检查邮箱是否已存在
      if (userService.isEmailExists(email)) {
        sendError(ctx, 409, "Email already registered");
        return;
      }

      // 检查注册频率限制
      String registrationKey = "register_attempts:" + ctx.request().remoteAddress().host();
      long attempts = cacheService.increment(registrationKey, 1, 3600); // 1小时内限制
      if (attempts > 5) {
        sendError(ctx, 429, "Too many registration attempts. Please try again later");
        return;
      }

      // 加密密码
      String hashedPassword = securityService.hashPassword(password);

      // 1. 创建用户实体
      User user = new User();
      user.setName(name);
      user.setRole(body.getString("role", "user"));
      user.setStatus("active");

      // 添加可选字段
      if (body.containsKey("phone")) {
        user.setPhone(body.getString("phone").trim());
      }
      if (body.containsKey("department")) {
        user.setDepartment(body.getString("department").trim());
      }

      // 创建用户
      User createdUser = userService.createUser(user);
      String userId = createdUser.getId();

      // 2. 创建邮箱账户
      UserAccount emailAccount = new UserAccount();
      emailAccount.setUserId(userId);
      emailAccount.setAccountType(AccountType.EMAIL);
      emailAccount.setIdentifier(email);
      emailAccount.setCredentials(hashedPassword);
      emailAccount.setVerified(false);
      emailAccount.setPrimaryAccount(true);
      emailAccount.setRegistrationIp(ctx.request().remoteAddress().host());

      UserAccount createdAccount = accountService.createAccount(emailAccount);

      // 生成访问令牌
      String accessToken = jwtTokenUtil.generateAccessToken(
          userId,
          email,
          createdUser.getRole());

      String refreshToken = jwtTokenUtil.generateRefreshToken(
          userId,
          email,
          createdUser.getRole());

      // 将刷新令牌存储到缓存
      cacheService.put("refresh_token:" + userId, refreshToken, 604800); // 7天

      // 生成邮箱验证码（如果需要）
      String verificationCode = securityService.generateAlphanumericCode(6);
      cacheService.put("email_verification:" + email, verificationCode, 1800); // 30分钟

      LOG.info("User registered successfully: {} (ID: {})", email, userId);

      // 返回成功响应（不包含敏感信息）
      Map<String, Object> userInfo = new HashMap<>();
      userInfo.put("id", createdUser.getId());
      userInfo.put("name", createdUser.getName());
      userInfo.put("email", email);
      userInfo.put("phone", createdUser.getPhone());
      userInfo.put("department", createdUser.getDepartment());
      userInfo.put("role", createdUser.getRole());
      userInfo.put("status", createdUser.getStatus());
      userInfo.put("createdAt", createdUser.getCreatedAt());
      // 不包含密码等敏感信息

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("message", "User registered successfully")
          .put("data", new JsonObject()
              .put("user", userInfo)
              .put("accessToken", accessToken)
              .put("refreshToken", refreshToken)
              .put("tokenType", "Bearer")
              .put("expiresIn", 3600) // 1小时
              .put("emailVerificationRequired", true)
              .put("verificationCode", verificationCode) // 实际项目中应该通过邮件发送
          );

      ctx.response()
          .setStatusCode(201)
          .putHeader("content-type", "application/json")
          .end(response.encode());

    } catch (IllegalArgumentException e) {
      LOG.warn("Registration validation failed: {}", e.getMessage());
      sendError(ctx, 400, e.getMessage());
    } catch (Exception e) {
      LOG.error("Registration failed", e);
      sendError(ctx, 500, "Registration failed: " + e.getMessage());
    }
  }

  /**
   * 用户登录
   */
  @PostMapping("/login")
  public void loginUser(RoutingContext ctx) {
    JsonObject body = ctx.getBodyAsJson();
    LOG.debug("User login attempt");

    if (body == null || body.isEmpty()) {
      sendError(ctx, 400, "Request body is required");
      return;
    }

    try {
      String email = body.getString("email");
      String password = body.getString("password");

      if (securityService.isBlank(email)) {
        sendError(ctx, 400, "Email is required");
        return;
      }

      if (securityService.isBlank(password)) {
        sendError(ctx, 400, "Password is required");
        return;
      }

      // 数据清理
      email = securityService.sanitizeEmail(email);
      String clientIp = ctx.request().remoteAddress().host();

      LOG.info("=== Starting email login authentication for {} ===", email);
      LOG.info("LoginSecurityService status: {}", loginSecurityService != null ? "Available" : "Not available");
      LOG.info("LoginLogService status: {}", loginLogService != null ? "Available" : "Not available");

      // Step 1: 安全检查 - 使用新的 LoginSecurityService
      if (loginSecurityService != null) {
        LoginAttemptResult securityCheck = loginSecurityService.checkLoginAttempt(email, clientIp);
        LOG.info("Step 2: Security check result: {}", securityCheck);

        if (!securityCheck.isAllowed()) {
          // 记录被阻止的登录尝试
          if (loginLogService != null) {
            recordBlockedLoginAttempt(email, clientIp, securityCheck.getReason());
          }

          JsonObject errorResponse = new JsonObject()
              .put("success", false)
              .put("error", securityCheck.getReason())
              .put("remainingAttempts", securityCheck.getRemainingAttempts())
              .put("waitSeconds", securityCheck.getWaitSeconds());

          if (securityCheck.getLockUntil() != null) {
            errorResponse.put("lockUntil", securityCheck.getLockUntil().toString());
          }

          ctx.response()
              .setStatusCode(429)
              .putHeader("content-type", "application/json")
              .end(errorResponse.encode());
          return;
        }
      } else {
        // Fallback to old cache-based logic if LoginSecurityService is not available
        String loginAttemptsKey = "login_attempts:" + email;
        Object attemptsObj = cacheService.get(loginAttemptsKey);
        long attempts = (attemptsObj instanceof Long) ? (Long) attemptsObj : 0;

        if (attempts >= 5) {
          sendError(ctx, 429, "Too many login attempts. Please try again later");
          return;
        }
      }

      // Step 2: 验证用户凭证
      Optional<User> userOpt = userService.findUserByEmail(email);
      if (userOpt.isEmpty()) {
        recordFailedLoginAttempt(email, clientIp, "User not found");
        sendError(ctx, 401, "Invalid email or password");
        return;
      }

      User user = userOpt.get();

      // 获取邮箱账户来验证密码
      Optional<UserAccount> emailAccountOpt = accountService.findAccount(email, AccountType.EMAIL);
      if (emailAccountOpt.isEmpty()) {
        recordFailedLoginAttempt(email, clientIp, "Email account not found");
        sendError(ctx, 401, "Invalid email or password");
        return;
      }

      UserAccount emailAccount = emailAccountOpt.get();
      String storedPassword = emailAccount.getCredentials();

      if (!securityService.verifyPassword(password, storedPassword)) {
        recordFailedLoginAttempt(email, clientIp, "Invalid password");
        LOG.warn("Login failed for email: {}", email);
        sendError(ctx, 401, "Invalid email or password");
        return;
      }

      // 检查用户状态
      if (!user.isActive()) {
        recordFailedLoginAttempt(email, clientIp, "Account not active");
        sendError(ctx, 403, "Account is not active");
        return;
      }

      // Step 3: 登录成功处理
      String userId = user.getId();
      String userRole = user.getRole();

      // 记录成功登录
      if (loginLogService != null) {
        recordSuccessfulLoginAttempt(email, clientIp, "Email login successful");
      }

      // 重置失败计数（使用新服务或旧缓存）
      if (loginSecurityService != null) {
        loginSecurityService.clearFailedAttempts(email, clientIp);
      } else {
        String loginAttemptsKey = "login_attempts:" + email;
        cacheService.remove(loginAttemptsKey);
      }

      // 生成新的访问令牌
      String accessToken = jwtTokenUtil.generateAccessToken(userId, email, userRole);
      String refreshToken = jwtTokenUtil.generateRefreshToken(userId, email, userRole);

      // 存储刷新令牌
      cacheService.put("refresh_token:" + userId, refreshToken, 604800); // 7天

      // 记录登录信息
      cacheService.put("last_login:" + userId, System.currentTimeMillis(), 86400); // 24小时
      cacheService.put("login_ip:" + userId, clientIp, 86400);

      LOG.info("User logged in successfully: {} (ID: {})", email, userId);

      // 返回用户信息（不包含敏感数据）
      Map<String, Object> userInfo = new HashMap<>();
      userInfo.put("id", user.getId());
      userInfo.put("name", user.getName());
      userInfo.put("email", email);
      userInfo.put("phone", user.getPhone());
      userInfo.put("department", user.getDepartment());
      userInfo.put("role", user.getRole());
      userInfo.put("status", user.getStatus());
      userInfo.put("lastLogin", user.getLastLogin());
      // 不包含密码等敏感信息

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("message", "Login successful")
          .put("data", new JsonObject()
              .put("user", userInfo)
              .put("accessToken", accessToken)
              .put("refreshToken", refreshToken)
              .put("tokenType", "Bearer")
              .put("expiresIn", 3600) // 1小时
          );

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());

    } catch (Exception e) {
      LOG.error("Login failed", e);
      sendError(ctx, 500, "Login failed: " + e.getMessage());
    }
  }

  /**
   * 记录失败的登录尝试
   */
  private void recordFailedLoginAttempt(String identifier, String clientIp, String reason) {
    try {
      // 使用新的登录安全服务
      if (loginSecurityService != null) {
        loginSecurityService.recordFailedAttempt(identifier, "email", clientIp, reason);
      } else {
        // Fallback to old cache-based logic
        String loginAttemptsKey = "login_attempts:" + identifier;
        cacheService.increment(loginAttemptsKey, 1, 900); // 15分钟
      }

      // 记录登录日志
      if (loginLogService != null) {
        LoginLog failedLog = createLoginLog(identifier, "email", clientIp, "failed", reason);
        loginLogService.recordFailedLogin(failedLog);
      }
    } catch (Exception e) {
      LOG.error("Failed to record login attempt", e);
    }
  }

  /**
   * 记录成功的登录尝试
   */
  private void recordSuccessfulLoginAttempt(String identifier, String clientIp, String reason) {
    try {
      if (loginLogService != null) {
        LoginLog successLog = createLoginLog(identifier, "email", clientIp, "success", reason);
        loginLogService.recordSuccessfulLogin(successLog);
      }
    } catch (Exception e) {
      LOG.error("Failed to record successful login", e);
    }
  }

  /**
   * 记录被阻止的登录尝试
   */
  private void recordBlockedLoginAttempt(String identifier, String clientIp, String reason) {
    try {
      if (loginLogService != null) {
        LoginLog blockedLog = createLoginLog(identifier, "email", clientIp, "blocked", reason);
        loginLogService.recordBlockedLogin(blockedLog);
      }
    } catch (Exception e) {
      LOG.error("Failed to record blocked login", e);
    }
  }

  /**
   * 创建登录日志对象
   */
  private LoginLog createLoginLog(String identifier, String identifierType, String clientIp, String status,
      String reason) {
    LoginLog loginLog = new LoginLog();
    loginLog.setIdentifier(identifier);
    loginLog.setIdentifierType(identifierType);
    loginLog.setClientIp(clientIp);
    loginLog.setLoginStatus(status);
    loginLog.setFailureReason(reason);
    loginLog.setUserAgent("Unknown"); // 可以从request header获取
    loginLog.setRiskScore(0); // 可以调用风险评估服务
    return loginLog;
  }

  /**
   * 用户登出
   */
  @PostMapping("/logout")
  public void logoutUser(RoutingContext ctx) {
    String authHeader = ctx.request().getHeader("Authorization");
    LOG.debug("User logout attempt");

    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      sendError(ctx, 401, "Authorization token required");
      return;
    }

    try {
      String token = authHeader.substring(7);

      // 验证并解析token
      JwtTokenUtil.TokenInfo tokenInfo = jwtTokenUtil.parseToken(token);
      if (tokenInfo == null) {
        sendError(ctx, 401, "Invalid token");
        return;
      }

      String userId = tokenInfo.getUserId();

      // 将token加入黑名单
      long remainingTime = jwtTokenUtil.getTokenRemainingTime(token);
      if (remainingTime > 0) {
        cacheService.put("token_blacklist:" + token, true, remainingTime);
      }

      // 清除用户的所有刷新token
      cacheService.removePattern("refresh_token:" + userId);

      // 清除其他会话信息
      cacheService.remove("last_login:" + userId);
      cacheService.remove("login_ip:" + userId);

      LOG.info("User logged out successfully: {}", userId);

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("message", "Logout successful");

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());

    } catch (Exception e) {
      LOG.error("Logout failed", e);
      sendError(ctx, 500, "Logout failed: " + e.getMessage());
    }
  }

  /**
   * 刷新访问令牌
   */
  @PostMapping("/refresh")
  public void refreshToken(RoutingContext ctx) {
    JsonObject body = ctx.getBodyAsJson();
    LOG.debug("Token refresh attempt");

    if (body == null || !body.containsKey("refreshToken")) {
      sendError(ctx, 400, "Refresh token is required");
      return;
    }

    try {
      String refreshToken = body.getString("refreshToken");

      // 验证刷新token
      if (!jwtTokenUtil.isRefreshToken(refreshToken)) {
        sendError(ctx, 401, "Invalid refresh token");
        return;
      }

      JwtTokenUtil.TokenInfo tokenInfo = jwtTokenUtil.parseToken(refreshToken);
      if (tokenInfo == null) {
        sendError(ctx, 401, "Invalid refresh token");
        return;
      }

      String userId = tokenInfo.getUserId();

      // 检查刷新token是否存在于缓存中
      String cachedToken = (String) cacheService.get("refresh_token:" + userId);
      if (!refreshToken.equals(cachedToken)) {
        sendError(ctx, 401, "Refresh token not found or expired");
        return;
      }

      // 生成新的访问令牌
      String newAccessToken = jwtTokenUtil.generateAccessToken(
          userId,
          tokenInfo.getEmail(),
          tokenInfo.getRole());

      LOG.info("Token refreshed successfully for user: {}", userId);

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("data", new JsonObject()
              .put("accessToken", newAccessToken)
              .put("tokenType", "Bearer")
              .put("expiresIn", 3600) // 1小时
          );

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());

    } catch (Exception e) {
      LOG.error("Token refresh failed", e);
      sendError(ctx, 500, "Token refresh failed: " + e.getMessage());
    }
  }

  /**
   * 获取当前用户信息
   */
  @GetMapping("/profile")
  public void getCurrentUser(RoutingContext ctx) {
    String authHeader = ctx.request().getHeader("Authorization");

    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      sendError(ctx, 401, "Authorization token required");
      return;
    }

    try {
      String token = authHeader.substring(7);

      // 检查token是否在黑名单中
      if (cacheService.exists("token_blacklist:" + token)) {
        sendError(ctx, 401, "Token has been revoked");
        return;
      }

      JwtTokenUtil.TokenInfo tokenInfo = jwtTokenUtil.parseToken(token);
      if (tokenInfo == null) {
        sendError(ctx, 401, "Invalid token");
        return;
      }

      // 获取用户详细信息
      Optional<User> userOpt = userService.getUserById(tokenInfo.getUserId());
      if (userOpt.isEmpty()) {
        sendError(ctx, 404, "User not found");
        return;
      }

      User user = userOpt.get();

      // 构建用户信息（不包含敏感数据）
      Map<String, Object> userInfo = new HashMap<>();
      userInfo.put("id", user.getId());
      userInfo.put("name", user.getName());
      userInfo.put("phone", user.getPhone());
      userInfo.put("department", user.getDepartment());
      userInfo.put("role", user.getRole());
      userInfo.put("status", user.getStatus());
      userInfo.put("lastLogin", user.getLastLogin());
      userInfo.put("createdAt", user.getCreatedAt());

      // 获取邮箱信息
      Optional<UserAccount> emailAccountOpt = accountService.getEmailAccount(user.getId());
      if (emailAccountOpt.isPresent()) {
        userInfo.put("email", emailAccountOpt.get().getIdentifier());
      }

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("data", userInfo);

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());

    } catch (Exception e) {
      LOG.error("Failed to get current user", e);
      sendError(ctx, 500, "Failed to get user information: " + e.getMessage());
    }
  }

  /**
   * 发送邮箱验证码
   */
  @PostMapping("/send-verification")
  public void sendEmailVerification(RoutingContext ctx) {
    JsonObject body = ctx.getBodyAsJson();
    LOG.debug("Send email verification request");

    if (body == null || body.isEmpty()) {
      sendError(ctx, 400, "Request body is required");
      return;
    }

    try {
      String email = body.getString("email");

      if (securityService.isBlank(email)) {
        sendError(ctx, 400, "Email is required");
        return;
      }

      // 清理邮箱地址
      email = securityService.sanitizeEmail(email);
      if (!securityService.isValidEmail(email)) {
        sendError(ctx, 400, "Invalid email format");
        return;
      }

      // 检查邮箱是否已注册
      if (!userService.isEmailExists(email)) {
        sendError(ctx, 404, "Email not registered");
        return;
      }

      // 检查发送频率限制
      String rateLimitKey = "email_verification_rate:" + email;
      if (cacheService.exists(rateLimitKey)) {
        sendError(ctx, 429, "Verification code already sent. Please wait before requesting again");
        return;
      }

      // 生成验证码
      String verificationCode = securityService.generateNumericCode(6);

      // 存储验证码（30分钟有效）
      cacheService.put("email_verification:" + email, verificationCode, 1800);

      // 设置发送频率限制（60秒）
      cacheService.put(rateLimitKey, "sent", 60);

      LOG.info("Email verification code sent to: {}", email);

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("message", "Verification code sent successfully")
          .put("data", new JsonObject()
              .put("email", email)
              .put("expiresIn", 1800)
              .put("verificationCode", verificationCode) // 实际项目中应该通过邮件发送
          );

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());

    } catch (Exception e) {
      LOG.error("Failed to send email verification", e);
      sendError(ctx, 500, "Failed to send verification code");
    }
  }

  /**
   * 验证邮箱验证码
   */
  @PostMapping("/verify-email")
  public void verifyEmail(RoutingContext ctx) {
    JsonObject body = ctx.getBodyAsJson();
    LOG.debug("Email verification request");

    if (body == null || body.isEmpty()) {
      sendError(ctx, 400, "Request body is required");
      return;
    }

    try {
      String email = body.getString("email");
      String code = body.getString("code");

      if (securityService.isBlank(email) || securityService.isBlank(code)) {
        sendError(ctx, 400, "Email and verification code are required");
        return;
      }

      // 清理邮箱地址
      email = securityService.sanitizeEmail(email);

      // 获取存储的验证码
      Object storedCode = cacheService.get("email_verification:" + email);
      if (storedCode == null) {
        sendError(ctx, 400, "Verification code expired or invalid");
        return;
      }

      // 验证码比较
      if (!code.trim().equals(storedCode.toString())) {
        sendError(ctx, 400, "Invalid verification code");
        return;
      }

      // 更新账户验证状态
      Optional<UserAccount> emailAccountOpt = accountService.findAccount(email, AccountType.EMAIL);
      if (emailAccountOpt.isPresent()) {
        accountService.setVerificationStatus(emailAccountOpt.get().getId(), true);
      }

      // 清除验证码
      cacheService.remove("email_verification:" + email);

      LOG.info("Email verified successfully: {}", email);

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("message", "Email verified successfully")
          .put("data", new JsonObject()
              .put("email", email)
              .put("verified", true));

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());

    } catch (Exception e) {
      LOG.error("Email verification failed", e);
      sendError(ctx, 500, "Email verification failed");
    }
  }

  /**
   * 发送密码重置邮件
   */
  @PostMapping("/forgot-password")
  public void forgotPassword(RoutingContext ctx) {
    JsonObject body = ctx.getBodyAsJson();
    LOG.debug("Forgot password request");

    if (body == null || body.isEmpty()) {
      sendError(ctx, 400, "Request body is required");
      return;
    }

    try {
      String email = body.getString("email");

      if (securityService.isBlank(email)) {
        sendError(ctx, 400, "Email is required");
        return;
      }

      // 清理邮箱地址
      email = securityService.sanitizeEmail(email);
      if (!securityService.isValidEmail(email)) {
        sendError(ctx, 400, "Invalid email format");
        return;
      }

      // 检查邮箱是否已注册
      if (!userService.isEmailExists(email)) {
        // 为了安全，不透露邮箱是否存在
        LOG.debug("Password reset requested for non-existent email: {}", email);
      } else {
        // 检查发送频率限制
        String rateLimitKey = "password_reset_rate:" + email;
        if (cacheService.exists(rateLimitKey)) {
          sendError(ctx, 429, "Password reset email already sent. Please wait before requesting again");
          return;
        }

        // 生成重置token
        String resetToken = securityService.generatePasswordResetToken();

        // 存储重置token（1小时有效）
        cacheService.put("password_reset:" + email, resetToken, 3600);

        // 设置发送频率限制（5分钟）
        cacheService.put(rateLimitKey, "sent", 300);

        LOG.info("Password reset token generated for: {}", email);
      }

      // 无论邮箱是否存在，都返回成功响应（安全考虑）
      JsonObject response = new JsonObject()
          .put("success", true)
          .put("message", "If the email exists, a password reset link has been sent")
          .put("data", new JsonObject()
              .put("email", email));

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());

    } catch (Exception e) {
      LOG.error("Failed to process forgot password request", e);
      sendError(ctx, 500, "Failed to process request");
    }
  }

  /**
   * 重置密码
   */
  @PostMapping("/reset-password")
  public void resetPassword(RoutingContext ctx) {
    JsonObject body = ctx.getBodyAsJson();
    LOG.debug("Reset password request");

    if (body == null || body.isEmpty()) {
      sendError(ctx, 400, "Request body is required");
      return;
    }

    try {
      String email = body.getString("email");
      String resetToken = body.getString("token");
      String newPassword = body.getString("password");

      if (securityService.isBlank(email) || securityService.isBlank(resetToken) ||
          securityService.isBlank(newPassword)) {
        sendError(ctx, 400, "Email, token, and new password are required");
        return;
      }

      // 清理邮箱地址
      email = securityService.sanitizeEmail(email);

      // 验证新密码强度
      if (!securityService.isStrongPassword(newPassword)) {
        sendError(ctx, 400,
            "Password must be at least 8 characters with uppercase, lowercase, number and special character");
        return;
      }

      // 获取存储的重置token
      Object storedToken = cacheService.get("password_reset:" + email);
      if (storedToken == null) {
        sendError(ctx, 400, "Invalid or expired reset token");
        return;
      }

      // 验证token
      if (!resetToken.trim().equals(storedToken.toString())) {
        sendError(ctx, 400, "Invalid reset token");
        return;
      }

      // 查找邮箱账户
      Optional<UserAccount> emailAccountOpt = accountService.findAccount(email, AccountType.EMAIL);
      if (emailAccountOpt.isEmpty()) {
        sendError(ctx, 400, "Account not found");
        return;
      }

      UserAccount emailAccount = emailAccountOpt.get();

      // 加密新密码
      String hashedPassword = securityService.hashPassword(newPassword);

      // 更新密码
      boolean updated = accountService.updateCredentials(emailAccount.getId(), hashedPassword);
      if (!updated) {
        sendError(ctx, 500, "Failed to update password");
        return;
      }

      // 清除重置token
      cacheService.remove("password_reset:" + email);

      LOG.info("Password reset successfully for: {}", email);

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("message", "Password reset successfully")
          .put("data", new JsonObject()
              .put("email", email));

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());

    } catch (Exception e) {
      LOG.error("Password reset failed", e);
      sendError(ctx, 500, "Password reset failed");
    }
  }

  // 认证页面路由

  /**
   * 登录页面
   */
  @GetMapping("/login")
  @RenderTemplate(value = "login", layout = "auth")
  @Intercepted({ "TemplateRendering" })
  public void getLoginPage(RoutingContext ctx) {
    Map<String, Object> data = new HashMap<>();
    data.put("title", "用户登录");
    data.put("redirectUrl", ctx.request().getParam("redirect"));

    ctx.put("viewData", data);
  }

  /**
   * 注册页面
   */
  @GetMapping("/register")
  @RenderTemplate(value = "register", layout = "auth")
  @Intercepted({ "TemplateRendering" })
  public void getRegisterPage(RoutingContext ctx) {
    Map<String, Object> data = new HashMap<>();
    data.put("title", "用户注册");

    ctx.put("viewData", data);
  }

  /**
   * 忘记密码页面
   */
  @GetMapping("/forgot-password")
  @RenderTemplate(value = "forgot-password", layout = "auth")
  @Intercepted({ "TemplateRendering" })
  public void getForgotPasswordPage(RoutingContext ctx) {
    Map<String, Object> data = new HashMap<>();
    data.put("title", "忘记密码");
    data.put("email", ctx.request().getParam("email")); // 支持预填邮箱

    ctx.put("viewData", data);
  }

  /**
   * 邮箱验证页面（可选）
   */
  @GetMapping("/verify-email")
  public void getVerifyEmailPage(RoutingContext ctx) {
    try {
      Map<String, Object> data = new HashMap<>();
      data.put("title", "邮箱验证");
      data.put("email", ctx.request().getParam("email"));

      // 简单的验证页面模板（可以后续添加）
      String html = "<!DOCTYPE html>" +
          "<html><head><title>邮箱验证</title></head>" +
          "<body style='font-family: Arial; text-align: center; padding: 50px;'>" +
          "<h1>📧 邮箱验证</h1>" +
          "<p>验证邮件已发送到: <strong>" + data.get("email") + "</strong></p>" +
          "<p>请查收邮件并点击验证链接完成注册。</p>" +
          "<a href='/page/auth/login' style='color: #667eea;'>返回登录</a>" +
          "</body></html>";

      ctx.response()
          .putHeader("content-type", "text/html; charset=utf-8")
          .end(html);
    } catch (Exception e) {
      LOG.error("Failed to render verify email page", e);
      ctx.response()
          .setStatusCode(500)
          .end("Internal Server Error");
    }
  }

  /**
   * 获取用户权限
   */
  @GetMapping("/permissions/:userId")
  public void getUserPermissions(RoutingContext ctx) {
    String userId = ctx.pathParam("userId");
    LOG.debug("Getting permissions for user: {}", userId);

    try {
      Set<String> permissions = permissionService.getUserPermissions(userId);

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("userId", userId)
          .put("permissions", new JsonArray(new ArrayList<>(permissions)));

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());
    } catch (Exception e) {
      LOG.error("Failed to get permissions for user: {}", userId, e);
      sendError(ctx, 500, "Failed to retrieve permissions: " + e.getMessage());
    }
  }

  /**
   * 授予权限
   */
  @PostMapping("/permissions/:userId")
  public void grantPermission(RoutingContext ctx) {
    String userId = ctx.pathParam("userId");
    JsonObject body = ctx.getBodyAsJson();

    if (body == null || !body.containsKey("permission")) {
      sendError(ctx, 400, "Permission is required");
      return;
    }

    String permission = body.getString("permission");
    LOG.debug("Granting permission '{}' to user: {}", permission, userId);

    try {
      permissionService.grantPermission(userId, permission);
      LOG.info("Permission '{}' granted to user: {}", permission, userId);

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("message", "Permission granted successfully");

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());
    } catch (Exception e) {
      LOG.error("Failed to grant permission '{}' to user: {}", permission, userId, e);
      sendError(ctx, 500, "Failed to grant permission: " + e.getMessage());
    }
  }

  /**
   * 撤销权限
   */
  @RequestMapping(value = "/permissions/:userId/:permission", method = "DELETE")
  public void revokePermission(RoutingContext ctx) {
    String userId = ctx.pathParam("userId");
    String permission = ctx.pathParam("permission");
    LOG.debug("Revoking permission '{}' from user: {}", permission, userId);

    try {
      permissionService.revokePermission(userId, permission);
      LOG.info("Permission '{}' revoked from user: {}", permission, userId);

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("message", "Permission revoked successfully");

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());
    } catch (Exception e) {
      LOG.error("Failed to revoke permission '{}' from user: {}", permission, userId, e);
      sendError(ctx, 500, "Failed to revoke permission: " + e.getMessage());
    }
  }

  /**
   * 检查权限
   */
  @GetMapping("/check/:userId/:permission")
  public void checkPermission(RoutingContext ctx) {
    String userId = ctx.pathParam("userId");
    String permission = ctx.pathParam("permission");

    try {
      boolean hasPermission = permissionService.hasPermission(userId, permission);

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("userId", userId)
          .put("permission", permission)
          .put("hasPermission", hasPermission);

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());
    } catch (Exception e) {
      LOG.error("Failed to check permission '{}' for user: {}", permission, userId, e);
      sendError(ctx, 500, "Failed to check permission: " + e.getMessage());
    }
  }

  /**
   * 批量授予权限
   */
  @PostMapping("/permissions/:userId/batch")
  public void grantPermissionsBatch(RoutingContext ctx) {
    String userId = ctx.pathParam("userId");
    JsonObject body = ctx.getBodyAsJson();

    if (body == null || !body.containsKey("permissions")) {
      sendError(ctx, 400, "Permissions array is required");
      return;
    }

    JsonArray permissionsArray = body.getJsonArray("permissions");
    List<String> permissions = new ArrayList<>();
    for (int i = 0; i < permissionsArray.size(); i++) {
      permissions.add(permissionsArray.getString(i));
    }

    LOG.debug("Granting {} permissions to user: {}", permissions.size(), userId);

    try {
      for (String permission : permissions) {
        permissionService.grantPermission(userId, permission);
      }
      LOG.info("Granted {} permissions to user: {}", permissions.size(), userId);

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("message", String.format("Granted %d permissions successfully", permissions.size()));

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());
    } catch (Exception e) {
      LOG.error("Failed to grant permissions to user: {}", userId, e);
      sendError(ctx, 500, "Failed to grant permissions: " + e.getMessage());
    }
  }

  /**
   * 获取所有可用权限
   */
  @GetMapping("/permissions/available")
  public void getAvailablePermissions(RoutingContext ctx) {
    try {
      // 从权限服务获取所有权限
      List<Permission> permissions = permissionService.getAllPermissions();

      // 转换为 JSON 数组
      JsonArray permissionArray = new JsonArray();
      for (Permission permission : permissions) {
        JsonObject permObj = new JsonObject()
            .put("code", permission.getCode())
            .put("name", permission.getName())
            .put("description", permission.getDescription())
            .put("pluginName", permission.getPluginName())
            .put("isActive", permission.getIsActive());
        permissionArray.add(permObj);
      }

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("permissions", permissionArray);

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());
    } catch (Exception e) {
      LOG.error("Failed to get available permissions", e);
      sendError(ctx, 500, "Failed to retrieve permissions: " + e.getMessage());
    }
  }

  // 页面路由

  /**
   * 权限管理主页
   */
  @GetMapping("/")
  @MenuItem(title = "认证概览", parentId = "auth", order = 1, permissions = { "auth.manage" })
  @RenderTemplate("auth-dashboard")
  public void getIndexPage(RoutingContext ctx) {
    try {
      // 获取统计数据
      Map<String, Object> data = new HashMap<>();
      data.put("title", "认证概览");
      data.put("pluginName", "Auth Plugin");
      data.put("pluginVersion", "1.0.0");

      // 获取统计信息（如果服务可用）
      if (loginLogService != null) {
        // 今日登录次数
        Map<String, Long> todayStats = loginLogService.getLoginStatistics(24, null);
        data.put("todayLogins", todayStats.getOrDefault("total", 0L));

        // 高风险登录
        List<LoginLog> highRiskLogins = loginLogService.getHighRiskLogins(24, 70);
        data.put("highRiskLogins", highRiskLogins.size());
      } else {
        data.put("todayLogins", 0);
        data.put("highRiskLogins", 0);
      }

      // 活跃用户（简化实现）
      if (userService != null) {
        List<User> allUsers = userService.getAllUsers();
        long activeUsers = allUsers.stream().filter(User::isActive).count();
        data.put("activeUsers", activeUsers);
      } else {
        data.put("activeUsers", 0);
      }

      // 被锁定账户
      if (loginSecurityService != null) {
        List<LoginAttempt> lockedAccounts = loginSecurityService.getLockedAccounts();
        data.put("lockedAccounts", lockedAccounts.size());
      } else {
        data.put("lockedAccounts", 0);
      }

      // 最近活动（示例数据）
      List<Map<String, Object>> recentActivities = new ArrayList<>();
      data.put("recentActivities", recentActivities);

      // 设置数据，框架自动处理渲染
      ctx.put("viewData", data);
    } catch (Exception e) {
      LOG.error("Failed to prepare auth dashboard data", e);
      ctx.response()
          .setStatusCode(500)
          .end("Internal Server Error");
    }
  }

  private Map<String, Object> createActivity(String title, String detail, String statusClass, String icon,
      String time) {
    Map<String, Object> activity = new HashMap<>();
    activity.put("title", title + " - " + detail);
    activity.put("statusClass", statusClass);
    activity.put("icon", icon);
    activity.put("time", time);
    return activity;
  }

  /**
   * 权限列表页面
   */
  @GetMapping("/permissions")
  @MenuItem(title = "权限管理", parentId = "auth", order = 2, permissions = { "permission.manage" })
  @RenderTemplate("permissions")
  @Intercepted({ "TemplateRendering" })
  public void getPermissionsPage(RoutingContext ctx) {
    try {
      // 从权限服务获取所有权限
      List<Permission> permissions = permissionService.getAllPermissions();

      // 转换为页面显示需要的格式
      List<Map<String, Object>> permissionList = new ArrayList<>();
      for (Permission permission : permissions) {
        Map<String, Object> permMap = new HashMap<>();
        permMap.put("code", permission.getCode());
        permMap.put("name", permission.getName());
        permMap.put("description", permission.getDescription());
        permMap.put("pluginName", permission.getPluginName());
        permMap.put("isActive", permission.getIsActive());
        permissionList.add(permMap);
      }

      Map<String, Object> data = new HashMap<>();
      data.put("title", "权限管理");
      data.put("permissions", permissionList);
      data.put("permissionCount", permissions.size());

      // 设置数据，框架自动处理渲染
      ctx.put("viewData", data);
    } catch (Exception e) {
      LOG.error("Failed to prepare permissions page data", e);
      ctx.response()
          .setStatusCode(500)
          .end("Internal Server Error");
    }
  }

  /**
   * 用户权限管理页面
   */
  @GetMapping("/user/:userId")
  @RenderTemplate("user-permissions")
  @Intercepted({ "TemplateRendering" })
  public void getUserPermissionsPage(RoutingContext ctx) {
    String userId = ctx.pathParam("userId");

    try {
      // 获取用户当前权限
      Set<String> userPermissions = permissionService.getUserPermissions(userId);

      // 获取所有系统权限
      List<Permission> allPermissions = permissionService.getAllPermissions();

      // 构建权限状态列表
      List<Map<String, Object>> permissionList = new ArrayList<>();
      for (Permission permission : allPermissions) {
        Map<String, Object> permItem = new HashMap<>();
        permItem.put("code", permission.getCode());
        permItem.put("name", permission.getName());
        permItem.put("description", permission.getDescription());
        permItem.put("granted", userPermissions.contains(permission.getCode()));
        permItem.put("pluginName", permission.getPluginName());
        permissionList.add(permItem);
      }

      Map<String, Object> data = new HashMap<>();
      data.put("title", "用户权限管理");
      data.put("userId", userId);
      data.put("permissions", permissionList);
      data.put("grantedCount", userPermissions.size());
      data.put("totalCount", allPermissions.size());

      // 设置数据，框架自动处理渲染
      ctx.put("viewData", data);
    } catch (Exception e) {
      LOG.error("Failed to prepare user permissions page data", e);
      ctx.response()
          .setStatusCode(500)
          .end("Internal Server Error");
    }
  }

  /**
   * 渲染登录日志页面
   */
  @GetMapping("/logs/page")
  @Intercepted({ "SimpleAuth", "TemplateRendering" })
  @MenuItem(title = "登录日志", parentId = "auth", order = 3, permissions = { "security.view" })
  @RenderTemplate("login-logs")
  public void renderLoginLogsPage(RoutingContext ctx) {
    LOG.debug("Rendering login logs page");

    try {
      Map<String, Object> data = new HashMap<>();
      data.put("title", "登录日志");
      data.put("currentUserId", ctx.get("userId"));
      data.put("currentUserRole", ctx.get("userRole"));

      // 设置数据，框架自动处理渲染
      ctx.put("viewData", data);
    } catch (Exception e) {
      LOG.error("Failed to prepare login logs page data", e);
      ctx.response()
          .setStatusCode(500)
          .end("Internal Server Error");
    }
  }

  /**
   * 认证设置页面
   */
  @GetMapping("/settings/page")
  @Intercepted({ "SimpleAuth", "OperationLog", "TemplateRendering" })
  @MenuItem(title = "认证设置", parentId = "auth", order = 4, permissions = { "auth.manage" })
  @RenderTemplate("auth-settings")
  public void renderSettingsPage(RoutingContext ctx) {
    LOG.debug("Rendering auth settings page");

    try {
      Map<String, Object> data = new HashMap<>();
      data.put("title", "认证设置");
      data.put("currentUserId", ctx.get("userId"));
      data.put("currentUserRole", ctx.get("userRole"));

      // 设置数据，框架自动处理渲染
      ctx.put("viewData", data);
    } catch (Exception e) {
      LOG.error("Failed to prepare settings page data", e);
      ctx.response()
          .setStatusCode(500)
          .end("Internal Server Error");
    }
  }

  // 辅助方法

  private void sendError(RoutingContext ctx, int statusCode, String message) {
    JsonObject error = new JsonObject()
        .put("success", false)
        .put("error", message);

    ctx.response()
        .setStatusCode(statusCode)
        .putHeader("content-type", "application/json")
        .end(error.encode());
  }

  private String renderTemplate(String templateName, Map<String, Object> data) {
    try (InputStream is = getClass().getResourceAsStream("/auth-plugin/templates/" + templateName)) {
      if (is == null) {
        throw new RuntimeException("Template not found: " + templateName);
      }

      Mustache mustache = mustacheFactory.compile(
          new java.io.InputStreamReader(is, StandardCharsets.UTF_8),
          templateName);

      StringWriter writer = new StringWriter();
      mustache.execute(writer, data).flush();
      return writer.toString();
    } catch (Exception e) {
      LOG.error("Error rendering template: " + templateName, e);
      throw new RuntimeException("Template rendering error", e);
    }
  }
}