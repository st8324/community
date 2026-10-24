package com.example.community.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.community.auth.Role;
import com.example.community.support.AuthFixtures;
import com.example.community.support.IntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SPA 경로 포워딩 (02 문서 T-18, 03 문서 HD-30). 브라우저 주소 방식이라 {@code /posts/3}을 새로고침하면 서버가 그 경로를 받는다.
 *
 * <p>테스트 클래스패스의 {@code static/index.html}은 프론트 빌드 결과를 대신하는 표식 파일이다 (운영에서는 Docker 빌드가 실제 파일을 넣는다).
 */
@IntegrationTest
class SpaForwardingTest {

  static final String INDEX_MARKER = "community-test-index";

  @Autowired MockMvc mvc;
  @Autowired AuthFixtures fixtures;

  @ParameterizedTest
  @ValueSource(
      strings = {"/login", "/signup", "/posts/3", "/posts/new", "/posts/3/edit", "/no/such/page"})
  void 점_없는_GET_경로는_index_html을_돌려준다(String path) throws Exception {
    mvc.perform(get(path))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
        .andExpect(content().string(containsString(INDEX_MARKER)));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/no-such-api", "/api/posts/1/no-such"})
  void 없는_API_경로는_index_html이_아니라_JSON_404다(String path) throws Exception {
    // /api/**는 로그인 필요이므로 로그인한 상태로 확인한다 (비회원이면 401).
    mvc.perform(get(path).cookie(fixtures.loginAsNew(Role.USER)))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/actuator/env", "/actuator/beans"})
  void actuator_경로는_index_html이_아니라_JSON_404다(String path) throws Exception {
    mvc.perform(get(path))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/missing.js", "/assets/missing-1a2b3c.js", "/favicon.ico"})
  void 점이_있는_경로는_파일_요청이므로_JSON_404다(String path) throws Exception {
    mvc.perform(get(path))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/posts/3", "/login"})
  void GET이_아닌_요청은_index_html을_돌려주지_않는다(String path) throws Exception {
    // 정적 리소스 처리기는 "리소스 없음(404)"을 "메서드 불허(405)"보다 먼저 판단한다. 요구하는 것은 index.html이 아니라는 점이다.
    mvc.perform(post(path).with(csrf()))
        .andExpect(status().is4xxClientError())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(content().string(not(containsString(INDEX_MARKER))));
  }
}
