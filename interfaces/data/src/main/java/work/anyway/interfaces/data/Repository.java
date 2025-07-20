package work.anyway.interfaces.data;

import java.util.List;
import java.util.Optional;

/**
 * 通用仓库接口
 * 提供类型安全的数据访问
 * 
 * @param <T> 实体类型，必须继承自 Entity
 * @author 作者名
 * @since 1.0.0
 */
public interface Repository<T extends Entity> {

  /**
   * 保存实体
   * 如果实体没有ID，将自动生成；如果已有ID，则更新
   * 
   * @param entity 要保存的实体
   * @return 保存后的实体（包含生成的ID和时间戳）
   */
  T save(T entity);

  /**
   * 根据ID查找实体
   * 
   * @param id 实体ID
   * @return 查找到的实体，如果不存在返回空
   */
  Optional<T> findById(String id);

  /**
   * 查找所有实体
   * 
   * @return 所有实体列表
   */
  List<T> findAll();

  /**
   * 根据条件查询实体
   * 
   * @param criteria 查询条件
   * @return 符合条件的实体列表
   */
  List<T> findBy(QueryCriteria<T> criteria);

  /**
   * 更新实体
   * 
   * @param entity 要更新的实体
   * @return 更新成功返回 true，否则返回 false
   */
  boolean update(T entity);

  /**
   * 删除实体
   * 
   * @param id 实体ID
   * @return 删除成功返回 true，否则返回 false
   */
  boolean delete(String id);

  /**
   * 批量保存实体
   * 
   * @param entities 要保存的实体列表
   * @return 成功保存的数量
   */
  int batchSave(List<T> entities);

  /**
   * 批量删除实体
   * 
   * @param ids 要删除的实体ID列表
   * @return 成功删除的数量
   */
  int batchDelete(List<String> ids);

  /**
   * 分页查询
   * 
   * @param options 查询选项（包含分页、排序、过滤条件等）
   * @return 分页查询结果
   */
  PageResult<T> findPage(QueryOptions options);

  /**
   * 统计实体数量
   * 
   * @return 实体总数
   */
  long count();

  /**
   * 统计符合条件的实体数量
   * 
   * @param criteria 查询条件
   * @return 符合条件的实体数量
   */
  long countBy(QueryCriteria<T> criteria);
}