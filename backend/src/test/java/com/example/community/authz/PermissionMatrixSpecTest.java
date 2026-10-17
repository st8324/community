package com.example.community.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.example.community.support.SpecCsv;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 권한 매트릭스 "명세 파일"을 검사한다 (Spring·DB 불필요, 빠름).
 *
 * <p>이 테스트는 매트릭스가 요구사항을 빠짐없이, 모순 없이 담고 있는지 본다. 실제 HTTP 호출 검증은 기능 PR에서 추가하는 {@code
 * PermissionMatrixTest}가 ACTIVE 행을 대상으로 수행한다.
 */
class PermissionMatrixSpecTest {

  static final String SPEC = "/authz/permission-matrix.csv";
  static final String EXECUTOR = "com.example.community.authz.PermissionMatrixTest";

  static final List<String> HEADER =
      List.of(
          "case_id",
          "req_id",
          "method",
          "path",
          "actor",
          "target",
          "expected",
          "can_edit",
          "can_delete",
          "state");

  /** 02 문서 5.3절 API 목록 (csrf·health 제외 — 보안 회귀 목록에서 다룸). */
  static final Set<String> REQUIRED_ENDPOINTS =
      Set.of(
          "POST /api/auth/signup",
          "POST /api/auth/login",
          "POST /api/auth/logout",
          "POST /api/auth/refresh",
          "GET /api/auth/me",
          "GET /api/posts",
          "GET /api/posts/{id}",
          "POST /api/posts",
          "PUT /api/posts/{id}",
          "DELETE /api/posts/{id}",
          "GET /api/posts/{id}/comments",
          "POST /api/posts/{id}/comments",
          "DELETE /api/comments/{id}");

  /** 소유자에 따라 결과가 달라지는 API: 회원·관리자 모두 OWN/OTHER 두 행이 있어야 한다. */
  static final Set<String> OWNERSHIP_ENDPOINTS =
      Set.of(
          "GET /api/posts/{id}",
          "PUT /api/posts/{id}",
          "DELETE /api/posts/{id}",
          "GET /api/posts/{id}/comments",
          "DELETE /api/comments/{id}");

  static SpecCsv.Table table;

  @BeforeAll
  static void load() {
    table = SpecCsv.read(SPEC);
  }

  static String endpoint(Map<String, String> r) {
    return r.get("method") + " " + r.get("path");
  }

  @Test
  void 헤더가_약속한_형식이다() {
    assertThat(table.header()).containsExactlyElementsOf(HEADER);
  }

  @Test
  void case_id는_유일하고_형식을_지킨다() {
    List<String> ids = table.rows().stream().map(r -> r.get("case_id")).toList();
    assertThat(ids).doesNotHaveDuplicates().allMatch(id -> id.matches("M-\\d{2}"));
  }

  @Test
  void 값은_정해진_어휘만_쓴다() {
    for (Map<String, String> r : table.rows()) {
      String id = r.get("case_id");
      assertThat(r.get("method")).as(id).isIn("GET", "POST", "PUT", "DELETE");
      assertThat(r.get("actor")).as(id).isIn("GUEST", "USER", "ADMIN");
      assertThat(r.get("target")).as(id).isIn("NONE", "OWN", "OTHER");
      assertThat(r.get("expected"))
          .as(id)
          .isIn("200", "201", "204", "400", "401", "403", "404", "N/A");
      assertThat(r.get("can_edit")).as(id).isIn("true", "false", "-");
      assertThat(r.get("can_delete")).as(id).isIn("true", "false", "-");
      assertThat(r.get("state")).as(id).isIn("PENDING", "ACTIVE", "N/A");
      assertThat(r.get("req_id")).as(id).matches("(AUTH|POST|CMT|ADM)-\\d{2}");
    }
  }

  @Test
  void N_A는_상태와_기대값이_함께_N_A다() {
    for (Map<String, String> r : table.rows()) {
      boolean expectedNa = r.get("expected").equals("N/A");
      boolean stateNa = r.get("state").equals("N/A");
      assertThat(expectedNa).as(r.get("case_id")).isEqualTo(stateNa);
    }
  }

  @Test
  void API_목록의_모든_엔드포인트가_있고_모르는_엔드포인트는_없다() {
    Set<String> inSpec =
        table.rows().stream().map(PermissionMatrixSpecTest::endpoint).collect(Collectors.toSet());
    assertThat(inSpec).containsExactlyInAnyOrderElementsOf(REQUIRED_ENDPOINTS);
  }

