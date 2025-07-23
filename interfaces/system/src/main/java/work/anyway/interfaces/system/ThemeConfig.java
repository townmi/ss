package work.anyway.interfaces.system;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.List;

/**
 * 主题配置类
 * 对应 theme.json 文件的结构
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThemeConfig {
  
  /**
   * 主题名称
   */
  private String name;
  
  /**
   * 主题版本
   */
  private String version;
  
  /**
   * 主题描述
   */
  private String description;
  
  /**
   * 主题作者
   */
  private String author;
  
  /**
   * 父主题（用于继承）
   */
  private String parent;
  
  /**
   * CSS 变量定义
   */
  private Map<String, String> variables;
  
  /**
   * 布局定义
   */
  private Map<String, LayoutConfig> layouts;
  
  /**
   * 必需的组件列表
   */
  private List<String> requiredComponents;
  
  /**
   * 布局配置
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class LayoutConfig {
    private String file;
    private String description;
  }
} 