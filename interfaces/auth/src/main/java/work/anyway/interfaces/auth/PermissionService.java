package work.anyway.interfaces.auth;

import java.util.Set;
import java.util.List;

/**
 * 权限服务接口
 * 提供权限管理相关功能
 */
public interface PermissionService {
  /**
   * 检查用户是否有指定权限
   */
  boolean hasPermission(String userId, String permission);

  /**
   * 授予用户权限
   */
  void grantPermission(String userId, String permission);

  /**
   * 撤销用户权限
   */
  void revokePermission(String userId, String permission);

  /**
   * 获取用户的所有权限
   */
  Set<String> getUserPermissions(String userId);

  /**
   * 注册权限定义
   * 
   * @param code        权限码
   * @param name        权限名称
   * @param description 权限描述
   * @return 是否为新创建的权限
   */
  boolean registerPermission(String code, String name, String description);

  /**
   * 授予角色权限
   * 
   * @param role       角色名称
   * @param permission 权限码
   * @return 是否授权成功
   */
  boolean grantPermissionToRole(String role, String permission);

  /**
   * 获取系统中所有的权限
   * 
   * @return 所有权限的列表
   */
  List<Permission> getAllPermissions();
}