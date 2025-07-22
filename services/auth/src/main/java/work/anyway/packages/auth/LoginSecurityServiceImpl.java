package work.anyway.packages.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import work.anyway.interfaces.auth.*;
import work.anyway.interfaces.data.Repository;
import work.anyway.interfaces.data.TypedDataService;
import work.anyway.interfaces.data.QueryCriteria;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 登录安全服务实现
 * 负责登录尝试限制、风险评估等安全功能
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Service
public class LoginSecurityServiceImpl implements LoginSecurityService {

  private static final Logger LOG = LoggerFactory.getLogger(LoginSecurityServiceImpl.class);

  private final Repository<LoginAttempt> loginAttemptRepository;
  private final Repository<IpBlacklist> ipBlacklistRepository;

  @Autowired
  private LoginSecurityConfig config;

  @Autowired
  public LoginSecurityServiceImpl(@Qualifier("enhancedDataService") TypedDataService dataService) {
    this.loginAttemptRepository = dataService.getRepository("login_attempts", LoginAttempt.class);
    this.ipBlacklistRepository = dataService.getRepository("ip_blacklist", IpBlacklist.class);
  }

  @Override
  public LoginAttemptResult checkLoginAttempt(String identifier, String clientIp) {
    LOG.debug("Checking login attempt for identifier: {}, IP: {}", identifier, clientIp);

    try {
      // 1. 检查IP黑名单（最高优先级）
      if (isIpBlacklisted(clientIp)) {
        LOG.warn("Login attempt from blacklisted IP: {}", clientIp);
        return LoginAttemptResult.blocked("IP address is blacklisted", null);
      }

      // 2. 检查账户级别限制
      Optional<LoginAttempt> accountAttempt = getLoginAttempt(identifier, clientIp);
      if (accountAttempt.isPresent()) {
        LoginAttempt attempt = accountAttempt.get();

        // 检查是否被锁定
        if (attempt.isLocked()) {
          LOG.warn("Account {} is locked until: {}", identifier, attempt.getLockedUntil());
          return LoginAttemptResult.blocked(
              "Account temporarily locked due to too many failed attempts",
              attempt.getLockedUntil());
        }

        // 检查是否需要等待（渐进式延迟）
        int waitSeconds = calculateWaitTime(attempt);
        if (waitSeconds > 0) {
          LOG.debug("Login attempt requires wait: {} seconds for {}", waitSeconds, identifier);
          return LoginAttemptResult.waitRequired(
              "Please wait " + waitSeconds + " seconds before retry",
              waitSeconds);
        }
      }

      // 3. 允许登录尝试
      int remainingAttempts = config.getMaxAttemptsPerAccount() -
          (accountAttempt.map(LoginAttempt::getAttemptCount).orElse(0));

      return LoginAttemptResult.allowed(Math.max(0, remainingAttempts), 0);

    } catch (Exception e) {
      LOG.error("Error checking login attempt", e);
      // 出错时允许登录，但记录错误
      return LoginAttemptResult.allowed(config.getMaxAttemptsPerAccount(), 0);
    }
  }

  @Override
  public boolean recordFailedAttempt(String identifier, String identifierType,
      String clientIp, String failureReason) {
    LOG.debug("Recording failed attempt for identifier: {}, IP: {}", identifier, clientIp);

    try {
      // 1. 只创建用户级别失败记录
      updateOrCreateUserAttempt(identifier, identifierType, clientIp, "account");

      // 2. 检查是否需要自动封禁IP
      if (config.isIpAutoBlacklistEnabled() && shouldAutoBlacklistIp(clientIp)) {
        autoBlacklistIp(clientIp, "Automatic blacklist due to excessive failed attempts from this IP",
            config.getIpAutoBlacklistDurationMinutes());
        LOG.warn("IP {} automatically blacklisted due to excessive failed attempts", clientIp);
      }

      LOG.info("Failed login attempt recorded for identifier: {}, IP: {}, reason: {}",
          identifier, clientIp, failureReason);
      return true;

    } catch (Exception e) {
      LOG.error("Failed to record login attempt", e);
      return false;
    }
  }

