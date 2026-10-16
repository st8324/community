package com.example.community.common.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

/**
 * 토큰을 {@code access_token} 쿠키에서만 읽는다 (AUTH-04 ③, D-14). {@code Authorization} 헤더는 읽지 않는다.
 *
 * <p>회원가입·로그인·CSRF 발급 요청에서는 쿠키를 읽지 않는다 (AUTH-02 ⑥, D-13). 읽으면 만료된 쿠키가 남은 사용자가 다시 로그인하려 할 때 401을 받아
 * 영영 로그인하지 못한다. 이 목록에 경로를 추가하는 것은 "그 경로는 무효 토큰을 무시한다"는 결정이므로 문서를 먼저 바꾼다.
 */
public class CookieBearerTokenResolver implements BearerTokenResolver {

  static final Set<String> IGNORE_TOKEN_PATHS =
      Set.of("/api/csrf", "/api/auth/signup", "/api/auth/login");

  @Override
  public String resolve(HttpServletRequest request) {
    if (IGNORE_TOKEN_PATHS.contains(request.getRequestURI())) {
      return null;
    }
    Cookie[] cookies = request.getCookies();
    if (cookies == null) {
      return null;
    }
    for (Cookie cookie : cookies) {
      if (AccessTokenService.COOKIE_NAME.equals(cookie.getName())) {
        String value = cookie.getValue();
        return value == null || value.isBlank() ? null : value;
      }
    }
    return null;
  }
}
