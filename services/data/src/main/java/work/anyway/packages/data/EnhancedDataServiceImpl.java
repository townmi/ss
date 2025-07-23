package work.anyway.packages.data;

import io.vertx.core.Vertx;
import io.vertx.sqlclient.Pool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import work.anyway.interfaces.data.*;

/**
 * 增强的数据服务实现
 * 提供类型安全的 Repository 支持
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Service("enhancedDataService")
@Primary
public class EnhancedDataServiceImpl extends AsyncDatabaseDataServiceImpl {

  private static final Logger LOG = LoggerFactory.getLogger(EnhancedDataServiceImpl.class);

  @Autowired
  public EnhancedDataServiceImpl(DataSourceManager dataSourceManager, Vertx vertx) {
    super(dataSourceManager, vertx);
    LOG.info("EnhancedDataServiceImpl initialized with typed repository support");
  }

  @Override
  public <T extends BaseEntity> Repository<T> getRepository(CollectionDef collectionDef, Class<T> entityClass) {
    // 使用类型安全的 Repository 实现
    Pool pool = dataSourceManager.getPool(collectionDef.getDataSource());
    return new TypedRepositoryImpl<>(vertx, pool, entityClass);
  }

  @Override
  public <T extends BaseEntity> Repository<T> getRepository(String table, Class<T> entityClass) {
    Pool pool = dataSourceManager.getDefaultPool();
    return new TypedRepositoryImpl<>(vertx, pool, entityClass);
  }

  @Override
  public <T extends BaseEntity> Repository<T> getRepository(String dataSource, String table, Class<T> entityClass) {
    Pool pool = dataSourceManager.getPool(dataSource);
    return new TypedRepositoryImpl<>(vertx, pool, entityClass);
  }

}