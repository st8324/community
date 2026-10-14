package com.example.community.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.community.support.SpecCsv;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 보안 회귀 테스트 "목록"을 검사한다.
 *
 * <p>목록이 TST-03 범위를 빠짐없이 덮는지, ACTIVE 항목이 가리키는 테스트 메서드가 실제로 존재하는지 확인한다. 테스트를 지우거나 이름을 바꾸면 여기서 실패한다 →
 * "보안 테스트가 조용히 사라지는 것"을 막는다.
 */
class SecurityRegressionSpecTest {

  static final String SPEC = "/security/security-regression.csv";
  static final String PACKAGE = "com.example.community.security.";

  static final List<String> HEADER =
      List.of("case_id", "req_id", "description", "test_ref", "state");

  /** 회귀 테스트로 반드시 지켜야 하는 요구사항 ID (01 TST-03 + 02 문서 8장의 '테스트' 검증 항목). */
  static final Set<String> REQUIRED_REQ_IDS =
      Set.of(
          "SEC-02", "SEC-03", "SEC-04", "SEC-05", "SEC-06", "SEC-08", "SEC-10", "SEC-13", "SEC-14",
          "DATA-05", "AUTH-02");

  static SpecCsv.Table table;

  @BeforeAll
  static void load() {
    table = SpecCsv.read(SPEC);
  }

  @Test
  void 헤더가_약속한_형식이다() {
    assertThat(table.header()).containsExactlyElementsOf(HEADER);
  }

  @Test
  void case_id와_test_ref는_유일하고_형식을_지킨다() {
    List<String> ids = table.rows().stream().map(r -> r.get("case_id")).toList();
    List<String> refs = table.rows().stream().map(r -> r.get("test_ref")).toList();
    assertThat(ids).doesNotHaveDuplicates().allMatch(id -> id.matches("S-\\d{2}"));
    assertThat(refs).doesNotHaveDuplicates().allMatch(ref -> ref.matches("[A-Z]\\w+#\\w+"));
    assertThat(table.rows()).allSatisfy(r -> assertThat(r.get("state")).isIn("PENDING", "ACTIVE"));
  }

  @Test
  void 필수_보안_요구사항이_모두_목록에_있다() {
    Set<String> covered =
        table.rows().stream().map(r -> r.get("req_id")).collect(Collectors.toSet());
    assertThat(covered).containsAll(REQUIRED_REQ_IDS);
  }

  @Test
  void ACTIVE_항목의_테스트_메서드가_실제로_존재한다() throws Exception {
    for (Map<String, String> r : table.rows()) {
      if (!r.get("state").equals("ACTIVE")) {
        continue;
      }
      String[] parts = r.get("test_ref").split("#");
      Class<?> clazz = Class.forName(PACKAGE + parts[0]);
      boolean found =
          Arrays.stream(clazz.getDeclaredMethods())
              .map(Method::getName)
              .anyMatch(name -> name.equals(parts[1]));
      assertThat(found).as("%s → %s", r.get("case_id"), r.get("test_ref")).isTrue();
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
    assertThat(pending).as("strict 모드: 미검증 보안 회귀 항목").isEmpty();
  }

  @AfterAll
  static void report() {
    Map<String, Long> byState =
        table.rows().stream()
            .collect(Collectors.groupingBy(r -> r.get("state"), Collectors.counting()));
    SpecCsv.writeReport(
        "security-regression.md",
        """
        ### 보안 회귀 (TST-03)
        | ACTIVE (검증 중) | PENDING (미구현) |
        |---:|---:|
        | %d | %d |
        """
            .formatted(byState.getOrDefault("ACTIVE", 0L), byState.getOrDefault("PENDING", 0L)));
  }
}
