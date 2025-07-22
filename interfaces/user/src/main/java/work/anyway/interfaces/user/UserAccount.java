package work.anyway.interfaces.user;

import work.anyway.interfaces.data.Entity;
import java.util.Date;

/**
 * 用户账户实体类
 * 代表用户的登录凭证，支持多种认证方式
 * 
 * @author 作者名
 * @since 1.0.0
 */
public class UserAccount extends Entity {

  private String userId;
  private AccountType accountType;
  private String identifier; // 邮箱地址、Google ID、微信 openid 等
  private String credentials; // 密码hash、token等（JSON格式存储）
  private Boolean verified = false;
  private Boolean primaryAccount = false; // 是否为主账户
  private Date lastLogin;
  private String registrationIp;

  /**
   * 默认构造函数
   */
  public UserAccount() {
    super();
  }

  /**
   * 构造函数
   * 
   * @param userId      用户ID
   * @param accountType 账户类型
   * @param identifier  标识符（邮箱、第三方ID等）
   * @param credentials 凭证信息
   */
  public UserAccount(String userId, AccountType accountType, String identifier, String credentials) {
    super();
    this.userId = userId;
    this.accountType = accountType;
    this.identifier = identifier;
    this.credentials = credentials;
  }

  // Getters and Setters

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public AccountType getAccountType() {
    return accountType;
  }

  public void setAccountType(AccountType accountType) {
    this.accountType = accountType;
  }

  public String getIdentifier() {
    return identifier;
  }

  public void setIdentifier(String identifier) {
    this.identifier = identifier;
  }

  public String getCredentials() {
    return credentials;
  }

  public void setCredentials(String credentials) {
    this.credentials = credentials;
  }

  public Boolean getVerified() {
    return verified;
  }

  public void setVerified(Boolean verified) {
    this.verified = verified;
  }

  public Boolean getPrimaryAccount() {
    return primaryAccount;
  }

  public void setPrimaryAccount(Boolean primaryAccount) {
    this.primaryAccount = primaryAccount;
  }

  public Date getLastLogin() {
    return lastLogin;
  }

  public void setLastLogin(Date lastLogin) {
    this.lastLogin = lastLogin;
  }

  public String getRegistrationIp() {
    return registrationIp;
  }

  public void setRegistrationIp(String registrationIp) {
    this.registrationIp = registrationIp;
  }

  // 便利方法

  /**
   * 检查是否为主账户
   * 
   * @return 是否为主账户
   */
  public boolean isPrimaryAccount() {
    return Boolean.TRUE.equals(primaryAccount);
  }

  /**
   * 检查账户是否已验证
   * 
   * @return 是否已验证
   */
  public boolean isVerified() {
    return Boolean.TRUE.equals(verified);
  }

  /**
   * 检查是否为邮箱账户
   * 
   * @return 是否为邮箱账户
   */
  public boolean isEmailAccount() {
    return AccountType.EMAIL.equals(accountType);
  }

  /**
   * 检查是否为第三方账户
   * 
   * @return 是否为第三方账户
   */
  public boolean isThirdPartyAccount() {
    return accountType != null && accountType.isThirdParty();
  }

  /**
   * 获取显示名称
   * 
   * @return 显示名称
   */
  public String getDisplayName() {
    if (accountType == null) {
      return identifier;
    }

    switch (accountType) {
      case EMAIL:
        return identifier;
      case PHONE:
        return maskPhone(identifier);
      case GOOGLE:
        return "Google账户";
      case WECHAT:
        return "微信账户";
      case GITHUB:
        return "GitHub账户";
      default:
        return accountType.getDescription();
    }
  }

  /**
   * 脱敏手机号
   * 
   * @param phone 手机号
   * @return 脱敏后的手机号
   */
  private String maskPhone(String phone) {
    if (phone == null || phone.length() < 11) {
      return phone;
    }
    return phone.substring(0, 3) + "****" + phone.substring(7);
  }

  @Override
  public String toString() {
    return "UserAccount{" +
        "id='" + getId() + '\'' +
        ", userId='" + userId + '\'' +
        ", accountType=" + accountType +
        ", identifier='" + identifier + '\'' +
        ", verified=" + verified +
        ", primaryAccount=" + primaryAccount +
        ", createdAt=" + getCreatedAt() +
        ", updatedAt=" + getUpdatedAt() +
        '}';
  }
}