  @Override
  public boolean clearFailedAttempts(String identifier, String clientIp) {
    LOG.debug("Clearing failed attempts for identifier: {}, IP: {}", identifier, clientIp);

    try {
      QueryCriteria<LoginAttempt> criteria = QueryCriteria.<LoginAttempt>create()
          .eq("identifier", identifier);
      if (clientIp != null) {
        criteria.eq("clientIp", clientIp);
      }

      List<LoginAttempt> attempts = loginAttemptRepository.findBy(criteria);
      int deletedCount = 0;

      for (LoginAttempt attempt : attempts) {
        if (loginAttemptRepository.delete(attempt.getId())) {
          deletedCount++;
        }
      }

      LOG.info("Cleared {} failed attempts for identifier: {}", deletedCount, identifier);
      return deletedCount > 0;

    } catch (Exception e) {
      LOG.error("Failed to clear login attempts", e);
      return false;
    }
  }

  @Override
  public Optional<LoginAttempt> getLoginAttempt(String identifier, String clientIp) {
    try {
      List<LoginAttempt> results = loginAttemptRepository.findBy(
          QueryCriteria.<LoginAttempt>create()
              .eq("identifier", identifier)
              .eq("clientIp", clientIp));

      if (!results.isEmpty()) {
        return Optional.of(results.get(0));
      }
      return Optional.empty();

    } catch (Exception e) {
      LOG.error("Error getting login attempt", e);
      return Optional.empty();
    }
  }

  @Override
  public boolean lockAccount(String identifier, int lockDurationMinutes, String reason) {
    LOG.info("Manually locking account: {}, duration: {} minutes, reason: {}",
        identifier, lockDurationMinutes, reason);

    try {
      // 查找现有记录或创建新记录
      Optional<LoginAttempt> existingAttempt = getLoginAttempt(identifier, "manual");
      LoginAttempt attempt;

      if (existingAttempt.isPresent()) {
        attempt = existingAttempt.get();
      } else {
        attempt = new LoginAttempt(identifier, "manual", "manual");
        attempt.setId(UUID.randomUUID().toString());
      }

      attempt.setLockLevel("account");
      attempt.setLockReason(reason);
      attempt.setLockedUntil(LocalDateTime.now().plusMinutes(lockDurationMinutes));

      if (existingAttempt.isPresent()) {
        return loginAttemptRepository.update(attempt);
      } else {
        LoginAttempt saved = loginAttemptRepository.save(attempt);
        return saved != null;
      }

    } catch (Exception e) {
      LOG.error("Failed to lock account", e);
      return false;
    }
  }

  @Override
  public boolean unlockAccount(String identifier, String adminUserId) {
    LOG.info("Manually unlocking account: {} by admin: {}", identifier, adminUserId);

    try {
      List<LoginAttempt> attempts = loginAttemptRepository.findBy(
          QueryCriteria.<LoginAttempt>create()
              .eq("identifier", identifier));
      int unlockedCount = 0;

      for (LoginAttempt attempt : attempts) {
        attempt.reset();

        if (loginAttemptRepository.update(attempt)) {
          unlockedCount++;
        }
      }

      LOG.info("Unlocked {} records for account: {}", unlockedCount, identifier);
      return unlockedCount > 0;

    } catch (Exception e) {
      LOG.error("Failed to unlock account", e);
      return false;
    }
  }

  @Override
  public int assessLoginRisk(String identifier, String clientIp, String userAgent) {
    int riskScore = 0;

    try {
      // 1. IP地理位置风险 (0-25分)
      riskScore += assessLocationRisk(identifier, clientIp);

      // 2. 登录时间风险 (0-15分)
      riskScore += assessTimeRisk();

      // 3. 设备风险 (0-20分)
      riskScore += assessDeviceRisk(identifier, userAgent);

      // 4. 频率风险 (0-25分)
      riskScore += assessFrequencyRisk(identifier, clientIp);

      // 5. 历史行为风险 (0-15分)
      riskScore += assessBehaviorRisk(identifier);

      return Math.min(riskScore, 100);

    } catch (Exception e) {
      LOG.error("Error assessing login risk", e);
      return 50; // 默认中等风险
    }
  }

