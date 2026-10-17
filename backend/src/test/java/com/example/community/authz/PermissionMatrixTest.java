package com.example.community.authz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.community.support.IntegrationTest;
import com.example.community.support.JsonBodies;
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
    MockHttpServletRequestBuilder request = requestFor(row);
    Cookie accessToken = loginAs(row.get("actor"));
    if (accessToken != null) {
      request.cookie(accessToken);
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
  private MockHttpServletRequestBuilder requestFor(Map<String, String> row) {
    String endpoint = row.get("method") + " " + row.get("path");
    return switch (endpoint) {
      case "POST /api/auth/signup" ->
          post("/api/auth/signup")
              .contentType(MediaType.APPLICATION_JSON)
              .content(
                  JsonBodies.of(
                      "email", TestData.uniqueEmail(), "password", TestData.validPassword()));
      default -> {
        fail("%s: '%s'의 실행기가 없다. ACTIVE로 바꾸기 전에 실행기를 추가한다.", row.get("case_id"), endpoint);
        yield null;
      }
    };
  }

  /** 행의 actor로 로그인한 토큰 쿠키. GUEST는 null. */
  private Cookie loginAs(String actor) {
    if (actor.equals("GUEST")) {
      return null;
    }
    fail("'%s' 로그인은 아직 지원하지 않는다 (로그인 기능 PR에서 추가).", actor);
    return null;
  }

  private static void assertFlag(String body, String field, String expected, String caseId) {
    if (expected.equals("-")) {
      return;
    }
    Boolean actual = JsonPath.read(body, "$." + field);
    assertThat(actual).as("%s %s", caseId, field).isEqualTo(Boolean.parseBoolean(expected));
  }
}
