package work.anyway.interfaces.auth;

/**
 * 安全服务接口
 * 提供密码加密、验证码生成、数据验证等安全相关功能
 * 
 * @author 作者名
 * @since 1.0.0
 */
public interface SecurityService {

  /**
   * 使用 BCrypt 加密密码
   * 
   * @param plainPassword 明文密码
   * @return 加密后的密码
   */
  String hashPassword(String plainPassword);

  /**
   * 验证密码是否匹配
   * 
   * @param plainPassword  明文密码
   * @param hashedPassword 加密后的密码
   * @return 是否匹配
   */
  boolean verifyPassword(String plainPassword, String hashedPassword);

  /**
   * 生成数字验证码
   * 
   * @param length 验证码长度
   * @return 数字验证码
   */
  String generateNumericCode(int length);

  /**
   * 生成字母数字验证码
   * 
   * @param length 验证码长度
   * @return 字母数字验证码
   */
  String generateAlphanumericCode(int length);

  /**
   * 生成随机字符串（用于token等）
   * 
   * @param length 长度
   * @return 随机字符串
   */
  String generateRandomString(int length);

  /**
   * 验证密码强度
   * 密码必须包含：
   * - 至少8位字符
   * - 至少1个小写字母
   * - 至少1个大写字母
   * - 至少1个数字
   * - 至少1个特殊字符
   * 
   * @param password 密码
   * @return 是否符合强度要求
   */
  boolean isStrongPassword(String password);

  /**
   * 验证邮箱格式
   * 
   * @param email 邮箱地址
   * @return 是否为有效邮箱格式
   */
  boolean isValidEmail(String email);

  /**
   * 验证用户名格式
   * 
   * @param username 用户名
   * @return 是否为有效用户名格式
   */
  boolean isValidUsername(String username);

  /**
   * 清理和标准化邮箱地址
   * 
   * @param email 邮箱地址
   * @return 清理后的邮箱地址
   */
  String sanitizeEmail(String email);

  /**
   * 清理和标准化用户名
   * 
   * @param username 用户名
   * @return 清理后的用户名
   */
  String sanitizeUsername(String username);

  /**
   * 生成用于密码重置的安全token
   * 
   * @return 安全token
   */
  String generatePasswordResetToken();

  /**
   * 生成用于邮箱验证的token
   * 
   * @return 验证token
   */
  String generateEmailVerificationToken();

  /**
   * 验证字符串是否为空或只包含空白字符
   * 
   * @param str 字符串
   * @return 是否为空白
   */
  boolean isBlank(String str);

  /**
   * 验证字符串长度是否在指定范围内
   * 
   * @param str       字符串
   * @param minLength 最小长度
   * @param maxLength 最大长度
   * @return 是否在范围内
   */
  boolean isValidLength(String str, int minLength, int maxLength);

  /**
   * 获取密码强度分数
   * 
   * @param password 密码
   * @return 强度分数 (0-100)
   */
  int getPasswordStrength(String password);

  /**
   * 获取密码强度描述
   * 
   * @param password 密码
   * @return 强度描述
   */
  String getPasswordStrengthDescription(String password);
}