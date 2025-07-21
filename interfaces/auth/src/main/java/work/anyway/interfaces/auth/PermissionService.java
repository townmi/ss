package work.anyway.interfaces.auth;

import java.util.Set;

public interface PermissionService {
  boolean hasPermission(String userId, String permission);

  void grantPermission(String userId, String permission);

  void revokePermission(String userId, String permission);

  Set<String> getUserPermissions(String userId);
}