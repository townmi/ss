package work.anyway.interfaces.user;

import java.util.List;
import java.util.Optional;

/**
 * 用户服务接口
 * 提供用户管理的所有业务操作
 * 
 * @author 作者名
 * @since 1.0.0
 */
public interface UserService {

  /**
   * 获取所有用户
   * 
   * @return 用户列表
   */
  List<User> getAllUsers();

  /**
   * 根据ID获取用户
   * 
   * @param userId 用户ID
   * @return 用户信息（如果存在）
   */
  Optional<User> getUserById(String userId);

  /**
   * 创建用户
   * 
   * @param user 用户信息
   * @return 创建的用户信息（包含生成的ID）
   */
  User createUser(User user);

  /**
   * 更新用户信息
   * 
   * @param userId 用户ID
   * @param user   要更新的用户信息
   * @return 是否更新成功
   */
  boolean updateUser(String userId, User user);

  /**
   * 删除用户
   * 
   * @param userId 用户ID
   * @return 是否删除成功
   */
  boolean deleteUser(String userId);

  /**
   * 根据邮箱查找用户（通过账户关联）
   * 
   * @param email 邮箱地址
   * @return 用户信息（如果存在）
   */
  Optional<User> findUserByEmail(String email);

  /**
   * 根据手机号查找用户
   * 
   * @param phone 手机号
   * @return 用户信息（如果存在）
   */
  Optional<User> findUserByPhone(String phone);

  /**
   * 检查邮箱是否已存在（通过账户检查）
   * 
   * @param email 邮箱地址
   * @return 是否存在
   */
  boolean isEmailExists(String email);

  /**
   * 检查手机号是否已存在
   * 
   * @param phone 手机号
   * @return 是否存在
   */
  boolean isPhoneExists(String phone);

  /**
   * 获取用户数量
   * 
   * @return 用户总数
   */
  long getUserCount();

  /**
   * 根据角色获取用户列表
   * 
   * @param role 用户角色
   * @return 该角色的用户列表
   */
  List<User> getUsersByRole(String role);

  /**
   * 根据状态获取用户列表
   * 
   * @param status 用户状态
   * @return 该状态的用户列表
   */
  List<User> getUsersByStatus(String status);

  /**
   * 根据部门获取用户列表
   * 
   * @param department 部门名称
   * @return 该部门的用户列表
   */
  List<User> getUsersByDepartment(String department);

  /**
   * 更新用户角色
   * 
   * @param userId 用户ID
   * @param role   新角色
   * @return 是否更新成功
   */
  boolean updateUserRole(String userId, String role);

  /**
   * 更新用户状态
   * 
   * @param userId 用户ID
   * @param status 新状态
   * @return 是否更新成功
   */
  boolean updateUserStatus(String userId, String status);

  /**
   * 更新最后登录时间
   * 
   * @param userId 用户ID
   * @return 是否更新成功
   */
  boolean updateLastLogin(String userId);

  /**
   * 批量创建用户
   * 
   * @param users 用户列表
   * @return 成功创建的用户数量
   */
  int batchCreateUsers(List<User> users);

  /**
   * 批量更新用户状态
   * 
   * @param userIds 用户ID列表
   * @param status  新状态
   * @return 成功更新的用户数量
   */
  int batchUpdateUserStatus(List<String> userIds, String status);

  /**
   * 搜索用户
   * 
   * @param keyword 关键词（姓名、邮箱、电话）
   * @return 匹配的用户列表
   */
  List<User> searchUsers(String keyword);

  /**
   * 检查用户是否为管理员
   * 
   * @param userId 用户ID
   * @return 是否为管理员
   */
  boolean isUserAdmin(String userId);

  /**
   * 检查用户是否激活
   * 
   * @param userId 用户ID
   * @return 是否激活
   */
  boolean isUserActive(String userId);
}