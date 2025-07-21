package work.anyway.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 映射 HTTP POST 请求到方法
 * 这是一个标记注解，实际的 HTTP 方法将在处理器中判断
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PostMapping {
    /**
     * URL 路径
     */
    String[] value() default {};
    
    /**
     * 路径别名
     */
    String[] path() default {};
    
    /**
     * 响应内容类型
     */
    String[] produces() default {};
    
    /**
     * 请求内容类型
     */
    String[] consumes() default {};
} 