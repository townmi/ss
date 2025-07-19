package work.anyway.interfaces.data;

import java.util.List;

/**
 * 分页查询结果类
 * 
 * @param <T> 数据类型
 * @author 作者名
 * @since 1.0.0
 */
public class PageResult<T> {
  
  private final List<T> data;
  private final long total;
  private final int page;
  private final int pageSize;
  
  /**
   * 创建分页结果
   * 
   * @param data 当前页数据
   * @param total 总记录数
   * @param page 当前页码
   * @param pageSize 每页大小
   */
  public PageResult(List<T> data, long total, int page, int pageSize) {
    this.data = data;
    this.total = total;
    this.page = page;
    this.pageSize = pageSize;
  }
  
  /**
   * 获取当前页数据
   */
  public List<T> getData() {
    return data;
  }
  
  /**
   * 获取总记录数
   */
  public long getTotal() {
    return total;
  }
  
  /**
   * 获取当前页码
   */
  public int getPage() {
    return page;
  }
  
  /**
   * 获取每页大小
   */
  public int getPageSize() {
    return pageSize;
  }
  
  /**
   * 获取总页数
   */
  public int getTotalPages() {
    return pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;
  }
  
  /**
   * 是否有下一页
   */
  public boolean hasNext() {
    return page < getTotalPages();
  }
  
  /**
   * 是否有上一页
   */
  public boolean hasPrevious() {
    return page > 1;
  }
  
  /**
   * 是否为第一页
   */
  public boolean isFirst() {
    return page == 1;
  }
  
  /**
   * 是否为最后一页
   */
  public boolean isLast() {
    return page == getTotalPages();
  }
} 