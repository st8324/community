package com.example.community.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.community.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

/** S-12 (SEC-10). S-13(actuator)은 별도 PR에서 이 클래스에 추가한다. */
@IntegrationTest
class ErrorResponseSecurityTest {

  /** 오류 본문에 나오면 안 되는 내부 정보의 흔적. */
  static final List<String> FORBIDDEN_FRAGMENTS =
      List.of(
          "Exception",
          "exception",
          "trace",
          "at com.",
          "at org.",
          "org.springframework",
          "com.example",
          "java.",
          "jakarta.",
          "SQL",
          "jdbc",
          "Hibernate",
          "\"path\"");

  @Autowired MockMvc mvc;

  /** 프레임워크가 기본 오류 응답을 만드는 대표 경로들을 두드려, 모두 표준 형식이고 내부 정보가 없는지 본다. */
  @Test
  void errorResponseHasNoInternals() throws Exception {
    List<RequestBuilder> badRequests =
        List.of(
            // 깨진 JSON → Jackson 파싱 예외
            post("/api/auth/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": "),
            // 타입이 다른 JSON → 역직렬화 예외 (메시지에 클래스명이 들어가기 쉬움)
            post("/api/auth/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": [1,2], \"password\": {\"x\": 1}}"),
            // 지원하지 않는 Content-Type
            post("/api/auth/signup").with(csrf()).contentType(MediaType.TEXT_PLAIN).content("x"),
            // 검증 실패
            post("/api/auth/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"not-an-email\", \"password\": \"short\"}"),
            // 인증 필요한 경로 (보안 필터에서 만든 오류)
            get("/api/auth/does-not-exist"));

    for (RequestBuilder request : badRequests) {
      MvcResult result = mvc.perform(request).andReturn();
      String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
      int status = result.getResponse().getStatus();

      assertThat(status).as(body).isBetween(400, 499);
      assertThat(result.getResponse().getContentType())
          .startsWith(MediaType.APPLICATION_JSON_VALUE);
      assertThat(body).as("표준 오류 형식").contains("\"code\"").contains("\"message\"");
      for (String fragment : FORBIDDEN_FRAGMENTS) {
        assertThat(body).as("상태 %d 본문에 '%s'", status, fragment).doesNotContain(fragment);
      }
    }
  }
}
