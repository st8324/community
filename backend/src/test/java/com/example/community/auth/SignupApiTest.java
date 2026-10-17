package com.example.community.auth;

import static com.example.community.support.TestData.uniqueEmail;
import static com.example.community.support.TestData.validPassword;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.community.support.AuthFixtures;
import com.example.community.support.IntegrationTest;
import com.example.community.support.JsonBodies;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** AUTH-01 회원가입 수용 기준 (01 문서 v0.4 4.1절). 쿠키 속성은 CookieSecurityTest. */
@IntegrationTest
class SignupApiTest {

  static final String SMILE = "😀";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  ResultActions signup(String json) throws Exception {
    return mvc.perform(
        post("/api/auth/signup")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(json));
  }

  static String body(String email, String password) {
    return JsonBodies.of("email", email, "password", password);
  }

  String storedRole(String email) {
    return jdbc.queryForObject("SELECT role FROM users WHERE email = ?", String.class, email);
  }

  long countByEmail(String email) {
    return jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Long.class, email);
  }

  // ── ① 성공 + 가입 직후 자동 로그인 (01 v0.4, D-15) ──────────────

  @Test
  void 올바른_이메일과_비밀번호로_가입하면_201이고_id와_마스킹된_표시명을_돌려준다() throws Exception {
    String email = uniqueEmail();
    MvcResult result =
        signup(body(email, validPassword()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(
                jsonPath("$.displayName")
                    .value(email.substring(0, 2) + "***" + email.substring(email.indexOf('@'))))
            .andExpect(jsonPath("$.email").doesNotExist())
            .andExpect(jsonPath("$.role").doesNotExist())
            .andReturn();
    assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
        .doesNotContain(email);
  }

  @Test
  void 가입하면_바로_로그인된다_받은_토큰_쿠키로_me를_호출할_수_있다() throws Exception {
    MvcResult result = signup(body(uniqueEmail(), validPassword())).andReturn();
    String line = AuthFixtures.accessTokenSetCookie(result).orElseThrow();
    Cookie token = new Cookie(AuthFixtures.ACCESS_TOKEN, AuthFixtures.cookieValue(line));
    Number id =
        JsonPath.read(result.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");

    mvc.perform(get("/api/auth/me").cookie(token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id.longValue()));
  }

  @Test
  void 가입에_실패하면_토큰_쿠키를_발급하지_않는다() throws Exception {
    String email = uniqueEmail();
    signup(body(email, validPassword())).andExpect(status().isCreated());

    MvcResult duplicate = signup(body(email, validPassword())).andReturn();
    MvcResult invalid = signup(body(uniqueEmail(), "short")).andReturn();
    assertThat(AuthFixtures.accessTokenSetCookie(duplicate)).isEmpty();
    assertThat(AuthFixtures.accessTokenSetCookie(invalid)).isEmpty();
  }

  // ── ② ③ 중복 ───────────────────────────────────────────

  @Test
  void 이미_가입된_이메일이면_409_EMAIL_ALREADY_USED다() throws Exception {
    String email = uniqueEmail();
    signup(body(email, validPassword())).andExpect(status().isCreated());

    signup(body(email, validPassword()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
    assertThat(countByEmail(email)).isEqualTo(1);
  }

  @Test
  void 대소문자만_다른_이메일도_중복이다() throws Exception {
    String email = uniqueEmail();
    signup(body(email.toUpperCase(Locale.ROOT), validPassword())).andExpect(status().isCreated());

    signup(body(email, validPassword())).andExpect(status().isConflict());
  }

  // ── ④ 역할 ─────────────────────────────────────────────

  @Test
  void 가입_직후_역할은_USER다() throws Exception {
    String email = uniqueEmail();
    signup(body(email, validPassword())).andExpect(status().isCreated());
    assertThat(storedRole(email)).isEqualTo("USER");
  }

  @Test
  void 요청에_role_ADMIN을_넣어도_무시하고_USER로_만든다() throws Exception {
    String email = uniqueEmail();
    String json = JsonBodies.of("email", email, "password", validPassword(), "role", "ADMIN");
    signup(json).andExpect(status().isCreated());
    assertThat(storedRole(email)).isEqualTo("USER");
  }

  // ── ⑤ ⑥ 이메일 정규화·형식 ────────────────────────────────

  @Test
  void 이메일은_앞뒤_공백을_지우고_소문자로_저장한다() throws Exception {
    String email = uniqueEmail();
    signup(body("  " + email.toUpperCase(Locale.ROOT) + " ", validPassword()))
        .andExpect(status().isCreated());
    assertThat(countByEmail(email)).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "plain", "a@b", "a b@example.com", "한글@example.com"})
  void 형식이_잘못된_이메일은_400_VALIDATION_FAILED이고_필드명만_알려준다(String email) throws Exception {
    signup(body(email, validPassword()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.fields").value(hasItem("email")));
  }

  @Test
  void 이메일이_255자면_400이다() throws Exception {
    String email = "a".repeat(255 - "@example.com".length()) + "@example.com";
    signup(body(email, validPassword())).andExpect(status().isBadRequest());
  }

  @Test
  void 이메일이_없으면_400이다() throws Exception {
    signup(JsonBodies.of("password", validPassword()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields").value(hasItem("email")));
  }

  // ── ⑦ 비밀번호 정책 (SEC-03) ────────────────────────────────

  @Test
  void 비밀번호_7자는_400_8자는_201이다() throws Exception {
    signup(body(uniqueEmail(), "a".repeat(7)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields").value(hasItem("password")));
    signup(body(uniqueEmail(), "a".repeat(8))).andExpect(status().isCreated());
  }

  @Test
  void 비밀번호_64자는_201_65자는_400이다() throws Exception {
    signup(body(uniqueEmail(), "a".repeat(64))).andExpect(status().isCreated());
    signup(body(uniqueEmail(), "a".repeat(65))).andExpect(status().isBadRequest());
  }

  @Test
  void 이모지_7개_비밀번호는_7자이므로_400이다() throws Exception {
    signup(body(uniqueEmail(), SMILE.repeat(7))).andExpect(status().isBadRequest());
    signup(body(uniqueEmail(), SMILE.repeat(8))).andExpect(status().isCreated());
  }

  @Test
  void 비밀번호의_앞뒤_공백은_자르지_않는다() throws Exception {
    // 공백 포함 8자. 서버가 trim하면 6자가 되어 400이 나온다.
    signup(body(uniqueEmail(), " abcdef ")).andExpect(status().isCreated());
  }

  @Test
  void 비밀번호가_없으면_400이다() throws Exception {
    signup(JsonBodies.of("email", uniqueEmail()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fields").value(hasItem("password")));
  }

  // ── 요청 형식 (4.5절) ─────────────────────────────────────

  @Test
  void 본문이_JSON이_아니면_400_INVALID_REQUEST다() throws Exception {
    signup("{not-json")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  void Content_Type이_JSON이_아니면_415다() throws Exception {
    mvc.perform(
            post("/api/auth/signup")
                .with(csrf())
                .contentType(MediaType.TEXT_PLAIN)
                .content("email=a@example.com"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
  }
}
