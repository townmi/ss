package work.anyway.packages.auth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import work.anyway.interfaces.auth.PermissionService;
import work.anyway.interfaces.data.DataService;

import java.util.*;
import java.util.UUID;

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

  @Override
  public boolean registerPermission(String code, String name, String description) {
    // 检查权限是否已存在
    Map<String, Object> criteria = new HashMap<>();
    criteria.put("code", code);

    List<Map<String, Object>> existing = dataService.findByCriteria("permissions", criteria);

    if (existing.isEmpty()) {
      // 创建新权限
      Map<String, Object> permission = new HashMap<>();
      permission.put("id", UUID.randomUUID().toString());
      permission.put("code", code);
      permission.put("name", name);
      permission.put("description", description);
      permission.put("is_active", true);
      permission.put("created_at", new Date());

      dataService.save("permissions", permission);
      return true; // 新创建
    } else {
      // 更新现有权限
      Map<String, Object> update = new HashMap<>();
      update.put("name", name);
      update.put("description", description);
      update.put("updated_at", new Date());

      dataService.update("permissions", (String) existing.get(0).get("id"), update);
      return false; // 已存在，更新
    }
  }

  @Override
  public boolean grantPermissionToRole(String roleCode, String permissionCode) {
    // 1. 查找角色
    Map<String, Object> roleCriteria = new HashMap<>();
    roleCriteria.put("code", roleCode);
    List<Map<String, Object>> roles = dataService.findByCriteria("roles", roleCriteria);

    if (roles.isEmpty()) {
      // 如果角色不存在，创建角色
      Map<String, Object> newRole = new HashMap<>();
      newRole.put("id", UUID.randomUUID().toString());
      newRole.put("code", roleCode);
      newRole.put("name", roleCode); // 默认使用 code 作为名称
      newRole.put("description", "Auto-created role");
      newRole.put("is_system", false);
      newRole.put("is_active", true);
      newRole.put("created_at", new Date());
      dataService.save("roles", newRole);
      roles = Collections.singletonList(newRole);
    }

    String roleId = (String) roles.get(0).get("id");

    // 2. 查找权限
    Map<String, Object> permCriteria = new HashMap<>();
    permCriteria.put("code", permissionCode);
    List<Map<String, Object>> permissions = dataService.findByCriteria("permissions", permCriteria);

    if (permissions.isEmpty()) {
      // 权限不存在
      return false;
    }

    String permissionId = (String) permissions.get(0).get("id");

    // 3. 检查是否已经存在关联
    Map<String, Object> linkCriteria = new HashMap<>();
    linkCriteria.put("role_id", roleId);
    linkCriteria.put("permission_id", permissionId);
    List<Map<String, Object>> existingLinks = dataService.findByCriteria("role_permissions", linkCriteria);

    if (!existingLinks.isEmpty()) {
      // 关联已存在
      return false;
    }

    // 4. 创建角色权限关联
    Map<String, Object> rolePermission = new HashMap<>();
    rolePermission.put("id", UUID.randomUUID().toString());
    rolePermission.put("role_id", roleId);
    rolePermission.put("permission_id", permissionId);
    rolePermission.put("granted_at", new Date());
    dataService.save("role_permissions", rolePermission);

    return true;
  }
}