package work.anyway.annotations;

import java.util.List;

/**
 * 插件注册表接口
 * 用于管理和查询已加载的插件
 */
public interface PluginRegistry {
    
    /**
     * 获取所有已注册的插件信息
     */
    List<PluginInfo> getAllPlugins();
    
    /**
     * 根据名称查找插件
     */
    PluginInfo findPluginByName(String name);
} 