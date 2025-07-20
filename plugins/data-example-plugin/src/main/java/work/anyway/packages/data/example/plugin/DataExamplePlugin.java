package work.anyway.packages.data.example.plugin;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import work.anyway.interfaces.data.*;
import work.anyway.interfaces.plugin.Plugin;
import work.anyway.interfaces.plugin.ServiceRegistry;
import work.anyway.packages.data.example.plugin.entity.User;
import work.anyway.packages.data.example.plugin.entity.Product;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 数据插件使用示例
 * 展示如何使用类型安全的数据访问 API
 * 
 * @author 作者名
 * @since 1.0.0
 */
public class DataExamplePlugin implements Plugin {
  
  private static final Logger LOG = LoggerFactory.getLogger(DataExamplePlugin.class);
  
  private DataService dataService;
  
  private Repository<User> userRepository;
  private Repository<Product> productRepository;
  
  @Override
  public String getName() {
    return "data-example";
  }
  
  @Override
  public String getVersion() {
    return "1.0.0";
  }
  
  @Override
  public String getDescription() {
    return "数据插件使用示例，展示类型安全的数据访问";
  }
  
  @Override
  public String getIcon() {
    return "📚";
  }
  
  @Override
  public String getMainPagePath() {
    return "/page/data-example/";
  }
  
  @Override
  public void initialize(Router router) {
    initialize(router, null);
  }
  
  @Override
  public void initialize(Router router, ServiceRegistry registry) {
    LOG.info("Initializing Data Example Plugin");
    
    // 如果 dataService 未注入，尝试从注册表获取
    if (dataService == null && registry != null) {
      Optional<DataService> dataServiceOpt = registry.lookup(DataService.class);
      if (dataServiceOpt.isPresent()) {
        dataService = dataServiceOpt.get();
        LOG.info("DataService obtained from registry");
      }
    }
    
    // 检查 dataService 是否实现了 TypedDataService
    if (dataService == null || !(dataService instanceof TypedDataService)) {
      LOG.error("DataService is not available or not TypedDataService implementation");
      return;
    }
    
    LOG.info("DataService is TypedDataService implementation, ready to use");
    
    // 初始化仓库
    initializeRepositories();
    
    // 配置请求体处理器
    router.route("/api/data-example/*").handler(BodyHandler.create());
    
    // 注册路由
    registerRoutes(router);
    
    LOG.info("Data Example Plugin initialized successfully");
  }
  
  /**
   * 初始化仓库并创建示例数据
   */
  private void initializeRepositories() {
    try {
      // 将 dataService 转换为 TypedDataService
      TypedDataService typedDataService = (TypedDataService) dataService;
      
      // 获取仓库
      userRepository = typedDataService.getRepository(Collections.USERS, User.class);
      productRepository = typedDataService.getRepository(Collections.PRODUCTS, Product.class);
      
      // 创建示例数据
      createSampleData();
      
      LOG.info("Repositories initialized successfully");
    } catch (Exception e) {
      LOG.error("Failed to initialize repositories", e);
    }
  }
  
  /**
   * 创建示例数据
   */
  private void createSampleData() {
    try {
      // 检查并创建用户数据
      if (userRepository.count() == 0) {
        User admin = new User();
        admin.setUsername("admin");
        admin.setEmail("admin@example.com");
        admin.setRole("admin");
        admin.setActive(true);
        userRepository.save(admin);
        
        User john = new User();
        john.setUsername("john_doe");
        john.setEmail("john@example.com");
        john.setRole("user");
        john.setActive(true);
        userRepository.save(john);
        
        User jane = new User();
        jane.setUsername("jane_smith");
        jane.setEmail("jane@example.com");
        jane.setRole("user");
        jane.setActive(false);
        userRepository.save(jane);
        
        LOG.info("Created sample users");
      }
      
      // 检查并创建产品数据
      if (productRepository.count() == 0) {
        Product laptop = new Product();
        laptop.setName("Laptop Pro");
        laptop.setDescription("High-performance laptop for professionals");
        laptop.setPrice(1299.99);
        laptop.setStock(50);
        productRepository.save(laptop);
        
        Product mouse = new Product();
        mouse.setName("Wireless Mouse");
        mouse.setDescription("Ergonomic wireless mouse with precision tracking");
        mouse.setPrice(49.99);
        mouse.setStock(200);
        productRepository.save(mouse);
        
        Product keyboard = new Product();
        keyboard.setName("Mechanical Keyboard");
        keyboard.setDescription("RGB mechanical keyboard with Cherry MX switches");
        keyboard.setPrice(149.99);
        keyboard.setStock(75);
        productRepository.save(keyboard);
        
        LOG.info("Created sample products");
      }
    } catch (Exception e) {
      LOG.error("Failed to create sample data", e);
    }
  }
  
