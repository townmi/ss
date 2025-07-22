package work.anyway.interfaces.data;

import lombok.Getter;
import work.anyway.annotations.Column;
import work.anyway.annotations.Table;
import work.anyway.annotations.Transient;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实体元数据
 * 缓存实体类的表信息和字段映射信息
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Getter
public class EntityMetadata<T> {

  private final Class<T> entityClass;
  private final String tableName;
  private final String schema;
  private final String dataSource;
  private final Map<String, FieldMetadata> fields;
  private final List<FieldMetadata> persistentFields;
  private final FieldMetadata primaryKeyField;

  // 缓存已解析的元数据
  private static final Map<Class<?>, EntityMetadata<?>> CACHE = new ConcurrentHashMap<>();

  private EntityMetadata(Class<T> entityClass) {
    this.entityClass = entityClass;

    // 解析 @Table 注解
    Table table = entityClass.getAnnotation(Table.class);
    if (table != null) {
      this.tableName = table.value();
      this.schema = table.schema();
      this.dataSource = table.dataSource();
    } else {
      // 默认使用类名的 snake_case 作为表名
      this.tableName = camelToSnake(entityClass.getSimpleName());
      this.schema = "";
      this.dataSource = "";
    }

    // 解析字段
    this.fields = new LinkedHashMap<>();
    this.persistentFields = new ArrayList<>();
    FieldMetadata primaryKey = null;

    // 遍历所有字段（包括父类）
    Class<?> currentClass = entityClass;
    while (currentClass != null && currentClass != Object.class) {
      for (Field field : currentClass.getDeclaredFields()) {
        // 跳过静态字段和 transient 字段
        if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) ||
            java.lang.reflect.Modifier.isTransient(field.getModifiers()) ||
            field.isAnnotationPresent(Transient.class)) {
          continue;
        }

        FieldMetadata fieldMeta = new FieldMetadata(field);
        fields.put(field.getName(), fieldMeta);

        if (!field.isAnnotationPresent(Transient.class)) {
          persistentFields.add(fieldMeta);

          if (fieldMeta.isPrimaryKey()) {
            primaryKey = fieldMeta;
          }
        }
      }
      currentClass = currentClass.getSuperclass();
    }

    // 如果没有显式指定主键，默认使用 "id" 字段
    if (primaryKey == null && fields.containsKey("id")) {
      primaryKey = fields.get("id");
      primaryKey.setPrimaryKey(true);
    }

    this.primaryKeyField = primaryKey;
  }

  /**
   * 获取实体类的元数据
   */
  @SuppressWarnings("unchecked")
  public static <T> EntityMetadata<T> of(Class<T> entityClass) {
    return (EntityMetadata<T>) CACHE.computeIfAbsent(entityClass, EntityMetadata::new);
  }

  /**
   * 获取完整的表名（包含 schema）
   */
  public String getFullTableName() {
    if (schema != null && !schema.isEmpty()) {
      return schema + "." + tableName;
    }
    return tableName;
  }

  /**
   * 生成 INSERT SQL
   */
  public String getInsertSql() {
    StringBuilder sql = new StringBuilder("INSERT INTO ");
    sql.append(getFullTableName()).append(" (");

    StringJoiner columns = new StringJoiner(", ");
    StringJoiner values = new StringJoiner(", ");

    for (FieldMetadata field : persistentFields) {
      columns.add(field.getColumnName());
      values.add("?");
    }

    sql.append(columns).append(") VALUES (").append(values).append(")");
    return sql.toString();
  }

  /**
   * 生成 UPDATE SQL
   */
  public String getUpdateSql() {
    StringBuilder sql = new StringBuilder("UPDATE ");
    sql.append(getFullTableName()).append(" SET ");

    StringJoiner sets = new StringJoiner(", ");
    for (FieldMetadata field : persistentFields) {
      if (!field.isPrimaryKey()) {
        sets.add(field.getColumnName() + " = ?");
      }
    }

    sql.append(sets);
    if (primaryKeyField != null) {
      sql.append(" WHERE ").append(primaryKeyField.getColumnName()).append(" = ?");
    }

    return sql.toString();
  }

  /**
   * 生成 SELECT BY ID SQL
   */
  public String getSelectByIdSql() {
    StringBuilder sql = new StringBuilder("SELECT ");

    StringJoiner columns = new StringJoiner(", ");
    for (FieldMetadata field : persistentFields) {
      columns.add(field.getColumnName());
    }

    sql.append(columns).append(" FROM ").append(getFullTableName());

    if (primaryKeyField != null) {
      sql.append(" WHERE ").append(primaryKeyField.getColumnName()).append(" = ?");
    }

    return sql.toString();
  }

  /**
   * 生成 DELETE SQL
   */
  public String getDeleteSql() {
    StringBuilder sql = new StringBuilder("DELETE FROM ");
    sql.append(getFullTableName());

    if (primaryKeyField != null) {
      sql.append(" WHERE ").append(primaryKeyField.getColumnName()).append(" = ?");
    }

    return sql.toString();
  }

  /**
   * 字段元数据
   */
  @Getter
  public static class FieldMetadata {
    private final Field field;
    private final String fieldName;
    private final String columnName;
    private boolean primaryKey;
    private final boolean nullable;
    private final boolean unique;
    private final int length;

    public FieldMetadata(Field field) {
      this.field = field;
      this.fieldName = field.getName();

      Column column = field.getAnnotation(Column.class);
      if (column != null) {
        this.columnName = column.value().isEmpty() ? camelToSnake(fieldName) : column.value();
        this.primaryKey = column.primaryKey();
        this.nullable = column.nullable();
        this.unique = column.unique();
        this.length = column.length();
      } else {
        this.columnName = camelToSnake(fieldName);
        this.primaryKey = "id".equals(fieldName);
        this.nullable = true;
        this.unique = false;
        this.length = 255;
      }

      field.setAccessible(true);
    }

    public void setPrimaryKey(boolean primaryKey) {
      this.primaryKey = primaryKey;
    }

    public Object getValue(Object entity) {
      try {
        return field.get(entity);
      } catch (IllegalAccessException e) {
        throw new RuntimeException("Failed to get field value: " + fieldName, e);
      }
    }

    public void setValue(Object entity, Object value) {
      try {
        field.set(entity, value);
      } catch (IllegalAccessException e) {
        throw new RuntimeException("Failed to set field value: " + fieldName, e);
      }
    }
  }

  /**
   * 将驼峰命名转换为下划线命名
   */
  private static String camelToSnake(String camel) {
    if (camel == null || camel.isEmpty()) {
      return camel;
    }

    StringBuilder snake = new StringBuilder();
    for (int i = 0; i < camel.length(); i++) {
      char c = camel.charAt(i);
      if (Character.isUpperCase(c)) {
        if (i > 0) {
          snake.append('_');
        }
        snake.append(Character.toLowerCase(c));
      } else {
        snake.append(c);
      }
    }
    return snake.toString();
  }
}