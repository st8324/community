package com.example.community.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.community.auth.Role;
import com.example.community.support.AuthFixtures;
import com.example.community.support.IntegrationTest;
import com.example.community.support.PostFixtures;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * POST-02 게시글 목록 (01 문서 v0.5, D-21).
 *
 * <p>테스트는 DB를 비우지 않으므로(03 문서 5.1절) 다른 테스트가 남긴 글이 섞여 있다. 그래서 각 테스트는 "지금까지 가장 늦은 작성 시각"보다 뒤의 시각으로 글을
 * 만들어, 자기 글이 첫 페이지 맨 위에 오게 한 뒤 그 부분만 확인한다.
 */
@IntegrationTest
class PostListApiTest {

  static final int PAGE_SIZE = 20;

  @Autowired MockMvc mvc;
  @Autowired AuthFixtures fixtures;
  @Autowired PostFixtures posts;

  /** 기존 글보다 뒤의 시각으로 n개를 오래된 것부터 만든다. 반환 목록도 오래된 것부터. */
  List<Long> createNewestPosts(int n) {
    long author = fixtures.createUser(Role.USER).id();
    LocalDateTime base = posts.latestCreatedAt().plusMinutes(1);
    List<Long> ids = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      ids.add(posts.create(author, "목록 " + i, "본문 " + i, base.plusSeconds(i)));
    }
    return ids;
  }

  static List<Long> itemIds(String json) {
    List<Number> ids = JsonPath.read(json, "$.items[*].id");
    return ids.stream().map(Number::longValue).toList();
  }

  String list(String query) throws Exception {
    return mvc.perform(get("/api/posts" + query))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString(StandardCharsets.UTF_8);
  }

  // ── ① 최신순 ③ 삭제 제외 ────────────────────────────────

  @Test
  void 최신순이고_삭제된_글은_목록에서_빠진다() throws Exception {
    List<Long> ids = createNewestPosts(3); // 오래된 순: a, b, c
    long a = ids.get(0);
    long b = ids.get(1);
    long c = ids.get(2);
    posts.softDelete(b, fixtures.createUser(Role.ADMIN).id());

    List<Long> top = itemIds(list("")).subList(0, 2);
    assertThat(top).containsExactly(c, a);
  }

  @Test
  void 작성_시각이_같으면_id가_큰_글이_먼저다() throws Exception {
    long author = fixtures.createUser(Role.USER).id();
    LocalDateTime same = posts.latestCreatedAt().plusMinutes(1);
    long first = posts.create(author, "같은 시각 1", "c", same);
    long second = posts.create(author, "같은 시각 2", "c", same);

    assertThat(itemIds(list("")).subList(0, 2)).containsExactly(second, first);
  }

  // ── ② 페이지당 20개, page는 0부터 ────────────────────────

  @Test
  void 한_페이지는_20개이고_page_1이_다음_20개다() throws Exception {
    List<Long> mine = createNewestPosts(PAGE_SIZE + 1); // 21개, 오래된 순

    String page0 = list("?page=0");
    assertThat(itemIds(page0)).hasSize(PAGE_SIZE);
    assertThat(itemIds(page0).get(0)).isEqualTo(mine.get(PAGE_SIZE)); // 가장 최근
    assertThat((Integer) JsonPath.read(page0, "$.page")).isZero();
    assertThat((Integer) JsonPath.read(page0, "$.size")).isEqualTo(PAGE_SIZE);
    long total = ((Number) JsonPath.read(page0, "$.totalElements")).longValue();
    assertThat(total).isGreaterThanOrEqualTo(PAGE_SIZE + 1);
    assertThat((Integer) JsonPath.read(page0, "$.totalPages"))
        .isEqualTo((int) ((total + PAGE_SIZE - 1) / PAGE_SIZE));

    String page1 = list("?page=1");
    assertThat(itemIds(page1).get(0)).as("21개 중 가장 오래된 글").isEqualTo(mine.get(0));
    assertThat((Integer) JsonPath.read(page1, "$.page")).isEqualTo(1);
  }

  @Test
  void page를_생략하면_0페이지다() throws Exception {
    List<Long> mine = createNewestPosts(1);
    String json = list("");
    assertThat((Integer) JsonPath.read(json, "$.page")).isZero();
    assertThat(itemIds(json).get(0)).isEqualTo(mine.get(0));
  }

  @Test
  void 크기_파라미터를_보내도_무시하고_20개다() throws Exception {
    createNewestPosts(PAGE_SIZE + 1);
    assertThat(itemIds(list("?size=1"))).hasSize(PAGE_SIZE);
    assertThat(itemIds(list("?size=1000"))).hasSize(PAGE_SIZE);
  }

  // ── ④ 비정상 page ──────────────────────────────────────

  @Test
  void 음수_page는_400_VALIDATION_FAILED이고_fields는_page다() throws Exception {
    mvc.perform(get("/api/posts?page=-1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fields[0]").value("page"));
  }

  @Test
  void 숫자가_아닌_page는_400_INVALID_REQUEST다() throws Exception {
    for (String bad : List.of("abc", "1.5", "99999999999")) {
      mvc.perform(get("/api/posts").param("page", bad))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
  }

  @Test
  void 마지막_페이지를_넘으면_200과_빈_목록이다() throws Exception {
    createNewestPosts(1);
    String json = list("?page=1000000");
    assertThat(itemIds(json)).isEmpty();
    assertThat(((Number) JsonPath.read(json, "$.totalElements")).longValue()).isPositive();
  }

  @Test
  void int_최댓값_page도_500이_아니라_200과_빈_목록이다() throws Exception {
    // page × 20이 int 범위를 넘는다. JPA의 시작 위치(firstResult)는 int라서 그대로 넘기면 서버 오류가 난다.
    createNewestPosts(1);
    String json = list("?page=" + Integer.MAX_VALUE);
    assertThat(itemIds(json)).isEmpty();
    assertThat((Integer) JsonPath.read(json, "$.page")).isEqualTo(Integer.MAX_VALUE);
    assertThat(((Number) JsonPath.read(json, "$.totalElements")).longValue()).isPositive();
  }

  // ── ⑤ 응답 형태 ────────────────────────────────────────

  @Test
  void 응답과_항목은_정해진_필드만_가진다() throws Exception {
    createNewestPosts(1);
    String json = list("");

    Map<String, Object> body = JsonPath.read(json, "$");
    assertThat(body).containsOnlyKeys("items", "page", "size", "totalElements", "totalPages");
    Map<String, Object> item = JsonPath.read(json, "$.items[0]");
    assertThat(item).containsOnlyKeys("id", "title", "authorName", "createdAt");
    assertThat((String) item.get("createdAt")).endsWith("Z");
    assertThat((String) item.get("authorName")).contains("***@");
  }

  // ── 누구나 조회, 무효 토큰은 401 (POST-03 ④, D-20) ─────────

  @Test
  void 회원도_같은_목록을_200으로_본다() throws Exception {
    mvc.perform(get("/api/posts").cookie(fixtures.loginAsNew(Role.USER)))
        .andExpect(status().isOk());
  }

  @Test
  void 망가진_토큰_쿠키와_함께_목록을_조회하면_401이다() throws Exception {
    mvc.perform(get("/api/posts").cookie(new Cookie(AuthFixtures.ACCESS_TOKEN, "not-a-jwt")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }
}
