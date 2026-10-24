package com.example.community.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * CMT-02 댓글 조회 (01 문서 v0.6). 역할 × 작성자 조합의 {@code canDelete}는 권한 매트릭스 M-34~M-38.
 *
 * <p>POST-05 ④(삭제된 글의 댓글은 조회되지 않는다)는 이 클래스의 해당 테스트가 검증한다 (01 D-25, 03 문서 5.7절).
 */
@IntegrationTest
class CommentListApiTest {

  @Autowired MockMvc mvc;
  @Autowired AuthFixtures fixtures;
  @Autowired PostFixtures posts;
  @Autowired CommentFixtures comments;

  MvcResult list(Object postId, Cookie token) throws Exception {
    var request = get("/api/posts/{id}/comments", postId);
    if (token != null) {
      request.cookie(token);
    }
    return mvc.perform(request).andReturn();
  }

  static String body(MvcResult result) throws Exception {
    return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  static List<String> contents(MvcResult result) throws Exception {
    return JsonPath.read(body(result), "$.items[*].content");
  }

  // ── ⑥ 빈 목록, 형태 ──────────────────────────────────────

  @Test
  void 댓글이_없으면_200이고_items는_빈_배열이다() throws Exception {
    long postId = posts.create(fixtures.createUser(Role.USER).id());

    MvcResult result = list(postId, null);

    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat((List<?>) JsonPath.read(body(result), "$.items")).isEmpty();
  }

  @Test
  void 항목은_id_content_authorName_createdAt_canDelete만_가진다() throws Exception {
    Account author = fixtures.createUser(Role.USER);
    long postId = posts.create(author.id());
    comments.create(postId, author.id());

    mvc.perform(get("/api/posts/{id}/comments", postId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].authorName").value(endsWith("@example.com")))
        .andExpect(jsonPath("$.items[0].createdAt").value(endsWith("Z")));

    Map<String, Object> item = JsonPath.read(body(list(postId, null)), "$.items[0]");
    assertThat(item).containsOnlyKeys("id", "content", "authorName", "createdAt", "canDelete");
    assertThat(JsonPath.<Map<String, Object>>read(body(list(postId, null)), "$"))
        .containsOnlyKeys("items");
  }

  // ── ② 작성순(오래된 순), ③ 삭제된 댓글 제외 ────────────────

  @Test
  void 오래된_댓글이_먼저_나오고_시각이_같으면_id_순이다() throws Exception {
    Account author = fixtures.createUser(Role.USER);
    long postId = posts.create(author.id());
    LocalDateTime t = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
    // 저장 순서를 일부러 시각 순서와 다르게 넣는다.
    comments.create(postId, author.id(), "셋째", t.plusMinutes(2));
    comments.create(postId, author.id(), "첫째", t);
    comments.create(postId, author.id(), "둘째-가", t.plusMinutes(1));
    comments.create(postId, author.id(), "둘째-나", t.plusMinutes(1));

    assertThat(contents(list(postId, null))).containsExactly("첫째", "둘째-가", "둘째-나", "셋째");
  }

  @Test
  void 삭제된_댓글은_목록에서_빠진다() throws Exception {
    Account author = fixtures.createUser(Role.USER);
    long postId = posts.create(author.id());
    comments.create(postId, author.id(), "남는 댓글", LocalDateTime.of(2026, 1, 1, 0, 0, 0));
    long removed =
        comments.create(postId, author.id(), "지운 댓글", LocalDateTime.of(2026, 1, 1, 0, 1, 0));
    comments.softDelete(removed, author.id());

    assertThat(contents(list(postId, null))).containsExactly("남는 댓글");
  }

  @Test
  void 다른_글의_댓글은_섞이지_않는다() throws Exception {
    Account author = fixtures.createUser(Role.USER);
    long mine = posts.create(author.id());
    long other = posts.create(author.id());
    comments.create(mine, author.id(), "내 글 댓글", LocalDateTime.of(2026, 1, 1, 0, 0, 0));
    comments.create(other, author.id(), "다른 글 댓글", LocalDateTime.of(2026, 1, 1, 0, 0, 0));

    assertThat(contents(list(mine, null))).containsExactly("내 글 댓글");
  }

  // ── ④ canDelete는 요청자 기준 ─────────────────────────────

  @Test
  void canDelete는_비회원_false_작성자_true_타인_false_관리자_모두_true다() throws Exception {
    Account author = fixtures.createUser(Role.USER);
    Account other = fixtures.createUser(Role.USER);
    long postId = posts.create(author.id());
    comments.create(postId, author.id(), "내 댓글", LocalDateTime.of(2026, 1, 1, 0, 0, 0));
    comments.create(postId, other.id(), "남의 댓글", LocalDateTime.of(2026, 1, 1, 0, 1, 0));

    assertThat(canDeleteFlags(list(postId, null))).containsExactly(false, false);
    assertThat(canDeleteFlags(list(postId, fixtures.login(author)))).containsExactly(true, false);
    assertThat(canDeleteFlags(list(postId, fixtures.login(other)))).containsExactly(false, true);
    assertThat(canDeleteFlags(list(postId, fixtures.loginAsNew(Role.ADMIN))))
        .containsExactly(true, true);
  }

  private static List<Boolean> canDeleteFlags(MvcResult result) throws Exception {
    return JsonPath.read(body(result), "$.items[*].canDelete");
  }

  // ── ⑤ 없는·삭제된 글 → 404 (POST-05 ④) ─────────────────────

  @Test
  void 존재하지_않는_글의_댓글은_404다() throws Exception {
    mvc.perform(get("/api/posts/{id}/comments", Long.MAX_VALUE))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void POST_05_AC4_삭제된_글의_댓글은_조회되지_않는다_404다() throws Exception {
    Account author = fixtures.createUser(Role.USER);
    long postId = posts.create(author.id());
    comments.create(postId, author.id());
    mvc.perform(get("/api/posts/{id}/comments", postId)).andExpect(status().isOk());

    posts.softDelete(postId, author.id());

    mvc.perform(get("/api/posts/{id}/comments", postId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  // ── ⑦ 무효 토큰 → 401 (D-20), ⑧ id 형식 ───────────────────

  @Test
  void 망가진_토큰_쿠키와_함께_조회하면_비회원으로_보지_않고_401이다() throws Exception {
    long postId = posts.create(fixtures.createUser(Role.USER).id());

    mvc.perform(
            get("/api/posts/{id}/comments", postId)
                .cookie(new Cookie(AuthFixtures.ACCESS_TOKEN, "not-a-jwt")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void 쿠키가_없으면_같은_글의_댓글을_비회원으로_200_조회한다() throws Exception {
    long postId = posts.create(fixtures.createUser(Role.USER).id());
    mvc.perform(get("/api/posts/{id}/comments", postId)).andExpect(status().isOk());
  }

  @Test
  void 숫자가_아닌_글_id는_400_INVALID_REQUEST다() throws Exception {
    mvc.perform(get("/api/posts/{id}/comments", "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }
}
