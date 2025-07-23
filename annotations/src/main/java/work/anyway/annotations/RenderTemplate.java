package work.anyway.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明页面使用的模板
 * 标记的方法将自动应用主题布局
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RenderTemplate {

  /**
   * 模板文件名
   * 相对于插件的 templates 目录
   */
  String value() default "";

  /**
   * 布局名称
   * 默认使用 base 布局
   */
  String layout() default "base";

  /**
   * 是否启用主题
   * 如果为 false，将直接渲染模板内容
   */
  boolean themed() default true;
}