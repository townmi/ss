package work.anyway.interfaces.system;

import java.util.List;

/**
 * 菜单服务接口
 * 提供菜单管理相关功能
 * 
 * @author 作者名
 * @since 1.0.0
 */
public interface MenuService {
    /**
     * 获取用户可见的菜单树
     * 
     * @param userId 用户ID
     * @return 过滤后的菜单树
     */
    List<MenuItemInfo> getUserMenuTree(String userId);
    
    /**
     * 获取用户可见的菜单树（带缓存控制）
     * 
     * @param userId 用户ID
     * @param useCache 是否使用缓存
     * @return 过滤后的菜单树
     */
    List<MenuItemInfo> getUserMenuTree(String userId, boolean useCache);
    
    /**
     * 清除用户菜单缓存
     * 
     * @param userId 用户ID
     */
    void clearUserMenuCache(String userId);
    
    /**
     * 清除所有菜单缓存
     */
    void clearAllMenuCache();
    
    /**
     * 刷新菜单元数据
     */
    void refreshMenuMetadata();
} 