package work.anyway.interfaces.data;

import lombok.Getter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 类型安全的查询条件构建器
 * 使用流式API构建查询条件
 * 
 * @param <T> 实体类型
 * @author 作者名
 * @since 1.0.0
 */
@Getter
public class QueryCriteria<T> {
  private final Map<String, Object> conditions = new HashMap<>();
  private Predicate<T> customFilter;
  private String orderBy;
  private boolean ascending = true;

  /**
   * 创建一个新的查询条件构建器
   * 
   * @param <T> 实体类型
   * @return 查询条件构建器实例
   */
  public static <T> QueryCriteria<T> create() {
    return new QueryCriteria<>();
  }

  /**
   * 添加相等条件
   * 
   * @param field 字段名
   * @param value 字段值
   * @return 当前构建器实例
   */
  public QueryCriteria<T> eq(String field, Object value) {
    conditions.put(field, value);
    return this;
  }

  /**
   * 添加模糊匹配条件
   * 
   * @param field   字段名
   * @param pattern 匹配模式（使用 % 作为通配符）
   * @return 当前构建器实例
   */
  public QueryCriteria<T> like(String field, String pattern) {
    conditions.put(field + "__like", pattern);
    return this;
  }

  /**
   * 添加大于条件
   * 
   * @param field 字段名
   * @param value 比较值
   * @return 当前构建器实例
   */
  public QueryCriteria<T> gt(String field, Object value) {
    conditions.put(field + "__gt", value);
    return this;
  }

  /**
   * 添加大于等于条件
   * 
   * @param field 字段名
   * @param value 比较值
   * @return 当前构建器实例
   */
  public QueryCriteria<T> gte(String field, Object value) {
    conditions.put(field + "__gte", value);
    return this;
  }

  /**
   * 添加小于条件
   * 
   * @param field 字段名
   * @param value 比较值
   * @return 当前构建器实例
   */
  public QueryCriteria<T> lt(String field, Object value) {
    conditions.put(field + "__lt", value);
    return this;
  }

  /**
   * 添加小于等于条件
   * 
   * @param field 字段名
   * @param value 比较值
   * @return 当前构建器实例
   */
  public QueryCriteria<T> lte(String field, Object value) {
    conditions.put(field + "__lte", value);
    return this;
  }

  /**
   * 添加包含条件（IN 查询）
   * 
   * @param field  字段名
   * @param values 值列表
   * @return 当前构建器实例
   */
  public QueryCriteria<T> in(String field, Object... values) {
    conditions.put(field + "__in", values);
    return this;
  }

  /**
   * 添加不等于条件
   * 
   * @param field 字段名
   * @param value 字段值
   * @return 当前构建器实例
   */
  public QueryCriteria<T> ne(String field, Object value) {
    conditions.put(field + "__ne", value);
    return this;
  }

  /**
   * 添加区间条件
   * 
   * @param field 字段名
   * @param min   最小值
   * @param max   最大值
   * @return 当前构建器实例
   */
  public QueryCriteria<T> between(String field, Object min, Object max) {
    conditions.put(field + "__gte", min);
    conditions.put(field + "__lte", max);
    return this;
  }

  /**
   * 添加自定义过滤器
   * 用于在内存中进行额外的过滤
   * 
   * @param filter 过滤函数
   * @return 当前构建器实例
   */
  public QueryCriteria<T> custom(Predicate<T> filter) {
    this.customFilter = filter;
    return this;
  }

  /**
   * 设置排序字段
   * 
   * @param field     排序字段
   * @param ascending 是否升序
   * @return 当前构建器实例
   */
  public QueryCriteria<T> orderBy(String field, boolean ascending) {
    this.orderBy = field;
    this.ascending = ascending;
    return this;
  }

}