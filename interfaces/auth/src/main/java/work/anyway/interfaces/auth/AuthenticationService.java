package work.anyway.interfaces.auth;

import java.util.Optional;

/**
 * 认证服务接口
 * 提供统一的用户认证入口，支持多种登录方式
 * 
 * @author 作者名
 * @since 1.0.0
 */
public interface AuthenticationService {

  /**
   * 认证结果类
   */
  @lombok.Data
  @lombok.AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
  public static class AuthResult {
    private final boolean success;
    private final String userId;
    private final String accountId;
    private final String accountType;
    private final String message;

    public static AuthResult success(String userId, String accountId, String accountType) {
      return new AuthResult(true, userId, accountId, accountType, "Authentication successful");
    }

    public static AuthResult failure(String message) {
      return new AuthResult(false, null, null, null, message);
    }
  }

  /**
   * 邮箱密码认证
   * 
   * @param email    邮箱地址
   * @param password 密码
   * @return 认证结果
   */
  AuthResult authenticateByEmail(String email, String password);

  /**
   * 手机号密码认证
   * 
   * @param phone    手机号
   * @param password 密码
   * @return 认证结果
   */
  AuthResult authenticateByPhone(String phone, String password);

  /**
   * Google账户认证
   * 
   * @param googleToken Google OAuth token
   * @return 认证结果
   */
  AuthResult authenticateByGoogle(String googleToken);

  /**
   * 微信账户认证
   * 
   * @param wechatCode 微信授权码
   * @return 认证结果
   */
  AuthResult authenticateByWechat(String wechatCode);

  /**
   * GitHub账户认证
   * 
   * @param githubToken GitHub OAuth token
   * @return 认证结果
   */
  AuthResult authenticateByGithub(String githubToken);

  /**
   * 通用认证方法
   * 
   * @param identifier  标识符（邮箱、手机号、第三方ID等）
   * @param credentials 凭证（密码、token等）
   * @param accountType 账户类型
   * @return 认证结果
   */
  AuthResult authenticate(String identifier, String credentials, String accountType);

  /**
   * 验证用户状态
   * 
   * @param userId 用户ID
   * @return 用户是否可以正常使用系统
   */
  boolean validateUserStatus(String userId);

  /**
   * 记录登录成功
   * 
   * @param userId    用户ID
   * @param accountId 账户ID
   * @param loginIp   登录IP
   */
  void recordSuccessfulLogin(String userId, String accountId, String loginIp);

  /**
   * 记录登录失败
   * 
   * @param identifier 标识符
   * @param reason     失败原因
   * @param loginIp    登录IP
   */
  void recordFailedLogin(String identifier, String reason, String loginIp);

  /**
   * 检查登录尝试限制
   * 
   * @param identifier 标识符（邮箱、手机号等）
   * @return 是否超过登录尝试限制
   */
  boolean isLoginAttemptLimited(String identifier);

  /**
   * 清除登录失败记录
   * 
   * @param identifier 标识符
   */
  void clearFailedLoginAttempts(String identifier);
}