package work.anyway.interfaces.data;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.HashMap;
import java.util.Map;

/**
 * 查询选项类
 * 支持分页、排序等高级查询功能
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryOptions {

  @Builder.Default
  private int page = 1;
  @Builder.Default
  private int pageSize = 20;
  private String sortBy;
  @Builder.Default
  private boolean ascending = true;
  @Builder.Default
  private Map<String, Object> filters = new HashMap<>();

  /**
   * 创建默认查询选项
   */
  public static QueryOptions create() {
    return new QueryOptions();
  }

  /**
   * 设置页码
   * 
   * @param page 页码（从1开始）
   * @return 当前对象，支持链式调用
   */
  public QueryOptions page(int page) {
    this.page = Math.max(1, page);
    return this;
  }

  /**
   * 设置每页大小
   * 
   * @param pageSize 每页记录数
   * @return 当前对象，支持链式调用
   */
  public QueryOptions pageSize(int pageSize) {
    this.pageSize = Math.max(1, Math.min(100, pageSize)); // 限制在1-100之间
    return this;
  }

  /**
   * 设置排序字段
   * 
   * @param sortBy 排序字段名
   * @return 当前对象，支持链式调用
   */
  public QueryOptions sortBy(String sortBy) {
    this.sortBy = sortBy;
    return this;
  }

  /**
   * 设置升序排序
   * 
   * @return 当前对象，支持链式调用
   */
  public QueryOptions ascending() {
    this.ascending = true;
    return this;
  }

  /**
   * 设置降序排序
   * 
   * @return 当前对象，支持链式调用
   */
  public QueryOptions descending() {
    this.ascending = false;
    return this;
  }

  /**
   * 添加过滤条件
   * 
   * @param key   字段名
   * @param value 字段值
   * @return 当前对象，支持链式调用
   */
  public QueryOptions filter(String key, Object value) {
    this.filters.put(key, value);
    return this;
  }

  /**
   * 添加多个过滤条件
   * 
   * @param filters 过滤条件Map
   * @return 当前对象，支持链式调用
   */
  public QueryOptions filters(Map<String, Object> filters) {
    this.filters.putAll(filters);
    return this;
  }

  /**
   * 计算偏移量
   * 
   * @return 查询偏移量
   */
  public int getOffset() {
    return (page - 1) * pageSize;
  }
}