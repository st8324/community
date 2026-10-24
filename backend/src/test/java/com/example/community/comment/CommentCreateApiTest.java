package com.example.community.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.community.auth.Role;
import com.example.community.support.AuthFixtures;
import com.example.community.support.AuthFixtures.Account;
import com.example.community.support.CommentFixtures;
import com.example.community.support.IntegrationTest;
import com.example.community.support.JsonBodies;
import com.example.community.support.PostFixtures;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** CMT-01 댓글 작성 (01 문서 v0.6). 역할별 허용 여부는 권한 매트릭스 M-39~M-41. */
@IntegrationTest
class CommentCreateApiTest {

  static final String EMOJI = "😀";

  @Autowired MockMvc mvc;
  @Autowired AuthFixtures fixtures;
  @Autowired PostFixtures posts;
  @Autowired CommentFixtures comments;

  long somePost() {
    return posts.create(fixtures.createUser(Role.USER).id());
  }

  ResultActions write(Object postId, Cookie token, Object... body) throws Exception {
    var request =
        post("/api/posts/{id}/comments", postId)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(JsonBodies.of(body));
    if (token != null) {
      request.cookie(token);
    }
    return mvc.perform(request);
  }

  static long idOf(MvcResult result) throws Exception {
    Number id =
        JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
    return id.longValue();
  }

  // ── ⑥ 성공: 201 + 목록 항목과 같은 형태 ─────────────────────

  @Test
  void 회원이_댓글을_쓰면_201이고_목록_항목과_같은_형태의_본문을_준다() throws Exception {
    Account me = fixtures.createUser(Role.USER);

    MvcResult result =
        write(somePost(), fixtures.login(me), "content", "첫 댓글")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.content").value("첫 댓글"))
            .andExpect(jsonPath("$.authorName").value(endsWith("@example.com")))
            .andExpect(jsonPath("$.authorName").value(containsString("***")))
            .andExpect(jsonPath("$.createdAt").value(endsWith("Z")))
            .andExpect(jsonPath("$.canDelete").value(true))
            .andReturn();

    Map<String, Object> body =
        JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$");
    assertThat(body).containsOnlyKeys("id", "content", "authorName", "createdAt", "canDelete");
  }

  @Test
  void 관리자도_댓글을_쓸_수_있다() throws Exception {
    write(somePost(), fixtures.loginAsNew(Role.ADMIN), "content", "공지 댓글")
        .andExpect(status().isCreated());
  }

  // ── ③ 작성자·글은 서버가 정한다 ───────────────────────────

  @Test
  void 작성자_글_작성_시각이_DB에_기록되고_삭제_기록은_비어_있다() throws Exception {
    Account me = fixtures.createUser(Role.USER);
    long postId = somePost();

    long id = idOf(write(postId, fixtures.login(me), "content", "c").andReturn());

    Map<String, Object> row = comments.row(id);
    assertThat(((Number) row.get("author_id")).longValue()).isEqualTo(me.id());
    assertThat(((Number) row.get("post_id")).longValue()).isEqualTo(postId);
    assertThat(row.get("created_at")).isNotNull();
    assertThat(row.get("deleted_at")).isNull();
    assertThat(row.get("deleted_by")).isNull();
  }

  @Test
  void 요청에_authorId와_postId를_넣어도_무시한다_글은_경로로만_정해진다() throws Exception {
    Account me = fixtures.createUser(Role.USER);
    Account other = fixtures.createUser(Role.USER);
    long pathPost = somePost();
    long bodyPost = somePost();

    long id =
        idOf(
            write(
                    pathPost,
                    fixtures.login(me),
                    "content",
                    "c",
                    "authorId",
                    other.id(),
                    "postId",
                    bodyPost,
                    "id",
                    999999)
                .andExpect(status().isCreated())
                .andReturn());

    Map<String, Object> row = comments.row(id);
    assertThat(((Number) row.get("author_id")).longValue()).isEqualTo(me.id());
    assertThat(((Number) row.get("post_id")).longValue()).isEqualTo(pathPost);
  }