  @Override
  public List<LoginAttempt> getLockedAccounts() {
    try {
      List<LoginAttempt> results = loginAttemptRepository.findBy(
          QueryCriteria.<LoginAttempt>create()
              .eq("lockLevel", "account"));
      return results.stream()
          .filter(LoginAttempt::isLocked)
          .collect(Collectors.toList());

    } catch (Exception e) {
      LOG.error("Error getting locked accounts", e);
      return Collections.emptyList();
    }
  }

  @Override
  public boolean isIpBlacklisted(String clientIp) {
    try {
      List<IpBlacklist> results = ipBlacklistRepository.findBy(
          QueryCriteria.<IpBlacklist>create()
              .eq("ipAddress", clientIp));

      if (results.isEmpty()) {
        return false;
      }

      // 检查是否过期
      IpBlacklist blacklistEntry = results.get(0);
      if (blacklistEntry.isExpired()) {
        // 已过期，删除记录
        ipBlacklistRepository.delete(blacklistEntry.getId());
        LOG.info("Removed expired IP blacklist entry for: {}", clientIp);
        return false;
      }

      return true;

    } catch (Exception e) {
      LOG.error("Error checking IP blacklist", e);
      return false;
    }
  }

  @Override
  public boolean blacklistIp(String clientIp, String reason, int durationMinutes, String adminUserId) {
    LOG.info("Adding IP to blacklist: {}, reason: {}, duration: {} minutes",
        clientIp, reason, durationMinutes);

    try {
      IpBlacklist blacklistEntry = IpBlacklist.builder()
          .ipAddress(clientIp)
          .reason(reason)
          .blockedBy(adminUserId)
          .isPermanent(durationMinutes <= 0)
          .build();

      if (durationMinutes > 0) {
        Date expiresAt = new Date(System.currentTimeMillis() + durationMinutes * 60 * 1000L);
        blacklistEntry.setExpiresAt(expiresAt);
      }

      IpBlacklist saved = ipBlacklistRepository.save(blacklistEntry);
      return saved != null;

    } catch (Exception e) {
      LOG.error("Failed to blacklist IP", e);
      return false;
    }
  }

  @Override
  public boolean removeIpFromBlacklist(String clientIp, String adminUserId) {
    LOG.info("Removing IP from blacklist: {} by admin: {}", clientIp, adminUserId);

    try {
      List<IpBlacklist> results = ipBlacklistRepository.findBy(
          QueryCriteria.<IpBlacklist>create()
              .eq("ipAddress", clientIp));
      int removedCount = 0;

      for (IpBlacklist entry : results) {
        if (ipBlacklistRepository.delete(entry.getId())) {
          removedCount++;
        }
      }

      LOG.info("Removed {} blacklist entries for IP: {}", removedCount, clientIp);
      return removedCount > 0;

    } catch (Exception e) {
      LOG.error("Failed to remove IP from blacklist", e);
      return false;
    }
  }

  @Override
  public List<IpBlacklist> getIpBlacklist() {
    LOG.debug("Getting all IP blacklist entries");
    return ipBlacklistRepository.findAll();
  }

  @Override
  public long cleanExpiredAttempts(int hoursToKeep) {
    LOG.info("Cleaning expired login attempts, keeping {} hours", hoursToKeep);

    try {
      Date cutoffTime = new Date(System.currentTimeMillis() - hoursToKeep * 3600 * 1000L);

      // 查找过期记录
      List<LoginAttempt> allAttempts = loginAttemptRepository.findAll();
      List<String> expiredIds = new ArrayList<>();

      for (LoginAttempt attempt : allAttempts) {
        // 如果最后尝试时间超过保留期限，且没有锁定或锁定已过期
        if (attempt.getLastAttemptAt() != null) {
          // 将 LocalDateTime 转换为 Date 进行比较
          Date lastAttemptDate = Date.from(attempt.getLastAttemptAt()
              .atZone(ZoneId.systemDefault()).toInstant());
          if (lastAttemptDate.before(cutoffTime) && !attempt.isLocked()) {
            expiredIds.add(attempt.getId());
          }
        }
      }

      // 批量删除过期记录
      int deletedCount = loginAttemptRepository.batchDelete(expiredIds);
      LOG.info("Cleaned {} expired login attempts", deletedCount);
      return deletedCount;

    } catch (Exception e) {
      LOG.error("Failed to clean expired attempts", e);
      return 0;
    }
  }

