package work.anyway.interfaces.auth;

import java.util.Set;

/**
 * 权限信息接口
 * 定义权限的数据结构
 * 
 * @author 作者名
 * @since 1.0.0
 */
public interface PermissionInfo {
  /**
   * 获取权限码
   */
  String getCode();

  /**
   * 获取权限名称
   */
  String getName();

  /**
   * 获取权限描述
   */
  String getDescription();

  /**
   * 获取默认分配给的角色
   */
  Set<String> getDefaultRoles();

  /**
   * 获取所属插件名称
   */
  String getPluginName();

  /**
   * 获取所属插件版本
   */
  String getPluginVersion();
}