  /**
   * 注册路由
   */
  private void registerRoutes(Router router) {
    // 页面路由
    router.get("/page/data-example/").handler(this::getIndexPage);
    router.get("/page/data-example/users").handler(this::getUsersPage);
    router.get("/page/data-example/products").handler(this::getProductsPage);
    router.get("/page/data-example/demo").handler(this::getDemoPage);
    
    // API 路由
    router.get("/api/data-example/users").handler(this::getUsers);
    router.get("/api/data-example/users/:id").handler(this::getUserById);
    router.post("/api/data-example/users").handler(this::createUser);
    router.put("/api/data-example/users/:id").handler(this::updateUser);
    router.delete("/api/data-example/users/:id").handler(this::deleteUser);
    
    router.get("/api/data-example/products").handler(this::getProducts);
    router.get("/api/data-example/products/:id").handler(this::getProductById);
    router.post("/api/data-example/products").handler(this::createProduct);
    router.put("/api/data-example/products/:id").handler(this::updateProduct);
    router.delete("/api/data-example/products/:id").handler(this::deleteProduct);
    
    // 示例查询 API
    router.get("/api/data-example/demo/active-users").handler(this::getActiveUsers);
    router.get("/api/data-example/demo/low-stock-products").handler(this::getLowStockProducts);
  }
  
  // 页面处理方法
  
  private void getIndexPage(RoutingContext ctx) {
    serveHtmlPage(ctx, "index.html");
  }
  
  private void getUsersPage(RoutingContext ctx) {
    serveHtmlPage(ctx, "users.html");
  }
  
  private void getProductsPage(RoutingContext ctx) {
    serveHtmlPage(ctx, "products.html");
  }
  
  private void getDemoPage(RoutingContext ctx) {
    serveHtmlPage(ctx, "demo.html");
  }
  
  // API 处理方法 - 用户
  
  private void getUsers(RoutingContext ctx) {
    try {
      List<User> users = userRepository.findAll();
      
      JsonArray jsonUsers = new JsonArray();
      for (User user : users) {
        jsonUsers.add(userToJson(user));
      }
      
      sendSuccess(ctx, new JsonObject()
        .put("data", jsonUsers)
        .put("total", users.size()));
    } catch (Exception e) {
      LOG.error("Failed to get users", e);
      sendError(ctx, 500, "Failed to get users: " + e.getMessage());
    }
  }
  
  private void getUserById(RoutingContext ctx) {
    try {
      String id = ctx.pathParam("id");
      Optional<User> user = userRepository.findById(id);
      
      if (user.isPresent()) {
        sendSuccess(ctx, new JsonObject().put("data", userToJson(user.get())));
      } else {
        sendError(ctx, 404, "User not found");
      }
    } catch (Exception e) {
      LOG.error("Failed to get user", e);
      sendError(ctx, 500, "Failed to get user: " + e.getMessage());
    }
  }
  
  private void createUser(RoutingContext ctx) {
    try {
      JsonObject body = ctx.body().asJsonObject();
      
      User user = new User();
      user.setUsername(body.getString("username"));
      user.setEmail(body.getString("email"));
      user.setRole(body.getString("role", "user"));
      user.setActive(body.getBoolean("active", true));
      
      User saved = userRepository.save(user);
      sendSuccess(ctx, new JsonObject().put("data", userToJson(saved)), 201);
    } catch (Exception e) {
      LOG.error("Failed to create user", e);
      sendError(ctx, 500, "Failed to create user: " + e.getMessage());
    }
  }
  
