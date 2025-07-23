package work.anyway.packages.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import work.anyway.interfaces.auth.*;
import work.anyway.interfaces.data.DataService;
import work.anyway.interfaces.data.TypedDataService;
import work.anyway.interfaces.data.Repository;
import work.anyway.interfaces.data.QueryCriteria;

import java.util.*;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 权限服务实现
 * 使用 DataService 存储权限数据
 */
@Service
public class PermissionServiceImpl implements PermissionService {

  private static final Logger LOG = LoggerFactory.getLogger(PermissionServiceImpl.class);
  private static final String PERMISSIONS_COLLECTION = "user_permissions";

  @Autowired
  private DataService dataService;

  // 强类型 Repository
  private final Repository<Role> roleRepository;
  private final Repository<Permission> permissionRepository;
  private final Repository<RolePermission> rolePermissionRepository;
  private final Repository<UserPermission> userPermissionRepository;
  private final Repository<UserRole> userRoleRepository;
  private final Repository<PermissionAuditLog> auditLogRepository;

  @Autowired
  public PermissionServiceImpl(
      DataService dataService,
      @Qualifier("enhancedDataService") TypedDataService typedDataService) {
    this.dataService = dataService;

    // 初始化强类型 Repository
    this.roleRepository = typedDataService.getRepository("roles", Role.class);
    this.permissionRepository = typedDataService.getRepository("permissions", Permission.class);
    this.rolePermissionRepository = typedDataService.getRepository("role_permissions", RolePermission.class);
    this.userPermissionRepository = typedDataService.getRepository("user_permissions", UserPermission.class);
    this.userRoleRepository = typedDataService.getRepository("user_roles", UserRole.class);
    this.auditLogRepository = typedDataService.getRepository("permission_audit_logs", PermissionAuditLog.class);
  }

  @Override
  public boolean hasPermission(String userId, String permission) {
    Set<String> permissions = getUserPermissions(userId);
    return permissions.contains(permission);
  }

  @Override
  public void grantPermission(String userId, String permission) {
    // 查找权限ID
    String permissionId = findPermissionId(permission);
    if (permissionId == null) {
      LOG.warn("Permission not found: {}", permission);
      return;
    }

    // 检查是否已存在
    List<UserPermission> existing = userPermissionRepository.findBy(
        QueryCriteria.<UserPermission>create()
            .eq("userId", userId)
            .eq("permissionId", permissionId));

    if (!existing.isEmpty()) {
      LOG.debug("User permission already exists: user={}, permission={}", userId, permission);
      return;
    }

    // 创建新的用户权限
    UserPermission userPermission = UserPermission.builder()
        .userId(userId)
        .permissionId(permissionId)
        .grantedAt(new Date())
        .build();

    try {
      userPermissionRepository.save(userPermission);
      LOG.info("Granted permission {} to user {}", permission, userId);

      // 记录审计日志
      auditLogRepository.save(
          PermissionAuditLog.success(
              PermissionAuditLog.Action.GRANT,
              PermissionAuditLog.TargetType.USER_PERMISSION,
              userPermission.getId(),
              "system", // TODO: 获取当前操作者
              "Direct permission grant"));
    } catch (Exception e) {
      LOG.error("Failed to grant permission {} to user {}", permission, userId, e);

      // 记录失败审计日志
      auditLogRepository.save(
          PermissionAuditLog.failure(
              PermissionAuditLog.Action.GRANT,
              PermissionAuditLog.TargetType.USER_PERMISSION,
              userId + ":" + permissionId,
              "system",
              e.getMessage()));
    }
  }

  @Override
  public void revokePermission(String userId, String permission) {
    // 查找权限ID
    String permissionId = findPermissionId(permission);
    if (permissionId == null) {
      LOG.warn("Permission not found: {}", permission);
      return;
    }

    // 查找用户权限
    List<UserPermission> existing = userPermissionRepository.findBy(
        QueryCriteria.<UserPermission>create()
            .eq("userId", userId)
            .eq("permissionId", permissionId));

    if (existing.isEmpty()) {
      LOG.debug("User permission not found: user={}, permission={}", userId, permission);
      return;
    }

    // 删除权限
    UserPermission userPermission = existing.get(0);
    try {
      userPermissionRepository.delete(userPermission.getId());
      LOG.info("Revoked permission {} from user {}", permission, userId);

      // 记录审计日志
      auditLogRepository.save(
          PermissionAuditLog.success(
              PermissionAuditLog.Action.REVOKE,
              PermissionAuditLog.TargetType.USER_PERMISSION,
              userPermission.getId(),
              "system", // TODO: 获取当前操作者
              "Direct permission revoke"));
    } catch (Exception e) {
      LOG.error("Failed to revoke permission {} from user {}", permission, userId, e);

      // 记录失败审计日志
      auditLogRepository.save(
          PermissionAuditLog.failure(
              PermissionAuditLog.Action.REVOKE,
              PermissionAuditLog.TargetType.USER_PERMISSION,
              userPermission.getId(),
              "system",
              e.getMessage()));
    }
  }

