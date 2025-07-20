package work.anyway.interfaces.data;

import java.util.Date;

/**
 * 实体基类
 * 所有需要持久化的实体都应该继承此类
 * 
 * @author 作者名
 * @since 1.0.0
 */
public abstract class Entity {
  private String id;
  private Date createdAt;
  private Date updatedAt;

  /**
   * 获取实体ID
   * 
   * @return 实体ID
   */
  public String getId() {
    return id;
  }

  /**
   * 设置实体ID
   * 
   * @param id 实体ID
   */
  public void setId(String id) {
    this.id = id;
  }

  /**
   * 获取创建时间
   * 
   * @return 创建时间
   */
  public Date getCreatedAt() {
    return createdAt;
  }

  /**
   * 设置创建时间
   * 
   * @param createdAt 创建时间
   */
  public void setCreatedAt(Date createdAt) {
    this.createdAt = createdAt;
  }

  /**
   * 获取更新时间
   * 
   * @return 更新时间
   */
  public Date getUpdatedAt() {
    return updatedAt;
  }

  /**
   * 设置更新时间
   * 
   * @param updatedAt 更新时间
   */
  public void setUpdatedAt(Date updatedAt) {
    this.updatedAt = updatedAt;
  }
}