package work.anyway.packages.auth.plugin.permission;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import work.anyway.annotations.ScanDataProvider;
import work.anyway.interfaces.auth.PermissionInfo;
import work.anyway.interfaces.auth.PermissionScanner;

import java.util.*;

/**
 * 权限扫描器实现
 * 从 Host 的注解扫描器获取原始数据，转换为业务接口
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Component
@Primary
public class AuthPermissionScanner implements PermissionScanner {

  @Autowired
  private ScanDataProvider scanDataProvider;

  /**
   * 权限映射（用于快速查找）
   */
  private Map<String, PermissionInfo> permissionMap;

  /**
   * 初始化标志
   */
  private boolean initialized = false;

  /**
   * 初始化权限数据
   */
  private synchronized void initialize() {
    if (initialized) {
      return;
    }

    // 转换原始数据为 PermissionInfo
    permissionMap = new HashMap<>();
    List<Map<String, Object>> rawPermissions = scanDataProvider.getScannedPermissions();

    for (Map<String, Object> rawPermission : rawPermissions) {
      PermissionInfo permissionInfo = convertToPermissionInfo(rawPermission);
      permissionMap.put(permissionInfo.getCode(), permissionInfo);
    }

    initialized = true;
  }

  /**
   * 将原始数据转换为 PermissionInfo
   */
  private PermissionInfo convertToPermissionInfo(Map<String, Object> rawData) {
    return PermissionInfoImpl.builder()
        .code((String) rawData.get("code"))
        .name((String) rawData.get("name"))
        .description((String) rawData.get("description"))
        .defaultRoles(new HashSet<>((List<String>) rawData.get("defaultRoles")))
        .pluginName((String) rawData.get("pluginName"))
        .pluginVersion((String) rawData.get("pluginVersion"))
        .build();
  }

  @Override
  public List<PermissionInfo> getAllPermissions() {
    if (!initialized) {
      initialize();
    }
    return new ArrayList<>(permissionMap.values());
  }

  @Override
  public PermissionInfo getPermission(String code) {
    if (!initialized) {
      initialize();
    }
    return permissionMap.get(code);
  }
}