package com.example.community.auth;

import com.example.community.auth.RefreshTokenService.Rotated;
import com.example.community.common.error.ErrorCode;
import com.example.community.common.error.ErrorResponse;
import com.example.community.common.security.AccessTokenService;
import com.example.community.common.security.RefreshTokenCookie;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 API (02 문서 5.3절). 컨트롤러는 DTO 매핑과 {@code @Valid}, 쿠키 헤더만 다룬다 (02 문서 5.2절). */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService authService;
  private final AccessTokenService accessTokens;
  private final RefreshTokenService refreshTokens;

  public AuthController(
      AuthService authService, AccessTokenService accessTokens, RefreshTokenService refreshTokens) {
    this.authService = authService;
    this.accessTokens = accessTokens;
    this.refreshTokens = refreshTokens;
  }

  /** AUTH-01: 201 + 가입 직후 자동 로그인 (D-15). rememberMe면 리프레시 토큰도. */
  @PostMapping("/signup")
  public ResponseEntity<MeResponse> signup(@Valid @RequestBody SignupRequest request) {
    User user = authService.signup(request);
    return loggedIn(ResponseEntity.status(HttpStatus.CREATED), user, request.keepLoggedIn());
  }

  /** AUTH-02: 200 + 토큰 쿠키 + {id, displayName}. 토큰은 본문에 넣지 않는다 (SEC-05: JS가 토큰을 만지지 않게). */
  @PostMapping("/login")
  public ResponseEntity<MeResponse> login(@Valid @RequestBody LoginRequest request) {
    User user = authService.login(request);
    return loggedIn(ResponseEntity.ok(), user, request.keepLoggedIn());
  }

  /**
   * AUTH-06: 리프레시 토큰으로 연장. 성공 200 + 새 액세스·리프레시 쿠키, 실패 401 + 리프레시 쿠키 삭제.
   *
   * <p>실패를 예외로 던지지 않는 이유는 {@link RefreshTokenService} 주석 참고 (묶음 폐기가 롤백되지 않게).
   */
  @PostMapping("/refresh")
  public ResponseEntity<?> refresh(
      @CookieValue(name = RefreshTokenCookie.NAME, required = false) String rawRefreshToken) {
    if (refreshTokens.rotate(rawRefreshToken) instanceof Rotated rotated) {
      User user = authService.currentUser(rotated.userId());
      return ResponseEntity.ok()
          .header(HttpHeaders.SET_COOKIE, accessCookie(user))
          .header(
              HttpHeaders.SET_COOKIE,
              RefreshTokenCookie.issue(rotated.next().rawToken(), rotated.next().maxAge())
                  .toString())
          .body(MeResponse.from(user));
    }
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .header(HttpHeaders.SET_COOKIE, RefreshTokenCookie.clear().toString())
        .body(ErrorResponse.of(ErrorCode.UNAUTHORIZED));
  }

  /**
   * AUTH-03: 항상 204 (로그인 불필요, D-17). 리프레시 토큰이 있으면 서버에서 묶음 폐기 → 두 쿠키 삭제. 액세스 토큰은 폐기할 수 없다 (01 문서 7장
   * 감수 위험).
   */
  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(name = RefreshTokenCookie.NAME, required = false) String rawRefreshToken) {
    refreshTokens.revokeFamilyOf(rawRefreshToken);
    return ResponseEntity.noContent()
        .header(HttpHeaders.SET_COOKIE, accessTokens.clearingCookie().toString())
        .header(HttpHeaders.SET_COOKIE, RefreshTokenCookie.clear().toString())
        .build();
  }

  /** AUTH-05: 200 + {id, displayName}. sub는 디코더가 숫자임을 이미 검증했다 (JwtConfig). */
  @GetMapping("/me")
  public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
    return MeResponse.from(authService.currentUser(Long.parseLong(jwt.getSubject())));
  }

  /** 로그인 상태로 만드는 응답 (가입·로그인 공통). */
  private ResponseEntity<MeResponse> loggedIn(
      ResponseEntity.BodyBuilder response, User user, boolean keepLoggedIn) {
    response.header(HttpHeaders.SET_COOKIE, accessCookie(user));
    if (keepLoggedIn) {
      RefreshTokenService.Issued refresh = refreshTokens.issueNewFamily(user.getId());
      response.header(
          HttpHeaders.SET_COOKIE,
          RefreshTokenCookie.issue(refresh.rawToken(), refresh.maxAge()).toString());
    }
    return response.body(MeResponse.from(user));
  }

  private String accessCookie(User user) {
    return accessTokens.cookieFor(accessTokens.issue(user.getId())).toString();
  }
}
