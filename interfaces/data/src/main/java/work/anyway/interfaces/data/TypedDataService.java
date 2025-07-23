package work.anyway.interfaces.data;

/**
 * 类型安全的数据服务接口
 * 扩展了原有的 DataService，提供泛型支持
 * 
 * @author 作者名
 * @since 1.0.0
 */
public interface TypedDataService extends DataService {

  /**
   * 获取类型安全的仓库
   * 
   * @param collectionDef 集合定义，包含数据源、模式、表名等信息
   * @param entityClass   实体类型
   * @param <T>           实体类型参数
   * @return 类型安全的仓库实例
   */
  <T extends BaseEntity> Repository<T> getRepository(CollectionDef collectionDef, Class<T> entityClass);

  /**
   * 使用默认数据源获取仓库
   * 
   * @param table       表名
   * @param entityClass 实体类型
   * @param <T>         实体类型参数
   * @return 类型安全的仓库实例
   */
  <T extends BaseEntity> Repository<T> getRepository(String table, Class<T> entityClass);

  /**
   * 使用指定数据源获取仓库
   * 
   * @param dataSource  数据源名称
   * @param table       表名
   * @param entityClass 实体类型
   * @param <T>         实体类型参数
   * @return 类型安全的仓库实例
   */
  <T extends BaseEntity> Repository<T> getRepository(String dataSource, String table, Class<T> entityClass);
}