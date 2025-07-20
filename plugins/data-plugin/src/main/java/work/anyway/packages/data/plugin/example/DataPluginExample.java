package work.anyway.packages.data.plugin.example;

import work.anyway.interfaces.data.*;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * 数据插件使用示例
 * 展示如何使用类型安全的数据访问
 * 
 * @author 作者名
 * @since 1.0.0
 */
public class DataPluginExample {

  /**
   * 定义用户实体
   */
  public static class User extends Entity {
    private String username;
    private String email;
    private String role;
    private boolean active;

    // Getters and Setters
    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }

    public String getRole() {
      return role;
    }

    public void setRole(String role) {
      this.role = role;
    }

    public boolean isActive() {
      return active;
    }

    public void setActive(boolean active) {
      this.active = active;
    }
  }

  /**
   * 定义产品实体
   */
  public static class Product extends Entity {
    private String name;
    private String description;
    private double price;
    private int stock;

    // Getters and Setters
    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public double getPrice() {
      return price;
    }

    public void setPrice(double price) {
      this.price = price;
    }

    public int getStock() {
      return stock;
    }

    public void setStock(int stock) {
      this.stock = stock;
    }
  }

  /**
   * 定义集合常量，避免拼写错误
   */
  public static class Collections {
    // 主数据源的用户表
    public static final CollectionDef USERS = CollectionDef.builder("users")
        .dataSource("main")
        .schema("public")
        .entityClass(User.class)
        .build();

    // 主数据源的产品表
    public static final CollectionDef PRODUCTS = CollectionDef.builder("products")
        .dataSource("main")
        .schema("public")
        .entityClass(Product.class)
        .build();

    // 分析数据源的用户活动表
    public static final CollectionDef USER_ACTIVITIES = CollectionDef.builder("user_activities")
        .dataSource("analytics")
        .schema("reporting")
        .entityClass(UserActivity.class)
        .build();
  }

  /**
   * 使用示例
   */
  public void exampleUsage(TypedDataService dataService) {
    // 1. 获取类型安全的仓库
    Repository<User> userRepository = dataService.getRepository(Collections.USERS, User.class);
    Repository<Product> productRepository = dataService.getRepository(Collections.PRODUCTS, Product.class);

    // 2. 创建和保存用户
    User newUser = new User();
    newUser.setUsername("john_doe");
    newUser.setEmail("john@example.com");
    newUser.setRole("admin");
    newUser.setActive(true);

    User savedUser = userRepository.save(newUser);
    System.out.println("保存的用户ID: " + savedUser.getId());

    // 3. 根据ID查找
    Optional<User> foundUser = userRepository.findById(savedUser.getId());
    foundUser.ifPresent(user -> {
      System.out.println("找到用户: " + user.getUsername());
    });

    // 4. 条件查询 - 查找所有管理员
    List<User> admins = userRepository.findBy(
        QueryCriteria.<User>create()
            .eq("role", "admin")
            .eq("active", true));
    System.out.println("活跃管理员数量: " + admins.size());

    // 5. 复杂查询 - 使用多个条件
    List<User> filteredUsers = userRepository.findBy(
        QueryCriteria.<User>create()
            .like("email", "%@example.com")
            .ne("role", "guest")
            .orderBy("username", true));

    // 6. 自定义过滤器
    List<User> customFiltered = userRepository.findBy(
        QueryCriteria.<User>create()
            .eq("active", true)
            .custom(user -> user.getEmail() != null && user.getEmail().contains("gmail")));

    // 7. 更新用户
    savedUser.setRole("super_admin");
    boolean updated = userRepository.update(savedUser);
    System.out.println("更新成功: " + updated);

    // 8. 分页查询
    QueryOptions options = QueryOptions.create()
        .page(1)
        .pageSize(10)
        .sortBy("createdAt")
        .descending();

    PageResult<User> userPage = userRepository.findPage(options);
    System.out.println("总用户数: " + userPage.getTotal());
    System.out.println("当前页用户数: " + userPage.getData().size());

    // 9. 批量操作
    List<Product> products = List.of(
        createProduct("iPhone 15", 999.99, 100),
        createProduct("MacBook Pro", 2499.99, 50),
        createProduct("AirPods Pro", 249.99, 200));

    int savedCount = productRepository.batchSave(products);
    System.out.println("批量保存产品数: " + savedCount);

    // 10. 使用不同的数据源
    // 使用默认数据源
    Repository<User> defaultUsers = dataService.getRepository("users", User.class);

    // 使用指定数据源
    Repository<User> testUsers = dataService.getRepository("test", "users", User.class);

    // 11. 统计
    long totalUsers = userRepository.count();
    long activeAdmins = userRepository.countBy(
        QueryCriteria.<User>create()
            .eq("role", "admin")
            .eq("active", true));
    System.out.println("总用户数: " + totalUsers);
    System.out.println("活跃管理员数: " + activeAdmins);
  }

  private Product createProduct(String name, double price, int stock) {
    Product product = new Product();
    product.setName(name);
    product.setPrice(price);
    product.setStock(stock);
    product.setDescription("High quality " + name);
    return product;
  }

  /**
   * 用户活动实体（示例）
   */
  public static class UserActivity extends Entity {
    private String userId;
    private String action;
    private Date timestamp;

    // Getters and Setters...
  }
}