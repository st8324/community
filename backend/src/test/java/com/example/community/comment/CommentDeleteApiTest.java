package com.example.community.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.community.auth.Role;
import com.example.community.support.AuthFixtures;
import com.example.community.support.AuthFixtures.Account;
import com.example.community.support.CommentFixtures;
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

/** CMT-03 댓글 삭제, ADM-02·ADM-03 (01 문서 v0.6). 역할 × 작성자 전체 조합은 권한 매트릭스 M-42~M-46. */
@IntegrationTest
class CommentDeleteApiTest {

  @Autowired MockMvc mvc;
  @Autowired AuthFixtures fixtures;
  @Autowired PostFixtures posts;
  @Autowired CommentFixtures comments;

  ResultActions remove(Object commentId, Cookie token) throws Exception {
    var request = delete("/api/comments/{id}", commentId).with(csrf());
    if (token != null) {
      request.cookie(token);
    }
    return mvc.perform(request);
  }

  long newPost() {
    return posts.create(fixtures.createUser(Role.USER).id());
  }

  // ── ① 소프트 삭제 ────────────────────────────────────────

  @Test
  void 작성자가_삭제하면_204이고_행은_남은_채_삭제_기록만_남는다() throws Exception {
    Account me = fixtures.createUser(Role.USER);
    long id = comments.create(newPost(), me.id());

    MvcResult result = remove(id, fixtures.login(me)).andExpect(status().isNoContent()).andReturn();
    assertThat(result.getResponse().getContentAsString()).isEmpty();

    Map<String, Object> row = comments.row(id);
    assertThat(row.get("deleted_at")).isNotNull();
    assertThat(((Number) row.get("deleted_by")).longValue()).isEqualTo(me.id());
    assertThat(row.get("content")).as("행과 내용은 그대로 남는다 (DATA-03)").isNotNull();
  }

  @Test
  void 삭제한_댓글은_목록에서_빠진다() throws Exception {
    Account me = fixtures.createUser(Role.USER);
    long postId = newPost();
    long id = comments.create(postId, me.id());

    remove(id, fixtures.login(me)).andExpect(status().isNoContent());

    String list =
        mvc.perform(get("/api/posts/{id}/comments", postId))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    List<Number> ids = JsonPath.read(list, "$.items[*].id");
    assertThat(ids.stream().map(Number::longValue)).doesNotContain(id);
  }

  // ── ② ⑦ 권한, ADM-02·03 관리자 ───────────────────────────

  @Test
  void 관리자가_타인_댓글을_삭제하면_204이고_삭제자는_관리자다() throws Exception {
    Account admin = fixtures.createUser(Role.ADMIN);
    long id = comments.create(newPost(), fixtures.createUser(Role.USER).id());

    remove(id, fixtures.login(admin)).andExpect(status().isNoContent());

    assertThat(((Number) comments.row(id).get("deleted_by")).longValue()).isEqualTo(admin.id());
  }

  @Test
  void 타인의_댓글을_삭제하면_403이고_댓글은_그대로다() throws Exception {
    long id = comments.create(newPost(), fixtures.createUser(Role.USER).id());

    remove(id, fixtures.loginAsNew(Role.USER))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));

    assertThat(comments.row(id).get("deleted_at")).isNull();
  }

  @Test
  void 글_작성자도_타인이_쓴_댓글은_삭제할_수_없다() throws Exception {
    Account postAuthor = fixtures.createUser(Role.USER);
    long postId = posts.create(postAuthor.id());
    long id = comments.create(postId, fixtures.createUser(Role.USER).id());

    remove(id, fixtures.login(postAuthor)).andExpect(status().isForbidden());
  }

  // ── ③ ④ 없음·이미 삭제·글 삭제됨 → 404 ─────────────────────

  @Test
  void 존재하지_않는_댓글은_404다() throws Exception {
    remove(Long.MAX_VALUE, fixtures.loginAsNew(Role.USER))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void 이미_삭제된_댓글은_404다() throws Exception {
    Account me = fixtures.createUser(Role.USER);
    long id = comments.create(newPost(), me.id());
    comments.softDelete(id, me.id());

    remove(id, fixtures.login(me)).andExpect(status().isNotFound());
  }

  @Test
  void 소속_글이_삭제된_댓글은_404이고_댓글_행은_바뀌지_않는다() throws Exception {
    Account me = fixtures.createUser(Role.USER);
    long postId = posts.create(me.id());
    long id = comments.create(postId, me.id());
    posts.softDelete(postId, me.id());

    remove(id, fixtures.login(me)).andExpect(status().isNotFound());

    assertThat(comments.row(id).get("deleted_at")).isNull();
  }

  // ── ⑤ 검사 순서: 401 → 404 → 403, ⑥ id 형식 ───────────────

  @Test
  void 비로그인은_401이다() throws Exception {
    long id = comments.create(newPost(), fixtures.createUser(Role.USER).id());

    remove(id, null)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    assertThat(comments.row(id).get("deleted_at")).isNull();
  }

  @Test
  void 검사_순서_비로그인이면_없는_댓글도_401이다() throws Exception {
    remove(Long.MAX_VALUE, null).andExpect(status().isUnauthorized());
  }

  @Test
  void 검사_순서_없는_댓글은_권한과_무관하게_404다() throws Exception {
    // 일반 회원이 "없는" 댓글을 지우려 해도 403이 아니라 404 — 존재 여부로 새는 정보가 없다 (D-29).
    remove(Long.MAX_VALUE, fixtures.loginAsNew(Role.USER)).andExpect(status().isNotFound());
  }

  @Test
  void 숫자가_아닌_댓글_id는_400_INVALID_REQUEST다() throws Exception {
    remove("abc", fixtures.loginAsNew(Role.USER))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }
}
