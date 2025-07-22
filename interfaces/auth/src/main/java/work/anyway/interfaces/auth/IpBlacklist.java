package work.anyway.interfaces.auth;

import lombok.*;
import work.anyway.annotations.*;
import work.anyway.interfaces.data.Entity;

import java.util.Date;

/**
 * IP黑名单实体
 * 记录被封禁的IP地址信息
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("ip_blacklist")
public class IpBlacklist extends Entity {

  @Column("ip_address")
  private String ipAddress;

  @Column("reason")
  private String reason;

  @Column("created_by")
  private String blockedBy;

  @Column("expires_at")
  private Date expiresAt;

  @Transient
  @Builder.Default
  private Boolean isPermanent = false;

  /**
   * 检查是否已过期
   */
  public boolean isExpired() {
    // 如果没有过期时间，表示永久封禁
    if (expiresAt == null) {
      return false;
    }
    return expiresAt.before(new Date());
  }

  /**
   * 检查是否为永久封禁
   */
  public boolean isPermanent() {
    return expiresAt == null;
  }
}