package work.anyway.interfaces.auth;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 登录日志服务接口
 * 负责记录和查询登录日志
 * 
 * @author 作者名
 * @since 1.0.0
 */
public interface LoginLogService {

  /**
   * 记录登录成功日志
   * 
   * @param loginLog 登录日志信息
   * @return 日志ID
   */
  String recordSuccessfulLogin(LoginLog loginLog);

  /**
   * 记录登录失败日志
   * 
   * @param loginLog 登录日志信息
   * @return 日志ID
   */
  String recordFailedLogin(LoginLog loginLog);

  /**
   * 记录被阻止的登录尝试
   * 
   * @param loginLog 登录日志信息
   * @return 日志ID
   */
  String recordBlockedLogin(LoginLog loginLog);

  /**
   * 获取用户登录历史
   * 
   * @param userId 用户ID
   * @param limit  返回条数限制
   * @return 登录历史列表
   */
  List<LoginLog> getUserLoginHistory(String userId, int limit);

  /**
   * 获取IP登录历史
   * 
   * @param clientIp 客户端IP
   * @param hours    过去几小时内
   * @return 登录历史列表
   */
  List<LoginLog> getIpLoginHistory(String clientIp, int hours);

  /**
   * 获取标识符登录历史
   * 
   * @param identifier 登录标识符
   * @param hours      过去几小时内
   * @return 登录历史列表
   */
  List<LoginLog> getIdentifierLoginHistory(String identifier, int hours);

  /**
   * 获取高风险登录
   * 
   * @param hours        过去几小时内
   * @param minRiskScore 最小风险分数
   * @return 高风险登录列表
   */
  List<LoginLog> getHighRiskLogins(int hours, int minRiskScore);

  /**
   * 检测异常登录
   * 
   * @param userId 用户ID
   * @param hours  检测时间范围
   * @return 异常登录列表
   */
  List<LoginLog> detectAbnormalLogins(String userId, int hours);

  /**
   * 统计登录次数
   * 
   * @param hours  统计时间范围
   * @param status 登录状态（可选）
   * @return 登录统计信息
   */
  Map<String, Long> getLoginStatistics(int hours, String status);

  /**
   * 获取登录趋势数据
   * 
   * @param days 过去几天
   * @return 每日登录统计
   */
  Map<String, Map<String, Long>> getLoginTrends(int days);

  /**
   * 获取最活跃的IP地址
   * 
   * @param hours 统计时间范围
   * @param limit 返回数量限制
   * @return IP地址和登录次数
   */
  Map<String, Long> getTopActiveIps(int hours, int limit);

  /**
   * 获取最频繁的失败登录
   * 
   * @param hours 统计时间范围
   * @param limit 返回数量限制
   * @return 标识符和失败次数
   */
  Map<String, Long> getTopFailedLogins(int hours, int limit);

  /**
   * 清理过期日志
   * 
   * @param daysToKeep 保留天数
   * @return 清理的记录数
   */
  long cleanExpiredLogs(int daysToKeep);

  /**
   * 获取登录日志详情
   * 
   * @param logId 日志ID
   * @return 登录日志详情
   */
  LoginLog getLoginLogById(String logId);

  /**
   * 搜索登录日志
   * 
   * @param criteria 搜索条件
   * @param limit    返回数量限制
   * @return 匹配的登录日志
   */
  List<LoginLog> searchLoginLogs(Map<String, Object> criteria, int limit);
}