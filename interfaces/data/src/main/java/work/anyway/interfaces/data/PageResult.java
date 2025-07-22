package work.anyway.interfaces.data;

import lombok.Getter;
import lombok.AllArgsConstructor;
import java.util.List;

/**
 * 分页查询结果类
 * 
 * @param <T> 数据类型
 * @author 作者名
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public class PageResult<T> {

  private final List<T> data;
  private final long total;
  private final int page;
  private final int pageSize;

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