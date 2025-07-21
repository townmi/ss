package work.anyway.packages.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import work.anyway.interfaces.auth.PermissionService;
import work.anyway.interfaces.data.DataService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 权限服务实现
 * 使用 DataService 存储权限数据
 */
@Service
public class PermissionServiceImpl implements PermissionService {

  private static final String PERMISSIONS_COLLECTION = "user_permissions";

  @Autowired
  private DataService dataService;

  @Override
  public boolean hasPermission(String userId, String permission) {
    Set<String> permissions = getUserPermissions(userId);
    return permissions.contains(permission);
  }

  @Override
  public void grantPermission(String userId, String permission) {
    Map<String, Object> criteria = new HashMap<>();
    criteria.put("userId", userId);

    List<Map<String, Object>> results = dataService.findByCriteria(PERMISSIONS_COLLECTION, criteria);

    if (results.isEmpty()) {
      // 创建新的权限记录
      Map<String, Object> data = new HashMap<>();
      data.put("userId", userId);
      data.put("permissions", new HashSet<>(Collections.singletonList(permission)));
      dataService.save(PERMISSIONS_COLLECTION, data);
    } else {
      // 更新现有权限
      Map<String, Object> existing = results.get(0);
      Set<String> permissions = new HashSet<>(
          (Collection<String>) existing.getOrDefault("permissions", new HashSet<>()));
      permissions.add(permission);

      Map<String, Object> update = new HashMap<>();
      update.put("permissions", permissions);
      dataService.update(PERMISSIONS_COLLECTION, (String) existing.get("id"), update);
    }
  }

  @Override
  public void revokePermission(String userId, String permission) {
    Map<String, Object> criteria = new HashMap<>();
    criteria.put("userId", userId);

    List<Map<String, Object>> results = dataService.findByCriteria(PERMISSIONS_COLLECTION, criteria);

    if (!results.isEmpty()) {
      Map<String, Object> existing = results.get(0);
      Set<String> permissions = new HashSet<>(
          (Collection<String>) existing.getOrDefault("permissions", new HashSet<>()));
      permissions.remove(permission);

      if (permissions.isEmpty()) {
        // 如果没有权限了，删除记录
        dataService.delete(PERMISSIONS_COLLECTION, (String) existing.get("id"));
      } else {
        // 更新权限
        Map<String, Object> update = new HashMap<>();
        update.put("permissions", permissions);
        dataService.update(PERMISSIONS_COLLECTION, (String) existing.get("id"), update);
      }
    }
  }

  @Override
  public Set<String> getUserPermissions(String userId) {
    Map<String, Object> criteria = new HashMap<>();
    criteria.put("userId", userId);

    List<Map<String, Object>> results = dataService.findByCriteria(PERMISSIONS_COLLECTION, criteria);

    if (results.isEmpty()) {
      return new HashSet<>();
    }

    Map<String, Object> permissionData = results.get(0);
    Object permissions = permissionData.get("permissions");

    if (permissions instanceof Collection) {
      return new HashSet<>((Collection<String>) permissions);
    }

    return new HashSet<>();
  }
}