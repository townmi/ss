package work.anyway.packages.auth.plugin.utils;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import work.anyway.interfaces.user.User;

/**
 * JWT Token 工具类
 * 负责 JWT token 的生成、验证和解析
 */
@Component
public class JwtTokenUtil {

  private static final Logger LOG = LoggerFactory.getLogger(JwtTokenUtil.class);

  // JWT 配置常量
  private static final String ISSUER = "work-anyway-auth";
  private static final String TOKEN_TYPE = "Bearer";

  // Claims 键名
  private static final String CLAIM_USER_ID = "userId";
  private static final String CLAIM_USER_NAME = "userName";
  private static final String CLAIM_EMAIL = "email";
  private static final String CLAIM_ROLE = "role";
  private static final String CLAIM_TOKEN_TYPE = "type";
  // 扩展的用户属性
  private static final String CLAIM_PHONE = "phone";
  private static final String CLAIM_DEPARTMENT = "department";
  private static final String CLAIM_STATUS = "status";
  private static final String CLAIM_AVATAR_URL = "avatarUrl";
  private static final String CLAIM_LAST_LOGIN = "lastLogin";

  // Token 类型
  private static final String ACCESS_TOKEN_TYPE = "access";
  private static final String REFRESH_TOKEN_TYPE = "refresh";

  // JWT 密钥 (实际项目中应该从配置文件读取)
  @Value("${auth.jwt.secret:work-anyway-default-secret-key-change-in-production}")
  private String jwtSecret;

  // Token 有效期配置
  @Value("${auth.jwt.access-token-expiry:3600}")
  private long accessTokenExpirySeconds; // 默认1小时

  @Value("${auth.jwt.refresh-token-expiry:604800}")
  private long refreshTokenExpirySeconds; // 默认7天

  private Algorithm algorithm;
  private JWTVerifier verifier;

  /**
   * 初始化 JWT 算法和验证器
   */
  private void initializeJwt() {
    if (algorithm == null) {
      algorithm = Algorithm.HMAC256(jwtSecret);
      verifier = JWT.require(algorithm)
          .withIssuer(ISSUER)
          .build();
    }
  }

  /**
   * 生成访问 token（完整用户信息）
   * 
   * @param user  用户对象
   * @param email 用户邮箱
   * @return JWT access token
   */
  public String generateAccessToken(User user, String email) {
    return generateToken(user, email, ACCESS_TOKEN_TYPE, accessTokenExpirySeconds);
  }

  /**
   * 生成刷新 token（完整用户信息）
   * 
   * @param user  用户对象
   * @param email 用户邮箱
   * @return JWT refresh token
   */
  public String generateRefreshToken(User user, String email) {
    return generateToken(user, email, REFRESH_TOKEN_TYPE, refreshTokenExpirySeconds);
  }

  /**
   * 生成访问 token（基础信息 - 向后兼容）
   * 
   * @param userId   用户ID
   * @param userName 用户姓名
   * @param email    用户邮箱
   * @param role     用户角色
   * @return JWT access token
   */
  public String generateAccessToken(String userId, String userName, String email, String role) {
    return generateToken(userId, userName, email, role, null, null, null, null, null, ACCESS_TOKEN_TYPE,
        accessTokenExpirySeconds);
  }

  /**
   * 生成刷新 token（基础信息 - 向后兼容）
   * 
   * @param userId   用户ID
   * @param userName 用户姓名
   * @param email    用户邮箱
   * @param role     用户角色
   * @return JWT refresh token
   */
  public String generateRefreshToken(String userId, String userName, String email, String role) {
    return generateToken(userId, userName, email, role, null, null, null, null, null, REFRESH_TOKEN_TYPE,
        refreshTokenExpirySeconds);
  }

