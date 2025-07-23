package work.anyway.interfaces.auth;

import lombok.*;
import work.anyway.interfaces.data.Entity;
import work.anyway.annotations.Table;
import work.anyway.annotations.Column;

/**
 * 角色实体
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("roles")
public class Role extends Entity {

  @Column("code")
  private String code;

  @Column("name")
  private String name;

  @Column("description")
  private String description;

  @Column("is_system")
  @Builder.Default
  private Boolean isSystem = false;

  @Column("is_active")
  @Builder.Default
  private Boolean isActive = true;
}