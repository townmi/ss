package work.anyway.packages.data.example.plugin.entity;

import work.anyway.interfaces.data.Entity;

/**
 * 用户实体示例
 * 
 * @author 作者名
 * @since 1.0.0
 */
public class User extends Entity {
  private String username;
  private String email;
  private String role;
  private boolean active;

  // Getters and Setters
  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }
} 