  @Test
  void 모든_엔드포인트에_세_역할이_모두_있다() {
    Map<String, Set<String>> actorsByEndpoint =
        table.rows().stream()
            .collect(
                Collectors.groupingBy(
                    PermissionMatrixSpecTest::endpoint,
                    Collectors.mapping(r -> r.get("actor"), Collectors.toSet())));
    actorsByEndpoint.forEach(
        (ep, actors) ->
            assertThat(actors).as(ep).containsExactlyInAnyOrder("GUEST", "USER", "ADMIN"));
  }

  @Test
  void 소유자_구분_API는_회원과_관리자에_OWN_OTHER가_모두_있다() {
    for (String ep : OWNERSHIP_ENDPOINTS) {
      for (String actor : List.of("USER", "ADMIN")) {
        Set<String> targets =
            table.rows().stream()
                .filter(r -> endpoint(r).equals(ep) && r.get("actor").equals(actor))
                .map(r -> r.get("target"))
                .collect(Collectors.toSet());
        assertThat(targets).as(ep + " / " + actor).containsExactlyInAnyOrder("OWN", "OTHER");
      }
    }
  }

  @Test
  void 요구사항_불변식_비회원은_수정_삭제_힌트가_항상_false다() {
    table.rows().stream()
        .filter(r -> r.get("actor").equals("GUEST"))
        .forEach(
            r -> {
              assertThat(r.get("can_edit")).as(r.get("case_id")).isNotEqualTo("true");
              assertThat(r.get("can_delete")).as(r.get("case_id")).isNotEqualTo("true");
            });
  }

  @Test
  void 요구사항_불변식_누구도_타인_글을_수정할_수_없다() {
    table.rows().stream()
        .filter(r -> r.get("target").equals("OTHER"))
        .forEach(
            r -> {
              assertThat(r.get("can_edit")).as(r.get("case_id")).isNotEqualTo("true");
              if (r.get("method").equals("PUT")) {
                assertThat(r.get("expected")).as(r.get("case_id")).isIn("401", "403");
              }
            });
  }

  /** 로그인 없이 호출하는 상태 변경 API (01 v0.4: 로그아웃은 항상 204, D-17 — 03 HD-22). */
  static final Set<String> NO_LOGIN_REQUIRED =
      Set.of("/api/auth/signup", "/api/auth/login", "/api/auth/logout");

  @Test
  void 비회원의_로그인_필요_요청은_401이다() {
    table.rows().stream()
        .filter(r -> r.get("actor").equals("GUEST") && !r.get("method").equals("GET"))
        .filter(r -> !NO_LOGIN_REQUIRED.contains(r.get("path")))
        .forEach(r -> assertThat(r.get("expected")).as(r.get("case_id")).isEqualTo("401"));
  }

  @Test
  void ACTIVE_행이_있으면_실행_테스트가_존재해야_한다() {
    boolean anyActive = table.rows().stream().anyMatch(r -> r.get("state").equals("ACTIVE"));
    if (!anyActive) {
      return;
    }
    try {
      Class.forName(EXECUTOR);
    } catch (ClassNotFoundException e) {
      fail("ACTIVE 행이 있는데 %s가 없다. ACTIVE는 '실제로 검증 중'이라는 뜻이다.", EXECUTOR);
    }
  }

  @Test
  void strict_모드에서는_PENDING이_남아_있으면_안_된다() {
    if (!SpecCsv.strictMode()) {
      return;
    }
    List<String> pending =
        table.rows().stream()
            .filter(r -> r.get("state").equals("PENDING"))
            .map(r -> r.get("case_id"))
            .toList();
    assertThat(pending).as("strict 모드: 미검증 권한 매트릭스 행").isEmpty();
  }

  @AfterAll
  static void report() {
    Map<String, Long> byState =
        table.rows().stream()
            .collect(Collectors.groupingBy(r -> r.get("state"), Collectors.counting()));
    SpecCsv.writeReport(
        "permission-matrix.md",
        """
        ### 권한 매트릭스 (TST-02)
        | ACTIVE (검증 중) | PENDING (미구현) | N/A (해당 없음) |
        |---:|---:|---:|
        | %d | %d | %d |
        """
            .formatted(
                byState.getOrDefault("ACTIVE", 0L),
                byState.getOrDefault("PENDING", 0L),
                byState.getOrDefault("N/A", 0L)));
  }
}