  // 私有辅助方法

  /**
   * 更新或创建用户级别的登录尝试记录
   */
  private void updateOrCreateUserAttempt(String identifier, String identifierType,
      String clientIp, String lockLevel) {
    try {
      // 先查找现有记录
      List<LoginAttempt> results = loginAttemptRepository.findBy(
          QueryCriteria.<LoginAttempt>create()
              .eq("identifier", identifier)
              .eq("clientIp", clientIp));

      LoginAttempt attempt;
      boolean isUpdate = false;

      if (!results.isEmpty()) {
        // 找到现有记录，更新它
        attempt = results.get(0);
        attempt.incrementAttempts();
        isUpdate = true;
      } else {
        // 创建新记录
        attempt = new LoginAttempt(identifier, identifierType, clientIp);
      }

      // 检查是否需要锁定
      int maxAttempts = "account".equals(lockLevel) ? config.getMaxAttemptsPerAccount() : config.getMaxAttemptsPerIp();
      if (attempt.shouldLock(maxAttempts)) {
        attempt.setLockLevel(lockLevel);
        attempt.setLockedUntil(LocalDateTime.now().plusMinutes(config.getLockDurationMinutes()));
        attempt.setLockReason("Too many failed login attempts");
      }

      if (isUpdate) {
        loginAttemptRepository.update(attempt);
        LOG.debug("Updated login attempt record for identifier: {}, IP: {}", identifier, clientIp);
      } else {
        loginAttemptRepository.save(attempt);
        LOG.debug("Created new login attempt record for identifier: {}, IP: {}", identifier, clientIp);
      }

    } catch (Exception e) {
      LOG.error("Error updating user login attempt for identifier: {}, IP: {}", identifier, clientIp, e);
    }
  }

  /**
   * 计算等待时间（渐进式延迟）
   */
  private int calculateWaitTime(LoginAttempt attempt) {
    if (attempt == null || attempt.getAttemptCount() == null || attempt.getAttemptCount() <= 2) {
      return 0;
    }

    // 检查最后尝试时间
    if (attempt.getLastAttemptAt() == null) {
      return 0;
    }

    // 计算渐进式延迟：3次后开始延迟，每次失败延迟时间翻倍
    int baseDelay = config.getProgressiveDelayBase();
    int multiplier = Math.min(attempt.getAttemptCount() - 2, 6); // 最大64倍延迟
    int totalDelay = baseDelay * (1 << multiplier);

    // 计算已经过去的时间
    LocalDateTime lastAttempt = attempt.getLastAttemptAt();
    LocalDateTime now = LocalDateTime.now();
    long elapsedSeconds = java.time.Duration.between(lastAttempt, now).getSeconds();

    return Math.max(0, totalDelay - (int) elapsedSeconds);
  }

  /**
   * 评估地理位置风险
   */
  private int assessLocationRisk(String identifier, String clientIp) {
    // 简化实现：检查是否为新IP
    try {
      List<LoginAttempt> historicalAttempts = loginAttemptRepository.findBy(
          QueryCriteria.<LoginAttempt>create()
              .eq("identifier", identifier));

      boolean isNewIp = historicalAttempts.stream()
          .noneMatch(attempt -> clientIp.equals(attempt.getClientIp()));

      return isNewIp ? 15 : 0;

    } catch (Exception e) {
      LOG.error("Error assessing location risk", e);
      return 5;
    }
  }

  /**
   * 评估时间风险
   */
  private int assessTimeRisk() {
    // 简化实现：检查是否为非工作时间
    int hour = LocalDateTime.now().getHour();
    if (hour < 6 || hour > 22) {
      return 10;
    }
    return 0;
  }

  /**
   * 评估设备风险
   */
  private int assessDeviceRisk(String identifier, String userAgent) {
    // 简化实现：检查User-Agent是否异常
    if (userAgent == null || userAgent.trim().isEmpty()) {
      return 20;
    }

    // 检查是否包含常见的恶意标识
    String lowerAgent = userAgent.toLowerCase();
    if (lowerAgent.contains("bot") || lowerAgent.contains("crawler") || lowerAgent.contains("spider")) {
      return 25;
    }

    return 0;
  }

