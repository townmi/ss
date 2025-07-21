package work.anyway.annotations;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个类为控制器，用于处理 HTTP 请求
 * 被标记的类会自动注册为 Spring Bean 并扫描其中的路由映射
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface Controller {
    /**
     * Bean 名称，默认为类名首字母小写
     */
    String value() default "";
} 