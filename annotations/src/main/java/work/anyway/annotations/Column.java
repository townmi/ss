package work.anyway.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记实体字段对应的数据库列
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Column {
  /**
   * 列名，如果不指定则使用字段名的 snake_case 形式
   */
  String value() default "";

  /**
   * 是否为主键
   */
  boolean primaryKey() default false;

  /**
   * 是否可为空
   */
  boolean nullable() default true;

  /**
   * 是否唯一
   */
  boolean unique() default false;

  /**
   * 列的长度（主要用于字符串类型）
   */
  int length() default 255;
}