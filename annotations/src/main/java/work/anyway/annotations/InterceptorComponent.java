package work.anyway.annotations;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个类为拦截器组件
 * 拦截器会自动注册到系统中
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface InterceptorComponent {
  /**
   * 拦截器名称，用于在@Intercepted注解中引用
   */
  String name();

  /**
   * 拦截器描述
   */
  String description() default "";

  /**
   * 执行顺序，数字越小优先级越高
   */
  int order() default 0;
}