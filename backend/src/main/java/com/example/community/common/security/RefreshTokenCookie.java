package com.example.community.common.security;

import java.time.Duration;
import org.springframework.http.ResponseCookie;

/**
 * 리프레시 토큰 쿠키 (SEC-16, D-18). {@code Path=/api/auth}라서 게시글·댓글 요청에는 실리지 않고 인증 API(연장·로그아웃)에만 전송된다.
 */
public final class RefreshTokenCookie {

  public static final String NAME = "refresh_token";
  public static final String PATH = "/api/auth";

  private RefreshTokenCookie() {}

  public static ResponseCookie issue(String rawToken, Duration maxAge) {
    return base(rawToken).maxAge(maxAge).build();
  }

  /** 발급 때와 같은 이름·Path·속성 + Max-Age=0 → 브라우저가 지운다. */
  public static ResponseCookie clear() {
    return base("").maxAge(Duration.ZERO).build();
  }

  private static ResponseCookie.ResponseCookieBuilder base(String value) {
    return ResponseCookie.from(NAME, value)
        .httpOnly(true)
        .secure(true)
        .sameSite("Strict")
        .path(PATH);
  }
}
