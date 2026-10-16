package com.example.community.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.community.support.AuthFixtures;
import com.example.community.support.AuthFixtures.Account;
import com.example.community.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** AUTH-03 로그아웃, AUTH-04 보호 API, AUTH-05 현재 사용자 조회. */
@IntegrationTest
class SessionApiTest {

  @Autowired MockMvc mvc;
  @Autowired AuthFixtures fixtures;

  // ── AUTH-05 ────────────────────────────────────────────

  @Test
  void 로그인_상태면_me는_200이고_id와_마스킹된_표시명만_준다() throws Exception {
    Account account = fixtures.createUser(Role.USER);
    Cookie token = fixtures.login(account);

    MvcResult result =
        mvc.perform(get("/api/auth/me").cookie(token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(account.id()))
            .andExpect(jsonPath("$.displayName").isString())
            .andExpect(jsonPath("$.email").doesNotExist())
            .andExpect(jsonPath("$.role").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
            .andReturn();
    assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
        .doesNotContain(account.email())
        .doesNotContain("USER");
  }

  @Test
  void 토큰_없이_me를_호출하면_401_UNAUTHORIZED다() throws Exception {
    mvc.perform(get("/api/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void 망가진_토큰으로_me를_호출하면_401이다() throws Exception {
    mvc.perform(get("/api/auth/me").cookie(new Cookie(AuthFixtures.ACCESS_TOKEN, "not-a-jwt")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  // ── AUTH-04 ③ 토큰은 쿠키로만 ─────────────────────────────

  @Test
  void Authorization_헤더로_보낸_토큰은_읽지_않는다() throws Exception {
    Cookie token = fixtures.loginAsNew(Role.USER);
    mvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token.getValue()))
        .andExpect(status().isUnauthorized());
  }

  // ── AUTH-03 ────────────────────────────────────────────

  @Test
  void 로그아웃하면_204이고_토큰_쿠키를_지운다() throws Exception {
    Cookie token = fixtures.loginAsNew(Role.USER);
    MvcResult result =
        mvc.perform(post("/api/auth/logout").with(csrf()).cookie(token))
            .andExpect(status().isNoContent())
            .andReturn();
    assertThat(AuthFixtures.accessTokenSetCookie(result))
        .hasValueSatisfying(line -> assertThat(line).contains("Max-Age=0"));
  }

  @Test
  void 로그아웃_후_쿠키가_없으면_보호된_API는_401이다() throws Exception {
    Cookie token = fixtures.loginAsNew(Role.USER);
    mvc.perform(post("/api/auth/logout").with(csrf()).cookie(token))
        .andExpect(status().isNoContent());
    // 브라우저는 Max-Age=0 쿠키를 지운다 → 다음 요청에는 쿠키가 없다.
    mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void 토큰_없이_로그아웃하면_401이다() throws Exception {
    mvc.perform(post("/api/auth/logout").with(csrf())).andExpect(status().isUnauthorized());
  }

  /**
   * 감수한 위험의 기록 (01 문서 7장 "토큰 강제 무효화 제외"). 로그아웃은 브라우저 쿠키를 지울 뿐 토큰 자체를 폐기하지 않는다 → 미리 복사된 토큰은 만료(1시간)
   * 전까지 유효하다. 블랙리스트를 도입하면 문서를 먼저 바꾸고 이 테스트의 기대값을 401로 바꾼다.
   */
  @Test
  void 감수한_위험_로그아웃해도_이미_복사된_토큰은_만료_전까지_유효하다() throws Exception {
    Cookie token = fixtures.loginAsNew(Role.USER);
    mvc.perform(post("/api/auth/logout").with(csrf()).cookie(token))
        .andExpect(status().isNoContent());
    mvc.perform(get("/api/auth/me").cookie(token)).andExpect(status().isOk());
  }
}
