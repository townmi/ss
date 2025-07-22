package work.anyway.interfaces.auth;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 登录安全服务接口
 * 负责登录尝试限制、风险评估等安全功能
 * 
 * @author 作者名
 * @since 1.0.0
 */
public interface LoginSecurityService {

  /**
   * 检查登录尝试是否被限制
   * 
   * @param identifier 登录标识符
   * @param clientIp   客户端IP
   * @return 限制结果
   */
  LoginAttemptResult checkLoginAttempt(String identifier, String clientIp);

  /**
   * 记录登录失败尝试
   * 
   * @param identifier     登录标识符
   * @param identifierType 标识符类型
   * @param clientIp       客户端IP
   * @param failureReason  失败原因
   * @return 是否记录成功
   */
  boolean recordFailedAttempt(String identifier, String identifierType,
      String clientIp, String failureReason);

  /**
   * 清除登录失败记录
   * 
   * @param identifier 登录标识符
   * @param clientIp   客户端IP（可选，为null时清除所有IP的记录）
   * @return 是否清除成功
   */
  boolean clearFailedAttempts(String identifier, String clientIp);

  /**
   * 获取登录尝试记录
   * 
   * @param identifier 登录标识符
   * @param clientIp   客户端IP
   * @return 登录尝试记录
   */
  Optional<LoginAttempt> getLoginAttempt(String identifier, String clientIp);

  /**
   * 手动锁定账户
   * 
   * @param identifier          登录标识符
   * @param lockDurationMinutes 锁定时长（分钟）
   * @param reason              锁定原因
   * @return 是否锁定成功
   */
  boolean lockAccount(String identifier, int lockDurationMinutes, String reason);

  /**
   * 手动解锁账户
   * 
   * @param identifier  登录标识符
   * @param adminUserId 管理员用户ID
   * @return 是否解锁成功
   */
  boolean unlockAccount(String identifier, String adminUserId);

  /**
   * 评估登录风险
   * 
   * @param identifier 登录标识符
   * @param clientIp   客户端IP
   * @param userAgent  用户代理
   * @return 风险评分 (0-100)
   */
  int assessLoginRisk(String identifier, String clientIp, String userAgent);

  /**
   * 获取被锁定的账户列表
   * 
   * @return 被锁定的账户列表
   */
  List<LoginAttempt> getLockedAccounts();

  /**
   * 检查IP是否在黑名单中
   * 
   * @param clientIp 客户端IP
   * @return 是否在黑名单中
   */
  boolean isIpBlacklisted(String clientIp);

  /**
   * 将IP加入黑名单
   * 
   * @param clientIp        客户端IP
   * @param reason          封禁原因
   * @param durationMinutes 封禁时长（分钟，0表示永久）
   * @param adminUserId     管理员用户ID
   * @return 是否成功
   */
  boolean blacklistIp(String clientIp, String reason, int durationMinutes, String adminUserId);

  /**
   * 从黑名单移除IP
   * 
   * @param clientIp    客户端IP
   * @param adminUserId 管理员用户ID
   * @return 是否成功
   */
  boolean removeIpFromBlacklist(String clientIp, String adminUserId);

  /**
   * 清理过期的登录尝试记录
   * 
   * @param hoursToKeep 保留小时数
   * @return 清理的记录数
   */
  long cleanExpiredAttempts(int hoursToKeep);
}