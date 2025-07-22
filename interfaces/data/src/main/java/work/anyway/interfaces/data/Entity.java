package work.anyway.interfaces.data;

import lombok.Data;
import work.anyway.annotations.Column;
import java.util.Date;

/**
 * 实体基类
 * 所有需要持久化的实体都应该继承此类
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Data
public abstract class Entity {
  @Column("id")
  private String id;

  @Column("created_at")
  private Date createdAt;

  @Column("updated_at")
  private Date updatedAt;
}