package work.anyway.interfaces.user;

public interface UserService {
  Object getUserById(String userId);

  String createUser(Object userInfo);

  Object getAllUsers();
}