  @Override
  public Set<String> getUserPermissions(String userId) {
    Set<String> allPermissions = new HashSet<>();

    // 1. 获取直接授予的权限
    List<UserPermission> directPermissions = userPermissionRepository.findBy(
        QueryCriteria.<UserPermission>create().eq("userId", userId));

    // 过滤未过期的权限
    Set<String> directPermissionIds = directPermissions.stream()
        .filter(up -> !up.isExpired())
        .map(UserPermission::getPermissionId)
        .collect(Collectors.toSet());

    // 2. 获取用户角色
    List<UserRole> userRoles = userRoleRepository.findBy(
        QueryCriteria.<UserRole>create().eq("userId", userId));

    // 过滤未过期的角色
    Set<String> roleIds = userRoles.stream()
        .filter(ur -> !ur.isExpired())
        .map(UserRole::getRoleId)
        .collect(Collectors.toSet());

    // 3. 获取角色权限
    Set<String> rolePermissionIds = new HashSet<>();
    for (String roleId : roleIds) {
      List<RolePermission> rolePermissions = rolePermissionRepository.findBy(
          QueryCriteria.<RolePermission>create().eq("roleId", roleId));

      rolePermissionIds.addAll(
          rolePermissions.stream()
              .map(RolePermission::getPermissionId)
              .collect(Collectors.toSet()));
    }

    // 4. 合并所有权限ID
    Set<String> allPermissionIds = new HashSet<>();
    allPermissionIds.addAll(directPermissionIds);
    allPermissionIds.addAll(rolePermissionIds);

    // 5. 转换为权限代码
    for (String permissionId : allPermissionIds) {
      Optional<Permission> permissionOpt = permissionRepository.findById(permissionId);
      if (permissionOpt.isPresent() && permissionOpt.get().getIsActive()) {
        allPermissions.add(permissionOpt.get().getCode());
      }
    }

    return allPermissions;
  }

  @Override
  public boolean registerPermission(String code, String name, String description) {
    // 使用强类型查询
    List<Permission> existing = permissionRepository.findBy(
        QueryCriteria.<Permission>create().eq("code", code));

    if (existing.isEmpty()) {
      // 创建新权限
      Permission permission = Permission.builder()
          .code(code)
          .name(name)
          .description(description)
          .isActive(true)
          .build();

      permissionRepository.save(permission);
      return true; // 新创建
    } else {
      // 更新现有权限
      Permission permission = existing.get(0);
      permission.setName(name);
      permission.setDescription(description);

      permissionRepository.update(permission);
      return false; // 已存在，更新
    }
  }

  @Override
  public boolean grantPermissionToRole(String roleCode, String permissionCode) {
    try {
      // 1. 查找或创建角色
      String roleId = ensureRoleExists(roleCode);
      if (roleId == null) {
        LOG.error("Failed to ensure role exists: {}", roleCode);
        return false;
      }

      // 2. 查找权限
      String permissionId = findPermissionId(permissionCode);
      if (permissionId == null) {
        LOG.warn("Permission not found: {}", permissionCode);
        return false;
      }

      // 3. 授予权限（使用强类型）
      return grantRolePermissionWithTypedRepository(roleId, permissionId);
    } catch (Exception e) {
      LOG.error("Unexpected error granting permission {} to role {}", permissionCode, roleCode, e);
      return false;
    }
  }

  @Override
  public List<Permission> getAllPermissions() {
    try {
      // 获取所有激活的权限
      List<Permission> permissions = permissionRepository.findBy(
          QueryCriteria.<Permission>create().eq("isActive", true));

      // 按 code 排序
      permissions.sort((p1, p2) -> p1.getCode().compareTo(p2.getCode()));

      LOG.debug("Retrieved {} active permissions from database", permissions.size());
      return permissions;
    } catch (Exception e) {
      LOG.error("Failed to retrieve all permissions", e);
      return new ArrayList<>();
    }
  }

