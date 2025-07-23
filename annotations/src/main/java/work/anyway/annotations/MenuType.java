package work.anyway.annotations;

/**
 * 菜单类型枚举
 * 
 * @author 作者名
 * @since 1.0.0
 */
public enum MenuType {
  /**
   * 页面菜单（默认）
   * 点击后在当前系统内打开页面
   */
  PAGE,

  /**
   * 外部链接
   * 点击后打开外部 URL
   */
  EXTERNAL,

  /**
   * 分隔线
   * 用于在菜单中添加视觉分隔
   */
  DIVIDER,

  /**
   * 分组
   * 仅用于组织菜单结构，本身不可点击
   */
  GROUP
}