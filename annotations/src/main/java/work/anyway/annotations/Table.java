package work.anyway.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记实体类对应的数据库表
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Table {
  /**
   * 表名
   */
  String value();

  /**
   * 模式名（可选）
   */
  String schema() default "";

  /**
   * 数据源（可选）
   */
  String dataSource() default "";
}