  private void updateUser(RoutingContext ctx) {
    try {
      String id = ctx.pathParam("id");
      JsonObject body = ctx.body().asJsonObject();
      
      Optional<User> existing = userRepository.findById(id);
      if (existing.isEmpty()) {
        sendError(ctx, 404, "User not found");
        return;
      }
      
      User user = existing.get();
      if (body.containsKey("username")) user.setUsername(body.getString("username"));
      if (body.containsKey("email")) user.setEmail(body.getString("email"));
      if (body.containsKey("role")) user.setRole(body.getString("role"));
      if (body.containsKey("active")) user.setActive(body.getBoolean("active"));
      
      boolean success = userRepository.update(user);
      if (success) {
        sendSuccess(ctx, new JsonObject().put("message", "User updated successfully"));
      } else {
        sendError(ctx, 500, "Failed to update user");
      }
    } catch (Exception e) {
      LOG.error("Failed to update user", e);
      sendError(ctx, 500, "Failed to update user: " + e.getMessage());
    }
  }
  
  private void deleteUser(RoutingContext ctx) {
    try {
      String id = ctx.pathParam("id");
      boolean success = userRepository.delete(id);
      
      if (success) {
        sendSuccess(ctx, new JsonObject().put("message", "User deleted successfully"));
      } else {
        sendError(ctx, 404, "User not found");
      }
    } catch (Exception e) {
      LOG.error("Failed to delete user", e);
      sendError(ctx, 500, "Failed to delete user: " + e.getMessage());
    }
  }
  
  // API 处理方法 - 产品
  
  private void getProducts(RoutingContext ctx) {
    try {
      List<Product> products = productRepository.findAll();
      
      JsonArray jsonProducts = new JsonArray();
      for (Product product : products) {
        jsonProducts.add(productToJson(product));
      }
      
      sendSuccess(ctx, new JsonObject()
        .put("data", jsonProducts)
        .put("total", products.size()));
    } catch (Exception e) {
      LOG.error("Failed to get products", e);
      sendError(ctx, 500, "Failed to get products: " + e.getMessage());
    }
  }
  
  private void getProductById(RoutingContext ctx) {
    try {
      String id = ctx.pathParam("id");
      Optional<Product> product = productRepository.findById(id);
      
      if (product.isPresent()) {
        sendSuccess(ctx, new JsonObject().put("data", productToJson(product.get())));
      } else {
        sendError(ctx, 404, "Product not found");
      }
    } catch (Exception e) {
      LOG.error("Failed to get product", e);
      sendError(ctx, 500, "Failed to get product: " + e.getMessage());
    }
  }
  
  private void createProduct(RoutingContext ctx) {
    try {
      JsonObject body = ctx.body().asJsonObject();
      
      Product product = new Product();
      product.setName(body.getString("name"));
      product.setDescription(body.getString("description"));
      product.setPrice(body.getDouble("price"));
      product.setStock(body.getInteger("stock", 0));
      
      Product saved = productRepository.save(product);
      sendSuccess(ctx, new JsonObject().put("data", productToJson(saved)), 201);
    } catch (Exception e) {
      LOG.error("Failed to create product", e);
      sendError(ctx, 500, "Failed to create product: " + e.getMessage());
    }
  }
  
  private void updateProduct(RoutingContext ctx) {
    try {
      String id = ctx.pathParam("id");
      JsonObject body = ctx.body().asJsonObject();
      
      Optional<Product> existing = productRepository.findById(id);
      if (existing.isEmpty()) {
        sendError(ctx, 404, "Product not found");
        return;
      }
      
      Product product = existing.get();
      if (body.containsKey("name")) product.setName(body.getString("name"));
      if (body.containsKey("description")) product.setDescription(body.getString("description"));
      if (body.containsKey("price")) product.setPrice(body.getDouble("price"));
      if (body.containsKey("stock")) product.setStock(body.getInteger("stock"));
      
      boolean success = productRepository.update(product);
      if (success) {
        sendSuccess(ctx, new JsonObject().put("message", "Product updated successfully"));
      } else {
        sendError(ctx, 500, "Failed to update product");
      }
    } catch (Exception e) {
      LOG.error("Failed to update product", e);
      sendError(ctx, 500, "Failed to update product: " + e.getMessage());
    }
  }
  
