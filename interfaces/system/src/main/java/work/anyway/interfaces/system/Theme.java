package work.anyway.interfaces.system;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;
import java.util.Map;
import java.util.List;

/**
 * 主题实体类
 * 表示一个完整的主题配置和资源
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Theme {
  
  /**
   * 主题名称（目录名）
   */
  private String name;
  
  /**
   * 主题显示名称
   */
  private String displayName;
  
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
   * 主题基础目录
   */
  private File baseDirectory;
  
  /**
   * 布局映射
   */
  private Map<String, LayoutInfo> layouts;
  
  /**
   * 组件映射
   */
  private Map<String, String> components;
  
  /**
   * 静态资源路径
   */
  private String staticPath;
  
  /**
   * 主题变量
   */
  private Map<String, String> variables;
  
  /**
   * 父主题名称（用于继承）
   */
  private String parent;
  
  /**
   * 布局信息
   */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class LayoutInfo {
    private String file;
    private String description;
    private transient Object compiledTemplate; // 编译后的模板缓存
  }
} 