  /**
   * 使用强类型 Repository 授予角色权限
   */
  private boolean grantRolePermissionWithTypedRepository(String roleId, String permissionId) {
    // 检查是否已存在
    List<RolePermission> existing = rolePermissionRepository.findBy(
        QueryCriteria.<RolePermission>create()
            .eq("roleId", roleId)
            .eq("permissionId", permissionId));

    if (!existing.isEmpty()) {
      LOG.trace("Role permission already exists: role={}, permission={}", roleId, permissionId);
      return false;
    }

    // 创建新的关联
    try {
      RolePermission rolePermission = RolePermission.builder()
          .roleId(roleId)
          .permissionId(permissionId)
          .grantedAt(new Date())
          .build();

      rolePermissionRepository.save(rolePermission);
      LOG.debug("Role permission granted successfully: role={}, permission={}", roleId, permissionId);
      return true;

    } catch (Exception e) {
      if (isDuplicateKeyError(e)) {
        LOG.trace("Role permission already exists (concurrent): role={}, permission={}", roleId, permissionId);
        return false;
      }

      LOG.error("Failed to grant role permission: role={}, permission={}", roleId, permissionId, e);
      return false;
    }
  }

  /**
   * 批量授予角色权限，优化性能和避免并发冲突
   * 
   * @param rolePermissions Map<roleCode, List<permissionCode>>
   * @return 成功授予的权限数量
   */
  public int batchGrantPermissionsToRoles(Map<String, List<String>> rolePermissions) {
    int totalGranted = 0;

    // 1. 预先加载所有角色和权限
    Map<String, Role> roleMap = loadRoles(rolePermissions.keySet());
    Map<String, Permission> permissionMap = loadPermissions(
        rolePermissions.values().stream()
            .flatMap(List::stream)
            .collect(Collectors.toSet()));

    // 2. 收集所有需要创建的角色权限关系
    List<RolePermission> toProcess = new ArrayList<>();

    for (Map.Entry<String, List<String>> entry : rolePermissions.entrySet()) {
      String roleCode = entry.getKey();
      Role role = roleMap.get(roleCode);
      if (role == null) {
        // 创建角色
        role = createRole(roleCode);
        if (role == null)
          continue;
        roleMap.put(roleCode, role);
      }

      for (String permissionCode : entry.getValue()) {
        Permission permission = permissionMap.get(permissionCode);
        if (permission == null)
          continue;

        RolePermission rp = RolePermission.builder()
            .roleId(role.getId())
            .permissionId(permission.getId())
            .grantedAt(new Date())
            .build();
        toProcess.add(rp);
      }
    }

    // 3. 使用优化的批量保存策略
    totalGranted = optimizedBatchSaveWithTypedRepository(toProcess);

    LOG.info("Batch granted {} role permissions out of {} requested",
        totalGranted, toProcess.size());

    return totalGranted;
  }

  /**
   * 批量加载角色
   */
  private Map<String, Role> loadRoles(Set<String> roleCodes) {
    Map<String, Role> roleMap = new HashMap<>();

    for (String code : roleCodes) {
      List<Role> roles = roleRepository.findBy(
          QueryCriteria.<Role>create().eq("code", code));
      if (!roles.isEmpty()) {
        roleMap.put(code, roles.get(0));
      }
    }

    return roleMap;
  }

  /**
   * 批量加载权限
   */
  private Map<String, Permission> loadPermissions(Set<String> permissionCodes) {
    Map<String, Permission> permissionMap = new HashMap<>();

    for (String code : permissionCodes) {
      List<Permission> permissions = permissionRepository.findBy(
          QueryCriteria.<Permission>create().eq("code", code));
      if (!permissions.isEmpty()) {
        permissionMap.put(code, permissions.get(0));
      }
    }

    return permissionMap;
  }

  /**
   * 创建角色
   */
  private Role createRole(String roleCode) {
    try {
      Role role = Role.builder()
          .code(roleCode)
          .name(roleCode)
          .description("Auto-created role")
          .isSystem(false)
          .isActive(true)
          .build();

      return roleRepository.save(role);
    } catch (Exception e) {
      // 可能是并发创建，尝试再次查询
      List<Role> roles = roleRepository.findBy(
          QueryCriteria.<Role>create().eq("code", roleCode));
      if (!roles.isEmpty()) {
        return roles.get(0);
      }
      LOG.error("Failed to create role: {}", roleCode, e);
      return null;
    }
  }

