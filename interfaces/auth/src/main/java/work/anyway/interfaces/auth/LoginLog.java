package work.anyway.interfaces.auth;

import lombok.*;
import work.anyway.annotations.*;
import work.anyway.interfaces.data.Entity;
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
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("login_logs")
public class LoginLog extends Entity {

  @Column("user_id")
  private String userId; // 用户ID（成功登录时有值）

  @Column("identifier")
  private String identifier; // 登录标识符

  @Column("identifier_type")
  private String identifierType; // 标识符类型

  @Column("login_status")
  private String loginStatus; // 登录状态

  @Column("failure_reason")
  private String failureReason; // 失败原因

  @Column("client_ip")
  private String clientIp; // 客户端IP

  @Column("user_agent")
  private String userAgent; // 用户代理

  @Column("login_source")
  @Builder.Default
  private String loginSource = "web"; // 登录来源

  @Column("session_id")
  private String sessionId; // 会话ID

  @Column("location_info")
  private String locationInfo; // 地理位置信息（JSON格式）

  @Column("device_info")
  private String deviceInfo; // 设备信息（JSON格式）

  @Column("risk_score")
  @Builder.Default
  private Integer riskScore = 0; // 风险评分

  @Column("login_duration")
  private Integer loginDuration; // 登录耗时（毫秒）

  public LoginLog(String identifier, String identifierType, String loginStatus, String clientIp) {
    this.identifier = identifier;
    this.identifierType = identifierType;
    this.loginStatus = loginStatus;
    this.clientIp = clientIp;
    this.loginSource = "web";
    this.riskScore = 0;
    this.setCreatedAt(new Date());
  }
}