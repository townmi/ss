package work.anyway.packages.auth.plugin;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import work.anyway.annotations.*;
import work.anyway.interfaces.auth.*;
import work.anyway.interfaces.auth.IpBlacklist;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Date;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static work.anyway.packages.auth.plugin.AuthPluginConstants.*;

/**
 * 安全管理控制器
 * 
 * Auth插件的管理模块，提供高级安全管理功能：
 * - 登录安全监控：查看登录统计、趋势、高风险登录
 * - 账户安全管理：锁定/解锁账户、查看被锁定账户
 * - IP安全管理：IP黑名单管理
 * - 系统维护：清理过期数据
 * 
 * 注意：所有接口都需要管理员权限
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Controller
@RequestMapping("/auth/admin/security")
@Intercepted({ INTERCEPTOR_OPERATION }) // 需要完整认证和操作日志
public class SecurityManagementController extends BaseAuthController {

  private static final Logger LOG = LoggerFactory.getLogger(SecurityManagementController.class);

  @Autowired
  private LoginSecurityService loginSecurityService;

  @Autowired
  private LoginLogService loginLogService;

  /**
   * 渲染安全统计页面
   */
  @GetMapping("/stats/page")
  public void renderSecurityStatsPage(RoutingContext ctx) {
    LOG.debug("Rendering security stats page");

    try {
      Map<String, Object> data = new HashMap<>();
      data.put("title", "安全统计");
      data.put("currentUserId", getCurrentUserId(ctx));
      data.put("currentUserRole", getCurrentUserRole(ctx));

      String html = renderTemplate("security-stats.mustache", data);
      ctx.response()
          .putHeader("content-type", "text/html; charset=utf-8")
          .end(html);
    } catch (Exception e) {
      LOG.error("Failed to render security stats page", e);
      sendError(ctx, 500, "Failed to render security stats page: " + e.getMessage());
    }
  }

  /**
   * 渲染被锁定账户页面
   */
  @GetMapping("/locked-accounts/page")
  public void renderLockedAccountsPage(RoutingContext ctx) {
    LOG.debug("Rendering locked accounts page");

    try {
      Map<String, Object> data = new HashMap<>();
      data.put("title", "被锁定账户");
      data.put("currentUserId", getCurrentUserId(ctx));
      data.put("currentUserRole", getCurrentUserRole(ctx));

      String html = renderTemplate("locked-accounts.mustache", data);
      ctx.response()
          .putHeader("content-type", "text/html; charset=utf-8")
          .end(html);
    } catch (Exception e) {
      LOG.error("Failed to render locked accounts page", e);
      sendError(ctx, 500, "Failed to render locked accounts page: " + e.getMessage());
    }
  }

  /**
   * 渲染IP黑名单页面
   */
  @GetMapping("/blacklist/page")
  public void renderBlacklistPage(RoutingContext ctx) {
    LOG.debug("Rendering IP blacklist page");

    try {
      Map<String, Object> data = new HashMap<>();
      data.put("title", "IP黑名单管理");
      data.put("currentUserId", getCurrentUserId(ctx));
      data.put("currentUserRole", getCurrentUserRole(ctx));

      String html = renderTemplate("ip-blacklist.mustache", data);
      ctx.response()
          .putHeader("content-type", "text/html; charset=utf-8")
          .end(html);
    } catch (Exception e) {
      LOG.error("Failed to render IP blacklist page", e);
      sendError(ctx, 500, "Failed to render IP blacklist page: " + e.getMessage());
    }
  }

  /**
   * 获取IP黑名单列表
   */
  @GetMapping("/blacklist")
  public void getBlacklist(RoutingContext ctx) {
    LOG.debug("Getting IP blacklist");

    try {
      List<IpBlacklist> blacklist = loginSecurityService.getIpBlacklist();

      JsonArray blacklistArray = new JsonArray();
      for (IpBlacklist item : blacklist) {
        JsonObject itemJson = new JsonObject()
            .put("ipAddress", item.getIpAddress())
            .put("reason", item.getReason())
            .put("blockedBy", item.getBlockedBy())
            .put("createdAt", item.getCreatedAt())
            .put("updatedAt", item.getUpdatedAt())
            .put("expiresAt", item.getExpiresAt())
            .put("isPermanent", item.getIsPermanent())
            .put("isExpired", item.isExpired());
        blacklistArray.add(itemJson);
      }

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("data", new JsonObject()
              .put("blacklist", blacklistArray)
              .put("total", blacklist.size()));

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());

    } catch (Exception e) {
      LOG.error("Failed to get blacklist", e);
      sendError(ctx, 500, "Failed to get blacklist: " + e.getMessage());
    }
  }

  /**
   * 获取登录安全统计
   */
  @GetMapping("/stats")
  public void getSecurityStats(RoutingContext ctx) {
    LOG.debug("Getting security statistics");

    try {
      int hours = Integer.parseInt(ctx.request().getParam("hours", "24"));

      // 获取登录统计
      Map<String, Long> loginStats = loginLogService.getLoginStatistics(hours, null);

      // 获取高风险登录
      List<LoginLog> highRiskLogins = loginLogService.getHighRiskLogins(hours, 70);

      // 获取被锁定的账户
      List<LoginAttempt> lockedAccounts = loginSecurityService.getLockedAccounts();

      // 获取最活跃的IP
      Map<String, Long> topIps = loginLogService.getTopActiveIps(hours, 10);

      // 获取最频繁的失败登录
      Map<String, Long> topFailedLogins = loginLogService.getTopFailedLogins(hours, 10);

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("data", new JsonObject()
              .put("loginStats", JsonObject.mapFrom(loginStats))
              .put("highRiskLogins", new JsonArray(
                  highRiskLogins.stream()
                      .map(this::loginLogToJson)
                      .toList()))
              .put("lockedAccounts", new JsonArray(
                  lockedAccounts.stream()
                      .map(this::loginAttemptToJson)
                      .toList()))
              .put("topActiveIps", JsonObject.mapFrom(topIps))
              .put("topFailedLogins", JsonObject.mapFrom(topFailedLogins))
              .put("statisticsHours", hours));

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());

    } catch (Exception e) {
      LOG.error("Failed to get security stats", e);
      sendError(ctx, 500, "Failed to get security statistics: " + e.getMessage());
    }
  }

  /**
   * 获取登录趋势数据
   */
  @GetMapping("/trends")
  public void getLoginTrends(RoutingContext ctx) {
    LOG.debug("Getting login trends");

    try {
      int days = Integer.parseInt(ctx.request().getParam("days", "7"));

      Map<String, Map<String, Long>> trends = loginLogService.getLoginTrends(days);

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("data", new JsonObject()
              .put("trends", JsonObject.mapFrom(
                  trends.entrySet().stream()
                      .collect(HashMap::new,
                          (map, entry) -> map.put(entry.getKey(), JsonObject.mapFrom(entry.getValue())),
                          HashMap::putAll)))
              .put("days", days));

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());

    } catch (Exception e) {
      LOG.error("Failed to get login trends", e);
      sendError(ctx, 500, "Failed to get login trends: " + e.getMessage());
    }
  }

  /**
   * 获取被锁定的账户列表
   */
  @GetMapping("/locked-accounts")
  public void getLockedAccounts(RoutingContext ctx) {
    LOG.debug("Getting locked accounts");

    try {
      List<LoginAttempt> lockedAccounts = loginSecurityService.getLockedAccounts();

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("data", new JsonArray(
              lockedAccounts.stream()
                  .map(this::loginAttemptToJson)
                  .toList()))
          .put("total", lockedAccounts.size());

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());

    } catch (Exception e) {
      LOG.error("Failed to get locked accounts", e);
      sendError(ctx, 500, "Failed to get locked accounts: " + e.getMessage());
    }
  }

  /**
   * 解锁账户
   */
  @PostMapping("/unlock-account")
  public void unlockAccount(RoutingContext ctx) {
    JsonObject body = ctx.getBodyAsJson();
    LOG.debug("Unlocking account request");

    if (body == null || body.isEmpty()) {
      sendError(ctx, 400, "Request body is required");
      return;
    }

    try {
      String identifier = body.getString("identifier");
      String adminUserId = getCurrentUserId(ctx); // 从认证上下文获取管理员ID

      if (identifier == null || identifier.trim().isEmpty()) {
        sendError(ctx, 400, "Identifier is required");
        return;
      }

      boolean success = loginSecurityService.unlockAccount(identifier, adminUserId);

      if (success) {
        LOG.info("Account unlocked successfully: {} by admin: {}", identifier, adminUserId);

        JsonObject response = new JsonObject()
            .put("success", true)
            .put("message", "Account unlocked successfully")
            .put("data", new JsonObject()
                .put("identifier", identifier)
                .put("unlockedBy", adminUserId));

        ctx.response()
            .putHeader("content-type", "application/json")
            .end(response.encode());
      } else {
        sendError(ctx, 404, "Account not found or not locked");
      }

    } catch (Exception e) {
      LOG.error("Failed to unlock account", e);
      sendError(ctx, 500, "Failed to unlock account: " + e.getMessage());
    }
  }

  /**
   * 锁定账户
   */
  @PostMapping("/lock-account")
  public void lockAccount(RoutingContext ctx) {
    JsonObject body = ctx.getBodyAsJson();
    LOG.debug("Locking account request");

    if (body == null || body.isEmpty()) {
      sendError(ctx, 400, "Request body is required");
      return;
    }

    try {
      String identifier = body.getString("identifier");
      Integer durationMinutes = body.getInteger("durationMinutes", 15);
      String reason = body.getString("reason", "Manually locked by administrator");

      if (identifier == null || identifier.trim().isEmpty()) {
        sendError(ctx, 400, "Identifier is required");
        return;
      }

      boolean success = loginSecurityService.lockAccount(identifier, durationMinutes, reason);

      if (success) {
        LOG.info("Account locked successfully: {} for {} minutes", identifier, durationMinutes);

        JsonObject response = new JsonObject()
            .put("success", true)
            .put("message", "Account locked successfully")
            .put("data", new JsonObject()
                .put("identifier", identifier)
                .put("durationMinutes", durationMinutes)
                .put("reason", reason));

        ctx.response()
            .putHeader("content-type", "application/json")
            .end(response.encode());
      } else {
        sendError(ctx, 500, "Failed to lock account");
      }

    } catch (Exception e) {
      LOG.error("Failed to lock account", e);
      sendError(ctx, 500, "Failed to lock account: " + e.getMessage());
    }
  }

  /**
   * 获取高风险登录
   */
  @GetMapping("/high-risk-logins")
  public void getHighRiskLogins(RoutingContext ctx) {
    LOG.debug("Getting high risk logins");

    try {
      int hours = Integer.parseInt(ctx.request().getParam("hours", "24"));
      int minRiskScore = Integer.parseInt(ctx.request().getParam("minRiskScore", "70"));

      List<LoginLog> highRiskLogins = loginLogService.getHighRiskLogins(hours, minRiskScore);

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("data", new JsonArray(
              highRiskLogins.stream()
                  .map(this::loginLogToJson)
                  .toList()))
          .put("total", highRiskLogins.size())
          .put("criteria", new JsonObject()
              .put("hours", hours)
              .put("minRiskScore", minRiskScore));

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());

    } catch (Exception e) {
      LOG.error("Failed to get high risk logins", e);
      sendError(ctx, 500, "Failed to get high risk logins: " + e.getMessage());
    }
  }

  /**
   * IP黑名单管理 - 添加IP到黑名单
   */
  @PostMapping("/blacklist-ip")
  public void blacklistIp(RoutingContext ctx) {
    JsonObject body = ctx.getBodyAsJson();
    LOG.debug("Blacklisting IP request");

    if (body == null || body.isEmpty()) {
      sendError(ctx, 400, "Request body is required");
      return;
    }

    try {
      String clientIp = body.getString("clientIp");
      String reason = body.getString("reason", "Manually blacklisted by administrator");
      Integer durationMinutes = body.getInteger("durationMinutes", 0); // 0表示永久
      String adminUserId = getCurrentUserId(ctx);

      if (clientIp == null || clientIp.trim().isEmpty()) {
        sendError(ctx, 400, "Client IP is required");
        return;
      }

      boolean success = loginSecurityService.blacklistIp(clientIp, reason, durationMinutes, adminUserId);

      if (success) {
        LOG.info("IP blacklisted successfully: {} by admin: {}", clientIp, adminUserId);

        JsonObject response = new JsonObject()
            .put("success", true)
            .put("message", "IP blacklisted successfully")
            .put("data", new JsonObject()
                .put("clientIp", clientIp)
                .put("reason", reason)
                .put("durationMinutes", durationMinutes)
                .put("blacklistedBy", adminUserId));

        ctx.response()
            .putHeader("content-type", "application/json")
            .end(response.encode());
      } else {
        sendError(ctx, 500, "Failed to blacklist IP");
      }

    } catch (Exception e) {
      LOG.error("Failed to blacklist IP", e);
      sendError(ctx, 500, "Failed to blacklist IP: " + e.getMessage());
    }
  }

  /**
   * IP黑名单管理 - 从黑名单移除IP
   */
  @PostMapping("/unblacklist-ip")
  public void unblacklistIp(RoutingContext ctx) {
    JsonObject body = ctx.getBodyAsJson();
    LOG.debug("Removing IP from blacklist request");

    if (body == null || body.isEmpty()) {
      sendError(ctx, 400, "Request body is required");
      return;
    }

    try {
      String clientIp = body.getString("clientIp");
      String adminUserId = getCurrentUserId(ctx);

      if (clientIp == null || clientIp.trim().isEmpty()) {
        sendError(ctx, 400, "Client IP is required");
        return;
      }

      boolean success = loginSecurityService.removeIpFromBlacklist(clientIp, adminUserId);

      if (success) {
        LOG.info("IP removed from blacklist successfully: {} by admin: {}", clientIp, adminUserId);

        JsonObject response = new JsonObject()
            .put("success", true)
            .put("message", "IP removed from blacklist successfully")
            .put("data", new JsonObject()
                .put("clientIp", clientIp)
                .put("removedBy", adminUserId));

        ctx.response()
            .putHeader("content-type", "application/json")
            .end(response.encode());
      } else {
        sendError(ctx, 404, "IP not found in blacklist");
      }

    } catch (Exception e) {
      LOG.error("Failed to remove IP from blacklist", e);
      sendError(ctx, 500, "Failed to remove IP from blacklist: " + e.getMessage());
    }
  }

  /**
   * 清理过期数据
   */
  @PostMapping("/cleanup")
  public void cleanupExpiredData(RoutingContext ctx) {
    JsonObject body = ctx.getBodyAsJson();
    LOG.debug("Cleanup expired data request");

    try {
      int logDaysToKeep = 90; // 默认保留90天日志
      int attemptHoursToKeep = 24; // 默认保留24小时的尝试记录

      if (body != null) {
        logDaysToKeep = body.getInteger("logDaysToKeep", logDaysToKeep);
        attemptHoursToKeep = body.getInteger("attemptHoursToKeep", attemptHoursToKeep);
      }

      // 清理过期的登录日志
      long cleanedLogs = loginLogService.cleanExpiredLogs(logDaysToKeep);

      // 清理过期的登录尝试记录
      long cleanedAttempts = loginSecurityService.cleanExpiredAttempts(attemptHoursToKeep);

      LOG.info("Cleanup completed: {} logs cleaned, {} attempts cleaned", cleanedLogs, cleanedAttempts);

      JsonObject response = new JsonObject()
          .put("success", true)
          .put("message", "Cleanup completed successfully")
          .put("data", new JsonObject()
              .put("cleanedLogs", cleanedLogs)
              .put("cleanedAttempts", cleanedAttempts)
              .put("logDaysKept", logDaysToKeep)
              .put("attemptHoursKept", attemptHoursToKeep));

      ctx.response()
          .putHeader("content-type", "application/json")
          .end(response.encode());

    } catch (Exception e) {
      LOG.error("Failed to cleanup expired data", e);
      sendError(ctx, 500, "Failed to cleanup expired data: " + e.getMessage());
    }
  }

  // 私有辅助方法

  /**
   * 将LoginLog转换为JSON
   */
  private JsonObject loginLogToJson(LoginLog log) {
    JsonObject json = new JsonObject()
        .put("id", log.getId())
        .put("userId", log.getUserId())
        .put("identifier", log.getIdentifier())
        .put("identifierType", log.getIdentifierType())
        .put("loginStatus", log.getLoginStatus())
        .put("failureReason", log.getFailureReason())
        .put("clientIp", log.getClientIp())
        .put("userAgent", log.getUserAgent())
        .put("loginSource", log.getLoginSource())
        .put("riskScore", log.getRiskScore())
        .put("loginDuration", log.getLoginDuration())
        .put("createdAt", log.getCreatedAt() != null ? log.getCreatedAt().toInstant().toString() : null);

    return json;
  }

  /**
   * 将LoginAttempt转换为JSON
   */
  private JsonObject loginAttemptToJson(LoginAttempt attempt) {
    JsonObject json = new JsonObject()
        .put("id", attempt.getId())
        .put("identifier", attempt.getIdentifier())
        .put("identifierType", attempt.getIdentifierType())
        .put("clientIp", attempt.getClientIp())
        .put("attemptCount", attempt.getAttemptCount())
        .put("lockLevel", attempt.getLockLevel())
        .put("lockReason", attempt.getLockReason())
        .put("isLocked", attempt.isLocked())
        .put("firstAttemptAt", attempt.getFirstAttemptAt() != null ? attempt.getFirstAttemptAt().toString() : null)
        .put("lastAttemptAt", attempt.getLastAttemptAt() != null ? attempt.getLastAttemptAt().toString() : null)
        .put("lockedUntil", attempt.getLockedUntil() != null ? attempt.getLockedUntil().toString() : null)
        .put("createdAt", attempt.getCreatedAt() != null ? attempt.getCreatedAt().toInstant().toString() : null);

    return json;
  }

  /**
   * 安全统计页面
   * GET /auth/admin/security/page
   */
  @GetMapping("/page")
  public void getSecurityStatsPage(RoutingContext ctx) {
    LOG.debug("Rendering security stats page");

    try {
      int hours = Integer.parseInt(ctx.request().getParam("hours", "24"));

      // 获取统计数据
      Map<String, Long> loginStats = loginLogService.getLoginStatistics(hours, null);
      List<LoginLog> highRiskLogins = loginLogService.getHighRiskLogins(hours, 70);
      Map<String, Long> topIps = loginLogService.getTopActiveIps(hours, 10);

      // 准备页面数据
      Map<String, Object> data = new HashMap<>();
      data.put("pluginName", AuthPluginConstants.PLUGIN_NAME);

      // 统计数据
      Map<String, Object> stats = new HashMap<>();
      stats.put("successLogins", loginStats.getOrDefault("success", 0L));
      stats.put("failedLogins", loginStats.getOrDefault("failed", 0L));
      stats.put("blockedLogins", loginStats.getOrDefault("blocked", 0L));
      long total = loginStats.getOrDefault("total", 1L);
      long success = loginStats.getOrDefault("success", 0L);
      stats.put("successRate", total > 0 ? (success * 100 / total) : 0);
      data.put("stats", stats);

      // 高风险登录
      if (!highRiskLogins.isEmpty()) {
        List<Map<String, Object>> riskItems = new ArrayList<>();
        for (LoginLog log : highRiskLogins) {
          Map<String, Object> item = new HashMap<>();
          item.put("time", formatTime(log.getCreatedAt()));
          item.put("identifier", log.getIdentifier());
          item.put("clientIp", log.getClientIp());
          item.put("riskScore", log.getRiskScore());
          item.put("riskLevel", getRiskLevel(log.getRiskScore()));
          item.put("status", log.getLoginStatus());
          item.put("statusClass", getStatusClass(log.getLoginStatus()));
          item.put("reason", log.getFailureReason());
          riskItems.add(item);
        }
        data.put("highRiskLogins", Map.of("items", riskItems));
      }

      // 最活跃的IP
      if (!topIps.isEmpty()) {
        List<Map<String, Object>> ipItems = new ArrayList<>();
        for (Map.Entry<String, Long> entry : topIps.entrySet()) {
          Map<String, Object> item = new HashMap<>();
          item.put("ip", entry.getKey());
          item.put("totalAttempts", entry.getValue());
          // 这里简化处理，实际应该查询具体的成功/失败次数
          item.put("successCount", entry.getValue() * 7 / 10);
          item.put("failedCount", entry.getValue() * 3 / 10);
          item.put("lastActivity", "刚刚");
          ipItems.add(item);
        }
        data.put("topActiveIps", Map.of("items", ipItems));
      }

      String html = renderTemplate("security-stats.mustache", data);

      ctx.response()
          .putHeader("content-type", "text/html; charset=utf-8")
          .end(html);

    } catch (Exception e) {
      LOG.error("Failed to render security stats page", e);
      ctx.response()
          .setStatusCode(500)
          .end("Internal Server Error");
    }
  }

  /**
   * 锁定账户管理页面
   * GET /auth/admin/security/locked
   */
  @GetMapping("/locked")
  public void getLockedAccountsPage(RoutingContext ctx) {
    LOG.debug("Rendering locked accounts page");

    try {
      // 获取锁定账户数据
      List<LoginAttempt> lockedAccounts = loginSecurityService.getLockedAccounts();

      // 准备页面数据
      Map<String, Object> data = new HashMap<>();
      data.put("pluginName", AuthPluginConstants.PLUGIN_NAME);
      data.put("lockedAccountsCount", lockedAccounts.size());
      data.put("blacklistedIpsCount", 0); // TODO: 实现IP黑名单统计
      data.put("todayUnlockedCount", 0); // TODO: 实现今日解锁统计

      if (!lockedAccounts.isEmpty()) {
        List<Map<String, Object>> accountItems = new ArrayList<>();
        for (LoginAttempt attempt : lockedAccounts) {
          Map<String, Object> item = new HashMap<>();
          item.put("identifier", attempt.getIdentifier());
          item.put("identifierType", attempt.getIdentifierType());
          item.put("lockType", getLockType(attempt));
          item.put("lockTypeClass", getLockTypeClass(attempt));
          item.put("lockReason", attempt.getLockReason());
          item.put("attemptCount", attempt.getAttemptCount());
          item.put("lockedAt", formatTime(convertToDate(attempt.getLastAttemptAt())));
          item.put("lockedUntil", formatTime(convertToDate(attempt.getLockedUntil())));
          accountItems.add(item);
        }
        data.put("lockedAccounts", accountItems);
        data.put("hasLockedAccounts", true);
      } else {
        data.put("hasLockedAccounts", false);
      }

      String html = renderTemplate("locked-accounts.mustache", data);

      ctx.response()
          .putHeader("content-type", "text/html; charset=utf-8")
          .end(html);

    } catch (Exception e) {
      LOG.error("Failed to render locked accounts page", e);
      ctx.response()
          .setStatusCode(500)
          .end("Internal Server Error");
    }
  }

  // 辅助方法

  private Date convertToDate(LocalDateTime localDateTime) {
    if (localDateTime == null)
      return null;
    return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
  }

  private String formatTime(Date date) {
    if (date == null)
      return "-";
    long diff = System.currentTimeMillis() - date.getTime();
    if (diff < 60000)
      return "刚刚";
    if (diff < 3600000)
      return (diff / 60000) + "分钟前";
    if (diff < 86400000)
      return (diff / 3600000) + "小时前";
    return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(date);
  }

  private String getRiskLevel(int riskScore) {
    if (riskScore >= 70)
      return "high";
    if (riskScore >= 40)
      return "medium";
    return "low";
  }

  private String getStatusClass(String status) {
    switch (status) {
      case "success":
        return "success";
      case "failed":
        return "failed";
      case "blocked":
        return "blocked";
      default:
        return "";
    }
  }

  private String getLockType(LoginAttempt attempt) {
    if (attempt.getClientIp() != null && attempt.isLocked()) {
      return "账户+IP";
    }
    return "账户";
  }

  private String getLockTypeClass(LoginAttempt attempt) {
    if (attempt.getClientIp() != null && attempt.isLocked()) {
      return "both";
    }
    return "account";
  }
}