  /**
   * 使用强类型 Repository 的优化批量保存
   */
  private int optimizedBatchSaveWithTypedRepository(List<RolePermission> rolePermissions) {
    if (rolePermissions.isEmpty()) {
      return 0;
    }

    // 1. 批量查询已存在的关系
    Set<String> existingKeys = new HashSet<>();

    // 按角色分组查询
    Map<String, List<RolePermission>> byRole = rolePermissions.stream()
        .collect(Collectors.groupingBy(RolePermission::getRoleId));

    for (Map.Entry<String, List<RolePermission>> entry : byRole.entrySet()) {
      String roleId = entry.getKey();

      List<RolePermission> existing = rolePermissionRepository.findBy(
          QueryCriteria.<RolePermission>create().eq("roleId", roleId));

      for (RolePermission rp : existing) {
        existingKeys.add(rp.getUniqueKey());
      }
    }

    // 2. 过滤出真正需要创建的
    List<RolePermission> toCreate = rolePermissions.stream()
        .filter(rp -> !existingKeys.contains(rp.getUniqueKey()))
        .collect(Collectors.toList());

    LOG.debug("Found {} existing relations, {} to create",
        existingKeys.size(), toCreate.size());

    // 3. 批量创建
    int created = 0;
    if (!toCreate.isEmpty()) {
      created = batchSaveRolePermissions(toCreate);
    }

    return created;
  }

  /**
   * 安全地保存单个角色权限
   */
  private boolean saveRolePermissionSafely(RolePermission rolePermission) {
    try {
      rolePermissionRepository.save(rolePermission);
      LOG.trace("Created role permission: {}", rolePermission.getUniqueKey());
      return true;
    } catch (Exception e) {
      if (isDuplicateKeyError(e)) {
        LOG.trace("Role permission already exists (concurrent): {}", rolePermission.getUniqueKey());
      } else {
        LOG.error("Failed to create role permission: {}", rolePermission.getUniqueKey(), e);
      }
      return false;
    }
  }

  /**
   * 内部类：角色权限对
   */
  private static class RolePermissionPair {
    final String roleId;
    final String permissionId;

    RolePermissionPair(String roleId, String permissionId) {
      this.roleId = roleId;
      this.permissionId = permissionId;
    }

    String getKey() {
      return roleId + ":" + permissionId;
    }
  }

  /**
   * 确保角色存在，如果不存在则创建
   * 
   * @return 角色ID，失败返回null
   */
  private String ensureRoleExists(String roleCode) {
    List<Role> roles = roleRepository.findBy(
        QueryCriteria.<Role>create().eq("code", roleCode));

    if (!roles.isEmpty()) {
      return roles.get(0).getId();
    }

    // 创建新角色
    Role role = createRole(roleCode);
    return role != null ? role.getId() : null;
  }

  /**
   * 查找权限ID
   */
  private String findPermissionId(String permissionCode) {
    List<Permission> permissions = permissionRepository.findBy(
        QueryCriteria.<Permission>create().eq("code", permissionCode));

    if (!permissions.isEmpty()) {
      return permissions.get(0).getId();
    }
    return null;
  }

  /**
   * 检查是否是重复键错误
   */
  private boolean isDuplicateKeyError(Exception e) {
    Throwable cause = e;

    // 解包异常
    while (cause != null) {
      String message = cause.getMessage();
      if (message != null &&
          (message.contains("Duplicate entry") ||
              message.contains("unique_role_permission") ||
              message.contains("errorCode=1062"))) {
        return true;
      }
      cause = cause.getCause();
    }

    return false;
  }

