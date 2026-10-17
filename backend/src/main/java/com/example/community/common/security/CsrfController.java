package com.example.community.common.security;

import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/csrf} → 204 + {@code XSRF-TOKEN} 쿠키 (02 문서 5.4절, 01 문서 4.5절).
 *
 * <p>Spring Security는 CSRF 토큰을 "필요할 때" 만든다(지연 로딩). 토큰 값을 한 번 읽어야 쿠키가 응답에 실린다. 토큰은 본문에 담지 않는다.
 */
@RestController
public class CsrfController {

  @GetMapping("/api/csrf")
  public ResponseEntity<Void> csrf(CsrfToken token) {
    token.getToken();
    return ResponseEntity.noContent().build();
  }
}
