package work.anyway.interfaces.user;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 用户服务接口
 * 提供用户管理的所有业务操作
 */
public interface UserService {

  /**
   * 获取所有用户
   * 
   * @return 用户列表
   */
  List<Map<String, Object>> getAllUsers();

  /**
   * 根据ID获取用户
   * 
   * @param userId 用户ID
   * @return 用户信息（如果存在）
   */
  Optional<Map<String, Object>> getUserById(String userId);

  /**
   * 创建用户
   * 
   * @param userInfo 用户信息
   * @return 创建的用户信息（包含生成的ID）
   */
  Map<String, Object> createUser(Map<String, Object> userInfo);

  /**
   * 更新用户信息
   * 
   * @param userId   用户ID
   * @param userInfo 要更新的用户信息
   * @return 是否更新成功
   */
  boolean updateUser(String userId, Map<String, Object> userInfo);

  /**
   * 删除用户
   * 
   * @param userId 用户ID
   * @return 是否删除成功
   */
  boolean deleteUser(String userId);

  /**
   * 根据邮箱查找用户
   * 
   * @param email 邮箱地址
   * @return 用户信息（如果存在）
   */
  Optional<Map<String, Object>> findUserByEmail(String email);

  /**
   * 验证用户凭证
   * 
   * @param username 用户名或邮箱
   * @param password 密码
   * @return 验证成功返回用户信息，否则返回空
   */
  Optional<Map<String, Object>> authenticateUser(String username, String password);

  /**
   * 检查邮箱是否已存在
   * 
   * @param email 邮箱地址
   * @return 是否存在
   */
  boolean isEmailExists(String email);

  /**
   * 获取用户数量
   * 
   * @return 用户总数
   */
  long getUserCount();
}