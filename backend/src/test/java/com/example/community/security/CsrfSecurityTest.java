package com.example.community.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.community.auth.Role;
import com.example.community.support.AuthFixtures;
import com.example.community.support.AuthFixtures.Account;
import com.example.community.support.IntegrationTest;
import com.example.community.support.JsonBodies;
import com.example.community.support.TestData;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * S-06 (SEC-05). CSRF 누락은 여기서만 검증한다 — 다른 테스트는 항상 {@code with(csrf())}를 붙인다.
 *
 * <p>실제 브라우저 흐름(쿠키 발급 → 헤더로 되돌림)은 {@link CsrfBrowserFlowSecurityTest}가 별도 컨텍스트에서 검증한다 (03 문서
 * HD-26).
 */
@IntegrationTest
class CsrfSecurityTest {

  @Autowired MockMvc mvc;
  @Autowired AuthFixtures fixtures;

  /**
   * S-06: 로그인·회원가입처럼 "아직 로그인 전" 요청도 CSRF를 검증한다. 막지 않으면 공격 사이트가 피해자 브라우저로 공격자 계정에 로그인시키는 "로그인 CSRF"가
   * 가능하다.
   */
  @Test
  void stateChangingRequestWithoutCsrfIsForbidden() throws Exception {
    Account account = fixtures.createUser(Role.USER);
    Cookie token = fixtures.login(account);

    List<MockHttpServletRequestBuilder> requests =
        List.of(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    JsonBodies.of(
                        "email", TestData.uniqueEmail(), "password", TestData.validPassword())),
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonBodies.of("email", account.email(), "password", account.password())),
            post("/api/auth/logout").cookie(token));

    for (MockHttpServletRequestBuilder request : requests) {
      mvc.perform(request)
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }
  }

  @Test
  void 틀린_CSRF_토큰도_403이다() throws Exception {
    Account account = fixtures.createUser(Role.USER);
    mvc.perform(
            post("/api/auth/login")
                .with(csrf().useInvalidToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonBodies.of("email", account.email(), "password", account.password())))
        .andExpect(status().isForbidden());
  }
}
