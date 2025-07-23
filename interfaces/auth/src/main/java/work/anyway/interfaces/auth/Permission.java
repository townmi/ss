package work.anyway.interfaces.auth;

import lombok.*;
import work.anyway.interfaces.data.Entity;
import work.anyway.annotations.Table;
import work.anyway.annotations.Column;

/**
 * 权限实体
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("permissions")
public class Permission extends Entity {

  @Column("code")
  private String code;

  @Column("name")
  private String name;

  @Column("description")
  private String description;

  @Column("plugin_name")
  private String pluginName;

  @Column("is_active")
  @Builder.Default
  private Boolean isActive = true;
}