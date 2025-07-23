package work.anyway.packages.system.plugin.menu;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import work.anyway.annotations.MenuType;
import work.anyway.interfaces.system.MenuItemInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 菜单项信息实现类
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuItemInfoImpl implements MenuItemInfo {
  private String id;
  private String title;
  private String path;
  private String parentId;
  private String icon;
  private int order;

  @Builder.Default
  private Set<String> permissions = new HashSet<>();

  @Builder.Default
  private Set<String> anyPermissions = new HashSet<>();

  @Builder.Default
  private boolean visible = true;

  @Builder.Default
  private MenuType type = MenuType.PAGE;

  @Builder.Default
  private String target = "_self";

  @Builder.Default
  private List<MenuItemInfo> children = new ArrayList<>();

  private String pluginName;
  private String pluginVersion;

  @Override
  public boolean hasChildren() {
    return children != null && !children.isEmpty();
  }

  /**
   * 设置子菜单列表
   */
  public void setChildren(List<MenuItemInfo> children) {
    this.children = children;
  }

  /**
   * 添加子菜单
   */
  public void addChild(MenuItemInfo child) {
    if (children == null) {
      children = new ArrayList<>();
    }
    children.add(child);
  }
}