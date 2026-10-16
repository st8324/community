package com.example.community.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.community.support.IntegrationTest;
import com.example.community.support.JsonBodies;
import com.example.community.support.TestData;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * SEC-05 실제 브라우저 흐름: {@code GET /api/csrf} → {@code XSRF-TOKEN} 쿠키 → 같은 값을 {@code X-XSRF-TOKEN} 헤더로
 * 보내면 통과.
 *
 * <p><b>왜 별도 컨텍스트인가 (03 문서 T-15, HD-26)</b>: spring-security-test의 {@code with(csrf())}는 처음 쓰일 때 공유
 * {@code CsrfFilter}의 저장소를 세션 기반 테스트용 저장소로 바꿔 끼우고, 컨텍스트가 재사용되는 동안 그대로 남는다. 그러면 운영 설정(쿠키 저장소)을 검증할 수
 * 없다. 이 클래스는 속성 하나를 달리해 전용 컨텍스트를 받고, 그 안에서 {@code csrf()}를 절대 쓰지 않는다.
 */
@IntegrationTest
@TestPropertySource(properties = "community.test.isolated-context=csrf-browser-flow")
class CsrfBrowserFlowSecurityTest {

  @Autowired MockMvc mvc;

  @Test
  void 브라우저처럼_CSRF_쿠키를_받아_헤더로_보내면_통과한다() throws Exception {
    MvcResult issued = mvc.perform(get("/api/csrf")).andExpect(status().isNoContent()).andReturn();
    Cookie xsrf = issued.getResponse().getCookie("XSRF-TOKEN");
    assertThat(xsrf).as("GET /api/csrf 응답의 XSRF-TOKEN 쿠키").isNotNull();
    assertThat(xsrf.isHttpOnly()).as("JS가 읽어야 하므로 HttpOnly가 아니다").isFalse();

    MvcResult signup =
        mvc.perform(
                post("/api/auth/signup")
                    .cookie(new Cookie("XSRF-TOKEN", xsrf.getValue()))
                    .header("X-XSRF-TOKEN", xsrf.getValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        JsonBodies.of(
                            "email", TestData.uniqueEmail(), "password", TestData.validPassword())))
            .andReturn();
    assertThat(signup.getResponse().getStatus())
        .as("가입 응답: %s", signup.getResponse().getContentAsString())
        .isEqualTo(201);
  }

  @Test
  void 쿠키와_헤더_값이_다르면_403이다() throws Exception {
    MvcResult issued = mvc.perform(get("/api/csrf")).andReturn();
    Cookie xsrf = issued.getResponse().getCookie("XSRF-TOKEN");
    assertThat(xsrf).isNotNull();

    mvc.perform(
            post("/api/auth/signup")
                .cookie(new Cookie("XSRF-TOKEN", xsrf.getValue()))
                .header("X-XSRF-TOKEN", "different-" + xsrf.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    JsonBodies.of(
                        "email", TestData.uniqueEmail(), "password", TestData.validPassword())))
        .andExpect(status().isForbidden());
  }
}
