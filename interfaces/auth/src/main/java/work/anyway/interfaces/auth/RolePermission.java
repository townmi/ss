package work.anyway.interfaces.auth;

import lombok.*;
import work.anyway.interfaces.data.Entity;
import work.anyway.annotations.Table;
import work.anyway.annotations.Column;

import java.util.Date;

/**
 * 角色权限关联实体
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("role_permissions")
public class RolePermission extends Entity {

  @Column("role_id")
  private String roleId;

  @Column("permission_id")
  private String permissionId;

  @Column("granted_at")
  @Builder.Default
  private Date grantedAt = new Date();

  @Column("granted_by")
  private String grantedBy;

  /**
   * 获取唯一键，用于判断是否重复
   */
  public String getUniqueKey() {
    return roleId + ":" + permissionId;
  }
}