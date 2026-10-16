package com.example.community.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리프레시 토큰 발급·교체·폐기 (AUTH-06, SEC-16, 02 문서 3.5절·T-13).
 *
 * <p>프레임워크가 제공하지 않아 직접 작성한 유일한 인증 흐름이다. 암호 요소는 JDK 표준({@link SecureRandom}, SHA-256)만 쓴다.
 *
 * <p><b>거절은 예외가 아니라 반환값</b>({@link Rejected})으로 알린다. 재사용 감지 시 "묶음 폐기"를 DB에 기록한 뒤 401을 줘야 하는데, 예외를
 * 던지면 트랜잭션이 롤백되어 폐기가 사라진다.
 *
 * <p>원문 토큰은 로그에 남기지 않는다 (SEC-11). 재사용 감지 로그에는 사용자 id만 남긴다.
 */
@Service
@EnableConfigurationProperties(RefreshTokenProperties.class)
public class RefreshTokenService {

  private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final int TOKEN_BYTES = 32;

  /** 32바이트 → Base64URL(패딩 없음) 43자. 형식이 다르면 해시도 계산하지 않고 거절한다. */
  private static final Pattern RAW_FORMAT = Pattern.compile("[A-Za-z0-9_-]{43}");

  /** 새로 발급한 토큰. {@code rawToken}은 쿠키로만 내보낸다. */
  public record Issued(String rawToken, Duration maxAge) {}

  /** 연장 결과. */
  public sealed interface Rotation permits Rotated, Rejected {}

  public record Rotated(long userId, Issued next) implements Rotation {}

  public record Rejected() implements Rotation {}

  private static final Rejected REJECTED = new Rejected();

  private final RefreshTokenRepository tokens;
  private final Clock clock;
  private final Duration ttl;

  public RefreshTokenService(
      RefreshTokenRepository tokens, Clock clock, RefreshTokenProperties properties) {
    if (properties.ttl() == null || properties.ttl().isNegative() || properties.ttl().isZero()) {
      throw new IllegalStateException("community.refresh.ttl이 설정되지 않았다 (A-7: 14d).");
    }
    this.tokens = tokens;
    this.clock = clock;
    this.ttl = properties.ttl();
  }

  /** AUTH-06 ①: rememberMe 로그인·가입 → 새 묶음. 만료 = 지금 + ttl (이후 교체해도 고정). */
  @Transactional
  public Issued issueNewFamily(long userId) {
    LocalDateTime now = now();
    String raw = newRawToken();
    tokens.save(
        new RefreshToken(userId, UUID.randomUUID().toString(), hash(raw), now.plus(ttl), now));
    return new Issued(raw, ttl);
  }

  /** AUTH-06 ②~⑤: 1회용 교체. 재사용이면 묶음 전체 폐기 후 거절. */
  @Transactional
  public Rotation rotate(String raw) {
    if (!looksLikeToken(raw)) {
      return REJECTED;
    }
    Optional<RefreshToken> found = tokens.findForUpdateByTokenHash(hash(raw));
    if (found.isEmpty()) {
      return REJECTED;
    }
    RefreshToken current = found.get();
    LocalDateTime now = now();

    if (current.isRevoked()) {
      int revoked = tokens.revokeFamily(current.getFamilyId(), now);
      log.warn("리프레시 토큰 재사용 감지 → 묶음 폐기: userId={}, 폐기 {}건", current.getUserId(), revoked);
      return REJECTED;
    }
    if (current.isExpiredAt(now)) {
      return REJECTED;
    }

    current.revoke(now);
    String nextRaw = newRawToken();
    // 절대 만료: 새 토큰도 묶음의 원래 만료 시각을 물려받는다 (AUTH-06 ⑥)
    tokens.save(
        new RefreshToken(
            current.getUserId(),
            current.getFamilyId(),
            hash(nextRaw),
            current.getExpiresAt(),
            now));
    Duration remaining = Duration.between(now, current.getExpiresAt());
    return new Rotated(current.getUserId(), new Issued(nextRaw, remaining));
  }

  /** AUTH-03 ①: 로그아웃 — 토큰이 속한 묶음 전체 폐기. 모르는 값이면 아무 일도 하지 않는다. */
  @Transactional
  public void revokeFamilyOf(String raw) {
    if (!looksLikeToken(raw)) {
      return;
    }
    tokens.findByTokenHash(hash(raw)).ifPresent(t -> tokens.revokeFamily(t.getFamilyId(), now()));
  }

  private LocalDateTime now() {
    // DATETIME(6)에 맞춰 마이크로초로 자른다 → 저장 전후 값이 같다 (절대 만료 비교·복사)
    return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
  }

  private static boolean looksLikeToken(String raw) {
    return raw != null && RAW_FORMAT.matcher(raw).matches();
  }

  private static String newRawToken() {
    byte[] bytes = new byte[TOKEN_BYTES];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  /** 256비트 난수는 추측할 수 없으므로 느린 해시(BCrypt)가 필요 없다. 인덱스로 바로 찾을 수 있는 SHA-256을 쓴다. */
  private static String hash(String raw) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.US_ASCII));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256을 쓸 수 없다", e);
    }
  }
}
