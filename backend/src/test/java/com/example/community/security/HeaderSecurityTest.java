package com.example.community.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

import com.example.community.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

/** S-14 (SEC-13), S-15 (SEC-14). 02 문서 5.8절 CSP 값, T-20. */
@IntegrationTest
class HeaderSecurityTest {

  /** 02 문서 5.8절의 CSP 값. 값을 바꾸려면 문서를 먼저 고친다. */
  static final String EXPECTED_CSP =
      "default-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action"
          + " 'self'";

  static final String FOREIGN_ORIGIN = "https://evil.example";

  @Autowired MockMvc mvc;

  /** S-14: 같은 출처만 쓰므로 CORS를 켜지 않는다. 다른 출처가 보내도 허용 헤더가 붙지 않아야 한다. */
  @Test
  void noCorsHeadersForForeignOrigin() throws Exception {
    List<RequestBuilder> requests =
        List.of(
            get("/api/csrf").header("Origin", FOREIGN_ORIGIN),
            get("/api/posts").header("Origin", FOREIGN_ORIGIN),
            get("/posts/3").header("Origin", FOREIGN_ORIGIN),
            options("/api/posts")
                .header("Origin", FOREIGN_ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "x-xsrf-token"));

    for (RequestBuilder request : requests) {
      MvcResult result = mvc.perform(request).andReturn();
      assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin")).isNull();
      assertThat(result.getResponse().getHeader("Access-Control-Allow-Credentials")).isNull();
      assertThat(result.getResponse().getHeader("Access-Control-Allow-Methods")).isNull();
    }
  }

  /** S-15: API 응답·오류 응답·SPA 화면 모두에 같은 보안 헤더가 붙는다. */
  @Test
  void securityHeadersArePresent() throws Exception {
    List<String> paths =
        List.of(
            "/api/csrf", // 204
            "/api/posts", // 200 JSON
            "/api/auth/me", // 401 (보안 필터가 만든 오류)
            "/posts/3"); // SPA 화면 (index.html)

    for (String path : paths) {
      MvcResult result = mvc.perform(get(path)).andReturn();
      assertThat(result.getResponse().getHeader("Content-Security-Policy"))
          .as("%s의 CSP", path)
          .isEqualTo(EXPECTED_CSP);
      assertThat(result.getResponse().getHeader("X-Content-Type-Options"))
          .as("%s의 nosniff", path)
          .isEqualTo("nosniff");
      assertThat(result.getResponse().getHeader("X-Frame-Options"))
          .as("%s의 X-Frame-Options", path)
          .isEqualTo("DENY");
    }
  }
}
