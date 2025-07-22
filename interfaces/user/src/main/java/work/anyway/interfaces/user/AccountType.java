package work.anyway.interfaces.user;

/**
 * 账户类型枚举
 * 定义系统支持的登录方式
 * 
 * @author 作者名
 * @since 1.0.0
 */
public enum AccountType {

  EMAIL("email", "邮箱登录"),
  GOOGLE("google", "Google登录"),
  WECHAT("wechat", "微信登录"),
  GITHUB("github", "GitHub登录"),
  PHONE("phone", "手机号登录");

  private final String code;
  private final String description;

  AccountType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public String getCode() {
    return code;
  }

  public String getDescription() {
    return description;
  }

  /**
   * 根据代码获取账户类型
   * 
   * @param code 账户类型代码
   * @return 对应的账户类型，如果不存在返回null
   */
  public static AccountType fromCode(String code) {
    if (code == null) {
      return null;
    }

    for (AccountType type : AccountType.values()) {
      if (type.code.equals(code)) {
        return type;
      }
    }
    return null;
  }

  /**
   * 检查是否为第三方登录类型
   * 
   * @return 是否为第三方登录
   */
  public boolean isThirdParty() {
    return this == GOOGLE || this == WECHAT || this == GITHUB;
  }

  /**
   * 检查是否需要密码
   * 
   * @return 是否需要密码
   */
  public boolean requiresPassword() {
    return this == EMAIL || this == PHONE;
  }

  @Override
  public String toString() {
    return description + " (" + code + ")";
  }
}