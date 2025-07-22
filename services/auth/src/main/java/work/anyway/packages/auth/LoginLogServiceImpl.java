package work.anyway.packages.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import work.anyway.interfaces.auth.LoginLog;
import work.anyway.interfaces.auth.LoginLogService;
import work.anyway.interfaces.data.Repository;
import work.anyway.interfaces.data.TypedDataService;
import work.anyway.interfaces.data.QueryCriteria;
import work.anyway.interfaces.data.QueryOptions;
import work.anyway.interfaces.data.PageResult;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 登录日志服务实现
 * 负责记录和查询登录日志
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Service
public class LoginLogServiceImpl implements LoginLogService {

  private static final Logger LOG = LoggerFactory.getLogger(LoginLogServiceImpl.class);

  private final Repository<LoginLog> loginLogRepository;

  @Autowired
  public LoginLogServiceImpl(@Qualifier("enhancedDataService") TypedDataService dataService) {
    this.loginLogRepository = dataService.getRepository("login_logs", LoginLog.class);
  }

  @Override
  public String recordSuccessfulLogin(LoginLog loginLog) {
    LOG.debug("Recording successful login for user: {}, identifier: {}",
        loginLog.getUserId(), loginLog.getIdentifier());

    try {
      loginLog.setLoginStatus("success");

      LoginLog saved = loginLogRepository.save(loginLog);

      if (saved != null) {
        LOG.info("Successful login recorded: user={}, identifier={}, IP={}",
            saved.getUserId(), saved.getIdentifier(), saved.getClientIp());
        return saved.getId();
      }
      return null;

    } catch (Exception e) {
      LOG.error("Failed to record successful login", e);
      return null;
    }
  }

  @Override
  public String recordFailedLogin(LoginLog loginLog) {
    LOG.debug("Recording failed login for identifier: {}, reason: {}",
        loginLog.getIdentifier(), loginLog.getFailureReason());

    try {
      loginLog.setLoginStatus("failed");

      LoginLog saved = loginLogRepository.save(loginLog);

      if (saved != null) {
        LOG.info("Failed login recorded: identifier={}, IP={}, reason={}",
            saved.getIdentifier(), saved.getClientIp(), saved.getFailureReason());
        return saved.getId();
      }
      return null;

    } catch (Exception e) {
      LOG.error("Failed to record failed login", e);
      return null;
    }
  }

  @Override
  public String recordBlockedLogin(LoginLog loginLog) {
    LOG.debug("Recording blocked login for identifier: {}", loginLog.getIdentifier());

    try {
      loginLog.setLoginStatus("blocked");

      // 被阻止的登录通常风险较高
      if (loginLog.getRiskScore() == null || loginLog.getRiskScore() < 70) {
        loginLog.setRiskScore(75);
      }

      LoginLog saved = loginLogRepository.save(loginLog);

      if (saved != null) {
        LOG.warn("Blocked login recorded: identifier={}, IP={}, reason={}",
            saved.getIdentifier(), saved.getClientIp(), saved.getFailureReason());
        return saved.getId();
      }
      return null;

    } catch (Exception e) {
      LOG.error("Failed to record blocked login", e);
      return null;
    }
  }

  @Override
  public List<LoginLog> getUserLoginHistory(String userId, int limit) {
    LOG.debug("Getting login history for user: {}, limit: {}", userId, limit);

    try {
      List<LoginLog> results = loginLogRepository.findBy(
          QueryCriteria.<LoginLog>create()
              .eq("userId", userId)
              .orderBy("createdAt", false) // 按时间倒序
      );

      return results.stream()
          .limit(limit)
          .collect(Collectors.toList());

    } catch (Exception e) {
      LOG.error("Failed to get user login history", e);
      return Collections.emptyList();
    }
  }

