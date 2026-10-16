package com.example.community.common.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * 액세스 토큰 발급과 토큰 쿠키 (SEC-05, SEC-06, 02 문서 5.4절).
 *
 * <p>클레임은 {@code sub}(사용자 id), {@code iat}, {@code exp}만 (AUTH-02 ⑦). 역할은 넣지 않는다 — 권한은 매 요청 DB 기준으로
 * 확인한다 (SEC-07).
 */
@Component
public class AccessTokenService {

  public static final String COOKIE_NAME = "access_token";

  private final JwtEncoder encoder;
  private final Clock clock;
  private final Duration ttl;

  public AccessTokenService(JwtEncoder encoder, Clock clock, JwtProperties properties) {
    if (properties.ttl() == null || properties.ttl().isNegative() || properties.ttl().isZero()) {
      throw new IllegalStateException("community.jwt.ttl이 설정되지 않았다 (A-5: 1h).");
    }
    this.encoder = encoder;
    this.clock = clock;
    this.ttl = properties.ttl();
  }

  public String issue(long userId) {
    Instant now = clock.instant();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .subject(Long.toString(userId))
            .issuedAt(now)
            .expiresAt(now.plus(ttl))
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  /** HttpOnly·Secure·SameSite=Strict·Path=/, Max-Age = 토큰 유효기간. Domain은 지정하지 않는다. */
  public ResponseCookie cookieFor(String token) {
    return baseCookie(token).maxAge(ttl).build();
  }

  /** 발급 때와 같은 이름·Path·속성 + Max-Age=0 → 브라우저가 같은 쿠키로 보고 지운다 (AUTH-03 ①). */
  public ResponseCookie clearingCookie() {
    return baseCookie("").maxAge(Duration.ZERO).build();
  }

  private static ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
    // Secure는 항상 켠다. 로컬 개발(http://localhost)도 브라우저가 "안전한 출처"로 취급해 동작한다.
    return ResponseCookie.from(COOKIE_NAME, value)
        .httpOnly(true)
        .secure(true)
        .sameSite("Strict")
        .path("/");
  }
}
