package work.anyway.interfaces.auth;

import lombok.*;
import work.anyway.interfaces.data.Entity;
import work.anyway.annotations.Table;
import work.anyway.annotations.Column;

import java.util.Date;

/**
 * 用户权限实体（直接授予用户的权限）
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("user_permissions")
public class UserPermission extends Entity {

  @Column("user_id")
  private String userId;

  @Column("permission_id")
  private String permissionId;

  @Column("granted_at")
  @Builder.Default
  private Date grantedAt = new Date();

  @Column("granted_by")
  private String grantedBy;

  @Column("expires_at")
  private Date expiresAt;

  @Column("reason")
  private String reason;

  /**
   * 检查权限是否已过期
   */
  public boolean isExpired() {
    return expiresAt != null && expiresAt.before(new Date());
  }

  /**
   * 获取唯一键，用于判断是否重复
   */
  public String getUniqueKey() {
    return userId + ":" + permissionId;
  }
}