  @Override
  public List<LoginLog> getIpLoginHistory(String clientIp, int hours) {
    LOG.debug("Getting IP login history for: {}, hours: {}", clientIp, hours);

    try {
      Date cutoffTime = new Date(System.currentTimeMillis() - hours * 3600 * 1000L);

      List<LoginLog> results = loginLogRepository.findBy(
          QueryCriteria.<LoginLog>create()
              .eq("clientIp", clientIp)
              .orderBy("createdAt", false));

      return results.stream()
          .filter(log -> log.getCreatedAt().after(cutoffTime))
          .collect(Collectors.toList());

    } catch (Exception e) {
      LOG.error("Failed to get IP login history", e);
      return Collections.emptyList();
    }
  }

  @Override
  public List<LoginLog> getIdentifierLoginHistory(String identifier, int hours) {
    LOG.debug("Getting identifier login history for: {}, hours: {}", identifier, hours);

    try {
      Date cutoffTime = new Date(System.currentTimeMillis() - hours * 3600 * 1000L);

      List<LoginLog> results = loginLogRepository.findBy(
          QueryCriteria.<LoginLog>create()
              .eq("identifier", identifier)
              .orderBy("createdAt", false));

      return results.stream()
          .filter(log -> log.getCreatedAt().after(cutoffTime))
          .collect(Collectors.toList());

    } catch (Exception e) {
      LOG.error("Failed to get identifier login history", e);
      return Collections.emptyList();
    }
  }

  @Override
  public List<LoginLog> getHighRiskLogins(int hours, int minRiskScore) {
    LOG.debug("Getting high risk logins, hours: {}, minRiskScore: {}", hours, minRiskScore);

    try {
      Date cutoffTime = new Date(System.currentTimeMillis() - hours * 3600 * 1000L);

      List<LoginLog> allLogs = loginLogRepository.findAll();

      return allLogs.stream()
          .filter(log -> {
            if (log.getCreatedAt().before(cutoffTime)) {
              return false;
            }
            Integer riskScore = log.getRiskScore();
            return riskScore != null && riskScore >= minRiskScore;
          })
          .sorted((a, b) -> {
            // 按风险分数降序，然后按时间降序
            int riskCompare = Integer.compare(
                b.getRiskScore() != null ? b.getRiskScore() : 0,
                a.getRiskScore() != null ? a.getRiskScore() : 0);
            if (riskCompare != 0) {
              return riskCompare;
            }
            return b.getCreatedAt().compareTo(a.getCreatedAt());
          })
          .collect(Collectors.toList());

    } catch (Exception e) {
      LOG.error("Failed to get high risk logins", e);
      return Collections.emptyList();
    }
  }

  @Override
  public List<LoginLog> detectAbnormalLogins(String userId, int hours) {
    LOG.debug("Detecting abnormal logins for user: {}, hours: {}", userId, hours);

    try {
      List<LoginLog> userLogs = getUserLoginHistory(userId, 100); // 获取最近100次登录
      Date cutoffTime = new Date(System.currentTimeMillis() - hours * 3600 * 1000L);

      // 获取用户历史登录的正常模式
      Set<String> normalIps = userLogs.stream()
          .filter(log -> "success".equals(log.getLoginStatus()))
          .filter(log -> log.getCreatedAt().before(cutoffTime))
          .map(LoginLog::getClientIp)
          .collect(Collectors.toSet());

      // 检测异常登录
      return userLogs.stream()
          .filter(log -> log.getCreatedAt().after(cutoffTime))
          .filter(log -> {
            // 异常条件：新IP、高风险分数、异常时间等
            boolean isNewIp = !normalIps.contains(log.getClientIp());
            boolean isHighRisk = log.getRiskScore() != null && log.getRiskScore() > 70;
            boolean isAbnormalTime = isAbnormalLoginTime(log.getCreatedAt());

            return isNewIp || isHighRisk || isAbnormalTime;
          })
          .collect(Collectors.toList());

    } catch (Exception e) {
      LOG.error("Failed to detect abnormal logins", e);
      return Collections.emptyList();
    }
  }

