package com.example.community.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.community.support.AuthFixtures;
import com.example.community.support.AuthFixtures.Account;
import com.example.community.support.AuthFixtures.Session;
import com.example.community.support.IntegrationTest;
import com.example.community.support.JsonBodies;
import com.example.community.support.TestData;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * AUTH-06 로그인 상태 유지, AUTH-02 ⑧, AUTH-01 ①의 rememberMe, AUTH-03 ① 쿠키 삭제 (01 문서 v0.4).
 *
 * <p>탈취 감지·해시 저장·쿠키 속성·로그아웃 폐기·절대 만료는 RefreshTokenSecurityTest(S-17~S-21).
 */
@IntegrationTest
class RememberMeApiTest {

  @Autowired MockMvc mvc;
  @Autowired AuthFixtures fixtures;

  ResultActions refresh(Cookie... cookies) throws Exception {
    var request = post("/api/auth/refresh").with(csrf());
    if (cookies.length > 0) {
      request.cookie(cookies);
    }
    return mvc.perform(request);
  }

  static Cookie refreshCookie(String value) {
    return new Cookie(AuthFixtures.REFRESH_TOKEN, value);
  }

  // ── 발급 (AUTH-02 ⑧, AUTH-06 ① ⑧) ──────────────────────────────

  @Test
  void rememberMe_없이_로그인하면_리프레시_쿠키를_주지_않는다() throws Exception {
    Account account = fixtures.createUser(Role.USER);
    MvcResult withoutField = fixtures.loginResult(account.email(), account.password());
    MvcResult withFalse = fixtures.loginResult(account.email(), account.password(), false);

    assertThat(AuthFixtures.refreshSetCookie(withoutField)).isEmpty();
    assertThat(AuthFixtures.refreshSetCookie(withFalse)).isEmpty();
  }

  @Test
  void rememberMe_true로_로그인하면_리프레시_쿠키를_함께_준다() throws Exception {
    Account account = fixtures.createUser(Role.USER);
    MvcResult result = fixtures.loginResult(account.email(), account.password(), true);

    assertThat(AuthFixtures.accessTokenSetCookie(result)).isPresent();
    assertThat(AuthFixtures.refreshSetCookie(result)).isPresent();
  }

  @Test
  void rememberMe_true로_가입해도_리프레시_쿠키를_준다() throws Exception {
    MvcResult result =
        mvc.perform(
                post("/api/auth/signup")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        JsonBodies.of(
                            "email",
                            TestData.uniqueEmail(),
                            "password",
                            TestData.validPassword(),
                            "rememberMe",
                            true)))
            .andExpect(status().isCreated())
            .andReturn();
    assertThat(AuthFixtures.refreshSetCookie(result)).isPresent();
  }

  @Test
  void 로그인에_실패하면_리프레시_쿠키도_없다() throws Exception {
    Account account = fixtures.createUser(Role.USER);
    MvcResult result = fixtures.loginResult(account.email(), TestData.validPassword(), true);
    assertThat(result.getResponse().getStatus()).isEqualTo(401);
    assertThat(AuthFixtures.refreshSetCookie(result)).isEmpty();
  }

  // ── 연장 (AUTH-06 ② ③ ⑦) ──────────────────────────────────────

  @Test
  void 리프레시_토큰으로_연장하면_200이고_새_액세스_토큰과_새_리프레시_토큰을_준다() throws Exception {
    Account account = fixtures.createUser(Role.USER);
    Session session = fixtures.loginRemembered(account);

    // 액세스 토큰 없이 리프레시 쿠키만 보낸다 (액세스 토큰이 만료돼 브라우저가 지운 상황)
    MvcResult result =
        refresh(session.refreshToken())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(account.id()))
            .andExpect(jsonPath("$.displayName").isString())
            .andReturn();

    String newAccess =
        AuthFixtures.cookieValue(AuthFixtures.accessTokenSetCookie(result).orElseThrow());
    String newRefresh =
        AuthFixtures.cookieValue(AuthFixtures.refreshSetCookie(result).orElseThrow());
    assertThat(newRefresh).isNotEqualTo(session.refreshToken().getValue());

    mvc.perform(get("/api/auth/me").cookie(new Cookie(AuthFixtures.ACCESS_TOKEN, newAccess)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(account.id()));
  }

  @Test
  void 연장한_새_리프레시_토큰으로_다시_연장할_수_있다() throws Exception {
    Session session = fixtures.loginRememberedAsNew(Role.USER);
    MvcResult first = refresh(session.refreshToken()).andExpect(status().isOk()).andReturn();
    String second = AuthFixtures.cookieValue(AuthFixtures.refreshSetCookie(first).orElseThrow());

    refresh(refreshCookie(second)).andExpect(status().isOk());
  }

  @Test
  void 한_번_쓴_리프레시_토큰은_다시_쓸_수_없다() throws Exception {
    Session session = fixtures.loginRememberedAsNew(Role.USER);
    refresh(session.refreshToken()).andExpect(status().isOk());

    refresh(session.refreshToken())
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void 망가진_액세스_토큰_쿠키가_남아_있어도_연장할_수_있다() throws Exception {
    Session session = fixtures.loginRememberedAsNew(Role.USER);
    refresh(new Cookie(AuthFixtures.ACCESS_TOKEN, "expired.or.broken"), session.refreshToken())
        .andExpect(status().isOk());
  }

  // ── 거절 (AUTH-06 ⑤) ─────────────────────────────────────────

  @Test
  void 리프레시_쿠키가_없으면_401이다() throws Exception {
    refresh()
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void 모르는_리프레시_토큰이나_형식이_틀린_값이면_401이고_리프레시_쿠키를_지운다() throws Exception {
    for (String value : new String[] {"A".repeat(43), "not-a-token", "x".repeat(5000)}) {
      MvcResult result =
          refresh(refreshCookie(value)).andExpect(status().isUnauthorized()).andReturn();
      assertThat(AuthFixtures.refreshSetCookie(result))
          .hasValueSatisfying(line -> assertThat(line).contains("Max-Age=0"));
      assertThat(AuthFixtures.accessTokenSetCookie(result)).isEmpty();
    }
  }

  @Test
  void 액세스_토큰만으로는_연장할_수_없다() throws Exception {
    Session session = fixtures.loginRememberedAsNew(Role.USER);
    refresh(session.accessToken()).andExpect(status().isUnauthorized());
  }

  // ── 로그아웃 (AUTH-03 ①) ─────────────────────────────────────

  @Test
  void 로그아웃하면_액세스와_리프레시_쿠키를_모두_지운다() throws Exception {
    Session session = fixtures.loginRememberedAsNew(Role.USER);
    MvcResult result =
        mvc.perform(post("/api/auth/logout").with(csrf()).cookie(session.cookies()))
            .andExpect(status().isNoContent())
            .andReturn();

    assertThat(AuthFixtures.accessTokenSetCookie(result))
        .hasValueSatisfying(line -> assertThat(line).contains("Max-Age=0"));
    assertThat(AuthFixtures.refreshSetCookie(result))
        .hasValueSatisfying(
            line -> assertThat(line).contains("Max-Age=0").contains("Path=/api/auth"));
  }

  @Test
  void 액세스_토큰이_만료된_상태에서도_로그아웃으로_리프레시_토큰을_폐기할_수_있다() throws Exception {
    Session session = fixtures.loginRememberedAsNew(Role.USER);
    mvc.perform(post("/api/auth/logout").with(csrf()).cookie(session.refreshToken()))
        .andExpect(status().isNoContent());

    refresh(session.refreshToken()).andExpect(status().isUnauthorized());
  }
}
