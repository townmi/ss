package work.anyway.annotations;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个类为插件
 * 插件会自动注册到系统中并在插件列表中显示
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface Plugin {
    /**
     * 插件名称
     */
    String name();
    
    /**
     * 插件版本
     */
    String version();
    
    /**
     * 插件描述
     */
    String description() default "";
    
    /**
     * 插件图标（emoji 或图标类名）
     */
    String icon() default "📦";
    
    /**
     * 插件主页路径
     */
    String mainPagePath() default "";
} 