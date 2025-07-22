package work.anyway.packages.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import work.anyway.interfaces.auth.*;
import work.anyway.interfaces.data.DataService;

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

  // 数据库表名
  private static final String LOGIN_ATTEMPTS_COLLECTION = "login_attempts";
  private static final String IP_BLACKLIST_COLLECTION = "ip_blacklist";

  // 配置参数现在从LoginSecurityConfig获取

  @Autowired
  private DataService dataService;

  @Autowired
  private LoginSecurityConfig config;

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
      Map<String, Object> criteria = new HashMap<>();
      criteria.put("identifier", identifier);
      if (clientIp != null) {
        criteria.put("clientIp", clientIp);
      }

      List<Map<String, Object>> attempts = dataService.findByCriteria(LOGIN_ATTEMPTS_COLLECTION, criteria);
      int deletedCount = 0;

      for (Map<String, Object> attemptMap : attempts) {
        String attemptId = (String) attemptMap.get("id");
        if (dataService.delete(LOGIN_ATTEMPTS_COLLECTION, attemptId)) {
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
      Map<String, Object> criteria = new HashMap<>();
      criteria.put("identifier", identifier);
      criteria.put("clientIp", clientIp);

      List<Map<String, Object>> results = dataService.findByCriteria(LOGIN_ATTEMPTS_COLLECTION, criteria);
      if (!results.isEmpty()) {
        return Optional.of(mapToLoginAttempt(results.get(0)));
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
      attempt.setUpdatedAt(new Date());

      Map<String, Object> attemptMap = loginAttemptToMap(attempt);
      if (existingAttempt.isPresent()) {
        return dataService.update(LOGIN_ATTEMPTS_COLLECTION, attempt.getId(), attemptMap);
      } else {
        attemptMap = dataService.save(LOGIN_ATTEMPTS_COLLECTION, attemptMap);
        return attemptMap != null;
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
      Map<String, Object> criteria = new HashMap<>();
      criteria.put("identifier", identifier);

      List<Map<String, Object>> attempts = dataService.findByCriteria(LOGIN_ATTEMPTS_COLLECTION, criteria);
      int unlockedCount = 0;

      for (Map<String, Object> attemptMap : attempts) {
        LoginAttempt attempt = mapToLoginAttempt(attemptMap);
        attempt.reset();
        attempt.setUpdatedAt(new Date());

        if (dataService.update(LOGIN_ATTEMPTS_COLLECTION, attempt.getId(), loginAttemptToMap(attempt))) {
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
      Map<String, Object> criteria = new HashMap<>();
      criteria.put("lockLevel", "account");

      List<Map<String, Object>> results = dataService.findByCriteria(LOGIN_ATTEMPTS_COLLECTION, criteria);
      return results.stream()
          .map(this::mapToLoginAttempt)
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
      Map<String, Object> criteria = new HashMap<>();
      criteria.put("ipAddress", clientIp);

      List<Map<String, Object>> results = dataService.findByCriteria(IP_BLACKLIST_COLLECTION, criteria);
      if (results.isEmpty()) {
        return false;
      }

      // 检查是否过期
      Map<String, Object> blacklistEntry = results.get(0);
      Date expiresAt = (Date) blacklistEntry.get("expiresAt");
      if (expiresAt != null && expiresAt.before(new Date())) {
        // 已过期，删除记录
        String entryId = (String) blacklistEntry.get("id");
        dataService.delete(IP_BLACKLIST_COLLECTION, entryId);
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
      Map<String, Object> blacklistEntry = new HashMap<>();
      blacklistEntry.put("id", UUID.randomUUID().toString());
      blacklistEntry.put("ipAddress", clientIp);
      blacklistEntry.put("blacklistType", "manual");
      blacklistEntry.put("reason", reason);
      blacklistEntry.put("createdBy", adminUserId);

      if (durationMinutes > 0) {
        Date expiresAt = new Date(System.currentTimeMillis() + durationMinutes * 60 * 1000L);
        blacklistEntry.put("expiresAt", expiresAt);
      }

      blacklistEntry.put("createdAt", new Date());
      blacklistEntry.put("updatedAt", new Date());

      Map<String, Object> saved = dataService.save(IP_BLACKLIST_COLLECTION, blacklistEntry);
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
      Map<String, Object> criteria = new HashMap<>();
      criteria.put("ipAddress", clientIp);

      List<Map<String, Object>> results = dataService.findByCriteria(IP_BLACKLIST_COLLECTION, criteria);
      int removedCount = 0;

      for (Map<String, Object> entry : results) {
        String entryId = (String) entry.get("id");
        if (dataService.delete(IP_BLACKLIST_COLLECTION, entryId)) {
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
  public long cleanExpiredAttempts(int hoursToKeep) {
    LOG.info("Cleaning expired login attempts, keeping {} hours", hoursToKeep);

    try {
      Date cutoffTime = new Date(System.currentTimeMillis() - hoursToKeep * 3600 * 1000L);

      // 查找过期记录
      List<Map<String, Object>> allAttempts = dataService.findAll(LOGIN_ATTEMPTS_COLLECTION);
      List<String> expiredIds = new ArrayList<>();

      for (Map<String, Object> attemptMap : allAttempts) {
        Date lastAttemptAt = (Date) attemptMap.get("lastAttemptAt");
        Date lockedUntil = (Date) attemptMap.get("lockedUntil");

        // 如果最后尝试时间超过保留期限，且没有锁定或锁定已过期
        if (lastAttemptAt != null && lastAttemptAt.before(cutoffTime)) {
          if (lockedUntil == null || lockedUntil.before(new Date())) {
            expiredIds.add((String) attemptMap.get("id"));
          }
        }
      }

      // 批量删除过期记录
      int deletedCount = dataService.batchDelete(LOGIN_ATTEMPTS_COLLECTION, expiredIds);
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
      // 先查找现有记录（使用更安全的查询方式）
      Map<String, Object> criteria = new HashMap<>();
      criteria.put("identifier", identifier);
      criteria.put("clientIp", clientIp);

      List<Map<String, Object>> results = dataService.findByCriteria(LOGIN_ATTEMPTS_COLLECTION, criteria);

      LoginAttempt attempt;
      boolean isUpdate = false;

      if (!results.isEmpty()) {
        // 找到现有记录，更新它
        Map<String, Object> existingData = results.get(0);
        attempt = mapToLoginAttempt(existingData);
        attempt.incrementAttempts();
        isUpdate = true;
      } else {
        // 创建新记录
        attempt = new LoginAttempt(identifier, identifierType, clientIp);
        attempt.setId(UUID.randomUUID().toString());
        attempt.setCreatedAt(new Date());
      }

      attempt.setUpdatedAt(new Date());

      // 检查是否需要锁定
      int maxAttempts = "account".equals(lockLevel) ? config.getMaxAttemptsPerAccount() : config.getMaxAttemptsPerIp();
      if (attempt.shouldLock(maxAttempts)) {
        attempt.setLockLevel(lockLevel);
        attempt.setLockedUntil(LocalDateTime.now().plusMinutes(config.getLockDurationMinutes()));
        attempt.setLockReason("Too many failed login attempts");
      }

      Map<String, Object> attemptMap = loginAttemptToMap(attempt);

      if (isUpdate) {
        dataService.update(LOGIN_ATTEMPTS_COLLECTION, attempt.getId(), attemptMap);
        LOG.debug("Updated login attempt record for identifier: {}, IP: {}", identifier, clientIp);
      } else {
        try {
          dataService.save(LOGIN_ATTEMPTS_COLLECTION, attemptMap);
          LOG.debug("Created new login attempt record for identifier: {}, IP: {}", identifier, clientIp);
        } catch (Exception saveEx) {
          // 如果保存失败（可能是并发导致的唯一约束冲突），尝试更新
          if (saveEx.getMessage() != null && saveEx.getMessage().contains("Duplicate entry")) {
            LOG.warn("Duplicate entry detected, attempting to update existing record for identifier: {}, IP: {}",
                identifier, clientIp);

            // 重新查找记录并更新
            List<Map<String, Object>> retryResults = dataService.findByCriteria(LOGIN_ATTEMPTS_COLLECTION, criteria);
            if (!retryResults.isEmpty()) {
              Map<String, Object> existingData = retryResults.get(0);
              String existingId = (String) existingData.get("id");
              dataService.update(LOGIN_ATTEMPTS_COLLECTION, existingId, attemptMap);
              LOG.debug("Successfully updated existing record after duplicate entry error");
            } else {
              throw saveEx; // 重新抛出原始异常
            }
          } else {
            throw saveEx; // 重新抛出原始异常
          }
        }
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
      Map<String, Object> criteria = new HashMap<>();
      criteria.put("identifier", identifier);

      List<Map<String, Object>> historicalAttempts = dataService.findByCriteria(LOGIN_ATTEMPTS_COLLECTION, criteria);

      boolean isNewIp = historicalAttempts.stream()
          .noneMatch(attempt -> clientIp.equals(attempt.get("clientIp")));

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
      Map<String, Object> criteria = new HashMap<>();
      criteria.put("clientIp", clientIp);

      List<Map<String, Object>> recentAttempts = dataService.findByCriteria(LOGIN_ATTEMPTS_COLLECTION, criteria);

      // 检查过去1小时内的尝试次数
      Date oneHourAgo = new Date(System.currentTimeMillis() - 3600 * 1000);
      long recentCount = recentAttempts.stream()
          .filter(attempt -> {
            Date lastAttempt = (Date) attempt.get("lastAttemptAt");
            return lastAttempt != null && lastAttempt.after(oneHourAgo);
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
      Map<String, Object> criteria = new HashMap<>();
      criteria.put("identifier", identifier);

      List<Map<String, Object>> attempts = dataService.findByCriteria(LOGIN_ATTEMPTS_COLLECTION, criteria);

      if (attempts.isEmpty()) {
        return 5; // 新用户轻微风险
      }

      // 计算平均失败次数
      double avgFailures = attempts.stream()
          .mapToInt(attempt -> {
            Integer count = (Integer) attempt.get("attemptCount");
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
      Date timeWindow = new Date(System.currentTimeMillis() - config.getIpCheckWindowHours() * 3600 * 1000L);

      // 查询该IP的所有失败记录
      Map<String, Object> criteria = new HashMap<>();
      criteria.put("clientIp", clientIp);

      List<Map<String, Object>> ipFailures = dataService.findByCriteria(LOGIN_ATTEMPTS_COLLECTION, criteria);

      long recentFailures = ipFailures.stream()
          .filter(attempt -> {
            Date lastAttempt = convertToDate(attempt.get("lastAttemptAt"));
            return lastAttempt != null && lastAttempt.after(timeWindow);
          })
          .mapToInt(attempt -> {
            Integer count = (Integer) attempt.get("attemptCount");
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
      Map<String, Object> blacklistEntry = new HashMap<>();
      blacklistEntry.put("id", UUID.randomUUID().toString());
      blacklistEntry.put("ipAddress", clientIp);
      blacklistEntry.put("blacklistType", "auto"); // 修正为正确的枚举值
      blacklistEntry.put("reason", reason);
      blacklistEntry.put("lastViolationAt", new Date());
      blacklistEntry.put("createdAt", new Date());
      blacklistEntry.put("updatedAt", new Date());

      if (durationMinutes > 0) {
        Date expiresAt = new Date(System.currentTimeMillis() + durationMinutes * 60 * 1000L);
        blacklistEntry.put("expiresAt", expiresAt);
      }

      Map<String, Object> saved = dataService.save(IP_BLACKLIST_COLLECTION, blacklistEntry);
      return saved != null;

    } catch (Exception e) {
      LOG.error("Failed to auto-blacklist IP: {}", clientIp, e);
      return false;
    }
  }

  /**
   * 将Map转换为LoginAttempt实体
   */
  private LoginAttempt mapToLoginAttempt(Map<String, Object> map) {
    LoginAttempt attempt = new LoginAttempt();
    attempt.setId((String) map.get("id"));
    attempt.setIdentifier((String) map.get("identifier"));
    attempt.setIdentifierType((String) map.get("identifierType"));
    attempt.setClientIp((String) map.get("clientIp"));
    attempt.setAttemptCount((Integer) map.get("attemptCount"));
    attempt.setLockLevel((String) map.get("lockLevel"));
    attempt.setLockReason((String) map.get("lockReason"));
    attempt.setCreatedAt(convertToDate(map.get("createdAt")));
    attempt.setUpdatedAt(convertToDate(map.get("updatedAt")));

    // 处理时间字段
    Date firstAttemptAt = convertToDate(map.get("firstAttemptAt"));
    if (firstAttemptAt != null) {
      attempt.setFirstAttemptAt(firstAttemptAt.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
    }

    Date lastAttemptAt = convertToDate(map.get("lastAttemptAt"));
    if (lastAttemptAt != null) {
      attempt.setLastAttemptAt(lastAttemptAt.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
    }

    Date lockedUntil = convertToDate(map.get("lockedUntil"));
    if (lockedUntil != null) {
      attempt.setLockedUntil(lockedUntil.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
    }

    return attempt;
  }

  /**
   * 将LoginAttempt实体转换为Map
   */
  private Map<String, Object> loginAttemptToMap(LoginAttempt attempt) {
    Map<String, Object> map = new HashMap<>();
    map.put("id", attempt.getId());
    map.put("identifier", attempt.getIdentifier());
    map.put("identifierType", attempt.getIdentifierType());
    map.put("clientIp", attempt.getClientIp());
    map.put("attemptCount", attempt.getAttemptCount());
    map.put("lockLevel", attempt.getLockLevel());
    map.put("lockReason", attempt.getLockReason());
    map.put("createdAt", attempt.getCreatedAt());
    map.put("updatedAt", attempt.getUpdatedAt());

    if (attempt.getFirstAttemptAt() != null) {
      map.put("firstAttemptAt", Date.from(attempt.getFirstAttemptAt().atZone(ZoneId.systemDefault()).toInstant()));
    }

    if (attempt.getLastAttemptAt() != null) {
      map.put("lastAttemptAt", Date.from(attempt.getLastAttemptAt().atZone(ZoneId.systemDefault()).toInstant()));
    }

    if (attempt.getLockedUntil() != null) {
      map.put("lockedUntil", Date.from(attempt.getLockedUntil().atZone(ZoneId.systemDefault()).toInstant()));
    }

    return map;
  }

  /**
   * 将对象转换为Date类型
   */
  private Date convertToDate(Object obj) {
    if (obj == null) {
      return null;
    }
    if (obj instanceof Date) {
      return (Date) obj;
    }
    if (obj instanceof Long) {
      return new Date((Long) obj);
    }
    return null;
  }

  /**
   * 将对象转换为LocalDateTime类型
   */
  private LocalDateTime convertToLocalDateTime(Object obj) {
    if (obj == null) {
      return null;
    }
    if (obj instanceof Date) {
      return ((Date) obj).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
    if (obj instanceof Long) {
      return new Date((Long) obj).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
    return null;
  }
}