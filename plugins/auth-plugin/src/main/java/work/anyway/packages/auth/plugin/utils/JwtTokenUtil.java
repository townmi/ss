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
import java.util.Map;

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
  private static final String CLAIM_EMAIL = "email";
  private static final String CLAIM_ROLE = "role";
  private static final String CLAIM_TOKEN_TYPE = "type";

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
   * 生成访问 token
   * 
   * @param userId 用户ID
   * @param email  用户邮箱
   * @param role   用户角色
   * @return JWT access token
   */
  public String generateAccessToken(String userId, String email, String role) {
    return generateToken(userId, email, role, ACCESS_TOKEN_TYPE, accessTokenExpirySeconds);
  }

  /**
   * 生成刷新 token
   * 
   * @param userId 用户ID
   * @param email  用户邮箱
   * @param role   用户角色
   * @return JWT refresh token
   */
  public String generateRefreshToken(String userId, String email, String role) {
    return generateToken(userId, email, role, REFRESH_TOKEN_TYPE, refreshTokenExpirySeconds);
  }

  /**
   * 生成 JWT token
   * 
   * @param userId        用户ID
   * @param email         用户邮箱
   * @param role          用户角色
   * @param tokenType     token类型
   * @param expirySeconds 过期时间（秒）
   * @return JWT token
   */
  private String generateToken(String userId, String email, String role, String tokenType, long expirySeconds) {
    try {
      initializeJwt();

      Instant now = Instant.now();
      Instant expiry = now.plus(expirySeconds, ChronoUnit.SECONDS);

      String token = JWT.create()
          .withIssuer(ISSUER)
          .withSubject(userId)
          .withClaim(CLAIM_USER_ID, userId)
          .withClaim(CLAIM_EMAIL, email)
          .withClaim(CLAIM_ROLE, role)
          .withClaim(CLAIM_TOKEN_TYPE, tokenType)
          .withIssuedAt(Date.from(now))
          .withExpiresAt(Date.from(expiry))
          .sign(algorithm);

      LOG.debug("Generated {} token for user: {}", tokenType, userId);
      return token;

    } catch (JWTCreationException e) {
      LOG.error("Failed to generate {} token for user: {}", tokenType, userId, e);
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

      return new TokenInfo(
          jwt.getClaim(CLAIM_USER_ID).asString(),
          jwt.getClaim(CLAIM_EMAIL).asString(),
          jwt.getClaim(CLAIM_ROLE).asString(),
          jwt.getClaim(CLAIM_TOKEN_TYPE).asString(),
          jwt.getIssuedAt().toInstant(),
          jwt.getExpiresAt().toInstant());

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
    private final String email;
    private final String role;
    private final String tokenType;
    private final Instant issuedAt;
    private final Instant expiresAt;

    public TokenInfo(String userId, String email, String role, String tokenType,
        Instant issuedAt, Instant expiresAt) {
      this.userId = userId;
      this.email = email;
      this.role = role;
      this.tokenType = tokenType;
      this.issuedAt = issuedAt;
      this.expiresAt = expiresAt;
    }

    public String getUserId() {
      return userId;
    }

    public String getEmail() {
      return email;
    }

    public String getRole() {
      return role;
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
      return Instant.now().isAfter(expiresAt);
    }

    public Map<String, Object> toMap() {
      return Map.of(
          "userId", userId,
          "email", email,
          "role", role,
          "tokenType", tokenType,
          "issuedAt", issuedAt.toString(),
          "expiresAt", expiresAt.toString());
    }
  }
}