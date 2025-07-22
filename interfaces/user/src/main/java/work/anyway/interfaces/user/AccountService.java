package work.anyway.interfaces.user;

import java.util.List;
import java.util.Optional;

/**
 * 账户服务接口
 * 提供用户账户管理的所有业务操作
 * 
 * @author 作者名
 * @since 1.0.0
 */
public interface AccountService {

  /**
   * 创建用户账户
   * 
   * @param account 账户信息
   * @return 创建的账户信息（包含生成的ID）
   */
  UserAccount createAccount(UserAccount account);

  /**
   * 根据ID获取账户
   * 
   * @param accountId 账户ID
   * @return 账户信息（如果存在）
   */
  Optional<UserAccount> getAccountById(String accountId);

  /**
   * 根据标识符和类型查找账户
   * 
   * @param identifier  标识符（邮箱、第三方ID等）
   * @param accountType 账户类型
   * @return 账户信息（如果存在）
   */
  Optional<UserAccount> findAccount(String identifier, AccountType accountType);

  /**
   * 根据用户ID获取所有账户
   * 
   * @param userId 用户ID
   * @return 该用户的所有账户列表
   */
  List<UserAccount> getUserAccounts(String userId);

  /**
   * 获取用户的主账户
   * 
   * @param userId 用户ID
   * @return 主账户信息（如果存在）
   */
  Optional<UserAccount> getPrimaryAccount(String userId);

  /**
   * 获取用户的邮箱账户
   * 
   * @param userId 用户ID
   * @return 邮箱账户信息（如果存在）
   */
  Optional<UserAccount> getEmailAccount(String userId);

  /**
   * 验证账户凭证
   * 
   * @param identifier  标识符
   * @param credentials 凭证
   * @param accountType 账户类型
   * @return 是否验证成功
   */
  boolean verifyCredentials(String identifier, String credentials, AccountType accountType);

  /**
   * 更新账户信息
   * 
   * @param accountId 账户ID
   * @param account   要更新的账户信息
   * @return 是否更新成功
   */
  boolean updateAccount(String accountId, UserAccount account);

  /**
   * 更新账户凭证
   * 
   * @param accountId   账户ID
   * @param credentials 新的凭证信息
   * @return 是否更新成功
   */
  boolean updateCredentials(String accountId, String credentials);

  /**
   * 设置账户验证状态
   * 
   * @param accountId 账户ID
   * @param verified  是否已验证
   * @return 是否更新成功
   */
  boolean setVerificationStatus(String accountId, boolean verified);

  /**
   * 设置主账户
   * 
   * @param userId    用户ID
   * @param accountId 要设为主账户的账户ID
   * @return 是否设置成功
   */
  boolean setPrimaryAccount(String userId, String accountId);

  /**
   * 记录登录时间
   * 
   * @param accountId 账户ID
   * @return 是否记录成功
   */
  boolean recordLogin(String accountId);

  /**
   * 删除账户
   * 
   * @param accountId 账户ID
   * @return 是否删除成功
   */
  boolean deleteAccount(String accountId);

  /**
   * 检查标识符是否已存在
   * 
   * @param identifier  标识符
   * @param accountType 账户类型
   * @return 是否已存在
   */
  boolean isIdentifierExists(String identifier, AccountType accountType);

  /**
   * 检查用户是否有指定类型的账户
   * 
   * @param userId      用户ID
   * @param accountType 账户类型
   * @return 是否存在该类型账户
   */
  boolean hasAccountType(String userId, AccountType accountType);

  /**
   * 获取账户总数
   * 
   * @return 账户总数
   */
  long getAccountCount();

  /**
   * 根据类型获取账户数量
   * 
   * @param accountType 账户类型
   * @return 该类型的账户数量
   */
  long getAccountCountByType(AccountType accountType);
}