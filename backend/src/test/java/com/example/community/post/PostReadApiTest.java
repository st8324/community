package com.example.community.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.community.auth.Role;
import com.example.community.support.AuthFixtures;
import com.example.community.support.AuthFixtures.Account;
import com.example.community.support.IntegrationTest;
import com.example.community.support.PostFixtures;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * POST-03 게시글 상세 (01 문서 v0.5). 역할 × 소유자별 {@code canEdit}/{@code canDelete}의 전체 조합은 권한
 * 매트릭스(M-16~M-20)가 검증하고, 여기서는 AC의 나머지 조건을 본다.
 */
@IntegrationTest
class PostReadApiTest {

  @Autowired MockMvc mvc;
  @Autowired AuthFixtures fixtures;
  @Autowired PostFixtures posts;

  // ── ① 반환 필드 ────────────────────────────────────────

  @Test
  void 비회원도_상세를_조회할_수_있고_정해진_필드만_받는다() throws Exception {
    Account author = fixtures.createUser(Role.USER);
    long id = posts.create(author.id(), "제목", "본문\n둘째 줄", LocalDateTime.of(2026, 10, 17, 1, 2, 3));

    MvcResult result =
        mvc.perform(get("/api/posts/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.title").value("제목"))
            .andExpect(jsonPath("$.content").value("본문\n둘째 줄"))
            .andExpect(jsonPath("$.createdAt").value("2026-10-17T01:02:03Z"))
            .andExpect(jsonPath("$.canEdit").value(false))
            .andExpect(jsonPath("$.canDelete").value(false))
            .andReturn();

    String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    Map<String, Object> body = JsonPath.read(json, "$");
    assertThat(body)
        .containsOnlyKeys(
            "id",
            "title",
            "content",
            "authorName",
            "createdAt",
            "updatedAt",
            "canEdit",
            "canDelete");
    assertThat(body.get("updatedAt")).isNull();
    // DATA-05: 작성자 이메일 원문·id·role은 내보내지 않는다
    assertThat(json).doesNotContain(author.email());
  }

  @Test
  void 작성자_표시명은_A_1_규칙으로_마스킹된다() throws Exception {
    Account author = fixtures.createUser(Role.USER);
    long id = posts.create(author.id());

    String local = author.email().substring(0, author.email().indexOf('@'));
    mvc.perform(get("/api/posts/{id}", id))
        .andExpect(jsonPath("$.authorName").value(local.substring(0, 2) + "***@example.com"));
  }

  @Test
  void 작성자가_보면_canEdit과_canDelete가_true다() throws Exception {
    Account author = fixtures.createUser(Role.USER);
    long id = posts.create(author.id());

    mvc.perform(get("/api/posts/{id}", id).cookie(fixtures.login(author)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.canEdit").value(true))
        .andExpect(jsonPath("$.canDelete").value(true));
  }

  // ── ② 없는 글·삭제된 글 ─────────────────────────────────

  @Test
  void 없는_글은_404_NOT_FOUND다() throws Exception {
    mvc.perform(get("/api/posts/{id}", Long.MAX_VALUE))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void 삭제된_글은_작성자가_봐도_404다() throws Exception {
    Account author = fixtures.createUser(Role.USER);
    long id = posts.create(author.id());
    posts.softDelete(id, author.id());

    mvc.perform(get("/api/posts/{id}", id).cookie(fixtures.login(author)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void 숫자가_아닌_id는_400_INVALID_REQUEST다() throws Exception {
    mvc.perform(get("/api/posts/{id}", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  // ── ④ 공개 조회에 무효 토큰 → 401 (D-20) ─────────────────

  @Test
  void 망가진_토큰_쿠키와_함께_조회하면_비회원으로_보지_않고_401이다() throws Exception {
    long id = posts.create(fixtures.createUser(Role.USER).id());

    mvc.perform(
            get("/api/posts/{id}", id).cookie(new Cookie(AuthFixtures.ACCESS_TOKEN, "not-a-jwt")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void 쿠키가_없으면_같은_글을_비회원으로_200_조회한다() throws Exception {
    long id = posts.create(fixtures.createUser(Role.USER).id());
    mvc.perform(get("/api/posts/{id}", id)).andExpect(status().isOk());
  }
}