  /**
   * 生成 JWT token（完整用户信息）
   * 
   * @param user          用户对象
   * @param email         用户邮箱
   * @param tokenType     token类型
   * @param expirySeconds 过期时间（秒）
   * @return JWT token
   */
  private String generateToken(User user, String email, String tokenType, long expirySeconds) {
    try {
      initializeJwt();

      Instant now = Instant.now();
      Instant expiry = now.plus(expirySeconds, ChronoUnit.SECONDS);

      // 构建JWT Builder
      var jwtBuilder = JWT.create()
          .withIssuer(ISSUER)
          .withSubject(user.getId())
          .withClaim(CLAIM_USER_ID, user.getId())
          .withClaim(CLAIM_USER_NAME, user.getName())
          .withClaim(CLAIM_EMAIL, email)
          .withClaim(CLAIM_ROLE, user.getRole())
          .withClaim(CLAIM_TOKEN_TYPE, tokenType)
          .withIssuedAt(Date.from(now))
          .withExpiresAt(Date.from(expiry));

      // 添加扩展的用户属性（如果存在）
      if (user.getPhone() != null) {
        jwtBuilder.withClaim(CLAIM_PHONE, user.getPhone());
      }
      if (user.getDepartment() != null) {
        jwtBuilder.withClaim(CLAIM_DEPARTMENT, user.getDepartment());
      }
      if (user.getStatus() != null) {
        jwtBuilder.withClaim(CLAIM_STATUS, user.getStatus());
      }
      if (user.getAvatarUrl() != null) {
        jwtBuilder.withClaim(CLAIM_AVATAR_URL, user.getAvatarUrl());
      }
      if (user.getLastLogin() != null) {
        jwtBuilder.withClaim(CLAIM_LAST_LOGIN, user.getLastLogin().getTime());
      }

      String token = jwtBuilder.sign(algorithm);

      LOG.debug("Generated {} token for user: {} ({})", tokenType, user.getName(), user.getId());
      return token;

    } catch (JWTCreationException e) {
      LOG.error("Failed to generate {} token for user: {} ({})", tokenType, user.getName(), user.getId(), e);
      throw new RuntimeException("Token generation failed", e);
    }
  }

  /**
   * 生成 JWT token（基础信息 - 向后兼容）
   * 
   * @param userId        用户ID
   * @param userName      用户姓名
   * @param email         用户邮箱
   * @param role          用户角色
   * @param phone         电话号码
   * @param department    部门
   * @param status        状态
   * @param avatarUrl     头像URL
   * @param lastLogin     最后登录时间
   * @param tokenType     token类型
   * @param expirySeconds 过期时间（秒）
   * @return JWT token
   */
  private String generateToken(String userId, String userName, String email, String role,
      String phone, String department, String status, String avatarUrl, Date lastLogin,
      String tokenType, long expirySeconds) {
    try {
      initializeJwt();

      Instant now = Instant.now();
      Instant expiry = now.plus(expirySeconds, ChronoUnit.SECONDS);

      // 构建JWT Builder
      var jwtBuilder = JWT.create()
          .withIssuer(ISSUER)
          .withSubject(userId)
          .withClaim(CLAIM_USER_ID, userId)
          .withClaim(CLAIM_USER_NAME, userName)
          .withClaim(CLAIM_EMAIL, email)
          .withClaim(CLAIM_ROLE, role)
          .withClaim(CLAIM_TOKEN_TYPE, tokenType)
          .withIssuedAt(Date.from(now))
          .withExpiresAt(Date.from(expiry));

      // 添加可选的扩展属性
      if (phone != null) {
        jwtBuilder.withClaim(CLAIM_PHONE, phone);
      }
      if (department != null) {
        jwtBuilder.withClaim(CLAIM_DEPARTMENT, department);
      }
      if (status != null) {
        jwtBuilder.withClaim(CLAIM_STATUS, status);
      }
      if (avatarUrl != null) {
        jwtBuilder.withClaim(CLAIM_AVATAR_URL, avatarUrl);
      }
      if (lastLogin != null) {
        jwtBuilder.withClaim(CLAIM_LAST_LOGIN, lastLogin.getTime());
      }

      String token = jwtBuilder.sign(algorithm);

      LOG.debug("Generated {} token for user: {} ({})", tokenType, userName, userId);
      return token;

    } catch (JWTCreationException e) {
      LOG.error("Failed to generate {} token for user: {} ({})", tokenType, userName, userId, e);
      throw new RuntimeException("Token generation failed", e);
    }
  }

  /**
   * 验证 token 是否有效
   * 
   * @param token JWT token
   * @return 是否有效
   */
  public boolean validateToken(String token) {
    try {
      initializeJwt();
      verifier.verify(token);
      return true;
    } catch (JWTVerificationException e) {
      LOG.debug("Token validation failed: {}", e.getMessage());
      return false;
    }
  }

