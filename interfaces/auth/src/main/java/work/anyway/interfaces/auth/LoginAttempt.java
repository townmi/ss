package work.anyway.interfaces.auth;

import lombok.*;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 登录尝试实体
 * 记录用户登录失败的尝试信息
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginAttempt {

  private String id; // 实体ID
  private String identifier; // 登录标识符
  private String identifierType; // 标识符类型
  private String clientIp; // 客户端IP
  @Builder.Default
  private Integer attemptCount = 1; // 尝试次数
  private LocalDateTime firstAttemptAt; // 首次尝试时间
  private LocalDateTime lastAttemptAt; // 最后尝试时间
  private LocalDateTime lockedUntil; // 锁定到什么时间
  @Builder.Default
  private String lockLevel = "none"; // 锁定级别
  private String lockReason; // 锁定原因
  private Date createdAt; // 创建时间
  private Date updatedAt; // 更新时间

  public LoginAttempt(String identifier, String identifierType, String clientIp) {
    this.identifier = identifier;
    this.identifierType = identifierType;
    this.clientIp = clientIp;
    this.attemptCount = 1;
    this.firstAttemptAt = LocalDateTime.now();
    this.lastAttemptAt = LocalDateTime.now();
    this.lockLevel = "none";
  }

  /**
   * 增加尝试次数
   */
  public void incrementAttempts() {
    this.attemptCount = (this.attemptCount == null ? 0 : this.attemptCount) + 1;
    this.lastAttemptAt = LocalDateTime.now();
  }

  /**
   * 检查是否被锁定
   */
  public boolean isLocked() {
    return lockedUntil != null && LocalDateTime.now().isBefore(lockedUntil);
  }

  /**
   * 检查是否需要锁定
   */
  public boolean shouldLock(int maxAttempts) {
    return attemptCount != null && attemptCount >= maxAttempts;
  }

  /**
   * 重置尝试记录
   */
  public void reset() {
    this.attemptCount = 0;
    this.firstAttemptAt = null;
    this.lastAttemptAt = null;
    this.lockedUntil = null;
    this.lockLevel = "none";
    this.lockReason = null;
  }

  @Override
  public String toString() {
    return "LoginAttempt{" +
        "id='" + getId() + '\'' +
        ", identifier='" + identifier + '\'' +
        ", identifierType='" + identifierType + '\'' +
        ", clientIp='" + clientIp + '\'' +
        ", attemptCount=" + attemptCount +
        ", firstAttemptAt=" + firstAttemptAt +
        ", lastAttemptAt=" + lastAttemptAt +
        ", lockedUntil=" + lockedUntil +
        ", lockLevel='" + lockLevel + '\'' +
        '}';
  }
}