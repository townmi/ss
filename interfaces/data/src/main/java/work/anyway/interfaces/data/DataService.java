package work.anyway.interfaces.data;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 数据访问服务接口
 * 提供通用的数据存取操作
 * 
 * @author 作者名
 * @since 1.0.0
 */
public interface DataService {
  
  /**
   * 保存数据
   * 
   * @param collection 集合名称
   * @param data 要保存的数据
   * @return 保存后的数据（包含生成的ID）
   */
  Map<String, Object> save(String collection, Map<String, Object> data);
  
  /**
   * 根据ID查找数据
   * 
   * @param collection 集合名称
   * @param id 数据ID
   * @return 查找到的数据，如果不存在返回空
   */
  Optional<Map<String, Object>> findById(String collection, String id);
  
  /**
   * 查找所有数据
   * 
   * @param collection 集合名称
   * @return 所有数据列表
   */
  List<Map<String, Object>> findAll(String collection);
  
  /**
   * 根据条件查询数据
   * 
   * @param collection 集合名称
   * @param criteria 查询条件
   * @return 符合条件的数据列表
   */
  List<Map<String, Object>> findByCriteria(String collection, Map<String, Object> criteria);
  
  /**
   * 更新数据
   * 
   * @param collection 集合名称
   * @param id 数据ID
   * @param data 要更新的数据
   * @return 更新成功返回 true，否则返回 false
   */
  boolean update(String collection, String id, Map<String, Object> data);
  
  /**
   * 删除数据
   * 
   * @param collection 集合名称
   * @param id 数据ID
   * @return 删除成功返回 true，否则返回 false
   */
  boolean delete(String collection, String id);
  
  /**
   * 统计数据数量
   * 
   * @param collection 集合名称
   * @return 数据总数
   */
  long count(String collection);
  
  /**
   * 统计符合条件的数据数量
   * 
   * @param collection 集合名称
   * @param criteria 查询条件
   * @return 符合条件的数据数量
   */
  long countByCriteria(String collection, Map<String, Object> criteria);
  
  /**
   * 分页查询数据
   * 
   * @param collection 集合名称
   * @param options 查询选项（包含分页、排序、过滤条件等）
   * @return 分页查询结果
   */
  PageResult<Map<String, Object>> query(String collection, QueryOptions options);
  
  /**
   * 批量保存数据
   * 
   * @param collection 集合名称
   * @param dataList 要保存的数据列表
   * @return 保存成功的数量
   */
  int batchSave(String collection, List<Map<String, Object>> dataList);
  
  /**
   * 批量删除数据
   * 
   * @param collection 集合名称
   * @param ids ID列表
   * @return 删除成功的数量
   */
  int batchDelete(String collection, List<String> ids);
} 