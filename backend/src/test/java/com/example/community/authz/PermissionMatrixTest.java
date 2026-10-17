package com.example.community.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.community.auth.Role;
import com.example.community.support.AuthFixtures;
import com.example.community.support.AuthFixtures.Account;
import com.example.community.support.IntegrationTest;
import com.example.community.support.JsonBodies;
import com.example.community.support.PostFixtures;
import com.example.community.support.SpecCsv;
import com.example.community.support.TestData;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 권한 매트릭스 실행기 (TST-02, 03 문서 5.3절·HD-20).
 *
 * <p>{@code permission-matrix.csv}의 ACTIVE 행마다 실제 HTTP 요청을 보내 상태코드와 {@code canEdit}/{@code
 * canDelete}를 확인한다. 엔드포인트별 "요청 만드는 법"은 {@link #requestFor}에 기능 PR이 하나씩 추가한다. 실행기가 없는 엔드포인트의 행을
 * ACTIVE로 바꾸면 그 행은 실패한다.
 */
@IntegrationTest
class PermissionMatrixTest {

  static final String SPEC = "/authz/permission-matrix.csv";

  @Autowired MockMvc mvc;
  @Autowired AuthFixtures fixtures;
  @Autowired PostFixtures posts;

  /** 행의 actor로 로그인한 상태. GUEST는 계정·쿠키 없음. */
  record Actor(Account account, Cookie[] cookies) {}

  static Stream<Arguments> activeRows() {
    return SpecCsv.read(SPEC).rows().stream()
        .filter(r -> r.get("state").equals("ACTIVE"))
        .map(r -> Arguments.of(Named.of(describe(r), r)));
  }

  static String describe(Map<String, String> r) {
    return "%s %s %s %s/%s → %s"
        .formatted(
            r.get("case_id"),
            r.get("method"),
            r.get("path"),
            r.get("actor"),
            r.get("target"),
            r.get("expected"));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("activeRows")
  void 권한_매트릭스_행의_기대값을_만족한다(Map<String, String> row) throws Exception {
    // 03 문서 HD-27: actor 로그인 → (대상 리소스 생성) → 요청
    Actor actor = loginAs(row.get("actor"));
    MockHttpServletRequestBuilder request = requestFor(row, actor);
    if (actor.cookies().length > 0) {
      request.cookie(actor.cookies());
    }

    MvcResult result = mvc.perform(request.with(csrf())).andReturn();

    String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    assertThat(result.getResponse().getStatus())
        .as("%s 응답: %s", row.get("case_id"), body)
        .isEqualTo(Integer.parseInt(row.get("expected")));
    assertFlag(body, "canEdit", row.get("can_edit"), row.get("case_id"));
    assertFlag(body, "canDelete", row.get("can_delete"), row.get("case_id"));
  }

  /** 엔드포인트별 실행기. 새 기능 PR은 여기에 case를 추가하고 해당 CSV 행을 ACTIVE로 바꾼다. */
  private MockHttpServletRequestBuilder requestFor(Map<String, String> row, Actor actor) {
    String endpoint = row.get("method") + " " + row.get("path");
    return switch (endpoint) {
      case "POST /api/auth/signup" ->
          post("/api/auth/signup")
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                  JsonBodies.of(
                      "email", TestData.uniqueEmail(), "password", TestData.validPassword()));
      case "POST /api/auth/login" -> {
        Account account = fixtures.createUser(Role.USER);
        yield post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(JsonBodies.of("email", account.email(), "password", account.password()));
      }
      case "POST /api/auth/logout" -> post("/api/auth/logout");
      case "POST /api/auth/refresh" -> post("/api/auth/refresh");
      case "GET /api/auth/me" -> get("/api/auth/me");
      // ── 게시글 (01 v0.5) ──
      case "POST /api/posts" ->
          post("/api/posts")
              .contentType(MediaType.APPLICATION_JSON)
              .content(JsonBodies.of("title", "매트릭스 제목", "content", "매트릭스 본문"));
      case "GET /api/posts" -> get("/api/posts");
      case "GET /api/posts/{id}" -> get("/api/posts/{id}", targetPost(row, actor));
      default -> {
        fail("%s: '%s'의 실행기가 없다. ACTIVE로 바꾸기 전에 실행기를 추가한다.", row.get("case_id"), endpoint);
        yield null;
      }
    };
  }

  /**
   * 행의 actor로 실제 로그인한 상태 (03 문서 HD-23). USER·ADMIN은 매번 새 계정으로 rememberMe=true 로그인 → 액세스·리프레시 쿠키,
   * GUEST는 계정·쿠키 없음.
   */
  private Actor loginAs(String actor) throws Exception {
    return switch (actor) {
      case "GUEST" -> new Actor(null, new Cookie[0]);
      case "USER", "ADMIN" -> {
        Account account = fixtures.createUser(Role.valueOf(actor));
        yield new Actor(account, fixtures.loginRemembered(account).cookies());
      }
      default -> throw new IllegalArgumentException(actor);
    };
  }

  /**
   * 행의 target에 맞는 글을 저장소에 직접 만든다 (03 문서 HD-27). OWN = actor의 글, OTHER = 새 일반 회원의 글.
   *
   * @return 글 id
   */
  private long targetPost(Map<String, String> row, Actor actor) {
    return switch (row.get("target")) {
      case "OWN" -> {
        if (actor.account() == null) {
          throw new IllegalStateException(row.get("case_id") + ": GUEST에는 OWN 대상이 없다");
        }
        yield posts.create(actor.account().id());
      }
      case "OTHER" -> posts.create(fixtures.createUser(Role.USER).id());
      default ->
          throw new IllegalStateException(row.get("case_id") + ": 글 대상이 필요한 행인데 target=NONE");
    };
  }

  private static void assertFlag(String body, String field, String expected, String caseId) {
    if (expected.equals("-")) {
      return;
    }
    Boolean actual = JsonPath.read(body, "$." + field);
    assertThat(actual).as("%s %s", caseId, field).isEqualTo(Boolean.parseBoolean(expected));
  }
}
