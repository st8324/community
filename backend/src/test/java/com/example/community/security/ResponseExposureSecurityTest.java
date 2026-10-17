package com.example.community.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.community.auth.Role;
import com.example.community.support.AuthFixtures;
import com.example.community.support.AuthFixtures.Account;
import com.example.community.support.IntegrationTest;
import com.example.community.support.JsonBodies;
import com.example.community.support.PostFixtures;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * S-10 (DATA-05), S-11 (SEC-08).
 *
 * <p><b>아직 PENDING</b> (03 문서 HD-21, HD-28): 두 항목은 "모든 응답"이 대상이라 댓글 응답이 생긴 뒤에 ACTIVE로 바꾼다. 게시글 PR은
 * 게시글 응답을 먼저 검사하고, 댓글 PR이 같은 메서드에 댓글 응답을 추가한다. PENDING이어도 테스트 자체는 매 빌드 실행된다.
 */
@IntegrationTest
class ResponseExposureSecurityTest {

  /** 응답 본문 어디에도 나오면 안 되는 키 이름 (DATA-05). 값이 아니라 JSON 키로 찾는다. */
  static final List<String> FORBIDDEN_KEYS =
      List.of(
          "\"password\"",
          "\"passwordHash\"",
          "\"password_hash\"",
          "\"role\"",
          "\"email\"",
          "\"authorId\"",
          "\"author_id\"",
          "\"deletedAt\"",
          "\"deleted_at\"",
          "\"deletedBy\"",
          "\"deleted_by\"");

  @Autowired MockMvc mvc;
  @Autowired AuthFixtures fixtures;
  @Autowired PostFixtures posts;

  /** S-10: 사용자가 볼 수 있는 응답을 모두 모아, 민감 키와 이메일 원문·역할 이름이 없는지 본다. */
  @Test
  void responsesDoNotLeakSensitiveFields() throws Exception {
    Account admin = fixtures.createUser(Role.ADMIN);
    Cookie adminToken = fixtures.login(admin);
    Account author = fixtures.createUser(Role.USER);
    Cookie authorToken = fixtures.login(author);
    long postId = posts.create(author.id());

    List<String> bodies = new ArrayList<>();
    // 게시글 작성 (작성자 본인 응답)
    bodies.add(
        body(
            mvc.perform(
                    post("/api/posts")
                        .with(csrf())
                        .cookie(authorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JsonBodies.of("title", "t", "content", "c")))
                .andReturn()));
    // 게시글 상세 — 비회원·작성자·관리자 시점
    bodies.add(body(mvc.perform(get("/api/posts/{id}", postId)).andReturn()));
    bodies.add(body(mvc.perform(get("/api/posts/{id}", postId).cookie(authorToken)).andReturn()));
    bodies.add(body(mvc.perform(get("/api/posts/{id}", postId).cookie(adminToken)).andReturn()));
    // 게시글 목록 — 비회원·관리자 시점 (방금 만든 글이 들어 있는 첫 페이지)
    bodies.add(body(mvc.perform(get("/api/posts")).andReturn()));
    bodies.add(body(mvc.perform(get("/api/posts").cookie(adminToken)).andReturn()));
    // (수정 PR, 댓글 PR에서 여기에 응답을 추가한다)

    for (String body : bodies) {
      assertThat(body).as("응답이 비어 있으면 검사가 무의미하다").isNotBlank();
      for (String key : FORBIDDEN_KEYS) {
        assertThat(body).as("금지 키 %s", key).doesNotContain(key);
      }
      assertThat(body)
          .doesNotContain(author.email())
          .doesNotContain(admin.email())
          .doesNotContain("\"ADMIN\"")
          .doesNotContain("\"USER\"");
    }
  }

  /**
   * S-11: 스크립트·HTML 문자열은 저장할 때 바꾸지도 않고(이스케이프·제거 없음), 조회할 때 JSON 문자열로 그대로 돌려준다. 이스케이프는 화면(React)의
   * 몫이다. 서버가 미리 HTML 이스케이프하면 화면에서 {@code &lt;}가 글자로 보이는 이중 이스케이프가 생긴다.
   */
  @Test
  void scriptStringRoundTripsAsPlainText() throws Exception {
    String title = "<script>alert('t')</script>";
    String content = "<img src=x onerror=\"alert(1)\">&amp; \\ </textarea> {{7*7}} ${7*7}";
    Cookie token = fixtures.loginAsNew(Role.USER);

    MvcResult created =
        mvc.perform(
                post("/api/posts")
                    .with(csrf())
                    .cookie(token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(JsonBodies.of("title", title, "content", content)))
            .andReturn();
    assertThat(created.getResponse().getStatus()).isEqualTo(201);
    Number id = JsonPath.read(body(created), "$.id");

    MvcResult read = mvc.perform(get("/api/posts/{id}", id.longValue())).andReturn();
    assertThat(read.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
    String json = body(read);
    assertThat((String) JsonPath.read(json, "$.title")).isEqualTo(title);
    assertThat((String) JsonPath.read(json, "$.content")).isEqualTo(content);
    // (댓글 PR에서 댓글 본문도 같은 방식으로 추가한다)
  }

  private static String body(MvcResult result) throws Exception {
    return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  }
}
