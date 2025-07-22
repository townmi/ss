package work.anyway.interfaces.auth;

import lombok.*;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 登录日志实体
 * 记录用户的登录历史信息
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginLog {

  private String id; // 日志ID
  private String userId; // 用户ID（成功登录时有值）
  private String identifier; // 登录标识符
  private String identifierType; // 标识符类型
  private String loginStatus; // 登录状态
  private String failureReason; // 失败原因
  private String clientIp; // 客户端IP
  private String userAgent; // 用户代理
  @Builder.Default
  private String loginSource = "web"; // 登录来源
  private String sessionId; // 会话ID
  private String locationInfo; // 地理位置信息（JSON格式）
  private String deviceInfo; // 设备信息（JSON格式）
  @Builder.Default
  private Integer riskScore = 0; // 风险评分
  private Integer loginDuration; // 登录耗时（毫秒）
  @Builder.Default
  private Date createdAt = new Date(); // 创建时间

  public LoginLog(String identifier, String identifierType, String loginStatus, String clientIp) {
    this.identifier = identifier;
    this.identifierType = identifierType;
    this.loginStatus = loginStatus;
    this.clientIp = clientIp;
    this.loginSource = "web";
    this.riskScore = 0;
    this.createdAt = new Date();
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