package work.anyway.interfaces.auth;

public interface PermissionService {
  boolean hasPermission(String userId, String permission);

  void grantPermission(String userId, String permission);

  void revokePermission(String userId, String permission);
}