package work.anyway.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 菜单项容器注解
 * 用于支持在同一个类或方法上声明多个菜单项
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface MenuItems {
  /**
   * 菜单项数组
   */
  MenuItem[] value();
}