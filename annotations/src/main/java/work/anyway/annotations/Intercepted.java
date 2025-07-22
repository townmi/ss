package work.anyway.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要拦截的方法或类
 * 可以指定具体的拦截器名称，如果不指定则使用所有可用的拦截器
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface Intercepted {
  /**
   * 拦截器名称数组，如果为空则使用所有拦截器
   */
  String[] value() default {};

  /**
   * 拦截器执行顺序，数字越小优先级越高
   */
  int order() default 0;
}