  /**
   * 从 token 中解析用户信息
   * 
   * @param token JWT token
   * @return 用户信息，如果解析失败返回 null
   */
  public TokenInfo parseToken(String token) {
    try {
      initializeJwt();
      DecodedJWT jwt = verifier.verify(token);

      // 安全地获取基础claims，避免null值
      String userId = jwt.getClaim(CLAIM_USER_ID).asString();
      String userName = jwt.getClaim(CLAIM_USER_NAME).asString();
      String email = jwt.getClaim(CLAIM_EMAIL).asString();
      String role = jwt.getClaim(CLAIM_ROLE).asString();
      String tokenType = jwt.getClaim(CLAIM_TOKEN_TYPE).asString();

      // 获取扩展的用户属性
      String phone = jwt.getClaim(CLAIM_PHONE).asString();
      String department = jwt.getClaim(CLAIM_DEPARTMENT).asString();
      String status = jwt.getClaim(CLAIM_STATUS).asString();
      String avatarUrl = jwt.getClaim(CLAIM_AVATAR_URL).asString();

      // 处理时间戳
      Instant issuedAt = jwt.getIssuedAt() != null ? jwt.getIssuedAt().toInstant() : null;
      Instant expiresAt = jwt.getExpiresAt() != null ? jwt.getExpiresAt().toInstant() : null;

      // 处理最后登录时间
      Date lastLogin = null;
      Long lastLoginTimestamp = jwt.getClaim(CLAIM_LAST_LOGIN).asLong();
      if (lastLoginTimestamp != null) {
        lastLogin = new Date(lastLoginTimestamp);
      }

      return new TokenInfo(userId, userName, email, role, phone, department, status,
          avatarUrl, lastLogin, tokenType, issuedAt, expiresAt);

    } catch (JWTVerificationException e) {
      LOG.debug("Failed to parse token: {}", e.getMessage());
      return null;
    }
  }

  /**
   * 获取 token 的剩余有效时间（秒）
   * 
   * @param token JWT token
   * @return 剩余有效时间，如果token无效返回-1
   */
  public long getTokenRemainingTime(String token) {
    TokenInfo tokenInfo = parseToken(token);
    if (tokenInfo == null) {
      return -1;
    }

    long remainingSeconds = ChronoUnit.SECONDS.between(Instant.now(), tokenInfo.getExpiresAt());
    return Math.max(0, remainingSeconds);
  }

  /**
   * 检查是否为访问 token
   * 
   * @param token JWT token
   * @return 是否为访问token
   */
  public boolean isAccessToken(String token) {
    TokenInfo tokenInfo = parseToken(token);
    return tokenInfo != null && ACCESS_TOKEN_TYPE.equals(tokenInfo.getTokenType());
  }

  /**
   * 检查是否为刷新 token
   * 
   * @param token JWT token
   * @return 是否为刷新token
   */
  public boolean isRefreshToken(String token) {
    TokenInfo tokenInfo = parseToken(token);
    return tokenInfo != null && REFRESH_TOKEN_TYPE.equals(tokenInfo.getTokenType());
  }

  /**
   * Token 信息类
   */
  public static class TokenInfo {
    private final String userId;
    private final String userName;
    private final String email;
    private final String role;
    private final String phone;
    private final String department;
    private final String status;
    private final String avatarUrl;
    private final Date lastLogin;
    private final String tokenType;
    private final Instant issuedAt;
    private final Instant expiresAt;

    public TokenInfo(String userId, String userName, String email, String role,
        String phone, String department, String status, String avatarUrl, Date lastLogin,
        String tokenType, Instant issuedAt, Instant expiresAt) {
      this.userId = userId;
      this.userName = userName;
      this.email = email;
      this.role = role;
      this.phone = phone;
      this.department = department;
      this.status = status;
      this.avatarUrl = avatarUrl;
      this.lastLogin = lastLogin;
      this.tokenType = tokenType;
      this.issuedAt = issuedAt;
      this.expiresAt = expiresAt;
    }

    public String getUserId() {
      return userId;
    }

    public String getUserName() {
      return userName;
    }

    public String getEmail() {
      return email;
    }

    public String getRole() {
      return role;
    }

    public String getPhone() {
      return phone;
    }

    public String getDepartment() {
      return department;
    }

    public String getStatus() {
      return status;
    }

    public String getAvatarUrl() {
      return avatarUrl;
    }

    public Date getLastLogin() {
      return lastLogin;
    }

    public String getTokenType() {
      return tokenType;
    }

    public Instant getIssuedAt() {
      return issuedAt;
    }

    public Instant getExpiresAt() {
      return expiresAt;
    }

    public boolean isExpired() {
      return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<>();
      map.put("userId", userId != null ? userId : "");
      map.put("userName", userName != null ? userName : "");
      map.put("email", email != null ? email : "");
      map.put("role", role != null ? role : "");
      map.put("phone", phone != null ? phone : "");
      map.put("department", department != null ? department : "");
      map.put("status", status != null ? status : "");
      map.put("avatarUrl", avatarUrl != null ? avatarUrl : "");
      map.put("lastLogin", lastLogin != null ? lastLogin.toString() : "");
      map.put("tokenType", tokenType != null ? tokenType : "");
      map.put("issuedAt", issuedAt != null ? issuedAt.toString() : "");
      map.put("expiresAt", expiresAt != null ? expiresAt.toString() : "");
      return map;
    }
  }
}