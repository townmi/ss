package work.anyway.interfaces.auth;

import java.time.LocalDateTime;
import java.util.Date;

/**
 * 登录日志实体
 * 记录用户的登录历史信息
 * 
 * @author 作者名
 * @since 1.0.0
 */
public class LoginLog {

  private String id; // 日志ID
  private String userId; // 用户ID（成功登录时有值）
  private String identifier; // 登录标识符
  private String identifierType; // 标识符类型
  private String loginStatus; // 登录状态
  private String failureReason; // 失败原因
  private String clientIp; // 客户端IP
  private String userAgent; // 用户代理
  private String loginSource; // 登录来源
  private String sessionId; // 会话ID
  private String locationInfo; // 地理位置信息（JSON格式）
  private String deviceInfo; // 设备信息（JSON格式）
  private Integer riskScore; // 风险评分
  private Integer loginDuration; // 登录耗时（毫秒）
  private Date createdAt; // 创建时间

  public LoginLog() {
  }

  public LoginLog(String identifier, String identifierType, String loginStatus, String clientIp) {
    this.identifier = identifier;
    this.identifierType = identifierType;
    this.loginStatus = loginStatus;
    this.clientIp = clientIp;
    this.loginSource = "web";
    this.riskScore = 0;
    this.createdAt = new Date();
  }

  // Getters and Setters
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getIdentifier() {
    return identifier;
  }

  public void setIdentifier(String identifier) {
    this.identifier = identifier;
  }

  public String getIdentifierType() {
    return identifierType;
  }

  public void setIdentifierType(String identifierType) {
    this.identifierType = identifierType;
  }

  public String getLoginStatus() {
    return loginStatus;
  }

  public void setLoginStatus(String loginStatus) {
    this.loginStatus = loginStatus;
  }

  public String getFailureReason() {
    return failureReason;
  }

  public void setFailureReason(String failureReason) {
    this.failureReason = failureReason;
  }

  public String getClientIp() {
    return clientIp;
  }

  public void setClientIp(String clientIp) {
    this.clientIp = clientIp;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }

  public String getLoginSource() {
    return loginSource;
  }

  public void setLoginSource(String loginSource) {
    this.loginSource = loginSource;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public String getLocationInfo() {
    return locationInfo;
  }

  public void setLocationInfo(String locationInfo) {
    this.locationInfo = locationInfo;
  }

  public String getDeviceInfo() {
    return deviceInfo;
  }

  public void setDeviceInfo(String deviceInfo) {
    this.deviceInfo = deviceInfo;
  }

  public Integer getRiskScore() {
    return riskScore;
  }

  public void setRiskScore(Integer riskScore) {
    this.riskScore = riskScore;
  }

  public Integer getLoginDuration() {
    return loginDuration;
  }

  public void setLoginDuration(Integer loginDuration) {
    this.loginDuration = loginDuration;
  }

  public Date getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Date createdAt) {
    this.createdAt = createdAt;
  }

  /**
   * 构建器模式
   */
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private LoginLog loginLog = new LoginLog();

    public Builder userId(String userId) {
      loginLog.setUserId(userId);
      return this;
    }

    public Builder identifier(String identifier) {
      loginLog.setIdentifier(identifier);
      return this;
    }

    public Builder identifierType(String identifierType) {
      loginLog.setIdentifierType(identifierType);
      return this;
    }

    public Builder loginStatus(String loginStatus) {
      loginLog.setLoginStatus(loginStatus);
      return this;
    }

    public Builder failureReason(String failureReason) {
      loginLog.setFailureReason(failureReason);
      return this;
    }

    public Builder clientIp(String clientIp) {
      loginLog.setClientIp(clientIp);
      return this;
    }

    public Builder userAgent(String userAgent) {
      loginLog.setUserAgent(userAgent);
      return this;
    }

    public Builder loginSource(String loginSource) {
      loginLog.setLoginSource(loginSource);
      return this;
    }

    public Builder sessionId(String sessionId) {
      loginLog.setSessionId(sessionId);
      return this;
    }

    public Builder locationInfo(String locationInfo) {
      loginLog.setLocationInfo(locationInfo);
      return this;
    }

    public Builder deviceInfo(String deviceInfo) {
      loginLog.setDeviceInfo(deviceInfo);
      return this;
    }

    public Builder riskScore(Integer riskScore) {
      loginLog.setRiskScore(riskScore);
      return this;
    }

    public Builder loginDuration(Integer loginDuration) {
      loginLog.setLoginDuration(loginDuration);
      return this;
    }

    public LoginLog build() {
      if (loginLog.getCreatedAt() == null) {
        loginLog.setCreatedAt(new Date());
      }
      return loginLog;
    }
  }

  @Override
  public String toString() {
    return "LoginLog{" +
        "id='" + id + '\'' +
        ", userId='" + userId + '\'' +
        ", identifier='" + identifier + '\'' +
        ", identifierType='" + identifierType + '\'' +
        ", loginStatus='" + loginStatus + '\'' +
        ", clientIp='" + clientIp + '\'' +
        ", riskScore=" + riskScore +
        ", createdAt=" + createdAt +
        '}';
  }
}