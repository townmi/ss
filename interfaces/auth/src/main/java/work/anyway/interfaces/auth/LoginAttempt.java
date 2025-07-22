package work.anyway.interfaces.auth;

import lombok.*;
import work.anyway.annotations.*;
import work.anyway.interfaces.data.Entity;
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
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("login_attempts")
public class LoginAttempt extends Entity {

  @Column("identifier")
  private String identifier; // 登录标识符

  @Column("identifier_type")
  private String identifierType; // 标识符类型

  @Column("client_ip")
  private String clientIp; // 客户端IP

  @Column("attempt_count")
  @Builder.Default
  private Integer attemptCount = 1; // 尝试次数

  @Column("first_attempt_at")
  private LocalDateTime firstAttemptAt; // 首次尝试时间

  @Column("last_attempt_at")
  private LocalDateTime lastAttemptAt; // 最后尝试时间

  @Column("locked_until")
  private LocalDateTime lockedUntil; // 锁定到什么时间

  @Column("lock_level")
  @Builder.Default
  private String lockLevel = "none"; // 锁定级别

  @Column("lock_reason")
  private String lockReason; // 锁定原因

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

}