  @Override
  public Map<String, Long> getLoginStatistics(int hours, String status) {
    LOG.debug("Getting login statistics, hours: {}, status: {}", hours, status);

    try {
      Date cutoffTime = new Date(System.currentTimeMillis() - hours * 3600 * 1000L);

      QueryCriteria<LoginLog> criteria = QueryCriteria.<LoginLog>create();
      if (status != null && !status.isEmpty()) {
        criteria.eq("loginStatus", status);
      }

      List<LoginLog> results = loginLogRepository.findBy(criteria);

      List<LoginLog> recentLogs = results.stream()
          .filter(log -> log.getCreatedAt().after(cutoffTime))
          .collect(Collectors.toList());

      Map<String, Long> statistics = new HashMap<>();
      statistics.put("total", (long) recentLogs.size());
      statistics.put("success", recentLogs.stream()
          .filter(log -> "success".equals(log.getLoginStatus()))
          .count());
      statistics.put("failed", recentLogs.stream()
          .filter(log -> "failed".equals(log.getLoginStatus()))
          .count());
      statistics.put("blocked", recentLogs.stream()
          .filter(log -> "blocked".equals(log.getLoginStatus()))
          .count());
      statistics.put("uniqueUsers", recentLogs.stream()
          .filter(log -> log.getUserId() != null)
          .map(LoginLog::getUserId)
          .distinct()
          .count());
      statistics.put("uniqueIps", recentLogs.stream()
          .map(LoginLog::getClientIp)
          .distinct()
          .count());

      return statistics;

    } catch (Exception e) {
      LOG.error("Failed to get login statistics", e);
      return Collections.emptyMap();
    }
  }

  @Override
  public Map<String, Map<String, Long>> getLoginTrends(int days) {
    LOG.debug("Getting login trends for {} days", days);

    try {
      Date cutoffTime = new Date(System.currentTimeMillis() - days * 24 * 3600 * 1000L);

      List<LoginLog> results = loginLogRepository.findAll();

      List<LoginLog> recentLogs = results.stream()
          .filter(log -> log.getCreatedAt().after(cutoffTime))
          .collect(Collectors.toList());

      Map<String, Map<String, Long>> trends = new LinkedHashMap<>();

      // 按天分组统计
      for (int i = days - 1; i >= 0; i--) {
        Date dayStart = new Date(System.currentTimeMillis() - i * 24 * 3600 * 1000L);
        Date dayEnd = new Date(dayStart.getTime() + 24 * 3600 * 1000L);
        String dayKey = String.format("%tF", dayStart);

        List<LoginLog> dayLogs = recentLogs.stream()
            .filter(log -> log.getCreatedAt().after(dayStart) && log.getCreatedAt().before(dayEnd))
            .collect(Collectors.toList());

        Map<String, Long> dayStats = new HashMap<>();
        dayStats.put("total", (long) dayLogs.size());
        dayStats.put("success", dayLogs.stream()
            .filter(log -> "success".equals(log.getLoginStatus()))
            .count());
        dayStats.put("failed", dayLogs.stream()
            .filter(log -> "failed".equals(log.getLoginStatus()))
            .count());
        dayStats.put("blocked", dayLogs.stream()
            .filter(log -> "blocked".equals(log.getLoginStatus()))
            .count());

        trends.put(dayKey, dayStats);
      }

      return trends;

    } catch (Exception e) {
      LOG.error("Failed to get login trends", e);
      return Collections.emptyMap();
    }
  }

