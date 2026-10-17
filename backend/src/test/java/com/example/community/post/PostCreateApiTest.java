package com.example.community.post;

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

/** POST-01 게시글 작성 (01 문서 v0.5). */
@IntegrationTest
class PostCreateApiTest {

  /** 이모지 1개 = 코드 포인트 1개 = UTF-16 2단위. 길이 규칙(D-22)이 코드 포인트 기준인지 확인하는 데 쓴다. */
  static final String EMOJI = "😀";

  @Autowired MockMvc mvc;
  @Autowired AuthFixtures fixtures;
  @Autowired PostFixtures posts;

  ResultActions write(Cookie token, Object... body) throws Exception {
    return mvc.perform(
        post("/api/posts")
            .with(csrf())
            .cookie(token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(JsonBodies.of(body)));
  }

  static long idOf(MvcResult result) throws Exception {
    Number id =
        JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
    return id.longValue();
  }

  // ── ① ④ 성공 ──────────────────────────────────────────

  @Test
  void 회원이_글을_쓰면_201이고_상세와_같은_형태의_본문을_준다() throws Exception {
    Account me = fixtures.createUser(Role.USER);

    MvcResult result =
        write(fixtures.login(me), "title", "첫 글", "content", "안녕하세요")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.title").value("첫 글"))
            .andExpect(jsonPath("$.content").value("안녕하세요"))
            .andExpect(jsonPath("$.authorName").value(endsWith("@example.com")))
            .andExpect(jsonPath("$.authorName").value(containsString("***")))
            .andExpect(jsonPath("$.createdAt").value(endsWith("Z")))
            .andExpect(jsonPath("$.canEdit").value(true))
            .andExpect(jsonPath("$.canDelete").value(true))
            .andReturn();

    Map<String, Object> body =
        JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$");
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
    assertThat(body.get("updatedAt")).as("수정 전에는 null (D-23)").isNull();
  }

  @Test
  void 관리자도_글을_쓸_수_있다() throws Exception {
    write(fixtures.loginAsNew(Role.ADMIN), "title", "공지", "content", "내용")
        .andExpect(status().isCreated());
  }

  // ── ③ 작성자·작성 시각은 서버가 기록 ──────────────────────

  @Test
  void 작성자와_작성_시각이_DB에_기록된다() throws Exception {
    Account me = fixtures.createUser(Role.USER);
    long id = idOf(write(fixtures.login(me), "title", "t", "content", "c").andReturn());

    Map<String, Object> row = posts.row(id);
    assertThat(((Number) row.get("author_id")).longValue()).isEqualTo(me.id());
    assertThat(row.get("created_at")).isNotNull();
    assertThat(row.get("updated_at")).isNull();
    assertThat(row.get("deleted_at")).isNull();
  }

  @Test
  void 요청에_authorId를_넣어도_무시하고_로그인한_사용자가_작성자다() throws Exception {
    Account me = fixtures.createUser(Role.USER);
    Account other = fixtures.createUser(Role.USER);

    long id =
        idOf(
            write(
                    fixtures.login(me),
                    "title",
                    "t",
                    "content",
                    "c",
                    "authorId",
                    other.id(),
                    "id",
                    999999)
                .andExpect(status().isCreated())
                .andReturn());

    assertThat(((Number) posts.row(id).get("author_id")).longValue()).isEqualTo(me.id());
  }

  // ── ① 앞뒤 공백 제거 후 저장, 코드 포인트 기준 길이 (D-22) ─────

  @Test
  void 제목과_본문의_앞뒤_공백을_제거한_값을_저장한다() throws Exception {
    long id =
        idOf(
            write(
                    fixtures.loginAsNew(Role.USER),
                    "title",
                    "  \t제목　 ",
                    "content",
                    "\n  본문 첫 줄\n둘째 줄  \n")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("제목"))
                .andExpect(jsonPath("$.content").value("본문 첫 줄\n둘째 줄"))
                .andReturn());

    Map<String, Object> row = posts.row(id);
    assertThat(row.get("title")).isEqualTo("제목");
    assertThat(row.get("content")).isEqualTo("본문 첫 줄\n둘째 줄");
  }

  @Test
  void 제목_100자와_본문_10000자는_허용한다() throws Exception {
    write(fixtures.loginAsNew(Role.USER), "title", "가".repeat(100), "content", "a".repeat(10_000))
        .andExpect(status().isCreated());
  }

  @Test
  void 이모지는_한_글자로_센다_제목_이모지_100개와_본문_이모지_10000개는_허용한다() throws Exception {
    write(
            fixtures.loginAsNew(Role.USER),
            "title",
            EMOJI.repeat(100),
            "content",
            EMOJI.repeat(10_000))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.title").value(EMOJI.repeat(100)));
  }

  // ── ② 범위 밖 → 400 VALIDATION_FAILED ───────────────────

  @Test
  void 제목이_101자면_400이고_fields는_title이다() throws Exception {
    write(fixtures.loginAsNew(Role.USER), "title", "가".repeat(101), "content", "c")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fields[0]").value("title"));
  }

  @Test
  void 이모지_101개_제목도_400이다() throws Exception {
    write(fixtures.loginAsNew(Role.USER), "title", EMOJI.repeat(101), "content", "c")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields[0]").value("title"));
  }

  @Test
  void 본문이_10001자면_400이고_fields는_content다() throws Exception {
    write(fixtures.loginAsNew(Role.USER), "title", "t", "content", "a".repeat(10_001))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fields[0]").value("content"));
  }

  @Test
  void 공백을_제거하면_101자가_넘지_않는_제목은_허용한다() throws Exception {
    write(fixtures.loginAsNew(Role.USER), "title", "  " + "가".repeat(100) + "  ", "content", "c")
        .andExpect(status().isCreated());
  }

  @Test
  void 빈_값이나_공백만_있거나_누락된_제목_본문은_400이다() throws Exception {
    Cookie token = fixtures.loginAsNew(Role.USER);

    write(token, "title", "", "content", "c")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields[0]").value("title"));
    write(token, "title", " \t\n　", "content", "c")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields[0]").value("title"));
    write(token, "title", "t", "content", "   ")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields[0]").value("content"));
    write(token, "content", "c")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields[0]").value("title"));
    write(token, "title", null, "content", null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields.length()").value(2))
        .andExpect(jsonPath("$.fields[0]").value("content"))
        .andExpect(jsonPath("$.fields[1]").value("title"));
  }

  @Test
  void 본문이_JSON이_아니면_400_INVALID_REQUEST다() throws Exception {
    mvc.perform(
            post("/api/posts")
                .with(csrf())
                .cookie(fixtures.loginAsNew(Role.USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": "))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  // ── ⑤ 비로그인 ─────────────────────────────────────────

  @Test
  void 로그인하지_않으면_401이다() throws Exception {
    mvc.perform(
            post("/api/posts")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JsonBodies.of("title", "t", "content", "c")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }
}
