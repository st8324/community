package com.example.community.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.community.auth.Role;
import com.example.community.support.AuthFixtures;
import com.example.community.support.AuthFixtures.Account;
import com.example.community.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** S-05 (SEC-05·06), S-07 (AUTH-03). */
@IntegrationTest
class CookieSecurityTest {

  @Autowired MockMvc mvc;
  @Autowired AuthFixtures fixtures;

  /**
   * S-05: HttpOnly(JS로 못 읽음 → XSS로 토큰 탈취 완화), Secure(HTTPS로만 전송), SameSite=Strict(다른 사이트에서 시작한 요청에
   * 쿠키 미전송), Path=/, Max-Age=3600(A-5). Domain을 지정하지 않는다(하위 도메인 공유 방지).
   */
  @Test
  void loginCookieAttributes() throws Exception {
    Account account = fixtures.createUser(Role.USER);
    MvcResult result = fixtures.loginResult(account.email(), account.password());
    String line = AuthFixtures.accessTokenSetCookie(result).orElseThrow();

    assertThat(line)
        .contains("HttpOnly")
        .contains("Secure")
        .contains("SameSite=Strict")
        .contains("Path=/")
        .contains("Max-Age=3600")
        .doesNotContainIgnoringCase("Domain=");
  }

  /** S-07: 같은 이름·Path·속성으로 Max-Age=0을 보내야 브라우저가 "같은 쿠키"로 보고 지운다. */
  @Test
  void logoutClearsCookie() throws Exception {
    Cookie token = fixtures.loginAsNew(Role.USER);
    MvcResult result = mvc.perform(post("/api/auth/logout").with(csrf()).cookie(token)).andReturn();
    String line = AuthFixtures.accessTokenSetCookie(result).orElseThrow();

    assertThat(AuthFixtures.cookieValue(line)).isEmpty();
    assertThat(line)
        .contains("Max-Age=0")
        .contains("Path=/")
        .contains("HttpOnly")
        .contains("Secure")
        .contains("SameSite=Strict");
  }
}
