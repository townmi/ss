package work.anyway.annotations;

/**
 * 插件信息类
 * 包含插件的元数据信息
 */
public class PluginInfo {
    private final String name;
    private final String version;
    private final String description;
    private final String icon;
    private final String mainPagePath;
    
    public PluginInfo(String name, String version, String description, String icon, String mainPagePath) {
        this.name = name;
        this.version = version;
        this.description = description;
        this.icon = icon;
        this.mainPagePath = mainPagePath;
    }
    
    // Getters
    public String getName() { 
        return name; 
    }
    
    public String getVersion() { 
        return version; 
    }
    
    public String getDescription() { 
        return description; 
    }
    
    public String getIcon() { 
        return icon; 
    }
    
    public String getMainPagePath() { 
        return mainPagePath; 
    }
} 