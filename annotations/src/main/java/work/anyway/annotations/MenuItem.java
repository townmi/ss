package work.anyway.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 菜单项注解
 * 用于声明插件或控制器方法对应的菜单项
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(MenuItems.class)
public @interface MenuItem {
  /**
   * 菜单ID，默认自动生成
   * 格式：类级别为 ClassName，方法级别为 ClassName.methodName
   */
  String id() default "";

  /**
   * 菜单标题（必填）
   * 支持 i18n key，如：menu.user.title
   */
  String title();

  /**
   * 菜单路径
   * 默认使用 @RequestMapping 的路径
   */
  String path() default "";

  /**
   * 父菜单ID
   * 空表示一级菜单
   */
  String parentId() default "";

  /**
   * 菜单图标
   * 支持 emoji 或图标类名
   */
  String icon() default "";

  /**
   * 排序值
   * 数值越小越靠前
   */
  int order() default 100;

  /**
   * 需要的权限（AND 关系）
   * 用户必须拥有所有列出的权限才能看到此菜单
   */
  String[] permissions() default {};

  /**
   * 需要的权限（OR 关系）
   * 用户只需拥有其中一个权限即可看到此菜单
   */
  String[] anyPermissions() default {};

  /**
   * 是否可见
   * 可用于临时隐藏菜单
   */
  boolean visible() default true;

  /**
   * 菜单类型
   */
  MenuType type() default MenuType.PAGE;

  /**
   * 打开方式
   * _self: 当前窗口（默认）
   * _blank: 新窗口
   */
  String target() default "_self";
}