  // ── ① 앞뒤 공백 제거 후 저장, 코드 포인트 기준 길이 (D-28) ─────

  @Test
  void 앞뒤_공백을_제거한_값을_저장한다() throws Exception {
    long id =
        idOf(
            write(somePost(), fixtures.loginAsNew(Role.USER), "content", "  \t\n 안녕\n하세요 　 ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("안녕\n하세요"))
                .andReturn());

    assertThat(comments.row(id).get("content")).isEqualTo("안녕\n하세요");
  }

  @Test
  void 한_글자_댓글과_1000자_댓글은_허용한다() throws Exception {
    Cookie token = fixtures.loginAsNew(Role.USER);
    long postId = somePost();
    write(postId, token, "content", "가").andExpect(status().isCreated());
    write(postId, token, "content", "가".repeat(1_000)).andExpect(status().isCreated());
  }

  @Test
  void 이모지는_한_글자로_센다_이모지_1000개는_허용한다() throws Exception {
    write(somePost(), fixtures.loginAsNew(Role.USER), "content", EMOJI.repeat(1_000))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.content").value(EMOJI.repeat(1_000)));
  }

  @Test
  void 공백을_제거하면_1000자인_댓글은_허용한다() throws Exception {
    write(somePost(), fixtures.loginAsNew(Role.USER), "content", "  " + "a".repeat(1_000) + "  ")
        .andExpect(status().isCreated());
  }

  // ── ② 범위 밖·누락·공백만 → 400 ──────────────────────────

  @Test
  void 내용이_1001자면_400이고_fields는_content다() throws Exception {
    write(somePost(), fixtures.loginAsNew(Role.USER), "content", "a".repeat(1_001))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fields[0]").value("content"));
  }

  @Test
  void 이모지_1001개도_400이다() throws Exception {
    write(somePost(), fixtures.loginAsNew(Role.USER), "content", EMOJI.repeat(1_001))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields[0]").value("content"));
  }

  @Test
  void 공백만_있으면_400이다() throws Exception {
    write(somePost(), fixtures.loginAsNew(Role.USER), "content", " \t\n　 ")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fields[0]").value("content"));
  }

  @Test
  void 빈_문자열_null_누락은_모두_400이다() throws Exception {
    Cookie token = fixtures.loginAsNew(Role.USER);
    long postId = somePost();
    write(postId, token, "content", "")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields[0]").value("content"));
    write(postId, token, "content", null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields[0]").value("content"));
    write(postId, token, "other", "x")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields[0]").value("content"));
  }

  // ── ④ 없는·삭제된 글 → 404 ───────────────────────────────

  @Test
  void 존재하지_않는_글에는_404다() throws Exception {
    write(Long.MAX_VALUE, fixtures.loginAsNew(Role.USER), "content", "c")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void 삭제된_글에는_404다_존재하지_않는_글과_같은_응답이다() throws Exception {
    Account author = fixtures.createUser(Role.USER);
    long postId = posts.create(author.id());
    posts.softDelete(postId, author.id());

    write(postId, fixtures.loginAsNew(Role.USER), "content", "c")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  // ── ⑦ ⑧ ⑨ 비로그인·id 형식·검사 순서 ──────────────────────

  @Test
  void 비로그인은_401이다() throws Exception {
    write(somePost(), null, "content", "c")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void 검사_순서_비로그인이면_입력이_틀리고_글이_없어도_401이다() throws Exception {
    write(Long.MAX_VALUE, null, "content", "")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void 검사_순서_입력이_틀리면_글이_없어도_400이다() throws Exception {
    write(Long.MAX_VALUE, fixtures.loginAsNew(Role.USER), "content", "")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void 숫자가_아닌_글_id는_400_INVALID_REQUEST다() throws Exception {
    write("abc", fixtures.loginAsNew(Role.USER), "content", "c")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }
}
