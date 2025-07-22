package work.anyway.annotations;

import lombok.Getter;
import lombok.AllArgsConstructor;

/**
 * 插件信息类
 * 包含插件的元数据信息
 */
@Getter
@AllArgsConstructor
public class PluginInfo {
    private final String name;
    private final String version;
    private final String description;
    private final String icon;
    private final String mainPagePath;
}