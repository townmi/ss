package work.anyway.interfaces.auth;

import lombok.*;
import work.anyway.interfaces.data.Entity;
import work.anyway.annotations.Table;
import work.anyway.annotations.Column;

import java.util.Date;

/**
 * 权限审计日志实体
 * 
 * @author 作者名
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("permission_audit_logs")
public class PermissionAuditLog extends Entity {

  /**
   * 操作类型枚举
   */
  public enum Action {
    GRANT("grant"),
    REVOKE("revoke"),
    ROLE_ASSIGN("role_assign"),
    ROLE_REMOVE("role_remove"),
    PERMISSION_CREATE("permission_create"),
    PERMISSION_UPDATE("permission_update"),
    PERMISSION_DELETE("permission_delete"),
    ROLE_CREATE("role_create"),
    ROLE_UPDATE("role_update"),
    ROLE_DELETE("role_delete");

    private final String code;

    Action(String code) {
      this.code = code;
    }

    public String getCode() {
      return code;
    }
  }

  /**
   * 目标类型枚举
   */
  public enum TargetType {
    USER("user"),
    ROLE("role"),
    PERMISSION("permission"),
    USER_PERMISSION("user_permission"),
    USER_ROLE("user_role"),
    ROLE_PERMISSION("role_permission");

    private final String code;

    TargetType(String code) {
      this.code = code;
    }

    public String getCode() {
      return code;
    }
  }

  @Column("action")
  private String action;

  @Column("target_type")
  private String targetType;

  @Column("target_id")
  private String targetId;

  @Column("operator_id")
  private String operatorId;

  @Column("operator_name")
  private String operatorName;

  @Column("ip_address")
  private String ipAddress;

  @Column("user_agent")
  private String userAgent;

  @Column("before_value")
  private String beforeValue;

  @Column("after_value")
  private String afterValue;

  @Column("reason")
  private String reason;

  @Column("result")
  private String result;

  @Column("error_message")
  private String errorMessage;

  @Column("created_at")
  @Builder.Default
  private Date createdAt = new Date();

  /**
   * 创建成功的审计日志
   */
  public static PermissionAuditLog success(Action action, TargetType targetType, String targetId,
      String operatorId, String reason) {
    return PermissionAuditLog.builder()
        .action(action.getCode())
        .targetType(targetType.getCode())
        .targetId(targetId)
        .operatorId(operatorId)
        .reason(reason)
        .result("success")
        .build();
  }

  /**
   * 创建失败的审计日志
   */
  public static PermissionAuditLog failure(Action action, TargetType targetType, String targetId,
      String operatorId, String errorMessage) {
    return PermissionAuditLog.builder()
        .action(action.getCode())
        .targetType(targetType.getCode())
        .targetId(targetId)
        .operatorId(operatorId)
        .result("failure")
        .errorMessage(errorMessage)
        .build();
  }
}