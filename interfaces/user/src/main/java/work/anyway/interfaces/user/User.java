package work.anyway.interfaces.user;

import work.anyway.interfaces.data.Entity;

import java.util.Date;
import java.util.List;

/**
 * 用户实体类
 * 代表系统中的真实用户，包含用户的业务信息
 * 
 * @author 作者名
 * @since 1.0.0
 */
public class User extends Entity {

  private String name;
  private String phone;
  private String department;
  private String role = "user";
  private String status = "active";
  private String avatarUrl;
  private String notes;
  private Date lastLogin;

  // 关联的账户列表（不持久化到数据库）
  private transient List<UserAccount> accounts;

  /**
   * 默认构造函数
   */
  public User() {
    super();
  }

  /**
   * 构造函数
   * 
   * @param name       用户姓名
   * @param phone      电话号码
   * @param department 部门
   */
  public User(String name, String phone, String department) {
    super();
    this.name = name;
    this.phone = phone;
    this.department = department;
  }

  // Getters and Setters

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getDepartment() {
    return department;
  }

  public void setDepartment(String department) {
    this.department = department;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getAvatarUrl() {
    return avatarUrl;
  }

  public void setAvatarUrl(String avatarUrl) {
    this.avatarUrl = avatarUrl;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public Date getLastLogin() {
    return lastLogin;
  }

  public void setLastLogin(Date lastLogin) {
    this.lastLogin = lastLogin;
  }

  public List<UserAccount> getAccounts() {
    return accounts;
  }

  public void setAccounts(List<UserAccount> accounts) {
    this.accounts = accounts;
  }

  // 便利方法

  /**
   * 检查用户是否为管理员
   * 
   * @return 是否为管理员
   */
  public boolean isAdmin() {
    return "admin".equals(this.role);
  }

  /**
   * 检查用户是否激活
   * 
   * @return 是否激活
   */
  public boolean isActive() {
    return "active".equals(this.status);
  }

  /**
   * 获取主要账户（邮箱账户）
   * 
   * @return 主要账户，如果不存在返回null
   */
  public UserAccount getPrimaryAccount() {
    if (accounts == null) {
      return null;
    }
    return accounts.stream()
        .filter(UserAccount::isPrimaryAccount)
        .findFirst()
        .orElse(null);
  }

  /**
   * 获取邮箱账户
   * 
   * @return 邮箱账户，如果不存在返回null
   */
  public UserAccount getEmailAccount() {
    if (accounts == null) {
      return null;
    }
    return accounts.stream()
        .filter(account -> AccountType.EMAIL.equals(account.getAccountType()))
        .findFirst()
        .orElse(null);
  }

  @Override
  public String toString() {
    return "User{" +
        "id='" + getId() + '\'' +
        ", name='" + name + '\'' +
        ", phone='" + phone + '\'' +
        ", department='" + department + '\'' +
        ", role='" + role + '\'' +
        ", status='" + status + '\'' +
        ", createdAt=" + getCreatedAt() +
        ", updatedAt=" + getUpdatedAt() +
        '}';
  }
}