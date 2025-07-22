package work.anyway.packages.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 登录安全配置类
 * 管理登录安全相关的配置参数
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Component
public class LoginSecurityConfig {

  // 基础安全配置
  @Value("${login.security.max.attempts.account:5}")
  private int maxAttemptsPerAccount;

  @Value("${login.security.max.attempts.ip:20}")
  private int maxAttemptsPerIp;

  @Value("${login.security.lock.duration.minutes:15}")
  private int lockDurationMinutes;

  @Value("${login.security.progressive.delay.base:60}")
  private int progressiveDelayBase;

  // IP自动封禁配置
  @Value("${login.security.ip.auto.blacklist.enabled:true}")
  private boolean ipAutoBlacklistEnabled;

  @Value("${login.security.ip.auto.blacklist.threshold:50}")
  private int ipAutoBlacklistThreshold;

  @Value("${login.security.ip.check.window.hours:1}")
  private int ipCheckWindowHours;

  @Value("${login.security.ip.auto.blacklist.duration.minutes:60}")
  private int ipAutoBlacklistDurationMinutes;

  // 风险评估权重配置
  @Value("${login.security.risk.location.weight:25}")
  private int locationRiskWeight;

  @Value("${login.security.risk.time.weight:15}")
  private int timeRiskWeight;

  @Value("${login.security.risk.device.weight:20}")
  private int deviceRiskWeight;

  @Value("${login.security.risk.frequency.weight:25}")
  private int frequencyRiskWeight;

  @Value("${login.security.risk.behavior.weight:15}")
  private int behaviorRiskWeight;

  // 数据保留配置
  @Value("${login.logs.retention.days:90}")
  private int logRetentionDays;

  @Value("${login.attempts.retention.hours:24}")
  private int attemptRetentionHours;

  // 自动清理配置
  @Value("${login.security.auto.cleanup.enabled:true}")
  private boolean autoCleanupEnabled;

  @Value("${login.security.auto.cleanup.interval.hours:24}")
  private int autoCleanupIntervalHours;

  // 风险阈值配置
  @Value("${login.security.high.risk.threshold:70}")
  private int highRiskThreshold;

  // 异常检测配置
  @Value("${login.security.abnormal.detection.enabled:true}")
  private boolean abnormalDetectionEnabled;

  @Value("${login.security.abnormal.detection.new.ip.enabled:true}")
  private boolean newIpDetectionEnabled;

  @Value("${login.security.abnormal.detection.unusual.time.enabled:true}")
  private boolean unusualTimeDetectionEnabled;

  // 通知配置
  @Value("${login.security.notification.email.enabled:false}")
  private boolean emailNotificationEnabled;

  @Value("${login.security.notification.email.recipients:admin@example.com}")
  private String emailRecipients;

  // 监控配置
  @Value("${login.security.monitoring.enabled:true}")
  private boolean monitoringEnabled;

  @Value("${login.security.monitoring.alert.threshold.failed.attempts:10}")
  private int alertThresholdFailedAttempts;

  @Value("${login.security.monitoring.alert.threshold.blocked.logins:5}")
  private int alertThresholdBlockedLogins;

  // Getters
  public int getMaxAttemptsPerAccount() {
    return maxAttemptsPerAccount;
  }

  public int getMaxAttemptsPerIp() {
    return maxAttemptsPerIp;
  }

  public int getLockDurationMinutes() {
    return lockDurationMinutes;
  }

  public int getProgressiveDelayBase() {
    return progressiveDelayBase;
  }

  public boolean isIpAutoBlacklistEnabled() {
    return ipAutoBlacklistEnabled;
  }

  public int getIpAutoBlacklistThreshold() {
    return ipAutoBlacklistThreshold;
  }

  public int getIpCheckWindowHours() {
    return ipCheckWindowHours;
  }

  public int getIpAutoBlacklistDurationMinutes() {
    return ipAutoBlacklistDurationMinutes;
  }

  public int getLocationRiskWeight() {
    return locationRiskWeight;
  }

  public int getTimeRiskWeight() {
    return timeRiskWeight;
  }

  public int getDeviceRiskWeight() {
    return deviceRiskWeight;
  }

  public int getFrequencyRiskWeight() {
    return frequencyRiskWeight;
  }

  public int getBehaviorRiskWeight() {
    return behaviorRiskWeight;
  }

  public int getLogRetentionDays() {
    return logRetentionDays;
  }

  public int getAttemptRetentionHours() {
    return attemptRetentionHours;
  }

  public boolean isAutoCleanupEnabled() {
    return autoCleanupEnabled;
  }

  public int getAutoCleanupIntervalHours() {
    return autoCleanupIntervalHours;
  }

  public int getHighRiskThreshold() {
    return highRiskThreshold;
  }

  public boolean isAbnormalDetectionEnabled() {
    return abnormalDetectionEnabled;
  }

  public boolean isNewIpDetectionEnabled() {
    return newIpDetectionEnabled;
  }

  public boolean isUnusualTimeDetectionEnabled() {
    return unusualTimeDetectionEnabled;
  }

  public boolean isEmailNotificationEnabled() {
    return emailNotificationEnabled;
  }

  public String getEmailRecipients() {
    return emailRecipients;
  }

  public boolean isMonitoringEnabled() {
    return monitoringEnabled;
  }

  public int getAlertThresholdFailedAttempts() {
    return alertThresholdFailedAttempts;
  }

  public int getAlertThresholdBlockedLogins() {
    return alertThresholdBlockedLogins;
  }

  /**
   * 获取总的风险权重（用于验证配置）
   */
  public int getTotalRiskWeight() {
    return locationRiskWeight + timeRiskWeight + deviceRiskWeight +
        frequencyRiskWeight + behaviorRiskWeight;
  }

  /**
   * 验证配置的有效性
   */
  public boolean isConfigValid() {
    // 基本参数验证
    if (maxAttemptsPerAccount <= 0 || maxAttemptsPerIp <= 0 ||
        lockDurationMinutes <= 0 || progressiveDelayBase <= 0) {
      return false;
    }

    // 风险权重验证（应该接近100）
    int totalWeight = getTotalRiskWeight();
    if (totalWeight < 80 || totalWeight > 120) {
      return false;
    }

    // 保留时间验证
    if (logRetentionDays <= 0 || attemptRetentionHours <= 0) {
      return false;
    }

    // 阈值验证
    if (highRiskThreshold < 0 || highRiskThreshold > 100) {
      return false;
    }

    return true;
  }

  @Override
  public String toString() {
    return "LoginSecurityConfig{" +
        "maxAttemptsPerAccount=" + maxAttemptsPerAccount +
        ", maxAttemptsPerIp=" + maxAttemptsPerIp +
        ", lockDurationMinutes=" + lockDurationMinutes +
        ", progressiveDelayBase=" + progressiveDelayBase +
        ", highRiskThreshold=" + highRiskThreshold +
        ", totalRiskWeight=" + getTotalRiskWeight() +
        ", autoCleanupEnabled=" + autoCleanupEnabled +
        ", monitoringEnabled=" + monitoringEnabled +
        '}';
  }
}