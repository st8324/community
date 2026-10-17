package com.example.community.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.example.community.auth.Role;
import com.example.community.support.AuthFixtures;
import com.example.community.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * SPA 경로 포워딩 (02 문서 5.8절 T-18·T-22, 03 문서 HD-30).
 *
 * <p>브라우저 주소 방식 라우팅이라 {@code /posts/3}을 새로고침하면 서버가 그 경로를 받는다. 화면 경로는 {@code index.html}로 넘기되,
 * API·actuator 오류와 없는 정적 파일은 HTML로 가리지 않아야 프론트가 오류를 읽을 수 있다. 테스트용 {@code index.html}은 {@code
 * src/test/resources/static}에 있다 (제목 {@code spa-test-index}).
 */
@IntegrationTest
class SpaForwardingTest {

  static final String INDEX_MARKER = "<title>spa-test-index</title>";

  @Autowired MockMvc mvc;
  @Autowired AuthFixtures fixtures;

  @ParameterizedTest
  @ValueSource(strings = {"/", "/posts/3", "/posts/new", "/posts/3/edit", "/login", "/signup"})
  void 화면_경로를_새로고침하면_index_html을_준다(String path) throws Exception {
    MvcResult result = mvc.perform(get(path)).andReturn();

    assertIndexHtml(result);
  }

  /** T-22: 액세스 쿠키는 Path=/라 화면 요청에도 실린다. 무효 토큰 때문에 앱이 안 뜨면 프론트의 복구 경로(T-19)도 못 탄다. */
  @Test
  void 망가진_토큰_쿠키가_있어도_화면과_정적_파일은_정상으로_준다() throws Exception {
    Cookie broken = new Cookie(AuthFixtures.ACCESS_TOKEN, "not-a-jwt");

    assertIndexHtml(mvc.perform(get("/posts/3").cookie(broken)).andReturn());
    assertIndexHtml(mvc.perform(get("/index.html").cookie(broken)).andReturn());
  }

  @Test
  void 망가진_토큰_쿠키로_API를_부르면_여전히_401이다() throws Exception {
    Cookie broken = new Cookie(AuthFixtures.ACCESS_TOKEN, "not-a-jwt");

    MvcResult result = mvc.perform(get("/api/posts").cookie(broken)).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(401); // 01 D-20, 02 T-14 유지
    assertJsonError(result);
  }

  @Test
  void 비회원이_없는_API_경로를_부르면_HTML이_아니라_401_JSON이다() throws Exception {
    MvcResult result = mvc.perform(get("/api/does-not-exist")).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(401);
    assertJsonError(result);
  }

  @Test
  void 회원이_없는_API_경로를_부르면_HTML이_아니라_404_JSON이다() throws Exception {
    Cookie token = fixtures.loginAsNew(Role.USER);

    MvcResult result = mvc.perform(get("/api/does-not-exist").cookie(token)).andReturn();

    assertThat(result.getResponse().getStatus()).isEqualTo(404);
    assertJsonError(result);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/assets/missing.js", "/favicon-missing.ico", "/actuator/env", "/api"})
  void 점이_있는_없는_파일과_actuator_api는_index_html로_넘기지_않는다(String path) throws Exception {
    MvcResult result = mvc.perform(get(path)).andReturn();

    assertThat(result.getResponse().getStatus()).as(path).isBetween(400, 499);
    assertThat(body(result)).as(path).doesNotContain(INDEX_MARKER);
  }

  private static void assertIndexHtml(MvcResult result) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(result.getResponse().getContentType()).startsWith(MediaType.TEXT_HTML_VALUE);
    assertThat(body(result)).contains(INDEX_MARKER);
  }

  private static void assertJsonError(MvcResult result) throws Exception {
    assertThat(result.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
    assertThat(body(result)).contains("\"code\"").doesNotContain(INDEX_MARKER);
  }

  private static String body(MvcResult result) throws Exception {
    return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  }
}
