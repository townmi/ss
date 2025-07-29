package work.anyway.packages.auth.plugin.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import work.anyway.interfaces.cache.CacheService;
import work.anyway.interfaces.user.User;
import work.anyway.interfaces.user.UserService;

import java.util.Optional;

/**
 * Token 验证工具类
 * 提供统一的 JWT token 验证逻辑，供拦截器使用
 */
@Component
public class TokenValidator {

  private static final Logger LOG = LoggerFactory.getLogger(TokenValidator.class);

  @Autowired
  private JwtTokenUtil jwtTokenUtil;

  @Autowired
  private CacheService cacheService;

  @Autowired(required = false)
  private UserService userService;

  /**
   * 验证 token 并返回验证结果
   * 
   * @param token JWT token
   * @return 验证结果
   */
  public TokenValidationResult validateToken(String token) {
    if (token == null || token.trim().isEmpty()) {
      return TokenValidationResult.failure("Token is required");
    }

    try {
      // 1. JWT 格式和签名验证
      if (!jwtTokenUtil.validateToken(token)) {
        return TokenValidationResult.failure("Invalid token format or signature");
      }

      // 2. 解析 token 信息
      JwtTokenUtil.TokenInfo tokenInfo = jwtTokenUtil.parseToken(token);
      if (tokenInfo == null) {
        return TokenValidationResult.failure("Failed to parse token");
      }

      // 3. 检查 token 是否过期
      if (tokenInfo.isExpired()) {
        return TokenValidationResult.failure("Token has expired");
      }

      // 4. 检查 token 黑名单
      if (cacheService.exists("token_blacklist:" + token)) {
        return TokenValidationResult.failure("Token has been revoked");
      }

      // 5. 验证用户是否存在且状态正常
      if (userService != null) {
        Optional<User> userOpt = userService.getUserById(tokenInfo.getUserId());
        if (userOpt.isEmpty()) {
          return TokenValidationResult.failure("User not found");
        }

        User user = userOpt.get();
        if (!user.isActive()) {
          return TokenValidationResult.failure("User account is not active");
        }
      }

      // 6. 验证成功，返回用户信息
      return TokenValidationResult.success(tokenInfo);

    } catch (Exception e) {
      LOG.error("Token validation error", e);
      return TokenValidationResult.failure("Token validation failed: " + e.getMessage());
    }
  }

  /**
   * 验证 token 但不进行用户数据库查询（避免阻塞事件循环）
   * 
   * @param token JWT token
   * @return 验证结果
   */
  public TokenValidationResult validateTokenWithoutUserCheck(String token) {
    if (token == null || token.trim().isEmpty()) {
      return TokenValidationResult.failure("Token is required");
    }

    try {
      // 1. JWT 格式和签名验证
      if (!jwtTokenUtil.validateToken(token)) {
        return TokenValidationResult.failure("Invalid token format or signature");
      }

      // 2. 解析 token 信息
      JwtTokenUtil.TokenInfo tokenInfo = jwtTokenUtil.parseToken(token);
      if (tokenInfo == null) {
        return TokenValidationResult.failure("Failed to parse token");
      }

      // 3. 检查 token 是否过期
      if (tokenInfo.isExpired()) {
        return TokenValidationResult.failure("Token has expired");
      }

      // 4. 检查 token 黑名单（如果缓存是非阻塞的）
      if (cacheService.exists("token_blacklist:" + token)) {
        return TokenValidationResult.failure("Token has been revoked");
      }

      // 跳过用户数据库查询，只返回 token 信息
      return TokenValidationResult.success(tokenInfo);

    } catch (Exception e) {
      LOG.error("Token validation error", e);
      return TokenValidationResult.failure("Token validation failed: " + e.getMessage());
    }
  }

  /**
   * 从请求头中提取 token
   * 
   * @param authHeader Authorization 请求头
   * @return 提取的 token，如果格式不正确返回 null
   */
  public String extractToken(String authHeader) {
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      return null;
    }
    return authHeader.substring(7).trim();
  }

  /**
   * 检查 token 是否为访问 token
   * 
   * @param token JWT token
   * @return 是否为访问 token
   */
  public boolean isAccessToken(String token) {
    return jwtTokenUtil.isAccessToken(token);
  }

  /**
   * 检查 token 是否为刷新 token
   * 
   * @param token JWT token
   * @return 是否为刷新 token
   */
  public boolean isRefreshToken(String token) {
    return jwtTokenUtil.isRefreshToken(token);
  }

  /**
   * 撤销 token（加入黑名单）
   * 
   * @param token JWT token
   */
  public void revokeToken(String token) {
    try {
      long remainingTime = jwtTokenUtil.getTokenRemainingTime(token);
      if (remainingTime > 0) {
        cacheService.put("token_blacklist:" + token, true, remainingTime);
        LOG.debug("Token revoked and added to blacklist");
      }
    } catch (Exception e) {
      LOG.error("Failed to revoke token", e);
    }
  }

  /**
   * Token 验证结果类
   */
  public static class TokenValidationResult {
    private final boolean success;
    private final String errorMessage;
    private final JwtTokenUtil.TokenInfo tokenInfo;

    private TokenValidationResult(boolean success, String errorMessage, JwtTokenUtil.TokenInfo tokenInfo) {
      this.success = success;
      this.errorMessage = errorMessage;
      this.tokenInfo = tokenInfo;
    }

    public static TokenValidationResult success(JwtTokenUtil.TokenInfo tokenInfo) {
      return new TokenValidationResult(true, null, tokenInfo);
    }

    public static TokenValidationResult failure(String errorMessage) {
      return new TokenValidationResult(false, errorMessage, null);
    }

    public boolean isSuccess() {
      return success;
    }

    public boolean isFailure() {
      return !success;
    }

    public String getErrorMessage() {
      return errorMessage;
    }

    public JwtTokenUtil.TokenInfo getTokenInfo() {
      return tokenInfo;
    }

    public String getUserId() {
      return tokenInfo != null ? tokenInfo.getUserId() : null;
    }

    public String getEmail() {
      return tokenInfo != null ? tokenInfo.getEmail() : null;
    }

    public String getRole() {
      return tokenInfo != null ? tokenInfo.getRole() : null;
    }

    public String getTokenType() {
      return tokenInfo != null ? tokenInfo.getTokenType() : null;
    }
  }
}