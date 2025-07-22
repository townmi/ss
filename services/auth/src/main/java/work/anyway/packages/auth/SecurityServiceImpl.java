package work.anyway.packages.auth;

import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import work.anyway.interfaces.auth.SecurityService;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * 安全服务实现
 * 提供密码加密、验证码生成、数据验证等安全相关功能
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Service
public class SecurityServiceImpl implements SecurityService {

  private static final Logger LOG = LoggerFactory.getLogger(SecurityServiceImpl.class);

  // 密码强度正则表达式
  private static final Pattern PASSWORD_PATTERN = Pattern.compile(
      "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$");

  // 邮箱格式正则表达式
  private static final Pattern EMAIL_PATTERN = Pattern.compile(
      "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

  // 用户名格式正则表达式（字母、数字、下划线，3-20位）
  private static final Pattern USERNAME_PATTERN = Pattern.compile(
      "^[a-zA-Z0-9_]{3,20}$");

  private final SecureRandom secureRandom = new SecureRandom();

  // 验证码字符集
  private static final String VERIFICATION_CODE_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private static final String NUMERIC_CODE_CHARS = "0123456789";

  @Override
  public String hashPassword(String plainPassword) {
    try {
      String hashedPassword = BCrypt.hashpw(plainPassword, BCrypt.gensalt(12));
      LOG.debug("Password hashed successfully");
      return hashedPassword;
    } catch (Exception e) {
      LOG.error("Failed to hash password", e);
      throw new RuntimeException("Password hashing failed", e);
    }
  }

  @Override
  public boolean verifyPassword(String plainPassword, String hashedPassword) {
    try {
      boolean matches = BCrypt.checkpw(plainPassword, hashedPassword);
      LOG.debug("Password verification result: {}", matches);
      return matches;
    } catch (Exception e) {
      LOG.error("Failed to verify password", e);
      return false;
    }
  }

  @Override
  public String generateNumericCode(int length) {
    return generateCode(NUMERIC_CODE_CHARS, length);
  }

  @Override
  public String generateAlphanumericCode(int length) {
    return generateCode(VERIFICATION_CODE_CHARS, length);
  }

  @Override
  public String generateRandomString(int length) {
    String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    return generateCode(chars, length);
  }

  @Override
  public boolean isStrongPassword(String password) {
    if (password == null || password.trim().isEmpty()) {
      return false;
    }
    return PASSWORD_PATTERN.matcher(password).matches();
  }

  @Override
  public boolean isValidEmail(String email) {
    if (email == null || email.trim().isEmpty()) {
      return false;
    }
    return EMAIL_PATTERN.matcher(email.trim()).matches();
  }

  @Override
  public boolean isValidUsername(String username) {
    if (username == null || username.trim().isEmpty()) {
      return false;
    }
    return USERNAME_PATTERN.matcher(username.trim()).matches();
  }

  @Override
  public String sanitizeEmail(String email) {
    if (email == null) {
      return null;
    }
    return email.trim().toLowerCase();
  }

  @Override
  public String sanitizeUsername(String username) {
    if (username == null) {
      return null;
    }
    return username.trim();
  }

  @Override
  public String generatePasswordResetToken() {
    return generateRandomString(32);
  }

  @Override
  public String generateEmailVerificationToken() {
    return generateRandomString(24);
  }

  @Override
  public boolean isBlank(String str) {
    return str == null || str.trim().isEmpty();
  }

  @Override
  public boolean isValidLength(String str, int minLength, int maxLength) {
    if (str == null) {
      return false;
    }
    int length = str.length();
    return length >= minLength && length <= maxLength;
  }

  @Override
  public int getPasswordStrength(String password) {
    if (password == null || password.isEmpty()) {
      return 0;
    }

    int score = 0;

    // 长度分数
    if (password.length() >= 8)
      score += 25;
    if (password.length() >= 12)
      score += 10;
    if (password.length() >= 16)
      score += 10;

    // 字符类型分数
    if (password.matches(".*[a-z].*"))
      score += 10; // 小写字母
    if (password.matches(".*[A-Z].*"))
      score += 15; // 大写字母
    if (password.matches(".*\\d.*"))
      score += 15; // 数字
    if (password.matches(".*[@$!%*?&].*"))
      score += 15; // 特殊字符

    return Math.min(100, score);
  }

  @Override
  public String getPasswordStrengthDescription(String password) {
    int strength = getPasswordStrength(password);

    if (strength < 30)
      return "Weak";
    if (strength < 60)
      return "Fair";
    if (strength < 80)
      return "Good";
    return "Strong";
  }

  /**
   * 生成验证码
   * 
   * @param chars  字符集
   * @param length 长度
   * @return 验证码
   */
  private String generateCode(String chars, int length) {
    StringBuilder code = new StringBuilder();
    for (int i = 0; i < length; i++) {
      int index = secureRandom.nextInt(chars.length());
      code.append(chars.charAt(index));
    }

    String result = code.toString();
    LOG.debug("Generated verification code of length: {}", length);
    return result;
  }
}