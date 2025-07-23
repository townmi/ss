package work.anyway.packages.system.plugin.menu;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import work.anyway.annotations.MenuType;
import work.anyway.annotations.ScanDataProvider;
import work.anyway.interfaces.system.MenuItemInfo;
import work.anyway.interfaces.system.MetadataScanner;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 系统元数据扫描器实现
 * 从 Host 的注解扫描器获取原始数据，转换为业务接口
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Component
@Primary
public class SystemMetadataScanner implements MetadataScanner {

  @Autowired
  private ScanDataProvider scanDataProvider;

  /**
   * 菜单映射（用于快速查找）
   */
  private Map<String, MenuItemInfo> menuMap;

  /**
   * 菜单树
   */
  private List<MenuItemInfo> menuTree;

  /**
   * 初始化标志
   */
  private boolean initialized = false;

  /**
   * 初始化菜单数据
   */
  private synchronized void initialize() {
    if (initialized) {
      return;
    }

    // 转换原始数据为 MenuItemInfo
    menuMap = new HashMap<>();
    List<Map<String, Object>> rawMenus = scanDataProvider.getScannedMenuItems();

    for (Map<String, Object> rawMenu : rawMenus) {
      MenuItemInfo menuItem = convertToMenuItemInfo(rawMenu);
      menuMap.put(menuItem.getId(), menuItem);
    }

    // 构建菜单树
    buildMenuTree();
    initialized = true;
  }

  /**
   * 将原始数据转换为 MenuItemInfo
   */
  private MenuItemInfo convertToMenuItemInfo(Map<String, Object> rawData) {
    return MenuItemInfoImpl.builder()
        .id((String) rawData.get("id"))
        .title((String) rawData.get("title"))
        .path((String) rawData.get("path"))
        .parentId((String) rawData.get("parentId"))
        .icon((String) rawData.get("icon"))
        .order((Integer) rawData.get("order"))
        .permissions(new HashSet<>((List<String>) rawData.get("permissions")))
        .anyPermissions(new HashSet<>((List<String>) rawData.get("anyPermissions")))
        .visible((Boolean) rawData.get("visible"))
        .type((MenuType) rawData.get("type"))
        .target((String) rawData.get("target"))
        .pluginName((String) rawData.get("pluginName"))
        .pluginVersion((String) rawData.get("pluginVersion"))
        .build();
  }

  /**
   * 构建菜单树
   */
  @Override
  public void buildMenuTree() {
    // 构建父子关系
    Map<String, List<MenuItemInfo>> childrenMap = new HashMap<>();
    List<MenuItemInfo> rootMenus = new ArrayList<>();

    for (MenuItemInfo menu : menuMap.values()) {
      if (menu.getParentId() == null || menu.getParentId().isEmpty()) {
        // 根菜单
        rootMenus.add(menu);
      } else {
        // 子菜单
        childrenMap.computeIfAbsent(menu.getParentId(), k -> new ArrayList<>()).add(menu);
      }
    }

    // 递归构建树
    menuTree = new ArrayList<>();
    for (MenuItemInfo root : rootMenus) {
      buildSubTree(root, childrenMap);
      menuTree.add(root);
    }

    // 排序
    sortMenuTree(menuTree);
  }

  /**
   * 递归构建子树
   */
  private void buildSubTree(MenuItemInfo parent, Map<String, List<MenuItemInfo>> childrenMap) {
    List<MenuItemInfo> children = childrenMap.get(parent.getId());
    if (children != null && parent instanceof MenuItemInfoImpl) {
      // 创建新的子节点列表
      List<MenuItemInfo> newChildren = new ArrayList<>();
      for (MenuItemInfo child : children) {
        newChildren.add(child);
        buildSubTree(child, childrenMap);
      }
      // 设置子节点
      ((MenuItemInfoImpl) parent).setChildren(newChildren);
      // 排序子菜单
      sortMenuTree(newChildren);
    }
  }

  /**
   * 排序菜单
   */
  private void sortMenuTree(List<MenuItemInfo> menus) {
    menus.sort(Comparator.comparingInt(MenuItemInfo::getOrder)
        .thenComparing(MenuItemInfo::getTitle));
  }

  @Override
  public List<MenuItemInfo> getAllMenuItems() {
    if (!initialized) {
      initialize();
    }
    return new ArrayList<>(menuMap.values());
  }

  @Override
  public List<MenuItemInfo> getMenuTree() {
    if (!initialized) {
      initialize();
    }
    return new ArrayList<>(menuTree);
  }

  @Override
  public MenuItemInfo getMenuItem(String id) {
    if (!initialized) {
      initialize();
    }
    return menuMap.get(id);
  }
}