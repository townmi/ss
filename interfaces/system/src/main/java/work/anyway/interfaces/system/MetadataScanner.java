package work.anyway.interfaces.system;

import java.util.List;

/**
 * 元数据扫描器接口
 * 提供菜单和权限元数据的访问
 * 
 * @author 作者名
 * @since 1.0.0
 */
public interface MetadataScanner {
    /**
     * 获取所有菜单项（平面结构）
     */
    List<MenuItemInfo> getAllMenuItems();
    
    /**
     * 获取菜单树
     */
    List<MenuItemInfo> getMenuTree();
    
    /**
     * 根据ID获取菜单
     */
    MenuItemInfo getMenuItem(String id);
    
    /**
     * 构建菜单树
     */
    void buildMenuTree();
} 