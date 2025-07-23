package work.anyway.packages.auth.plugin.permission;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import work.anyway.interfaces.auth.PermissionInfo;

import java.util.HashSet;
import java.util.Set;

/**
 * 权限信息实现类
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionInfoImpl implements PermissionInfo {
  private String code;
  private String name;
  private String description;

  @Builder.Default
  private Set<String> defaultRoles = new HashSet<>();

  private String pluginName;
  private String pluginVersion;
}