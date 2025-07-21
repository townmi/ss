package work.anyway.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 映射 HTTP 请求到控制器方法
 * 可以用在类或方法上，类上的映射作为基础路径
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequestMapping {
    /**
     * URL 路径，支持多个路径映射到同一个方法
     */
    String[] value() default {};
    
    /**
     * 路径别名，与 value 相同
     */
    String[] path() default {};
    
    /**
     * HTTP 方法，默认支持所有方法
     * 可选值: "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"
     */
    String[] method() default {};
    
    /**
     * 响应内容类型
     */
    String[] produces() default {};
    
    /**
     * 请求内容类型
     */
    String[] consumes() default {};
} 