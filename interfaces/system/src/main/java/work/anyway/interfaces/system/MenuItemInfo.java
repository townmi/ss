package work.anyway.interfaces.system;

import work.anyway.annotations.MenuType;

import java.util.List;
import java.util.Set;

/**
 * 菜单项信息接口
 * 定义菜单项的数据结构
 * 
 * @author 作者名
 * @since 1.0.0
 */
public interface MenuItemInfo {
    /**
     * 获取菜单ID
     */
    String getId();
    
    /**
     * 获取菜单标题
     */
    String getTitle();
    
    /**
     * 获取菜单路径
     */
    String getPath();
    
    /**
     * 获取父菜单ID
     */
    String getParentId();
    
    /**
     * 获取菜单图标
     */
    String getIcon();
    
    /**
     * 获取排序值
     */
    int getOrder();
    
    /**
     * 获取需要的权限（AND 关系）
     */
    Set<String> getPermissions();
    
    /**
     * 获取需要的权限（OR 关系）
     */
    Set<String> getAnyPermissions();
    
    /**
     * 是否可见
     */
    boolean isVisible();
    
    /**
     * 获取菜单类型
     */
    MenuType getType();
    
    /**
     * 获取打开方式
     */
    String getTarget();
    
    /**
     * 获取子菜单列表
     */
    List<? extends MenuItemInfo> getChildren();
    
    /**
     * 获取插件名称
     */
    String getPluginName();
    
    /**
     * 获取插件版本
     */
    String getPluginVersion();
    
    /**
     * 是否有子菜单
     */
    boolean hasChildren();
} 