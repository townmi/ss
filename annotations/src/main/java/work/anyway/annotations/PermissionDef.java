package work.anyway.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限定义注解
 * 用于在插件类上声明权限定义
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(PermissionDefs.class)
public @interface PermissionDef {
  /**
   * 权限码（必填）
   * 建议格式：module.action，如 user.create
   */
  String code();

  /**
   * 权限名称（必填）
   * 用于显示的友好名称
   */
  String name();

  /**
   * 权限描述
   * 详细说明此权限的作用
   */
  String description() default "";

  /**
   * 默认分配给哪些角色
   * 系统初始化时会自动为这些角色分配此权限
   */
  String[] defaultRoles() default {};
}