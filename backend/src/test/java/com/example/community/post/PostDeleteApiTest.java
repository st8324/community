package com.example.community.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * POST-05 게시글 삭제, ADM-02·ADM-03 관리자 삭제·추적 (01 문서 v0.5). 역할 × 소유자 전체 조합은 권한 매트릭스 M-29~M-33.
 *
 * <p>POST-05 ④(삭제된 글의 댓글 비노출)는 댓글 API가 생기는 댓글 PR에서 검증한다 (01 D-25, 03 문서 5.5절).
 */
@IntegrationTest
class PostDeleteApiTest {

  @Autowired MockMvc mvc;
  @Autowired AuthFixtures fixtures;
  @Autowired PostFixtures posts;

  ResultActions remove(Object postId, Cookie token) throws Exception {
    var request = delete("/api/posts/{id}", postId).with(csrf());
    if (token != null) {
      request.cookie(token);
    }
    return mvc.perform(request);
  }

  // ── ① 소프트 삭제 ──────────────────────────────────────

  @Test
  void 작성자가_삭제하면_204이고_행은_남은_채_삭제_기록만_남는다() throws Exception {
    Account me = fixtures.createUser(Role.USER);
    long id = posts.create(me.id());

    MvcResult result = remove(id, fixtures.login(me)).andExpect(status().isNoContent()).andReturn();
    assertThat(result.getResponse().getContentAsString()).isEmpty();

    Map<String, Object> row = posts.row(id);
    assertThat(row.get("deleted_at")).isNotNull();
    assertThat(((Number) row.get("deleted_by")).longValue()).isEqualTo(me.id());
    assertThat(row.get("title")).as("행과 내용은 그대로 남는다 (DATA-03)").isNotNull();
  }

  @Test
  void 삭제한_글은_상세에서_404이고_목록에서_빠진다() throws Exception {
    Account me = fixtures.createUser(Role.USER);
    long id = posts.create(me.id(), "곧 지울 글", "c", posts.latestCreatedAt().plusMinutes(1));

    remove(id, fixtures.login(me)).andExpect(status().isNoContent());

    mvc.perform(get("/api/posts/{id}", id)).andExpect(status().isNotFound());
    String list =
        mvc.perform(get("/api/posts"))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    List<Number> ids = JsonPath.read(list, "$.items[*].id");
    assertThat(ids.stream().map(Number::longValue)).doesNotContain(id);
  }

  // ── ② 권한, ADM-02·03 관리자 ─────────────────────────────

  @Test
  void 관리자는_타인_글을_삭제할_수_있고_삭제자로_관리자가_기록된다() throws Exception {
    long id = posts.create(fixtures.createUser(Role.USER).id());
    Account admin = fixtures.createUser(Role.ADMIN);

    remove(id, fixtures.login(admin)).andExpect(status().isNoContent());

    assertThat(((Number) posts.row(id).get("deleted_by")).longValue()).isEqualTo(admin.id());
  }

  @Test
  void 회원이_타인_글을_삭제하면_403이고_글은_그대로다() throws Exception {
    long id = posts.create(fixtures.createUser(Role.USER).id());

    remove(id, fixtures.loginAsNew(Role.USER))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));

    assertThat(posts.row(id).get("deleted_at")).isNull();
    mvc.perform(get("/api/posts/{id}", id)).andExpect(status().isOk());
  }

  // ── ③ 이미 삭제된 글, 검사 순서 (D-24) ────────────────────

  @Test
  void 이미_삭제된_글은_404다() throws Exception {
    Account me = fixtures.createUser(Role.USER);
    long id = posts.create(me.id());
    Cookie token = fixtures.login(me);

    remove(id, token).andExpect(status().isNoContent());
    remove(id, token)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void 이미_삭제된_글을_다시_지워도_처음_삭제_기록은_바뀌지_않는다() throws Exception {
    Account author = fixtures.createUser(Role.USER);
    long id = posts.create(author.id());
    posts.softDelete(id, author.id());

    remove(id, fixtures.loginAsNew(Role.ADMIN)).andExpect(status().isNotFound());

    assertThat(((Number) posts.row(id).get("deleted_by")).longValue()).isEqualTo(author.id());
  }

  @Test
  void 삭제된_타인_글은_403이_아니라_404다() throws Exception {
    Account author = fixtures.createUser(Role.USER);
    long id = posts.create(author.id());
    posts.softDelete(id, author.id());

    remove(id, fixtures.loginAsNew(Role.USER)).andExpect(status().isNotFound());
  }

  @Test
  void 없는_글은_404이고_숫자가_아닌_id는_400이다() throws Exception {
    Cookie token = fixtures.loginAsNew(Role.USER);
    remove(Long.MAX_VALUE, token).andExpect(status().isNotFound());
    remove("abc", token)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void 로그인하지_않으면_401이다() throws Exception {
    long id = posts.create(fixtures.createUser(Role.USER).id());

    remove(id, null)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    assertThat(posts.row(id).get("deleted_at")).isNull();
  }
}