  /**
   * 为用户分配角色
   * 
   * @param userId    用户ID
   * @param roleCode  角色代码
   * @param reason    分配原因
   * @param expiresAt 过期时间（可选）
   * @return 成功返回 true
   */
  public boolean assignRoleToUser(String userId, String roleCode, String reason, Date expiresAt) {
    // 查找角色
    List<Role> roles = roleRepository.findBy(
        QueryCriteria.<Role>create().eq("code", roleCode));

    if (roles.isEmpty()) {
      LOG.warn("Role not found: {}", roleCode);
      return false;
    }

    Role role = roles.get(0);

    // 检查是否已分配
    List<UserRole> existing = userRoleRepository.findBy(
        QueryCriteria.<UserRole>create()
            .eq("userId", userId)
            .eq("roleId", role.getId()));

    if (!existing.isEmpty()) {
      UserRole userRole = existing.get(0);
      if (!userRole.isExpired()) {
        LOG.debug("User role already assigned and not expired: user={}, role={}", userId, roleCode);
        return false;
      }
      // 如果已过期，删除旧记录
      userRoleRepository.delete(userRole.getId());
    }

    // 创建新的用户角色关联
    UserRole userRole = UserRole.builder()
        .userId(userId)
        .roleId(role.getId())
        .assignedAt(new Date())
        .assignedBy("system") // TODO: 获取当前操作者
        .reason(reason)
        .expiresAt(expiresAt)
        .build();

    try {
      userRoleRepository.save(userRole);
      LOG.info("Assigned role {} to user {}", roleCode, userId);

      // 记录审计日志
      auditLogRepository.save(
          PermissionAuditLog.success(
              PermissionAuditLog.Action.ROLE_ASSIGN,
              PermissionAuditLog.TargetType.USER_ROLE,
              userRole.getId(),
              "system",
              reason));
      return true;
    } catch (Exception e) {
      LOG.error("Failed to assign role {} to user {}", roleCode, userId, e);

      // 记录失败审计日志
      auditLogRepository.save(
          PermissionAuditLog.failure(
              PermissionAuditLog.Action.ROLE_ASSIGN,
              PermissionAuditLog.TargetType.USER_ROLE,
              userId + ":" + role.getId(),
              "system",
              e.getMessage()));
      return false;
    }
  }

  /**
   * 移除用户角色
   * 
   * @param userId   用户ID
   * @param roleCode 角色代码
   * @param reason   移除原因
   * @return 成功返回 true
   */
  public boolean removeRoleFromUser(String userId, String roleCode, String reason) {
    // 查找角色
    List<Role> roles = roleRepository.findBy(
        QueryCriteria.<Role>create().eq("code", roleCode));

    if (roles.isEmpty()) {
      LOG.warn("Role not found: {}", roleCode);
      return false;
    }

    Role role = roles.get(0);

    // 查找用户角色关联
    List<UserRole> existing = userRoleRepository.findBy(
        QueryCriteria.<UserRole>create()
            .eq("userId", userId)
            .eq("roleId", role.getId()));

    if (existing.isEmpty()) {
      LOG.debug("User role not found: user={}, role={}", userId, roleCode);
      return false;
    }

    UserRole userRole = existing.get(0);

    try {
      userRoleRepository.delete(userRole.getId());
      LOG.info("Removed role {} from user {}", roleCode, userId);

      // 记录审计日志
      auditLogRepository.save(
          PermissionAuditLog.success(
              PermissionAuditLog.Action.ROLE_REMOVE,
              PermissionAuditLog.TargetType.USER_ROLE,
              userRole.getId(),
              "system",
              reason));
      return true;
    } catch (Exception e) {
      LOG.error("Failed to remove role {} from user {}", roleCode, userId, e);

      // 记录失败审计日志
      auditLogRepository.save(
          PermissionAuditLog.failure(
              PermissionAuditLog.Action.ROLE_REMOVE,
              PermissionAuditLog.TargetType.USER_ROLE,
              userRole.getId(),
              "system",
              e.getMessage()));
      return false;
    }
  }

  /**
   * 获取用户的所有角色
   * 
   * @param userId 用户ID
   * @return 角色代码列表
   */
  public List<String> getUserRoles(String userId) {
    List<UserRole> userRoles = userRoleRepository.findBy(
        QueryCriteria.<UserRole>create().eq("userId", userId));

    List<String> roleCodes = new ArrayList<>();

    for (UserRole userRole : userRoles) {
      if (!userRole.isExpired()) {
        Optional<Role> roleOpt = roleRepository.findById(userRole.getRoleId());
        if (roleOpt.isPresent() && roleOpt.get().getIsActive()) {
          roleCodes.add(roleOpt.get().getCode());
        }
      }
    }

    return roleCodes;
  }

  /**
   * 批量保存角色权限（支持 TypedRepository 的批量操作）
   * 
   * @param rolePermissions 角色权限列表
   * @return 成功保存的数量
   */
  public int batchSaveRolePermissions(List<RolePermission> rolePermissions) {
    if (rolePermissions.isEmpty()) {
      return 0;
    }

    try {
      // 如果 Repository 支持批量保存
      return rolePermissionRepository.batchSave(rolePermissions);
    } catch (UnsupportedOperationException e) {
      // 降级到单个保存
      LOG.debug("Batch save not supported, falling back to individual saves");
      int saved = 0;
      for (RolePermission rp : rolePermissions) {
        if (saveRolePermissionSafely(rp)) {
          saved++;
        }
      }
      return saved;
    } catch (Exception e) {
      LOG.error("Batch save failed", e);
      return 0;
    }
  }
}