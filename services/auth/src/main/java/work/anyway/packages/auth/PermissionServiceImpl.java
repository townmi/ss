package work.anyway.packages.auth;

import work.anyway.interfaces.auth.PermissionService;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PermissionServiceImpl implements PermissionService {

  private final ConcurrentHashMap<String, Set<String>> perms = new ConcurrentHashMap<>();

  @Override
  public boolean hasPermission(String userId, String permission) {
    return perms.getOrDefault(userId, Set.of()).contains(permission);
  }

  @Override
  public void grantPermission(String userId, String permission) {
    perms.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(permission);
  }

  @Override
  public void revokePermission(String userId, String permission) {
    perms.getOrDefault(userId, Set.of()).remove(permission);
  }

  @Override
  public Set<String> getUserPermissions(String userId) {
    return Set.copyOf(perms.getOrDefault(userId, Set.of()));
  }
}