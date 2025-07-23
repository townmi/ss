package work.anyway.interfaces.auth;

import lombok.*;
import work.anyway.interfaces.data.Entity;
import work.anyway.annotations.Table;
import work.anyway.annotations.Column;

import java.util.Date;

/**
 * 用户角色关联实体
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("user_roles")
public class UserRole extends Entity {

  @Column("user_id")
  private String userId;

  @Column("role_id")
  private String roleId;

  @Column("assigned_at")
  @Builder.Default
  private Date assignedAt = new Date();

  @Column("assigned_by")
  private String assignedBy;

  @Column("expires_at")
  private Date expiresAt;

  @Column("reason")
  private String reason;

  /**
   * 检查角色是否已过期
   */
  public boolean isExpired() {
    return expiresAt != null && expiresAt.before(new Date());
  }

  /**
   * 获取唯一键，用于判断是否重复
   */
  public String getUniqueKey() {
    return userId + ":" + roleId;
  }
}