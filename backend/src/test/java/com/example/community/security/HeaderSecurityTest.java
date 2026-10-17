package com.example.community.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

import com.example.community.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/** S-14 (SEC-13), S-15 (SEC-14). 02 문서 5.8절 CSP 값, T-20. */
@IntegrationTest
class HeaderSecurityTest {

  /** 02 문서 5.8절. 값을 바꾸려면 문서를 먼저 고친다. */
  static final String EXPECTED_CSP =
      "default-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action"
          + " 'self'";

  static final String FOREIGN_ORIGIN = "https://evil.example";

  @Autowired MockMvc mvc;

  /**
   * S-15: API 응답과 화면(index.html) 응답 모두에 CSP·nosniff·프레임 금지가 있다. CSP는 React 이스케이프(SEC-08)가 뚫려도
   * 인라인·외부 스크립트 실행을 막는 2차 방어선이고, frame-ancestors는 클릭재킹을 막는다.
   */
  @Test
  void securityHeadersArePresent() throws Exception {
    for (String path : List.of("/api/posts", "/posts/3")) {
      MockHttpServletResponse response = mvc.perform(get(path)).andReturn().getResponse();

      assertThat(response.getHeader("Content-Security-Policy")).as(path).isEqualTo(EXPECTED_CSP);
      assertThat(response.getHeader("X-Content-Type-Options")).as(path).isEqualTo("nosniff");
      assertThat(response.getHeader("X-Frame-Options")).as(path).isEqualTo("DENY");
    }
  }

  /** S-14: 같은 출처만 쓰므로 CORS를 열지 않는다. 다른 Origin의 일반 요청·사전 요청 모두 허용 헤더가 없어야 한다. */
  @Test
  void noCorsHeadersForForeignOrigin() throws Exception {
    MockHttpServletResponse simple =
        mvc.perform(get("/api/posts").header(HttpHeaders.ORIGIN, FOREIGN_ORIGIN))
            .andReturn()
            .getResponse();
    MockHttpServletResponse preflight =
        mvc.perform(
                options("/api/posts")
                    .header(HttpHeaders.ORIGIN, FOREIGN_ORIGIN)
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
            .andReturn()
            .getResponse();

    for (MockHttpServletResponse response : List.of(simple, preflight)) {
      assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
      assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isNull();
    }
  }
}