  @Override
  public Map<String, Long> getTopActiveIps(int hours, int limit) {
    LOG.debug("Getting top active IPs, hours: {}, limit: {}", hours, limit);

    try {
      Date cutoffTime = new Date(System.currentTimeMillis() - hours * 3600 * 1000L);

      List<LoginLog> results = loginLogRepository.findAll();

      Map<String, Long> ipCounts = results.stream()
          .filter(log -> log.getCreatedAt().after(cutoffTime))
          .collect(Collectors.groupingBy(
              LoginLog::getClientIp,
              Collectors.counting()));

      return ipCounts.entrySet().stream()
          .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
          .limit(limit)
          .collect(Collectors.toMap(
              Map.Entry::getKey,
              Map.Entry::getValue,
              (e1, e2) -> e1,
              LinkedHashMap::new));

    } catch (Exception e) {
      LOG.error("Failed to get top active IPs", e);
      return Collections.emptyMap();
    }
  }

  @Override
  public Map<String, Long> getTopFailedLogins(int hours, int limit) {
    LOG.debug("Getting top failed logins, hours: {}, limit: {}", hours, limit);

    try {
      Date cutoffTime = new Date(System.currentTimeMillis() - hours * 3600 * 1000L);

      List<LoginLog> results = loginLogRepository.findAll();

      Map<String, Long> failedCounts = results.stream()
          .filter(log -> log.getCreatedAt().after(cutoffTime))
          .filter(log -> "failed".equals(log.getLoginStatus()) || "blocked".equals(log.getLoginStatus()))
          .collect(Collectors.groupingBy(
              LoginLog::getIdentifier,
              Collectors.counting()));

      return failedCounts.entrySet().stream()
          .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
          .limit(limit)
          .collect(Collectors.toMap(
              Map.Entry::getKey,
              Map.Entry::getValue,
              (e1, e2) -> e1,
              LinkedHashMap::new));

    } catch (Exception e) {
      LOG.error("Failed to get top failed logins", e);
      return Collections.emptyMap();
    }
  }

  @Override
  public long cleanExpiredLogs(int daysToKeep) {
    LOG.info("Cleaning expired login logs, keeping {} days", daysToKeep);

    try {
      Date cutoffTime = new Date(System.currentTimeMillis() - daysToKeep * 24 * 3600 * 1000L);

      List<LoginLog> allLogs = loginLogRepository.findAll();
      List<String> expiredIds = allLogs.stream()
          .filter(log -> log.getCreatedAt() != null && log.getCreatedAt().before(cutoffTime))
          .map(LoginLog::getId)
          .collect(Collectors.toList());

      int deletedCount = loginLogRepository.batchDelete(expiredIds);
      LOG.info("Cleaned {} expired login logs", deletedCount);
      return deletedCount;

    } catch (Exception e) {
      LOG.error("Failed to clean expired logs", e);
      return 0;
    }
  }

  @Override
  public LoginLog getLoginLogById(String logId) {
    LOG.debug("Getting login log by ID: {}", logId);

    try {
      Optional<LoginLog> result = loginLogRepository.findById(logId);
      return result.orElse(null);

    } catch (Exception e) {
      LOG.error("Failed to get login log by ID", e);
      return null;
    }
  }

  @Override
  public List<LoginLog> searchLoginLogs(Map<String, Object> criteria, int limit) {
    LOG.debug("Searching login logs with criteria: {}, limit: {}", criteria, limit);

    try {
      QueryCriteria<LoginLog> queryCriteria = QueryCriteria.<LoginLog>create();
      criteria.forEach(queryCriteria::eq);

      List<LoginLog> results = loginLogRepository.findBy(
          queryCriteria.orderBy("createdAt", false));

      return results.stream()
          .limit(limit)
          .collect(Collectors.toList());

    } catch (Exception e) {
      LOG.error("Failed to search login logs", e);
      return Collections.emptyList();
    }
  }

  // 私有辅助方法

  /**
   * 检查是否为异常登录时间
   */
  private boolean isAbnormalLoginTime(Date loginTime) {
    Calendar cal = Calendar.getInstance();
    cal.setTime(loginTime);
    int hour = cal.get(Calendar.HOUR_OF_DAY);
    int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);

    // 深夜时间 (0-5点) 或周末深夜
    return hour < 6 || (hour > 23 && (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY));
  }

}