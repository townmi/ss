package work.anyway.interfaces.auth;

import lombok.*;
import java.time.LocalDateTime;

/**
 * 登录尝试结果
 * 用于返回登录尝试检查的结果
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginAttemptResult {

  private boolean allowed; // 是否允许登录
  private String reason; // 拒绝原因
  private int remainingAttempts; // 剩余尝试次数
  private LocalDateTime lockUntil; // 锁定到什么时间
  private int waitSeconds; // 需要等待的秒数

  public LoginAttemptResult(boolean allowed, String reason) {
    this.allowed = allowed;
    this.reason = reason;
  }

  /**
   * 创建允许的结果
   */
  public static LoginAttemptResult allowed(int remainingAttempts, int waitSeconds) {
    LoginAttemptResult result = new LoginAttemptResult(true, null);
    result.setRemainingAttempts(remainingAttempts);
    result.setWaitSeconds(waitSeconds);
    return result;
  }

  /**
   * 创建被阻止的结果
   */
  public static LoginAttemptResult blocked(String reason, LocalDateTime lockUntil) {
    LoginAttemptResult result = new LoginAttemptResult(false, reason);
    result.setLockUntil(lockUntil);
    return result;
  }

  /**
   * 创建需要等待的结果
   */
  public static LoginAttemptResult waitRequired(String reason, int waitSeconds) {
    LoginAttemptResult result = new LoginAttemptResult(false, reason);
    result.setWaitSeconds(waitSeconds);
    return result;
  }

}