package work.anyway.packages.auth.plugin;

/**
 * Auth插件常量定义
 * 
 * @author 作者名
 * @since 1.0.0
 */
public final class AuthPluginConstants {

  // 插件基本信息
  public static final String PLUGIN_NAME = "Auth Plugin";
  public static final String PLUGIN_VERSION = "1.0.0";
  public static final String PLUGIN_BASE_PATH = "/auth";

  // API路径前缀
  public static final String API_BASE_PATH = "/api/auth";
  public static final String ADMIN_API_BASE_PATH = "/api/admin/auth";
  public static final String PAGE_BASE_PATH = "/page/auth";

  // 拦截器名称
  public static final String INTERCEPTOR_PUBLIC = "SystemRequestLog";
  public static final String INTERCEPTOR_AUTH = "SimpleAuth";
  public static final String INTERCEPTOR_ADMIN = "Authentication";
  public static final String INTERCEPTOR_OPERATION = "OperationLog";

  // 缓存键前缀
  public static final String CACHE_PREFIX_TOKEN = "auth:token:";
  public static final String CACHE_PREFIX_REFRESH = "auth:refresh:";
  public static final String CACHE_PREFIX_BLACKLIST = "auth:blacklist:";
  public static final String CACHE_PREFIX_VERIFICATION = "auth:verification:";
  public static final String CACHE_PREFIX_RESET = "auth:reset:";
  public static final String CACHE_PREFIX_LOGIN_ATTEMPT = "auth:attempt:";
  public static final String CACHE_PREFIX_RATE_LIMIT = "auth:rate:";

  // 时间常量（秒）
  public static final int TOKEN_EXPIRY_SECONDS = 3600; // 1小时
  public static final int REFRESH_TOKEN_EXPIRY_SECONDS = 604800; // 7天
  public static final int VERIFICATION_EXPIRY_SECONDS = 1800; // 30分钟
  public static final int RESET_TOKEN_EXPIRY_SECONDS = 3600; // 1小时
  public static final int LOGIN_ATTEMPT_WINDOW_SECONDS = 900; // 15分钟

  // 限制常量
  public static final int MAX_LOGIN_ATTEMPTS = 5;
  public static final int MAX_REGISTRATION_ATTEMPTS_PER_HOUR = 5;
  public static final int RATE_LIMIT_WINDOW_SECONDS = 60;

  // 预定义权限
  public static final String[] DEFAULT_PERMISSIONS = {
      "user.create", "user.read", "user.update", "user.delete",
      "admin.access", "admin.manage",
      "system.config", "system.monitor",
      "report.view", "report.export"
  };

  private AuthPluginConstants() {
    // 防止实例化
  }
}