package work.anyway.packages.auth.plugin.permission;

import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import work.anyway.interfaces.auth.PermissionInfo;
import work.anyway.interfaces.auth.PermissionScanner;
import work.anyway.interfaces.auth.PermissionService;

import java.util.List;

/**
 * 权限初始化器
 * 在应用启动时将扫描到的权限定义同步到数据库
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Component
public class PermissionInitializer {

  private static final Logger LOG = LoggerFactory.getLogger(PermissionInitializer.class);

  @Autowired
  private PermissionScanner permissionScanner;

  @Autowired
  private PermissionService permissionService;

  @Autowired(required = false)
  private Vertx vertx;

  /**
   * 应用启动完成后初始化权限
   * 
   * @param event Spring 容器刷新事件
   */
  @EventListener
  public void onApplicationEvent(ContextRefreshedEvent event) {
    LOG.info("Initializing permissions from metadata...");

    if (vertx != null) {
      // 在 worker 线程中执行阻塞操作
      vertx.executeBlocking(promise -> {
        try {
          // 获取所有扫描到的权限定义
          List<PermissionInfo> permissions = permissionScanner.getAllPermissions();
          LOG.info("Found {} permission definitions to initialize", permissions.size());

          // 初始化权限
          initializePermissions(permissions);

          LOG.info("Permission initialization completed successfully");
          promise.complete();
        } catch (Exception e) {
          LOG.error("Failed to initialize permissions", e);
          promise.fail(e);
        }
      }, false, result -> {
        if (result.failed()) {
          LOG.error("Permission initialization failed", result.cause());
        }
      });
    } else {
      // 如果没有 Vertx 实例（例如在测试中），直接执行
      try {
        List<PermissionInfo> permissions = permissionScanner.getAllPermissions();
        LOG.info("Found {} permission definitions to initialize", permissions.size());
        initializePermissions(permissions);
        LOG.info("Permission initialization completed successfully");
      } catch (Exception e) {
        LOG.error("Failed to initialize permissions", e);
      }
    }
  }

  /**
   * 初始化权限定义
   * 
   * @param permissions 权限定义列表
   */
  private void initializePermissions(List<PermissionInfo> permissions) {
    int created = 0;
    int updated = 0;
    int roleAssignments = 0;

    for (PermissionInfo perm : permissions) {
      try {
        // 1. 注册权限（如果不存在则创建，存在则更新）
        boolean isNew = registerPermissionWithPlugin(perm);

        if (isNew) {
          created++;
          LOG.debug("Created new permission: {} - {}", perm.getCode(), perm.getName());
        } else {
          updated++;
          LOG.debug("Updated existing permission: {} - {}", perm.getCode(), perm.getName());
        }

        // 2. 为默认角色分配权限
        for (String role : perm.getDefaultRoles()) {
          try {
            boolean assigned = permissionService.grantPermissionToRole(role, perm.getCode());
            if (assigned) {
              roleAssignments++;
              LOG.debug("Granted permission {} to role {}", perm.getCode(), role);
            }
          } catch (Exception e) {
            LOG.warn("Failed to grant permission {} to role {}: {}",
                perm.getCode(), role, e.getMessage());
          }
        }

      } catch (Exception e) {
        LOG.error("Failed to initialize permission: {} - {}",
            perm.getCode(), perm.getName(), e);
      }
    }

    LOG.info("Permission initialization summary: {} created, {} updated, {} role assignments",
        created, updated, roleAssignments);
  }

  /**
   * 注册权限并设置插件信息
   */
  private boolean registerPermissionWithPlugin(PermissionInfo perm) {
    // 由于 PermissionService 接口只接受三个参数，我们需要扩展接口或直接使用 DataService
    // 这里暂时使用基础的 registerPermission 方法
    return permissionService.registerPermission(
        perm.getCode(),
        perm.getName(),
        perm.getDescription());
  }

  /**
   * 手动触发权限初始化
   * 可用于运行时重新加载权限定义
   */
  public void reinitializePermissions() {
    LOG.info("Manually reinitializing permissions...");

    List<PermissionInfo> permissions = permissionScanner.getAllPermissions();
    initializePermissions(permissions);

    LOG.info("Manual permission reinitialization completed");
  }
}