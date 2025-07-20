package work.anyway.packages.data.example.plugin;

import work.anyway.interfaces.data.CollectionDef;
import work.anyway.packages.data.example.plugin.entity.User;
import work.anyway.packages.data.example.plugin.entity.Product;

/**
 * 示例集合定义
 * 
 * @author 作者名
 * @since 1.0.0
 */
public class Collections {
  // 用户表
  public static final CollectionDef USERS = CollectionDef.builder("example_users")
      .dataSource("main")
      .schema("public")
      .entityClass(User.class)
      .build();

  // 产品表
  public static final CollectionDef PRODUCTS = CollectionDef.builder("example_products")
      .dataSource("main")
      .schema("public")
      .entityClass(Product.class)
      .build();
} 