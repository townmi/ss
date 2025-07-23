package work.anyway.interfaces.auth;

import java.util.List;

/**
 * 权限扫描器接口
 * 提供权限元数据的访问
 * 
 * @author 作者名
 * @since 1.0.0
 */
public interface PermissionScanner {
  /**
   * 获取所有权限定义
   */
  List<PermissionInfo> getAllPermissions();

  /**
   * 根据权限码获取权限定义
   */
  PermissionInfo getPermission(String code);
}