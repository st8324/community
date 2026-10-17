package com.example.community.auth;

import com.example.community.common.security.AccessTokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 API (02 문서 5.3절). 컨트롤러는 DTO 매핑과 {@code @Valid}만 한다 (02 문서 5.2절). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;
  private final AccessTokenService accessTokens;

  public AuthController(AuthService authService, AccessTokenService accessTokens) {
    this.authService = authService;
    this.accessTokens = accessTokens;
  }

  /** AUTH-01: 201, 본문 없음, 토큰 발급 없음 (D-10). */
  @PostMapping("/signup")
  public ResponseEntity<Void> signup(@Valid @RequestBody SignupRequest request) {
    authService.signup(request);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  /** AUTH-02: 200 + 토큰 쿠키 + {id, displayName}. 토큰은 본문에 넣지 않는다 (SEC-05: JS가 토큰을 만지지 않게). */
  @PostMapping("/login")
  public ResponseEntity<MeResponse> login(@Valid @RequestBody LoginRequest request) {
    User user = authService.login(request);
    String token = accessTokens.issue(user.getId());
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, accessTokens.cookieFor(token).toString())
        .body(MeResponse.from(user));
  }

  /** AUTH-03: 204 + 쿠키 삭제. 로그인 상태에서만 (SecurityConfig: /api/** 인증 필요). */
  @PostMapping("/logout")
  public ResponseEntity<Void> logout() {
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, accessTokens.clearingCookie().toString())
        .build();
  }

  /** AUTH-05: 200 + {id, displayName}. sub는 디코더가 숫자임을 이미 검증했다 (JwtConfig). */
  @GetMapping("/me")
  public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
    return MeResponse.from(authService.currentUser(Long.parseLong(jwt.getSubject())));
  }
}
