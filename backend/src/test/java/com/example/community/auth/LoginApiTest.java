package com.example.community.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.community.support.AuthFixtures;
import com.example.community.support.AuthFixtures.Account;
import com.example.community.support.IntegrationTest;
import com.example.community.support.JsonBodies;
import com.example.community.support.TestData;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** AUTH-02 로그인 수용 기준 (01 문서 v0.3 4.1절). 쿠키 속성은 CookieSecurityTest(S-05), 계정 열거는 S-16. */
@IntegrationTest
class LoginApiTest {

  @Autowired MockMvc mvc;
  @Autowired AuthFixtures fixtures;

  ResultActions login(String email, String password, Cookie... cookies) throws Exception {
    var request =
        post("/api/auth/login")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(JsonBodies.of("email", email, "password", password));
    if (cookies.length > 0) {
      request.cookie(cookies);
    }
    return mvc.perform(request);
  }

  static String expectedDisplayName(String email) {
    // TestData.uniqueEmail()의 로컬부는 13자 → 앞 2자 + *** (A-1)
    return email.substring(0, 2) + "***" + email.substring(email.indexOf('@'));
  }

  // ── ① 성공 ─────────────────────────────────────────────

  @Test
  void 정상_자격증명이면_200이고_id와_마스킹된_표시명을_돌려준다() throws Exception {
    Account account = fixtures.createUser(Role.USER);
    login(account.email(), account.password())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(account.id()))
        .andExpect(jsonPath("$.displayName").value(expectedDisplayName(account.email())));
  }

  @Test
  void 로그인_응답에는_이메일_원문_role_비밀번호_해시가_없다() throws Exception {
    Account account = fixtures.createUser(Role.ADMIN);
    MvcResult result = login(account.email(), account.password()).andReturn();
    String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);

    assertThat(body)
        .doesNotContain(account.email())
        .doesNotContainIgnoringCase("role")
        .doesNotContain("ADMIN")
        .doesNotContainIgnoringCase("password")
        .doesNotContain("$2");
  }

  @Test
  void 로그인에_성공하면_토큰_쿠키를_발급한다() throws Exception {
    Account account = fixtures.createUser(Role.USER);
    MvcResult result = login(account.email(), account.password()).andReturn();
    assertThat(AuthFixtures.accessTokenSetCookie(result)).isPresent();
  }

  // ── ② 실패 ─────────────────────────────────────────────

  @Test
  void 비밀번호가_틀리면_401_INVALID_CREDENTIALS다() throws Exception {
    Account account = fixtures.createUser(Role.USER);
    MvcResult result =
        login(account.email(), TestData.validPassword())
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andReturn();
    assertThat(AuthFixtures.accessTokenSetCookie(result)).isEmpty();
  }

  @Test
  void 없는_이메일이면_401_INVALID_CREDENTIALS다() throws Exception {
    login(TestData.uniqueEmail(), TestData.validPassword())
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
  }

  // ── ④ 정규화 ───────────────────────────────────────────

  @Test
  void 이메일_대소문자와_앞뒤_공백이_달라도_로그인된다() throws Exception {
    Account account = fixtures.createUser(Role.USER);
    login(" " + account.email().toUpperCase(Locale.ROOT) + " ", account.password())
        .andExpect(status().isOk());
  }

  @Test
  void 비밀번호는_대소문자를_구분한다() throws Exception {
    Account account = fixtures.createUser(Role.USER);
    login(account.email(), account.password().toUpperCase(Locale.ROOT))
        .andExpect(status().isUnauthorized());
  }

  // ── ⑤ 형식 위반 ────────────────────────────────────────

  @Test
  void 빈_이메일이나_빈_비밀번호는_400이다() throws Exception {
    login("", TestData.validPassword())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields").value(hasItem("email")));
    login(TestData.uniqueEmail(), "")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields").value(hasItem("password")));
  }

  @Test
  void 비밀번호_65자나_72바이트_초과는_400이다() throws Exception {
    Account account = fixtures.createUser(Role.USER);
    login(account.email(), "a".repeat(65)).andExpect(status().isBadRequest());
    login(account.email(), "가".repeat(25)).andExpect(status().isBadRequest());
  }

  @Test
  void 이메일_255자는_400이다() throws Exception {
    login("a".repeat(243) + "@example.com", TestData.validPassword())
        .andExpect(status().isBadRequest());
  }

  // ── ⑥ 남아 있는 무효 토큰 쿠키 ──────────────────────────────

  @Test
  void 만료되거나_망가진_토큰_쿠키가_남아_있어도_로그인할_수_있다() throws Exception {
    Account account = fixtures.createUser(Role.USER);
    login(
            account.email(),
            account.password(),
            new Cookie(AuthFixtures.ACCESS_TOKEN, "broken.token.value"))
        .andExpect(status().isOk());
  }

  @Test
  void 망가진_토큰_쿠키가_남아_있어도_회원가입할_수_있다() throws Exception {
    mvc.perform(
            post("/api/auth/signup")
                .with(csrf())
                .cookie(new Cookie(AuthFixtures.ACCESS_TOKEN, "broken.token.value"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    JsonBodies.of(
                        "email", TestData.uniqueEmail(), "password", TestData.validPassword())))
        .andExpect(status().isCreated());
  }

  // ── ⑦ 토큰 내용 (SEC-06, A-5) ───────────────────────────────

  @Test
  void 발급한_토큰에는_sub_iat_exp만_있고_유효기간은_1시간이다() throws Exception {
    Account account = fixtures.createUser(Role.ADMIN);
    MvcResult result = login(account.email(), account.password()).andReturn();
    String token =
        AuthFixtures.cookieValue(AuthFixtures.accessTokenSetCookie(result).orElseThrow());

    SignedJWT jwt = SignedJWT.parse(token);
    JWTClaimsSet claims = jwt.getJWTClaimsSet();
    assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("HS256");
    assertThat(claims.getClaims().keySet()).containsExactlyInAnyOrder("sub", "iat", "exp");
    assertThat(claims.getSubject()).isEqualTo(Long.toString(account.id()));
    assertThat(
            Duration.between(
                claims.getIssueTime().toInstant(), claims.getExpirationTime().toInstant()))
        .isEqualTo(Duration.ofHours(1));
  }
}
