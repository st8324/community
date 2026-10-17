package com.example.community.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.community.auth.Role;
import com.example.community.support.AuthFixtures;
import com.example.community.support.AuthFixtures.Account;
import com.example.community.support.AuthFixtures.Session;
import com.example.community.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * S-17~S-21 (SEC-16, AUTH-03, AUTH-06). 리프레시 토큰은 프레임워크가 아니라 직접 작성한 흐름이라(02 문서 1장 예외, T-13) 동작을 여기서
 * 고정한다.
 *
 * <p>해시는 테스트가 따로 계산한다 — 제품 코드의 해시 함수를 그대로 쓰면 "둘 다 틀린" 경우를 잡지 못한다.
 */
@IntegrationTest
class RefreshTokenSecurityTest {

  @Autowired MockMvc mvc;
  @Autowired AuthFixtures fixtures;
  @Autowired JdbcTemplate jdbc;

  ResultActions refresh(String rawToken) throws Exception {
    return mvc.perform(
        post("/api/auth/refresh")
            .with(csrf())
            .cookie(new Cookie(AuthFixtures.REFRESH_TOKEN, rawToken)));
  }

  static String sha256Hex(String raw) throws Exception {
    byte[] digest =
        MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.US_ASCII));
    return HexFormat.of().formatHex(digest);
  }

  static String refreshValue(MvcResult result) {
    return AuthFixtures.cookieValue(AuthFixtures.refreshSetCookie(result).orElseThrow());
  }

  /**
   * S-17: 탈취 시나리오. 공격자가 r1을 복사해 두고, 정상 사용자가 r1로 연장해 r2를 받았다. 공격자가 r1을 쓰면 "이미 쓴 토큰" → 묶음 전체(r2 포함)
   * 폐기. 같은 사용자의 다른 로그인(다른 기기) 묶음은 영향이 없다.
   */
  @Test
  void reusedRefreshTokenRevokesWholeFamily() throws Exception {
    Account account = fixtures.createUser(Role.USER);
    Session thisDevice = fixtures.loginRemembered(account);
    Session otherDevice = fixtures.loginRemembered(account);
    String r1 = thisDevice.refreshToken().getValue();

    String r2 = refreshValue(refresh(r1).andExpect(status().isOk()).andReturn());

    refresh(r1).andExpect(status().isUnauthorized()); // 재사용 감지
    refresh(r2).andExpect(status().isUnauthorized()); // 묶음 폐기됨 — 커밋됐는지 확인
    refresh(otherDevice.refreshToken().getValue()).andExpect(status().isOk()); // 다른 묶음은 무사
  }

  /** S-18: Path=/api/auth → 게시글·댓글 요청에는 실리지 않는다. Max-Age = 14일(A-7). */
  @Test
  void refreshCookieAttributes() throws Exception {
    Account account = fixtures.createUser(Role.USER);
    MvcResult login = fixtures.loginResult(account.email(), account.password(), true);
    String line = AuthFixtures.refreshSetCookie(login).orElseThrow();

    assertThat(line)
        .contains("HttpOnly")
        .contains("Secure")
        .contains("SameSite=Strict")
        .contains("Path=/api/auth")
        .contains("Max-Age=1209600")
        .doesNotContainIgnoringCase("Domain=");

    // 연장 응답의 쿠키도 같은 속성, Max-Age는 남은 기간 (14일 이하)
    MvcResult rotated = refresh(AuthFixtures.cookieValue(line)).andReturn();
    String rotatedLine = AuthFixtures.refreshSetCookie(rotated).orElseThrow();
    assertThat(rotatedLine)
        .contains("HttpOnly")
        .contains("Secure")
        .contains("SameSite=Strict")
        .contains("Path=/api/auth");
    long maxAge = Long.parseLong(rotatedLine.replaceAll(".*Max-Age=(\\d+).*", "$1"));
    assertThat(maxAge).isPositive().isLessThanOrEqualTo(1_209_600L);
  }

  /** S-19: DB가 유출돼도 저장된 값으로는 쿠키를 만들 수 없다. 원문은 32바이트 난수(Base64URL 43자). */
  @Test
  void refreshTokenIsStoredAsHashOnly() throws Exception {
    Session session = fixtures.loginRememberedAsNew(Role.USER);
    String raw = session.refreshToken().getValue();

    assertThat(raw).matches("[A-Za-z0-9_-]{43}");
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE token_hash = ?", Long.class, raw))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE token_hash = ?",
                Long.class,
                sha256Hex(raw)))
        .isEqualTo(1);
  }

  /** S-20: 쿠키 삭제만으로는 부족하다 — 복사해 둔 리프레시 토큰도 서버에서 무효가 되어야 한다. */
  @Test
  void logoutRevokesRefreshToken() throws Exception {
    Session session = fixtures.loginRememberedAsNew(Role.USER);
    String copied = session.refreshToken().getValue();

    mvc.perform(post("/api/auth/logout").with(csrf()).cookie(session.cookies()))
        .andExpect(status().isNoContent());

    refresh(copied).andExpect(status().isUnauthorized());
    List<Object> revokedAt =
        jdbc.queryForList(
            "SELECT revoked_at FROM refresh_tokens WHERE token_hash = ?",
            Object.class,
            sha256Hex(copied));
    assertThat(revokedAt).hasSize(1).doesNotContainNull();
  }

  /**
   * S-21: 만료 시각은 최초 로그인 기준(절대 만료). 연장해도 늘지 않고, 지나면 401.
   *
   * <p>만료 시각을 이틀 전으로 옮긴다 — JVM 시간대와 무관하게 확실히 과거가 되도록 여유를 크게 둔다.
   */
  @Test
  void refreshTokenHasAbsoluteExpiry() throws Exception {
    Session session = fixtures.loginRememberedAsNew(Role.USER);
    String r1 = session.refreshToken().getValue();
    String expiresBefore = expiresAtOf(r1);

    String r2 = refreshValue(refresh(r1).andExpect(status().isOk()).andReturn());
    assertThat(expiresAtOf(r2)).as("교체해도 만료 시각 동일").isEqualTo(expiresBefore);

    jdbc.update(
        "UPDATE refresh_tokens SET expires_at = ? WHERE token_hash = ?",
        LocalDateTime.now(ZoneOffset.UTC).minusDays(2),
        sha256Hex(r2));
    refresh(r2).andExpect(status().isUnauthorized());
  }

  String expiresAtOf(String raw) throws Exception {
    return jdbc.queryForObject(
        "SELECT CAST(expires_at AS CHAR) FROM refresh_tokens WHERE token_hash = ?",
        String.class,
        sha256Hex(raw));
  }
}
