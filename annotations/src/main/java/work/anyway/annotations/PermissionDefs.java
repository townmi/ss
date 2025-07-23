package work.anyway.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限定义容器注解
 * 用于支持在同一个类上声明多个权限定义
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface PermissionDefs {
  /**
   * 权限定义数组
   */
  PermissionDef[] value();
}