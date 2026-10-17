package com.example.community.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.community.auth.Role;
import com.example.community.support.AuthFixtures;
import com.example.community.support.AuthFixtures.Account;
import com.example.community.support.IntegrationTest;
import com.example.community.support.JsonBodies;
import com.example.community.support.PostFixtures;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** POST-04 게시글 수정 (01 문서 v0.5). 역할 × 소유자 전체 조합은 권한 매트릭스 M-24~M-28. */
@IntegrationTest
class PostUpdateApiTest {

  @Autowired MockMvc mvc;
  @Autowired AuthFixtures fixtures;
  @Autowired PostFixtures posts;

  ResultActions edit(long postId, Cookie token, Object... body) throws Exception {
    var request =
        put("/api/posts/{id}", postId)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(JsonBodies.of(body));
    if (token != null) {
      request.cookie(token);
    }
    return mvc.perform(request);
  }

  // ── ③ ⑤ 성공 ──────────────────────────────────────────

  @Test
  void 작성자가_수정하면_200이고_수정된_상세를_주며_updatedAt이_생긴다() throws Exception {
    Account me = fixtures.createUser(Role.USER);
    long id = posts.create(me.id());

    edit(id, fixtures.login(me), "title", " 고친 제목 ", "content", "고친 본문")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.title").value("고친 제목"))
        .andExpect(jsonPath("$.content").value("고친 본문"))
        .andExpect(jsonPath("$.updatedAt").value(endsWith("Z")))
        .andExpect(jsonPath("$.canEdit").value(true))
        .andExpect(jsonPath("$.canDelete").value(true));

    Map<String, Object> row = posts.row(id);
    assertThat(row.get("title")).isEqualTo("고친 제목");
    assertThat(row.get("content")).isEqualTo("고친 본문");
    assertThat(row.get("updated_at")).as("수정 시각 기록 (③)").isNotNull();
  }

  @Test
  void 요청에_authorId를_넣어도_작성자는_바뀌지_않는다() throws Exception {
    Account me = fixtures.createUser(Role.USER);
    Account other = fixtures.createUser(Role.USER);
    long id = posts.create(me.id());

    edit(id, fixtures.login(me), "title", "t", "content", "c", "authorId", other.id())
        .andExpect(status().isOk());

    assertThat(((Number) posts.row(id).get("author_id")).longValue()).isEqualTo(me.id());
  }

  // ── ② 검증 규칙은 POST-01과 같다 ─────────────────────────

  @Test
  void 수정도_작성과_같은_검증_규칙이다() throws Exception {
    Account me = fixtures.createUser(Role.USER);
    long id = posts.create(me.id());
    Cookie token = fixtures.login(me);

    edit(id, token, "title", "가".repeat(101), "content", "c")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fields[0]").value("title"));
    edit(id, token, "title", "t", "content", "  ")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields[0]").value("content"));
    // 제목만 보내는 일부 수정은 없다 (POST-04 ②)
    edit(id, token, "title", "t")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields[0]").value("content"));

    assertThat(posts.row(id).get("updated_at")).as("실패한 수정은 아무것도 바꾸지 않는다").isNull();
  }

  // ── ① 본인 글만 ────────────────────────────────────────

  @Test
  void 타인_글을_수정하면_403_FORBIDDEN이고_글은_그대로다() throws Exception {
    Account author = fixtures.createUser(Role.USER);
    long id = posts.create(author.id());
    Object before = posts.row(id).get("title");

    edit(id, fixtures.loginAsNew(Role.USER), "title", "가로채기", "content", "x")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));

    assertThat(posts.row(id).get("title")).isEqualTo(before);
    assertThat(posts.row(id).get("updated_at")).isNull();
  }

  @Test
  void 관리자도_타인_글은_수정할_수_없다() throws Exception {
    long id = posts.create(fixtures.createUser(Role.USER).id());

    edit(id, fixtures.loginAsNew(Role.ADMIN), "title", "관리자 수정", "content", "x")
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  // ── ④ 삭제된 글, ⑥ 검사 순서 401 → 400 → 404 → 403 (D-24) ──

  @Test
  void 삭제된_글은_작성자가_수정해도_404다() throws Exception {
    Account me = fixtures.createUser(Role.USER);
    long id = posts.create(me.id());
    posts.softDelete(id, me.id());

    edit(id, fixtures.login(me), "title", "t", "content", "c")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void 없는_글은_404다() throws Exception {
    edit(Long.MAX_VALUE, fixtures.loginAsNew(Role.USER), "title", "t", "content", "c")
        .andExpect(status().isNotFound());
  }

  @Test
  void 삭제된_타인_글은_403이_아니라_404다() throws Exception {
    Account author = fixtures.createUser(Role.USER);
    long id = posts.create(author.id());
    posts.softDelete(id, author.id());

    edit(id, fixtures.loginAsNew(Role.USER), "title", "t", "content", "c")
        .andExpect(status().isNotFound());
  }

  @Test
  void 입력이_잘못되면_타인_글이어도_먼저_400이다() throws Exception {
    long id = posts.create(fixtures.createUser(Role.USER).id());

    edit(id, fixtures.loginAsNew(Role.USER), "title", "", "content", "c")
        .andExpect(status().isBadRequest());
  }

  @Test
  void 로그인하지_않으면_401이다() throws Exception {
    long id = posts.create(fixtures.createUser(Role.USER).id());

    edit(id, null, "title", "t", "content", "c")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }
}
