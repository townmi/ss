package work.anyway.packages.system.plugin.menu;

import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import work.anyway.annotations.Controller;
import work.anyway.annotations.GetMapping;
import work.anyway.annotations.Intercepted;
import work.anyway.annotations.RequestMapping;
import work.anyway.interfaces.system.MenuItemInfo;
import work.anyway.interfaces.system.MenuService;
import work.anyway.interfaces.system.MetadataScanner;

import java.util.List;

/**
 * 菜单控制器
 * 提供菜单相关的 API 接口
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Controller
@RequestMapping("/api/menus")
@Intercepted({ "SystemRequestLog" })
public class MenuController {

  private static final Logger LOG = LoggerFactory.getLogger(MenuController.class);

  @Autowired
  private MenuService menuService;

  @Autowired
  private MetadataScanner metadataScanner;

  /**
   * 获取当前用户的菜单树
   * 
   * @param ctx 路由上下文
   */
  @GetMapping("")
  @Intercepted({ "SimpleAuth" })
  public void getUserMenus(RoutingContext ctx) {
    try {
      // 从上下文获取用户ID（由 SimpleAuth 拦截器设置）
      String userId = ctx.get("userId");
      if (userId == null) {
        ctx.response()
            .setStatusCode(401)
            .putHeader("content-type", "application/json")
            .end(Json.encode(new ErrorResponse("Unauthorized", "User not authenticated")));
        return;
      }

      LOG.debug("Getting menu tree for user: {}", userId);

      // 获取用户菜单树
      List<MenuItemInfo> menuTree = menuService.getUserMenuTree(userId);

      // 返回 JSON 响应
      ctx.response()
          .putHeader("content-type", "application/json")
          .end(Json.encode(menuTree));

      LOG.debug("Returned {} root menus for user: {}", menuTree.size(), userId);

    } catch (Exception e) {
      LOG.error("Failed to get user menus", e);
      ctx.response()
          .setStatusCode(500)
          .putHeader("content-type", "application/json")
          .end(Json.encode(new ErrorResponse("Internal Server Error", e.getMessage())));
    }
  }

  /**
   * 获取所有菜单（管理员接口）
   * 
   * @param ctx 路由上下文
   */
  @GetMapping("/all")
  @Intercepted({ "Authentication" })
  public void getAllMenus(RoutingContext ctx) {
    try {
      // TODO: 检查管理员权限

      // 获取所有菜单（平面结构）
      List<MenuItemInfo> allMenus = metadataScanner.getAllMenuItems();

      // 返回 JSON 响应
      ctx.response()
          .putHeader("content-type", "application/json")
          .end(Json.encode(allMenus));

      LOG.info("Returned {} menus for admin", allMenus.size());

    } catch (Exception e) {
      LOG.error("Failed to get all menus", e);
      ctx.response()
          .setStatusCode(500)
          .putHeader("content-type", "application/json")
          .end(Json.encode(new ErrorResponse("Internal Server Error", e.getMessage())));
    }
  }

  /**
   * 刷新菜单元数据（管理员接口）
   * 
   * @param ctx 路由上下文
   */
  @GetMapping("/refresh")
  @Intercepted({ "Authentication" })
  public void refreshMenus(RoutingContext ctx) {
    try {
      // TODO: 检查管理员权限

      // 刷新菜单元数据
      menuService.refreshMenuMetadata();

      // 返回成功响应
      ctx.response()
          .putHeader("content-type", "application/json")
          .end(Json.encode(new SuccessResponse("Menu metadata refreshed successfully")));

      LOG.info("Menu metadata refreshed by admin");

    } catch (Exception e) {
      LOG.error("Failed to refresh menu metadata", e);
      ctx.response()
          .setStatusCode(500)
          .putHeader("content-type", "application/json")
          .end(Json.encode(new ErrorResponse("Internal Server Error", e.getMessage())));
    }
  }

  /**
   * 错误响应
   */
  private static class ErrorResponse {
    public final String error;
    public final String message;
    public final long timestamp;

    public ErrorResponse(String error, String message) {
      this.error = error;
      this.message = message;
      this.timestamp = System.currentTimeMillis();
    }
  }

  /**
   * 成功响应
   */
  private static class SuccessResponse {
    public final boolean success = true;
    public final String message;
    public final long timestamp;

    public SuccessResponse(String message) {
      this.message = message;
      this.timestamp = System.currentTimeMillis();
    }
  }
}