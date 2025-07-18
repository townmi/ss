package work.anyway.api.user;

public interface UserService {
  Object getUserById(String userId);

  String createUser(Object userInfo);

  Object getAllUsers();
}