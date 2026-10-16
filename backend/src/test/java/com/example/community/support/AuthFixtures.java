package com.example.community.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.community.auth.Role;
import com.example.community.auth.User;
import com.example.community.auth.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 사용자 생성·로그인 도우미 (03 문서 HD-19). {@link IntegrationTest}가 등록한다.
 *
 * <p>관리자는 API로 만들 수 없으므로(시드 전용, ADM-01) 저장소에 직접 저장한다. 로그인은 실제 API를 거쳐 실제 쿠키를 받는다.
 */
public class AuthFixtures {

  public static final String ACCESS_TOKEN = "access_token";
  public static final String REFRESH_TOKEN = "refresh_token";

  /** 테스트가 쓰는 계정. password는 평문 — 테스트 안에서만 존재한다. */
  public record Account(long id, String email, String password, Role role) {}

  /** 로그인 상태 유지(rememberMe=true)로 로그인한 세션: 액세스·리프레시 토큰 쿠키. */
  public record Session(Cookie accessToken, Cookie refreshToken) {
    public Cookie[] cookies() {
      return new Cookie[] {accessToken, refreshToken};
    }
  }

  private final MockMvc mvc;
  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  public AuthFixtures(
      MockMvc mvc, UserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
    this.mvc = mvc;
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.clock = clock;
  }

  public Account createUser(Role role) {
    String email = TestData.uniqueEmail();
    String password = TestData.validPassword();
    User saved =
        users.save(
            User.create(email, passwordEncoder.encode(password), role, LocalDateTime.now(clock)));
    return new Account(saved.getId(), email, password, role);
  }

  public MvcResult loginResult(String email, String password) throws Exception {
    return mvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonBodies.of("email", email, "password", password)))
        .andReturn();
  }

  public MvcResult loginResult(String email, String password, boolean rememberMe) throws Exception {
    return mvc.perform(
            post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    JsonBodies.of("email", email, "password", password, "rememberMe", rememberMe)))
        .andReturn();
  }

  /** rememberMe=true 로그인 → 액세스·리프레시 쿠키. 둘 중 하나라도 없으면 테스트를 실패시킨다. */
  public Session loginRemembered(Account account) throws Exception {
    MvcResult result = loginResult(account.email(), account.password(), true);
    String access = accessTokenSetCookie(result).orElseThrow(() -> new AssertionError("액세스 쿠키 없음"));
    String refresh = refreshSetCookie(result).orElseThrow(() -> new AssertionError("리프레시 쿠키 없음"));
    return new Session(
        new Cookie(ACCESS_TOKEN, cookieValue(access)),
        new Cookie(REFRESH_TOKEN, cookieValue(refresh)));
  }

  public Session loginRememberedAsNew(Role role) throws Exception {
    return loginRemembered(createUser(role));
  }

  /** 응답의 Set-Cookie 중 refresh_token 줄. */
  public static Optional<String> refreshSetCookie(MvcResult result) {
    return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
        .filter(line -> line.startsWith(REFRESH_TOKEN + "="))
        .findFirst();
  }

  /** 로그인해서 받은 토큰 쿠키. 로그인에 실패하면 테스트를 실패시킨다. */
  public Cookie login(Account account) throws Exception {
    MvcResult result = loginResult(account.email(), account.password());
    String line =
        accessTokenSetCookie(result)
            .orElseThrow(
                () ->
                    new AssertionError(
                        "로그인 실패: 상태 " + result.getResponse().getStatus() + ", 토큰 쿠키 없음"));
    return new Cookie(ACCESS_TOKEN, cookieValue(line));
  }

  public Cookie loginAsNew(Role role) throws Exception {
    return login(createUser(role));
  }

  /** 응답의 Set-Cookie 중 access_token 줄. */
  public static Optional<String> accessTokenSetCookie(MvcResult result) {
    return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE).stream()
        .filter(line -> line.startsWith(ACCESS_TOKEN + "="))
        .findFirst();
  }

  /** {@code name=value; Path=/; ...}에서 value. */
  public static String cookieValue(String setCookieLine) {
    String first = setCookieLine.split(";", 2)[0];
    return first.substring(first.indexOf('=') + 1);
  }
}
