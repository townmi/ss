package work.anyway.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限要求注解
 * 用于标记方法需要特定权限才能访问
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
  /**
   * 需要的权限（AND 关系）
   * 用户必须拥有所有列出的权限才能访问
   */
  String[] value();

  /**
   * 需要的权限（OR 关系）
   * 用户只需拥有其中一个权限即可访问
   */
  String[] any() default {};

  /**
   * 无权限时的提示信息
   */
  String message() default "Permission denied";
}