  /**
   * 评估频率风险
   */
  private int assessFrequencyRisk(String identifier, String clientIp) {
    // 简化实现：检查短时间内的尝试次数
    try {
      List<LoginAttempt> recentAttempts = loginAttemptRepository.findBy(
          QueryCriteria.<LoginAttempt>create()
              .eq("clientIp", clientIp));

      // 检查过去1小时内的尝试次数
      LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
      long recentCount = recentAttempts.stream()
          .filter(attempt -> {
            LocalDateTime lastAttempt = attempt.getLastAttemptAt();
            return lastAttempt != null && lastAttempt.isAfter(oneHourAgo);
          })
          .count();

      if (recentCount > 10) {
        return 25;
      } else if (recentCount > 5) {
        return 15;
      }

      return 0;

    } catch (Exception e) {
      LOG.error("Error assessing frequency risk", e);
      return 5;
    }
  }

  /**
   * 评估行为风险
   */
  private int assessBehaviorRisk(String identifier) {
    // 简化实现：检查历史失败率
    try {
      List<LoginAttempt> attempts = loginAttemptRepository.findBy(
          QueryCriteria.<LoginAttempt>create()
              .eq("identifier", identifier));

      if (attempts.isEmpty()) {
        return 5; // 新用户轻微风险
      }

      // 计算平均失败次数
      double avgFailures = attempts.stream()
          .mapToInt(attempt -> {
            Integer count = attempt.getAttemptCount();
            return count != null ? count : 0;
          })
          .average()
          .orElse(0.0);

      if (avgFailures > 3) {
        return 15;
      } else if (avgFailures > 1) {
        return 8;
      }

      return 0;

    } catch (Exception e) {
      LOG.error("Error assessing behavior risk", e);
      return 5;
    }
  }

  /**
   * 判断是否需要自动封禁IP
   */
  private boolean shouldAutoBlacklistIp(String clientIp) {
    try {
      // 统计该IP在指定时间窗口内的所有失败尝试
      LocalDateTime timeWindow = LocalDateTime.now().minusHours(config.getIpCheckWindowHours());

      // 查询该IP的所有失败记录
      List<LoginAttempt> ipFailures = loginAttemptRepository.findBy(
          QueryCriteria.<LoginAttempt>create()
              .eq("clientIp", clientIp));

      long recentFailures = ipFailures.stream()
          .filter(attempt -> {
            LocalDateTime lastAttempt = attempt.getLastAttemptAt();
            return lastAttempt != null && lastAttempt.isAfter(timeWindow);
          })
          .mapToInt(attempt -> {
            Integer count = attempt.getAttemptCount();
            return count != null ? count : 0;
          })
          .sum();

      LOG.debug("IP {} has {} recent failures in last {} hours, threshold is {}",
          clientIp, recentFailures, config.getIpCheckWindowHours(), config.getIpAutoBlacklistThreshold());

      return recentFailures >= config.getIpAutoBlacklistThreshold();

    } catch (Exception e) {
      LOG.error("Error checking auto-blacklist condition for IP: {}", clientIp, e);
      return false;
    }
  }

  /**
   * 自动封禁IP
   */
  private boolean autoBlacklistIp(String clientIp, String reason, int durationMinutes) {
    try {
      IpBlacklist blacklistEntry = IpBlacklist.builder()
          .ipAddress(clientIp)
          .reason(reason)
          .blockedBy("system")
          .isPermanent(durationMinutes <= 0)
          .build();

      if (durationMinutes > 0) {
        Date expiresAt = new Date(System.currentTimeMillis() + durationMinutes * 60 * 1000L);
        blacklistEntry.setExpiresAt(expiresAt);
      }

      IpBlacklist saved = ipBlacklistRepository.save(blacklistEntry);
      return saved != null;

    } catch (Exception e) {
      LOG.error("Failed to auto-blacklist IP: {}", clientIp, e);
      return false;
    }
  }

}