  private void deleteProduct(RoutingContext ctx) {
    try {
      String id = ctx.pathParam("id");
      boolean success = productRepository.delete(id);
      
      if (success) {
        sendSuccess(ctx, new JsonObject().put("message", "Product deleted successfully"));
      } else {
        sendError(ctx, 404, "Product not found");
      }
    } catch (Exception e) {
      LOG.error("Failed to delete product", e);
      sendError(ctx, 500, "Failed to delete product: " + e.getMessage());
    }
  }
  
  // 示例查询方法
  
  private void getActiveUsers(RoutingContext ctx) {
    try {
      List<User> activeUsers = userRepository.findBy(
        QueryCriteria.<User>create()
          .eq("active", true)
          .orderBy("username", true)
      );
      
      JsonArray jsonUsers = new JsonArray();
      for (User user : activeUsers) {
        jsonUsers.add(userToJson(user));
      }
      
      sendSuccess(ctx, new JsonObject()
        .put("data", jsonUsers)
        .put("total", activeUsers.size())
        .put("description", "Active users sorted by username"));
    } catch (Exception e) {
      LOG.error("Failed to get active users", e);
      sendError(ctx, 500, "Failed to get active users: " + e.getMessage());
    }
  }
  
  private void getLowStockProducts(RoutingContext ctx) {
    try {
      // 获取库存小于 20 的产品
      List<Product> lowStockProducts = productRepository.findBy(
        QueryCriteria.<Product>create()
          .lt("stock", 20)
          .orderBy("stock", true)
      );
      
      JsonArray jsonProducts = new JsonArray();
      for (Product product : lowStockProducts) {
        jsonProducts.add(productToJson(product));
      }
      
      sendSuccess(ctx, new JsonObject()
        .put("data", jsonProducts)
        .put("total", lowStockProducts.size())
        .put("description", "Products with stock less than 20"));
    } catch (Exception e) {
      LOG.error("Failed to get low stock products", e);
      sendError(ctx, 500, "Failed to get low stock products: " + e.getMessage());
    }
  }
  
  // 辅助方法
  
  private void serveHtmlPage(RoutingContext ctx, String filename) {
    try {
      String html = readResourceFile("data-example-plugin/templates/" + filename);
      if (html != null) {
        ctx.response()
          .putHeader("content-type", "text/html; charset=utf-8")
          .end(html);
      } else {
        ctx.response().setStatusCode(404).end("Page not found");
      }
    } catch (Exception e) {
      LOG.error("Failed to serve page: " + filename, e);
      ctx.response().setStatusCode(500).end("Internal Server Error");
    }
  }
  
  private String readResourceFile(String path) {
    try (InputStream is = DataExamplePlugin.class.getResourceAsStream("/" + path)) {
      if (is == null) return null;
      try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name())) {
        scanner.useDelimiter("\\A");
        return scanner.hasNext() ? scanner.next() : "";
      }
    } catch (Exception e) {
      LOG.error("Failed to read resource file: " + path, e);
      return null;
    }
  }
  
  private void sendSuccess(RoutingContext ctx, JsonObject data) {
    sendSuccess(ctx, data, 200);
  }
  
  private void sendSuccess(RoutingContext ctx, JsonObject data, int statusCode) {
    JsonObject response = new JsonObject()
      .put("success", true)
      .mergeIn(data);
    
    ctx.response()
      .setStatusCode(statusCode)
      .putHeader("content-type", "application/json")
      .end(response.encode());
  }
  
  private void sendError(RoutingContext ctx, int statusCode, String message) {
    JsonObject response = new JsonObject()
      .put("success", false)
      .put("error", message);
    
    ctx.response()
      .setStatusCode(statusCode)
      .putHeader("content-type", "application/json")
      .end(response.encode());
  }
  
  private JsonObject userToJson(User user) {
    return new JsonObject()
      .put("id", user.getId())
      .put("username", user.getUsername())
      .put("email", user.getEmail())
      .put("role", user.getRole())
      .put("active", user.isActive())
      .put("createdAt", user.getCreatedAt())
      .put("updatedAt", user.getUpdatedAt());
  }
  
  private JsonObject productToJson(Product product) {
    return new JsonObject()
      .put("id", product.getId())
      .put("name", product.getName())
      .put("description", product.getDescription())
      .put("price", product.getPrice())
      .put("stock", product.getStock())
      .put("createdAt", product.getCreatedAt())
      .put("updatedAt", product.getUpdatedAt());
  }
} 