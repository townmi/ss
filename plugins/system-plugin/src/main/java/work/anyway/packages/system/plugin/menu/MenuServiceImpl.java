package work.anyway.packages.system.plugin.menu;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import work.anyway.interfaces.auth.PermissionService;
import work.anyway.interfaces.system.MenuItemInfo;
import work.anyway.interfaces.system.MenuService;
import work.anyway.interfaces.system.MetadataScanner;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 菜单服务实现
 * 负责根据用户权限过滤和构建菜单树
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Service
public class MenuServiceImpl implements MenuService {

  private static final Logger LOG = LoggerFactory.getLogger(MenuServiceImpl.class);

  @Autowired
  private MetadataScanner metadataScanner;

  @Autowired
  private PermissionService permissionService;

  @Override
  public List<MenuItemInfo> getUserMenuTree(String userId) {
    LOG.debug("Getting menu tree for user: {}", userId);

    // 获取用户权限
    Set<String> userPermissions = permissionService.getUserPermissions(userId);
    LOG.debug("User {} has permissions: {}", userId, userPermissions);

    // 获取所有菜单并过滤
    List<MenuItemInfo> allMenus = metadataScanner.getAllMenuItems();
    List<MenuItemInfo> visibleMenus = allMenus.stream()
        .filter(menu -> isMenuVisible(menu, userPermissions))
        .map(this::cloneMenuItem) // 深拷贝避免修改原始数据
        .collect(Collectors.toList());

    LOG.debug("Filtered {} menus to {} visible menus for user {}",
        allMenus.size(), visibleMenus.size(), userId);

    // 构建树结构
    return buildMenuTree(visibleMenus);
  }

  @Override
  public List<MenuItemInfo> getUserMenuTree(String userId, boolean useCache) {
    // TODO: 实现缓存逻辑
    return getUserMenuTree(userId);
  }

  @Override
  public void clearUserMenuCache(String userId) {
    // TODO: 实现缓存清除逻辑
    LOG.info("Cleared menu cache for user: {}", userId);
  }

  @Override
  public void clearAllMenuCache() {
    // TODO: 实现缓存清除逻辑
    LOG.info("Cleared all menu cache");
  }

  @Override
  public void refreshMenuMetadata() {
    metadataScanner.buildMenuTree();
    clearAllMenuCache();
    LOG.info("Menu metadata refreshed");
  }

  /**
   * 判断菜单是否对用户可见
   */
  private boolean isMenuVisible(MenuItemInfo menu, Set<String> userPermissions) {
    // 不可见的菜单直接过滤
    if (!menu.isVisible()) {
      return false;
    }

    // 检查 AND 权限（所有权限都必须满足）
    if (!menu.getPermissions().isEmpty()) {
      boolean hasAllPermissions = userPermissions.containsAll(menu.getPermissions());
      if (!hasAllPermissions) {
        LOG.trace("Menu {} requires permissions {} but user only has {}",
            menu.getId(), menu.getPermissions(), userPermissions);
        return false;
      }
    }

    // 检查 OR 权限（至少满足一个）
    if (!menu.getAnyPermissions().isEmpty()) {
      boolean hasAnyPermission = menu.getAnyPermissions().stream()
          .anyMatch(userPermissions::contains);
      if (!hasAnyPermission) {
        LOG.trace("Menu {} requires any of permissions {} but user has none",
            menu.getId(), menu.getAnyPermissions());
        return false;
      }
    }

    return true;
  }

  /**
   * 深拷贝菜单项
   */
  private MenuItemInfo cloneMenuItem(MenuItemInfo original) {
    return MenuItemInfoImpl.builder()
        .id(original.getId())
        .title(original.getTitle())
        .path(original.getPath())
        .parentId(original.getParentId())
        .icon(original.getIcon())
        .order(original.getOrder())
        .permissions(new HashSet<>(original.getPermissions()))
        .anyPermissions(new HashSet<>(original.getAnyPermissions()))
        .visible(original.isVisible())
        .type(original.getType())
        .target(original.getTarget())
        .pluginName(original.getPluginName())
        .pluginVersion(original.getPluginVersion())
        .children(new ArrayList<>()) // 子菜单将在构建树时重新添加
        .build();
  }

  /**
   * 构建菜单树
   */
  private List<MenuItemInfo> buildMenuTree(List<MenuItemInfo> menus) {
    // 创建ID到菜单的映射
    Map<String, MenuItemInfo> menuMap = menus.stream()
        .collect(Collectors.toMap(MenuItemInfo::getId, m -> m));

    // 构建父子关系
    List<MenuItemInfo> rootMenus = new ArrayList<>();

    for (MenuItemInfo menu : menus) {
      if (menu.getParentId() == null || menu.getParentId().isEmpty()) {
        // 根菜单
        rootMenus.add(menu);
      } else {
        // 查找父菜单
        MenuItemInfo parent = menuMap.get(menu.getParentId());
        if (parent != null) {
          ((MenuItemInfoImpl) parent).addChild(menu);
        } else {
          // 父菜单不存在或不可见，将此菜单作为根菜单
          LOG.warn("Parent menu {} not found for menu {}, treating as root menu",
              menu.getParentId(), menu.getId());
          rootMenus.add(menu);
        }
      }
    }

    // 排序
    sortMenuTree(rootMenus);

    return rootMenus;
  }

  /**
   * 递归排序菜单树
   */
  private void sortMenuTree(List<MenuItemInfo> menus) {
    menus.sort(Comparator.comparingInt(MenuItemInfo::getOrder)
        .thenComparing(MenuItemInfo::getTitle));

    // 递归排序子菜单
    for (MenuItemInfo menu : menus) {
      if (menu.hasChildren()) {
        sortMenuTree(new ArrayList<>(menu.getChildren()));
      }
    }
  }
}