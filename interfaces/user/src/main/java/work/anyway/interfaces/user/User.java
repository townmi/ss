package work.anyway.interfaces.user;

import lombok.*;
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
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends Entity {

  private String name;
  private String phone;
  private String department;
  @Builder.Default
  private String role = "user";
  @Builder.Default
  private String status = "active";
  private String avatarUrl;
  private String notes;
  private Date lastLogin;

  // 关联的账户列表（不持久化到数据库）
  private transient List<UserAccount> accounts;

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
    this.role = "user";
    this.status = "active";
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

}