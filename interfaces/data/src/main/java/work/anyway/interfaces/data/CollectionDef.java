package work.anyway.interfaces.data;

/**
 * 集合定义
 * 用于类型安全地定义数据源和表信息
 * 
 * @author 作者名
 * @since 1.0.0
 */
public class CollectionDef {
  private final String dataSource;
  private final String schema;
  private final String table;
  private final Class<? extends Entity> entityClass;

  private CollectionDef(Builder builder) {
    this.dataSource = builder.dataSource;
    this.schema = builder.schema;
    this.table = builder.table;
    this.entityClass = builder.entityClass;
  }

  /**
   * 获取完整的集合名称
   * 格式: datasource:schema.table 或 datasource:table 或 table
   * 
   * @return 完整的集合名称
   */
  public String getFullName() {
    StringBuilder sb = new StringBuilder();
    if (dataSource != null && !dataSource.isEmpty()) {
      sb.append(dataSource).append(":");
    }
    if (schema != null && !schema.isEmpty()) {
      sb.append(schema).append(".");
    }
    sb.append(table);
    return sb.toString();
  }

  /**
   * 获取数据源名称
   * 
   * @return 数据源名称，可能为 null
   */
  public String getDataSource() {
    return dataSource;
  }

  /**
   * 获取模式名称
   * 
   * @return 模式名称，可能为 null
   */
  public String getSchema() {
    return schema;
  }

  /**
   * 获取表名
   * 
   * @return 表名
   */
  public String getTable() {
    return table;
  }

  /**
   * 获取实体类型
   * 
   * @return 实体类型
   */
  public Class<? extends Entity> getEntityClass() {
    return entityClass;
  }

  /**
   * 创建一个构建器
   * 
   * @param table 表名
   * @return 构建器实例
   */
  public static Builder builder(String table) {
    return new Builder(table);
  }

  /**
   * 集合定义构建器
   */
  public static class Builder {
    private final String table;
    private String dataSource;
    private String schema;
    private Class<? extends Entity> entityClass;

    private Builder(String table) {
      if (table == null || table.isEmpty()) {
        throw new IllegalArgumentException("表名不能为空");
      }
      this.table = table;
    }

    /**
     * 设置数据源
     * 
     * @param dataSource 数据源名称
     * @return 当前构建器实例
     */
    public Builder dataSource(String dataSource) {
      this.dataSource = dataSource;
      return this;
    }

    /**
     * 设置模式
     * 
     * @param schema 模式名称
     * @return 当前构建器实例
     */
    public Builder schema(String schema) {
      this.schema = schema;
      return this;
    }

    /**
     * 设置实体类型
     * 
     * @param entityClass 实体类型
     * @return 当前构建器实例
     */
    public Builder entityClass(Class<? extends Entity> entityClass) {
      this.entityClass = entityClass;
      return this;
    }

    /**
     * 构建集合定义
     * 
     * @return 集合定义实例
     */
    public CollectionDef build() {
      return new CollectionDef(this);
    }
  }

  @Override
  public String toString() {
    return "CollectionDef{" +
        "fullName='" + getFullName() + '\'' +
        ", entityClass=" + (entityClass != null ? entityClass.getSimpleName() : "null") +
        '}';
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    CollectionDef that = (CollectionDef) o;

    if (!table.equals(that.table))
      return false;
    if (dataSource != null ? !dataSource.equals(that.dataSource) : that.dataSource != null)
      return false;
    return schema != null ? schema.equals(that.schema) : that.schema == null;
  }

  @Override
  public int hashCode() {
    int result = dataSource != null ? dataSource.hashCode() : 0;
    result = 31 * result + (schema != null ? schema.hashCode() : 0);
    result = 31 * result + table.hashCode();
    return result;
  }
}